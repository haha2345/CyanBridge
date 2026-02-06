# CyanBridge AI 识图（阿里云 DashScope Vision Understanding）开发文档（含完整代码）

本文档用于指导开发者在 Android 项目中复刻 CyanBridge 的“AI 识图 MVP”功能：在图片预览页点击“识别”按钮后，将本地图片上传到阿里云 DashScope（OpenAI 兼容接口）进行视觉理解，并将返回文本展示在同一页面。

> 参考上游文档：`/Users/mysterio/0_projects/FROM_GITHUB/Alternative-HeyCyan-App-and-SDK/android/阿里云 视觉理解api.md`

---

## 1. 功能目标与交互流程

### 1.1 目标
- 仅在用户点击“识别”按钮后才调用云端 API（避免自动上传、费用与隐私风险）。
- 使用 Qwen VL 模型对图片进行描述（MVP：回答“图片中有什么”）。
- 将识别结果显示在 `activity_ai_vision_preview` 预览页面中。

### 1.2 用户流程（UI）
1. App 获取到一张图片并保存为临时文件（cache 下的 jpg）。
2. 进入 `AiVisionPreviewActivity`：显示图片预览。
3. 用户点击“识别”：
   - App 读取图片文件 -> Base64（NO_WRAP）-> `data:image/jpeg;base64,...`
   - 调用 DashScope OpenAI 兼容接口：`POST /compatible-mode/v1/chat/completions`
   - 解析响应 `choices[0].message.content`（字符串或数组）得到文本
   - 将文本展示在页面底部结果区

---

## 2. 配置与密钥管理（禁止把 key 写进 git）

DashScope API Key 不写死在代码中，而是通过 `local.properties` 注入到 `BuildConfig`。

### 2.1 添加本地配置文件（不提交）

创建/编辑：

`/Users/mysterio/0_projects/FROM_GITHUB/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/local.properties`

内容示例：

```properties
# 阿里云 DashScope 百炼 API Key
DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxxxxx

# 可选：地域 base url 覆盖（默认北京地域）
# DASHSCOPE_BASE_URL=https://dashscope.aliyuncs.com
# 其它地域示例（以官方文档为准）：
#   https://dashscope-us.aliyuncs.com/api/v1  (示例：弗吉尼亚地域 SDK base_url)
#   https://dashscope-intl.aliyuncs.com/api/v1 (示例：新加坡地域 SDK base_url)
```

> 注意：本实现使用 OpenAI 兼容接口路径 `.../compatible-mode/v1/chat/completions`，所以 `DASHSCOPE_BASE_URL` 默认只需要 host：`https://dashscope.aliyuncs.com`。

### 2.2 Gradle 注入到 BuildConfig（完整代码）

文件：

`/Users/mysterio/0_projects/FROM_GITHUB/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/build.gradle`

本项目的 `app/build.gradle` 已包含读取 `local.properties` 的工具函数，并注入了 DashScope 字段。如下为相关完整代码（文件头到 dependencies，含 DashScope 字段）：

```groovy
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

def localProps = new Properties()
def localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.withInputStream { localProps.load(it) }
}

def localPropStr = { String key, String defVal ->
    def v = (localProps.getProperty(key) ?: defVal)
    // Escape for buildConfigField string literal
    return v.replace("\\", "\\\\").replace("\"", "\\\"")
}

android {
    namespace = "com.fersaiyan.cyanbridge"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fersaiyan.cyanbridge"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.2"

        // MVP voice pipeline config (put secrets in local.properties, not in git)
        // local.properties:
        //   OPENAI_API_KEY=...
        //   OPENAI_BASE_URL=https://api.openai.com
        //   OPENAI_CHAT_MODEL=gpt-4o-mini
        //   OPENAI_TRANSCRIBE_MODEL=whisper-1
        buildConfigField "String", "OPENAI_API_KEY", "\"${localPropStr("OPENAI_API_KEY", "")}\""
        buildConfigField "String", "OPENAI_BASE_URL", "\"${localPropStr("OPENAI_BASE_URL", "https://api.openai.com")}\""
        buildConfigField "String", "OPENAI_CHAT_MODEL", "\"${localPropStr("OPENAI_CHAT_MODEL", "gpt-4o-mini")}\""
        buildConfigField "String", "OPENAI_TRANSCRIBE_MODEL", "\"${localPropStr("OPENAI_TRANSCRIBE_MODEL", "whisper-1")}\""

        // Aliyun DashScope (Vision Understanding) config (put secrets in local.properties, not in git)
        // local.properties:
        //   DASHSCOPE_API_KEY=...
        // Optional override:
        //   DASHSCOPE_BASE_URL=https://dashscope.aliyuncs.com
        buildConfigField "String", "DASHSCOPE_API_KEY", "\"${localPropStr("DASHSCOPE_API_KEY", "")}\""
        buildConfigField "String", "DASHSCOPE_BASE_URL", "\"${localPropStr("DASHSCOPE_BASE_URL", "https://dashscope.aliyuncs.com")}\""

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation files("libs/glasses_sdk_20250723_v01.aar")
    implementation(libs.androidx.core.ktx)

    implementation("com.google.android.material:material:1.12.0")
    implementation("com.github.getActivity:XXPermissions:20.0")
    implementation("org.greenrobot:eventbus:3.2.0")
    implementation("com.github.ForgetAll:LoadingDialog:v1.1.2")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("com.github.CymChad:BaseRecyclerViewAdapterHelper:3.0.4")
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation "androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"
    testImplementation 'junit:junit:4.13.2'
}
```

---

## 3. 页面 UI：activity_ai_vision_preview（完整布局代码）

文件：

`/Users/mysterio/0_projects/FROM_GITHUB/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/res/layout/activity_ai_vision_preview.xml`

说明：
- `btn_recognize`：用户点击触发识别
- `tv_result`：展示识别文本，放在 ScrollView 内支持长文本滚动

完整代码：

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg_dark">

    <LinearLayout
        android:id="@+id/top_bar"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="12dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <ImageButton
            android:id="@+id/btn_back"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="@android:color/transparent"
            android:contentDescription="@string/ai_vision_preview_back"
            android:src="@android:drawable/ic_menu_revert"
            app:tint="@color/cyan_accent" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center_vertical"
            android:layout_marginStart="12dp"
            android:text="@string/ai_vision_preview_title"
            android:textColor="@color/text_primary"
            android:textSize="16sp"
            android:textStyle="bold" />
    </LinearLayout>

    <TextView
        android:id="@+id/preview_status"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:layout_marginEnd="16dp"
        android:layout_marginTop="8dp"
        android:text="@string/ai_vision_preview_hint"
        android:textColor="@color/text_secondary"
        android:textSize="12sp"
        app:layout_constraintTop_toBottomOf="@id/top_bar"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <ImageView
        android:id="@+id/preview_image"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:layout_margin="16dp"
        android:adjustViewBounds="true"
        android:scaleType="fitCenter"
        app:layout_constraintTop_toBottomOf="@id/preview_status"
        app:layout_constraintBottom_toTopOf="@id/bottom_panel"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <LinearLayout
        android:id="@+id/bottom_panel"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingStart="16dp"
        android:paddingEnd="16dp"
        android:paddingBottom="16dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <Button
            android:id="@+id/btn_recognize"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:text="@string/ai_vision_recognize_button"
            android:textAllCaps="false" />

        <ScrollView
            android:layout_width="match_parent"
            android:layout_height="140dp"
            android:layout_marginTop="12dp">

            <TextView
                android:id="@+id/tv_result"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/ai_vision_recognize_initial"
                android:textColor="@color/text_primary"
                android:textSize="14sp" />
        </ScrollView>
    </LinearLayout>

</androidx.constraintlayout.widget.ConstraintLayout>
```

---

## 4. 字符串资源（完整新增项）

文件：

`/Users/mysterio/0_projects/FROM_GITHUB/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/res/values/strings.xml`

本功能新增的 strings 如下（直接复制到 `<resources>` 内即可）：

```xml
<string name="ai_vision_recognize_button">识别</string>
<string name="ai_vision_recognize_initial">点击“识别”开始上传图片并获取识别结果</string>
<string name="ai_vision_recognize_in_progress">识别中…</string>
<string name="ai_vision_recognize_done">识别完成</string>
<string name="ai_vision_recognize_missing_key">未配置 DASHSCOPE_API_KEY（请在 local.properties 中设置）</string>
<string name="ai_vision_recognize_failed">识别失败</string>
```

---

## 5. DashScope OpenAI 兼容接口客户端（完整代码）

文件：

`/Users/mysterio/0_projects/FROM_GITHUB/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/ai/DashScopeVisionClient.kt`

说明：
- 读取 `BuildConfig.DASHSCOPE_API_KEY` / `BuildConfig.DASHSCOPE_BASE_URL`
- 使用 OkHttp `POST /compatible-mode/v1/chat/completions`
- 将本地 JPEG 作为 base64 data url 传入
- 解析响应 `choices[0].message.content`（字符串或数组），得到最终文本

完整代码：

```kotlin
package com.fersaiyan.cyanbridge.ai

import android.util.Base64
import com.fersaiyan.cyanbridge.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class DashScopeVisionClient(
    private val apiKey: String = BuildConfig.DASHSCOPE_API_KEY,
    private val baseUrl: String = BuildConfig.DASHSCOPE_BASE_URL,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build(),
) {
    fun describeImage(
        imageFile: File,
        prompt: String = "请用中文简要描述图片中有什么，列出主要对象与场景。",
        model: String = "qwen3-vl-flash",
    ): String {
        if (apiKey.isBlank()) {
            throw IllegalStateException("Missing DASHSCOPE_API_KEY")
        }
        if (!imageFile.exists()) {
            throw IllegalArgumentException("Image file not found: ${imageFile.absolutePath}")
        }

        val bytes = imageFile.readBytes()
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

        // OpenAI-compatible endpoint for DashScope
        // Base host is configurable; default is https://dashscope.aliyuncs.com
        val url = baseUrl.trimEnd('/') + "/compatible-mode/v1/chat/completions"

        val content = JSONArray()
            .put(
                JSONObject()
                    .put("type", "image_url")
                    .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64"))
            )
            .put(
                JSONObject()
                    .put("type", "text")
                    .put("text", prompt)
            )

        val messages = JSONArray()
            .put(
                JSONObject()
                    .put("role", "user")
                    .put("content", content)
            )

        val bodyJson = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", 0.2)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody(mediaType))
            .build()

        http.newCall(request).execute().use { resp ->
            val respBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val snippet = if (respBody.length > 1200) respBody.take(1200) + "…" else respBody
                throw RuntimeException("HTTP ${resp.code}: $snippet")
            }
            return parseOpenAiCompatibleText(respBody)
        }
    }

    private fun parseOpenAiCompatibleText(body: String): String {
        val root = JSONObject(body)
        val choices = root.optJSONArray("choices") ?: return body
        if (choices.length() <= 0) return body
        val msg = choices.optJSONObject(0)?.optJSONObject("message") ?: return body
        val content = msg.opt("content") ?: return body
        return when (content) {
            is String -> content
            is JSONArray -> {
                // Some implementations may return an array of content blocks.
                val sb = StringBuilder()
                for (i in 0 until content.length()) {
                    val part = content.optJSONObject(i)
                    val text = part?.optString("text") ?: continue
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(text)
                }
                if (sb.isNotEmpty()) sb.toString() else body
            }
            else -> body
        }
    }
}
```

---

## 6. 预览页 Activity（点击识别按钮触发上传 + 展示结果，完整代码）

文件：

`/Users/mysterio/0_projects/FROM_GITHUB/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/ui/AiVisionPreviewActivity.kt`

说明：
- `btn_recognize` 点击时：
  - 防连点：如果 `recognizeJob` 仍在执行则直接 return
  - key 为空直接提示，不发请求
  - IO 线程发起请求，主线程更新 UI
- `setRecognizeUiState(...)` 控制按钮可用、status 提示

完整代码：

```kotlin
package com.fersaiyan.cyanbridge.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fersaiyan.cyanbridge.BuildConfig
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.ai.DashScopeVisionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AiVisionPreviewActivity : AppCompatActivity() {

    private var imagePath: String? = null
    private var deleteOnFinish: Boolean = true
    private var recognizeJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_vision_preview)

        imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        deleteOnFinish = intent.getBooleanExtra(EXTRA_DELETE_ON_FINISH, true)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        val imageView = findViewById<ImageView>(R.id.preview_image)
        val statusView = findViewById<TextView>(R.id.preview_status)
        val btnRecognize = findViewById<Button>(R.id.btn_recognize)
        val resultView = findViewById<TextView>(R.id.tv_result)

        val path = imagePath
        if (path.isNullOrBlank()) {
            statusView.text = getString(R.string.ai_vision_preview_missing_path)
            Toast.makeText(this, getString(R.string.ai_vision_preview_missing_path), Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(path)
        if (!file.exists()) {
            statusView.text = getString(R.string.ai_vision_preview_file_missing)
            Toast.makeText(this, getString(R.string.ai_vision_preview_file_missing), Toast.LENGTH_SHORT).show()
            return
        }

        val bmp = BitmapFactory.decodeFile(path)
        if (bmp == null) {
            statusView.text = getString(R.string.ai_vision_preview_decode_failed)
            Toast.makeText(this, getString(R.string.ai_vision_preview_decode_failed), Toast.LENGTH_SHORT).show()
            return
        }

        statusView.text = getString(R.string.ai_vision_preview_hint)
        imageView.setImageBitmap(bmp)

        btnRecognize.setOnClickListener {
            if (recognizeJob?.isActive == true) return@setOnClickListener

            if (BuildConfig.DASHSCOPE_API_KEY.isBlank()) {
                resultView.text = getString(R.string.ai_vision_recognize_missing_key)
                Toast.makeText(this, getString(R.string.ai_vision_recognize_missing_key), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val img = File(path)
            if (!img.exists()) {
                resultView.text = getString(R.string.ai_vision_preview_file_missing)
                Toast.makeText(this, getString(R.string.ai_vision_preview_file_missing), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setRecognizeUiState(
                statusView = statusView,
                button = btnRecognize,
                resultView = resultView,
                inProgress = true,
                message = getString(R.string.ai_vision_recognize_in_progress)
            )

            recognizeJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val client = DashScopeVisionClient()
                    val text = client.describeImage(imageFile = img).trim()
                    withContext(Dispatchers.Main) {
                        setRecognizeUiState(
                            statusView = statusView,
                            button = btnRecognize,
                            resultView = resultView,
                            inProgress = false,
                            message = getString(R.string.ai_vision_recognize_done)
                        )
                        resultView.text = if (text.isNotBlank()) text else getString(R.string.ai_vision_recognize_failed)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Recognize failed: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        setRecognizeUiState(
                            statusView = statusView,
                            button = btnRecognize,
                            resultView = resultView,
                            inProgress = false,
                            message = getString(R.string.ai_vision_recognize_failed)
                        )
                        resultView.text = "${getString(R.string.ai_vision_recognize_failed)}：${e.message ?: e.javaClass.simpleName}"
                    }
                }
            }
        }
    }

    override fun finish() {
        cleanupIfNeeded()
        super.finish()
    }

    override fun onDestroy() {
        recognizeJob?.cancel()
        if (isFinishing) {
            cleanupIfNeeded()
        }
        super.onDestroy()
    }

    private fun cleanupIfNeeded() {
        if (!deleteOnFinish) return
        val path = imagePath ?: return
        try {
            val file = File(path)
            if (file.exists() && file.absolutePath.contains("${File.separator}ai_vision_mvp${File.separator}")) {
                val ok = file.delete()
                Log.i(TAG, "Deleted temp image=$path ok=$ok")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete temp image: ${e.message}")
        }
    }

    private fun setRecognizeUiState(
        statusView: TextView,
        button: Button,
        resultView: TextView,
        inProgress: Boolean,
        message: String,
    ) {
        statusView.text = message
        button.isEnabled = !inProgress
        resultView.isEnabled = !inProgress
    }

    companion object {
        private const val TAG = "AiVisionPreview"
        private const val EXTRA_IMAGE_PATH = "extra_image_path"
        private const val EXTRA_DELETE_ON_FINISH = "extra_delete_on_finish"

        fun start(context: Context, imagePath: String, deleteOnFinish: Boolean = true) {
            context.startActivity(
                Intent(context, AiVisionPreviewActivity::class.java).apply {
                    putExtra(EXTRA_IMAGE_PATH, imagePath)
                    putExtra(EXTRA_DELETE_ON_FINISH, deleteOnFinish)
                }
            )
        }
    }
}
```

---

## 7. 方法与调用点说明（开发者需要知道的“怎么接起来”）

### 7.1 `DashScopeVisionClient.describeImage(...)`
- 位置：`com.fersaiyan.cyanbridge.ai.DashScopeVisionClient`
- 入参：
  - `imageFile: File`：本地图片文件（JPEG）
  - `prompt: String`：提示词（默认中文描述）
  - `model: String`：模型名（默认 `qwen3-vl-flash`）
- 返回：识别文本（`String`）
- 抛错：key 缺失、文件不存在、HTTP 非 2xx、JSON 解析异常等

### 7.2 `AiVisionPreviewActivity.start(...)`
- 位置：`com.fersaiyan.cyanbridge.ui.AiVisionPreviewActivity`
- 作用：从任意 Context 跳转到预览页并携带图片路径
- 典型调用：

```kotlin
com.fersaiyan.cyanbridge.ui.AiVisionPreviewActivity.start(
    this@MainActivity,
    outFile.absolutePath
)
```

---

## 8. 调试与测试指令（命令行）

### 8.1 构建（Gradle）
在项目目录：

```bash
cd /Users/mysterio/0_projects/FROM_GITHUB/Alternative-HeyCyan-App-and-SDK/android/CyanBridge
./gradlew :app:assembleDebug
```

如果你的环境需要指定 Java（项目说明建议 Java 17+）：

```bash
JAVA_HOME=/path/to/jdk17 ./gradlew :app:assembleDebug
```

### 8.2 安装 APK（adb）
构建产物一般在（以实际输出为准）：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 8.3 日志（logcat）
识图相关的 tag：
- `AiVisionPreview`（预览页 + 识别失败日志）

示例：

```bash
adb logcat -s AiVisionPreview
```

---

## 9. 常见问题（FAQ）

### 9.1 点击识别提示“未配置 DASHSCOPE_API_KEY”
- 说明 `BuildConfig.DASHSCOPE_API_KEY` 为空
- 检查：
  - `CyanBridge/local.properties` 是否存在
  - 是否包含 `DASHSCOPE_API_KEY=...`
  - 重新 sync/重启构建（BuildConfig 需要重新生成）

### 9.2 返回 `HTTP 401/403`
- key 无效、过期、地域不匹配或权限问题
- 换正确的 key，或检查 DashScope 控制台权限

### 9.3 失败信息里出现 “invalid content / unsupported format”
- 可能是服务端对多模态 content 的格式要求有差异
- 建议保留响应体（当前实现会在异常中附上 body snippet）用于进一步兼容

---

## 10. 安全注意事项
- 永远不要把真实 `DASHSCOPE_API_KEY` 写进代码或提交到 git。
- 如果要发布 release 版本，建议引入更安全的密钥管理方式（例如 CI 注入、加密存储、后端中转等）。

---

## 11. 端到端串联：MainActivity 获取图片 -> 进入预览页 -> 点击识别

本章节补齐“图片从哪里来”：即 CyanBridge 如何从眼镜侧拿到 JPEG 缩略图，并把图片路径传给 `AiVisionPreviewActivity`。识别部分在本文前面章节已完整给出。

> 关键原则：不要依赖 `glassesControl` 的 ACK 是否为 0；实测某些固件会回 `error=-1`，但仍会发送 notify 并且 `getPictureThumbnails` 正常出图。最终以 notify + 分片数据为准。

### 11.1 MainActivity 页面按钮（XML，完整片段）

文件：

`/Users/mysterio/0_projects/FROM_GITHUB/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/res/layout/acitivyt_main.xml`

将该按钮放到合适的区域（本项目放在 “AI ASSISTANT HIJACK” 区块下方）：

```xml
<Button
    android:id="@+id/btn_ai_vision_mvp"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="@string/ai_vision_mvp_button"
    android:backgroundTint="@color/card_bg"
    android:textColor="@color/cyan_accent"
    android:layout_marginTop="8dp"
    style="@style/Widget.MaterialComponents.Button.OutlinedButton"/>
```

### 11.2 initView 绑定点击事件（Kotlin，完整片段）

文件：

`/Users/mysterio/0_projects/FROM_GITHUB/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/MainActivity.kt`

在 `initView()` 的 `setOnClickListener(...)` 中包含 `binding.btnAiVisionMvp`，并在 `when (this)` 中处理：

```kotlin
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
            // ... 其它按钮处理 ...
        }
    }
}
```

### 11.3 确保注册设备 notify listener（Kotlin，完整片段）

`startAiVisionMvp()` 依赖设备 notify 来判断“拍照事件已发生/可开始拉取缩略图”。因此 `onCreate()` 中必须注册 `LargeDataHandler` 的 out device listener。

文件：

`/Users/mysterio/0_projects/FROM_GITHUB/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/MainActivity.kt`

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = AcitivytMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    initView()

    // Ensure we always listen for glasses reports (battery, AI, volume, etc.)
    LargeDataHandler.getInstance().addOutDeviceListener(100, deviceNotifyListener)
}
```

### 11.4 notify 解码：捕获 “拍照/AI Photo” 信号（Kotlin，完整片段）

当 `aiVisionMvpJob` 正在执行时，`MyDeviceNotifyListener` 会把 `0x01` 或 `0x02` notify 当作“图片可用信号”，用于唤醒 `startAiVisionMvp()` 中等待的 `photoSignal`，并 **return** 避免走其它旧逻辑（比如 AI hijack 的缩略图下载分支）。

文件：

`/Users/mysterio/0_projects/FROM_GITHUB/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/MainActivity.kt`

```kotlin
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
// Glasses pass quick recognition / AI Photo
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

    // ...（当不是 MVP 流程时，仍保留原有逻辑）...
}
```

### 11.5 关键方法：startAiVisionMvp（Kotlin，完整代码）

该方法完成：
- BLE 连接检查
- 发送触发命令（SDK 文档：`glassesControl([0x02,0x01,0x06, thumbnailSize, thumbnailSize, 0x02])`）
- 等待短时间 notify 作为“可开始拉取”的信号
- 调用 `getPictureThumbnails` 接收 JPEG 分片并拼接
- 校验可解码后写入 `cacheDir/ai_vision_mvp/ai_<timestamp>.jpg`
- 启动 `AiVisionPreviewActivity` 并传入图片路径

文件：

`/Users/mysterio/0_projects/FROM_GITHUB/Alternative-HeyCyan-App-and-SDK/android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/MainActivity.kt`

完整代码：

```kotlin
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
```

### 11.6 端到端调试指令（logcat）

抓取本链路最关键的日志 tag：

```bash
adb logcat -s AiVisionMvp DeviceNotify AiVisionPreview
```

