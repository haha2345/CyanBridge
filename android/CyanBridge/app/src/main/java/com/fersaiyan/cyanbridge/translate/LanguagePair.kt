package com.fersaiyan.cyanbridge.translate

/**
 * 翻译语言对定义。
 * @param sourceCode ASR 源语言代码
 * @param sourceName 源语言显示名
 * @param targetCode 目标语言代码
 * @param targetName 目标语言显示名
 */
data class LanguagePair(
        val sourceCode: String,
        val sourceName: String,
        val targetCode: String,
        val targetName: String,
) {
    val displayLabel: String
        get() = "$sourceName → $targetName"

    /** 交换源/目标语言 */
    fun swap() = LanguagePair(targetCode, targetName, sourceCode, sourceName)

    companion object {
        val PRESETS =
                listOf(
                        LanguagePair("zh", "中文", "en", "英语"),
                        LanguagePair("en", "英语", "zh", "中文"),
                        LanguagePair("zh", "中文", "ja", "日语"),
                        LanguagePair("ja", "日语", "zh", "中文"),
                        LanguagePair("zh", "中文", "ko", "韩语"),
                        LanguagePair("ko", "韩语", "zh", "中文"),
                        LanguagePair("zh", "中文", "fr", "法语"),
                        LanguagePair("zh", "中文", "de", "德语"),
                        LanguagePair("zh", "中文", "es", "西班牙语"),
                        LanguagePair("en", "英语", "ja", "日语"),
                )

        val DEFAULT = PRESETS[0]

        /** 获取源语言名称 by code */
        fun languageName(code: String): String =
                when (code) {
                    "zh" -> "中文"
                    "en" -> "英语"
                    "ja" -> "日语"
                    "ko" -> "韩语"
                    "fr" -> "法语"
                    "de" -> "德语"
                    "es" -> "西班牙语"
                    "ru" -> "俄语"
                    "it" -> "意大利语"
                    "pt" -> "葡萄牙语"
                    else -> code
                }
    }
}
