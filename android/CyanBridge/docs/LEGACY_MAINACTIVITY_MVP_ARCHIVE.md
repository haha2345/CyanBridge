# MainActivity Legacy MVP 代码存档

> **日期**: 2026-03-11
> **来源**: `android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/MainActivity.kt` (2998 行)
> **状态**: 已归档。功能已由 Compose 架构中的 `GlassesRepository` + `MediaSyncManager` 替代。

---

## 一、AI Vision MVP (BLE 拍照→缩略图→预览)

### 流程概述
```
[按钮] → glassesControl(0x02,0x01,0x06, size, size, 0x02)  // 触发眼镜拍照
       → 等待 DeviceNotify[0x01/0x02] 确认拍照完成
       → getPictureThumbnails() 接收 JPEG 分块
       → bcfd 页级去重 OR 内容指纹去重
       → 拼装完整 JPEG → BitmapFactory 解码验证
       → 保存到 cacheDir, 启动 AiVisionPreviewActivity
```

### 关键参数
- `thumbnailSize = 0x02` (0..6, 对应不同分辨率)
- 拍照超时: 12s (等待通知) + 30s (获取缩略图)
- bcfd 协议头: `data[0]=0xBC, data[1]=0xFD`, 页号=`data[9]`, 总页数=`data[7]`, payload 从 `data[11]` 开始

### 去重逻辑 (重要！)
```kotlin
// 方式1: bcfd 页级去重
if (data.size > 11 && data[0] == 0xBC.toByte() && data[1] == 0xFD.toByte()) {
    val pageNum = data[9].toInt() and 0xFF
    totalPagesFromProtocol = data[7].toInt() and 0xFF
    if (pageNum in receivedPages) return  // 跳过重复页
    receivedPages[pageNum] = data.copyOfRange(11, data.size)
}

// 方式2: 内容指纹去重 (fallback)
fun chunkFingerprintLocal(data: ByteArray): Long {
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
```

### 现有替代
- `GlassesRepository.captureAndGetThumbnail()` — 相同的 bcfd 去重逻辑
- `ChatEngine.handleVisionRequest()` — 调用 `DashScopeVisionClient` 做 AI 分析
- `AssistantScreen` — Compose UI 显示结果

---

## 二、Data Download MVP (BLE + WiFi P2P + HTTP 媒体下载)

### 流程概述
```
[按钮] → 检查 BLE 连接 + 权限
       → WifiP2pManagerSingleton: registerReceiver(), startPeerDiscovery()
       → glassesControl(0x02,0x01,0x04) // 让眼镜开启WiFi
       → 等待 BLE 通知 (0x08) 获取眼镜 WiFi IP
       → 同时等待 WiFi P2P 连接回调
       → bindProcessToNetwork(p2pNetwork)
       → 探测候选IP列表 → 子网扫描(192.168.49.0/24)
       → GET http://<ip>/files/media.config  // 获取文件列表
       → 逐个下载 jpg/mp4/opus → 保存到 Gallery/Music
       → cleanupP2pAfterDownload()
```

### WiFi P2P 网络绑定 (关键！)
```kotlin
// 必须将进程绑定到 P2P 网络，否则 HTTP 请求走默认网络
private fun bindProcessToNetwork(network: Network?) {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    cm.bindProcessToNetwork(network)
}

// HTTP 连接需要用 P2P 网络的 SocketFactory
private fun openHttpConnection(url: URL): HttpURLConnection {
    val network = downloadP2pNetwork ?: findLikelyP2pNetwork()
    return if (network != null) {
        network.openConnection(url) as HttpURLConnection
    } else {
        url.openConnection() as HttpURLConnection
    }
}
```

### 子网扫描逻辑
```kotlin
// 并发扫描 192.168.49.2~254，找到有 HTTP 服务的 IP
private suspend fun discoverGlassesIpByScan(prefix: String): String? {
    val sem = Semaphore(32)  // 限制并发
    for (host in 2..254) {
        launch(Dispatchers.IO) {
            sem.withPermit {
                if (isPortOpen(ip, 80, 300)) {
                    if (mediaConfigOk(ip, 1200)) found.complete(ip)
                }
            }
        }
    }
}
```

### Ogg/Opus 封装 (独有！)
眼镜录音文件为裸 Opus 数据，需要封装为标准 Ogg/Opus 格式才能在 Android MediaStore 正常播放：
```kotlin
private fun wrapOpusIfNeeded(raw: ByteArray): Pair<ByteArray, String>
// 解析 length-prefixed packets → 构建 OggS 页 → OpusHead + OpusTags
private fun buildOggOpusFromPackets(packets: List<ByteArray>, packetDurationMs: Int): ByteArray
// CRC32 计算 (Ogg 标准)
private fun oggCrc(data: ByteArray): Int
```

### 画廊保存逻辑
```kotlin
// JPG → MediaStore.Images (DCIM/CyanBridge)
private fun saveJpegToGallery(input, displayName, takenTimeMs): GallerySaveResult

// MP4 → MediaStore.Video (DCIM/CyanBridge)
private fun saveMp4ToGallery(input, displayName, takenTimeMs): GallerySaveResult

// Opus → MediaStore.Audio (Music/CyanBridge)
private fun saveOpusToLibrary(input, displayName, takenTimeMs): GallerySaveResult
```

### 现有替代
- `MediaSyncManager.kt` (1184 行) — 完整的 WiFi P2P 媒体同步实现
- `GlassesRepository.kt` — BLE 控制和事件监听

---

## 三、DeviceNotifyListener (BLE 事件路由)

### 事件码表
| 字节 `loadData[6]` | 事件 | 处理 |
|---|---|---|
| `0x01` | 拍照完成通知 | 触发 aiVisionMvpPhotoSignal |
| `0x02` | AI 快速识别 / 拍照按钮 | 触发缩略图下载 + hijack |
| `0x03` | 语音唤醒 / AI 按钮 | 触发 ASR 或 hijack 到手机助手 |
| `0x04` | OTA 升级进度 | download/soc/nor 进度 |
| `0x05` | 电池报告 | battery% + charging 状态 |
| `0x08` | WiFi IP 报告 | 4 字节 IP (用于数据下载) |
| `0x09` | P2P/WiFi 错误 | error=255 时 resetDeviceP2p |
| `0x0c` | 暂停事件 | (未实现) |
| `0x0d` | 解绑 APP | (未实现) |
| `0x0e` | 内存不足 | (未实现) |
| `0x10` | 翻译暂停 | (未实现) |
| `0x12` | 音量变化 | 音乐/通话/系统音量 + 模式 |

### 现有替代
- `GlassesRepository.kt` — 已实现完整的 `DeviceNotifyListener`
- `GlassesViewModel.kt` — 处理事件并更新 Compose UI

---

## 四、其他工具函数

| 函数 | 说明 | 是否需要保留 |
|---|---|---|
| `handleTaskerCommand()` | Tasker Intent 命令路由 | ⚠️ 需迁移到 ComposeMainActivity |
| `triggerAssistantVoiceQuery()` | 唤醒手机助手 (Gemini/ChatGPT) | 已有替代方案 |
| `triggerAssistantImageQuery()` | 图片→Gallery→Tasker 广播 | 已有替代方案 |
| `dumpOtaServerInfo()` | OTA 服务器信息 dump | 调试用，可弃 |
| `testPullModeOta()` | OTA 拉取测试 | 调试用，可弃 |
| `logLargeDataHandlerMethodsOnce()` | SDK 方法反射日志 | 调试用，可弃 |

> [!NOTE]
> `handleTaskerCommand()` 中的 Tasker Intent 路由目前仅在 MainActivity 中实现。
> 如果后续需要 Tasker 集成，需要在 `ComposeMainActivity` 中重新实现。
