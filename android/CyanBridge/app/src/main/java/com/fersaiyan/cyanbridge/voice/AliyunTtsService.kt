package com.fersaiyan.cyanbridge.voice

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.alibaba.fastjson.JSONObject
import com.alibaba.idst.nui.CommonUtils
import com.alibaba.idst.nui.Constants
import com.alibaba.idst.nui.INativeTtsCallback
import com.alibaba.idst.nui.NativeNui
import com.fersaiyan.cyanbridge.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 阿里云 TTS 封装：
 * - 传入文本，返回 wav 文件路径
 * - 内部处理初始化、参数设置与回调数据拼接
 */
class AliyunTtsService(
    private val context: Context,
    private val appKey: String = BuildConfig.ALIYUN_TTS_APPKEY.ifBlank { BuildConfig.ALIYUN_ASR_APPKEY },
    private val token: String = BuildConfig.ALIYUN_TTS_TOKEN.ifBlank { BuildConfig.ALIYUN_ASR_TOKEN },
) {
    private val initialized = AtomicBoolean(false)
    private val nui = NativeNui(Constants.ModeType.MODE_TTS)
    private val ttsLock = Any()

    @Volatile
    private var session: TtsSession? = null

    /**
     * 合成文本为 wav 文件。
     * @return 成功返回本地路径，失败返回 null
     */
    fun synthesizeToFile(text: String, messageId: String = UUID.randomUUID().toString()): String? {
        synchronized(ttsLock) {
                val trimmed = text.trim()
                if (trimmed.isBlank()) return null
                if (!ensureInitialized()) return null

                val ttsText = if (trimmed.length > MAX_TTS_CHARS) trimmed.take(MAX_TTS_CHARS) else trimmed
                val dir = context.getExternalFilesDir("tts") ?: context.cacheDir
                if (!dir.exists()) dir.mkdirs()
                val outFile = File(dir, "$messageId.wav")

            // Latch released by TTS_EVENT_END / ERROR / CANCEL.
            val latch = CountDownLatch(1)
            val buffer = ByteArrayOutputStream()
            // TTS requires a 32-char taskId. Hyphens make it invalid and cause 144002 errors.
            val taskId = messageId.replace("-", "").ifBlank { UUID.randomUUID().toString().replace("-", "") }
            session = TtsSession(taskId = taskId, buffer = buffer, latch = latch)

            applyParams(ttsText)

            // startTts(priority, taskId, text) - order matters.
            val startRet = nui.startTts(DEFAULT_PRIORITY, taskId, ttsText)
            if (startRet != Constants.NuiResultCode.SUCCESS) {
                Log.e(TAG, "startTts failed: $startRet")
                session = null
                return null
            }
            Log.i(TAG, "startTts ok taskId=$taskId len=${ttsText.length}")

            // Wait for end/error; otherwise treat as timeout.
            val completed = latch.await(TTS_TIMEOUT_SEC, TimeUnit.SECONDS)
            if (!completed) {
                Log.e(TAG, "TTS timeout after ${TTS_TIMEOUT_SEC}s")
                session = null
                return null
            }
            val current = session
            session = null

            if (current == null) return null
            if (current.errorCode != null) {
                Log.e(TAG, "TTS error: ${current.errorCode}")
                return null
            }

            val bytes = buffer.toByteArray()
            if (bytes.isEmpty()) {
                Log.e(TAG, "TTS produced empty audio")
                return null
            }

            outFile.writeBytes(bytes)
            Log.i(TAG, "TTS saved: ${outFile.absolutePath} size=${bytes.size}")
            return outFile.absolutePath
        }
    }

    /**
     * 初始化 SDK（只需一次）。
     */
    private fun ensureInitialized(): Boolean {
        if (initialized.get()) return true
        if (appKey.isBlank() || token.isBlank()) {
            Log.e(TAG, "ALIYUN_TTS_APPKEY / ALIYUN_TTS_TOKEN missing")
            return false
        }

        CommonUtils.copyAssetsData(context.applicationContext)

        val workspace = context.getExternalFilesDir("aliyun_tts") ?: context.cacheDir
        if (!workspace.exists()) workspace.mkdirs()

        val ticket = JSONObject()
        ticket["app_key"] = appKey
        ticket["token"] = token
        ticket["device_id"] = getDeviceId()
        ticket["url"] = DEFAULT_WS_URL
        ticket["workspace"] = workspace.absolutePath
        ticket["mode_type"] = Constants.TtsModeTypeCloud
        // Short text mode by default; long text uses tts_version=1.
        ticket["tts_version"] = "0"

        val ret = nui.tts_initialize(callback, ticket.toString(), Constants.LogLevel.LOG_LEVEL_NONE, false)
        if (ret != Constants.NuiResultCode.SUCCESS) {
            Log.e(TAG, "tts_initialize failed: $ret")
            return false
        }
        initialized.set(true)
        return true
    }

    /**
     * 设置 TTS 参数（音色、格式、采样率等）。
     */
    private fun applyParams(text: String) {
        nui.setparamTts("app_key", appKey)
        nui.setparamTts("token", token)
        nui.setparamTts("font_name", DEFAULT_VOICE)
        nui.setparamTts("encode_type", DEFAULT_ENCODE)
        nui.setparamTts("sample_rate", DEFAULT_SAMPLE_RATE)
        nui.setparamTts("mode_type", Constants.TtsModeTypeCloud)
        nui.setparamTts("tts_version", if (text.length > MAX_TTS_CHARS) "1" else "0")
    }

    /**
     * TTS 回调：接收事件和音频数据。
     */
    private val callback =
        object : INativeTtsCallback {
            /**
             * TTS 事件回调：结束/错误/取消等。
             */
            override fun onTtsEventCallback(event: INativeTtsCallback.TtsEvent, taskId: String, retCode: Int) {
                val current = session ?: return
                if (current.taskId != taskId) return
                when (event) {
                    INativeTtsCallback.TtsEvent.TTS_EVENT_END -> {
                        Log.i(TAG, "tts end taskId=$taskId bytes=${current.receivedBytes}")
                        current.latch.countDown()
                    }
                    INativeTtsCallback.TtsEvent.TTS_EVENT_ERROR -> {
                        Log.e(TAG, "tts error event taskId=$taskId ret=$retCode bytes=${current.receivedBytes}")
                        current.errorCode = retCode
                        current.latch.countDown()
                    }
                    INativeTtsCallback.TtsEvent.TTS_EVENT_CANCEL -> {
                        Log.w(TAG, "tts cancel taskId=$taskId bytes=${current.receivedBytes}")
                        current.latch.countDown()
                    }
                    else -> Unit
                }
            }

            // onTtsDataCallback(info, infoLen, data): data holds audio bytes.
            // info is not taskId, so do not filter by it.
            /**
             * 音频数据回调：data 即为音频字节。
             */
            override fun onTtsDataCallback(info: String, infoLen: Int, data: ByteArray?) {
                val current = session ?: return
                if (data == null || data.isEmpty()) return
                current.buffer.write(data, 0, data.size)
                current.receivedBytes += data.size
                if (!current.loggedFirstChunk) {
                    current.loggedFirstChunk = true
                    Log.i(TAG, "tts data first chunk taskId=${current.taskId} len=${data.size}")
                }
            }

            /**
             * 音量回调（未使用）。
             */
            override fun onTtsVolCallback(vol: Int) = Unit
        }

    /**
     * 获取设备唯一标识，用于 SDK ticket。
     */
    private fun getDeviceId(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "device-${UUID.randomUUID()}"
        } catch (_: Throwable) {
            "device-${UUID.randomUUID()}"
        }
    }

    private data class TtsSession(
        val taskId: String,
        val buffer: ByteArrayOutputStream,
        val latch: CountDownLatch,
        var errorCode: Int? = null,
        var receivedBytes: Int = 0,
        var loggedFirstChunk: Boolean = false,
    )

    companion object {
        private const val TAG = "AliyunTts"
        private const val DEFAULT_WS_URL = "wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1"
        private const val DEFAULT_VOICE = "zhiyue"
        private const val DEFAULT_ENCODE = "wav"
        private const val DEFAULT_SAMPLE_RATE = "16000"
        private const val DEFAULT_PRIORITY = "1"
        private const val MAX_TTS_CHARS = 300
        private const val TTS_TIMEOUT_SEC = 30L
    }
}
