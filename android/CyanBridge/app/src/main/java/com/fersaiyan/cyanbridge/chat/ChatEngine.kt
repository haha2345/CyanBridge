package com.fersaiyan.cyanbridge.chat

import android.content.Context
import android.util.Log
import com.fersaiyan.cyanbridge.ai.QwenChatClient
import com.fersaiyan.cyanbridge.ui.chat.ChatAudioPlayer
import com.fersaiyan.cyanbridge.voice.AliyunTtsService
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
 * - 使用阿里云 TTS 合成语音并缓存到文件
 * - 写入对话存储
 */
object ChatEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean(false)

    private lateinit var repository: ChatRepository
    private lateinit var qwen: QwenChatClient
    private lateinit var ttsService: AliyunTtsService
    private val audioPlayer = ChatAudioPlayer()

    /** 初始化对话引擎（全局单例）。 */
    fun init(context: Context) {
        if (initialized.getAndSet(true)) return
        ChatStore.init(context.applicationContext)
        repository = ChatRepository()
        qwen = QwenChatClient()
        ttsService = AliyunTtsService(context.applicationContext)
        Log.i(TAG, "ChatEngine initialized with Aliyun TTS")
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

    /**
     * 播放消息的音频：
     * - 如果有 audioPath 缓存文件，直接 MediaPlayer 播放
     * - 如果没有缓存，先调用阿里云 TTS 合成并缓存，再播放
     */
    fun replayMessage(message: ChatMessageEntity) {
        val path = message.audioPath
        if (!path.isNullOrBlank() && java.io.File(path).exists()) {
            Log.i(TAG, "replay from cache: $path")
            audioPlayer.play(path)
        } else {
            Log.i(TAG, "replay: no cache, synthesizing for ${message.id}")
            scope.launch {
                val audioPath = ttsService.synthesizeToFile(message.content, message.id)
                if (audioPath != null) {
                    // Update message with cached audioPath
                    val updated = message.copy(audioPath = audioPath)
                    repository.update(updated)
                    audioPlayer.play(audioPath)
                    Log.i(TAG, "replay synthesized & cached: $audioPath")
                } else {
                    Log.e(TAG, "replay TTS failed for ${message.id}")
                }
            }
        }
    }

    /** 短文本语音引导（页面切换等），不保存文件。 直接调用 TTS 合成并播放临时文件。 */
    fun replayVoiceGuide(text: String) {
        scope.launch {
            val id = "guide_${System.currentTimeMillis()}"
            val path = ttsService.synthesizeToFile(text, id)
            if (path != null) {
                audioPlayer.play(path)
            }
        }
    }

    /** 停止播放。 */
    fun stopSpeaking() {
        audioPlayer.stop()
    }

    /** 调用大模型并生成回复，然后 TTS 合成并播放。 */
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
        val messageId = UUID.randomUUID().toString()

        // Synthesize TTS audio and cache it
        val audioPath = ttsService.synthesizeToFile(reply, messageId)
        Log.i(TAG_FLOW, "tts audioPath=$audioPath")

        val assistantMessage =
                ChatMessageEntity(
                        id = messageId,
                        role = ChatRoles.ASSISTANT,
                        content = reply,
                        createdAt = now,
                        status = ChatStatus.OK,
                        source = ChatSource.SYSTEM,
                        audioPath = audioPath,
                )

        repository.insert(assistantMessage)
        // ChatPlaybackManager will auto-play since audioPath is set
    }

    private const val TAG = "ChatEngine"
    private const val TAG_FLOW = "ChatFlow"
}
