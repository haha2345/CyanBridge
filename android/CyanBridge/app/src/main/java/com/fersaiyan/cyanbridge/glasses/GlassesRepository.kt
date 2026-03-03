package com.fersaiyan.cyanbridge.glasses

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import com.fersaiyan.cyanbridge.ui.BluetoothEvent
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.bluetooth.DeviceManager
import com.oudmon.ble.base.communication.LargeDataHandler
import com.oudmon.ble.base.scan.BleScannerHelper
import com.oudmon.ble.base.scan.ScanRecord
import com.oudmon.ble.base.scan.ScanWrapperCallback
import kotlinx.coroutines.flow.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Wraps all BLE SDK interactions into a clean, flow-based API. Singleton — lives as long as the
 * Application.
 */
class GlassesRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "GlassesRepo"
        private const val PREFS_NAME = "glasses_prefs"
        private const val KEY_DEVICE_NAME = "saved_device_name"
        private const val KEY_DEVICE_ADDRESS = "saved_device_address"

        @Volatile private var INSTANCE: GlassesRepository? = null

        fun getInstance(context: Context): GlassesRepository =
                INSTANCE
                        ?: synchronized(this) {
                            INSTANCE
                                    ?: GlassesRepository(context.applicationContext).also {
                                        INSTANCE = it
                                    }
                        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Connection State ──────────────────────────────────────

    private val _connectionState = MutableStateFlow(BleOperateManager.getInstance().isConnected)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _deviceName = MutableStateFlow(prefs.getString(KEY_DEVICE_NAME, null))
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    // ── Battery ───────────────────────────────────────────────

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

    private val _isCharging = MutableStateFlow<Boolean?>(null)
    val isCharging: StateFlow<Boolean?> = _isCharging.asStateFlow()

    // ── Scan Results ──────────────────────────────────────────

    data class ScannedDevice(val name: String, val address: String, val rssi: Int)

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // ── EventBus Listener ─────────────────────────────────────

    private var eventBusRegistered = false
    private var batteryCallbackRegistered = false

    fun registerEvents() {
        if (!eventBusRegistered) {
            EventBus.getDefault().register(this)
            eventBusRegistered = true
        }
    }

    fun unregisterEvents() {
        if (eventBusRegistered) {
            EventBus.getDefault().unregister(this)
            eventBusRegistered = false
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBluetoothEvent(event: BluetoothEvent) {
        _connectionState.value = event.connect
        if (event.connect) {
            val name = DeviceManager.getInstance().deviceName
            _deviceName.value = name
            requestBattery()
        } else {
            _batteryLevel.value = null
            _isCharging.value = null
        }
    }

    // ── Saved Device ──────────────────────────────────────────

    fun getSavedDeviceAddress(): String? =
            prefs.getString(KEY_DEVICE_ADDRESS, null)?.takeIf { it.isNotBlank() }

    fun getSavedDeviceName(): String? =
            prefs.getString(KEY_DEVICE_NAME, null)?.takeIf { it.isNotBlank() }

    private fun saveDevice(name: String, address: String) {
        prefs.edit().putString(KEY_DEVICE_NAME, name).putString(KEY_DEVICE_ADDRESS, address).apply()
        _deviceName.value = name
    }

    fun clearSavedDevice() {
        prefs.edit().remove(KEY_DEVICE_NAME).remove(KEY_DEVICE_ADDRESS).apply()
        _deviceName.value = null
    }

    // ── Connect / Disconnect ──────────────────────────────────

    /** Connect to a known device by address. */
    fun connectToDevice(address: String, name: String? = null) {
        Log.i(TAG, "connectToDevice: $address ($name)")
        if (name != null) {
            saveDevice(name, address)
        }
        BleOperateManager.getInstance().connectDirectly(address)
    }

    /**
     * Try to auto-reconnect to the last saved device. Returns true if a saved device exists and
     * connection was attempted.
     */
    fun tryAutoReconnect(): Boolean {
        val address = getSavedDeviceAddress() ?: return false
        val name = getSavedDeviceName()
        Log.i(TAG, "tryAutoReconnect: $address ($name)")
        connectToDevice(address, name)
        return true
    }

    fun disconnect() {
        Log.i(TAG, "disconnect")
        BleOperateManager.getInstance().unBindDevice()
    }

    // ── BLE Scanning ──────────────────────────────────────────

    fun startScan() {
        _scannedDevices.value = emptyList()
        _isScanning.value = true

        BleScannerHelper.getInstance().reSetCallback()
        BleScannerHelper.getInstance()
                .scanDevice(
                        context,
                        null,
                        object : ScanWrapperCallback {
                            override fun onStart() {}

                            override fun onLeScan(
                                    device: BluetoothDevice?,
                                    rssi: Int,
                                    scanRecord: ByteArray?
                            ) {
                                if (device == null || device.name.isNullOrEmpty()) return

                                val scanned =
                                        ScannedDevice(
                                                name = device.name ?: "Unknown",
                                                address = device.address,
                                                rssi = rssi
                                        )

                                val current = _scannedDevices.value.toMutableList()
                                if (current.none { it.address == scanned.address }) {
                                    current.add(0, scanned)
                                    current.sortByDescending { it.rssi }
                                    _scannedDevices.value = current

                                    if (current.size > 30) {
                                        stopScan()
                                    }
                                }
                            }

                            override fun onStop() {
                                _isScanning.value = false
                            }

                            override fun onScanFailed(errorCode: Int) {
                                Log.e(TAG, "Scan failed: $errorCode")
                                _isScanning.value = false
                            }

                            override fun onParsedData(
                                    device: BluetoothDevice?,
                                    scanRecord: ScanRecord?
                            ) {}
                            override fun onBatchScanResults(results: MutableList<ScanResult>?) {}
                        }
                )

        // Auto-stop scan after 15 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ stopScan() }, 15_000)
    }

    fun stopScan() {
        BleScannerHelper.getInstance().stopScan(context)
        _isScanning.value = false
    }

    // ── Battery ───────────────────────────────────────────────

    fun requestBattery() {
        if (!BleOperateManager.getInstance().isConnected) return
        ensureBatteryCallback()
        LargeDataHandler.getInstance().syncBattery()
    }

    private fun ensureBatteryCallback() {
        if (batteryCallbackRegistered) return
        batteryCallbackRegistered = true

        LargeDataHandler.getInstance().addBatteryCallBack("compose") { _, response ->
            parseBatteryResponse(response)
        }
    }

    private fun parseBatteryResponse(response: Any?) {
        if (response == null) return
        try {
            val clazz = response.javaClass
            val batteryField = clazz.getDeclaredField("battery").apply { isAccessible = true }
            val chargingField = clazz.getDeclaredField("charging").apply { isAccessible = true }

            _batteryLevel.value = batteryField.getInt(response)
            _isCharging.value = chargingField.getBoolean(response)

            Log.i(TAG, "Battery: ${_batteryLevel.value}% charging=${_isCharging.value}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse battery response", e)
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    fun isConnected(): Boolean = BleOperateManager.getInstance().isConnected

    fun refreshConnectionState() {
        _connectionState.value = BleOperateManager.getInstance().isConnected
        if (_connectionState.value) {
            _deviceName.value = DeviceManager.getInstance().deviceName ?: getSavedDeviceName()
        }
    }
}
