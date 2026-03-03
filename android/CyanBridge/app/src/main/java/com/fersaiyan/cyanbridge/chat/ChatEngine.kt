package com.fersaiyan.cyanbridge.chat

import android.content.Context
import android.util.Log
import com.fersaiyan.cyanbridge.ai.DashScopeVisionClient
import com.fersaiyan.cyanbridge.ai.QwenChatClient
import com.fersaiyan.cyanbridge.glasses.GlassesRepository
import com.fersaiyan.cyanbridge.ui.chat.ChatAudioPlayer
import com.fersaiyan.cyanbridge.vision.VisionInterceptor
import com.fersaiyan.cyanbridge.voice.AliyunTtsService
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val visionClient = DashScopeVisionClient()
    private lateinit var appContext: Context

    /** 初始化对话引擎（全局单例）。 */
    fun init(context: Context) {
        if (initialized.getAndSet(true)) return
        appContext = context.applicationContext
        ChatStore.init(appContext)
        repository = ChatRepository()
        qwen = QwenChatClient()
        ttsService = AliyunTtsService(appContext)
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
            val id = java.util.UUID.randomUUID().toString()
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
        // ── 视觉指令分流 ──
        val visionMode = VisionInterceptor.isVisionCommand(userText)
        if (visionMode != null) {
            Log.i(TAG_FLOW, "Vision command detected: mode=$visionMode")
            handleVisionRequest(userText, visionMode)
            return
        }

        // ── 普通文字对话 ──
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

    /** 处理视觉请求：拍照 → VL 分析 → TTS 朗读。 */
    private suspend fun handleVisionRequest(userText: String, mode: VisionInterceptor.VisionMode) {
        val glassesRepo = GlassesRepository.getInstance(appContext)

        // 1. 语音提示：正在拍照
        val hintId = UUID.randomUUID().toString()
        val hintText = "正在拍照识别，请稍候"
        val hintMsg =
                ChatMessageEntity(
                        id = hintId,
                        role = ChatRoles.ASSISTANT,
                        content = hintText,
                        createdAt = System.currentTimeMillis(),
                        status = ChatStatus.OK,
                        source = ChatSource.SYSTEM,
                )
        repository.insert(hintMsg)
        // Quick TTS hint
        val hintAudio = ttsService.synthesizeToFile(hintText, hintId)
        if (hintAudio != null) {
            withContext(Dispatchers.Main) { audioPlayer.play(hintAudio) }
            // Brief wait for hint to finish
            kotlinx.coroutines.delay(1500)
        }

        // 2. 眼镜拍照 + 获取缩略图
        Log.i(TAG_FLOW, "Vision: capturing thumbnail...")
        val imageFile = glassesRepo.captureAndGetThumbnail()

        val now = System.currentTimeMillis()
        val messageId = UUID.randomUUID().toString()

        if (imageFile == null) {
            Log.e(TAG_FLOW, "Vision: thumbnail capture failed")
            val errText = "拍照失败，请确认眼镜已连接并重试"
            val errAudio = ttsService.synthesizeToFile(errText, messageId)
            val errMsg =
                    ChatMessageEntity(
                            id = messageId,
                            role = ChatRoles.ASSISTANT,
                            content = errText,
                            createdAt = now,
                            status = ChatStatus.ERROR,
                            source = ChatSource.SYSTEM,
                            audioPath = errAudio,
                    )
            repository.insert(errMsg)
            return
        }

        // 3. Qwen-VL 分析
        Log.i(TAG_FLOW, "Vision: analyzing image with Qwen-VL...")
        val prompt = VisionInterceptor.buildPrompt(mode, userText)
        val reply =
                try {
                    withContext(Dispatchers.IO) {
                        visionClient.describeImage(imageFile, prompt).trim()
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Vision VL analysis failed", t)
                    null
                }

        if (reply.isNullOrBlank()) {
            val errText = "图片分析失败，请稍后重试"
            val errAudio = ttsService.synthesizeToFile(errText, messageId)
            val errMsg =
                    ChatMessageEntity(
                            id = messageId,
                            role = ChatRoles.ASSISTANT,
                            content = errText,
                            createdAt = now,
                            status = ChatStatus.ERROR,
                            source = ChatSource.SYSTEM,
                            audioPath = errAudio,
                    )
            repository.insert(errMsg)
            return
        }

        // 4. TTS 朗读分析结果
        Log.i(TAG_FLOW, "Vision: reply len=${reply.length}")
        val audioPath = ttsService.synthesizeToFile(reply, messageId)

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

        // Clean up temp image
        try {
            imageFile.delete()
        } catch (_: Exception) {}
    }

    private const val TAG = "ChatEngine"
    private const val TAG_FLOW = "ChatFlow"
}
