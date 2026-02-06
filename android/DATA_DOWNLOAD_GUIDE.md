# 数据下载指南（BLE + Wi‑Fi P2P + HTTP）

本文档整理了 CyanBridge 中的照片/视频/录音下载流程，并给出可直接迁移到新项目的
核心步骤与代码片段。

流程总览：
1) BLE 连接眼镜
2) 启动 Wi‑Fi P2P 发现/连接
3) 发送 BLE 指令进入传输模式并请求 IP
4) 解析眼镜 HTTP IP
5) 下载 `media.config` 并解析文件列表
6) 下载 JPG/MP4/OPUS
7) 保存到 MediaStore（相册）
8) 退出传输模式 + 清理 P2P

参考源码：`CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/MainActivity.kt`

---

## 1) 必要权限

### Manifest
- `BLUETOOTH`, `BLUETOOTH_ADMIN`
- `BLUETOOTH_CONNECT`（Android 12+）
- `BLUETOOTH_SCAN`（Android 12+）
- `ACCESS_FINE_LOCATION`（常用于 P2P 发现）
- `NEARBY_WIFI_DEVICES`（Android 13+）
- `INTERNET`

### 运行时
- Android 13+：下载前请求 `NEARBY_WIFI_DEVICES`
- Android 12 及以下：确保位置权限已授权

---

## 2) 允许明文 HTTP

眼镜是 HTTP 服务（非 HTTPS），需要允许明文：

`android:networkSecurityConfig="@xml/network_security_config"`

将 `CyanBridge/app/src/main/res/xml/network_security_config.xml`
复制到新项目中。

---

## 3) 核心流程（高层）

### 启动下载
```kotlin
private fun startDataDownload() {
    if (!BleOperateManager.getInstance().isConnected) return

    // 重置状态
    downloadP2pConnected = false
    downloadBleIp = null
    downloadWifiIp = null
    downloadInProgress = false
    downloadResolvedHttpIp = null
    unbindProcessFromNetwork()

    // 注册下载 notify 监听（cmdType=2）
    LargeDataHandler.getInstance().addOutDeviceListener(2, downloadNotifyListener)

    // 启动 P2P 发现
    val wifiP2pManager = WifiP2pManagerSingleton.getInstance(this)
    downloadWifiP2pManager = wifiP2pManager
    wifiP2pManager.resetFailCount()
    wifiP2pManager.registerReceiver()
    wifiP2pManager.addCallback(downloadP2pCallback)
    wifiP2pManager.startPeerDiscovery()

    // 让眼镜进入传输模式并回报 IP
    LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x04)) { _, _ -> }
}
```

### P2P 回调
```kotlin
private val downloadP2pCallback = object : WifiP2pManagerSingleton.WifiP2pCallback {
    override fun onPeersChanged(peers: Collection<WifiP2pDevice>) {
        peers.firstOrNull()?.let { wifiP2pManager.connectToDevice(it) }
    }

    override fun onConnected(info: WifiP2pInfo) {
        downloadP2pConnected = info.groupFormed
        downloadWifiIp = info.groupOwnerAddress?.hostAddress
        downloadP2pNetwork = findLikelyP2pNetwork()
        bindProcessToNetwork(downloadP2pNetwork)
        maybeStartHttpDownload("P2P")
    }

    override fun onDisconnected() {
        downloadP2pConnected = false
        downloadP2pNetwork = null
        unbindProcessFromNetwork()
    }
}
```

### BLE 下载通知监听（cmdType=2）
```kotlin
private inner class DownloadNotifyListener : GlassesDeviceNotifyListener() {
    override fun parseData(cmdType: Int, response: GlassesDeviceNotifyRsp) {
        val load = response.loadData
        if (load.size < 7) return
        when (load[6].toInt()) {
            0x08 -> {
                val ip = "${ByteUtil.byteToInt(load[7])}." +
                        "${ByteUtil.byteToInt(load[8])}." +
                        "${ByteUtil.byteToInt(load[9])}." +
                        "${ByteUtil.byteToInt(load[10])}"
                onDownloadBleIp(ip)
            }
            0x09 -> {
                val errorCode = ByteUtil.byteToInt(load[7])
                if (errorCode == 255) maybeResetP2pAfterError255("download")
            }
        }
    }
}
```

---

## 4) 解析眼镜 HTTP IP

优先使用 BLE 回报 IP，不要依赖 groupOwner（常见 192.168.49.1 是手机）。

```kotlin
private fun maybeStartHttpDownload(source: String) {
    if (downloadInProgress || downloadAttemptJob?.isActive == true) return

    downloadAttemptJob = CoroutineScope(Dispatchers.IO).launch {
        val overallTimeoutMs = 90_000L
        val startMs = System.currentTimeMillis()

        while (isActive && System.currentTimeMillis() - startMs < overallTimeoutMs) {
            for (candidate in buildCandidateIps()) {
                if (isProbablyGroupOwnerIp(candidate)) continue
                if (mediaConfigOk(candidate, 2000)) {
                    downloadResolvedHttpIp = candidate
                    downloadInProgress = true
                    downloadMediaList(candidate)
                    return@launch
                }
            }
            delay(1500)
        }
    }
}
```

---

## 5) 下载 media.config 并解析列表

```kotlin
private fun downloadMediaList(deviceIp: String) {
    CoroutineScope(Dispatchers.IO).launch {
        val url = "http://$deviceIp/files/media.config"
        val conn = openHttpConnection(URL(url))
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 30000
        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
            val content = conn.inputStream.bufferedReader().use { it.readText() }
            parseMediaList(content, deviceIp)
        }
        conn.disconnect()
    }
}
```

解析并分组：
```kotlin
private fun parseMediaList(content: String, deviceIp: String) {
    val jpg = mutableListOf<String>()
    val mp4 = mutableListOf<String>()
    val opus = mutableListOf<String>()

    content.trim().lines().forEach { line ->
        val name = line.trim()
        if (name.isEmpty()) return@forEach
        when {
            name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> jpg.add(name)
            name.endsWith(".mp4", true) -> mp4.add(name)
            name.endsWith(".opus", true) -> opus.add(name)
        }
    }

    downloadAllMediaFiles(jpg, mp4, opus, deviceIp)
}
```

---

## 6) 保存到相册（MediaStore）

在 CyanBridge 中：
- JPG -> `MediaStore.Images`
- MP4 -> `MediaStore.Video`
- OPUS -> `MediaStore.Audio`（必要时封装成 Ogg）

示例（JPG）：
```kotlin
private suspend fun downloadSingleJpgFile(fileName: String, deviceIp: String): Boolean {
    val url = "http://$deviceIp/files/$fileName"
    val conn = openHttpConnection(URL(url))
    conn.requestMethod = "GET"
    conn.connectTimeout = 10000
    conn.readTimeout = 30000

    return if (conn.responseCode == HttpURLConnection.HTTP_OK) {
        val takenMs = parseTakenTimeMillisFromFilename(fileName) ?: System.currentTimeMillis()
        val saved = conn.inputStream.use { input ->
            saveJpegToGallery(input, fileName, takenMs)
        }
        saved.success
    } else {
        false
    }
}
```

OPUS 处理逻辑见 `saveOpusToLibrary()`：
- 如果以 `OggS` 开头，直接写入
- 否则按照固定包/长度前缀尝试封装 Ogg/Opus

---

## 7) 绑定到 P2P 网络

必须绑定到 P2P 网络，避免路由走错（尤其三星）。

```kotlin
private fun openHttpConnection(url: URL): HttpURLConnection {
    val network = downloadP2pNetwork ?: findLikelyP2pNetwork()?.also { downloadP2pNetwork = it }
    return if (network != null) {
        network.openConnection(url) as HttpURLConnection
    } else {
        url.openConnection() as HttpURLConnection
    }
}
```

---

## 8) 下载完成后退出传输模式

完成后显式退出传输模式，促使眼镜更新 media count：

```kotlin
private fun exitTransferModeAfterDownload() {
    if (!BleOperateManager.getInstance().isConnected) return
    LargeDataHandler.getInstance().glassesControl(
        byteArrayOf(0x02, 0x01, 0x0F)
    ) { _, _ -> }
}
```

---

## 9) 清理 P2P

```kotlin
private fun cleanupP2pAfterDownload() {
    downloadAttemptJob?.cancel()
    downloadAttemptJob = null
    unbindProcessFromNetwork()
    downloadWifiP2pManager?.removeGroup { }
    downloadWifiP2pManager?.unregisterReceiver()
    downloadWifiP2pManager = null
    downloadWifiP2pCallback = null
    downloadP2pConnected = false
    downloadInProgress = false
    downloadP2pNetwork = null
    downloadResolvedHttpIp = null
}
```

---

## 10) 推荐日志 tag

用于排查：
- `DataDownload`
- `DeviceNotify`
- `WifiP2pManagerSingleton`
- `WifiP2pBroadcastReceiver`
- `BleIpBridge`

示例：
```bash
adb logcat -d -s DataDownload DeviceNotify WifiP2pManagerSingleton WifiP2pBroadcastReceiver BleIpBridge
```

---

## 11) 新项目快速迁移清单

- [ ] 集成 `glasses_sdk_*.aar`
- [ ] 拷贝 `WifiP2pManagerSingleton` + `WifiP2pBroadcastReceiver`
- [ ] 添加网络安全配置（允许 HTTP）
- [ ] 添加下载状态字段（同 MainActivity）
- [ ] 注册下载 notify 监听（cmdType=2）
- [ ] 绑定 P2P 网络后发 HTTP
- [ ] 下载 `media.config` 并解析
- [ ] 保存到 MediaStore
- [ ] 退出传输模式 + 清理 P2P

