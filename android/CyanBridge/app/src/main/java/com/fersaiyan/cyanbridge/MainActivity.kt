package com.fersaiyan.cyanbridge

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import com.oudmon.ble.base.communication.utils.ByteUtil
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.bluetooth.DeviceManager
import com.oudmon.ble.base.communication.LargeDataHandler
import com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyListener
import com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyRsp
import com.fersaiyan.cyanbridge.databinding.AcitivytMainBinding
import com.fersaiyan.cyanbridge.ui.DeviceBindActivity
import com.fersaiyan.cyanbridge.ui.BluetoothUtils
import com.fersaiyan.cyanbridge.ui.BluetoothEvent
import com.fersaiyan.cyanbridge.ui.bleIpBridge
import com.fersaiyan.cyanbridge.ui.hasBluetooth
import com.fersaiyan.cyanbridge.ui.requestAllPermission
import com.fersaiyan.cyanbridge.ui.requestBluetoothPermission
import com.fersaiyan.cyanbridge.ui.requestLocationPermission
import com.fersaiyan.cyanbridge.ui.requestNearbyWifiDevicesPermission
import com.fersaiyan.cyanbridge.ui.setOnClickListener
import com.fersaiyan.cyanbridge.ui.startKtxActivity
import com.fersaiyan.cyanbridge.ui.wifi.p2p.WifiP2pManagerSingleton
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.ConnectivityManager
import android.net.Network
import android.provider.MediaStore
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.os.Environment
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import com.fersaiyan.cyanbridge.ui.BatteryOptimizationGuideActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import androidx.core.content.ContextCompat
import java.net.URL
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory
import java.text.SimpleDateFormat
import java.util.Locale
import java.security.SecureRandom
import androidx.core.content.FileProvider
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.coroutineContext

import android.provider.Settings
import android.net.Uri
import android.app.KeyguardManager

import android.speech.tts.TextToSpeech
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.graphics.BitmapFactory

import com.fersaiyan.cyanbridge.voice.GlassesVoiceChatMvp
import com.fersaiyan.cyanbridge.voice.OpenAiClient


class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    companion object {
        const val EXTRA_TASKER_COMMAND = "tasker_command"
        private var loggedLargeDataHandlerMethods = false

        fun actionTaskerCommand(appPackageName: String): String =
            "$appPackageName.ACTION_TASKER_COMMAND"

        fun aiEventAction(appPackageName: String): String =
            "$appPackageName.AI_EVENT"

        // Edit this URL before using the pull-mode OTA test button.
        // In the official app, the phone runs an HTTP server on its own
        // Wi‑Fi Direct address and the glasses fetch the file from there.
        // For experiments you can point this at a simple `python -m http.server`
        // instance on the phone or on a reachable host.
        private const val TEST_PULL_OTA_URL =
            "http://192.168.49.1:8080/dummy.swu"
    }

    private lateinit var binding: AcitivytMainBinding
    private val deviceNotifyListener by lazy { MyDeviceNotifyListener() }

    // AI Hijack settings
    private var isAiHijackEnabled = true // Default to enabled
    private var isImageAssistantMode = true // Use assistant vs share intent
    private var aiAssistantMode = "Gemini" // "Gemini" or "ChatGPT"

    // AI Vision MVP state (capture -> getPictureThumbnails -> preview page)
    private var aiVisionMvpJob: Job? = null
    private val aiVisionMvpTimeoutMs = 30_000L
    private var aiVisionMvpPhotoSignal: CompletableDeferred<Unit>? = null

    // State used by the BLE+WiFi P2P data-download flow
    private var downloadP2pConnected = false
    private var downloadBleIp: String? = null
    private var downloadWifiIp: String? = null
    private var downloadInProgress = false
    private var downloadAttemptJob: Job? = null
    private var downloadResolvedHttpIp: String? = null
    private var downloadP2pNetwork: Network? = null
    private var boundNetwork: Network? = null
    private var lastP2pResetAtMs: Long = 0L
    private var downloadWifiP2pManager: WifiP2pManagerSingleton? = null
    private var downloadWifiP2pCallback: WifiP2pManagerSingleton.WifiP2pCallback? = null

    // Official app registers the notify listener with cmdType=2 for album import.
    // Keep our main listener (cmdType=100) for general events, and add a narrow
    // one for the download flow so we don't duplicate thumbnail/audio handling.
    private val downloadNotifyListener by lazy { DownloadNotifyListener() }
    private var downloadNotifyListenerRegistered = false
    private var batteryPollJob: Job? = null
    private val batteryPollIntervalMs = 60_000L
    private var pendingBatteryToast = false
    private var batteryCallbackRegistered = false

    private val voiceChatMvp by lazy {
        GlassesVoiceChatMvp(
            context = this,
            openAi = OpenAiClient(),
            speak = ::speak,
            onStatus = ::voiceStatus,
        )
    }

    private fun voiceStatus(message: String) {
        Log.i("VoiceMVP", message)
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AcitivytMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
        logLargeDataHandlerMethodsOnce()
        // Initialize TTS
        tts = TextToSpeech(this, this)
        
        // Ensure we always listen for glasses reports (battery, AI, volume, etc.)
        LargeDataHandler.getInstance().addOutDeviceListener(100, deviceNotifyListener)

        // Lazily register the import/download notify listener the first time we need it.
        handleTaskerCommand(intent)

        BatteryOptimizationGuideActivity.launchIfNeeded(this)
    }

    override fun onStart() {
        super.onStart()
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
        updateConnectionStatus(BleOperateManager.getInstance().isConnected)
        startBatteryPolling()
    }

    override fun onStop() {
        super.onStop()
        stopBatteryPolling()
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        aiVisionMvpJob?.cancel()
        voiceChatMvp.shutdown()
        tts?.stop()
        tts?.shutdown()
    }
    inner class PermissionCallback : OnPermissionCallback {
        override fun onGranted(permissions: MutableList<String>, all: Boolean) {
            if (!all) {
                // Permissions not fully granted; do nothing for now
            } else {
                this@MainActivity.startKtxActivity<DeviceBindActivity>()
            }
        }

        override fun onDenied(permissions: MutableList<String>, never: Boolean) {
            super.onDenied(permissions, never)
            if(never){
                XXPermissions.startPermissionActivity(this@MainActivity, permissions);
            }
        }

    }


    override fun onResume() {
        super.onResume()
        try {
            if (!BluetoothUtils.isEnabledBluetooth(this)) {
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                }
                startActivityForResult(intent, 300)
            }
        } catch (e: Exception) {
        }
        if (!hasBluetooth(this)) {
            requestBluetoothPermission(this, BluetoothPermissionCallback())
        }

        requestAllPermission(this, OnPermissionCallback { permissions, all ->  })

        // Check for Overlay permission needed for background launch
        if (isAiHijackEnabled && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, 1234)
            Toast.makeText(this, "Please enable Overlay permission for background AI", Toast.LENGTH_LONG).show()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTaskerCommand(intent)
    }

    inner class BluetoothPermissionCallback : OnPermissionCallback {
        override fun onGranted(permissions: MutableList<String>, all: Boolean) {
            if (!all) {

            }
        }

        override fun onDenied(permissions: MutableList<String>, never: Boolean) {
            super.onDenied(permissions, never)
            if (never) {
                XXPermissions.startPermissionActivity(this@MainActivity, permissions)
            }
        }

    }

    private fun initView() {
        setOnClickListener(
            binding.btnScan,
            binding.btnConnect,
            binding.btnDisconnect,
            binding.btnAddListener,
            binding.btnSetTime,
            binding.btnVersion,
            binding.btnCamera,
            binding.btnVideo,
            binding.btnRecord,
            binding.btnBt,
            binding.btnBattery,
            binding.btnVolume,
            binding.btnMediaCount,
            binding.btnDataDownload,
            binding.btnOtaInfo,
            binding.btnPullOtaTest,
            binding.btnModeGemini,
            binding.btnModeChatgpt,
            binding.btnModeTasker,
            binding.btnTestHijackVoice,
            binding.btnTestHijackImage,
            binding.btnAiVisionMvp
        ) {
            when (this) {
                binding.btnAiVisionMvp -> {
                    startAiVisionMvp()
                }

                binding.btnTestHijackVoice -> {
                    triggerAssistantVoiceQuery()
                }

                binding.btnTestHijackImage -> {
                    // Create a dummy image for testing if none exists
                    val testFile = File(getExternalFilesDir("DCIM"), "test_ai.jpg")
                    if (!testFile.exists()) {
                        try {
                            testFile.writeText("dummy image data")
                        } catch (e: Exception) {}
                    }
                    triggerAssistantImageQuery(testFile.absolutePath)
                }

                binding.btnModeGemini -> {
                    aiAssistantMode = "Gemini"
                    binding.btnModeGemini.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.cyan_accent))
                    binding.btnModeChatgpt.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    binding.btnModeTasker.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    Toast.makeText(this@MainActivity, "AI Mode: Google Gemini", Toast.LENGTH_SHORT).show()
                }

                binding.btnModeChatgpt -> {
                    aiAssistantMode = "ChatGPT"
                    binding.btnModeGemini.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    binding.btnModeChatgpt.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.cyan_accent))
                    binding.btnModeTasker.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    Toast.makeText(this@MainActivity, "AI Mode: ChatGPT", Toast.LENGTH_SHORT).show()
                }

                binding.btnModeTasker -> {
                    aiAssistantMode = "Tasker"
                    binding.btnModeGemini.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    binding.btnModeChatgpt.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    binding.btnModeTasker.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.cyan_accent))
                    Toast.makeText(this@MainActivity, "AI Mode: Tasker Broadcast", Toast.LENGTH_SHORT).show()
                }

                binding.btnScan -> {
                    requestLocationPermission(this@MainActivity, PermissionCallback())
                }

                binding.btnConnect -> {
                    Toast.makeText(this@MainActivity, "Reconnecting to glasses…", Toast.LENGTH_SHORT).show()
                    BleOperateManager.getInstance()
                        .connectDirectly(DeviceManager.getInstance().deviceAddress)
                }

                binding.btnDisconnect -> {
                    Toast.makeText(this@MainActivity, "Disconnecting from glasses…", Toast.LENGTH_SHORT).show()
                    BleOperateManager.getInstance().unBindDevice()
                }

                binding.btnAddListener -> {
                    Toast.makeText(this@MainActivity, "Registering device event listener…", Toast.LENGTH_SHORT).show()
                    LargeDataHandler.getInstance().addOutDeviceListener(100, deviceNotifyListener)
                }

                binding.btnSetTime -> {
                    Toast.makeText(this@MainActivity, "Syncing glasses time…", Toast.LENGTH_SHORT).show()
                    Log.i("setTime", "setTime" + BleOperateManager.getInstance().isConnected)
                    LargeDataHandler.getInstance().syncTime { _, _ -> }
                }

                binding.btnVersion -> {
                    Toast.makeText(this@MainActivity, "Reading device version…", Toast.LENGTH_SHORT).show()
                    LargeDataHandler.getInstance().syncDeviceInfo { _, response ->
                        if (response != null) {
                            val message =
                                "WiFi FW: ${response.wifiFirmwareVersion}, BT FW: ${response.firmwareVersion}"
                            Log.i("DeviceInfo", message)
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                            }
                        } else {
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Failed to get device version",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }

                binding.btnCamera -> {
                    LargeDataHandler.getInstance().glassesControl(
                        byteArrayOf(0x02, 0x01, 0x01)
                    ) { _, it ->
                        if (it.dataType == 1 && it.errorCode == 0) {
                            when (it.workTypeIng) {
                                2 -> {
                                    //Glasses are recording video
                                }
                                4 -> {
                                    //Glasses are in transfer mode
                                }
                                5 -> {
                                    //Glasses are in OTA mode
                                }
                                1, 6 ->{
                                    //Glasses are in camera mode
                                }
                                7 -> {
                                    //Glasses are in AI conversation
                                }
                                8 ->{
                                    //Glasses are in recording mode
                                }
                            }
                        } else {
                            //Execute start and end
                        }
                    }
                }

                binding.btnVideo -> {
                    // Default UI behavior: start video recording
                    controlVideoRecording(true)
                }

                binding.btnRecord -> {
                    // Default UI behavior: start audio recording
                    controlAudioRecording(true)
                }

                binding.btnBt -> {
                    Toast.makeText(this@MainActivity, "Starting classic Bluetooth scan…", Toast.LENGTH_SHORT).show()
                    //BT scan
                    BleOperateManager.getInstance().classicBluetoothStartScan()

                }
                binding.btnBattery -> {
                    requestBatteryStatus(showToast = true)
                }
                binding.btnVolume ->{
                    Toast.makeText(this@MainActivity, "Requesting volume info…", Toast.LENGTH_SHORT).show()
                    //Read volume control and show values
                    LargeDataHandler.getInstance().getVolumeControl { _, response ->
                        if (response != null) {
                            val msg = """
                                Music: ${response.currVolumeMusic}/${response.maxVolumeMusic}
                                Call: ${response.currVolumeCall}/${response.maxVolumeCall}
                                System: ${response.currVolumeSystem}/${response.maxVolumeSystem}
                                Mode: ${response.currVolumeType}
                            """.trimIndent()
                            Log.i("VolumeControl", msg.replace('\n', ' '))
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                            }
                        } else {
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Failed to read volume info",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
                binding.btnMediaCount ->{
                    Toast.makeText(this@MainActivity, "Requesting media count…", Toast.LENGTH_SHORT).show()
                    LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x04)) { _, it ->
                        if (it.dataType == 4) {
                            val mediaCount = it.imageCount + it.videoCount + it.recordCount
                            val msg = if (mediaCount > 0) {
                                "Media not uploaded - Photos: ${it.imageCount}, Videos: ${it.videoCount}, Records: ${it.recordCount}"
                            } else {
                                "No pending media on glasses"
                            }
                            Log.i("MediaCount", msg)
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                binding.btnDataDownload -> {
                    Toast.makeText(this@MainActivity, "Starting data download…", Toast.LENGTH_SHORT).show()
                    // Check and request necessary permissions
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // Android 13+ requires NEARBY_WIFI_DEVICES permission
                        requestNearbyWifiDevicesPermission(this@MainActivity, object : OnPermissionCallback {
                            override fun onGranted(permissions: MutableList<String>, all: Boolean) {
                                if (all) {
                                    // Start BLE+WiFi P2P data download
                                    startDataDownload()
                                }
                            }

                            override fun onDenied(permissions: MutableList<String>, never: Boolean) {
                                super.onDenied(permissions, never)
                                if (never) {
                                    XXPermissions.startPermissionActivity(this@MainActivity, permissions)
                                }
                            }
                        })
                    } else {
                        // Android 12 and below start download directly
                        startDataDownload()
                    }
                }
                binding.btnOtaInfo -> {
                    Toast.makeText(this@MainActivity, "Dumping OTA server info…", Toast.LENGTH_SHORT).show()
                    dumpOtaServerInfo()
                }
                binding.btnPullOtaTest -> {
                    Toast.makeText(this@MainActivity, "Triggering pull‑mode OTA test…", Toast.LENGTH_SHORT).show()
                    testPullModeOta()
                }
            }
        }

        binding.btnModeGemini.setTextColor(if (aiAssistantMode == "Gemini") ContextCompat.getColor(this, R.color.cyan_accent) else ContextCompat.getColor(this, R.color.text_secondary))
        binding.btnModeChatgpt.setTextColor(if (aiAssistantMode == "ChatGPT") ContextCompat.getColor(this, R.color.cyan_accent) else ContextCompat.getColor(this, R.color.text_secondary))
        binding.btnModeTasker.setTextColor(if (aiAssistantMode == "Tasker") ContextCompat.getColor(this, R.color.cyan_accent) else ContextCompat.getColor(this, R.color.text_secondary))

        binding.cbHijackEnabled.isChecked = isAiHijackEnabled
        binding.cbHijackEnabled.setOnCheckedChangeListener { _, isChecked ->

            isAiHijackEnabled = isChecked
            Toast.makeText(this, "Hijack ${if (isChecked) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
        }

        binding.cbImageAsAssistant.isChecked = isImageAssistantMode
        binding.cbImageAsAssistant.text = if (isImageAssistantMode) "Direct Assistant" else "App Sharing"
        
        binding.cbImageAsAssistant.setOnCheckedChangeListener { _, isChecked ->
            isImageAssistantMode = isChecked
            val modeName = if (isChecked) "Direct Assistant" else "App Sharing"
            binding.cbImageAsAssistant.text = modeName
            Toast.makeText(this, "Image Hijack: $modeName", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dumpOtaServerInfo() {
        if (!BleOperateManager.getInstance().isConnected) {
            Log.e("OTAProbe", "Bluetooth not connected. Please connect to glasses first.")
            Toast.makeText(
                this,
                "Bluetooth not connected. Please connect to glasses first.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        LargeDataHandler.getInstance().syncDeviceInfo { _, response ->
            if (response == null) {
                Log.e("OTAProbe", "syncDeviceInfo returned null response")
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Failed to read device info for OTA",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@syncDeviceInfo
            }

            val wifiHw = response.wifiHardwareVersion ?: ""
            val wifiFw = response.wifiFirmwareVersion ?: ""
            val btFw = response.firmwareVersion ?: ""
            val hw = response.hardwareVersion ?: ""

            // OTA binary URL used by the official app's debug/down path.
            val otaBinaryUrl =
                "https://qcwxfactory.oss-cn-beijing.aliyuncs.com/bin/glasses/${wifiHw}.swu"

            // Try to download the OTA file directly into the app's files dir
            // so you can pull it with `adb` for inspection.
            val otaDir = File(getExternalFilesDir(null), "ota")
            if (!otaDir.exists()) {
                otaDir.mkdirs()
            }
            val outFile = File(otaDir, "${wifiHw}.swu")

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.i(
                        "OTAProbe",
                        "Attempting OTA binary download to: ${outFile.absolutePath}"
                    )
                    val url = URL(otaBinaryUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 15000
                    conn.readTimeout = 60000

                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        conn.inputStream.use { input ->
                            FileOutputStream(outFile).use { output ->
                                val buffer = ByteArray(8 * 1024)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    output.write(buffer, 0, read)
                                }
                                output.flush()
                            }
                        }
                        Log.i(
                            "OTAProbe",
                            "OTA binary download completed: ${outFile.absolutePath} (size=${outFile.length()} bytes)"
                        )
                    } else {
                        Log.e(
                            "OTAProbe",
                            "OTA binary download failed, HTTP ${conn.responseCode}"
                        )
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.e(
                        "OTAProbe",
                        "Exception while downloading OTA binary: ${e.message}",
                        e
                    )
                }
            }

            Log.i("OTAProbe", "==== OTA SERVER INFO START ====")
            Log.i("OTAProbe", "Device hardware version     : $hw")
            Log.i("OTAProbe", "WiFi hardware version       : $wifiHw")
            Log.i("OTAProbe", "WiFi firmware version       : $wifiFw")
            Log.i("OTAProbe", "Bluetooth firmware version  : $btFw")
            Log.i(
                "OTAProbe",
                "OTA metadata API (global)   : https://www.qlifesnap.com/glasses/app-update/last-ota"
            )
            Log.i(
                "OTAProbe",
                "OTA metadata API (China)    : https://www.qlifesnap.com/glasses/app-update/last-ota/china"
            )
            Log.i("OTAProbe", "OTA binary URL candidate    : $otaBinaryUrl")

            val lastOtaJsonTemplate = """
                {
                  "appId": <APP_ID>,
                  "uid": <USER_ID>,
                  "hardwareVersion": "$wifiHw",
                  "romVersion": "$wifiFw",
                  "os": 1,
                  "mac": "<PHONE_OR_BT_MAC>",
                  "country": "<COUNTRY_CODE>",
                  "dev": 2
                }
            """.trimIndent()

            Log.i("OTAProbe", "Sample LastOtaRequest JSON (fill in placeholders):")
            Log.i("OTAProbe", lastOtaJsonTemplate)
            Log.i(
                "OTAProbe",
                "Sample curl (metadata): curl -X POST 'https://www.qlifesnap.com/glasses/app-update/last-ota' -H 'Content-Type: application/json' -d '<JSON_ABOVE>'"
            )
            Log.i(
                "OTAProbe",
                "Sample curl (binary)  : curl -o '${wifiHw}.swu' '$otaBinaryUrl'"
            )
            Log.i("OTAProbe", "==== OTA SERVER INFO END ====")

            runOnUiThread {
                Toast.makeText(
                    this,
                    "OTA server info dumped to logcat (tag: OTAProbe)",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Minimal wrapper around LargeDataHandler.writeIpToSoc so we can observe
     * how the glasses behave when asked to fetch an OTA image from an HTTP
     * server under our control.
     *
     * This does not start any HTTP server on the phone; you must run one
     * yourself and point TEST_PULL_OTA_URL at it.
     */
    private fun testPullModeOta() {
        if (!BleOperateManager.getInstance().isConnected) {
            Log.e("PullOtaTest", "Bluetooth not connected. Please connect to glasses first.")
            Toast.makeText(
                this,
                "Bluetooth not connected. Please connect to glasses first.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val url = TEST_PULL_OTA_URL
        if (url.isBlank()) {
            Log.e("PullOtaTest", "TEST_PULL_OTA_URL is blank; edit MainActivity to set it.")
            Toast.makeText(
                this,
                "TEST_PULL_OTA_URL is blank. Edit MainActivity first.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        Log.i("PullOtaTest", "Calling writeIpToSoc with URL: $url")
        LargeDataHandler.getInstance().writeIpToSoc(url) { cmdType, response ->
            Log.i(
                "PullOtaTest",
                "writeIpToSoc callback: cmdType=$cmdType, response=$response"
            )
        }
    }
    
    private fun controlVideoRecording(start: Boolean) {
        val value = if (start) 0x02 else 0x03
        LargeDataHandler.getInstance().glassesControl(
            byteArrayOf(0x02, 0x01, value.toByte())
        ) { _, it ->
            if (it.dataType == 1) {
                if (it.errorCode == 0) {
                    when (it.workTypeIng) {
                        2 -> {
                            //Glasses are recording video
                        }
                        4 -> {
                            //Glasses are in transfer mode
                        }
                        5 -> {
                            //Glasses are in OTA mode
                        }
                        1, 6 ->{
                            //Glasses are in camera mode
                        }
                        7 -> {
                            //Glasses are in AI conversation
                        }
                        8 ->{
                            //Glasses are in recording mode
                        }
                    }
                } else {
                    //Execute start and end
                }
            }
        }
    }
    
    private fun controlAudioRecording(start: Boolean) {
        val value = if (start) 0x08 else 0x0c
        LargeDataHandler.getInstance().glassesControl(
            byteArrayOf(0x02, 0x01, value.toByte())
        ) { _, it ->
            if (it.dataType == 1) {
                if (it.errorCode == 0) {
                    when (it.workTypeIng) {
                        2 -> {
                            //Glasses are recording video
                        }
                        4 -> {
                            //Glasses are in transfer mode
                        }
                        5 -> {
                            //Glasses are in OTA mode
                        }
                        1, 6 ->{
                            //Glasses are in camera mode
                        }
                        7 -> {
                            //Glasses are in AI conversation
                        }
                        8 ->{
                            //Glasses are in recording mode
                        }
                    }
                } else {
                    //Execute start and end
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBluetoothEvent(event: BluetoothEvent) {
        updateConnectionStatus(event.connect)
        if (event.connect) {
            requestBatteryStatus(showToast = false)
        } else {
            updateBatteryText(null)
        }
    }

    private fun startBatteryPolling() {
        if (batteryPollJob?.isActive == true) {
            return
        }
        batteryPollJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                if (BleOperateManager.getInstance().isConnected) {
                    requestBatteryStatus(showToast = false)
                } else {
                    updateBatteryText(null)
                }
                delay(batteryPollIntervalMs)
            }
        }
    }

    private fun stopBatteryPolling() {
        batteryPollJob?.cancel()
        batteryPollJob = null
    }

    private fun sendAiBroadcast(type: String, path: String? = null) {
        val intent = Intent(aiEventAction(packageName)).apply {
            putExtra("type", type)
            path?.let { putExtra("path", it) }
            putExtra("assistant", aiAssistantMode)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        sendBroadcast(intent)
        Log.i("AIHijack", "Sent Broadcast to Tasker: $type")
    }

    private fun triggerAssistantVoiceQuery() {
        Log.i("AIHijack", "Triggering Voice Query for $aiAssistantMode")
        
        if (aiAssistantMode == "Tasker") {
            sendAiBroadcast("voice")
            return
        }

        // Wake up screen if locked

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }

        // Tell glasses to stop proprietary AI audio stream
        LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x0b)) { _, _ -> }

        try {
            val intent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (aiAssistantMode == "ChatGPT") {
                    // Try to target ChatGPT specifically if possible, 
                    // otherwise default assistant will handle it if user set it to ChatGPT
                    setPackage("com.openai.chatgpt")
                }
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("AIHijack", "Failed to trigger assistant: ${e.message}")
            runOnUiThread {
                Toast.makeText(this, "Assistant not found or failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun triggerAssistantImageQuery(imagePath: String) {
        Log.i("AIHijack", "Redirecting Image Query to Tasker logic with $imagePath")

        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        val isLocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            keyguardManager.isDeviceLocked
        } else {
            keyguardManager.isKeyguardLocked
        }

        // Wake and dismiss keyguard only when needed
        if (isLocked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            keyguardManager.requestDismissKeyguard(this, null)
        }

        if (isLocked) {
            speak("Unlock your phone to answer the image query")
        }

        // Stop glasses AI mode
        LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x0b)) { _, _ -> }

        try {
            val file = File(imagePath)
            if (!file.exists()) {
                Log.e("AIHijack", "Image file does not exist: $imagePath")
                return
            }

            // Copy file to public DCIM folder so it shows up in Gallery/Recents
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            val cameraDir = File(publicDir, "Camera")
            if (!cameraDir.exists()) cameraDir.mkdirs()
            
            val publicFile = File(cameraDir, "Glasses_AI_${System.currentTimeMillis()}.jpg")
            file.copyTo(publicFile, overwrite = true)
            
            // Scan the file so MediaStore/Gallery sees it immediately
            MediaScannerConnection.scanFile(this, arrayOf(publicFile.absolutePath), arrayOf("image/jpeg")) { path, uri ->
                Log.i("AIHijack", "Scanned to Gallery: $path")
                // Once scanned, trigger the Tasker broadcast
                runOnUiThread {
                    sendAiBroadcast("image", path)
                }
            }
        } catch (e: Exception) {
            Log.e("AIHijack", "Failed to process image for Tasker: ${e.message}")
        }
    }


    private fun updateConnectionStatus(connected: Boolean) {
        val deviceName = DeviceManager.getInstance().deviceName
        val status = if (connected) {
            if (!deviceName.isNullOrBlank()) {
                "Connected - $deviceName"
            } else {
                "Connected"
            }
        } else {
            "Disconnected"
        }
        binding.statusText.text = status
        if (!connected) {
            updateBatteryText(null)
        }
    }

    private fun updateBatteryText(battery: Int?) {
        binding.batteryText.text = battery?.let { "$it%" } ?: "--%"
    }

    private fun requestBatteryStatus(showToast: Boolean) {
        if (showToast) {
            pendingBatteryToast = true
            Toast.makeText(this@MainActivity, "Requesting battery level…", Toast.LENGTH_SHORT).show()
        }
        ensureBatteryCallback()
        // Trigger battery sync
        LargeDataHandler.getInstance().syncBattery()
    }

    private fun ensureBatteryCallback() {
        if (batteryCallbackRegistered) {
            return
        }
        batteryCallbackRegistered = true
        // Add battery listener. According to the SDK docs this
        // callback is invoked when syncBattery completes.
        LargeDataHandler.getInstance().addBatteryCallBack("init") { _, response ->
            val result = parseBatteryResponse(response)
            Log.i("BatteryCallback", result.message)
            runOnUiThread {
                updateBatteryText(result.battery)
                if (pendingBatteryToast) {
                    Toast.makeText(
                        this@MainActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                    pendingBatteryToast = false
                }
            }
        }
    }

    private data class BatteryResult(
        val battery: Int?,
        val charging: Boolean?,
        val message: String
    )

    private fun parseBatteryResponse(response: Any?): BatteryResult {
        if (response == null) {
            return BatteryResult(null, null, "Battery callback: null response")
        }
        return try {
            val clazz = response.javaClass
            val batteryField = clazz.getDeclaredField("battery").apply {
                isAccessible = true
            }
            val chargingField = clazz.getDeclaredField("charging").apply {
                isAccessible = true
            }

            val battery = batteryField.getInt(response)
            val charging = chargingField.getBoolean(response)
            val message =
                "Battery: $battery% (${if (charging) "charging" else "not charging"})"
            BatteryResult(battery, charging, message)
        } catch (e: Exception) {
            Log.e("BatteryCallback", "Failed to parse BatteryResponse", e)
            BatteryResult(null, null, "Battery: $response")
        }
    }

    private fun handleBatteryReport(battery: Int, charging: Boolean) {
        val message = "Battery: $battery% (${if (charging) "charging" else "not charging"})"
        Log.i("BatteryCallback", message)
        runOnUiThread {
            updateBatteryText(battery)
            if (pendingBatteryToast) {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                pendingBatteryToast = false
            }
        }
    }

    private fun handleTaskerCommand(startIntent: Intent?) {
        if (startIntent == null) return

        val isFromTaskerAction = startIntent.action == actionTaskerCommand(packageName)
        val command = startIntent.getStringExtra(EXTRA_TASKER_COMMAND)

        if (!isFromTaskerAction && command.isNullOrBlank()) {
            return
        }

        val normalizedCommand = command?.lowercase() ?: return

        when (normalizedCommand) {
            "scan" -> binding.btnScan.performClick()
            "connect" -> binding.btnConnect.performClick()
            "disconnect" -> binding.btnDisconnect.performClick()
            "add_listener" -> binding.btnAddListener.performClick()
            "set_time" -> binding.btnSetTime.performClick()
            "version" -> binding.btnVersion.performClick()
            "camera" -> binding.btnCamera.performClick()

            // Video recording controls
            "video" -> binding.btnVideo.performClick()
            "video_start" -> controlVideoRecording(true)
            "video_stop" -> controlVideoRecording(false)

            // Audio recording controls
            "record" -> binding.btnRecord.performClick()
            "record_start" -> controlAudioRecording(true)
            "record_stop" -> controlAudioRecording(false)

            "bt_scan" -> binding.btnBt.performClick()
            "battery" -> binding.btnBattery.performClick()
            "volume" -> binding.btnVolume.performClick()
            "media_count" -> binding.btnMediaCount.performClick()
            "data_download" -> binding.btnDataDownload.performClick()
        }
    }

    private fun startDataDownload() {
        Log.i("DataDownload", "Starting BLE+WiFi P2P data download...")

        // Check Bluetooth connection status
        if (!BleOperateManager.getInstance().isConnected) {
            Log.e("DataDownload", "Bluetooth not connected. Please connect to glasses first.")
            Toast.makeText(
                this,
                "Bluetooth not connected. Please connect to glasses first.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Check NEARBY_WIFI_DEVICES on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !XXPermissions.isGranted(this, "android.permission.NEARBY_WIFI_DEVICES")
        ) {
            Log.e("DataDownload", "NEARBY_WIFI_DEVICES permission not granted")
            Toast.makeText(
                this,
                "NEARBY_WIFI_DEVICES permission not granted.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Reset state for a fresh run
        downloadP2pConnected = false
        downloadBleIp = null
        downloadWifiIp = null
        downloadInProgress = false
        downloadAttemptJob?.cancel()
        downloadAttemptJob = null
        downloadResolvedHttpIp = null
        downloadP2pNetwork = null
        unbindProcessFromNetwork()

        if (!downloadNotifyListenerRegistered) {
            try {
                LargeDataHandler.getInstance().addOutDeviceListener(2, downloadNotifyListener)
                downloadNotifyListenerRegistered = true
                Log.i("DataDownload", "Registered download notify listener (cmdType=2)")
            } catch (e: Exception) {
                Log.e("DataDownload", "Failed to register download notify listener", e)
            }
        }

        val wifiP2pManager = WifiP2pManagerSingleton.getInstance(this)
        downloadWifiP2pManager = wifiP2pManager

        // Mirror vendor flow: clear internal retry state.
        wifiP2pManager.resetFailCount()

        // Register receiver and listen for P2P state/peer changes
        wifiP2pManager.registerReceiver()

        val callback = object : WifiP2pManagerSingleton.WifiP2pCallback {
            override fun onWifiP2pEnabled() {
                Log.i("DataDownload", "WiFi P2P enabled")
            }

            override fun onWifiP2pDisabled() {
                Log.e("DataDownload", "WiFi P2P disabled")
            }

            override fun onPeersChanged(peers: Collection<WifiP2pDevice>) {
                Log.i("DataDownload", "Found ${peers.size} P2P devices")
                // Connect to the first available peer (the official app
                // filters by name/MAC; here we keep it simple).
                val target = peers.firstOrNull()
                if (target != null) {
                    Log.i(
                        "DataDownload",
                        "Connecting to peer: ${target.deviceName} / ${target.deviceAddress}"
                    )
                    wifiP2pManager.connectToDevice(target)
                }
            }

            override fun onThisDeviceChanged(device: WifiP2pDevice) {
                Log.i(
                    "DataDownload",
                    "This device changed: ${device.deviceName} - ${device.status}"
                )
            }

            override fun onConnected(info: WifiP2pInfo) {
                Log.i(
                    "DataDownload",
                    "P2P connected: groupFormed=${info.groupFormed}, isGroupOwner=${info.isGroupOwner}"
                )
                onDownloadP2pConnected(info)
            }

            override fun onDisconnected() {
                Log.i("DataDownload", "P2P disconnected")
                downloadP2pConnected = false
                downloadP2pNetwork = null
                unbindProcessFromNetwork()
            }

            override fun onPeerDiscoveryStarted() {
                Log.i("DataDownload", "Peer discovery started")
            }

            override fun onPeerDiscoveryFailed(reason: Int) {
                Log.e("DataDownload", "Peer discovery failed: $reason")
            }

            override fun onConnectRequestSent() {
                Log.i("DataDownload", "Connect request sent")
            }

            override fun onConnectRequestFailed(reason: Int) {
                Log.e("DataDownload", "Connect request failed: $reason")
            }

            override fun connecting() {
                Log.i("DataDownload", "Connecting to P2P device...")
            }

            override fun cancelConnect() {
                Log.i("DataDownload", "P2P connection cancelled")
            }

            override fun cancelConnectFail(reason: Int) {
                Log.e("DataDownload", "Cancel connect failed: $reason")
            }

            override fun retryAlsoFailed() {
                Log.e("DataDownload", "P2P connection retry failed")
            }
        }

        downloadWifiP2pCallback = callback
        wifiP2pManager.addCallback(callback)

        // Start scanning for the glasses over WiFi Direct
        wifiP2pManager.startPeerDiscovery()

        // Ask the glasses (over BLE) to bring up WiFi/P2P and report their IP,
        // mirroring the official app's importAlbum() flow.
        LargeDataHandler.getInstance().glassesControl(
            byteArrayOf(0x02, 0x01, 0x04)
        ) { _, resp ->
            Log.i(
                "DataDownload",
                "glassesControl[0x02,0x01,0x04] -> dataType=${resp.dataType}, error=${resp.errorCode}"
            )
        }
    }
    
    private fun getDeviceIpFromBLE(): String? {
        // Prefer IP detected from BLE notifications, fall back to the
        // known sample IP if we have not seen one yet.
        val ipFromBle = bleIpBridge.ip.value
        if (!ipFromBle.isNullOrEmpty()) {
            Log.i("DataDownload", "Device IP from BleIpBridge: $ipFromBle")
            return ipFromBle
        }
        // No safe fallback: the glasses IP varies per session.
        return null
    }
    
    private fun downloadMediaList(deviceIp: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Lock the device IP for the whole transfer session.
                downloadResolvedHttpIp = deviceIp
                val url = "http://$deviceIp/files/media.config"
                Log.i("DataDownload", "Downloading media list from: $url")
                
                val connection = openHttpConnection(URL(url))
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 30000
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val content = inputStream.bufferedReader().use { it.readText() }
                    
                    // Show downloaded content
                    Log.i("DataDownload", "=== MEDIA CONFIG CONTENT ===")
                    Log.i("DataDownload", content)
                    Log.i("DataDownload", "=== END MEDIA CONFIG ===")
                    
                    // Parse media file list and start downloads. Do NOT clean up P2P here;
                    // we must keep the network bound until all files are downloaded.
                    parseMediaList(content, deviceIp)
                } else {
                    Log.e("DataDownload", "Failed to download media list. Response code: ${connection.responseCode}")
                    withContext(Dispatchers.Main) {
                        showDownloadError("Failed to download media list. Response code: ${connection.responseCode}")
                    }
                }
                
                connection.disconnect()
            } catch (e: Exception) {
                    Log.e("DataDownload", "Error downloading media list: ${e.message}", e)
                    CoroutineScope(Dispatchers.Main).launch {
                        when (e) {
                            is java.io.IOException -> {
                                if (e.message?.contains("Cleartext HTTP traffic") == true) {
                                    showDownloadError("Network security blocked HTTP connection. Please check app settings.")
                                } else if (e.message?.contains("Failed to connect") == true) {
                                    showDownloadError("Cannot connect to glasses device. Please ensure P2P connection is established.")
                                } else {
                                    showDownloadError("Network error: ${e.message}")
                                }
                            }
                            else -> showDownloadError("Download failed: ${e.message}")
                        }
                    }
                }
            }
        }
    
        private fun parseMediaList(content: String, deviceIp: String) {
            // Parse the media configuration file content - this is a text file containing media file names.
            Log.i("DataDownload", "Parsing media list content...")
            
            try {
                // Split by line, each line should be a file name
                val lines = content.trim().lines()
                val jpgFiles = mutableListOf<String>()
                val mp4Files = mutableListOf<String>()
                val opusFiles = mutableListOf<String>()
                var otherFiles = 0
                
                lines.forEach { line ->
                    val trimmedLine = line.trim()
                    if (trimmedLine.isNotEmpty()) {
                        when {
                            trimmedLine.endsWith(".jpg", ignoreCase = true) ||
                                trimmedLine.endsWith(".jpeg", ignoreCase = true) -> {
                                jpgFiles.add(trimmedLine)
                                Log.i("DataDownload", "Found JPG file: $trimmedLine")
                            }

                            trimmedLine.endsWith(".mp4", ignoreCase = true) -> {
                                mp4Files.add(trimmedLine)
                                Log.i("DataDownload", "Found MP4 file: $trimmedLine")
                            }

                            trimmedLine.endsWith(".opus", ignoreCase = true) -> {
                                opusFiles.add(trimmedLine)
                                Log.i("DataDownload", "Found OPUS file: $trimmedLine")
                            }

                            else -> {
                                otherFiles++
                                Log.i("DataDownload", "Found other file: $trimmedLine")
                            }
                        }
                    }
                }

                Log.i(
                    "DataDownload",
                    "Media list parsed: jpg=${jpgFiles.size}, mp4=${mp4Files.size}, opus=${opusFiles.size}, other=$otherFiles"
                )

                if (jpgFiles.isEmpty() && mp4Files.isEmpty() && opusFiles.isEmpty()) {
                    Log.w("DataDownload", "No JPG/MP4/OPUS files found in media.config")
                    CoroutineScope(Dispatchers.Main).launch {
                        showDownloadError("No JPG/MP4/OPUS files found in media.config")
                    }
                    return
                }

                // Download everything we understand. Keep P2P bound until all downloads finish.
                downloadAllMediaFiles(jpgFiles, mp4Files, opusFiles, deviceIp)
                
            } catch (e: Exception) {
                Log.e("DataDownload", "Error parsing media list: ${e.message}", e)
                CoroutineScope(Dispatchers.Main).launch {
                    showDownloadError("Failed to parse media list: ${e.message}")
                }
            }
        }

    private fun downloadAllMediaFiles(
        jpgFiles: List<String>,
        mp4Files: List<String>,
        opusFiles: List<String>,
        deviceIp: String,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.i(
                "DataDownload",
                "Starting download: jpg=${jpgFiles.size}, mp4=${mp4Files.size}, opus=${opusFiles.size}"
            )
            
            var jpgSuccess = 0
            var jpgFail = 0
            var mp4Success = 0
            var mp4Fail = 0
            var opusSuccess = 0
            var opusFail = 0
            
            for ((index, fileName) in jpgFiles.withIndex()) {
                try {
                    Log.i("DataDownload", "Downloading file ${index + 1}/${jpgFiles.size}: $fileName")

                    val success = downloadSingleJpgFile(fileName, deviceIp)
                    if (success) {
                        jpgSuccess++
                        Log.i("DataDownload", "✓ Successfully downloaded: $fileName")
                    } else {
                        jpgFail++
                        Log.e("DataDownload", "✗ Failed to download: $fileName")
                    }
                    
                    // Add a small delay to avoid excessively fast requests
                    delay(500)
                    
                } catch (e: Exception) {
                    jpgFail++
                    Log.e("DataDownload", "Error downloading $fileName: ${e.message}", e)
                }
            }

            for ((index, fileName) in mp4Files.withIndex()) {
                try {
                    Log.i("DataDownload", "Downloading video ${index + 1}/${mp4Files.size}: $fileName")

                    val success = downloadSingleMp4File(fileName, deviceIp)
                    if (success) {
                        mp4Success++
                        Log.i("DataDownload", "✓ Successfully downloaded: $fileName")
                    } else {
                        mp4Fail++
                        Log.e("DataDownload", "✗ Failed to download: $fileName")
                    }

                    // Videos are larger; be gentler.
                    delay(800)
                } catch (e: Exception) {
                    mp4Fail++
                    Log.e("DataDownload", "Error downloading $fileName: ${e.message}", e)
                }
            }

            for ((index, fileName) in opusFiles.withIndex()) {
                try {
                    Log.i("DataDownload", "Downloading audio ${index + 1}/${opusFiles.size}: $fileName")

                    val success = downloadSingleOpusFile(fileName, deviceIp)
                    if (success) {
                        opusSuccess++
                        Log.i("DataDownload", "✓ Successfully downloaded: $fileName")
                    } else {
                        opusFail++
                        Log.e("DataDownload", "✗ Failed to download: $fileName")
                    }

                    delay(500)
                } catch (e: Exception) {
                    opusFail++
                    Log.e("DataDownload", "Error downloading $fileName: ${e.message}", e)
                }
            }
            
            // Show final result
            val totalSuccess = jpgSuccess + mp4Success + opusSuccess
            val totalFail = jpgFail + mp4Fail + opusFail
            Log.i(
                "DataDownload",
                "Download completed: jpg=$jpgSuccess/${jpgFiles.size} ok, mp4=$mp4Success/${mp4Files.size} ok, opus=$opusSuccess/${opusFiles.size} ok, failed=$totalFail"
            )
            
            withContext(Dispatchers.Main) {
                if (totalFail == 0) {
                    showDownloadSuccess("All $totalSuccess files downloaded successfully!")
                } else {
                    showDownloadError("Download completed with errors: $totalSuccess successful, $totalFail failed")
                }
            }
        }
    }
    
    private suspend fun downloadSingleJpgFile(fileName: String, deviceIp: String): Boolean {
        return try {
            val url = "http://$deviceIp/files/$fileName"
            Log.i("DataDownload", "Downloading: $url")

            val connection = openHttpConnection(URL(url))
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 30000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val takenMs = parseTakenTimeMillisFromFilename(fileName) ?: System.currentTimeMillis()
                val saved = connection.inputStream.use { input ->
                    saveJpegToGallery(input, fileName, takenMs)
                }
                if (saved.bytes > 0) {
                    Log.i("DataDownload", "File downloaded: $fileName (${saved.bytes} bytes)")
                }
                if (saved.success) {
                    Log.i("DataDownload", "Saved to gallery: name=$fileName uri=${saved.uri}")
                    true
                } else {
                    Log.e("DataDownload", "Failed to save to gallery: $fileName")
                    false
                }
            } else {
                Log.e("DataDownload", "Failed to download $fileName. Response code: ${connection.responseCode}")
                false
            }
            
        } catch (e: Exception) {
            Log.e("DataDownload", "Error downloading $fileName: ${e.message}", e)
            false
        }
    }

    private suspend fun downloadSingleMp4File(fileName: String, deviceIp: String): Boolean {
        return try {
            val url = "http://$deviceIp/files/$fileName"
            Log.i("DataDownload", "Downloading: $url")

            val connection = openHttpConnection(URL(url))
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 180000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val takenMs = parseTakenTimeMillisFromFilename(fileName) ?: System.currentTimeMillis()
                val saved = connection.inputStream.use { input ->
                    saveMp4ToGallery(input, fileName, takenMs)
                }
                if (saved.bytes > 0) {
                    Log.i("DataDownload", "File downloaded: $fileName (${saved.bytes} bytes)")
                }
                if (saved.success) {
                    Log.i("DataDownload", "Saved to gallery: name=$fileName uri=${saved.uri}")
                    true
                } else {
                    Log.e("DataDownload", "Failed to save to gallery: $fileName")
                    false
                }
            } else {
                Log.e("DataDownload", "Failed to download $fileName. Response code: ${connection.responseCode}")
                false
            }
        } catch (e: Exception) {
            Log.e("DataDownload", "Error downloading $fileName: ${e.message}", e)
            false
        }
    }

    private suspend fun downloadSingleOpusFile(fileName: String, deviceIp: String): Boolean {
        return try {
            val url = "http://$deviceIp/files/$fileName"
            Log.i("DataDownload", "Downloading: $url")

            val connection = openHttpConnection(URL(url))
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 120000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val takenMs = parseTakenTimeMillisFromFilename(fileName) ?: System.currentTimeMillis()
                val saved = connection.inputStream.use { input ->
                    saveOpusToLibrary(input, fileName, takenMs)
                }
                if (saved.bytes > 0) {
                    Log.i("DataDownload", "File downloaded: $fileName (${saved.bytes} bytes)")
                }
                if (saved.success) {
                    Log.i("DataDownload", "Saved to library: name=$fileName uri=${saved.uri}")
                    true
                } else {
                    Log.e("DataDownload", "Failed to save to library: $fileName")
                    false
                }
            } else {
                Log.e("DataDownload", "Failed to download $fileName. Response code: ${connection.responseCode}")
                false
            }
        } catch (e: Exception) {
            Log.e("DataDownload", "Error downloading $fileName: ${e.message}", e)
            false
        }
    }
    
    private data class GallerySaveResult(
        val success: Boolean,
        val uri: String?,
        val bytes: Long,
    )

    private fun parseTakenTimeMillisFromFilename(fileName: String): Long? {
        // The glasses filenames look like: yyyyMMddHHmmssSSS?.jpg
        // Example: 20260127095159018.jpg
        val digits = fileName.takeWhile { it.isDigit() }
        if (digits.length < 14) return null

        return try {
            val base = digits.substring(0, 14)
            val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            val baseDate = sdf.parse(base) ?: return null
            val msPart = digits.substring(14).take(3)
            val extraMs = msPart.toIntOrNull() ?: 0
            baseDate.time + extraMs
        } catch (_: Exception) {
            null
        }
    }

    private fun saveJpegToGallery(input: InputStream, displayName: String, takenTimeMs: Long): GallerySaveResult {
        return try {
            val resolver = contentResolver

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_TAKEN, takenTimeMs)
                put(MediaStore.Images.Media.DATE_ADDED, takenTimeMs / 1000)
                put(MediaStore.Images.Media.DATE_MODIFIED, takenTimeMs / 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/CyanBridge")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return GallerySaveResult(false, null, 0)

            var bytes = 0L
            try {
                resolver.openOutputStream(uri, "w")?.use { out ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        bytes += read
                    }
                    out.flush()
                } ?: run {
                    resolver.delete(uri, null, null)
                    return GallerySaveResult(false, null, bytes)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                    resolver.update(uri, done, null, null)
                } else {
                    // Pre-Android 10: some galleries need an explicit media scan.
                    MediaScannerConnection.scanFile(
                        this,
                        arrayOf(uri.toString()),
                        arrayOf("image/jpeg"),
                        null
                    )
                }

                GallerySaveResult(true, uri.toString(), bytes)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                Log.e("DataDownload", "Gallery write failed for $displayName: ${e.message}", e)
                GallerySaveResult(false, uri.toString(), bytes)
            }
        } catch (e: Exception) {
            Log.e("DataDownload", "saveJpegToGallery failed for $displayName: ${e.message}", e)
            GallerySaveResult(false, null, 0)
        }
    }

    private fun saveMp4ToGallery(input: InputStream, displayName: String, takenTimeMs: Long): GallerySaveResult {
        return try {
            val resolver = contentResolver

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_TAKEN, takenTimeMs)
                put(MediaStore.Video.Media.DATE_ADDED, takenTimeMs / 1000)
                put(MediaStore.Video.Media.DATE_MODIFIED, takenTimeMs / 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Keep videos in the same DCIM/CyanBridge folder as photos.
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/CyanBridge")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return GallerySaveResult(false, null, 0)

            var bytes = 0L
            try {
                resolver.openOutputStream(uri, "w")?.use { out ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        bytes += read
                    }
                    out.flush()
                } ?: run {
                    resolver.delete(uri, null, null)
                    return GallerySaveResult(false, null, bytes)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                    resolver.update(uri, done, null, null)
                }

                GallerySaveResult(true, uri.toString(), bytes)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                Log.e("DataDownload", "Gallery video write failed for $displayName: ${e.message}", e)
                GallerySaveResult(false, uri.toString(), bytes)
            }
        } catch (e: Exception) {
            Log.e("DataDownload", "saveMp4ToGallery failed for $displayName: ${e.message}", e)
            GallerySaveResult(false, null, 0)
        }
    }

    private fun saveOpusToLibrary(input: InputStream, displayName: String, takenTimeMs: Long): GallerySaveResult {
        return try {
            val resolver = contentResolver

            val rawBytes = readAllBytes(input)
            val (payloadBytes, payloadNote) = wrapOpusIfNeeded(rawBytes)
            val headHex = bytesToHex(payloadBytes, 24)
            Log.i(
                "DataDownload",
                "OPUS save: name=$displayName, raw=${rawBytes.size} bytes, out=${payloadBytes.size} bytes, mode=$payloadNote, head=$headHex"
            )

            val title = displayName.substringBeforeLast('.', displayName)
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                // Use Ogg/Opus container when possible.
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/ogg")
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.IS_MUSIC, 0)
                put(MediaStore.MediaColumns.DATE_ADDED, takenTimeMs / 1000)
                put(MediaStore.MediaColumns.DATE_MODIFIED, takenTimeMs / 1000)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Keep alongside photos/videos per your preference (DCIM/CyanBridge).
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/CyanBridge")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: return GallerySaveResult(false, null, 0)

            var bytes = 0L
            try {
                resolver.openOutputStream(uri, "w")?.use { out ->
                    out.write(payloadBytes)
                    bytes = payloadBytes.size.toLong()
                    out.flush()
                } ?: run {
                    resolver.delete(uri, null, null)
                    return GallerySaveResult(false, null, bytes)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    resolver.update(uri, done, null, null)
                }

                GallerySaveResult(true, uri.toString(), bytes)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                Log.e("DataDownload", "Gallery audio write failed for $displayName: ${e.message}", e)
                GallerySaveResult(false, uri.toString(), bytes)
            }
        } catch (e: Exception) {
            Log.e("DataDownload", "saveOpusToLibrary failed for $displayName: ${e.message}", e)
            GallerySaveResult(false, null, 0)
        }
    }

    private fun readAllBytes(input: InputStream): ByteArray {
        val bos = ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            bos.write(buffer, 0, read)
        }
        return bos.toByteArray()
    }

    private fun bytesToHex(bytes: ByteArray, max: Int): String {
        val n = minOf(bytes.size, max)
        val sb = StringBuilder(n * 2)
        for (i in 0 until n) {
            sb.append(String.format("%02x", bytes[i]))
        }
        if (bytes.size > max) sb.append("...")
        return sb.toString()
    }

    private fun wrapOpusIfNeeded(raw: ByteArray): Pair<ByteArray, String> {
        if (raw.size >= 4 && raw[0].toInt() == 'O'.code && raw[1].toInt() == 'g'.code && raw[2].toInt() == 'g'.code && raw[3].toInt() == 'S'.code) {
            return raw to "ogg-already"
        }

        // Try to interpret the file as a sequence of length-prefixed Opus packets and wrap
        // them into a proper Ogg/Opus container so standard players (VLC) can open it.
        val packets = parseLengthPrefixedPackets(raw, littleEndian = true)
            ?: parseLengthPrefixedPackets(raw, littleEndian = false)
            ?: parseLengthPrefixedPackets1B(raw)
            ?: guessFixedSizePackets(raw)

        if (packets == null || packets.isEmpty()) {
            // Unknown/proprietary layout (the official app decodes these with jl_opus).
            return raw to "raw-unwrapped"
        }

        return try {
            val ogg = buildOggOpusFromPackets(packets, packetDurationMs = 40)
            ogg to "wrapped packets=${packets.size}"
        } catch (e: Exception) {
            Log.w("DataDownload", "Failed to wrap opus into ogg: ${e.message}")
            raw to "raw-unwrapped"
        }
    }

    private fun parseLengthPrefixedPackets(raw: ByteArray, littleEndian: Boolean): List<ByteArray>? {
        // Heuristic: repeated [u16 len][len bytes]...
        var i = 0
        val out = ArrayList<ByteArray>()
        while (i + 2 <= raw.size) {
            val b0 = raw[i].toInt() and 0xFF
            val b1 = raw[i + 1].toInt() and 0xFF
            val len = if (littleEndian) (b0 or (b1 shl 8)) else ((b0 shl 8) or b1)
            i += 2
            if (len <= 0 || len > 2000) return null
            if (i + len > raw.size) return null
            out.add(raw.copyOfRange(i, i + len))
            i += len
        }
        if (i != raw.size) return null
        // Require a few packets so we don't mis-detect.
        return if (out.size >= 3) out else null
    }

    private fun parseLengthPrefixedPackets1B(raw: ByteArray): List<ByteArray>? {
        // Heuristic: repeated [u8 len][len bytes]...
        var i = 0
        val out = ArrayList<ByteArray>()
        while (i + 1 <= raw.size) {
            val len = raw[i].toInt() and 0xFF
            i += 1
            if (len <= 0 || len > 255) return null
            if (i + len > raw.size) return null
            out.add(raw.copyOfRange(i, i + len))
            i += len
        }
        if (i != raw.size) return null
        return if (out.size >= 3) out else null
    }

    private fun guessFixedSizePackets(raw: ByteArray): List<ByteArray>? {
        // Last-resort heuristic: some devices store raw Opus packets back-to-back with a
        // fixed packet byte size. Try a few common sizes.
        if (raw.isEmpty()) return null
        // 40 bytes is especially common for these glasses (official app uses packetSize=40).
        val candidates = intArrayOf(40, 60, 80, 100, 120, 160, 200, 240, 320)
        for (size in candidates) {
            if (size <= 0) continue
            if (raw.size % size != 0) continue
            val count = raw.size / size
            if (count < 5) continue
            val out = ArrayList<ByteArray>(count)
            var i = 0
            while (i < raw.size) {
                out.add(raw.copyOfRange(i, i + size))
                i += size
            }
            return out
        }
        return null
    }

    private fun buildOggOpusFromPackets(packets: List<ByteArray>, packetDurationMs: Int): ByteArray {
        // Ogg/Opus expects OpusHead + OpusTags packets before audio packets.
        val serial = SecureRandom().nextInt()
        var seq = 0
        var granulePos: Long = 0

        val out = ByteArrayOutputStream()

        val opusHead = buildOpusHead(channels = 1, preSkip = 0)
        val opusTags = buildOpusTags(vendor = "CyanBridge")

        // Header pages
        writeOggPage(out, serial, seq++, granulePosition = 0, headerType = 0x02, packets = listOf(opusHead))
        writeOggPage(out, serial, seq++, granulePosition = 0, headerType = 0x00, packets = listOf(opusTags))

        // Audio pages
        val samplesPerPacket48k = (packetDurationMs * 48_000L) / 1000L
        val maxSegments = 255
        var idx = 0
        while (idx < packets.size) {
            val pagePackets = ArrayList<ByteArray>()
            var segCount = 0
            var localGranule = granulePos

            while (idx < packets.size) {
                val p = packets[idx]
                var neededSeg = (p.size + 254) / 255
                if (p.size % 255 == 0) neededSeg += 1
                if (segCount + neededSeg > maxSegments) break
                pagePackets.add(p)
                segCount += neededSeg
                localGranule += samplesPerPacket48k
                idx++
            }

            granulePos = localGranule
            val isLast = idx >= packets.size
            val headerType = if (isLast) 0x04 else 0x00
            writeOggPage(out, serial, seq++, granulePosition = granulePos, headerType = headerType, packets = pagePackets)
        }

        return out.toByteArray()
    }

    private fun buildOpusHead(channels: Int, preSkip: Int): ByteArray {
        // OpusHead (19 bytes)
        val b = ByteArrayOutputStream()
        b.write("OpusHead".toByteArray(Charsets.US_ASCII))
        b.write(1) // version
        b.write(channels and 0xFF)
        // pre-skip LE16
        b.write(preSkip and 0xFF)
        b.write((preSkip shr 8) and 0xFF)
        // input sample rate LE32 (Opus is coded at 48k internally)
        val sr = 48_000
        b.write(sr and 0xFF)
        b.write((sr shr 8) and 0xFF)
        b.write((sr shr 16) and 0xFF)
        b.write((sr shr 24) and 0xFF)
        // output gain LE16
        b.write(0)
        b.write(0)
        // channel mapping family (0 = mono/stereo)
        b.write(0)
        return b.toByteArray()
    }

    private fun buildOpusTags(vendor: String): ByteArray {
        val vendorBytes = vendor.toByteArray(Charsets.UTF_8)
        val b = ByteArrayOutputStream()
        b.write("OpusTags".toByteArray(Charsets.US_ASCII))
        writeLe32(b, vendorBytes.size)
        b.write(vendorBytes)
        // user comment list length = 0
        writeLe32(b, 0)
        return b.toByteArray()
    }

    private fun writeLe32(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 24) and 0xFF)
    }

    private fun writeOggPage(
        out: ByteArrayOutputStream,
        serial: Int,
        seq: Int,
        granulePosition: Long,
        headerType: Int,
        packets: List<ByteArray>,
    ) {
        val segmentTable = ByteArrayOutputStream()
        val payload = ByteArrayOutputStream()

        for (p in packets) {
            var remaining = p.size
            var offset = 0
            while (remaining > 0) {
                val seg = minOf(255, remaining)
                segmentTable.write(seg)
                payload.write(p, offset, seg)
                offset += seg
                remaining -= seg
            }
            if (p.size % 255 == 0) {
                // Lacing: 255 indicates continuation; add 0 to terminate packet exactly on boundary.
                segmentTable.write(0)
            }
        }

        val segBytes = segmentTable.toByteArray()
        if (segBytes.size > 255) {
            throw IllegalStateException("Ogg page has too many segments: ${segBytes.size}")
        }
        val payloadBytes = payload.toByteArray()

        val header = ByteArrayOutputStream()
        header.write("OggS".toByteArray(Charsets.US_ASCII))
        header.write(0) // version
        header.write(headerType and 0xFF)
        writeLe64(header, granulePosition)
        writeLe32(header, serial)
        writeLe32(header, seq)
        // checksum placeholder
        writeLe32(header, 0)
        header.write(segBytes.size)
        header.write(segBytes)

        val pageBytes = header.toByteArray() + payloadBytes
        val crc = oggCrc(pageBytes)

        // Patch checksum at byte offset 22 (from start of OggS)
        pageBytes[22] = (crc and 0xFF).toByte()
        pageBytes[23] = ((crc shr 8) and 0xFF).toByte()
        pageBytes[24] = ((crc shr 16) and 0xFF).toByte()
        pageBytes[25] = ((crc shr 24) and 0xFF).toByte()

        out.write(pageBytes)
    }

    private fun writeLe64(out: ByteArrayOutputStream, v: Long) {
        out.write((v and 0xFF).toInt())
        out.write(((v shr 8) and 0xFF).toInt())
        out.write(((v shr 16) and 0xFF).toInt())
        out.write(((v shr 24) and 0xFF).toInt())
        out.write(((v shr 32) and 0xFF).toInt())
        out.write(((v shr 40) and 0xFF).toInt())
        out.write(((v shr 48) and 0xFF).toInt())
        out.write(((v shr 56) and 0xFF).toInt())
    }

    private val oggCrcTable: IntArray = run {
        val table = IntArray(256)
        for (i in 0 until 256) {
            var r = i shl 24
            for (j in 0 until 8) {
                r = if ((r and 0x80000000.toInt()) != 0) {
                    (r shl 1) xor 0x04C11DB7
                } else {
                    r shl 1
                }
            }
            table[i] = r
        }
        table
    }

    private fun oggCrc(data: ByteArray): Int {
        var crc = 0
        for (b in data) {
            val idx = ((crc ushr 24) xor (b.toInt() and 0xFF)) and 0xFF
            crc = (crc shl 8) xor oggCrcTable[idx]
        }
        return crc
    }
    
    private fun cleanupP2pAfterDownload() {
        downloadAttemptJob?.cancel()
        downloadAttemptJob = null
        unbindProcessFromNetwork()
        val manager = downloadWifiP2pManager
        val callback = downloadWifiP2pCallback
        if (manager != null && callback != null) {
            manager.removeCallback(callback)
        }
        manager?.removeGroup { success ->
            Log.i("DataDownload", "P2P group removed: $success")
        }
        manager?.unregisterReceiver()
        downloadWifiP2pManager = null
        downloadWifiP2pCallback = null
        downloadP2pConnected = false
        downloadInProgress = false
        downloadP2pNetwork = null
        downloadResolvedHttpIp = null
    }

    private fun showDownloadSuccess(message: String) {
        exitTransferModeAfterDownload()
        cleanupP2pAfterDownload()
        Log.i("DataDownload", "SUCCESS: $message")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun showDownloadError(message: String, cleanup: Boolean = true) {
        if (cleanup) {
            cleanupP2pAfterDownload()
        }
        Log.e("DataDownload", "ERROR: $message")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun startAiVisionMvp() {
        if (!BleOperateManager.getInstance().isConnected) {
            Toast.makeText(this, "请先连接眼镜（BLE）", Toast.LENGTH_SHORT).show()
            return
        }

        if (aiVisionMvpJob?.isActive == true) {
            Toast.makeText(this, "正在获取图片，请稍候…", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnAiVisionMvp.isEnabled = false

        val outDir = File(cacheDir, "ai_vision_mvp").apply { mkdirs() }
        val outFile = File(outDir, "ai_${System.currentTimeMillis()}.jpg")

        Log.i("AiVisionMvp", "Starting capture -> thumbnails flow, out=${outFile.absolutePath}")
        Toast.makeText(this, "正在拍照并获取缩略图…", Toast.LENGTH_SHORT).show()

        aiVisionMvpJob = CoroutineScope(Dispatchers.IO).launch {
            val photoSignal = CompletableDeferred<Unit>()
            aiVisionMvpPhotoSignal = photoSignal

            val buf = ByteArrayOutputStream()
            val done = CompletableDeferred<Boolean>()
            var hadData = false
            var chunkCount = 0

            try {
                // Trigger "smart picture recognition / thumbnail capture" as documented in the SDK PDF:
                // glassesControl([0x02,0x01,0x06, thumbnailSize, thumbnailSize, 0x02])
                val thumbnailSize = 0x02 // 0..6
                val captureAck = CompletableDeferred<Int?>()
                LargeDataHandler.getInstance().glassesControl(
                    byteArrayOf(
                        0x02,
                        0x01,
                        0x06,
                        thumbnailSize.toByte(),
                        thumbnailSize.toByte(),
                        0x02
                    )
                ) { _, resp ->
                    Log.i(
                        "AiVisionMvp",
                        "capture glassesControl[0x02,0x01,0x06,...] -> dataType=${resp.dataType}, error=${resp.errorCode}, workTypeIng=${resp.workTypeIng}"
                    )
                    if (!captureAck.isCompleted) {
                        captureAck.complete(resp.errorCode)
                    }
                }

                val captureErr = withTimeoutOrNull(1500L) { captureAck.await() }
                if (captureErr != null && captureErr != 0) {
                    // Observed behavior: some firmwares return error=-1 even though they
                    // still send the AI-photo notify and stream thumbnail bytes fine.
                    // Treat this as informational only; success is determined by notify + chunks.
                    Log.i(
                        "AiVisionMvp",
                        "capture command ack error=$captureErr (common/benign); continue waiting for notify/chunks"
                    )
                }
            } catch (e: Exception) {
                Log.w("AiVisionMvp", "Failed to trigger capture command: ${e.message}")
            }

            try {
                // Some firmwares only start thumbnail streaming after a camera/AI-photo notify arrives.
                // Wait briefly for a signal, but do not hard-fail if the notify is missing.
                val gotSignal = withTimeoutOrNull(12_000L) { photoSignal.await() } != null
                if (!gotSignal) {
                    Log.w("AiVisionMvp", "Did not receive photo signal notify in time; still attempting thumbnails")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "未收到拍照事件，仍尝试获取图片…", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Give the glasses a short moment to prepare the thumbnail payload.
                    delay(700)
                }

                // Start receiving thumbnail chunks (JPEG) from the SDK.
                LargeDataHandler.getInstance().getPictureThumbnails { _, isComplete, data ->
                    if (done.isCompleted || aiVisionMvpJob?.isActive != true) return@getPictureThumbnails

                    if (data != null && data.isNotEmpty()) {
                        hadData = true
                        chunkCount++
                        if (chunkCount == 1) {
                            Log.i("AiVisionMvp", "thumbnail first chunk size=${data.size}")
                        }
                        try {
                            buf.write(data)
                        } catch (e: Exception) {
                            Log.e("AiVisionMvp", "Failed to append thumbnail chunk: ${e.message}")
                            done.complete(false)
                            return@getPictureThumbnails
                        }
                    }

                    if (isComplete) {
                        Log.i(
                            "AiVisionMvp",
                            "thumbnail complete chunks=$chunkCount bytes=${buf.size()} hadData=$hadData"
                        )
                        done.complete(hadData)
                    }
                }

                val result = withTimeoutOrNull(aiVisionMvpTimeoutMs) { done.await() }
                if (result == null) {
                    Log.e("AiVisionMvp", "Thumbnail capture timed out (hadData=$hadData, chunks=$chunkCount)")
                    withContext(Dispatchers.Main) {
                        binding.btnAiVisionMvp.isEnabled = true
                        Toast.makeText(this@MainActivity, "获取图片超时，请重试", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                if (result != true) {
                    Log.e("AiVisionMvp", "Thumbnail capture finished but no data received")
                    withContext(Dispatchers.Main) {
                        binding.btnAiVisionMvp.isEnabled = true
                        Toast.makeText(this@MainActivity, "未收到图片数据，请重试", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val bytes = buf.toByteArray()
                if (bytes.isEmpty()) {
                    Log.e("AiVisionMvp", "Thumbnail capture completed but buffer empty")
                    withContext(Dispatchers.Main) {
                        binding.btnAiVisionMvp.isEnabled = true
                        Toast.makeText(this@MainActivity, "未收到图片数据", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (decoded == null) {
                    Log.e("AiVisionMvp", "Received bytes but failed to decode bitmap (size=${bytes.size})")
                    withContext(Dispatchers.Main) {
                        binding.btnAiVisionMvp.isEnabled = true
                        Toast.makeText(this@MainActivity, "图片解码失败", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                try {
                    FileOutputStream(outFile).use { it.write(bytes) }
                } catch (e: Exception) {
                    Log.e("AiVisionMvp", "Failed to write image file: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        binding.btnAiVisionMvp.isEnabled = true
                        Toast.makeText(this@MainActivity, "保存图片失败", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                Log.i("AiVisionMvp", "Image ready: ${outFile.absolutePath} (${outFile.length()} bytes)")
                withContext(Dispatchers.Main) {
                    binding.btnAiVisionMvp.isEnabled = true
                    com.fersaiyan.cyanbridge.ui.AiVisionPreviewActivity.start(
                        this@MainActivity,
                        outFile.absolutePath
                    )
                }
            } catch (e: Exception) {
                Log.e("AiVisionMvp", "Failed AI vision MVP flow: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    binding.btnAiVisionMvp.isEnabled = true
                    Toast.makeText(this@MainActivity, "获取图片失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                aiVisionMvpPhotoSignal = null
                try {
                    buf.close()
                } catch (_: Exception) {
                    // ignore
                }
            }
        }
    }

    private fun exitTransferModeAfterDownload() {
        if (!BleOperateManager.getInstance().isConnected) {
            Log.w("DataDownload", "Skip exit transfer mode: BLE not connected")
            return
        }
        Log.i("DataDownload", "Requesting transfer-mode exit (glassesControl[0x02,0x01,0x0F])")
        try {
            LargeDataHandler.getInstance().glassesControl(
                byteArrayOf(0x02, 0x01, 0x0F)
            ) { _, resp ->
                Log.i(
                    "DataDownload",
                    "exitTransferMode -> dataType=${resp.dataType}, error=${resp.errorCode}"
                )
            }
        } catch (e: Exception) {
            Log.e("DataDownload", "Failed to exit transfer mode: ${e.message}", e)
        }
    }

    private fun isProbablyGroupOwnerIp(ip: String?): Boolean {
        if (ip.isNullOrBlank()) return false
        // Typical Wi-Fi Direct GO address when phone is GO.
        return ip == "192.168.49.1"
    }

    private fun buildCandidateIps(): List<String> {
        // Ordered best-effort list. We intentionally include a common glasses IP
        // fallback (192.168.49.79) because in practice BLE IP sometimes never arrives.
        val set = LinkedHashSet<String>()

        downloadBleIp?.let { set.add(it) }
        bleIpBridge.ip.value?.let { set.add(it) }

        // If the P2P group owner is the phone (often 192.168.49.1), this is NOT the glasses.
        // Keep it as a very low-priority hint only.
        downloadWifiIp?.let { set.add(it) }

        set.add("192.168.49.79")
        set.add("192.168.49.2")
        set.add("192.168.49.3")

        return set.toList()
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            val factory: SocketFactory? = downloadP2pNetwork?.socketFactory
            val sock = factory?.createSocket() ?: Socket()
            sock.use { s ->
                s.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun mediaConfigOk(ip: String, timeoutMs: Int, logFailures: Boolean = false): Boolean {
        return try {
            val conn = openHttpConnection(URL("http://$ip/files/media.config"))
            conn.requestMethod = "GET"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            val code = conn.responseCode
            conn.disconnect()
            code == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            if (logFailures) {
                Log.w("DataDownload", "media.config probe failed for $ip: ${e.message}")
            }
            false
        }
    }

    private suspend fun discoverGlassesIpByScan(prefix: String = "192.168.49."): String? {
        // Fast scan for an HTTP server on port 80 in the P2P subnet.
        // Concurrency is limited to avoid overwhelming the device/network stack.
        return supervisorScope {
            val sem = Semaphore(32)
            val connectTimeoutMs = 300
            val verifyTimeoutMs = 1200
            val found = CompletableDeferred<String?>()
            val firstOpenPortIp = AtomicReference<String?>(null)

            for (host in 2..254) {
                val ip = "$prefix$host"
                if (ip == "192.168.49.1") continue
                launch(Dispatchers.IO) {
                    sem.withPermit {
                        if (found.isCompleted) return@withPermit
                        if (isPortOpen(ip, 80, connectTimeoutMs)) {
                            firstOpenPortIp.compareAndSet(null, ip)
                            // Prefer an IP that actually serves media.config.
                            if (mediaConfigOk(ip, verifyTimeoutMs)) {
                                found.complete(ip)
                            }
                        }
                    }
                }
            }

            val res = withTimeoutOrNull(20_000L) { found.await() } ?: firstOpenPortIp.get()
            coroutineContext.cancelChildren()
            res
        }
    }

    /**
     * Debug helper: log all methods on LargeDataHandler so we can
     * discover additional SDK capabilities (such as WiFi transfer APIs)
     * without needing decompiled sources.
     */
    private fun logLargeDataHandlerMethodsOnce() {
        if (loggedLargeDataHandlerMethods) return
        loggedLargeDataHandlerMethods = true
        try {
            val clazz = LargeDataHandler.getInstance()::class.java
            val methods = clazz.declaredMethods
            for (m in methods) {
                val params = m.parameterTypes.joinToString(",") { it.simpleName ?: it.name }
                val ret = m.returnType.simpleName ?: m.returnType.name
                Log.i("LDHMethods", "method=${m.name}, params=($params), return=$ret")
            }
        } catch (e: Exception) {
            Log.e("LDHMethods", "Failed to introspect LargeDataHandler methods", e)
        }
    }

    private fun testConnection(deviceIp: String): Boolean {
        Log.i("DataDownload", "Testing connection to $deviceIp...")
        try {
            // Try to connect to the actual media configuration file
            val url = URL("http://$deviceIp/files/media.config")
            val connection = openHttpConnection(url)
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000 // Connection timeout
            connection.readTimeout = 5000 // Read timeout
            
            val responseCode = connection.responseCode
            Log.i("DataDownload", "Connection test response code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Try to read a small amount of content to confirm that the connection is available
                val inputStream = connection.inputStream
                val buffer = ByteArray(1024)
                val bytesRead = inputStream.read(buffer)
                inputStream.close()
                
                Log.i("DataDownload", "Connection test successful - read $bytesRead bytes")
                return true
            }
            
            return false
        } catch (e: Exception) {
            Log.e("DataDownload", "Connection test failed: ${e.message}", e)
            return false
        }
    }

    private fun onDownloadBleIp(ip: String) {
        Log.i("DataDownload", "BLE reported device WiFi IP: $ip")
        downloadBleIp = ip

        // If we're stuck scanning/probing without a good route, restart the resolver now that
        // we have the authoritative device IP from BLE.
        if (downloadAttemptJob?.isActive == true && !downloadInProgress) {
            Log.i("DataDownload", "New BLE IP arrived; restarting HTTP resolver")
            downloadAttemptJob?.cancel()
            downloadAttemptJob = null
        }
        maybeStartHttpDownload("BLE")
    }

    private fun onDownloadP2pConnected(info: WifiP2pInfo) {
        downloadP2pConnected = info.groupFormed
        downloadWifiIp = info.groupOwnerAddress?.hostAddress
        downloadP2pNetwork = findLikelyP2pNetwork()
        bindProcessToNetwork(downloadP2pNetwork)
        Log.i(
            "DataDownload",
            "onDownloadP2pConnected: p2pConnected=$downloadP2pConnected, isGroupOwner=${info.isGroupOwner}, groupOwnerIp=$downloadWifiIp"
        )
        maybeStartHttpDownload("P2P")
    }

    private fun maybeStartHttpDownload(source: String) {
        if (downloadInProgress || downloadAttemptJob?.isActive == true) {
            Log.i("DataDownload", "Download already in progress, ignoring trigger from $source")
            return
        }
        val bridgeIp = bleIpBridge.ip.value
        Log.i(
            "DataDownload",
            "HTTP start trigger from $source. p2p=$downloadP2pConnected, bleIp=$downloadBleIp, groupOwnerIp=$downloadWifiIp, bleBridgeIp=$bridgeIp"
        )

        downloadAttemptJob = CoroutineScope(Dispatchers.IO).launch {
            val startMs = System.currentTimeMillis()
            val overallTimeoutMs = 90_000L
            var lastStatusLogMs = 0L
            var didSubnetScan = false

            while (isActive && System.currentTimeMillis() - startMs < overallTimeoutMs) {
                val now = System.currentTimeMillis()
                if (now - lastStatusLogMs > 5000) {
                    lastStatusLogMs = now
                    Log.i(
                        "DataDownload",
                        "Resolving glasses HTTP IP... p2p=$downloadP2pConnected, bleIp=$downloadBleIp, groupOwnerIp=$downloadWifiIp"
                    )
                }

                // 1) Try known candidates first.
                for (candidate in buildCandidateIps()) {
                    if (!isActive) return@launch
                    if (candidate.isBlank()) continue
                    if (isProbablyGroupOwnerIp(candidate)) {
                        // The phone typically has nothing on port 80.
                        continue
                    }
                    val shouldLog = candidate == downloadBleIp
                    if (mediaConfigOk(candidate, 2000, logFailures = shouldLog)) {
                        downloadResolvedHttpIp = candidate
                        downloadInProgress = true
                        Log.i("DataDownload", "Resolved glasses HTTP IP via candidate list: $candidate")
                        downloadMediaList(candidate)
                        return@launch
                    }
                }

                // 2) If we have a group owner IP in 192.168.49.x, scan that /24.
                if (!didSubnetScan &&
                    downloadP2pConnected &&
                    downloadResolvedHttpIp == null &&
                    downloadBleIp == null &&
                    bleIpBridge.ip.value == null &&
                    (downloadWifiIp?.startsWith("192.168.49.") == true)
                ) {
                    didSubnetScan = true
                    Log.i("DataDownload", "Candidate IPs failed; scanning 192.168.49.0/24 for HTTP server...")
                    val found = discoverGlassesIpByScan("192.168.49.")
                    if (!found.isNullOrBlank()) {
                        downloadResolvedHttpIp = found
                        downloadInProgress = true
                        Log.i("DataDownload", "Resolved glasses HTTP IP via scan: $found")
                        downloadMediaList(found)
                        return@launch
                    }
                }

                delay(1500)
            }

            withContext(Dispatchers.Main) {
                // Do not immediately tear down P2P; the glasses sometimes report IP late.
                showDownloadError(
                    "Could not resolve glasses HTTP IP (bleIp=$downloadBleIp, groupOwnerIp=$downloadWifiIp, p2p=$downloadP2pConnected)",
                    cleanup = false
                )
            }

            // Avoid leaving the P2P group around forever.
            delay(15_000)
            withContext(Dispatchers.Main) {
                if (!downloadInProgress) {
                    Log.i("DataDownload", "Cleaning up P2P after unresolved download")
                    cleanupP2pAfterDownload()
                }
            }
        }
    }

    private inner class DownloadNotifyListener : GlassesDeviceNotifyListener() {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun parseData(cmdType: Int, response: GlassesDeviceNotifyRsp) {
            // Only handle download-relevant notifications here to avoid duplicating
            // other flows already handled by MyDeviceNotifyListener.
            val load = response.loadData
            if (load.size < 7) return
            when (load[6].toInt()) {
                0x08 -> {
                    if (load.size >= 11) {
                        val ip = "${ByteUtil.byteToInt(load[7])}." +
                                "${ByteUtil.byteToInt(load[8])}." +
                                "${ByteUtil.byteToInt(load[9])}." +
                                "${ByteUtil.byteToInt(load[10])}"
                        Log.i("DeviceNotify", "(download) BLE reported WiFi IP: $ip")
                        onDownloadBleIp(ip)
                    }
                }

                0x09 -> {
                    val raw = load.getOrNull(7) ?: 0
                    val errorCode = ByteUtil.byteToInt(raw)
                    Log.e("DeviceNotify", "(download) P2P/WiFi error from device: $errorCode (raw=$raw)")
                    if (errorCode == 255) {
                        maybeResetP2pAfterError255("download")
                    }
                }
            }
        }
    }

    private fun openHttpConnection(url: URL): HttpURLConnection {
        val network = downloadP2pNetwork ?: findLikelyP2pNetwork()?.also { downloadP2pNetwork = it }
        val conn = if (network != null) {
            network.openConnection(url) as HttpURLConnection
        } else {
            url.openConnection() as HttpURLConnection
        }
        conn.instanceFollowRedirects = true
        return conn
    }

    private fun findLikelyP2pNetwork(): Network? {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            for (n in cm.allNetworks) {
                val lp = cm.getLinkProperties(n) ?: continue
                val ifName = lp.interfaceName ?: ""
                val has49 = lp.linkAddresses.any { la ->
                    la.address.hostAddress?.startsWith("192.168.49.") == true
                }
                if (ifName.contains("p2p", ignoreCase = true) || has49) {
                    val addrs = lp.linkAddresses.joinToString { it.address.hostAddress ?: "?" }
                    Log.i("DataDownload", "Selected P2P network: if=$ifName addrs=[$addrs]")
                    return n
                }
            }
            null
        } catch (e: Exception) {
            Log.w("DataDownload", "Failed to locate P2P network: ${e.message}")
            null
        }
    }

    private fun bindProcessToNetwork(network: Network?) {
        if (network == null) return
        if (boundNetwork == network) return
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val ok = cm.bindProcessToNetwork(network)
            if (ok) {
                boundNetwork = network
                Log.i("DataDownload", "Bound process to P2P network")
            } else {
                Log.w("DataDownload", "bindProcessToNetwork returned false")
            }
        } catch (e: Exception) {
            Log.w("DataDownload", "bindProcessToNetwork failed: ${e.message}")
        }
    }

    private fun unbindProcessFromNetwork() {
        if (boundNetwork == null) return
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.bindProcessToNetwork(null)
        } catch (_: Exception) {
            // ignore
        } finally {
            boundNetwork = null
        }
    }

    private fun maybeResetP2pAfterError255(source: String) {
        val now = System.currentTimeMillis()
        val haveDeviceIp = !downloadBleIp.isNullOrBlank() || !bleIpBridge.ip.value.isNullOrBlank()

        // On some devices (notably Samsung), sending the reset command while we are actively
        // trying to talk to the glasses can drop the P2P link and kill the HTTP session.
        if (downloadInProgress || (downloadAttemptJob?.isActive == true && haveDeviceIp)) {
            Log.i("DataDownload", "Suppressing resetDeviceP2p on error=255 (source=$source) during active download/resolve")
            return
        }

        if (now - lastP2pResetAtMs < 10_000) {
            return
        }
        lastP2pResetAtMs = now
        WifiP2pManagerSingleton.getInstance(this).resetDeviceP2p()
    }

    inner class MyDeviceNotifyListener : GlassesDeviceNotifyListener() {

        @RequiresApi(Build.VERSION_CODES.O)
        override fun parseData(cmdType: Int, response: GlassesDeviceNotifyRsp) {
            Log.i(
                "DeviceNotify",
                "cmdType=$cmdType, loadData=${response.loadData.joinToString(separator = ",") { it.toInt().toString() }}"
            )
            when (response.loadData[6].toInt()) {
                //Glasses battery report
                0x05 -> {
                    //Current battery
                    val battery = response.loadData[7].toInt()
                    //Is it charging
                    val changing = response.loadData[8].toInt()
                    handleBatteryReport(battery, changing == 1)
                }
                // Glasses photo-related notify (observed on some firmwares after shutter)
                0x01 -> {
                    if (aiVisionMvpJob?.isActive == true) {
                        Log.i("DeviceNotify", "Photo signal notify received (AiVisionMvp in progress)")
                        aiVisionMvpPhotoSignal?.let { signal ->
                            if (!signal.isCompleted) {
                                signal.complete(Unit)
                            }
                        }
                    }
                }
                //Glasses pass quick recognition / AI Photo
                0x02 -> {
                    if (aiVisionMvpJob?.isActive == true) {
                        val b9 = response.loadData.getOrNull(9)?.toInt()
                        Log.i("DeviceNotify", "AI Photo notify received (AiVisionMvp in progress), b9=$b9")
                        aiVisionMvpPhotoSignal?.let { signal ->
                            if (!signal.isCompleted) {
                                signal.complete(Unit)
                            }
                        }
                        return
                    }
                    Log.i("DeviceNotify", "AI Photo Button Pressed - Starting Chunked Download")
                    val fileName = "AI_Thumb_${System.currentTimeMillis()}.jpg"
                    val file = File(getExternalFilesDir("DCIM"), fileName)
                    
                    // The SDK sends the image in multiple chunks. 
                    // We must append them to the file and wait for isComplete (success parameter).
                    LargeDataHandler.getInstance().getPictureThumbnails { _, isComplete, data ->
                        if (data != null) {
                            try {
                                FileOutputStream(file, true).use { it.write(data) }
                                if (isComplete) {
                                    Log.i("DeviceNotify", "Thumbnail transfer complete: ${file.absolutePath} (${file.length()} bytes)")
                                    if (isAiHijackEnabled) {
                                        triggerAssistantImageQuery(file.absolutePath)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("DeviceNotify", "Failed to write thumbnail chunk: ${e.message}")
                            }
                        }
                    }
                }

                //Glasses activate microphone / AI button
                0x03 -> {
                    if (response.loadData[7].toInt() == 1) {
                        Log.i("DeviceNotify", "AI Button Pressed - Hijacking to Phone Assistant")
                        if (isAiHijackEnabled) {
                            triggerAssistantVoiceQuery()
                        } else {
                            // Original-path MVP: use BLE audio stream -> STT -> LLM -> print + TTS
                            voiceChatMvp.toggle()
                        }
                    }
                }
                //ota upgrade
                0x04 -> {
                    try {
                        val download = response.loadData[7].toInt()
                        val soc = response.loadData[8].toInt()
                        val nor = response.loadData[9].toInt()
                        //download firmware download progress soc download progress nor upgrade progress
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                0x0c -> {
                    //The glasses trigger a pause event, voice broadcast
                    if (response.loadData[7].toInt() == 1) {
                        //to do
                    }
                }

                0x0d -> {
                    //Unbind APP event
                    if (response.loadData[7].toInt() == 1) {
                        //to do
                    }
                }
                //Glasses memory low event
                0x0e -> {

                }
                //Translation pause event
                0x10 -> {

                }
                //Glasses volume change event
                0x12 -> {
                    //Music volume
                    //Minimum volume
                    response.loadData[8].toInt()
                    //Maximum volume
                    response.loadData[9].toInt()
                    //Current volume
                    response.loadData[10].toInt()

                    //Incoming call volume
                    //Minimum volume
                    response.loadData[12].toInt()
                    //Maximum volume
                    response.loadData[13].toInt()
                    //Current volume
                    response.loadData[14].toInt()

                    //Glasses system volume
                    //Minimum volume
                    response.loadData[16].toInt()
                    //Maximum volume
                    response.loadData[17].toInt()
                    //Current volume
                    response.loadData[18].toInt()

                    //Current volume mode
                    val mode = response.loadData[19].toInt()

                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Volume changed (mode=$mode)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                }
                // Glasses report WiFi IP for data download
                0x08 -> {
                    if (response.loadData.size >= 11) {
                        val ip = "${ByteUtil.byteToInt(response.loadData[7])}." +
                                "${ByteUtil.byteToInt(response.loadData[8])}." +
                                "${ByteUtil.byteToInt(response.loadData[9])}." +
                                "${ByteUtil.byteToInt(response.loadData[10])}"
                        Log.i("DeviceNotify", "BLE reported WiFi IP: $ip")
                        onDownloadBleIp(ip)
                    } else {
                        Log.w(
                            "DeviceNotify",
                            "0x08 notify with too-short payload, size=${response.loadData.size}"
                        )
                    }
                }
                // Glasses report P2P / WiFi error during data download
                0x09 -> {
                    val raw = response.loadData.getOrNull(7) ?: 0
                    val errorCode = ByteUtil.byteToInt(raw)
                    Log.e("DeviceNotify", "P2P/WiFi error from device: $errorCode (raw=$raw)")
                    if (errorCode == 255) {
                        // Mirror the official app: ask the glasses/phone P2P
                        // layer to reset, but do NOT treat this as a fatal
                        // error for the whole download flow. The official app
                        // still proceeds to receive an IP and download.
                        maybeResetP2pAfterError255("main")
                    }
                }
            }
        }
    }
}
