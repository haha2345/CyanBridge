package com.fersaiyan.cyanbridge.chat

/**
 * 对话角色常量。
 */
object ChatRoles {
    const val USER = "user"
    const val ASSISTANT = "assistant"
}

/**
 * 消息状态常量。
 */
object ChatStatus {
    const val OK = "ok"
    const val ERROR = "error"
    const val PENDING = "pending"
}

/**
 * 消息来源常量（输入/ASR/系统等）。
 */
object ChatSource {
    const val TEXT = "text"
    const val ASR = "asr"
    const val IMAGE = "image"
    const val SYSTEM = "system"
}
