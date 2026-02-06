package com.fersaiyan.cyanbridge.ai

import com.fersaiyan.cyanbridge.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * DashScope Qwen-Flash 客户端：
 * - 使用 OpenAI-compatible 接口
 * - 返回简短回答
 */
class QwenChatClient(
    private val apiKey: String = BuildConfig.DASHSCOPE_API_KEY,
    private val baseUrl: String = BuildConfig.DASHSCOPE_BASE_URL,
    // Keep model short-answer friendly.
    private val model: String = "qwen-flash",
    private val maxTokens: Int = 128,
    private val temperature: Double = 0.7,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build(),
) {
    /**
     * 向 Qwen-Flash 发送消息并返回回复文本。
     */
    fun chat(userText: String): String {
        if (apiKey.isBlank()) {
            throw IllegalStateException("Missing DASHSCOPE_API_KEY")
        }

        // DashScope OpenAI-compatible endpoint for Qwen.
        val url = baseUrl.trimEnd('/') + "/compatible-mode/v1/chat/completions"

        val messages = JSONArray()
            .put(
                JSONObject()
                    .put("role", "system")
                    .put("content", "请用简短、直接的方式回答。")
            )
            .put(
                JSONObject()
                    .put("role", "user")
                    .put("content", userText)
            )

        val payload = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("max_tokens", maxTokens)
            .put("temperature", temperature)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(mediaType))
            .build()

        http.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val snippet = if (body.length > 1200) body.take(1200) + "…" else body
                throw RuntimeException("HTTP ${resp.code}: $snippet")
            }
            return parseOpenAiCompatibleText(body)
        }
    }

    /**
     * 解析 OpenAI-compatible 响应内容。
     */
    private fun parseOpenAiCompatibleText(body: String): String {
        val root = JSONObject(body)
        val choices = root.optJSONArray("choices") ?: return ""
        if (choices.length() == 0) return ""
        val msg = choices.optJSONObject(0)?.optJSONObject("message") ?: return ""
        // Some responses may return an array of text parts.
        val content = msg.opt("content") ?: return ""
        return when (content) {
            is String -> content
            is JSONArray -> {
                val sb = StringBuilder()
                for (i in 0 until content.length()) {
                    val part = content.optJSONObject(i)
                    val text = part?.optString("text") ?: continue
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(text)
                }
                sb.toString()
            }
            else -> ""
        }
    }
}
