# CyanBridge — 盲人智能眼镜助手 🕶️

> 基于 HeyCyan 智能眼镜 SDK 的开源替代 App，专为视障人士设计

[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](android/CyanBridge)
[![Kotlin](https://img.shields.io/badge/Kotlin_2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](android/CyanBridge)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](android/CyanBridge)

---

## 项目简介

CyanBridge 是一款为 **HeyCyan 智能眼镜** 开发的开源替代 Android App。相比官方 App，CyanBridge 专注于 **无障碍体验** 和 **开源 AI 集成**，致力于帮助视障用户通过智能眼镜更好地感知世界。

### 核心特性

| 功能               | 说明                           | 状态     |
| ------------------ | ------------------------------ | -------- |
| 🔗 **眼镜连接管理** | BLE 扫描、配对、连接、电量监控 | ✅ 已实现 |
| 🎙️ **语音助手**     | 语音唤醒 → ASR → AI 对话 → TTS | ✅ 已实现 |
| 📸 **媒体管理**     | 照片/视频/录音 拍摄与下载      | ✅ 已实现 |
| 🌐 **同声传译**     | 实时语音翻译（140+ 语言）      | 🚧 开发中 |
| 👁️ **场景描述**     | AI 看图说话，描述周围环境      | ✅ MVP    |
| 👤 **面部描述**     | 描述面前人物的外貌特征         | 📋 计划中 |
| ♿ **无障碍 UI**    | 全盲/半盲双模式，TalkBack 适配 | 🚧 开发中 |

---

## 技术架构

```
CyanBridge App
├── UI Layer (Jetpack Compose + Material3)
│   ├── 首页 — 眼镜连接状态 + 快捷操作
│   ├── 助手 — 语音对话 + AI 回复
│   ├── 媒体 — 照片/视频/录音管理
│   └── 设置 — 无障碍/设备/账户
├── Domain Layer
│   ├── Voice Pipeline (ASR → LLM → TTS)
│   ├── Translation Engine
│   └── Vision Understanding
├── Data Layer
│   ├── BLE Manager (HeyCyan SDK)
│   ├── WiFi P2P (媒体传输)
│   └── API Clients (Aliyun / DashScope)
└── SDK (glasses_sdk.aar)
```

### 技术栈

| 层级     | 技术                                             |
| -------- | ------------------------------------------------ |
| **UI**   | Jetpack Compose + Material3 + Navigation Compose |
| **语音** | 阿里云 NUI ASR + 阿里云 NLS TTS                  |
| **AI**   | DashScope (Qwen) / OpenAI 兼容 API               |
| **通信** | HeyCyan BLE SDK + WiFi P2P                       |
| **架构** | MVVM + Kotlin Coroutines + Flow                  |

---

## 开发路线图

### ✅ Phase 0 — APK 逆向分析
- 反编译官方 APK，分析同声传译 / 意图分类 / 语音管线实现
- 产出 [逆向分析报告](android/APK_REVERSE_ENGINEERING_FULL.md)

### ✅ Phase 1 — Compose 架构重构
- Kotlin 2.0 + Jetpack Compose + Navigation Component
- 4 页底部导航（首页/助手/媒体/设置）
- Material3 主题 + 无障碍语义标签

### 🚧 Phase 2 — 无障碍 UI 深化
- 全盲模式（超大触控区 + 语音引导）
- 半盲模式（高对比度 + 大字体）
- TalkBack 全链路适配

### 🚧 Phase 3 — 翻译功能
- 基于 Qwen 大模型的同声传译
- 翻译历史 + 语言自动识别
- 离线缓存 + 低延迟播放

### ✅ Phase 4 — 视觉理解
- 眼镜拍照 + AI 图像识别（Qwen VL）
- 语音唤醒后发出拍照指令
- 拍摄图片于对话气泡中显示，点击放大查看
- 使用教程卡片（替换翻译按钮，所有模式可见）

### ✅ Phase 5 — 媒体库同步
- WiFi P2P 连接眼镜 → HTTP 下载 media.config
- 自动解析并下载所有 JPG/MP4/OPUS 文件
- 保存到手机相册（DCIM/CyanBridge）
- 下载进度实时展示 + 文件网格浏览

### ✅ Phase 6 — 用户系统
- 本地 Mock → 手机号/邮箱/微信登录（验证码 888888）
- 会员体系：普通⭐/高级🌟/钻石💎 + 功能权益对比表
- SharedPreferences 本地持久化

---

## 快速开始

### 环境要求

- Android Studio Ladybug+ (2024.2+)
- JDK 17+
- Android 设备（BLE 支持）
- HeyCyan 智能眼镜

### 构建运行

```bash
git clone https://github.com/FerSaiyan/Alternative-HeyCyan-App-and-SDK.git
cd Alternative-HeyCyan-App-and-SDK/android/CyanBridge
```

在 `local.properties` 中配置 API Keys：

```properties
# AI 对话（二选一）
OPENAI_API_KEY=sk-xxx
OPENAI_BASE_URL=https://api.openai.com

# 阿里云语音
ALIYUN_ASR_APPKEY=xxx
ALIYUN_ASR_TOKEN=xxx

# 阿里云视觉理解
DASHSCOPE_API_KEY=sk-xxx
```

用 Android Studio 打开项目，Build & Run。

---

## 项目结构

```
.
├── android/
│   ├── CyanBridge/            # 主 App 项目
│   │   ├── docs/              # 📖 项目文档
│   │   │   ├── PROJECT_ANALYSIS.md   # 项目全面分析（架构/模块/数据流/协议）
│   │   │   └── DOC_INDEX.md          # 文档目录索引
│   │   ├── app/src/main/java/com/fersaiyan/cyanbridge/
│   │   │   ├── ai/            # AI 模型客户端 (Qwen-VL, Qwen-Flash)
│   │   │   ├── auth/          # 用户认证 & 会员体系
│   │   │   ├── chat/          # 对话引擎 & 历史存储
│   │   │   ├── glasses/       # BLE 眼镜控制核心
│   │   │   ├── media/         # WiFi P2P 媒体同步
│   │   │   ├── translate/     # 同声传译引擎
│   │   │   ├── vision/        # 视觉指令检测
│   │   │   ├── voice/         # ASR + TTS + Token 管理
│   │   │   └── ui/            # Compose UI (screens, navigation, theme)
│   │   └── app/libs/          # HeyCyan BLE SDK (.aar)
│   ├── APK_REVERSE_ENGINEERING_FULL.md   # 官方 APK 逆向分析
│   ├── VOICE_AI_PIPELINE_FROM_OFFICIAL_APK.md  # 语音管线分析
│   ├── AI_VISION_DASHSCOPE_MVP.md        # AI 视觉开发文档
│   ├── DATA_DOWNLOAD_GUIDE.md            # 媒体下载指南
│   └── VOICE_CHAT_DEV_MANUAL.md          # 语音对话开发手册
├── ios/                       # iOS SDK (官方原版)
├── QCSDK.framework/           # iOS BLE SDK
├── 需求.md                    # 产品需求文档
└── README.md
```

---

## 相关文档

> 📖 完整文档索引请查看 [android/CyanBridge/docs/DOC_INDEX.md](android/CyanBridge/docs/DOC_INDEX.md)

### 核心文档
- **[项目全面分析](android/CyanBridge/docs/PROJECT_ANALYSIS.md)** — 架构、模块、数据流、BLE 协议详解
- [文档目录索引](android/CyanBridge/docs/DOC_INDEX.md) — 所有文档分类汇总

### 开发指南
- [语音对话开发手册](android/VOICE_CHAT_DEV_MANUAL.md) — ASR + LLM + TTS 开发指南
- [AI 视觉 DashScope MVP](android/AI_VISION_DASHSCOPE_MVP.md) — Qwen-VL 视觉理解开发文档
- [数据下载指南](android/DATA_DOWNLOAD_GUIDE.md) — BLE + WiFi P2P + HTTP 媒体下载

### 逆向分析
- [官方 APK 逆向分析报告](android/APK_REVERSE_ENGINEERING_FULL.md) — 翻译/意图分类/语音管线
- [语音 AI 管线文档](android/VOICE_AI_PIPELINE_FROM_OFFICIAL_APK.md) — ASR→LLM→TTS 完整流程
- [视觉 AI 逆向分析](android/VISION_AI_REVERSE_ENGINEERING.md) — 新版官方 APK 图像识别逆向

### SDK 文档
- [Android SDK 开发指南](android/Android_SDK_Development_Guide_CN.pdf) — 官方 BLE SDK 文档

---

## 分支说明

| 分支                    | 说明                                     |
| ----------------------- | ---------------------------------------- |
| `main`                  | 当前开发分支，包含 CyanBridge App 和改进 |
| `manufacturer-original` | 厂商原版 SDK（未修改基线）               |

## 许可

本项目中的 HeyCyan BLE SDK (`.aar` / `.framework`) 为厂商私有协议。  
CyanBridge App 代码为开源项目，欢迎贡献。

## 致谢

- [HeyCyan / Cyan Glasses](https://www.qlifesnap.com) — 智能眼镜硬件
- [ebowwa/HeyCyanGlassesSDK](https://github.com/ebowwa/HeyCyanGlassesSDK) — 原始 SDK 文档整理
