package com.fersaiyan.cyanbridge.glasses

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import com.fersaiyan.cyanbridge.chat.ChatEngine
import com.fersaiyan.cyanbridge.chat.ChatSource
import com.fersaiyan.cyanbridge.ui.BluetoothEvent
import com.fersaiyan.cyanbridge.voice.AliyunAsrWakeSession
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.bluetooth.DeviceManager
import com.oudmon.ble.base.communication.LargeDataHandler
import com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyListener
import com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyRsp
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

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

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
        _isConnecting.value = false // Connection attempt resolved
        if (event.connect) {
            val name = DeviceManager.getInstance().deviceName
            _deviceName.value = name
            requestBattery()
            syncAiVoiceWake()
        } else {
            _batteryLevel.value = null
            _isCharging.value = null
        }
    }

    // ── Voice Wake ───────────────────────────────────────────

    private var lastVoiceWakeAtMs: Long = 0L
    private var aliyunAsr: AliyunAsrWakeSession? = null
    private var deviceNotifyRegistered = false

    /**
     * Register the DeviceNotify listener so we catch wake events (0x03) even from
     * ComposeMainActivity (the old listener was only in MainActivity).
     */
    private fun ensureDeviceNotifyListener() {
        if (deviceNotifyRegistered) return
        deviceNotifyRegistered = true
        Log.i(TAG, "Registering DeviceNotifyListener for wake events")
        LargeDataHandler.getInstance()
                .addOutDeviceListener(
                        100,
                        object : GlassesDeviceNotifyListener() {
                            override fun parseData(cmdType: Int, response: GlassesDeviceNotifyRsp) {
                                val data = response.loadData
                                val hex = data?.joinToString("") { "%02x".format(it) } ?: "null"
                                Log.i(
                                        TAG,
                                        "DeviceNotify: cmdType=0x${Integer.toHexString(cmdType)}, size=${data?.size}, hex=$hex"
                                )
                                try {
                                    // cmdType=0x73 is the AI/voice category.
                                    // Sub-command is at loadData[6], value at loadData[7].
                                    // bc730200c0800301 → sub=0x03 val=0x01 = voice wake triggered
                                    if (cmdType == 0x73 && data != null && data.size >= 8) {
                                        val subCmd = data[6].toInt() and 0xFF
                                        val value = data[7].toInt() and 0xFF
                                        Log.i(
                                                TAG,
                                                "  0x73 sub=0x${"%02x".format(subCmd)} val=$value"
                                        )
                                        if (subCmd == 0x03 && value == 1) {
                                            Log.i(TAG, "*** VOICE WAKE EVENT DETECTED ***")
                                            onVoiceWakeTriggered()
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "DeviceNotify parse error", e)
                                }
                            }
                        }
                )
    }

    private fun onVoiceWakeTriggered() {
        val now = System.currentTimeMillis()
        if (now - lastVoiceWakeAtMs < 800) {
            Log.i(TAG, "Wake debounce: skipped")
            return
        }
        lastVoiceWakeAtMs = now
        Log.i(TAG, ">>> Starting ASR from voice wake")

        // Lazily create the ASR session
        if (aliyunAsr == null) {
            Log.i(TAG, "Creating AliyunAsrWakeSession")
            aliyunAsr =
                    AliyunAsrWakeSession(
                            context = context,
                            onResult = { text ->
                                Log.i(TAG, "ASR result=$text")
                                ChatEngine.submitUserText(text, ChatSource.ASR)
                            },
                            onStatus = { message -> Log.i(TAG, "ASR status: $message") }
                    )
        }
        aliyunAsr?.start("wake")
    }

    private fun syncAiVoiceWake() {
        if (!BleOperateManager.getInstance().isConnected) return
        // Also ensure the DeviceNotify listener is registered
        ensureDeviceNotifyListener()
        Log.i(TAG, "Querying aiVoiceWake status...")
        LargeDataHandler.getInstance().aiVoiceWake(false, false) { _, rsp ->
            val isOpen =
                    try {
                        rsp?.javaClass
                                ?.getDeclaredField("isOpen")
                                ?.apply { isAccessible = true }
                                ?.getBoolean(rsp)
                                ?: false
                    } catch (_: Exception) {
                        false
                    }
            Log.i(TAG, "aiVoiceWake query: isOpen=$isOpen")
            if (!isOpen) {
                LargeDataHandler.getInstance().aiVoiceWake(true, true) { _, rsp2 ->
                    val isOpen2 =
                            try {
                                rsp2?.javaClass
                                        ?.getDeclaredField("isOpen")
                                        ?.apply { isAccessible = true }
                                        ?.getBoolean(rsp2)
                                        ?: false
                            } catch (_: Exception) {
                                false
                            }
                    Log.i(TAG, "aiVoiceWake set(true): isOpen=$isOpen2")
                }
            }
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
        _isConnecting.value = true
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

    // ── Media Control ─────────────────────────────────────────

    enum class GlassesMode {
        IDLE,
        CAMERA,
        VIDEO_RECORDING,
        AUDIO_RECORDING,
        TRANSFER,
        OTA,
        AI_CONVERSATION
    }

    private val _glassesMode = MutableStateFlow(GlassesMode.IDLE)
    val glassesMode: StateFlow<GlassesMode> = _glassesMode.asStateFlow()

    private val _lastActionResult = MutableStateFlow<String?>(null)
    val lastActionResult: StateFlow<String?> = _lastActionResult.asStateFlow()

    fun clearActionResult() {
        _lastActionResult.value = null
    }

    fun takePhoto() {
        if (!BleOperateManager.getInstance().isConnected) return
        Log.i(TAG, "takePhoto")
        LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x01)) { _, rsp ->
            logMediaResponse(rsp, "拍照")
        }
        // Photo is instant — don't change glassesMode, just show toast
        _lastActionResult.value = "拍照 成功"
    }

    fun startVideoRecording() {
        if (!BleOperateManager.getInstance().isConnected) return
        Log.i(TAG, "startVideoRecording")
        _glassesMode.value = GlassesMode.VIDEO_RECORDING
        LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x02)) { _, rsp ->
            logMediaResponse(rsp, "开始录像")
        }
        _lastActionResult.value = "开始录像"
    }

    fun stopVideoRecording() {
        if (!BleOperateManager.getInstance().isConnected) return
        Log.i(TAG, "stopVideoRecording")
        _glassesMode.value = GlassesMode.IDLE
        LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x03)) { _, rsp ->
            logMediaResponse(rsp, "停止录像")
        }
        _lastActionResult.value = "停止录像"
    }

    fun startAudioRecording() {
        if (!BleOperateManager.getInstance().isConnected) return
        Log.i(TAG, "startAudioRecording")
        _glassesMode.value = GlassesMode.AUDIO_RECORDING
        LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x08)) { _, rsp ->
            logMediaResponse(rsp, "开始录音")
        }
        _lastActionResult.value = "开始录音"
    }

    fun stopAudioRecording() {
        if (!BleOperateManager.getInstance().isConnected) return
        Log.i(TAG, "stopAudioRecording")
        _glassesMode.value = GlassesMode.IDLE
        LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x0c)) { _, rsp ->
            logMediaResponse(rsp, "停止录音")
        }
        _lastActionResult.value = "停止录音"
    }

    private fun logMediaResponse(rsp: Any?, action: String) {
        try {
            val clazz = rsp?.javaClass ?: return
            val dataType =
                    clazz.getDeclaredField("dataType").apply { isAccessible = true }.getInt(rsp)
            val errorCode =
                    clazz.getDeclaredField("errorCode").apply { isAccessible = true }.getInt(rsp)
            val workTypeIng =
                    clazz.getDeclaredField("workTypeIng").apply { isAccessible = true }.getInt(rsp)
            Log.i(
                    TAG,
                    "$action response: dataType=$dataType, errorCode=$errorCode, workTypeIng=$workTypeIng"
            )
        } catch (e: Exception) {
            Log.e(TAG, "$action response parse error", e)
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
