# CyanBridge

CyanBridge 是一款面向盲人/视障用户的 HeyCyan 智能眼镜辅助 Android App。

## 📖 项目文档

- **[项目全面分析](docs/PROJECT_ANALYSIS.md)** — 架构、模块、数据流、BLE 协议、API 配置等
- **[文档目录索引](docs/DOC_INDEX.md)** — 所有文档的分类索引和推荐阅读顺序

## Key Notes
- **Android-only assistant support**: Gemini/ChatGPT workflows are supported on Android only.
- **Image queries require automation**: Tasker (paid) + AutoInput (paid plugin) + the CyanBridge Tasker profile enabled.
  - **TaskerNet**: [Tasker AI Profile](https://taskernet.com/shares/?user=AS35m8m%2BZfcOI%2FAn4TYXwIRGXRuXzE9zXexYgafojsO%2FQSXgVbu8nOiYo%2BLhLj1izKWhtzdxI6eOvMI%3D&id=Profile%3ATasker+AI)
  - **Repo profile (.xml)**: [tasker/Tasker_AI.xml](tasker/Tasker_AI.xml)

## Tasker Integration (Intents)
- **Broadcast action from app**: `com.fersaiyan.cyanbridge.AI_EVENT`
- **Command intent to app**: `com.fersaiyan.cyanbridge.ACTION_TASKER_COMMAND`
