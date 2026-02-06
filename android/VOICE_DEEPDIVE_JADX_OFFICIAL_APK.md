# Cyan Glasses Official APK (jadx 深挖版)：语音唤醒 / 语音识别 / LLM 对话 / TTS 全链路整理

> 本文档基于 **jadx 反编译源码** + vendor AAR（同 CyanBridge 使用的 `LargeDataHandler`）对官方 APK 的“所有语音相关逻辑”做一次更彻底的梳理。
>
> 重点回答你最关心的问题：**“语音唤醒到底怎么实现？”** —— 结论是：**唤醒词检测不在手机端做**，而是由 **眼镜端固件完成**；手机端只负责：
> 1) 通过 BLE 指令开关“眼镜侧语音唤醒”  
> 2) 监听眼镜发来的“唤醒触发事件”，随后启动语音会话（BLE 音频流 -> ASR -> LLM -> TTS）

---

## 0. 证据来源（本机路径）

### 0.1 jadx 输出

- jadx 工程目录（dex 反编译输出）：`/tmp/jadx_cyan_glasses/`
  - 源码：`/tmp/jadx_cyan_glasses/sources/`

> 说明：直接对 APK 跑 jadx 会被 APK 内部 `dump_syms/.../dump_syms.bin` 干扰；这里是对 `/tmp/cyan_glasses_apk/classes*.dex` 跑 jadx 得到的 sources。

### 0.2 APK 解包目录（资源/so/asset）

- APK 解包：`/tmp/cyan_glasses_apk/`
  - 语音唤醒引导音频：`/tmp/cyan_glasses_apk/assets/heyCyan_voice.MP3`
  - Azure Speech so（包含 kws 扩展，但不代表 app 真的用）：`/tmp/cyan_glasses_apk/lib/*/libMicrosoft.CognitiveServices.Speech.extension.kws*.so`

### 0.3 真机 logcat 证据（你提供的 wake_test.log）

- 日志文件：`/private/tmp/wake_test.log`

我在日志里抓到了**非常典型**的一段“眼镜端唤醒 -> 手机端开始 Azure ASR -> 眼镜端推送 Opus 音频流”的时间线：

- 眼镜端触发“开始说话/唤醒事件”（DeviceNotify：`cmdType=0x73`）：
  - `02-06 16:25:45.680 ... w->a：bc730200c0800301`（`wake_test.log` 第 33247 行）
- 手机端（官方 app）立刻进入语音识别流程：
  - `02-06 16:25:45.726 ... ------azure-ai测试开始说话`（第 33385 行）
  - `02-06 16:25:45.727 ... 开始解码`（第 33402 行）
- 随后出现**大量** `cmdType=0x59` 的 BLE 大包（AI Opus 音频流）：
  - `02-06 16:25:45.909 ... w->a：bc59...`（第 33591 行起连续爆发）
- 以及 TTS 播放状态（`cmdType=0x48`，对应 `aiVoicePlay(1/2/3)`）：
  - `02-06 16:25:52.735 ... a->w：bc480200c1100201`（第 47537 行，play start）
  - `02-06 16:25:54.736 ... a->w：bc48020081110202`（第 49569 行，play heartbeat）
  - `02-06 16:25:55.725 ... a->w：bc48020040d10203`（第 49752 行，play end）

如果你想自己快速验证这段证据，可以用：

```bash
egrep -n "bc730200c0800301|azure-ai|测试开始说话|开始解码|bc59|bc48" /private/tmp/wake_test.log | head
```

---

## 1. 语音唤醒（Wake Word）调查结论（最关键）

### 1.1 手机端没有做本地 KWS（Keyword Spotting）

在 `com.aitowe...` 业务代码里 **没有任何** 对以下 Azure KWS API 的调用：

- `KeywordRecognizer`
- `KeywordRecognitionModel`
- `startKeywordRecognitionAsync(...)`

证据：
- `/tmp/jadx_cyan_glasses/sources/com/microsoft/cognitiveservices/speech/KeywordRecognizer.java` 等类存在（SDK 自带）
- 但在 `/tmp/jadx_cyan_glasses/sources/com/aitowe/` 下搜索不到任何引用（仅 SDK 包内部自引用）

因此：**官方语音唤醒不是手机端用 Azure KWS 监听麦克风**。

### 1.2 官方“语音唤醒”是眼镜端实现；手机端只做“开关 + 事件响应”

手机端做两件事：

1) **开关眼镜端语音唤醒**（BLE 指令 `aiVoiceWake`）
2) **监听眼镜端触发唤醒后的 notify**（DeviceNotify 子类型 `loadData[6] == 3`），并启动语音会话

---

## 2. 语音唤醒开关：BLE 指令 `aiVoiceWake(...)`

### 2.1 UI 入口：AI 设置页的开关

文件：`/tmp/jadx_cyan_glasses/sources/com/aitowe/aitoglasses/home/activity/AIHelperActivity.java`

- 初始化时先“查询状态”：
  - `LargeDataHandler.getInstance().aiVoiceWake(false, false, listener);`
  - listener 收到 `GlassesAiVoiceRsp` 后：`binding.gsc1.setChecked(response.isOpen())`
- 用户手动切换开关（只在 `button.isPressed()` 为 true 时发送）：
  - `LargeDataHandler.getInstance().aiVoiceWake(true, isChecked, listener);`

这说明：**语音唤醒开关状态存储在眼镜端**；手机端每次进页面主动 query，再同步 UI。

### 2.2 协议细节：`aiVoiceWake` 的 action code 与 subData 字节格式

文件：`/tmp/jadx_cyan_glasses/sources/com/oudmon/ble/base/communication/LargeDataHandler.java`

方法：`aiVoiceWake(boolean enable, boolean flag, ILargeDataResponse<GlassesAiVoiceRsp> cb)`

关键行为（可直接照搬到 CyanBridge）：

- action code：`68`
- `enable == true`：
  - subData = `[0x02, flag?1:0]`
- `enable == false`（用于 query/读取当前状态）：
  - subData = `[0x01, 0x00]`

响应解析：
- `GlassesAiVoiceRsp.acceptData()` 读取 `data[7] == 1` 认为 open
  - 文件：`/tmp/jadx_cyan_glasses/sources/com/oudmon/ble/base/communication/bigData/resp/GlassesAiVoiceRsp.java`

---

## 3. 唤醒词是什么？官方 app 给出的唯一线索

文件：`/tmp/jadx_cyan_glasses/sources/com/aitowe/aitoglasses/home/activity/AIWakeUpActivity.java`

这是一个“唤醒方式教学页”，点击播放一个 assets 音频：

- `AssetsAudioPlayer.playAsset(..., "heyCyan_voice.MP3", ...)`
- 音频文件：`/tmp/cyan_glasses_apk/assets/heyCyan_voice.MP3`

该 Activity 本身不实现识别，它只是教学/引导用户怎么唤醒眼镜。

---

## 4. 唤醒触发事件：眼镜通过 DeviceNotify 告诉手机“开始说话/开始 AI 会话”

### 4.1 事件入口：MainActivity 的 DeviceNotify 监听器

文件：`/tmp/jadx_cyan_glasses/sources/com/aitowe/aitoglasses/MainActivity.java`

注册 listener（每次 onResume 都会注册，key=100）：

```java
private final void registerOutDeviceListener() {
    LargeDataHandler.getInstance().addOutDeviceListener(100, getDeviceNotifyListener());
}
```

### 4.2 唤醒事件的判定条件（核心）

同文件中：`MainActivity.MyDeviceNotifyListener.parseData(...)`

```java
switch (response.getLoadData()[6]) {
  case 3:
    // ... 这里就是“语音唤醒/AI唤醒触发”入口
    if (response.getLoadData()[7] == 1) {
        GlassesAzureSpeechRecognizer.getInstance().start();
    }
    break;
}
```

含义：

- `cmdType`（外层）来自 BLE 大包通知（通常 logcat 会显示 cmdType=115/0x73）
- `loadData[6] == 3`：表示眼镜发来的“AI 语音触发”事件（官方没有给枚举名，只能从行为推断）
- `loadData[7] == 1`：表示“开始说话/开始采集语音”之类的子状态（官方 log：`"------azure-ai测试开始说话"`）

### 4.3 网络不可用时的特殊回包：`aiVoicePlay(241)`

同一段代码里：

- 如果手机无网络：
  - toast 提示
  - `LargeDataHandler.getInstance().aiVoicePlay(241, null);`

推断：`241 (0xF1)` 是一种“错误/拒绝开始会话”的状态码，回传给眼镜用于提示用户（例如眼镜端播报“无网络”之类）。

---

## 5. 唤醒后手机侧到底做了什么（从事件到开始 ASR 的完整步骤）

仍在 `MainActivity.MyDeviceNotifyListener.parseData` 的 `case 3` 中：

1) 检查网络（无网络则回 `aiVoicePlay(241)` 并退出）
2) 如果不在翻译模式（`!GlassesWearJavaApplication.getInstance().isTranslateDoing()`），开始准备会话：
   - 设置 ASR 语言：
     - `GlassesAzureSpeechRecognizer.getInstance().setAiLanguage( switchRTAsrLanguage(UserConfig.getAiLanguageCode()) )`
   - 停掉上一次 SSE（防止多路 SSE 重叠）：
     - `SSEHandler.INSTANCE.getInstance().stop()`
   - `voiceType = GPT (0)`，并 `initData()`：
     - `GlassesAzureSpeechRecognizer.getInstance().voiceType = 0;`
     - `GlassesAzureSpeechRecognizer.getInstance().initData();`
       - 里面会调用 `initAzure()`，其内部会立即 `recognizeFromMicrophone()` 启动 continuous recognition
   - 设置 `translateTo`（影响后续 TTS voice 选择/语言）：
     - `GlassesAzureSpeechRecognizer.getInstance().setTranslateTo(UserConfig.getAiLanguageCode())`
3) 若 `loadData[7] == 1`：
   - `GlassesAzureSpeechRecognizer.getInstance().start();`

### 5.1 `GlassesAzureSpeechRecognizer.start()` 做了哪些关键事

文件：`/tmp/jadx_cyan_glasses/sources/com/aitowe/aitoglasses/ai/spark/GlassesAzureSpeechRecognizer.java`

`start()` 重点是：**准备接收眼镜 BLE 音频流并喂给 ASR**，但它**不会**发送 `[0x02,0x01,0x07]` 去请求眼镜开始推流。

它做的事：

- 重启播放系统（给 TTS 播放准备）：`AudioTrackManager.restartPlay()`
- 启动 Opus decode：
  - `decodeOpusStream()` -> `OpusManager.startDecodeStream(opusOption, onDecodeStreamCallback)`
  - `opusOption`：`hasHead=false, sampleRate=16000, packetSize=40, channel=1`
- 注册 cmdType=89(0x59) 的 BLE 大包监听（AI 音频流）：
  - `LargeDataHandler.getInstance().initPackageNotify(aiChatListener)`
  - `aiChatListener`：`cmdType==89` 时取 `subData[6..]` 作为 Opus payload
- 启动 **BLE 心跳**（维持会话/设备状态）：
  - `heartbeatTimer.start(() -> LargeDataHandler.syncHeartBeat(7), 3000ms)`
  - `syncHeartBeat(7)` 底层发送 cmdId=69，subData=`[7,1]`
- 启动超时保护：
  - GPT 模式：`timeoutTask(15s)`，超时会 `glassesControl([2,1,11])` 并 stopHeartBeat

> 这也反向说明：**眼镜在触发唤醒事件后，很可能会自行开始推送 cmdType=89 的音频流**；手机端只要及时进入 `start()` 即可开始解码/识别。

---

## 6. “说完了”的判定：Azure recognized 事件（非本地 VAD）

文件：`/tmp/jadx_cyan_glasses/sources/com/aitowe/aitoglasses/ai/spark/GlassesAzureSpeechRecognizer.java`

关键点：

- `initAzure()` 创建 `PushAudioInputStream` + `SpeechRecognizer`
- 直接调用 `recognizeFromMicrophone()`：
  - 注册 `recognizing`（中间结果）与 `recognized`（最终结果）
  - 调 `startContinuousRecognitionAsync()`

在 GPT 模式下的 `recognized` 事件里（简化描述）：

1) 拿到最终文本 `result`
2) 发 `glassesControl([2,1,11])` 停止眼镜端 AI 语音会话/推流
3) 停止 timer、停止识别、清理状态
4) `AiChatDepository.saveChatFromSparkChain(result)`
5) `AiChatDepository.chatGpt(chatType=1, content=result, imageBase64="")`

---

## 7. LLM（官方 SSE）与签名（与语音唤醒无关但属于语音全链路）

文件：`/tmp/jadx_cyan_glasses/sources/com/aitowe/aitoglasses/api/SSEHandler.java`

- SSE URL 规则：`https://www.qlifesnap.com/glasses/ai/chat/stream/{uid}/{country}/{appName}/{language}`
- headers：
  - `token: UserConfig.getUserToken()`
  - `Accept: text/event-stream`
  - `X-Timestamp`, `X-Signature`（见 `processSignedRequest`）
- body：`List<AiChatBean(role, content, type)>` -> Gson -> application/json

> 这部分若你要接自有 LLM，完全可以替换掉，仅复用前后（BLE 音频 + ASR + TTS + 回传状态）。

---

## 8. TTS 播放与眼镜回传（aiVoicePlay 1/2/3）

文件：`/tmp/jadx_cyan_glasses/sources/com/aitowe/aitoglasses/ai/spark/GlassesAzureSpeechRecognizer.java`

- 播放开始：`aiVoicePlay(1)`
- 播放心跳：每 2s `aiVoicePlay(2)`
- 播放完成：`aiVoicePlay(3)`

并且 DeviceNotify `loadData[6]==12 && loadData[7]==1` 会触发“停止播放/停止 SSE/清队列”等（推断为眼镜端发来的“停止播报/停止本次对话”指令）：

文件：`/tmp/jadx_cyan_glasses/sources/com/aitowe/aitoglasses/MainActivity.java` 的 `case 12`

---

## 9. 你在 CyanBridge 里实现“官方同款语音唤醒”的最小闭环（只讲逻辑，不改代码）

要复现官方“语音唤醒 -> 语音对话”闭环，本质只需要做到：

1) BLE 连接完成后 `LargeDataHandler.initEnable()`（打开 notify）
2) 提供一个设置页/入口，调用：
   - query：`aiVoiceWake(false,false,cb)` -> cb 的 `GlassesAiVoiceRsp.isOpen()`
   - set：`aiVoiceWake(true, enable, cb)`
3) 常驻注册 DeviceNotify listener（类似官方 `addOutDeviceListener(100, ...)`）
4) 在 DeviceNotify listener 中处理：
   - `loadData[6] == 3 && loadData[7] == 1`：
     - 网络检查；无网络则 `aiVoicePlay(241)`（可选但推荐对齐官方）
     - 初始化 ASR（PushAudioInputStream continuous）
     - 调 `start()`：注册 cmdType=89 音频流、启动 Opus decode、启动 `syncHeartBeat(7)` 定时器
5) 按官方 Opus “40 bytes packet + 120 bytes 缓冲”写入 `OpusManager.writeAudioStream`

---

## 10. 待补全/需要真机验证的点（我建议下一步做的事）

1) `DeviceNotify loadData[6]==3` 的完整字段语义：
   - 当前只从官方行为推断：“AI唤醒/开始说话”
   - 建议你在 CyanBridge logcat 打印完整 `loadData`（hex + 索引解释），多录几次：
     - 语音唤醒触发
     - 触摸唤醒/按键触发（如果存在）
     - 取消/超时
2) `aiVoicePlay(241)` 的眼镜端表现：
   - 真机观察眼镜是否播报/提示“无网络”等，从而确认 0xF1 的语义
3) `aiVoiceWake(true, flag)` 第二个 boolean（flag）是否不仅仅是 enable：
   - 官方 UI 直接把它当 `isChecked` 用
   - 若眼镜端有“灵敏度/模式”之类扩展，需要抓更多响应/逆向固件才能确定
