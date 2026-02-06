package com.fersaiyan.cyanbridge.ui.chat

import android.media.MediaPlayer
import android.util.Log
import java.io.File

/**
 * 简单音频播放器（用于播放 TTS wav 文件）。
 */
class ChatAudioPlayer {
    private var player: MediaPlayer? = null

    /**
     * 播放指定本地音频路径。
     */
    fun play(path: String) {
        val file = File(path)
        if (!file.exists()) {
            Log.w(TAG, "Audio file missing: $path")
            return
        }
        stop()
        val mp = MediaPlayer()
        try {
            mp.setDataSource(path)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { stop() }
            mp.setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            mp.prepareAsync()
            player = mp
        } catch (t: Throwable) {
            Log.e(TAG, "Play failed", t)
            mp.release()
        }
    }

    /**
     * 停止播放并释放资源。
     */
    fun stop() {
        val mp = player ?: return
        try {
            mp.stop()
        } catch (_: Throwable) {
        }
        mp.release()
        player = null
    }

    companion object {
        private const val TAG = "ChatAudioPlayer"
    }
}
