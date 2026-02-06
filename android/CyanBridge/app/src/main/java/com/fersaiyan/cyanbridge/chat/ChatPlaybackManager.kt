package com.fersaiyan.cyanbridge.chat

import android.util.Log
import com.fersaiyan.cyanbridge.ui.chat.ChatAudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 全局自动播报管理：
 * - 监听 ChatStore
 * - 当 assistant 消息出现音频时立即播放
 */
object ChatPlaybackManager {
    // Use Main dispatcher so MediaPlayer callbacks run on UI thread.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val started = AtomicBoolean(false)
    private val audioPlayer = ChatAudioPlayer()

    private var initialized = false
    private var lastPlayedAudioPath: String? = null

    /**
     * 启动全局播放监听（建议在 Application 中调用）。
     */
    fun start() {
        if (started.getAndSet(true)) return
        scope.launch {
            ChatStore.observe().collectLatest { list ->
                handleMessages(list)
            }
        }
    }

    /**
     * 处理消息列表变化并决定是否自动播放。
     */
    private fun handleMessages(list: List<ChatMessageEntity>) {
        // Auto-play latest assistant audio anywhere in the app (not just ChatActivity).
        val latestWithAudio = list.lastOrNull {
            it.role == ChatRoles.ASSISTANT && !it.audioPath.isNullOrBlank()
        }
        if (!initialized) {
            // Skip history on first load to avoid replaying old messages.
            initialized = true
            lastPlayedAudioPath = latestWithAudio?.audioPath
            if (latestWithAudio != null) {
                Log.i(TAG, "autoPlay init skip messageId=${latestWithAudio.id}")
            }
            return
        }
        val path = latestWithAudio?.audioPath ?: return
        if (path == lastPlayedAudioPath) return
        Log.i(TAG, "autoPlay messageId=${latestWithAudio.id} path=$path")
        audioPlayer.play(path)
        lastPlayedAudioPath = path
    }

    /**
     * 停止当前播放（用于退出/清理）。
     */
    fun stop() {
        audioPlayer.stop()
    }

    private const val TAG = "ChatAudio"
}
