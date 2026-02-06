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

