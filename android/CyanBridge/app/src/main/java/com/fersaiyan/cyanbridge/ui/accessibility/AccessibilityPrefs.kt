package com.fersaiyan.cyanbridge.ui.accessibility

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 无障碍偏好管理（全局单例）：
 * - blindMode：全盲模式（超大触控区 + 高对比度 + 语音引导）
 * - highContrast：高对比度主题
 * - largeText：大字体模式
 * - voiceGuide：语音引导（页面切换时 TTS 朗读）
 */
object AccessibilityPrefs {
    private const val PREFS_NAME = "accessibility_prefs"
    private const val KEY_BLIND_MODE = "blind_mode"
    private const val KEY_HIGH_CONTRAST = "high_contrast"
    private const val KEY_LARGE_TEXT = "large_text"
    private const val KEY_VOICE_GUIDE = "voice_guide"

    private lateinit var prefs: SharedPreferences

    private val _blindMode = MutableStateFlow(false)
    val blindMode: StateFlow<Boolean> = _blindMode.asStateFlow()

    private val _highContrast = MutableStateFlow(false)
    val highContrast: StateFlow<Boolean> = _highContrast.asStateFlow()

    private val _largeText = MutableStateFlow(false)
    val largeText: StateFlow<Boolean> = _largeText.asStateFlow()

    private val _voiceGuide = MutableStateFlow(false)
    val voiceGuide: StateFlow<Boolean> = _voiceGuide.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _blindMode.value = prefs.getBoolean(KEY_BLIND_MODE, false)
        _highContrast.value = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
        _largeText.value = prefs.getBoolean(KEY_LARGE_TEXT, false)
        _voiceGuide.value = prefs.getBoolean(KEY_VOICE_GUIDE, false)

        // Blind mode implies high contrast + voice guide
        if (_blindMode.value) {
            _highContrast.value = true
            _voiceGuide.value = true
        }
    }

    fun setBlindMode(enabled: Boolean) {
        _blindMode.value = enabled
        prefs.edit().putBoolean(KEY_BLIND_MODE, enabled).apply()
        if (enabled) {
            // Blind mode auto-enables high contrast + voice guide
            setHighContrast(true)
            setVoiceGuide(true)
        }
    }

    fun setHighContrast(enabled: Boolean) {
        _highContrast.value = enabled
        prefs.edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply()
    }

    fun setLargeText(enabled: Boolean) {
        _largeText.value = enabled
        prefs.edit().putBoolean(KEY_LARGE_TEXT, enabled).apply()
    }

    fun setVoiceGuide(enabled: Boolean) {
        _voiceGuide.value = enabled
        prefs.edit().putBoolean(KEY_VOICE_GUIDE, enabled).apply()
    }
}
