package com.fersaiyan.cyanbridge.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fersaiyan.cyanbridge.auth.MemberTier
import com.fersaiyan.cyanbridge.auth.UserRepository
import com.fersaiyan.cyanbridge.ui.accessibility.AccessibilityPrefs
import com.fersaiyan.cyanbridge.ui.accessibility.LargeTouchButton
import com.fersaiyan.cyanbridge.ui.accessibility.isBlindMode

@Composable
fun SettingsScreen(
        onNavigateToLogin: () -> Unit = {},
        onNavigateToMembership: () -> Unit = {},
) {
        val blindMode by AccessibilityPrefs.blindMode.collectAsState()
        val highContrast by AccessibilityPrefs.highContrast.collectAsState()
        val largeText by AccessibilityPrefs.largeText.collectAsState()
        val voiceGuide by AccessibilityPrefs.voiceGuide.collectAsState()
        val isBlind = isBlindMode()

        val context = LocalContext.current
        val userRepo = remember { UserRepository.getInstance(context) }
        val user by userRepo.currentUser.collectAsState()
        val isLoggedIn = user != null

        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(
                                        horizontal = if (isBlind) 16.dp else 24.dp,
                                        vertical = 24.dp
                                )
        ) {
                // ── Title ──
                Text(
                        text = "设置",
                        style =
                                MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold
                                ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { contentDescription = "设置页面" }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── User Profile Card ──
                if (isLoggedIn) {
                        UserProfileCard(
                                nickname = user!!.nickname,
                                tier = user!!.memberTier,
                                loginInfo =
                                        when {
                                                user!!.phone != null -> "手机：${user!!.phone}"
                                                user!!.email != null -> "邮箱：${user!!.email}"
                                                else -> "微信登录"
                                        },
                                onMembership = onNavigateToMembership,
                                onLogout = { userRepo.logout() },
                        )
                } else {
                        // Not logged in
                        Card(
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor =
                                                        MaterialTheme.colorScheme.primaryContainer,
                                        ),
                        ) {
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                ) {
                                        Icon(
                                                Icons.Filled.AccountCircle,
                                                null,
                                                modifier = Modifier.size(48.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                        "未登录",
                                                        style =
                                                                MaterialTheme.typography.titleMedium
                                                                        .copy(
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold
                                                                        ),
                                                )
                                                Text(
                                                        "登录后享受完整功能",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onPrimaryContainer.copy(
                                                                        alpha = 0.7f
                                                                ),
                                                )
                                        }
                                        Button(
                                                onClick = onNavigateToLogin,
                                                shape = RoundedCornerShape(10.dp),
                                        ) { Text("登录") }
                                }
                        }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Accessibility Section ──
                SectionTitle("无障碍")

                if (isBlind) {
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
                                icon = Icons.Filled.CardMembership,
                                title = "会员中心",
                                description = user?.memberTier?.displayName ?: "未开通",
                                onClick = onNavigateToMembership,
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
                        Card(
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                        ) {
                                Column {
                                        SettingsToggleItem(
                                                icon = Icons.Filled.Accessibility,
                                                title = "全盲模式",
                                                subtitle = "超大按钮、高对比度、语音引导",
                                                checked = blindMode,
                                                onCheckedChange = {
                                                        AccessibilityPrefs.setBlindMode(it)
                                                }
                                        )
                                        HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                        SettingsToggleItem(
                                                icon = Icons.Filled.Contrast,
                                                title = "高对比度",
                                                subtitle = "黑底 + 黄色高亮",
                                                checked = highContrast,
                                                onCheckedChange = {
                                                        AccessibilityPrefs.setHighContrast(it)
                                                }
                                        )
                                        HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                        SettingsToggleItem(
                                                icon = Icons.Filled.TextFields,
                                                title = "大字体",
                                                subtitle = "增大所有文字显示",
                                                checked = largeText,
                                                onCheckedChange = {
                                                        AccessibilityPrefs.setLargeText(it)
                                                }
                                        )
                                        HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                        SettingsToggleItem(
                                                icon = Icons.Filled.RecordVoiceOver,
                                                title = "语音引导",
                                                subtitle = "切换页面时语音提示",
                                                checked = voiceGuide,
                                                onCheckedChange = {
                                                        AccessibilityPrefs.setVoiceGuide(it)
                                                }
                                        )
                                }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // ── Device Section ──
                        SectionTitle("设备管理")

                        Card(
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                        ) {
                                Column {
                                        SettingsClickItem(
                                                icon = Icons.Filled.Bluetooth,
                                                title = "眼镜管理",
                                                subtitle = "配对、连接、固件更新",
                                                onClick = { /* TODO */}
                                        )
                                        HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                        )
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

                        Card(
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                        ) {
                                Column {
                                        if (!isLoggedIn) {
                                                SettingsClickItem(
                                                        icon = Icons.Filled.Person,
                                                        title = "登录 / 注册",
                                                        subtitle = "手机号 · 邮箱 · 微信",
                                                        onClick = onNavigateToLogin,
                                                )
                                                HorizontalDivider(
                                                        modifier =
                                                                Modifier.padding(horizontal = 16.dp)
                                                )
                                        }
                                        SettingsClickItem(
                                                icon = Icons.Filled.CardMembership,
                                                title = "会员中心",
                                                subtitle =
                                                        user?.memberTier?.let {
                                                                "${it.emoji} ${it.displayName}"
                                                        }
                                                                ?: "查看会员等级与权益",
                                                onClick = onNavigateToMembership,
                                        )
                                }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // ── About Section ──
                        SectionTitle("关于")

                        Card(
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                        ) {
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
private fun UserProfileCard(
        nickname: String,
        tier: MemberTier,
        loginInfo: String,
        onMembership: () -> Unit,
        onLogout: () -> Unit,
) {
        val tierColor =
                when (tier) {
                        MemberTier.FREE -> Color(0xFF78909C)
                        MemberTier.NORMAL -> Color(0xFFFFA726)
                        MemberTier.ADVANCED -> Color(0xFFFFD54F)
                        MemberTier.DIAMOND -> Color(0xFF4FC3F7)
                }

        Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
        ) {
                Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                // Avatar circle
                                Surface(
                                        shape = CircleShape,
                                        color = tierColor.copy(alpha = 0.2f),
                                        modifier = Modifier.size(52.dp),
                                ) {
                                        Box(contentAlignment = Alignment.Center) {
                                                Text(tier.emoji, fontSize = 24.sp)
                                        }
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                                nickname,
                                                style =
                                                        MaterialTheme.typography.titleMedium.copy(
                                                                fontWeight = FontWeight.Bold
                                                        ),
                                        )
                                        Text(
                                                loginInfo,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = tierColor.copy(alpha = 0.15f),
                                                modifier = Modifier.padding(top = 4.dp),
                                        ) {
                                                Text(
                                                        "${tier.emoji} ${tier.displayName}",
                                                        modifier =
                                                                Modifier.padding(
                                                                        horizontal = 8.dp,
                                                                        vertical = 2.dp
                                                                ),
                                                        style =
                                                                MaterialTheme.typography.labelSmall
                                                                        .copy(
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold
                                                                        ),
                                                        color = tierColor,
                                                )
                                        }
                                }
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                        onClick = onMembership,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                ) {
                                        Icon(
                                                Icons.Filled.CardMembership,
                                                null,
                                                Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("会员中心")
                                }
                                OutlinedButton(
                                        onClick = onLogout,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors =
                                                ButtonDefaults.outlinedButtonColors(
                                                        contentColor =
                                                                MaterialTheme.colorScheme.error,
                                                ),
                                ) {
                                        Icon(Icons.Filled.Logout, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("退出登录")
                                }
                        }
                }
        }
}

@Composable
private fun SectionTitle(text: String) {
        Text(
                text = text,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier =
                        Modifier.padding(bottom = 8.dp).semantics {
                                contentDescription = "${text}设置区域"
                        }
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                }
                Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
}

@Composable
private fun SettingsClickItem(
        icon: ImageVector,
        title: String,
        subtitle: String,
        onClick: () -> Unit
) {
        Surface(onClick = onClick) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                        subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }
                        Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                }
        }
}
