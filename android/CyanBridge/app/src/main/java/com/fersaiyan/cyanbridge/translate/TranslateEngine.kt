package com.fersaiyan.cyanbridge.translate

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import com.fersaiyan.cyanbridge.ai.QwenChatClient
import com.fersaiyan.cyanbridge.ui.chat.ChatAudioPlayer
import com.fersaiyan.cyanbridge.voice.AliyunTtsService
import java.util.UUID
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 同声传译引擎：
 * - 手机麦克风 → 阿里云 ASR → Qwen 翻译 → 阿里云 TTS → 播放
 * - 句子边界检测 + 翻译队列
 */
class TranslateEngine(private val context: Context) {

    enum class State {
        IDLE,
        LISTENING,
        TRANSLATING,
        PLAYING
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Public state ──
    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _sourceText = MutableStateFlow("")
    val sourceText: StateFlow<String> = _sourceText.asStateFlow()

    private val _translatedText = MutableStateFlow("")
    val translatedText: StateFlow<String> = _translatedText.asStateFlow()

    private val _languagePair = MutableStateFlow(LanguagePair.DEFAULT)
    val languagePair: StateFlow<LanguagePair> = _languagePair.asStateFlow()

    // ── Internal ──
    private val nui = NativeNui(Constants.ModeType.MODE_ASR)
    private val nuiInitialized = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private var ttsJob: Job? = null

    private val qwen = QwenChatClient()
    private lateinit var ttsService: AliyunTtsService
    private val audioPlayer = ChatAudioPlayer()

    // Sentence buffer for current ASR partial results
    private val sentenceBuffer = StringBuilder()
    // TTS playback queue
    private val ttsQueue = LinkedBlockingDeque<String>(100)

    fun setLanguagePair(pair: LanguagePair) {
        _languagePair.value = pair
    }

    fun swapLanguages() {
        _languagePair.value = _languagePair.value.swap()
    }

    /** 开始翻译（手机麦克风 → ASR → Qwen → TTS）。 */
    fun start() {
        if (running.getAndSet(true)) return
        Log.i(TAG, "start() pair=${_languagePair.value.displayLabel}")
        _state.value = State.LISTENING
        _sourceText.value = ""
        _translatedText.value = ""
        sentenceBuffer.clear()
        ttsQueue.clear()

        ttsService = AliyunTtsService(context)

        scope.launch {
            if (!ensureNuiInitialized()) {
                Log.e(TAG, "NUI init failed")
                stop()
                return@launch
            }
            startAsr()
            startMicrophone()
            startTtsConsumer()
        }
    }

    /** 停止翻译。 */
    fun stop() {
        if (!running.getAndSet(false)) return
        Log.i(TAG, "stop()")
        _state.value = State.IDLE

        recordJob?.cancel()
        ttsJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Throwable) {}
        audioRecord = null

        try {
            nui.stopDialog()
        } catch (_: Throwable) {}
        audioPlayer.stop()
        ttsQueue.clear()
    }

    // ── NUI初始化 ──

    private fun ensureNuiInitialized(): Boolean {
        if (nuiInitialized.get()) return true
        val appKey = BuildConfig.ALIYUN_ASR_APPKEY
        val token = BuildConfig.ALIYUN_ASR_TOKEN
        if (appKey.isBlank() || token.isBlank()) {
            Log.e(TAG, "Missing ALIYUN_ASR credentials")
            return false
        }
        CommonUtils.copyAssetsData(context.applicationContext)
        val workspace = context.getExternalFilesDir("aliyun_translate") ?: context.cacheDir
        if (!workspace.exists()) workspace.mkdirs()

        val ticket = JSONObject()
        ticket["app_key"] = appKey
        ticket["token"] = token
        ticket["device_id"] = getDeviceId()
        ticket["url"] = "wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1"
        ticket["workspace"] = workspace.absolutePath

        val ret =
                nui.initialize(
                        nuiCallback,
                        ticket.toString(),
                        Constants.LogLevel.LOG_LEVEL_NONE,
                        false
                )
        if (ret != Constants.NuiResultCode.SUCCESS) {
            Log.e(TAG, "nui.initialize failed: $ret")
            return false
        }
        nuiInitialized.set(true)
        return true
    }

    private fun startAsr() {
        nui.setParams(buildAsrParams())
        val ret = nui.startDialog(Constants.VadMode.TYPE_P2T, buildDialogParams())
        Log.i(TAG, "startDialog ret=$ret")
    }

    private fun buildAsrParams(): String {
        val p = JSONObject()
        p["nls_config"] =
                JSONObject().apply {
                    this["enable_intermediate_result"] = true
                    this["enable_punctuation_prediction"] = true
                    this["enable_inverse_text_normalization"] = true
                }
        p["service_type"] = Constants.kServiceTypeASR
        return p.toString()
    }

    private fun buildDialogParams(): String {
        return JSONObject()
                .apply {
                    this["enable_voice_detection"] = false // no VAD, continuous mode
                }
                .toString()
    }

    // ── 手机麦克风录音 ──

    private fun startMicrophone() {
        val sampleRate = 16000
        val bufferSize =
                AudioRecord.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                )

        @Suppress("MissingPermission")
        audioRecord =
                AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize * 2
                )
        audioRecord?.startRecording()

        recordJob =
                scope.launch {
                    val buffer = ByteArray(3200) // 100ms at 16kHz 16bit
                    while (isActive && running.get()) {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                        if (read > 0) {
                            // NUI expects external audio data
                            // This is handled via onNuiNeedAudioData callback
                            micBuffer.offer(buffer.copyOf(read))
                        }
                    }
                }
    }

    // Mic audio buffer for NUI
    private val micBuffer = LinkedBlockingDeque<ByteArray>(200)

    // ── NUI回调 ──

    private val nuiCallback =
            object : INativeNuiCallback {
                override fun onNuiEventCallback(
                        event: Constants.NuiEvent,
                        resultCode: Int,
                        arg2: Int,
                        kwsResult: KwsResult?,
                        asrResult: AsrResult?
                ) {
                    when (event) {
                        Constants.NuiEvent.EVENT_ASR_PARTIAL_RESULT -> {
                            val text = parseAsrText(asrResult?.asrResult ?: "")
                            if (text.isNotBlank()) {
                                _sourceText.value = sentenceBuffer.toString() + text
                                // Check sentence boundary
                                if (isSentenceComplete(text)) {
                                    val sentence = sentenceBuffer.toString() + text
                                    sentenceBuffer.clear()
                                    enqueueTranslation(sentence)
                                }
                            }
                        }
                        Constants.NuiEvent.EVENT_ASR_RESULT -> {
                            val text = parseAsrText(asrResult?.asrResult ?: "")
                            if (text.isNotBlank()) {
                                val sentence = sentenceBuffer.toString() + text
                                sentenceBuffer.clear()
                                _sourceText.value = sentence
                                enqueueTranslation(sentence)
                            }
                            // Restart ASR for continuous listening
                            if (running.get()) {
                                scope.launch {
                                    delay(200)
                                    if (running.get()) startAsr()
                                }
                            }
                        }
                        Constants.NuiEvent.EVENT_ASR_ERROR -> {
                            Log.e(TAG, "ASR error: $resultCode")
                            if (running.get()) {
                                scope.launch {
                                    delay(1000)
                                    if (running.get()) startAsr()
                                }
                            }
                        }
                        else -> {}
                    }
                }

                override fun onNuiNeedAudioData(buffer: ByteArray, len: Int): Int {
                    val data =
                            micBuffer.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                                    ?: return 0
                    val copyLen = minOf(data.size, len)
                    System.arraycopy(data, 0, buffer, 0, copyLen)
                    return copyLen
                }

                override fun onNuiAudioStateChanged(state: Constants.AudioState) {}
                override fun onNuiAudioRMSChanged(rms: Float) {}
                override fun onNuiVprEventCallback(event: Constants.NuiVprEvent) {}
            }

    private fun parseAsrText(json: String): String {
        return try {
            val obj = com.alibaba.fastjson.JSON.parseObject(json)
            obj?.getJSONObject("payload")?.getString("result") ?: ""
        } catch (_: Throwable) {
            json
        }
    }

    // ── 句子边界检测 ──

    private fun isSentenceComplete(text: String): Boolean {
        if (text.length < 6) return false
        val endsWithPunct =
                text.endsWith("。") ||
                        text.endsWith(".") ||
                        text.endsWith("?") ||
                        text.endsWith("？") ||
                        text.endsWith("!") ||
                        text.endsWith("！") ||
                        text.endsWith(",") ||
                        text.endsWith("，")
        return endsWithPunct || text.length >= 15
    }

    // ── 翻译 + TTS ──

    private fun enqueueTranslation(sentence: String) {
        val trimmed = sentence.trim()
        if (trimmed.isBlank()) return
        _state.value = State.TRANSLATING
        scope.launch {
            val pair = _languagePair.value
            val prompt = "将以下${pair.sourceName}翻译成${pair.targetName}，只输出翻译结果：\n$trimmed"
            Log.i(TAG, "translate prompt=$prompt")
            try {
                val result = qwen.chat(prompt).trim()
                Log.i(TAG, "translate result=$result")
                if (result.isNotBlank()) {
                    _translatedText.value = (_translatedText.value + "\n" + result).trim()
                    ttsQueue.putLast(result)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "translate failed", t)
            }
        }
    }

    private fun startTtsConsumer() {
        ttsJob =
                scope.launch {
                    while (isActive && running.get()) {
                        val text =
                                try {
                                    ttsQueue.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                                } catch (_: InterruptedException) {
                                    null
                                }
                        if (text != null) {
                            _state.value = State.PLAYING
                            val id = "translate_${System.currentTimeMillis()}"
                            val path = ttsService.synthesizeToFile(text, id)
                            if (path != null) {
                                audioPlayer.play(path)
                                // Wait for playback to roughly finish
                                delay((text.length * 200L).coerceAtMost(5000))
                            }
                            if (ttsQueue.isEmpty()) {
                                _state.value = State.LISTENING
                            }
                        }
                    }
                }
    }

    private fun getDeviceId(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                    ?: "device-${UUID.randomUUID()}"
        } catch (_: Throwable) {
            "device-${UUID.randomUUID()}"
        }
    }

    companion object {
        private const val TAG = "TransEngine"
    }
}
