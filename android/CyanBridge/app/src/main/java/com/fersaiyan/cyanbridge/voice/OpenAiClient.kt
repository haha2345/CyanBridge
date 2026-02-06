package com.fersaiyan.cyanbridge.voice

import com.fersaiyan.cyanbridge.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class OpenAiClient(
    private val apiKey: String = BuildConfig.OPENAI_API_KEY,
    private val baseUrl: String = BuildConfig.OPENAI_BASE_URL.trimEnd('/'),
    private val chatModel: String = BuildConfig.OPENAI_CHAT_MODEL,
    private val transcribeModel: String = BuildConfig.OPENAI_TRANSCRIBE_MODEL,
) {
    private val client =
        OkHttpClient.Builder()
            .callTimeout(60, TimeUnit.SECONDS)
            .build()

    suspend fun transcribeWav(wavFile: File): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "BuildConfig.OPENAI_API_KEY is empty" }

        val body =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", transcribeModel)
                .addFormDataPart(
                    "file",
                    wavFile.name,
                    wavFile.asRequestBody("audio/wav".toMediaType()),
                )
                .build()

        val req =
            Request.Builder()
                .url("$baseUrl/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("OpenAI transcribe failed: HTTP ${resp.code} $raw")
            JSONObject(raw).optString("text").orEmpty()
        }
    }

    suspend fun chat(userText: String): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "BuildConfig.OPENAI_API_KEY is empty" }

        val messages =
            JSONArray()
                .put(JSONObject().put("role", "system").put("content", "You are a helpful assistant."))
                .put(JSONObject().put("role", "user").put("content", userText))

        val payload =
            JSONObject()
                .put("model", chatModel)
                .put("messages", messages)

        val req =
            Request.Builder()
                .url("$baseUrl/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("OpenAI chat failed: HTTP ${resp.code} $raw")

            val json = JSONObject(raw)
            val choices = json.optJSONArray("choices") ?: return@use ""
            if (choices.length() == 0) return@use ""
            choices.getJSONObject(0).getJSONObject("message").optString("content").orEmpty()
        }
    }
}

