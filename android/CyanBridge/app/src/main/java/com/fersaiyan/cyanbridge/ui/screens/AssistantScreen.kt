package com.fersaiyan.cyanbridge.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fersaiyan.cyanbridge.chat.ChatMessageEntity
import com.fersaiyan.cyanbridge.chat.ChatRoles
import com.fersaiyan.cyanbridge.chat.ChatStatus
import com.fersaiyan.cyanbridge.ui.theme.CyanDark
import com.fersaiyan.cyanbridge.ui.theme.CyanPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AssistantScreen(assistantVm: AssistantViewModel = viewModel()) {
    val messages by assistantVm.messages.collectAsState()
    val isListening by assistantVm.isListening.collectAsState()
    val statusText by assistantVm.statusText.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
            modifier = Modifier.fillMaxSize(),
    ) {
        // ── Header ──
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = "语音助手",
                        style =
                                MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold
                                ),
                        color = MaterialTheme.colorScheme.onSurface
                )
                if (statusText.isNotBlank()) {
                    Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = CyanPrimary
                    )
                }
            }
            if (messages.isNotEmpty()) {
                IconButton(onClick = { assistantVm.clearHistory() }) {
                    Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "清空对话",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── Message List ──
        if (messages.isEmpty()) {
            // Empty state
            Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🤖", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                            text = "你好！我是你的智能助手",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                            text = "点击麦克风或输入文字開始对话",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(message = message, onReplay = { assistantVm.replayMessage(it) })
                }
            }
        }

        // ── Listening indicator ──
        if (isListening) {
            ListeningIndicator()
        }

        // ── Input Bar ──
        InputBar(
                inputText = inputText,
                isListening = isListening,
                onInputChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        assistantVm.sendText(inputText)
                        inputText = ""
                        keyboardController?.hide()
                    }
                },
                onMicToggle = { assistantVm.toggleListening() }
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessageEntity, onReplay: (ChatMessageEntity) -> Unit) {
    val isUser = message.role == ChatRoles.USER
    val isError = message.status == ChatStatus.ERROR
    val timeStr =
            remember(message.createdAt) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.createdAt))
            }

    Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            // Replay button for AI messages
            IconButton(onClick = { onReplay(message) }, modifier = Modifier.size(32.dp)) {
                Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "重放此消息",
                        modifier = Modifier.size(18.dp),
                        tint = CyanPrimary
                )
            }
        }

        Card(
                shape =
                        RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isUser) 16.dp else 4.dp,
                                bottomEnd = if (isUser) 4.dp else 16.dp,
                        ),
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        when {
                                            isError -> MaterialTheme.colorScheme.errorContainer
                                            isUser -> CyanPrimary
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                        ),
                modifier =
                        Modifier.widthIn(max = 280.dp).semantics {
                            contentDescription = "${if (isUser) "你" else "助手"}说：${message.content}"
                        }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                                when {
                                    isError -> MaterialTheme.colorScheme.onErrorContainer
                                    isUser -> MaterialTheme.colorScheme.onPrimary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                                when {
                                    isUser -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                                    else ->
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.6f
                                            )
                                },
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (isUser) {
            // Replay button for user messages
            IconButton(onClick = { onReplay(message) }, modifier = Modifier.size(32.dp)) {
                Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "重放此消息",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ListeningIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "listening")
    val dotAlpha1 by
            infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(600),
                                    repeatMode = RepeatMode.Reverse
                            ),
                    label = "d1"
            )
    val dotAlpha2 by
            infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(600, delayMillis = 200),
                                    repeatMode = RepeatMode.Reverse
                            ),
                    label = "d2"
            )
    val dotAlpha3 by
            infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(600, delayMillis = 400),
                                    repeatMode = RepeatMode.Reverse
                            ),
                    label = "d3"
            )

    Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "正在聆听", style = MaterialTheme.typography.bodySmall, color = CyanPrimary)
        Spacer(modifier = Modifier.width(8.dp))
        listOf(dotAlpha1, dotAlpha2, dotAlpha3).forEach { alpha ->
            Box(
                    modifier =
                            Modifier.size(6.dp)
                                    .clip(CircleShape)
                                    .background(CyanPrimary.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

@Composable
private fun InputBar(
        inputText: String,
        isListening: Boolean,
        onInputChange: (String) -> Unit,
        onSend: () -> Unit,
        onMicToggle: () -> Unit
) {
    // Pulsing animation for mic
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by
            infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.2f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(600, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                            ),
                    label = "pulse"
            )

    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            // Text input
            OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息...") },
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    colors =
                            OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanPrimary,
                                    cursorColor = CyanPrimary
                            )
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Send button
            if (inputText.isNotBlank()) {
                IconButton(
                        onClick = onSend,
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(CyanPrimary)
                ) {
                    Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Mic button
            IconButton(
                    onClick = onMicToggle,
                    modifier =
                            Modifier.size(48.dp)
                                    .then(if (isListening) Modifier.scale(pulseScale) else Modifier)
                                    .clip(CircleShape)
                                    .background(
                                            if (isListening)
                                                    Brush.radialGradient(
                                                            listOf(
                                                                    MaterialTheme.colorScheme.error,
                                                                    MaterialTheme.colorScheme.error
                                                                            .copy(alpha = 0.7f)
                                                            )
                                                    )
                                            else Brush.radialGradient(listOf(CyanPrimary, CyanDark))
                                    ),
            ) {
                Icon(
                        imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (isListening) "停止聆听" else "开始对话",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
