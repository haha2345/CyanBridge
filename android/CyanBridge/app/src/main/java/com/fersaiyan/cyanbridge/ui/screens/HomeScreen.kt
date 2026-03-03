package com.fersaiyan.cyanbridge.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fersaiyan.cyanbridge.glasses.GlassesRepository
import com.fersaiyan.cyanbridge.glasses.GlassesViewModel
import com.fersaiyan.cyanbridge.ui.theme.CyanDark
import com.fersaiyan.cyanbridge.ui.theme.CyanPrimary

@Composable
fun HomeScreen(glassesVm: GlassesViewModel = viewModel()) {

    val isConnected by glassesVm.isConnected.collectAsState()
    val deviceName by glassesVm.deviceName.collectAsState()
    val batteryLevel by glassesVm.batteryLevel.collectAsState()
    val isCharging by glassesVm.isCharging.collectAsState()
    val showScanDialog by glassesVm.showScanDialog.collectAsState()

    // Refresh on composition
    LaunchedEffect(Unit) { glassesVm.refreshState() }

    Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Title ──
        Text(
                text = "CyanBridge",
                style =
                        MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                        ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { contentDescription = "CyanBridge 盲人智能眼镜助手" }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
                text = "盲人智能眼镜助手",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ── Connection Status Card ──
        GlassesConnectionCard(
                isConnected = isConnected,
                deviceName = deviceName,
                batteryLevel = batteryLevel,
                isCharging = isCharging ?: false,
                hasSavedDevice = glassesVm.hasSavedDevice,
                onConnectClick = { glassesVm.onConnectTapped() },
                onScanClick = { glassesVm.onScanRequested() },
                onForgetClick = { glassesVm.onForgetDevice() }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Quick Actions Grid ──
        Text(
                text = "快捷操作",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                color = MaterialTheme.colorScheme.onSurface
        )

        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                    modifier = Modifier.weight(1f),
                    title = "拍照",
                    description = "控制眼镜拍照",
                    enabled = isConnected,
                    onClick = { /* TODO */}
            )
            QuickActionCard(
                    modifier = Modifier.weight(1f),
                    title = "录像",
                    description = "控制眼镜录像",
                    enabled = isConnected,
                    onClick = { /* TODO */}
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                    modifier = Modifier.weight(1f),
                    title = "录音",
                    description = "控制眼镜录音",
                    enabled = isConnected,
                    onClick = { /* TODO */}
            )
            QuickActionCard(
                    modifier = Modifier.weight(1f),
                    title = "翻译",
                    description = "同声传译功能",
                    enabled = isConnected,
                    onClick = { /* TODO */}
            )
        }
    }

    // ── BLE Scan Dialog ──
    if (showScanDialog) {
        BleScanDialog(viewModel = glassesVm, onDismiss = { glassesVm.onScanDialogDismissed() })
    }
}

@Composable
private fun GlassesConnectionCard(
        isConnected: Boolean,
        deviceName: String?,
        batteryLevel: Int?,
        isCharging: Boolean,
        hasSavedDevice: Boolean,
        onConnectClick: () -> Unit,
        onScanClick: () -> Unit,
        onForgetClick: () -> Unit
) {
    Card(
            modifier =
                    Modifier.fillMaxWidth().semantics {
                        contentDescription =
                                if (isConnected) {
                                    "眼镜已连接，${deviceName ?: ""}，电量${batteryLevel ?: "未知"}%"
                                } else {
                                    "眼镜未连接，点击连接"
                                }
                    },
            shape = RoundedCornerShape(20.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    if (isConnected)
                                            MaterialTheme.colorScheme.primaryContainer.copy(
                                                    alpha = 0.3f
                                            )
                                    else MaterialTheme.colorScheme.surfaceVariant
                    )
    ) {
        Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status icon
            Box(
                    modifier =
                            Modifier.size(80.dp)
                                    .clip(CircleShape)
                                    .background(
                                            Brush.radialGradient(
                                                    colors =
                                                            if (isConnected)
                                                                    listOf(
                                                                            CyanPrimary.copy(
                                                                                    alpha = 0.3f
                                                                            ),
                                                                            CyanDark.copy(
                                                                                    alpha = 0.1f
                                                                            )
                                                                    )
                                                            else
                                                                    listOf(
                                                                            MaterialTheme
                                                                                    .colorScheme
                                                                                    .error.copy(
                                                                                    alpha = 0.2f
                                                                            ),
                                                                            MaterialTheme
                                                                                    .colorScheme
                                                                                    .error.copy(
                                                                                    alpha = 0.05f
                                                                            )
                                                                    )
                                            )
                                    ),
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                        imageVector =
                                if (isConnected) Icons.Filled.Bluetooth
                                else Icons.Filled.BluetoothDisabled,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = if (isConnected) CyanPrimary else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Connection status text
            Text(
                    text = if (isConnected) "眼镜已连接" else "眼镜未连接",
                    style =
                            MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.SemiBold
                            ),
                    color = MaterialTheme.colorScheme.onSurface
            )

            // Device name
            if (isConnected && deviceName != null) {
                Text(
                        text = deviceName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Battery info
            AnimatedVisibility(visible = isConnected && batteryLevel != null) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                            imageVector =
                                    when {
                                        isCharging -> Icons.Filled.BatteryChargingFull
                                        (batteryLevel ?: 100) <= 20 -> Icons.Filled.BatteryAlert
                                        else -> Icons.Filled.BatteryFull
                                    },
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint =
                                    when {
                                        (batteryLevel ?: 100) <= 20 ->
                                                MaterialTheme.colorScheme.error
                                        isCharging -> CyanPrimary
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                            text = "${batteryLevel}%" + if (isCharging) " 充电中" else "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action buttons
            if (isConnected) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                            onClick = onForgetClick,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                    ) { Text("忘记设备") }
                    FilledTonalButton(
                            onClick = onConnectClick,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                    ) { Text("断开连接") }
                }
            } else {
                FilledTonalButton(
                        onClick = onConnectClick,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                            text = if (hasSavedDevice) "重新连接" else "搜索眼镜",
                            style =
                                    MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                    )
                    )
                }

                if (hasSavedDevice) {
                    TextButton(onClick = onScanClick) { Text("搜索其他设备") }
                }
            }
        }
    }
}

// ── BLE Scan Dialog ──────────────────────────────────────────

@Composable
private fun BleScanDialog(viewModel: GlassesViewModel, onDismiss: () -> Unit) {
    val devices by viewModel.scannedDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    // Start scanning when dialog opens
    LaunchedEffect(Unit) { viewModel.onScanRequested() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 500.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Title
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            text = "搜索蓝牙设备",
                            style =
                                    MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold
                                    )
                    )
                    if (isScanning) {
                        CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                        text = if (isScanning) "正在搜索附近的眼镜设备..." else "搜索完成，找到 ${devices.size} 个设备",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Device list
                if (devices.isEmpty() && !isScanning) {
                    Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                    Icons.Filled.BluetoothSearching,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.4f
                                            )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                    text = "未找到设备",
                                    color =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.6f
                                            )
                            )
                        }
                    }
                } else {
                    LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(devices, key = { it.address }) { device ->
                            DeviceListItem(
                                    device = device,
                                    onClick = { viewModel.onDeviceSelected(device) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                    ) { Text("取消") }
                    if (!isScanning) {
                        FilledTonalButton(
                                onClick = { viewModel.onScanRequested() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                        ) { Text("重新搜索") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceListItem(device: GlassesRepository.ScannedDevice, onClick: () -> Unit) {
    Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier =
                    Modifier.semantics {
                        contentDescription = "蓝牙设备 ${device.name}，信号强度 ${device.rssi}"
                    }
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                    Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = device.name,
                        style =
                                MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Medium
                                ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
                Text(
                        text = device.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Signal strength indicator
            Text(
                    text = "${device.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickActionCard(
        modifier: Modifier = Modifier,
        title: String,
        description: String,
        enabled: Boolean = true,
        onClick: () -> Unit
) {
    Card(
            onClick = onClick,
            modifier =
                    modifier.height(80.dp).semantics { contentDescription = "$title，$description" },
            shape = RoundedCornerShape(16.dp),
            enabled = enabled,
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContainerColor =
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
    ) {
        Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center
        ) {
            Text(
                    text = title,
                    style =
                            MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                            ),
                    color =
                            if (enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                            if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}
