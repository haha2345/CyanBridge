package com.fersaiyan.cyanbridge.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import com.fersaiyan.cyanbridge.chat.ChatEngine
import com.fersaiyan.cyanbridge.chat.ChatRepository
import com.fersaiyan.cyanbridge.chat.ChatSource

/**
 * 对话页面 ViewModel：
 * - 提供消息列表
 * - 发送输入文本
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository()

    // 将 StateFlow 转为 LiveData 供 Activity 观察。
    val messages = repository.observeAll().asLiveData()

    /**
     * 发送文本消息。
     */
    fun sendText(text: String) {
        ChatEngine.submitUserText(text, ChatSource.TEXT)
    }
}
