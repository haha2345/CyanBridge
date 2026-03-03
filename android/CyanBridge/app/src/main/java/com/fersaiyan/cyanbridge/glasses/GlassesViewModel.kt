package com.fersaiyan.cyanbridge.glasses

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class GlassesViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = GlassesRepository.getInstance(application)

    // ── Exposed State ─────────────────────────────────────────

    val isConnected: StateFlow<Boolean> = repo.connectionState
    val isConnecting: StateFlow<Boolean> = repo.isConnecting
    val deviceName: StateFlow<String?> = repo.deviceName
    val batteryLevel: StateFlow<Int?> = repo.batteryLevel
    val isCharging: StateFlow<Boolean?> = repo.isCharging
    val scannedDevices: StateFlow<List<GlassesRepository.ScannedDevice>> = repo.scannedDevices
    val isScanning: StateFlow<Boolean> = repo.isScanning

    // UI state
    private val _showScanDialog = MutableStateFlow(false)
    val showScanDialog: StateFlow<Boolean> = _showScanDialog.asStateFlow()

    val hasSavedDevice: Boolean
        get() = repo.getSavedDeviceAddress() != null

    // ── Battery Polling ───────────────────────────────────────

    private var batteryPollJob: Job? = null

    init {
        repo.registerEvents()
        startBatteryPolling()
    }

    private fun startBatteryPolling() {
        batteryPollJob?.cancel()
        batteryPollJob =
                viewModelScope.launch {
                    while (isActive) {
                        if (repo.isConnected()) {
                            repo.requestBattery()
                        }
                        delay(60_000L)
                    }
                }
    }

    // ── Actions ───────────────────────────────────────────────

    /**
     * Called when user taps the connect button. If a saved device exists → auto-reconnect. If no
     * saved device → show scan dialog.
     */
    fun onConnectTapped() {
        if (repo.isConnected()) {
            // Already connected, disconnect
            repo.disconnect()
            return
        }

        if (repo.tryAutoReconnect()) {
            // Auto-reconnecting to saved device
            return
        }

        // No saved device — show scan dialog
        _showScanDialog.value = true
    }

    fun onScanRequested() {
        _showScanDialog.value = true
        repo.startScan()
    }

    fun onDeviceSelected(device: GlassesRepository.ScannedDevice) {
        repo.stopScan()
        _showScanDialog.value = false
        repo.connectToDevice(device.address, device.name)
    }

    fun onScanDialogDismissed() {
        repo.stopScan()
        _showScanDialog.value = false
    }

    fun onForgetDevice() {
        repo.disconnect()
        repo.clearSavedDevice()
    }

    fun refreshState() {
        repo.refreshConnectionState()
    }

    override fun onCleared() {
        super.onCleared()
        batteryPollJob?.cancel()
        repo.unregisterEvents()
    }
}
