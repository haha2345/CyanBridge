package com.fersaiyan.cyanbridge.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fersaiyan.cyanbridge.translate.LanguagePair
import com.fersaiyan.cyanbridge.translate.TranslateEngine
import com.fersaiyan.cyanbridge.ui.accessibility.isBlindMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val engine = remember { TranslateEngine(context) }
    val state by engine.state.collectAsState()
    val sourceText by engine.sourceText.collectAsState()
    val translatedText by engine.translatedText.collectAsState()
    val pair by engine.languagePair.collectAsState()
    val blind = isBlindMode()

    var showLanguageDialog by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted
                ->
                hasPermission = granted
            }

    DisposableEffect(Unit) { onDispose { engine.stop() } }

    val isRunning = state != TranslateEngine.State.IDLE

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text("同声传译") },
                        navigationIcon = {
                            IconButton(
                                    onClick = {
                                        engine.stop()
                                        onBack()
                                    }
                            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                        },
                )
            }
    ) { padding ->
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(padding)
                                .padding(horizontal = if (blind) 16.dp else 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Language Selector ──
            LanguageSelector(
                    pair = pair,
                    onSwap = { engine.swapLanguages() },
                    onSelect = { showLanguageDialog = true },
                    enabled = !isRunning,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Source Text Area ──
            TextPanel(
                    label = pair.sourceName,
                    text = sourceText.ifBlank { if (isRunning) "正在聆听..." else "点击下方按钮开始翻译" },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Translated Text Area ──
            TextPanel(
                    label = pair.targetName,
                    text =
                            translatedText.ifBlank {
                                when (state) {
                                    TranslateEngine.State.TRANSLATING -> "翻译中..."
                                    TranslateEngine.State.PLAYING -> "播放中..."
                                    else -> "翻译结果将显示在这里"
                                }
                            },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.weight(1f),
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Start/Stop Button ──
            val btnSize = if (blind) 88.dp else 72.dp
            FilledIconButton(
                    onClick = {
                        if (isRunning) {
                            engine.stop()
                        } else {
                            if (!hasPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                engine.start()
                            }
                        }
                    },
                    modifier =
                            Modifier.size(btnSize).semantics {
                                contentDescription = if (isRunning) "停止翻译" else "开始翻译"
                            },
                    shape = CircleShape,
                    colors =
                            IconButtonDefaults.filledIconButtonColors(
                                    containerColor =
                                            if (isRunning) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.primary
                            ),
            ) {
                Icon(
                        imageVector = if (isRunning) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(if (blind) 40.dp else 32.dp),
                )
            }

            // Status
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                    text =
                            when (state) {
                                TranslateEngine.State.IDLE -> "点击麦克风开始"
                                TranslateEngine.State.LISTENING -> "聆听中..."
                                TranslateEngine.State.TRANSLATING -> "翻译中..."
                                TranslateEngine.State.PLAYING -> "播放翻译..."
                            },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // ── Language Selection Dialog ──
    if (showLanguageDialog) {
        LanguagePickerDialog(
                onSelect = { selected ->
                    engine.setLanguagePair(selected)
                    showLanguageDialog = false
                },
                onDismiss = { showLanguageDialog = false },
        )
    }
}

@Composable
private fun LanguageSelector(
        pair: LanguagePair,
        onSwap: () -> Unit,
        onSelect: () -> Unit,
        enabled: Boolean,
) {
    Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
    ) {
        OutlinedButton(
                onClick = onSelect,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "源语言：${pair.sourceName}" },
        ) { Text(pair.sourceName, style = MaterialTheme.typography.titleMedium) }

        IconButton(
                onClick = onSwap,
                enabled = enabled,
                modifier = Modifier.padding(horizontal = 8.dp),
        ) { Icon(Icons.Filled.SwapHoriz, "交换语言") }

        OutlinedButton(
                onClick = onSelect,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "目标语言：${pair.targetName}" },
        ) { Text(pair.targetName, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun TextPanel(
        label: String,
        text: String,
        color: androidx.compose.ui.graphics.Color,
        modifier: Modifier = Modifier,
) {
    OutlinedCard(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun LanguagePickerDialog(
        onSelect: (LanguagePair) -> Unit,
        onDismiss: () -> Unit,
) {
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("选择翻译语言对") },
            text = {
                Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LanguagePair.PRESETS.forEach { pair ->
                        TextButton(
                                onClick = { onSelect(pair) },
                                modifier =
                                        Modifier.fillMaxWidth().semantics {
                                            contentDescription = pair.displayLabel
                                        },
                        ) {
                            Text(
                                    text = pair.displayLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
