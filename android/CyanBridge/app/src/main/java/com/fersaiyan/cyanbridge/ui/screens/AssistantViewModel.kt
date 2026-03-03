package com.fersaiyan.cyanbridge.ui.screens

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fersaiyan.cyanbridge.chat.ChatEngine
import com.fersaiyan.cyanbridge.chat.ChatMessageEntity
import com.fersaiyan.cyanbridge.chat.ChatSource
import com.fersaiyan.cyanbridge.chat.ChatStore
import com.fersaiyan.cyanbridge.voice.AliyunAsrWakeSession
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.communication.LargeDataHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 语音助手 ViewModel：
 * - 消息列表 (ChatStore)
 * - ASR 录音状态
 * - 手动输入
 * - 消息重播 (Android TTS)
 */
class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    val messages: StateFlow<List<ChatMessageEntity>> = ChatStore.observe()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private lateinit var aliyunAsr: AliyunAsrWakeSession

    init {
        aliyunAsr =
                AliyunAsrWakeSession(
                        context = application.applicationContext,
                        onResult = { asrText ->
                            Log.i(TAG, "ASR result: $asrText")
                            _isListening.value = false
                            _statusText.value = ""
                            ChatEngine.submitUserText(asrText, ChatSource.ASR)
                        },
                        onStatus = { status ->
                            Log.i(TAG, "ASR status: $status")
                            _statusText.value = status
                            if (!aliyunAsr.isRunning()) {
                                _isListening.value = false
                            }
                        }
                )
    }

    /** 切换 ASR 录音。 */
    fun toggleListening() {
        if (_isListening.value) {
            stopListening()
        } else {
            startListening()
        }
    }

    /** 开始 ASR 录音（需要先启动眼镜语音流 0x07）。 */
    fun startListening() {
        if (!BleOperateManager.getInstance().isConnected) {
            _statusText.value = "眼镜未连接"
            return
        }

        _isListening.value = true
        _statusText.value = "正在聆听..."

        // Start BLE voice stream, then ASR
        LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x07)) { _, _ -> }
        aliyunAsr.start("mic_button")
    }

    /** 停止 ASR 录音。 */
    fun stopListening() {
        _isListening.value = false
        _statusText.value = ""
        aliyunAsr.stop("user_stop")
    }

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
        aliyunAsr.shutdown()
        ChatEngine.stopSpeaking()
    }

    companion object {
        private const val TAG = "AssistantVM"
    }
}
