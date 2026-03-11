# CyanBridge 文档目录索引

> **最后更新**: 2026-03-11

本文件整理了项目中所有散落的 Markdown / PDF 文档，按用途分类，方便快速查找。

---

## 📐 项目架构与分析

| 文档 | 路径 | 说明 |
|------|------|------|
| **🔑 项目全面分析** | [`docs/PROJECT_ANALYSIS.md`](./PROJECT_ANALYSIS.md) | **核心文档**：完整的项目架构、模块详解、数据流、BLE 协议、API 配置、权限清单等 |
| 项目 README | [`CyanBridge/README.md`](../README.md) | 项目简介、Tasker 集成说明 |
| 仓库总 README | [`../../README.md`](../../README.md) | 项目概述、技术栈、开发路线图、快速开始指南 |
| 需求文档 | [`../../需求.md`](../../需求.md) | 产品需求原始文档，包含功能列表和会员体系设计 |

---

## 🔧 开发指南

| 文档 | 路径 | 说明 |
|------|------|------|
| 语音对话开发手册 | [`../VOICE_CHAT_DEV_MANUAL.md`](../VOICE_CHAT_DEV_MANUAL.md) | ASR + 大模型 + TTS 实现方式、调用链路和调试方法 |
| AI 视觉 DashScope MVP | [`../AI_VISION_DASHSCOPE_MVP.md`](../AI_VISION_DASHSCOPE_MVP.md) | Qwen-VL 视觉理解完整开发文档 (含端到端代码) |
| 数据下载指南 | [`../DATA_DOWNLOAD_GUIDE.md`](../DATA_DOWNLOAD_GUIDE.md) | BLE + WiFi P2P + HTTP 媒体下载流程和迁移清单 |
| Android Data Transfer Notes | [`../AGENTS.md`](../AGENTS.md) | 媒体传输协议参考、P2P 注意事项、OTA 调研、Logcat 标签 |
| WiFi 传输架构 | [`../../WIFI_TRANSFER_ARCHITECTURE.md`](../../WIFI_TRANSFER_ARCHITECTURE.md) | iOS 端 WiFi 传输系统技术深度分析 (QCSDK + HTTP) |

---

## 🔍 逆向工程分析

| 文档 | 路径 | 说明 |
|------|------|------|
| 官方 APK 全面逆向 | [`../APK_REVERSE_ENGINEERING_FULL.md`](../APK_REVERSE_ENGINEERING_FULL.md) | JADX 反编译旧版官方 APK 的翻译/意图分类/语音管线分析 |
| 视觉 AI 逆向分析 | [`../VISION_AI_REVERSE_ENGINEERING.md`](../VISION_AI_REVERSE_ENGINEERING.md) | 新版 Heycyan.apk 图像识别深度逆向 (含旧版对比) |
| 语音 AI 管线分析 | [`../VOICE_AI_PIPELINE_FROM_OFFICIAL_APK.md`](../VOICE_AI_PIPELINE_FROM_OFFICIAL_APK.md) | 官方 APK 语音唤醒/ASR/LLM/TTS 逻辑与指令整理 |
| 语音深度逆向 (JADX) | [`../VOICE_DEEPDIVE_JADX_OFFICIAL_APK.md`](../VOICE_DEEPDIVE_JADX_OFFICIAL_APK.md) | 官方 APK JADX 深挖版：语音唤醒机制确认 (眼镜端固件做) |

---

## 📚 第三方 API 文档

| 文档 | 路径 | 说明 |
|------|------|------|
| 阿里云 Token 获取 | [`../自动获取token.md`](../自动获取token.md) | SDK 自动获取和刷新 NLS Token 的方法 |
| 阿里云视觉理解 API | [`../阿里云 视觉理解api.md`](../阿里云%20视觉理解api.md) | Qwen-VL 视觉理解 API 使用指南 |
| 阿里云大模型对话 API | [`../阿里云大模型对话api使用方法.md`](../阿里云大模型对话api使用方法.md) | DashScope 大模型对话 API 调用方法 |
| 阿里云语音合成 API | [`../阿里云语音合成功能api文档.md`](../阿里云语音合成功能api文档.md) | NLS TTS 语音合成 SDK 文档 |
| 阿里云语音识别 API | [`../阿里云语音识别接口使用手册.md`](../阿里云语音识别接口使用手册.md) | NLS ASR 一句话识别 SDK 文档 |

---

## 📄 SDK 与工具文档

| 文档 | 路径 | 说明 |
|------|------|------|
| Android SDK 开发指南 | [`../Android_SDK_Development_Guide_CN.pdf`](../Android_SDK_Development_Guide_CN.pdf) | 官方 BLE SDK 中文文档 (PDF) |

---

## 🗂 文档分布建议

当前文档散布在多个目录中，下面是推荐的阅读顺序：

### 🆕 新手入门
1. [`../../README.md`](../../README.md) — 了解项目全貌
2. [`docs/PROJECT_ANALYSIS.md`](./PROJECT_ANALYSIS.md) — 深入了解代码架构
3. [`../../需求.md`](../../需求.md) — 理解产品需求

### 🔨 功能开发
4. [`../VOICE_CHAT_DEV_MANUAL.md`](../VOICE_CHAT_DEV_MANUAL.md) — 语音对话开发
5. [`../AI_VISION_DASHSCOPE_MVP.md`](../AI_VISION_DASHSCOPE_MVP.md) — AI 视觉开发
6. [`../DATA_DOWNLOAD_GUIDE.md`](../DATA_DOWNLOAD_GUIDE.md) — 媒体下载开发

### 🔬 协议研究
7. [`../AGENTS.md`](../AGENTS.md) — 传输协议参考
8. 逆向分析文档 (4篇) — 官方 APK 实现参考

### 📖 API 查阅
9. 阿里云 API 文档 (5篇) — 按需查阅
