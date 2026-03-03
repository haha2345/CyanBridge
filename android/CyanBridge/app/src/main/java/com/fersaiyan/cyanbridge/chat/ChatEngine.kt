package com.fersaiyan.cyanbridge.chat

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import com.fersaiyan.cyanbridge.ai.QwenChatClient
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 语音对话核心引擎：
 * - 统一接收文本（ASR/输入）
 * - 调用大模型（Qwen-Flash）
 * - 使用 Android 系统 TTS 播报
 * - 写入对话存储
 */
object ChatEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean(false)

    private lateinit var repository: ChatRepository
    private lateinit var qwen: QwenChatClient
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 初始化对话引擎（全局单例）。 */
    fun init(context: Context) {
        if (initialized.getAndSet(true)) return
        ChatStore.init(context.applicationContext)
        repository = ChatRepository()
        qwen = QwenChatClient()

        initTts(context.applicationContext)
    }

    private fun initTts(appContext: Context) {
        tts =
                TextToSpeech(appContext) { status ->
                    Log.i(TAG, "TTS init callback status=$status")
                    if (status == TextToSpeech.SUCCESS) {
                        setupTtsLanguage()
                    } else {
                        Log.e(
                                TAG,
                                "TTS init failed (status=$status), retrying with default engine..."
                        )
                        // Retry: sometimes a second init succeeds
                        mainHandler.postDelayed(
                                {
                                    tts?.shutdown()
                                    tts =
                                            TextToSpeech(appContext) { retryStatus ->
                                                Log.i(TAG, "TTS retry callback status=$retryStatus")
                                                if (retryStatus == TextToSpeech.SUCCESS) {
                                                    setupTtsLanguage()
                                                } else {
                                                    Log.e(
                                                            TAG,
                                                            "TTS retry also failed. Check device TTS settings."
                                                    )
                                                    // List available engines for diagnostics
                                                    tts?.engines?.forEach { engine ->
                                                        Log.i(
                                                                TAG,
                                                                "  Available TTS engine: ${engine.name} (${engine.label})"
                                                        )
                                                    }
                                                }
                                            }
                                },
                                1000
                        )
                    }
                }
    }

    private fun setupTtsLanguage() {
        val t = tts ?: return
        // Try Chinese variants, then fall back to device default
        val result = t.setLanguage(Locale.CHINESE)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Locale.CHINESE not supported (result=$result), trying Locale.CHINA...")
            val r2 = t.setLanguage(Locale.CHINA)
            if (r2 == TextToSpeech.LANG_MISSING_DATA || r2 == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Locale.CHINA not supported, falling back to default")
                t.setLanguage(Locale.getDefault())
            }
        }
        t.setSpeechRate(1.0f)
        ttsReady = true
        Log.i(TAG, "Android TTS ready! lang=${t.voice?.locale}, engine=${t.defaultEngine}")
    }

    /** 提交用户文本。 */
    fun submitUserText(rawText: String, source: String = ChatSource.TEXT) {
        val text = ChatTextParser.normalize(rawText) ?: return
        Log.i(TAG_FLOW, "submitUserText source=$source text=${text.take(120)}")
        val now = System.currentTimeMillis()
        val userMessage =
                ChatMessageEntity(
                        id = UUID.randomUUID().toString(),
                        role = ChatRoles.USER,
                        content = text,
                        createdAt = now,
                        status = ChatStatus.OK,
                        source = source,
                )

        scope.launch {
            repository.insert(userMessage)
            generateAssistantReply(text)
        }
    }

    /** 用 Android TTS 朗读任意文本（用于消息重播）。 */
    fun speakText(text: String) {
        Log.i(TAG, "speakText called, ttsReady=$ttsReady, text=${text.take(60)}")
        if (!ttsReady) {
            Log.e(TAG, "TTS not ready, cannot speak")
            return
        }
        mainHandler.post {
            tts?.stop()
            val result =
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
            Log.i(TAG, "TTS speak result=$result")
        }
    }

    /** 停止 TTS 播放。 */
    fun stopSpeaking() {
        tts?.stop()
    }

    /** 调用大模型并生成回复，然后 TTS 播放。 */
    private suspend fun generateAssistantReply(userText: String) {
        Log.i(TAG_FLOW, "qwen request text=${userText.take(120)}")
        val reply =
                try {
                    qwen.chat(userText).trim()
                } catch (t: Throwable) {
                    Log.e(TAG, "Qwen chat failed", t)
                    null
                }

        val now = System.currentTimeMillis()
        if (reply.isNullOrBlank()) {
            Log.e(TAG_FLOW, "qwen reply empty")
            val errorMessage =
                    ChatMessageEntity(
                            id = UUID.randomUUID().toString(),
                            role = ChatRoles.ASSISTANT,
                            content = "大模型调用失败，请稍后重试。",
                            createdAt = now,
                            status = ChatStatus.ERROR,
                            source = ChatSource.SYSTEM,
                    )
            repository.insert(errorMessage)
            return
        }

        Log.i(TAG_FLOW, "qwen reply len=${reply.length}")
        val assistantMessage =
                ChatMessageEntity(
                        id = UUID.randomUUID().toString(),
                        role = ChatRoles.ASSISTANT,
                        content = reply,
                        createdAt = now,
                        status = ChatStatus.OK,
                        source = ChatSource.SYSTEM,
                )

        repository.insert(assistantMessage)

        // Use Android TTS to speak the reply
        speakText(reply)
    }

    private const val TAG = "ChatEngine"
    private const val TAG_FLOW = "ChatFlow"
}
