package com.fersaiyan.cyanbridge.ui.accessibility

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 全盲模式超大触控按钮：
 * - 最小高度 80dp
 * - 粗边框 + 大图标 + 大文字
 * - 语义标签完整
 */
@Composable
fun LargeTouchButton(
        icon: ImageVector,
        title: String,
        description: String = "",
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
) {
    val desc = if (description.isNotBlank()) "$title，$description" else title
    OutlinedCard(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
            modifier =
                    modifier.fillMaxWidth().defaultMinSize(minHeight = 80.dp).semantics {
                        contentDescription = desc
                    },
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = title,
                        style =
                                MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                ),
                        color = MaterialTheme.colorScheme.onSurface,
                )
                if (description.isNotBlank()) {
                    Text(
                            text = description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 高对比度卡片：加粗边框 + semantic 标签。 */
@Composable
fun AccessibleCard(
        modifier: Modifier = Modifier,
        contentDescription: String = "",
        content: @Composable ColumnScope.() -> Unit,
) {
    val isHighContrast by AccessibilityPrefs.highContrast.collectAsState()
    OutlinedCard(
            shape = RoundedCornerShape(16.dp),
            border =
                    if (isHighContrast) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    else CardDefaults.outlinedCardBorder(),
            modifier =
                    modifier.fillMaxWidth()
                            .then(
                                    if (contentDescription.isNotBlank())
                                            Modifier.semantics {
                                                this.contentDescription = contentDescription
                                            }
                                    else Modifier
                            ),
    ) { Column(content = content) }
}

/** 判断当前是否处于全盲模式（可在 @Composable 中使用）。 */
@Composable
fun isBlindMode(): Boolean {
    val blind by AccessibilityPrefs.blindMode.collectAsState()
    return blind
}
