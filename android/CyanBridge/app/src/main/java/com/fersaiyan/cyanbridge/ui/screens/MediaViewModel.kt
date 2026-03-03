package com.fersaiyan.cyanbridge.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.fersaiyan.cyanbridge.glasses.GlassesRepository
import com.fersaiyan.cyanbridge.media.MediaSyncManager
import kotlinx.coroutines.flow.StateFlow

/** MediaScreen 的 ViewModel：暴露同步状态和已下载文件列表。 */
class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val syncManager = MediaSyncManager.getInstance(application)
    private val glassesRepo = GlassesRepository.getInstance(application)

    val syncState: StateFlow<MediaSyncManager.SyncState> = syncManager.syncState
    val downloadedFiles: StateFlow<List<MediaSyncManager.MediaFileItem>> =
            syncManager.downloadedFiles
    val isConnected: StateFlow<Boolean> = glassesRepo.connectionState

    fun startSync() = syncManager.startSync()
    fun cancelSync() = syncManager.cancelSync()
}
