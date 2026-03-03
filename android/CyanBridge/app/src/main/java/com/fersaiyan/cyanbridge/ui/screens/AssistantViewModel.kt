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
 * - 消息重播 (Android TTS)
 * - 清空历史
 *
 * 注意：ASR 录音由 MainActivity 的 aliyunAsr 统一管理， 语音唤醒和麦克风按钮都通过 MainActivity 路由。
 */
class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    val messages: StateFlow<List<ChatMessageEntity>> = ChatStore.observe()

    /** 手动发送文字。 */
    fun sendText(text: String) {
        if (text.isBlank()) return
        ChatEngine.submitUserText(text, ChatSource.TEXT)
    }

    /** 用 Android TTS 朗读一条消息。 */
    fun replayMessage(message: ChatMessageEntity) {
        ChatEngine.speakText(message.content)
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
