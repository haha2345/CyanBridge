package com.fersaiyan.cyanbridge.chat

/**
 * 对话消息模型：
 * - role: user/assistant
 * - audioPath: TTS 生成的本地音频路径
 */
data class ChatMessageEntity(
    val id: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val status: String,
    val source: String,
    val audioPath: String? = null,
    val audioDurationMs: Long? = null,
    val metaJson: String? = null,
)
