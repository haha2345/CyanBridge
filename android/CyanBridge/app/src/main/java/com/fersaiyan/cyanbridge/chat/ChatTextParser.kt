package com.fersaiyan.cyanbridge.chat

import org.json.JSONObject

/**
 * ASR / 文本归一化工具：
 * - 识别 ASR JSON
 * - 提取 payload.result 或 result
 * - 失败则返回原文
 */
object ChatTextParser {
    /**
     * 将输入字符串归一化为对话文本。
     */
    fun normalize(rawText: String): String? {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val obj = JSONObject(trimmed)
                // ASR JSON: prefer payload.result, fallback to top-level result.
                val payload = obj.optJSONObject("payload")
                val payloadResult = payload?.optString("result")
                if (!payloadResult.isNullOrBlank()) return payloadResult.trim()

                val result = obj.optString("result")
                if (!result.isNullOrBlank()) return result.trim()
            } catch (_: Throwable) {
            }
        }
        return trimmed
    }
}
