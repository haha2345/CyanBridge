package com.fersaiyan.cyanbridge.media

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.fersaiyan.cyanbridge.ui.wifi.p2p.WifiP2pManagerSingleton
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.communication.LargeDataHandler
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 媒体同步管理器：从智能眼镜下载所有多媒体文件。
 *
 * 流程：WiFi P2P 连接 → BLE 命令 → HTTP media.config → 解析 → 逐个下载 → 保存到相册
 */
class MediaSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "MediaSync"
        @Volatile private var instance: MediaSyncManager? = null
        fun getInstance(context: Context): MediaSyncManager {
            return instance
                    ?: synchronized(this) {
                        instance
                                ?: MediaSyncManager(context.applicationContext).also {
                                    instance = it
                                }
                    }
        }
    }

    // ── State ──

    sealed class SyncState {
        data object Idle : SyncState()
        data object Connecting : SyncState()
        data class Syncing(val current: Int, val total: Int, val fileName: String) : SyncState()
        data class Done(val success: Int, val failed: Int) : SyncState()
        data class Error(val message: String) : SyncState()
    }

    data class MediaFileItem(
            val name: String,
            val type: MediaType, // PHOTO, VIDEO, AUDIO
            val localUri: String?,
            val timestamp: Long,
    )

    enum class MediaType {
        PHOTO,
        VIDEO,
        AUDIO
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _downloadedFiles = MutableStateFlow<List<MediaFileItem>>(emptyList())
    val downloadedFiles: StateFlow<List<MediaFileItem>> = _downloadedFiles.asStateFlow()

    private var syncJob: Job? = null
    private var p2pNetwork: Network? = null
    private var boundNetwork: Network? = null
    private var p2pConnected = false
    private var groupOwnerIp: String? = null
    private var bleIp: String? = null

    // ── Public API ──

    fun startSync() {
        if (syncJob?.isActive == true) return
        if (!BleOperateManager.getInstance().isConnected) {
            _syncState.value = SyncState.Error("请先连接眼镜")
            return
        }
        _syncState.value = SyncState.Connecting
        _downloadedFiles.value = emptyList()
        p2pConnected = false
        groupOwnerIp = null
        bleIp = null
        p2pNetwork = null
        unbindProcess()

        syncJob =
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        startP2pAndDownload()
                    } catch (e: CancellationException) {
                        Log.i(TAG, "Sync cancelled")
                        withContext(Dispatchers.Main) { _syncState.value = SyncState.Idle }
                    } catch (e: Exception) {
                        Log.e(TAG, "Sync failed", e)
                        withContext(Dispatchers.Main) {
                            _syncState.value = SyncState.Error(e.message ?: "未知错误")
                        }
                    } finally {
                        cleanupP2p()
                    }
                }
    }

    fun cancelSync() {
        syncJob?.cancel()
        syncJob = null
        cleanupP2p()
        _syncState.value = SyncState.Idle
    }

    // ── P2P + Download Flow ──

    private suspend fun startP2pAndDownload() {
        val wifiP2pManager = WifiP2pManagerSingleton.getInstance(context)
        wifiP2pManager.resetFailCount()
        wifiP2pManager.registerReceiver()

        val p2pReady = CompletableDeferred<String>() // will complete with device IP

        val callback =
                object : WifiP2pManagerSingleton.WifiP2pCallback {
                    override fun onWifiP2pEnabled() {
                        Log.i(TAG, "WiFi P2P enabled")
                    }
                    override fun onWifiP2pDisabled() {
                        Log.e(TAG, "WiFi P2P disabled")
                    }
                    override fun onPeersChanged(peers: Collection<WifiP2pDevice>) {
                        Log.i(TAG, "Found ${peers.size} P2P devices")
                        val target = peers.firstOrNull()
                        if (target != null) {
                            Log.i(TAG, "Connecting to: ${target.deviceName}")
                            wifiP2pManager.connectToDevice(target)
                        }
                    }
                    override fun onThisDeviceChanged(device: WifiP2pDevice) {}
                    override fun onConnected(info: WifiP2pInfo) {
                        p2pConnected = info.groupFormed
                        groupOwnerIp = info.groupOwnerAddress?.hostAddress
                        p2pNetwork = findP2pNetwork()
                        bindProcess(p2pNetwork)
                        Log.i(TAG, "P2P connected: ip=$groupOwnerIp")
                        // Don't complete yet — we need to find the glasses IP
                    }
                    override fun onDisconnected() {
                        p2pConnected = false
                        p2pNetwork = null
                        unbindProcess()
                    }
                    override fun onPeerDiscoveryStarted() {}
                    override fun onPeerDiscoveryFailed(reason: Int) {}
                    override fun onConnectRequestSent() {}
                    override fun onConnectRequestFailed(reason: Int) {}
                    override fun connecting() {}
                    override fun cancelConnect() {}
                    override fun cancelConnectFail(reason: Int) {}
                    override fun retryAlsoFailed() {
                        if (!p2pReady.isCompleted)
                                p2pReady.completeExceptionally(Exception("P2P 连接失败"))
                    }
                }
        wifiP2pManager.addCallback(callback)
        wifiP2pManager.startPeerDiscovery()

        // Send BLE command to bring up glasses WiFi
        LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x04)) { _, resp ->
            Log.i(TAG, "BLE WiFi command ack: dataType=${resp.dataType}, error=${resp.errorCode}")
        }

        // Wait for P2P to connect and try to find device IP
        val deviceIp =
                withTimeoutOrNull(60_000) {
                    // Poll until we can reach media.config
                    var resolvedIp: String? = null
                    val startMs = System.currentTimeMillis()
                    while (isActive && System.currentTimeMillis() - startMs < 55_000) {
                        if (p2pConnected) {
                            // Try candidates
                            val candidates = buildCandidateIps()
                            for (ip in candidates) {
                                if (ip.isBlank()) continue
                                if (mediaConfigOk(ip, 2000)) {
                                    resolvedIp = ip
                                    break
                                }
                            }
                            if (resolvedIp != null) break

                            // Try subnet scan
                            if (groupOwnerIp?.startsWith("192.168.49.") == true) {
                                resolvedIp = scanSubnet("192.168.49.")
                                if (resolvedIp != null) break
                            }
                        }
                        delay(2000)
                    }
                    resolvedIp
                }

        if (deviceIp == null) {
            _syncState.value = SyncState.Error("无法连接眼镜 WiFi 服务")
            wifiP2pManager.removeCallback(callback)
            return
        }

        Log.i(TAG, "Resolved glasses HTTP IP: $deviceIp")

        // Download media.config
        val mediaConfig = downloadMediaConfig(deviceIp)
        if (mediaConfig == null) {
            _syncState.value = SyncState.Error("获取文件列表失败")
            wifiP2pManager.removeCallback(callback)
            return
        }

        // Parse file list
        val files = parseMediaConfig(mediaConfig)
        if (files.isEmpty()) {
            _syncState.value = SyncState.Done(0, 0)
            wifiP2pManager.removeCallback(callback)
            return
        }

        // Download all files
        var success = 0
        var failed = 0
        val downloaded = mutableListOf<MediaFileItem>()

        for ((index, file) in files.withIndex()) {
            if (syncJob?.isActive != true) break
            _syncState.value = SyncState.Syncing(index + 1, files.size, file.first)

            val uri = downloadAndSaveFile(file.first, file.second, deviceIp)
            val timestamp = parseTakenTimeMs(file.first) ?: System.currentTimeMillis()
            if (uri != null) {
                success++
                downloaded.add(MediaFileItem(file.first, file.second, uri, timestamp))
            } else {
                failed++
            }
            _downloadedFiles.value = downloaded.toList()

            // Pace downloads
            delay(if (file.second == MediaType.VIDEO) 800 else 500)
        }

        _syncState.value = SyncState.Done(success, failed)
        wifiP2pManager.removeCallback(callback)
    }

    // ── HTTP Helpers ──

    private fun downloadMediaConfig(deviceIp: String): String? {
        return try {
            val url = "http://$deviceIp/files/media.config"
            Log.i(TAG, "Downloading: $url")
            val conn = openConnection(URL(url))
            conn.connectTimeout = 10000
            conn.readTimeout = 30000
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                Log.e(TAG, "media.config failed: ${conn.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "media.config error: ${e.message}")
            null
        }
    }

    private fun parseMediaConfig(content: String): List<Pair<String, MediaType>> {
        val files = mutableListOf<Pair<String, MediaType>>()
        content.trim().lines().forEach { line ->
            val name = line.trim()
            if (name.isNotEmpty()) {
                val type =
                        when {
                            name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) ->
                                    MediaType.PHOTO
                            name.endsWith(".mp4", true) -> MediaType.VIDEO
                            name.endsWith(".opus", true) -> MediaType.AUDIO
                            else -> null
                        }
                if (type != null) files.add(name to type)
            }
        }
        Log.i(
                TAG,
                "Parsed ${files.size} files: photos=${files.count { it.second == MediaType.PHOTO }}, videos=${files.count { it.second == MediaType.VIDEO }}, audio=${files.count { it.second == MediaType.AUDIO }}"
        )
        return files
    }

    private fun downloadAndSaveFile(fileName: String, type: MediaType, deviceIp: String): String? {
        return try {
            val url = "http://$deviceIp/files/$fileName"
            val conn = openConnection(URL(url))
            conn.connectTimeout = 15000
            conn.readTimeout = if (type == MediaType.VIDEO) 180000 else 60000
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Download $fileName failed: ${conn.responseCode}")
                return null
            }
            val takenMs = parseTakenTimeMs(fileName) ?: System.currentTimeMillis()
            val result =
                    conn.inputStream.use { input ->
                        when (type) {
                            MediaType.PHOTO -> saveJpegToGallery(input, fileName, takenMs)
                            MediaType.VIDEO -> saveMp4ToGallery(input, fileName, takenMs)
                            MediaType.AUDIO -> saveOpusToLibrary(input, fileName, takenMs)
                        }
                    }
            if (result.success) {
                Log.i(TAG, "✓ $fileName (${result.bytes} bytes)")
                result.uri
            } else {
                Log.e(TAG, "✗ $fileName save failed")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download $fileName error: ${e.message}")
            null
        }
    }

    // ── Network Helpers ──

    private fun openConnection(url: URL): HttpURLConnection {
        val network = p2pNetwork ?: findP2pNetwork()?.also { p2pNetwork = it }
        val conn =
                if (network != null) {
                    network.openConnection(url) as HttpURLConnection
                } else {
                    url.openConnection() as HttpURLConnection
                }
        conn.instanceFollowRedirects = true
        return conn
    }

    private fun findP2pNetwork(): Network? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            for (n in cm.allNetworks) {
                val lp = cm.getLinkProperties(n) ?: continue
                val ifName = lp.interfaceName ?: ""
                val has49 =
                        lp.linkAddresses.any {
                            it.address.hostAddress?.startsWith("192.168.49.") == true
                        }
                if (ifName.contains("p2p", ignoreCase = true) || has49) return n
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun bindProcess(network: Network?) {
        if (network == null) return
        if (boundNetwork == network) return
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.bindProcessToNetwork(network)
            boundNetwork = network
        } catch (_: Exception) {}
    }

    private fun unbindProcess() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.bindProcessToNetwork(null)
            boundNetwork = null
        } catch (_: Exception) {}
    }

    private fun buildCandidateIps(): List<String> {
        val list = mutableListOf<String>()
        bleIp?.let { list.add(it) }
        groupOwnerIp?.let { ip ->
            // Try flipping last octet
            val parts = ip.split(".")
            if (parts.size == 4) {
                val lastOctet = parts[3].toIntOrNull() ?: 0
                val flipped =
                        if (lastOctet == 1) "${parts[0]}.${parts[1]}.${parts[2]}.134"
                        else "${parts[0]}.${parts[1]}.${parts[2]}.1"
                list.add(flipped)
            }
            list.add(ip)
        }
        return list.distinct()
    }

    private fun mediaConfigOk(ip: String, timeoutMs: Int): Boolean {
        return try {
            val conn = openConnection(URL("http://$ip/files/media.config"))
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "HEAD"
            val ok = conn.responseCode == HttpURLConnection.HTTP_OK
            conn.disconnect()
            ok
        } catch (_: Exception) {
            false
        }
    }

    private fun scanSubnet(prefix: String): String? {
        // Quick scan for HTTP server on /24
        for (i in 1..254) {
            val ip = "$prefix$i"
            if (ip == groupOwnerIp) continue
            if (mediaConfigOk(ip, 500)) return ip
        }
        return null
    }

    // ── Gallery Save Helpers (mirrors MainActivity logic) ──

    private data class SaveResult(val success: Boolean, val uri: String?, val bytes: Long)

    private fun saveJpegToGallery(
            input: InputStream,
            displayName: String,
            takenMs: Long
    ): SaveResult {
        return try {
            val resolver = context.contentResolver
            val values =
                    ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.DATE_TAKEN, takenMs)
                        put(MediaStore.Images.Media.DATE_ADDED, takenMs / 1000)
                        put(MediaStore.Images.Media.DATE_MODIFIED, takenMs / 1000)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(
                                    MediaStore.Images.Media.RELATIVE_PATH,
                                    Environment.DIRECTORY_DCIM + "/CyanBridge"
                            )
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }
            val uri =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                            ?: return SaveResult(false, null, 0)
            var bytes = 0L
            resolver.openOutputStream(uri, "w")?.use { out ->
                val buf = ByteArray(8 * 1024)
                while (true) {
                    val r = input.read(buf)
                    if (r <= 0) break
                    out.write(buf, 0, r)
                    bytes += r
                }
                out.flush()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                        uri,
                        ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                        null,
                        null
                )
            }
            SaveResult(true, uri.toString(), bytes)
        } catch (e: Exception) {
            Log.e(TAG, "saveJpeg failed: ${e.message}")
            SaveResult(false, null, 0)
        }
    }

    private fun saveMp4ToGallery(
            input: InputStream,
            displayName: String,
            takenMs: Long
    ): SaveResult {
        return try {
            val resolver = context.contentResolver
            val values =
                    ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.DATE_TAKEN, takenMs)
                        put(MediaStore.Video.Media.DATE_ADDED, takenMs / 1000)
                        put(MediaStore.Video.Media.DATE_MODIFIED, takenMs / 1000)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(
                                    MediaStore.Video.Media.RELATIVE_PATH,
                                    Environment.DIRECTORY_DCIM + "/CyanBridge"
                            )
                            put(MediaStore.Video.Media.IS_PENDING, 1)
                        }
                    }
            val uri =
                    resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                            ?: return SaveResult(false, null, 0)
            var bytes = 0L
            resolver.openOutputStream(uri, "w")?.use { out ->
                val buf = ByteArray(128 * 1024)
                while (true) {
                    val r = input.read(buf)
                    if (r <= 0) break
                    out.write(buf, 0, r)
                    bytes += r
                }
                out.flush()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                        uri,
                        ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                        null,
                        null
                )
            }
            SaveResult(true, uri.toString(), bytes)
        } catch (e: Exception) {
            Log.e(TAG, "saveMp4 failed: ${e.message}")
            SaveResult(false, null, 0)
        }
    }

    private fun saveOpusToLibrary(
            input: InputStream,
            displayName: String,
            takenMs: Long
    ): SaveResult {
        return try {
            val resolver = context.contentResolver
            val rawBytes = readAllBytes(input)
            val title = displayName.substringBeforeLast('.', displayName)
            val values =
                    ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                        put(MediaStore.Audio.Media.MIME_TYPE, "audio/ogg")
                        put(MediaStore.Audio.Media.TITLE, title)
                        put(MediaStore.Audio.Media.IS_MUSIC, 0)
                        put(MediaStore.MediaColumns.DATE_ADDED, takenMs / 1000)
                        put(MediaStore.MediaColumns.DATE_MODIFIED, takenMs / 1000)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(
                                    MediaStore.MediaColumns.RELATIVE_PATH,
                                    Environment.DIRECTORY_DCIM + "/CyanBridge"
                            )
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                    }
            val uri =
                    resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                            ?: return SaveResult(false, null, 0)
            var bytes = 0L
            resolver.openOutputStream(uri, "w")?.use { out ->
                out.write(rawBytes)
                bytes = rawBytes.size.toLong()
                out.flush()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                        uri,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                        null,
                        null
                )
            }
            SaveResult(true, uri.toString(), bytes)
        } catch (e: Exception) {
            Log.e(TAG, "saveOpus failed: ${e.message}")
            SaveResult(false, null, 0)
        }
    }

    private fun readAllBytes(input: InputStream): ByteArray {
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(32 * 1024)
        while (true) {
            val r = input.read(buf)
            if (r <= 0) break
            bos.write(buf, 0, r)
        }
        return bos.toByteArray()
    }

    private fun parseTakenTimeMs(fileName: String): Long? {
        val digits = fileName.takeWhile { it.isDigit() }
        if (digits.length < 14) return null
        return try {
            val base = digits.substring(0, 14)
            val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            val d = sdf.parse(base) ?: return null
            val ms = digits.substring(14).take(3).toIntOrNull() ?: 0
            d.time + ms
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanupP2p() {
        unbindProcess()
        try {
            val wifiP2pManager = WifiP2pManagerSingleton.getInstance(context)
            wifiP2pManager.unregisterReceiver()
        } catch (_: Exception) {}
    }
}
