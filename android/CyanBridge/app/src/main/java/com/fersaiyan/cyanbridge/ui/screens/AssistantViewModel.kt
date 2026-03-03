package com.fersaiyan.cyanbridge.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fersaiyan.cyanbridge.chat.ChatEngine
import com.fersaiyan.cyanbridge.chat.ChatMessageEntity
import com.fersaiyan.cyanbridge.chat.ChatSource
import com.fersaiyan.cyanbridge.chat.ChatStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 语音助手 ViewModel：
 * - 观察 ChatStore 消息列表
 * - 手动输入文字
 * - 消息重播 (阿里云 TTS，带缓存)
 * - 清空历史
 */
class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    val messages: StateFlow<List<ChatMessageEntity>> = ChatStore.observe()

    /** 手动发送文字。 */
    fun sendText(text: String) {
        if (text.isBlank()) return
        ChatEngine.submitUserText(text, ChatSource.TEXT)
    }

    /** 重播消息：有缓存直接播放，否则合成后缓存。 */
    fun replayMessage(message: ChatMessageEntity) {
        ChatEngine.replayMessage(message)
    }

    /** 清空对话历史。 */
    fun clearHistory() {
        viewModelScope.launch { ChatStore.clearAll() }
    }

    override fun onCleared() {
        super.onCleared()
        ChatEngine.stopSpeaking()
    }
}
