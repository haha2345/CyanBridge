package com.fersaiyan.cyanbridge.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.alibaba.fastjson.JSONObject
import com.alibaba.idst.nui.AsrResult
import com.alibaba.idst.nui.CommonUtils
import com.alibaba.idst.nui.Constants
import com.alibaba.idst.nui.INativeNuiCallback
import com.alibaba.idst.nui.KwsResult
import com.alibaba.idst.nui.NativeNui
import com.fersaiyan.cyanbridge.BuildConfig
import com.jieli.jl_audio_decode.callback.OnDecodeStreamCallback
import com.jieli.jl_audio_decode.opus.OpusManager
import com.jieli.jl_audio_decode.opus.model.OpusOption
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.communication.ILargeDataResponse
import com.oudmon.ble.base.communication.LargeDataHandler
import com.oudmon.ble.base.communication.bigData.resp.AiChatResponse
import com.oudmon.ble.base.communication.bigData.resp.GlassModelControlResponse
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 阿里云 ASR 会话：
 * - 从眼镜 BLE 大包接收 Opus
 * - 解码为 PCM 并喂给 NUI SDK
 * - 结果回调给上层
 */
class AliyunAsrWakeSession(
        private val context: Context,
        private val onResult: (String) -> Unit,
        private val onStatus: (String) -> Unit,
) : INativeNuiCallback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)

    private val audioQueue = AudioByteQueue()
    private var opusManager: OpusManager? = null

    private val opusChunk = ByteArray(120)
    private var opusIndex = 0

    private var nuiInstance: NativeNui? = null
    private var initDone = false
    private var sessionTimeoutJob: Job? = null

    private val aiChatListener =
            object : ILargeDataResponse<AiChatResponse> {
                // BLE 大包：提取音频 payload 并送入 Opus 解码。
                override fun parseData(cmdType: Int, response: AiChatResponse?) {
                    if (!running.get()) return
                    if (cmdType != 0x59) return
                    val sub = response?.subData ?: return
                    if (sub.size <= 6) return
                    val payload = sub.copyOfRange(6, sub.size)
                    feedOpus(payload)
                }
            }

    private val controlCb =
            object : ILargeDataResponse<GlassModelControlResponse> {
                // 控制类回调，这里不处理具体数据。
                override fun parseData(cmdType: Int, response: GlassModelControlResponse?) = Unit
            }

    private var opusFrameCount = 0
    private var pcmBytesTotal = 0L

    private val decodeCb =
            object : OnDecodeStreamCallback {
                // Opus 解码后输出 PCM 数据。
                override fun onDecodeStream(pcmData: ByteArray) {
                    pcmBytesTotal += pcmData.size
                    if (pcmBytesTotal % 16000 < pcmData.size) { // ~每1秒log一次
                        Log.i(TAG, "PCM decoded: ${pcmBytesTotal} bytes total")
                    }
                    audioQueue.offer(pcmData)
                }
                // 解码开始回调。
                override fun onStart() {
                    Log.i(TAG, "Opus decode started")
                }
                // 解码完成回调。
                override fun onComplete(path: String?) {
                    Log.i(TAG, "Opus decode complete, path=$path")
                }
                // 解码错误回调。
                override fun onError(code: Int, message: String?) {
                    Log.e(TAG, "Opus decode error: code=$code message=$message")
                }
            }

    /** 是否处于识别中。 */
    fun isRunning(): Boolean = running.get()

    /** 启动一次 ASR 识别会话。 */
    fun start(source: String) {
        if (!running.compareAndSet(false, true)) return

        if (!BleOperateManager.getInstance().isConnected) {
            running.set(false)
            logStatus("BLE not connected; cannot start ASR")
            return
        }

        if (BuildConfig.ALIYUN_ASR_APPKEY.isBlank() || BuildConfig.ALIYUN_ASR_TOKEN.isBlank()) {
            running.set(false)
            logStatus("ALIYUN_ASR_APPKEY / ALIYUN_ASR_TOKEN missing in local.properties")
            return
        }

        logStatus("Aliyun ASR: starting ($source)")
        Log.i(
                TAG,
                "APPKEY=${BuildConfig.ALIYUN_ASR_APPKEY.take(6)}... TOKEN=${BuildConfig.ALIYUN_ASR_TOKEN.take(6)}..."
        )

        audioQueue.clear()
        opusFrameCount = 0
        pcmBytesTotal = 0
        opusIndex = 0

        if (!ensureNuiInitialized()) {
            running.set(false)
            return
        }

        opusManager =
                try {
                    OpusManager().also { mgr ->
                        val opt =
                                OpusOption()
                                        .setHasHead(false)
                                        .setSampleRate(16_000)
                                        .setPacketSize(40)
                                        .setChannel(1)
                        mgr.startDecodeStream(opt, decodeCb)
                    }
                } catch (t: Throwable) {
                    running.set(false)
                    logStatus("Opus init failed: ${t.message}")
                    return
                }

        LargeDataHandler.getInstance().initPackageNotify(aiChatListener)

        scope.launch {
            val nui = nuiInstance ?: return@launch
            val params = genParams()
            val ret = nui.setParams(params)
            logStatus("Aliyun setParams ret=$ret")
            val startRet = nui.startDialog(Constants.VadMode.TYPE_P2T, genDialogParams())
            logStatus("Aliyun startDialog ret=$startRet")
            if (startRet != Constants.NuiResultCode.SUCCESS) {
                scheduleStop("start-failed")
            }
        }

        sessionTimeoutJob?.cancel()
        sessionTimeoutJob =
                scope.launch {
                    delay(SESSION_TIMEOUT_MS)
                    if (running.get()) scheduleStop("timeout")
                }
    }

    /** 主动停止识别。 */
    fun stop(reason: String) {
        scheduleStop(reason)
    }

    /** 释放资源并停止协程。 */
    fun shutdown() {
        try {
            scheduleStop("shutdown")
        } catch (_: Throwable) {}
        scope.cancel()
    }

    /** NUI 音频状态回调。 */
    override fun onNuiAudioStateChanged(state: Constants.AudioState) {
        Log.i(TAG, "onNuiAudioStateChanged: $state")
    }

    /** NUI 需要音频数据时从队列读取。 */
    override fun onNuiNeedAudioData(buffer: ByteArray, len: Int): Int {
        val read = audioQueue.read(buffer, len)
        if (read > 0) {
            Log.i(TAG, "onNuiNeedAudioData: requested=$len returned=$read")
        }
        return read
    }

    /** NUI 事件回调（最终识别结果 / VAD / 错误）。 */
    override fun onNuiEventCallback(
            event: Constants.NuiEvent,
            resultCode: Int,
            arg2: Int,
            kwsResult: KwsResult?,
            asrResult: AsrResult?,
    ) {
        Log.i(TAG, "NUI event=$event resultCode=$resultCode arg2=$arg2")
        when (event) {
            Constants.NuiEvent.EVENT_ASR_PARTIAL_RESULT -> {
                val partial = asrResult?.asrResult?.trim().orEmpty()
                Log.i(TAG, "ASR partial: $partial")
            }
            Constants.NuiEvent.EVENT_ASR_RESULT -> {
                val text = asrResult?.asrResult?.trim().orEmpty()
                Log.i(TAG, "ASR FINAL result raw: $text")
                if (text.isNotBlank()) {
                    mainHandler.post { onResult(text) }
                } else {
                    logStatus("ASR result empty")
                }
                scheduleStop("asr_result")
            }
            Constants.NuiEvent.EVENT_VAD_END -> {
                Log.i(TAG, "VAD end detected")
                scheduleStop("vad_end")
            }
            Constants.NuiEvent.EVENT_ASR_ERROR -> {
                Log.e(TAG, "ASR ERROR code=$resultCode arg2=$arg2")
                scheduleStop("asr_error")
            }
            Constants.NuiEvent.EVENT_MIC_ERROR -> {
                Log.e(TAG, "MIC ERROR (no audio data arriving)")
                scheduleStop("mic_error")
            }
            else -> {
                Log.i(TAG, "NUI other event: $event")
            }
        }
    }

    /** 音量回调（本工程不使用）。 */
    override fun onNuiAudioRMSChanged(valRms: Float) = Unit

    /** VPR 事件回调（本工程不使用）。 */
    override fun onNuiVprEventCallback(event: Constants.NuiVprEvent) = Unit

    /** 初始化 NUI SDK。 */
    private fun ensureNuiInitialized(): Boolean {
        if (initDone && nuiInstance != null) return true

        val nui = nuiInstance ?: NativeNui().also { nuiInstance = it }

        // Copy assets once (required by SDK, even for cloud-only it avoids WARNs)
        CommonUtils.copyAssetsData(context.applicationContext)

        val debugPath = context.externalCacheDir?.absolutePath + "/aliyun_nui_debug"
        val ret =
                nui.initialize(
                        this,
                        genInitParams(debugPath),
                        Constants.LogLevel.LOG_LEVEL_DEBUG,
                        false,
                )
        logStatus("Aliyun initialize ret=$ret")
        initDone = ret == Constants.NuiResultCode.SUCCESS
        return initDone
    }

    /** 生成 NUI 初始化参数。 */
    private fun genInitParams(debugPath: String): String {
        val obj = JSONObject()
        obj["app_key"] = BuildConfig.ALIYUN_ASR_APPKEY
        obj["token"] = BuildConfig.ALIYUN_ASR_TOKEN
        obj["device_id"] = getDeviceId()
        obj["url"] = DEFAULT_WS_URL
        obj["debug_path"] = debugPath
        obj["log_track_level"] =
                Constants.LogLevel.toInt(Constants.LogLevel.LOG_LEVEL_NONE).toString()
        obj["service_mode"] = Constants.ModeAsrCloud
        return obj.toString()
    }

    /** 生成识别参数（采样率、VAD 等）。 */
    private fun genParams(): String {
        val nlsConfig = JSONObject()
        nlsConfig["enable_intermediate_result"] = false
        nlsConfig["enable_punctuation_prediction"] = true
        nlsConfig["sample_rate"] = 16000
        nlsConfig["sr_format"] = "pcm"
        nlsConfig["enable_voice_detection"] = true
        nlsConfig["max_start_silence"] = 10000
        nlsConfig["max_end_silence"] = 800

        val params = JSONObject()
        params["nls_config"] = nlsConfig
        params["service_type"] = Constants.kServiceTypeASR
        return params.toString()
    }

    /** 生成对话参数（可扩展 token/app_key 刷新）。 */
    private fun genDialogParams(): String {
        val dialog = JSONObject()
        // token/app_key can be refreshed here if needed
        return dialog.toString()
    }

    /** 获取设备唯一 ID。 */
    private fun getDeviceId(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                    ?: "unknown_device"
        } catch (_: Throwable) {
            "unknown_device"
        }
    }

    /** 将 BLE 下发的 Opus 包按固定大小拼接并喂给解码器。 */
    private fun feedOpus(bytes: ByteArray) {
        val mgr = opusManager ?: return
        for (b in bytes) {
            opusChunk[opusIndex++] = b
            if (opusIndex == opusChunk.size) {
                try {
                    mgr.writeAudioStream(opusChunk)
                } catch (t: Throwable) {
                    logStatus("writeAudioStream failed: ${t.message}")
                }
                opusIndex = 0
            }
        }
    }

    /** 异步停止，避免阻塞回调线程。 */
    private fun scheduleStop(reason: String) {
        scope.launch { stopInternal(reason) }
    }

    /** 具体停止逻辑：停止对话、停止解码、清理队列。 */
    private fun stopInternal(reason: String) {
        if (!running.compareAndSet(true, false)) return

        logStatus("Aliyun ASR: stopping ($reason)")

        sessionTimeoutJob?.cancel()

        try {
            nuiInstance?.stopDialog()
        } catch (_: Throwable) {}

        LargeDataHandler.getInstance().removeGptNotify()
        LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x0B), controlCb)

        val mgr = opusManager
        opusManager = null
        try {
            mgr?.stopDecodeStream()
        } catch (_: Throwable) {}
        try {
            mgr?.release()
        } catch (_: Throwable) {}
        audioQueue.clear()
    }

    /** 统一输出状态日志，并回调给 UI。 */
    private fun logStatus(message: String) {
        Log.i(TAG, message)
        mainHandler.post { onStatus(message) }
    }

    /** PCM 数据队列：供 NUI 读取。 */
    private class AudioByteQueue {
        private val queue = LinkedBlockingQueue<ByteArray>()
        private var current: ByteArray? = null
        private var offset: Int = 0

        private var offerCount = 0
        private var readCallCount = 0

        /** 写入 PCM 块。 */
        fun offer(data: ByteArray) {
            if (data.isNotEmpty()) {
                queue.offer(data)
                offerCount++
                if (offerCount % 50 == 0) {
                    Log.i("AliAsr", "audioQueue.offer #$offerCount queueSize=${queue.size}")
                }
            }
        }

        /** 读取 PCM 数据供 NUI 使用。 */
        fun read(dst: ByteArray, len: Int): Int {
            readCallCount++
            var total = 0
            while (total < len) {
                val cur = current
                if (cur == null || offset >= cur.size) {
                    current = null
                    offset = 0
                    val next = queue.poll(20, TimeUnit.MILLISECONDS) ?: break
                    current = next
                }

                val active = current ?: break
                val toCopy = minOf(len - total, active.size - offset)
                System.arraycopy(active, offset, dst, total, toCopy)
                offset += toCopy
                total += toCopy
            }
            if (readCallCount % 100 == 0) {
                Log.i(
                        "AliAsr",
                        "audioQueue.read #$readCallCount queueSize=${queue.size} returned=$total"
                )
            }
            return total
        }

        /** 清空队列。 */
        fun clear() {
            queue.clear()
            current = null
            offset = 0
            Log.i("AliAsr", "audioQueue.clear()")
        }
    }

    companion object {
        private const val TAG = "AliAsr"
        private const val DEFAULT_WS_URL = "wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1"
        private const val SESSION_TIMEOUT_MS = 30_000L
    }
}
