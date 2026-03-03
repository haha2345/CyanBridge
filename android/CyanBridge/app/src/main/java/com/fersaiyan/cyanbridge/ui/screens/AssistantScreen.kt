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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.fersaiyan.cyanbridge.ui.accessibility.isBlindMode
import com.fersaiyan.cyanbridge.ui.theme.CyanPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AssistantScreen(assistantVm: AssistantViewModel = viewModel()) {
        val messages by assistantVm.messages.collectAsState()

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
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 16.dp),
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
                                Text(
                                        text = "唤醒眼镜或输入文字开始对话",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                                text = "对眼镜说唤醒词开始语音对话\n或在下方输入文字",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                        )
                                }
                        }
                } else {
                        LazyColumn(
                                state = listState,
                                modifier =
                                        Modifier.weight(1f)
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                                items(messages, key = { it.id }) { message ->
                                        ChatBubble(
                                                message = message,
                                                onReplay = { assistantVm.replayMessage(it) }
                                        )
                                }
                        }
                }

                // ── Input Bar ──
                InputBar(
                        inputText = inputText,
                        onInputChange = { inputText = it },
                        onSend = {
                                if (inputText.isNotBlank()) {
                                        assistantVm.sendText(inputText)
                                        inputText = ""
                                        keyboardController?.hide()
                                }
                        }
                )
        }
}

@Composable
private fun ChatBubble(message: ChatMessageEntity, onReplay: (ChatMessageEntity) -> Unit) {
        val isUser = message.role == ChatRoles.USER
        val isError = message.status == ChatStatus.ERROR
        val blind = isBlindMode()
        val timeStr =
                remember(message.createdAt) {
                        SimpleDateFormat("HH:mm", Locale.getDefault())
                                .format(Date(message.createdAt))
                }

        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Bottom
        ) {
                if (!isUser) {
                        // Replay button for AI messages (larger in blind mode)
                        val btnSize = if (blind) 48.dp else 32.dp
                        val iconSize = if (blind) 28.dp else 18.dp
                        IconButton(
                                onClick = { onReplay(message) },
                                modifier = Modifier.size(btnSize)
                        ) {
                                Icon(
                                        Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "重放此消息",
                                        modifier = Modifier.size(iconSize),
                                        tint = MaterialTheme.colorScheme.primary
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
                                                        isError ->
                                                                MaterialTheme.colorScheme
                                                                        .errorContainer
                                                        isUser -> CyanPrimary
                                                        else ->
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant
                                                }
                                ),
                        modifier =
                                Modifier.widthIn(max = 280.dp).semantics {
                                        contentDescription =
                                                "${if (isUser) "你" else "助手"}说：${message.content}"
                                }
                ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                                // ── Vision image ──
                                val imgPath = message.imagePath
                                var showImageDialog by remember { mutableStateOf(false) }
                                if (!imgPath.isNullOrBlank()) {
                                        val bmp =
                                                remember(imgPath) {
                                                        try {
                                                                val f = java.io.File(imgPath)
                                                                if (f.exists())
                                                                        android.graphics
                                                                                .BitmapFactory
                                                                                .decodeFile(
                                                                                        f.absolutePath
                                                                                )
                                                                else null
                                                        } catch (_: Exception) {
                                                                null
                                                        }
                                                }
                                        if (bmp != null) {
                                                Image(
                                                        bitmap = bmp.asImageBitmap(),
                                                        contentDescription = "拍摄的图片",
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .clip(
                                                                                RoundedCornerShape(
                                                                                        8.dp
                                                                                )
                                                                        )
                                                                        .clickable {
                                                                                showImageDialog =
                                                                                        true
                                                                        }
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))

                                                if (showImageDialog) {
                                                        Dialog(
                                                                onDismissRequest = {
                                                                        showImageDialog = false
                                                                }
                                                        ) {
                                                                Image(
                                                                        bitmap =
                                                                                bmp.asImageBitmap(),
                                                                        contentDescription = "放大查看",
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                                        .clip(
                                                                                                RoundedCornerShape(
                                                                                                        12.dp
                                                                                                )
                                                                                        )
                                                                                        .clickable {
                                                                                                showImageDialog =
                                                                                                        false
                                                                                        }
                                                                )
                                                        }
                                                }
                                        }
                                }

                                Text(
                                        text = message.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color =
                                                when {
                                                        isError ->
                                                                MaterialTheme.colorScheme
                                                                        .onErrorContainer
                                                        isUser ->
                                                                MaterialTheme.colorScheme.onPrimary
                                                        else -> MaterialTheme.colorScheme.onSurface
                                                }
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                        text = timeStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color =
                                                when {
                                                        isUser ->
                                                                MaterialTheme.colorScheme.onPrimary
                                                                        .copy(alpha = 0.6f)
                                                        else ->
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant.copy(
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
                        IconButton(
                                onClick = { onReplay(message) },
                                modifier = Modifier.size(32.dp)
                        ) {
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
private fun InputBar(inputText: String, onInputChange: (String) -> Unit, onSend: () -> Unit) {
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
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
                        IconButton(
                                onClick = onSend,
                                modifier =
                                        Modifier.size(48.dp)
                                                .clip(CircleShape)
                                                .background(
                                                        if (inputText.isNotBlank()) CyanPrimary
                                                        else CyanPrimary.copy(alpha = 0.4f)
                                                ),
                                enabled = inputText.isNotBlank()
                        ) {
                                Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "发送",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                )
                        }
                }
        }
}
