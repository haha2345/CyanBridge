package com.fersaiyan.cyanbridge.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fersaiyan.cyanbridge.media.MediaSyncManager
import com.fersaiyan.cyanbridge.ui.accessibility.LargeTouchButton
import com.fersaiyan.cyanbridge.ui.accessibility.isBlindMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(mediaVm: MediaViewModel = viewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("图片", "视频", "录音")
    val tabIcons = listOf(Icons.Filled.Image, Icons.Filled.Videocam, Icons.Filled.MicNone)
    val blind = isBlindMode()
    val context = LocalContext.current

    val syncState by mediaVm.syncState.collectAsState()
    val downloadedFiles by mediaVm.downloadedFiles.collectAsState()
    val isConnected by mediaVm.isConnected.collectAsState()

    // Filter files by selected tab
    val filteredFiles =
            remember(downloadedFiles, selectedTab) {
                val type =
                        when (selectedTab) {
                            0 -> MediaSyncManager.MediaType.PHOTO
                            1 -> MediaSyncManager.MediaType.VIDEO
                            else -> MediaSyncManager.MediaType.AUDIO
                        }
                downloadedFiles.filter { it.type == type }
            }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .padding(horizontal = if (blind) 16.dp else 24.dp, vertical = 24.dp)
    ) {
        // ── Title ──
        Text(
                text = "媒体库",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { contentDescription = "媒体库页面" }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Sync Button ──
        SyncButton(
                syncState = syncState,
                isConnected = isConnected,
                blind = blind,
                onStart = { mediaVm.startSync() },
                onCancel = { mediaVm.cancelSync() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Sync Status ──
        SyncStatusBar(syncState)

        Spacer(modifier = Modifier.height(16.dp))

        if (blind) {
            // Blind mode: large touch buttons
            tabs.forEachIndexed { index, title ->
                val count =
                        downloadedFiles.count {
                            it.type ==
                                    when (index) {
                                        0 -> MediaSyncManager.MediaType.PHOTO
                                        1 -> MediaSyncManager.MediaType.VIDEO
                                        else -> MediaSyncManager.MediaType.AUDIO
                                    }
                        }
                LargeTouchButton(
                        icon = tabIcons[index],
                        title = "$title ($count)",
                        description = if (count > 0) "有${count}个${title}文件" else "暂无${title}",
                        onClick = { selectedTab = index },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        } else {
            // Normal mode: Tab Row
            TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    val count =
                            downloadedFiles.count {
                                it.type ==
                                        when (index) {
                                            0 -> MediaSyncManager.MediaType.PHOTO
                                            1 -> MediaSyncManager.MediaType.VIDEO
                                            else -> MediaSyncManager.MediaType.AUDIO
                                        }
                            }
                    Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text("$title ($count)") },
                            modifier =
                                    Modifier.semantics {
                                        contentDescription = "$title 分类，共${count}项"
                                    }
                    )
                }
            }

            // ── Content ──
            if (filteredFiles.isEmpty()) {
                // Empty state
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                                imageVector = tabIcons[selectedTab],
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                                text = "暂无${tabs[selectedTab]}",
                                style = MaterialTheme.typography.bodyLarge,
                                color =
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.6f
                                        )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                                text = if (isConnected) "点击\"同步\"从眼镜下载" else "连接眼镜后可同步",
                                style = MaterialTheme.typography.bodyMedium,
                                color =
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.4f
                                        )
                        )
                    }
                }
            } else {
                // File grid
                LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredFiles) { file ->
                        MediaFileCard(file) {
                            // Open file with system default app
                            try {
                                val uri = Uri.parse(file.localUri)
                                val mime =
                                        when (file.type) {
                                            MediaSyncManager.MediaType.PHOTO -> "image/jpeg"
                                            MediaSyncManager.MediaType.VIDEO -> "video/mp4"
                                            MediaSyncManager.MediaType.AUDIO -> "audio/ogg"
                                        }
                                val intent =
                                        Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, mime)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncButton(
        syncState: MediaSyncManager.SyncState,
        isConnected: Boolean,
        blind: Boolean,
        onStart: () -> Unit,
        onCancel: () -> Unit,
) {
    val isSyncing =
            syncState is MediaSyncManager.SyncState.Connecting ||
                    syncState is MediaSyncManager.SyncState.Syncing

    if (blind) {
        LargeTouchButton(
                icon = if (isSyncing) Icons.Filled.Cancel else Icons.Filled.Sync,
                title = if (isSyncing) "取消同步" else "同步眼镜媒体",
                description =
                        when {
                            !isConnected -> "请先连接眼镜"
                            isSyncing -> "正在同步中..."
                            else -> "从眼镜下载所有照片视频录音"
                        },
                onClick = { if (isSyncing) onCancel() else onStart() },
        )
    } else {
        Button(
                onClick = { if (isSyncing) onCancel() else onStart() },
                enabled = isConnected || isSyncing,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors =
                        if (isSyncing)
                                ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                )
                        else ButtonDefaults.buttonColors()
        ) {
            Icon(
                    imageVector = if (isSyncing) Icons.Filled.Cancel else Icons.Filled.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                    text = if (isSyncing) "取消同步" else "同步眼镜媒体",
                    style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
private fun SyncStatusBar(syncState: MediaSyncManager.SyncState) {
    when (syncState) {
        is MediaSyncManager.SyncState.Idle -> {}
        is MediaSyncManager.SyncState.Connecting -> {
            Column {
                Text(
                        "正在连接眼镜 WiFi...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        is MediaSyncManager.SyncState.Syncing -> {
            Column {
                Text(
                        "下载中 ${syncState.current}/${syncState.total}: ${syncState.fileName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                        progress = { syncState.current.toFloat() / syncState.total },
                        modifier = Modifier.fillMaxWidth()
                )
            }
        }
        is MediaSyncManager.SyncState.Done -> {
            val msg =
                    if (syncState.failed == 0) "✓ 同步完成：${syncState.success} 个文件"
                    else "同步完成：${syncState.success} 成功，${syncState.failed} 失败"
            Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                            if (syncState.failed == 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
            )
        }
        is MediaSyncManager.SyncState.Error -> {
            Text(
                    "❌ ${syncState.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun MediaFileCard(file: MediaSyncManager.MediaFileItem, onClick: () -> Unit) {
    val icon: ImageVector =
            when (file.type) {
                MediaSyncManager.MediaType.PHOTO -> Icons.Filled.Image
                MediaSyncManager.MediaType.VIDEO -> Icons.Filled.Videocam
                MediaSyncManager.MediaType.AUDIO -> Icons.Filled.MicNone
            }

    Card(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { onClick() },
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
    ) {
        Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            Icon(
                    imageVector = icon,
                    contentDescription = file.name,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                    text = file.name.substringBeforeLast('.'),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
            )
        }
    }
}
