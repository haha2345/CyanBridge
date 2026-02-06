package com.fersaiyan.cyanbridge.chat

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 对话历史存储：
 * - 内存中使用 StateFlow 保存列表
 * - 磁盘使用 JSON 文件持久化
 */
object ChatStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean(false)
    private val mutex = Mutex()

    private lateinit var file: File
    // In-memory snapshot for UI; persisted to JSON on each update.
    private val state = MutableStateFlow<List<ChatMessageEntity>>(emptyList())

    /**
     * 初始化存储（只需调用一次）。
     */
    fun init(context: Context) {
        if (initialized.getAndSet(true)) return
        // Persisted file: /data/data/<pkg>/files/chat_history.json
        file = File(context.filesDir, "chat_history.json")
        scope.launch {
            loadFromDisk()
        }
    }

    /**
     * 观察当前会话列表（UI 使用）。
     */
    fun observe(): StateFlow<List<ChatMessageEntity>> = state

    /**
     * 插入新消息。
     */
    suspend fun insert(message: ChatMessageEntity) {
        updateList(state.value + message)
    }

    /**
     * 更新指定消息（用于补齐 audioPath 等字段）。
     */
    suspend fun update(message: ChatMessageEntity) {
        val list = state.value.map { if (it.id == message.id) message else it }
        updateList(list)
    }

    /**
     * 清空历史记录。
     */
    suspend fun clearAll() {
        updateList(emptyList())
    }

    /**
     * 写入内存并持久化到磁盘。
     */
    private suspend fun updateList(list: List<ChatMessageEntity>) {
        mutex.withLock {
            state.value = list
            saveToDisk(list)
        }
    }

    /**
     * 从磁盘读取 JSON 恢复对话历史。
     */
    private fun loadFromDisk() {
        if (!file.exists()) return
        try {
            val text = file.readText(Charset.forName("UTF-8"))
            val json = JSONArray(text)
            val list = ArrayList<ChatMessageEntity>(json.length())
            for (i in 0 until json.length()) {
                val obj = json.optJSONObject(i) ?: continue
                list.add(obj.toMessage())
            }
            state.value = list
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load chat history", t)
        }
    }

    /**
     * 将列表保存为 JSON 文件。
     */
    private fun saveToDisk(list: List<ChatMessageEntity>) {
        try {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            file.writeText(arr.toString(), Charsets.UTF_8)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to save chat history", t)
        }
    }

    /**
     * 单条消息 -> JSON。
     */
    private fun ChatMessageEntity.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("role", role)
            .put("content", content)
            .put("created_at", createdAt)
            .put("status", status)
            .put("source", source)
            .put("audio_path", audioPath ?: JSONObject.NULL)
            .put("audio_duration_ms", audioDurationMs ?: JSONObject.NULL)
            .put("meta_json", metaJson ?: JSONObject.NULL)
    }

    /**
     * JSON -> 单条消息。
     */
    private fun JSONObject.toMessage(): ChatMessageEntity {
        val audioPathValue = if (isNull("audio_path")) null else optString("audio_path")
        val metaValue = if (isNull("meta_json")) null else optString("meta_json")
        val audioDurationValue = if (isNull("audio_duration_ms")) null else optLong("audio_duration_ms")
        return ChatMessageEntity(
            id = optString("id"),
            role = optString("role"),
            content = optString("content"),
            createdAt = optLong("created_at"),
            status = optString("status"),
            source = optString("source"),
            audioPath = audioPathValue?.ifBlank { null },
            audioDurationMs = audioDurationValue,
            metaJson = metaValue?.ifBlank { null },
        )
    }

    private const val TAG = "ChatStore"
}
