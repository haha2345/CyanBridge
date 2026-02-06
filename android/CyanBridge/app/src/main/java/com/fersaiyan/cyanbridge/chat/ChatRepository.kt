package com.fersaiyan.cyanbridge.chat

import kotlinx.coroutines.flow.StateFlow

/**
 * 对话数据仓库：对 ChatStore 的轻量封装。
 */
class ChatRepository {
    /**
     * 插入消息。
     */
    suspend fun insert(message: ChatMessageEntity) = ChatStore.insert(message)

    /**
     * 更新消息（例如补齐 audioPath）。
     */
    suspend fun update(message: ChatMessageEntity) = ChatStore.update(message)

    /**
     * 观察消息列表。
     */
    fun observeAll(): StateFlow<List<ChatMessageEntity>> = ChatStore.observe()

    /**
     * 清空全部消息。
     */
    suspend fun clearAll() = ChatStore.clearAll()
}
