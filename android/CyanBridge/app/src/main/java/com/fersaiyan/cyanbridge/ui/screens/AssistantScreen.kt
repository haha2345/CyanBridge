package com.fersaiyan.cyanbridge.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.ui.theme.CyanPrimary
import com.fersaiyan.cyanbridge.ui.theme.CyanDark

@Composable
fun AssistantScreen() {
    var isListening by remember { mutableStateOf(false) }
    var lastResponse by remember { mutableStateOf("你好！我是你的智能助手。可以帮你识别物体、读取文字、描述场景。") }

    // Pulsing animation for mic button
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Title ──
        Text(
            text = "语音助手",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics {
                contentDescription = "语音助手页面，点击下方按钮开始对话"
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "说出你的需求，我来帮你",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.weight(1f))

        // ── Response Card ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "助手回复：$lastResponse"
                },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = lastResponse,
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── Status Text ──
        Text(
            text = if (isListening) "正在聆听..." else "点击麦克风开始对话",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isListening) CyanPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ── Mic Button ──
        Box(
            modifier = Modifier
                .size(120.dp)
                .then(
                    if (isListening) Modifier.scale(pulseScale) else Modifier
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = if (isListening)
                            listOf(CyanPrimary, CyanDark)
                        else
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = {
                    isListening = !isListening
                    if (!isListening) {
                        lastResponse = "我看到前方有一张桌子，上面放着一杯水和一本书。桌子在画面中央偏右的位置。"
                    }
                },
                modifier = Modifier
                    .size(120.dp)
                    .semantics {
                        contentDescription = if (isListening) "停止聆听" else "开始对话"
                    }
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Filled.Mic else Icons.Filled.MicOff,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
