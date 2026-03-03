package com.fersaiyan.cyanbridge.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fersaiyan.cyanbridge.auth.FEATURE_MATRIX
import com.fersaiyan.cyanbridge.auth.MemberTier
import com.fersaiyan.cyanbridge.auth.UserRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MembershipScreen(onBack: () -> Unit = {}, onLoginNeeded: () -> Unit = {}) {
    val context = LocalContext.current
    val userRepo = remember { UserRepository.getInstance(context) }
    val user by userRepo.currentUser.collectAsState()
    val scope = rememberCoroutineScope()
    var purchasingTier by remember { mutableStateOf<MemberTier?>(null) }

    Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        // ── Top Bar ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text(
                    "会员中心",
                    style =
                            MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold
                            ),
                    modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Current Status Card ──
        val currentTier = user?.memberTier ?: MemberTier.FREE
        CurrentMemberCard(currentTier, user?.nickname ?: "未登录")

        Spacer(Modifier.height(24.dp))

        // ── Tier Cards ──
        Text(
                "选择会员方案",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )

        Spacer(Modifier.height(12.dp))

        val tiers = listOf(MemberTier.NORMAL, MemberTier.ADVANCED, MemberTier.DIAMOND)
        tiers.forEach { tier ->
            val isCurrentTier = currentTier == tier
            TierCard(
                    tier = tier,
                    isCurrentTier = isCurrentTier,
                    isPurchasing = purchasingTier == tier,
                    onPurchase = {
                        if (user == null) {
                            Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show()
                            onLoginNeeded()
                            return@TierCard
                        }
                        purchasingTier = tier
                        scope.launch {
                            delay(1000) // Simulate payment
                            val success = userRepo.purchaseMembership(tier)
                            purchasingTier = null
                            Toast.makeText(
                                            context,
                                            if (success) "🎉 ${tier.displayName}开通成功！" else "购买失败",
                                            Toast.LENGTH_SHORT
                                    )
                                    .show()
                        }
                    }
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(24.dp))

        // ── Feature Comparison Table ──
        Text(
                "功能权益对比",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )

        Spacer(Modifier.height(12.dp))

        FeatureComparisonTable()

        Spacer(Modifier.height(24.dp))

        // ── Footer ──
        Text(
                "💡 当前为演示版本，购买为模拟操作。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CurrentMemberCard(tier: MemberTier, nickname: String) {
    val gradient =
            when (tier) {
                MemberTier.FREE -> listOf(Color(0xFF78909C), Color(0xFF546E7A))
                MemberTier.NORMAL -> listOf(Color(0xFFFFA726), Color(0xFFEF6C00))
                MemberTier.ADVANCED -> listOf(Color(0xFFFFD54F), Color(0xFFF9A825))
                MemberTier.DIAMOND -> listOf(Color(0xFF4FC3F7), Color(0xFF0277BD))
            }

    Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .background(Brush.horizontalGradient(gradient))
                                .padding(24.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                            tier.emoji,
                            fontSize = 36.sp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                                nickname,
                                style =
                                        MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold
                                        ),
                                color = Color.White,
                        )
                        Text(
                                tier.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
                if (tier != MemberTier.FREE) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                            "有效期：30天",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TierCard(
        tier: MemberTier,
        isCurrentTier: Boolean,
        isPurchasing: Boolean,
        onPurchase: () -> Unit,
) {
    val borderColor =
            when (tier) {
                MemberTier.NORMAL -> Color(0xFFFFA726)
                MemberTier.ADVANCED -> Color(0xFFFFD54F)
                MemberTier.DIAMOND -> Color(0xFF4FC3F7)
                else -> MaterialTheme.colorScheme.outline
            }

    Card(
            shape = RoundedCornerShape(16.dp),
            modifier =
                    Modifier.fillMaxWidth()
                            .then(
                                    if (isCurrentTier)
                                            Modifier.border(
                                                    2.dp,
                                                    borderColor,
                                                    RoundedCornerShape(16.dp)
                                            )
                                    else Modifier
                            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tier.emoji, fontSize = 28.sp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            tier.displayName,
                            style =
                                    MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                    ),
                    )
                    Text(
                            "¥${tier.monthlyPrice}/月 · ¥${tier.yearlyPrice}/年",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isCurrentTier) {
                    Surface(
                            color = borderColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                                "当前",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style =
                                        MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold
                                        ),
                                color = borderColor,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Feature chips
            Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tier.features.forEach { feature ->
                    SuggestionChip(
                            onClick = {},
                            label = { Text(feature, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(28.dp),
                    )
                }
            }

            if (!isCurrentTier) {
                Spacer(Modifier.height(12.dp))
                Button(
                        onClick = onPurchase,
                        enabled = !isPurchasing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                ) {
                    if (isPurchasing) {
                        CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("购买中...")
                    } else {
                        Text("立即开通 ¥${tier.monthlyPrice}/月")
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureComparisonTable() {
    Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row
            Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                        "功能",
                        style =
                                MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold
                                ),
                        modifier = Modifier.weight(1.4f),
                )
                listOf("免费", "普通", "高级", "钻石").forEach { label ->
                    Text(
                            label,
                            style =
                                    MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold
                                    ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                    )
                }
            }

            HorizontalDivider()

            // Feature rows
            FEATURE_MATRIX.forEach { row ->
                Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                            "${row.icon} ${row.name}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1.4f),
                    )
                    listOf(row.free, row.normal, row.advanced, row.diamond).forEach { available ->
                        Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                        ) {
                            if (available) {
                                Box(
                                        modifier =
                                                Modifier.size(20.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.15f)
                                                        ),
                                        contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                            Icons.Filled.Check,
                                            null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            } else {
                                Text(
                                        "—",
                                        style = MaterialTheme.typography.labelSmall,
                                        color =
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = 0.3f
                                                ),
                                )
                            }
                        }
                    }
                }

                if (row != FEATURE_MATRIX.last()) {
                    HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}
