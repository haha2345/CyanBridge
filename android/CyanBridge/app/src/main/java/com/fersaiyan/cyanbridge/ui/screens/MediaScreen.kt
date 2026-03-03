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
        val mediaCount by mediaVm.mediaCount.collectAsState()

        // Auto-query count when connected
        LaunchedEffect(isConnected) {
                if (isConnected && mediaCount == null) {
                        mediaVm.queryMediaCount()
                }
        }

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
                // ── Title Row ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                                text = "媒体库",
                                style =
                                        MaterialTheme.typography.headlineMedium.copy(
                                                fontWeight = FontWeight.Bold
                                        ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier =
                                        Modifier.weight(1f).semantics {
                                                contentDescription = "媒体库页面"
                                        }
                        )
                        // Refresh count button
                        if (isConnected) {
                                IconButton(onClick = { mediaVm.queryMediaCount() }) {
                                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                                }
                        }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Media Count Display ──
                MediaCountCard(mediaCount, syncState)

                Spacer(modifier = Modifier.height(12.dp))

                // ── Action Buttons ──
                ActionArea(
                        syncState = syncState,
                        isConnected = isConnected,
                        mediaCount = mediaCount,
                        blind = blind,
                        onQuery = { mediaVm.queryMediaCount() },
                        onDownload = { mediaVm.startSync() },
                        onCancel = { mediaVm.cancelSync() }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ── Sync Status ──
                SyncStatusBar(syncState)

                Spacer(modifier = Modifier.height(12.dp))

                if (blind) {
                        tabs.forEachIndexed { index, title ->
                                val count =
                                        downloadedFiles.count {
                                                it.type ==
                                                        when (index) {
                                                                0 ->
                                                                        MediaSyncManager.MediaType
                                                                                .PHOTO
                                                                1 ->
                                                                        MediaSyncManager.MediaType
                                                                                .VIDEO
                                                                else ->
                                                                        MediaSyncManager.MediaType
                                                                                .AUDIO
                                                        }
                                        }
                                LargeTouchButton(
                                        icon = tabIcons[index],
                                        title = "$title ($count)",
                                        description =
                                                if (count > 0) "有${count}个${title}文件"
                                                else "暂无${title}",
                                        onClick = { selectedTab = index },
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                        }
                } else {
                        // Tab Row
                        TabRow(
                                selectedTabIndex = selectedTab,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                                tabs.forEachIndexed { index, title ->
                                        val count =
                                                downloadedFiles.count {
                                                        it.type ==
                                                                when (index) {
                                                                        0 ->
                                                                                MediaSyncManager
                                                                                        .MediaType
                                                                                        .PHOTO
                                                                        1 ->
                                                                                MediaSyncManager
                                                                                        .MediaType
                                                                                        .VIDEO
                                                                        else ->
                                                                                MediaSyncManager
                                                                                        .MediaType
                                                                                        .AUDIO
                                                                }
                                                }
                                        Tab(
                                                selected = selectedTab == index,
                                                onClick = { selectedTab = index },
                                                text = { Text("$title ($count)") },
                                                modifier =
                                                        Modifier.semantics {
                                                                contentDescription = "$title 分类"
                                                        }
                                        )
                                }
                        }

                        // Content
                        if (filteredFiles.isEmpty()) {
                                Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                        imageVector = tabIcons[selectedTab],
                                                        contentDescription = null,
                                                        modifier = Modifier.size(64.dp),
                                                        tint =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant.copy(
                                                                        alpha = 0.4f
                                                                )
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                        text = "暂无已下载的${tabs[selectedTab]}",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant.copy(
                                                                        alpha = 0.6f
                                                                )
                                                )
                                        }
                                }
                        } else {
                                LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = 100.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxSize()
                                ) {
                                        items(filteredFiles) { file ->
                                                MediaFileCard(file) {
                                                        try {
                                                                val uri = Uri.parse(file.localUri)
                                                                val mime =
                                                                        when (file.type) {
                                                                                MediaSyncManager
                                                                                        .MediaType
                                                                                        .PHOTO ->
                                                                                        "image/jpeg"
                                                                                MediaSyncManager
                                                                                        .MediaType
                                                                                        .VIDEO ->
                                                                                        "video/mp4"
                                                                                MediaSyncManager
                                                                                        .MediaType
                                                                                        .AUDIO ->
                                                                                        "audio/ogg"
                                                                        }
                                                                val intent =
                                                                        Intent(Intent.ACTION_VIEW)
                                                                                .apply {
                                                                                        setDataAndType(
                                                                                                uri,
                                                                                                mime
                                                                                        )
                                                                                        addFlags(
                                                                                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                                                        )
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
private fun MediaCountCard(
        count: MediaSyncManager.MediaCount?,
        syncState: MediaSyncManager.SyncState
) {
        if (count == null && syncState !is MediaSyncManager.SyncState.Querying) return

        Card(
                colors =
                        CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                modifier = Modifier.fillMaxWidth()
        ) {
                if (syncState is MediaSyncManager.SyncState.Querying) {
                        Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("正在查询眼镜文件数量...", style = MaterialTheme.typography.bodyMedium)
                        }
                } else if (count != null) {
                        Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                                CountItem("📷", "照片", count.images)
                                CountItem("🎬", "视频", count.videos)
                                CountItem("🎙", "录音", count.records)
                        }
                }
        }
}

@Composable
private fun CountItem(emoji: String, label: String, count: Int) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(emoji, style = MaterialTheme.typography.headlineSmall)
                Text(
                        "$count",
                        style =
                                MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                )
                )
                Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
        }
}

@Composable
private fun ActionArea(
        syncState: MediaSyncManager.SyncState,
        isConnected: Boolean,
        mediaCount: MediaSyncManager.MediaCount?,
        blind: Boolean,
        onQuery: () -> Unit,
        onDownload: () -> Unit,
        onCancel: () -> Unit,
) {
        val isSyncing =
                syncState is MediaSyncManager.SyncState.Connecting ||
                        syncState is MediaSyncManager.SyncState.Syncing
        val hasFiles = mediaCount != null && mediaCount.total > 0

        if (blind) {
                when {
                        isSyncing ->
                                LargeTouchButton(
                                        icon = Icons.Filled.Cancel,
                                        title = "取消下载",
                                        description = "正在下载中...",
                                        onClick = onCancel
                                )
                        hasFiles ->
                                LargeTouchButton(
                                        icon = Icons.Filled.Download,
                                        title = "下载全部 (${mediaCount!!.total}个)",
                                        description =
                                                "照片${mediaCount.images} 视频${mediaCount.videos} 录音${mediaCount.records}",
                                        onClick = onDownload
                                )
                        !isConnected ->
                                LargeTouchButton(
                                        icon = Icons.Filled.SyncDisabled,
                                        title = "未连接眼镜",
                                        description = "请先在首页连接眼镜",
                                        onClick = {}
                                )
                        else ->
                                LargeTouchButton(
                                        icon = Icons.Filled.Search,
                                        title = "查询眼镜文件",
                                        description = "查看眼镜里有多少文件",
                                        onClick = onQuery
                                )
                }
        } else {
                when {
                        isSyncing ->
                                Button(
                                        onClick = onCancel,
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme.error
                                                )
                                ) {
                                        Icon(Icons.Filled.Cancel, null, Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("取消下载")
                                }
                        hasFiles ->
                                Button(
                                        onClick = onDownload,
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                        Icon(Icons.Filled.Download, null, Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("下载全部 (${mediaCount!!.total}个文件)")
                                }
                        !isConnected ->
                                OutlinedButton(
                                        onClick = {},
                                        enabled = false,
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) { Text("请先连接眼镜") }
                        else ->
                                OutlinedButton(
                                        onClick = onQuery,
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                        Icon(Icons.Filled.Search, null, Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("查询眼镜文件")
                                }
                }
        }
}

@Composable
private fun SyncStatusBar(syncState: MediaSyncManager.SyncState) {
        when (syncState) {
                is MediaSyncManager.SyncState.Idle,
                is MediaSyncManager.SyncState.Querying,
                is MediaSyncManager.SyncState.Counted -> {}
                is MediaSyncManager.SyncState.Connecting -> {
                        Column {
                                Text(
                                        "正在建立 WiFi 连接...",
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
                                        progress = {
                                                syncState.current.toFloat() / syncState.total
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                )
                        }
                }
                is MediaSyncManager.SyncState.Done -> {
                        val msg =
                                if (syncState.failed == 0) "✓ 完成：${syncState.success} 个文件"
                                else "完成：${syncState.success} 成功，${syncState.failed} 失败"
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
        val icon =
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
                                icon,
                                file.name,
                                Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
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
