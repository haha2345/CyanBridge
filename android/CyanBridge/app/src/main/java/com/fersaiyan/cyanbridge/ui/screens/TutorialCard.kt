package com.fersaiyan.cyanbridge.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.fersaiyan.cyanbridge.ui.accessibility.isBlindMode

/** 教学辅助卡片：告诉用户如何使用智能眼镜。 */
@Composable
fun TutorialCard(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val blind = isBlindMode()

    Card(
            modifier =
                    modifier.fillMaxWidth().semantics { contentDescription = "使用教程，点击查看如何使用智能眼镜" },
            shape = RoundedCornerShape(16.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header — always visible
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                        text = "使用教程",
                        style =
                                MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                ),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                            imageVector =
                                    if (expanded) Icons.Filled.ExpandLess
                                    else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "收起教程" else "展开教程",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            // Summary text when collapsed
            if (!expanded) {
                Text(
                        text = "点击了解如何唤醒眼镜、对话和使用视觉功能",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
            }

            // Expandable content
            AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Spacer(modifier = Modifier.height(4.dp))

                    TutorialSection(
                            icon = Icons.Filled.RecordVoiceOver,
                            title = "如何唤醒",
                            items =
                                    listOf(
                                            "对眼镜说 \"Hey Glass\" 即可唤醒",
                                            "唤醒后眼镜会发出提示音",
                                    )
                    )

                    TutorialSection(
                            icon = Icons.Filled.Chat,
                            title = "如何对话",
                            items =
                                    listOf(
                                            "唤醒后直接说出你的问题",
                                            "例如：\"今天天气怎么样？\"",
                                            "眼镜会语音回答你的问题",
                                    )
                    )

                    TutorialSection(
                            icon = Icons.Filled.Visibility,
                            title = "视觉功能",
                            items =
                                    listOf(
                                            "\"帮我找一下我的杯子\" — 找物体",
                                            "\"请帮我读一下这段文字\" — 文字识别",
                                            "\"前面这个人是谁\" — 人物描述",
                                            "\"他的表情是什么\" — 表情识别",
                                            "\"地铁站在哪\" — 环境导航",
                                    )
                    )

                    if (blind) {
                        TutorialSection(
                                icon = Icons.Filled.AccessibilityNew,
                                title = "无障碍提示",
                                items =
                                        listOf(
                                                "所有功能均支持语音操作",
                                                "无需查看屏幕，眼镜会语音播报结果",
                                        )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialSection(icon: ImageVector, title: String, items: List<String>) {
    Column(
            modifier =
                    Modifier.semantics { contentDescription = "$title：${items.joinToString("，")}" }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                    text = title,
                    style =
                            MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                            ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        items.forEach { item ->
            Row(modifier = Modifier.padding(start = 28.dp, bottom = 4.dp)) {
                Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f),
                )
            }
        }
    }
}
