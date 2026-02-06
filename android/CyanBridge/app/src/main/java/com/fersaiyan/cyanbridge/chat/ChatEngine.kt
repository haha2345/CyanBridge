package com.fersaiyan.cyanbridge.chat

import android.content.Context
import android.util.Log
import com.fersaiyan.cyanbridge.ai.QwenChatClient
import com.fersaiyan.cyanbridge.voice.AliyunTtsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 语音对话核心引擎：
 * - 统一接收文本（ASR/输入）
 * - 调用大模型（Qwen-Flash）
 * - 触发阿里云 TTS
 * - 写入对话存储并更新音频路径
 */
object ChatEngine {
    // Background scope for network + disk IO.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean(false)

    private lateinit var repository: ChatRepository
    private lateinit var qwen: QwenChatClient
    private lateinit var tts: AliyunTtsService

    /**
     * 初始化对话引擎（全局单例）。
     * 在 Application 中调用一次即可。
     */
    fun init(context: Context) {
        if (initialized.getAndSet(true)) return
        ChatStore.init(context.applicationContext)
        repository = ChatRepository()
        qwen = QwenChatClient()
        tts = AliyunTtsService(context.applicationContext)
    }

    /**
     * 提交用户文本（可直接传 ASR JSON）。
     * 内部会进行 JSON 归一化并启动对话流程。
     */
    fun submitUserText(rawText: String, source: String = ChatSource.TEXT) {
        // Normalize ASR JSON here so callers can pass raw text.
        val text = ChatTextParser.normalize(rawText) ?: return
        Log.i(TAG_FLOW, "submitUserText source=$source raw=${rawText.take(120)} normalized=${text.take(120)}")
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
     * 调用大模型并生成回复，同时触发 TTS 合成并更新音频路径。
     */
    private suspend fun generateAssistantReply(userText: String) {
        // 1) Call LLM, 2) persist assistant text, 3) synthesize TTS, 4) update audioPath.
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
        val assistantId = UUID.randomUUID().toString()
        val assistantMessage =
            ChatMessageEntity(
                id = assistantId,
                role = ChatRoles.ASSISTANT,
                content = reply,
                createdAt = now,
                status = ChatStatus.OK,
                source = ChatSource.SYSTEM,
            )

        repository.insert(assistantMessage)

        Log.i(TAG_TTS, "tts start messageId=$assistantId")
        val audioPath =
            try {
                tts.synthesizeToFile(reply, assistantId)
            } catch (t: Throwable) {
                Log.e(TAG, "TTS failed", t)
                null
            }

        if (!audioPath.isNullOrBlank()) {
            Log.i(TAG_TTS, "tts ok messageId=$assistantId path=$audioPath")
            repository.update(assistantMessage.copy(audioPath = audioPath))
        } else {
            Log.e(TAG_TTS, "tts failed messageId=$assistantId")
        }
    }

    private const val TAG = "ChatEngine"
    private const val TAG_FLOW = "ChatFlow"
    private const val TAG_TTS = "ChatTts"
}
