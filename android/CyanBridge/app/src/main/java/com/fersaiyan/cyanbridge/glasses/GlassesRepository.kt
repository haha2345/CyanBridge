package com.fersaiyan.cyanbridge.glasses

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.Inflater

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

    // ── Vision thumbnail capture signal ──
    @Volatile private var visionPhotoSignal: CompletableDeferred<Unit>? = null
    private val captureTimeoutMs = 30_000L
    private val maxCaptureRetries = 2

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
                                        when (subCmd) {
                                            0x03 -> {
                                                if (value == 1) {
                                                    Log.i(TAG, "*** VOICE WAKE EVENT DETECTED ***")
                                                    onVoiceWakeTriggered()
                                                }
                                            }
                                            // Photo captured (0x01) or AI Photo (0x02)
                                            0x01,
                                            0x02 -> {
                                                Log.i(
                                                        TAG,
                                                        "Photo/AI-Photo signal: sub=0x${"%02x".format(subCmd)}"
                                                )
                                                visionPhotoSignal?.let { signal ->
                                                    if (!signal.isCompleted) signal.complete(Unit)
                                                }
                                            }
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

    /**
     * 拍照+获取缩略图。从眼镜摄像头拍一张照片并通过 BLE 接收 JPEG 缩略图。 参照 MainActivity.startAiVisionMvp() 的已验证逻辑。
     * @return 本地 JPEG 文件, 失败返回 null
     */
    suspend fun captureAndGetThumbnail(): File? =
            withContext(Dispatchers.IO) {
                val VTAG = "VisionCapture"
                if (!BleOperateManager.getInstance().isConnected) {
                    Log.e(VTAG, "captureAndGetThumbnail: BLE not connected")
                    return@withContext null
                }

                Log.i(VTAG, "╔══════════════════════════════════════════════════╗")
                Log.i(VTAG, "║ captureAndGetThumbnail START (maxRetries=$maxCaptureRetries) ║")
                Log.i(VTAG, "╚══════════════════════════════════════════════════╝")

                for (attempt in 0..maxCaptureRetries) {
                    if (attempt > 0) {
                        val backoffMs = 800L + (attempt - 1) * 700L
                        Log.i(VTAG, "── Retry #$attempt after ${backoffMs}ms backoff ──")
                        delay(backoffMs)
                    }
                    val file = doSingleCaptureAndGetThumbnail(attempt)
                    if (file != null) {
                        Log.i(VTAG, "✓ captureAndGetThumbnail SUCCESS on attempt=$attempt: ${file.absolutePath}")
                        return@withContext file
                    }
                    Log.w(VTAG, "✗ attempt=$attempt produced no usable image")
                }
                Log.e(VTAG, "✗ ALL ${maxCaptureRetries + 1} capture attempts exhausted")
                null
            }

    /**
     * Single capture-and-thumbnail attempt with detailed logging.
     * Returns a usable JPEG File, or null to trigger retry.
     */
    private suspend fun doSingleCaptureAndGetThumbnail(attempt: Int): File? {
        val VTAG = "VisionCapture"
        val startMs = System.currentTimeMillis()
        Log.i(VTAG, "═══ doSingleCapture START attempt=$attempt time=$startMs ═══")

        val outDir = File(context.cacheDir, "ai_vision_mvp").apply { mkdirs() }
        val outFile = File(outDir, "ai_${startMs}.jpg")
        val buf = ByteArrayOutputStream()
        val done = CompletableDeferred<Boolean>()
        val photoSignal = CompletableDeferred<Unit>()
        visionPhotoSignal = photoSignal
        var hadData = false
        var chunkCount = 0
        var lastChunkTimeMs = 0L
        var firstChunkTimeMs = 0L

        val receivedPages = sortedMapOf<Int, ByteArray>()
        val seenHashes = mutableSetOf<Long>()
        var totalPagesFromProtocol = -1
        var usesPageProtocol = false

        try {
            // ── 1. Drain stale data ──
            Log.i(VTAG, "[1/6] Draining stale thumbnail data...")
            val drainDone = CompletableDeferred<Unit>()
            var drainedCount = 0
            var drainedBytes = 0
            LargeDataHandler.getInstance().getPictureThumbnails { _, isComplete, data ->
                if (data != null && data.isNotEmpty()) {
                    drainedCount++
                    drainedBytes += data.size
                    val hex = data.take(16).joinToString("") { "%02x".format(it) }
                    Log.d(VTAG, "  drain chunk #$drainedCount: ${data.size}B hex=$hex")
                }
                if (isComplete) {
                    if (!drainDone.isCompleted) drainDone.complete(Unit)
                }
            }
            withTimeoutOrNull(300L) { drainDone.await() }
            Log.i(VTAG, "[1/6] Drain done: $drainedCount chunks, $drainedBytes bytes discarded")

            // ── 2. Send capture command ──
            Log.i(VTAG, "[2/6] Sending capture command...")
            val thumbnailSize: Byte = 0x02
            val captureAck = CompletableDeferred<Int?>()
            val captureSendMs = System.currentTimeMillis()
            LargeDataHandler.getInstance().glassesControl(
                            byteArrayOf(0x02, 0x01, 0x06, thumbnailSize, thumbnailSize, 0x02)
                    ) { _, resp ->
                val ackMs = System.currentTimeMillis()
                Log.i(VTAG, "  capture ACK: dataType=${resp.dataType}, error=${resp.errorCode}, latency=${ackMs - captureSendMs}ms")
                if (!captureAck.isCompleted) captureAck.complete(resp.errorCode)
            }

            val captureErr = withTimeoutOrNull(2000L) { captureAck.await() }
            when {
                captureErr == null -> Log.w(VTAG, "[2/6] capture ACK TIMEOUT (2s)")
                captureErr != 0 -> {
                    Log.w(VTAG, "[2/6] capture ACK error=$captureErr")
                    if (attempt == 0) {
                        Log.i(VTAG, "  warm-up delay 600ms...")
                        delay(600)
                    }
                }
                else -> Log.i(VTAG, "[2/6] capture ACK OK")
            }

            // ── 3. Wait for photo signal ──
            Log.i(VTAG, "[3/6] Waiting for photo signal (12s timeout)...")
            val sigStart = System.currentTimeMillis()
            val gotSignal = withTimeoutOrNull(12_000L) { photoSignal.await() } != null
            val sigMs = System.currentTimeMillis() - sigStart
            if (!gotSignal) {
                Log.w(VTAG, "[3/6] Photo signal NOT received (${sigMs}ms)")
            } else {
                Log.i(VTAG, "[3/6] Photo signal received (${sigMs}ms)")
                val postDelay = if (attempt == 0) 1200L else 1500L
                Log.i(VTAG, "  stabilization delay ${postDelay}ms...")
                delay(postDelay)
            }

            // ── 4. Receive thumbnail chunks ──
            Log.i(VTAG, "[4/6] Registering getPictureThumbnails callback...")
            val cbRegMs = System.currentTimeMillis()
            LargeDataHandler.getInstance().getPictureThumbnails { _, isComplete, data ->
                val nowMs = System.currentTimeMillis()
                val dt = nowMs - cbRegMs
                if (done.isCompleted) {
                    Log.d(VTAG, "  LATE chunk: ${data?.size ?: 0}B isComplete=$isComplete +${dt}ms")
                    return@getPictureThumbnails
                }
                if (data != null && data.isNotEmpty()) {
                    val hex = data.take(16).joinToString("") { "%02x".format(it) }
                    if (data.size > 11 && data[0] == 0xBC.toByte() && data[1] == 0xFD.toByte()) {
                        usesPageProtocol = true
                        val pg = data[9].toInt() and 0xFF
                        totalPagesFromProtocol = data[7].toInt() and 0xFF
                        if (pg in receivedPages) {
                            Log.d(VTAG, "  chunk: DUP page $pg/$totalPagesFromProtocol ${data.size}B +${dt}ms")
                            return@getPictureThumbnails
                        }
                        val payload = data.copyOfRange(11, data.size)
                        receivedPages[pg] = payload
                        hadData = true; chunkCount++
                        if (firstChunkTimeMs == 0L) firstChunkTimeMs = nowMs
                        lastChunkTimeMs = nowMs
                        Log.i(VTAG, "  chunk #$chunkCount: PAGE $pg/$totalPagesFromProtocol ${payload.size}B hex=$hex +${dt}ms")
                    } else {
                        val fp = chunkFingerprint(data)
                        if (!seenHashes.add(fp)) {
                            Log.d(VTAG, "  chunk: DUP hash ${data.size}B +${dt}ms")
                            return@getPictureThumbnails
                        }
                        hadData = true; chunkCount++
                        if (firstChunkTimeMs == 0L) firstChunkTimeMs = nowMs
                        lastChunkTimeMs = nowMs
                        Log.i(VTAG, "  chunk #$chunkCount: RAW ${data.size}B hex=$hex +${dt}ms bufTotal=${buf.size() + data.size}")
                        try { buf.write(data) } catch (e: Exception) {
                            Log.e(VTAG, "  write error: ${e.message}")
                            done.complete(false)
                            return@getPictureThumbnails
                        }
                    }
                } else {
                    Log.d(VTAG, "  chunk: EMPTY isComplete=$isComplete +${dt}ms")
                }
                if (isComplete) {
                    val total = if (usesPageProtocol) receivedPages.values.sumOf { it.size } else buf.size()
                    Log.i(VTAG, "  ✓ isComplete: chunks=$chunkCount bytes=$total pages=${receivedPages.size} +${dt}ms")
                    done.complete(hadData)
                }
            }

            // 5. Wait for completion
            Log.i(VTAG, "[5/6] Waiting for data (timeout=${captureTimeoutMs}ms)...")
            val idleTimeoutMs = 5_000L
            val waitStart = System.currentTimeMillis()
            val result = withTimeoutOrNull(captureTimeoutMs) {
                val completed = withTimeoutOrNull(captureTimeoutMs - idleTimeoutMs) { done.await() }
                if (completed == true) {
                    Log.i(VTAG, "  SDK done after ${System.currentTimeMillis() - waitStart}ms")
                    return@withTimeoutOrNull true
                }
                if (hadData && lastChunkTimeMs > 0) {
                    val elapsed = System.currentTimeMillis() - lastChunkTimeMs
                    Log.w(VTAG, "  No SDK done; lastChunk ${elapsed}ms ago, chunks=$chunkCount")
                    if (elapsed >= idleTimeoutMs) {
                        Log.w(VTAG, "  → idle timeout, treating as complete")
                        return@withTimeoutOrNull true
                    }
                    val extra = idleTimeoutMs - elapsed + 500
                    Log.i(VTAG, "  → waiting ${extra}ms more...")
                    delay(extra)
                    Log.w(VTAG, "  → idle timeout after extra wait (chunks=$chunkCount)")
                    return@withTimeoutOrNull true
                }
                Log.w(VTAG, "  No data received at all")
                false
            }
            val waited = System.currentTimeMillis() - waitStart
            Log.i(VTAG, "[5/6] result=$result hadData=$hadData chunks=$chunkCount waited=${waited}ms")
            if (firstChunkTimeMs > 0) {
                Log.i(VTAG, "  firstChunk=+${firstChunkTimeMs - cbRegMs}ms lastChunk=+${lastChunkTimeMs - cbRegMs}ms")
            }

            // 6. Reassemble & validate
            Log.i(VTAG, "[6/6] Reassembling... pageProto=$usesPageProtocol pages=${receivedPages.size} buf=${buf.size()}")
            val bytes = if (usesPageProtocol && receivedPages.isNotEmpty()) {
                val assembled = ByteArrayOutputStream()
                val maxPage = totalPagesFromProtocol.takeIf { it > 0 }
                    ?: ((receivedPages.keys.maxOrNull() ?: 0) + 1)
                val missing = mutableListOf<Int>()
                for (page in 0 until maxPage) {
                    receivedPages[page]?.let { assembled.write(it) } ?: missing.add(page)
                }
                if (missing.isNotEmpty()) Log.w(VTAG, "  Missing pages: $missing/$maxPage")
                Log.i(VTAG, "  Assembled ${receivedPages.size}/$maxPage pages, ${assembled.size()} bytes")
                assembled.toByteArray()
            } else {
                Log.i(VTAG, "  Using raw buf: ${buf.size()} bytes")
                buf.toByteArray()
            }

            if (result != true || bytes.isEmpty()) {
                if (hadData && bytes.isNotEmpty()) {
                    Log.w(VTAG, "  Timeout but have ${bytes.size} bytes — trying to use")
                } else {
                    Log.e(VTAG, "  ✗ FAILED: no data (result=$result hadData=$hadData)")
                    Log.i(VTAG, "═══ doSingleCapture END attempt=$attempt FAILED ${System.currentTimeMillis() - startMs}ms ═══")
                    return null
                }
            }

            // Magic bytes
            val head = bytes.take(32).joinToString("") { "%02x".format(it) }
            val tail = bytes.takeLast(16).joinToString("") { "%02x".format(it) }
            Log.i(VTAG, "  Raw: ${bytes.size}B head=$head tail=$tail")

            // JPEG extraction
            Log.i(VTAG, "  Extracting JPEG...")
            val jpegBytes = tryExtractJpeg(bytes)
            if (jpegBytes != null) {
                Log.i(VTAG, "  JPEG extracted: ${jpegBytes.size} bytes")
                val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                if (bitmap == null) {
                    Log.w(VTAG, "  ✗ decode → null")
                    Log.i(VTAG, "═══ doSingleCapture END attempt=$attempt FAILED (decode) ${System.currentTimeMillis() - startMs}ms ═══")
                    return null
                }
                Log.i(VTAG, "  Bitmap: ${bitmap.width}x${bitmap.height} config=${bitmap.config}")
                if (!isImageUsable(bitmap)) {
                    Log.w(VTAG, "  ✗ quality check FAILED")
                    bitmap.recycle()
                    Log.i(VTAG, "═══ doSingleCapture END attempt=$attempt FAILED (quality) ${System.currentTimeMillis() - startMs}ms ═══")
                    return null
                }
                bitmap.recycle()
                Log.i(VTAG, "  ✓ quality OK")

                FileOutputStream(outFile).use { it.write(jpegBytes) }
                Log.i(VTAG, "  Saved: ${outFile.absolutePath} (${outFile.length()} bytes)")
                Log.i(VTAG, "═══ doSingleCapture END attempt=$attempt SUCCESS ${System.currentTimeMillis() - startMs}ms ═══")
                return outFile
            } else {
                Log.w(VTAG, "  ✗ No JPEG in ${bytes.size} raw bytes")
                try {
                    val debugFile = File(outDir, "debug_raw_${System.currentTimeMillis()}.bin")
                    FileOutputStream(debugFile).use { it.write(bytes) }
                    Log.i(VTAG, "  Debug dump: ${debugFile.absolutePath} (${bytes.size} bytes)")
                } catch (_: Exception) {}
                Log.i(VTAG, "═══ doSingleCapture END attempt=$attempt FAILED (no JPEG) ${System.currentTimeMillis() - startMs}ms ═══")
                return null
            }
        } catch (e: Exception) {
            Log.e(VTAG, "═══ doSingleCapture EXCEPTION attempt=$attempt ${System.currentTimeMillis() - startMs}ms ═══", e)
            return null
        } finally {
            visionPhotoSignal = null
            try { buf.close() } catch (_: Exception) {}
        }
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

    /**
     * 尝试从原始 BLE 字节中提取合法 JPEG 数据。
     * 新型号眼镜可能发送 zlib 压缩或带私有头的数据。
     * 还会处理 BLE SDK 重复发送分块导致的数据重复问题。
     * 返回 JPEG 字节，或 null 如果无法提取。
     */
    private fun tryExtractJpeg(raw: ByteArray): ByteArray? {
        if (raw.size < 2) return null

        // 1. 已经是 JPEG (FF D8)
        if (raw[0] == 0xFF.toByte() && raw[1] == 0xD8.toByte()) {
            Log.i(TAG, "▶ Data is already JPEG (${raw.size} bytes)")
            return stripDuplicateJpeg(raw)
        }

        // 2. 尝试 zlib 解压 (标准 zlib 头: 78 xx)
        if (raw[0] == 0x78.toByte()) {
            Log.i(TAG, "▶ Detected zlib header (0x${ "%02x%02x".format(raw[0], raw[1])}), inflating...")
            val inflated = tryInflate(raw, nowrap = false)
            if (inflated != null) {
                val sub = findJpegInBytes(inflated)
                if (sub != null) return sub
                // 解压成功但不是 JPEG — 可能是其他格式，返回解压后的数据
                Log.w(TAG, "▶ zlib inflated ${raw.size} → ${inflated.size} bytes, but no JPEG SOI found")
                return inflated
            }
        }

        // 3. 尝试 raw deflate (跳过任何前导字节)
        for (skip in 0..minOf(16, raw.size - 2)) {
            val inflated = tryInflate(raw, nowrap = true, offset = skip)
            if (inflated != null && inflated.size > raw.size / 2) {
                Log.i(TAG, "▶ raw deflate OK (skip=$skip): ${raw.size} → ${inflated.size} bytes")
                val sub = findJpegInBytes(inflated)
                if (sub != null) return sub
                return inflated
            }
        }

        // 4. 在原始数据中搜索 JPEG SOI 标记 (FF D8 FF)
        val sub = findJpegInBytes(raw)
        if (sub != null) return sub

        Log.w(TAG, "▶ No JPEG found in ${raw.size} bytes")
        return null
    }

    /** 在字节数组中搜索 JPEG SOI (FF D8 FF) 标记，找到后去重 */
    private fun findJpegInBytes(data: ByteArray): ByteArray? {
        for (i in 0 until minOf(data.size - 2, 1024)) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD8.toByte() && data[i + 2] == 0xFF.toByte()) {
                Log.i(TAG, "▶ Found JPEG SOI at offset $i")
                val jpeg = data.copyOfRange(i, data.size)
                return stripDuplicateJpeg(jpeg)
            }
        }
        return null
    }

    /**
     * 去除 BLE SDK 重复发送分块导致的 JPEG 数据重复。
     * SDK 收到 ~10 个分块后会重新请求 chunk 0，导致整个图像数据
     * 被追加两次。检测第二个 JFIF 头 (FF D8 FF) 并截断。
     */
    private fun stripDuplicateJpeg(jpeg: ByteArray): ByteArray {
        if (jpeg.size < 4) return jpeg
        // 跳过第一个 SOI (前 2 字节)，从 offset 2 开始搜索第二个 FF D8 FF
        for (i in 2 until jpeg.size - 2) {
            if (jpeg[i] == 0xFF.toByte() && jpeg[i + 1] == 0xD8.toByte() && jpeg[i + 2] == 0xFF.toByte()) {
                Log.w(TAG, "▶ Duplicate JPEG SOI at offset $i! Truncating ${jpeg.size} → $i bytes")
                return jpeg.copyOfRange(0, i)
            }
        }
        return jpeg
    }

    /** 尝试 Inflate 解压 */
    private fun tryInflate(data: ByteArray, nowrap: Boolean, offset: Int = 0): ByteArray? {
        if (offset >= data.size) return null
        try {
            val inflater = Inflater(nowrap)
            inflater.setInput(data, offset, data.size - offset)
            val out = ByteArrayOutputStream(data.size * 4)
            val buf = ByteArray(8192)
            while (!inflater.finished()) {
                val count = inflater.inflate(buf)
                if (count == 0 && inflater.needsInput()) break
                out.write(buf, 0, count)
            }
            inflater.end()
            val result = out.toByteArray()
            if (result.isNotEmpty()) {
                return result
            }
        } catch (e: Exception) {
            // Silently fail — caller will try other methods
        }
        return null
    }

    /** 快速内容指纹，用于去重分块 */
    private fun chunkFingerprint(data: ByteArray): Long {
        if (data.isEmpty()) return 0L
        var h = data.size.toLong()
        val n = minOf(64, data.size)
        for (i in 0 until n) { h = h * 31 + (data[i].toLong() and 0xFF) }
        if (data.size > n) {
            for (i in maxOf(0, data.size - 64) until data.size) {
                h = h * 31 + (data[i].toLong() and 0xFF)
            }
        }
        return h
    }

    /**
     * Validate that a decoded image contains actual scene content rather than
     * garbled sensor noise (horizontal color bars from an unready camera).
     *
     * Strategy:
     *  - Sample pixels across the image in a grid
     *  - Compute brightness standard deviation (garbled frames have very low variance)
     *  - Check for horizontal edge contrast (real scenes have edges; color bars do not)
     *
     * Returns true if the image looks like a real photograph.
     */
    private fun isImageUsable(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 10 || h < 10) return false  // too small to analyze

        // Sample ~200 pixels in a grid
        val gridStepX = maxOf(1, w / 14)
        val gridStepY = maxOf(1, h / 14)
        val brightness = mutableListOf<Double>()
        var edgeCount = 0
        var prevRow = -1
        var prevBr = 0.0

        var y = gridStepY / 2
        while (y < h) {
            var x = gridStepX / 2
            while (x < w) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val br = 0.299 * r + 0.587 * g + 0.114 * b
                brightness.add(br)

                // Check vertical edge (same column, adjacent row)
                if (prevRow == y && kotlin.math.abs(br - prevBr) > 30) {
                    edgeCount++
                }
                prevBr = br
                prevRow = y
                x += gridStepX
            }
            y += gridStepY
        }

        if (brightness.size < 20) return false

        val mean = brightness.average()
        val variance = brightness.sumOf { (it - mean) * (it - mean) } / brightness.size
        val stdDev = kotlin.math.sqrt(variance)
        val edgeRatio = edgeCount.toDouble() / brightness.size

        Log.i(TAG, "Image quality: stdDev=%.1f edgeRatio=%.3f samples=${brightness.size}".format(stdDev, edgeRatio))

        // Garbled frames typically have stdDev < 15 and edgeRatio < 0.05.
        // Real scenes usually have stdDev > 25 and edgeRatio > 0.10.
        if (stdDev < 12.0) {
            Log.w(TAG, "Image appears blank/uniform (stdDev=%.1f)".format(stdDev))
            return false
        }
        if (stdDev < 20.0 && edgeRatio < 0.05) {
            Log.w(TAG, "Image appears garbled (low variance + few edges)")
            return false
        }
        return true
    }

    fun isConnected(): Boolean = BleOperateManager.getInstance().isConnected

    fun refreshConnectionState() {
        _connectionState.value = BleOperateManager.getInstance().isConnected
        if (_connectionState.value) {
            _deviceName.value = DeviceManager.getInstance().deviceName ?: getSavedDeviceName()
        }
    }
}
