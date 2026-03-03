package com.fersaiyan.cyanbridge.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.ui.accessibility.LargeTouchButton
import com.fersaiyan.cyanbridge.ui.accessibility.isBlindMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("图片", "视频", "录音")
    val tabIcons = listOf(Icons.Filled.Image, Icons.Filled.Videocam, Icons.Filled.MicNone)
    val blind = isBlindMode()

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
                modifier = Modifier.semantics { contentDescription = "媒体库页面，浏览照片视频录音" }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (blind) {
            // Blind mode: large touch buttons for each category
            tabs.forEachIndexed { index, title ->
                LargeTouchButton(
                        icon = tabIcons[index],
                        title = title,
                        description = "暂无${title}，连接眼镜后内容会显示在这里",
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
                    Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) },
                            modifier = Modifier.semantics { contentDescription = "$title 分类，共0项" }
                    )
                }
            }

            // ── Empty State ──
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                            text = "连接眼镜后拍摄的内容将显示在这里",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}
