package com.fersaiyan.cyanbridge.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.fersaiyan.cyanbridge.glasses.GlassesRepository
import com.fersaiyan.cyanbridge.media.MediaSyncManager
import kotlinx.coroutines.flow.StateFlow

/**
 * MediaScreen 的 ViewModel：暴露媒体计数和同步状态。
 *
 * Two-step flow:
 * 1. queryMediaCount() → BLE 即时查询眼镜文件数量
 * 2. startSync() → WiFi P2P 下载所有文件
 */
class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val syncManager = MediaSyncManager.getInstance(application)
    private val glassesRepo = GlassesRepository.getInstance(application)

    val syncState: StateFlow<MediaSyncManager.SyncState> = syncManager.syncState
    val downloadedFiles: StateFlow<List<MediaSyncManager.MediaFileItem>> =
            syncManager.downloadedFiles
    val mediaCount: StateFlow<MediaSyncManager.MediaCount?> = syncManager.mediaCount
    val isConnected: StateFlow<Boolean> = glassesRepo.connectionState

    /** Step 1: BLE instant query (no WiFi) */
    fun queryMediaCount() = syncManager.queryMediaCount()

    /** Step 2: WiFi P2P download all files */
    fun startSync() = syncManager.startSync()

    fun cancelSync() = syncManager.cancelSync()
}
