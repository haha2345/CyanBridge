package com.fersaiyan.cyanbridge.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.ui.accessibility.AccessibilityPrefs
import com.fersaiyan.cyanbridge.ui.accessibility.LargeTouchButton
import com.fersaiyan.cyanbridge.ui.accessibility.isBlindMode

@Composable
fun SettingsScreen() {
    val blindMode by AccessibilityPrefs.blindMode.collectAsState()
    val highContrast by AccessibilityPrefs.highContrast.collectAsState()
    val largeText by AccessibilityPrefs.largeText.collectAsState()
    val voiceGuide by AccessibilityPrefs.voiceGuide.collectAsState()
    val isBlind = isBlindMode()

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = if (isBlind) 16.dp else 24.dp, vertical = 24.dp)
    ) {
        // ── Title ──
        Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { contentDescription = "设置页面" }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Accessibility Section ──
        SectionTitle("无障碍")

        if (isBlind) {
            // Blind mode: large touch buttons
            LargeTouchButton(
                    icon = Icons.Filled.Accessibility,
                    title = "全盲模式",
                    description = if (blindMode) "已开启" else "已关闭",
                    onClick = { AccessibilityPrefs.setBlindMode(!blindMode) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            LargeTouchButton(
                    icon = Icons.Filled.RecordVoiceOver,
                    title = "语音引导",
                    description = if (voiceGuide) "已开启" else "已关闭",
                    onClick = { AccessibilityPrefs.setVoiceGuide(!voiceGuide) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            LargeTouchButton(
                    icon = Icons.Filled.Bluetooth,
                    title = "眼镜管理",
                    description = "配对、连接",
                    onClick = { /* TODO */},
            )
            Spacer(modifier = Modifier.height(12.dp))
            LargeTouchButton(
                    icon = Icons.Filled.Info,
                    title = "关于 CyanBridge",
                    description = "版本 1.0.2",
                    onClick = { /* TODO */},
            )
        } else {
            // Normal mode: cards with toggles
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsToggleItem(
                            icon = Icons.Filled.Accessibility,
                            title = "全盲模式",
                            subtitle = "超大按钮、高对比度、语音引导",
                            checked = blindMode,
                            onCheckedChange = { AccessibilityPrefs.setBlindMode(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggleItem(
                            icon = Icons.Filled.Contrast,
                            title = "高对比度",
                            subtitle = "黑底 + 黄色高亮",
                            checked = highContrast,
                            onCheckedChange = { AccessibilityPrefs.setHighContrast(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggleItem(
                            icon = Icons.Filled.TextFields,
                            title = "大字体",
                            subtitle = "增大所有文字显示",
                            checked = largeText,
                            onCheckedChange = { AccessibilityPrefs.setLargeText(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggleItem(
                            icon = Icons.Filled.RecordVoiceOver,
                            title = "语音引导",
                            subtitle = "切换页面时语音提示",
                            checked = voiceGuide,
                            onCheckedChange = { AccessibilityPrefs.setVoiceGuide(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Device Section ──
            SectionTitle("设备管理")

            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsClickItem(
                            icon = Icons.Filled.Bluetooth,
                            title = "眼镜管理",
                            subtitle = "配对、连接、固件更新",
                            onClick = { /* TODO */}
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsClickItem(
                            icon = Icons.Filled.Language,
                            title = "翻译语言",
                            subtitle = "设置默认翻译语言对",
                            onClick = { /* TODO */}
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Account Section ──
            SectionTitle("账户")

            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsClickItem(
                            icon = Icons.Filled.Person,
                            title = "登录 / 注册",
                            subtitle = "使用手机号登录",
                            onClick = { /* TODO */}
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsClickItem(
                            icon = Icons.Filled.CardMembership,
                            title = "会员中心",
                            subtitle = "查看会员等级与权益",
                            onClick = { /* TODO */}
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── About Section ──
            SectionTitle("关于")

            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsClickItem(
                            icon = Icons.Filled.Info,
                            title = "关于 CyanBridge",
                            subtitle = "版本 1.0.2",
                            onClick = { /* TODO */}
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingsToggleItem(
        icon: ImageVector,
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
) {
    Row(
            modifier =
                    Modifier.fillMaxWidth().padding(16.dp).semantics {
                        contentDescription = "$title，$subtitle，当前${if (checked) "已开启" else "已关闭"}"
                    },
            verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
            Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors =
                        SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                        )
        )
    }
}

@Composable
private fun SettingsClickItem(
        icon: ImageVector,
        title: String,
        subtitle: String,
        onClick: () -> Unit
) {
    Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$title，$subtitle" }
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = title,
                        style =
                                MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Medium
                                )
                )
                Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
