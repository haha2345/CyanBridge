# 最新官方 APP (Heycyan.apk) 图像识别深度逆向分析

> 工具: JADX 反编译最新 `Heycyan.apk` (250MB, 包名 `com.glasssutdio.wear`)
> 
> 对比: 旧版 `Cyan_Glasses_1.0.1.20_20251108.apk` (包名 `com.aitowe.aitoglasses`)

---

## 一、核心结论

> [!IMPORTANT]
> 1. **官方 APP 不做 zlib 解压** — 原始 BLE 数据直接写入 `.jpg` 文件
> 2. **官方 APP 不做本地图像格式校验** — 没有预先用 `BitmapFactory.decodeByteArray()` 验证
> 3. **写入方式用 append 模式** — `FileOutputStream(file, true)` 多个分块追加写入同一个文件
> 4. **新版增加了 `ImageProcessor`** — 仅在云台相机模式下用 `BitmapFactory.decodeFile()` 做旋转裁剪，其他模式直接 base64 编码发给服务端
> 5. **`writeToFile1` 写入的数据就是合法 JPEG** — `ImageProcessor.decodeWithMemoryControl()` 能用 `BitmapFactory.decodeFile()` 成功解码

---

## 二、新旧版本对比

| 特性 | 旧版 (`com.aitowe.aitoglasses`) | 新版 (`com.glasssutdio.wear`) |
|------|--------------------------------|-------------------------------|
| 包名 | `com.aitowe.aitoglasses` | `com.glasssutdio.wear` |
| `getPictureThumbnails` | 相同 (`subData[11:]`) | 相同 (`subData[11:]`) |
| `writeToFile1` | `FileOutputStream(file, true)` append | 相同 |
| 图像后处理 | 无 | **新增 `ImageProcessor`** |
| base64 前缀 | `data:image/jpeg;base64,` | 相同 |
| `chatGpt` 参数 | `chatGpt(2, "", base64)` | `chatGpt(2, "", base64, timestamp)` 多了时间戳 |
| 新 BLE 命令 | 无 | `chatGPTDeepSeekContent`, `chatGptQuestion`, `chatGpt` (文字下发到眼镜) |
| 云台支持 | 无 | 识别 `loadData[6]=22` 解析云台角度 |
| Agent 模式 | 无 | `agentAiChatMode()` 新 AI agent 模式 |

---

## 三、完整数据流（最新版）

### 3.1 触发入口

```java
// MainActivity.java:1280-1294
byte b = response.getLoadData()[6];
if (b == 2) {
    // 如果 loadData[9]==2 → 设置 AI Vision 提示文本
    if (response.getLoadData().length > 9 && response.getLoadData()[9] == 2) {
        AiChatDepository.INSTANCE.getGetInstance().setUserVisionText(...);
    }
    
    // 设置 TTS 语言
    GlassesAzureSpeechRecognizer.getInstance().setTranslateTo(...);
    // 播放开始提示音（"--"）
    GlassesAzureSpeechRecognizer.getInstance().startTTS(" ");
    
    // 获取缩略图
    final String path = GFileUtilKt.getAlbumDirFile().getAbsolutePath();
    final String fileName = "Thumb_" + System.currentTimeMillis() + ".jpg";
    LargeDataHandler.getInstance().getPictureThumbnails(...);
}
```

### 3.2 SDK 分块协议（与旧版完全相同）

```java
// LargeDataHandler.java:517-538
public void getPictureThumbnails(ILargeDataImageResponse listener) {
    respMap.put(-3, new ILargeDataResponse<PictureThumbnailsResponse>() {
        public void parseData(int cmdType, PictureThumbnailsResponse response) {
            if ((cmdType & 0xFF) == 0xFD) {
                int total    = ByteUtil.bytesToInt(subData[7:9]);
                int currIndex = ByteUtil.bytesToInt(subData[9:11]);
                
                byte[] imageData = subData[11:];  // 纯图像载荷
                
                if (currIndex + 1 != total) {
                    syncPictureThumbnails(currIndex + 1);
                    listener.parseData(cmdType, false, imageData);
                } else {
                    listener.parseData(cmdType, true, imageData);  // 最后一块
                }
            }
        }
    });
    syncPictureThumbnails(0);
}
```

### 3.3 数据处理回调（核心！）

```java
// MainActivity.java:1518-1534
public final void invoke2(MyDeviceNotifyListener ktxRunOnBgSingle) {
    byte[] data = bArr;
    String path = str;
    
    // 1️⃣ 直接写入文件（append 模式）— 不做任何格式检测
    GFileUtilKt.writeToFile1(data, path, fileName);
    
    if (success) {  // 最后一块到达
        // 2️⃣ 云台相机旋转修正（仅在云台模式下）
        if (GlassesWearJavaApplication.getInstance().getGimbalCameraAngle() > 0
            && ImageProcessor.INSTANCE.processAndReplaceGimbalCamera(
                path + "/" + fileName,
                GlassesWearJavaApplication.getInstance().getGimbalCameraAngle())) {
            GlassesWearJavaApplication.getInstance().setGimbalCameraAngle(0);
        }
        
        // 3️⃣ 保存聊天记录
        long timestamp = System.currentTimeMillis();
        AiChatDepository.INSTANCE.getGetInstance()
            .saveChatImageFromGlasses(fileName, path + "/" + fileName, timestamp);
        
        // 4️⃣ Base64 编码（读取原始文件字节）
        String base64 = CustomBase64Encoder.imageToBase64(path + "/" + fileName);
        // 返回 "data:image/jpeg;base64,..." 格式
        
        // 5️⃣ 发送给服务端 VL API
        AiChatDepository.chatGpt$default(
            AiChatDepository.INSTANCE.getGetInstance(),
            2, "", base64, timestamp, false, 16, null);
    }
}
```

### 3.4 writeToFile1 实现（与旧版完全相同）

```java
// GFileUtilKt.java
public static final void writeToFile1(byte[] data, String path, String fileName) {
    File file = new File(path + "/" + fileName);
    // 创建目录和文件...
    FileOutputStream fos = new FileOutputStream(file, true);  // ← APPEND 模式！
    BufferedOutputStream bos = new BufferedOutputStream(fos);
    bos.write(data);
    bos.close();
}
```

---

## 四、ImageProcessor 深度分析（新版新增）

### 4.1 用途

`ImageProcessor` 仅在**云台相机**模式下使用（`gimbalCameraAngle > 0`），用于旋转图像并压缩。

### 4.2 关键方法

```java
// ImageProcessor.java:119-196
public boolean processAndReplaceGimbalCamera(String filePath, int rotation) {
    File file = new File(filePath);
    
    // 1. 用 BitmapFactory 解码文件
    Bitmap bitmap = decodeWithMemoryControl(file);
    //   → BitmapFactory.decodeFile(file.getAbsolutePath(), options)
    //   → 如果解码失败会抛 IOException("Decode failed")
    
    // 2. 旋转
    if (rotation != 0) {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true);
    }
    
    // 3. 缩放（2x）
    bitmap = scaleBitmap(bitmap, 2.0f);
    
    // 4. 智能压缩（目标 ≤ 2048KB，质量从 95 降到 40）
    smartCompress(bitmap, tempFile);
    
    // 5. 原子性替换原文件
    atomicReplace(tempFile, file);
}
```

> [!IMPORTANT]
> `decodeWithMemoryControl()` 内部使用 `BitmapFactory.decodeFile()` — 这证明官方 APP 期望 `writeToFile1` 写入的数据是**合法的 JPEG 格式**。
> 如果 CyanBridge 收到的 `78 9d` 开头数据在官方 APP 也无法被 `ImageProcessor` 解码，
> 那说明**新型号眼镜可能不走这个云台路径**，而是直接 base64 发给服务端。

---

## 五、数据流图

```mermaid
sequenceDiagram
    participant G as 眼镜 (BLE)
    participant SDK as LargeDataHandler
    participant MA as MainActivity
    participant File as 本地 .jpg 文件
    participant IP as ImageProcessor
    participant B64 as CustomBase64Encoder
    participant API as 服务端 VL API (SSE)

    G->>SDK: DeviceNotify [6]=0x02
    MA->>SDK: getPictureThumbnails()
    SDK->>G: syncPictureThumbnails(0)
    
    loop n 个分块
        G->>SDK: PictureThumbnailsResponse
        SDK->>MA: callback(success, data[11:])
        MA->>File: writeToFile1(data) [APPEND]
    end
    
    Note over MA: success = true
    
    alt 云台模式 (gimbalAngle > 0)
        MA->>IP: processAndReplaceGimbalCamera(filePath, angle)
        IP->>File: BitmapFactory.decodeFile()
        IP->>IP: rotate + scale + compress
        IP->>File: atomicReplace() 覆盖原文件
    end
    
    MA->>B64: imageToBase64(filePath)
    B64->>File: FileInputStream 读取全部字节
    B64-->>MA: "data:image/jpeg;base64,..."
    MA->>API: chatGpt(2, "", base64, timestamp)
    API-->>MA: SSE 流式回复 (VL 结果)
```

---

## 六、与 CyanBridge 的差异对比

| 特性 | 官方 APP (最新) | CyanBridge |
|------|----------------|------------|
| **数据拼接** | `writeToFile1` 磁盘 append | `ByteArrayOutputStream` 内存拼接 |
| **本地格式校验** | ❌ **直接写文件，不验证** | ✅ `BitmapFactory.decodeByteArray()` |
| **zlib 解压** | ❌ 不做 | ✅ 检测 `0x78` 头并尝试 inflate |
| **JPEG SOI 搜索** | ❌ 不做 | ✅ 搜索 `FF D8` |
| **后处理** | 仅云台模式旋转 | 无 |
| **传输格式** | `data:image/jpeg;base64,...` | 自行检测 |
| **API 调用** | SSE 流式 (`startImageStream`) | 同步 HTTP (`describeImage`) |

---

## 七、修复建议

CyanBridge 的拍照失败根本原因：在 `captureAndGetThumbnail()` 中用 `BitmapFactory.decodeByteArray()` 验证图像数据，而新型号眼镜传来的数据格式（`78 9d` 开头）无法被 Android 原生解码器识别。

**建议修复方案**：模仿官方 APP 的做法：
1. **移除 `BitmapFactory` 验证** — 不在本地解码验证图像
2. **直接写入文件** — 收到的原始字节直接保存为 `.jpg`
3. **Base64 编码后发给 VL API** — `describeImage()` 接收 base64 字符串
4. **让 Qwen-VL 自行处理格式** — 服务端 VL 模型通常能处理多种图像编码

如果需要在本地显示缩略图，可以在写入文件后尝试用 `BitmapFactory.decodeFile()` 解码（如 `ImageProcessor` 所做），但解码失败不应阻止数据发送给 VL API。
