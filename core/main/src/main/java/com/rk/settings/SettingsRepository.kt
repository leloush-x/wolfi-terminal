package com.rk.settings

import android.content.Context
import com.rk.libcommons.application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Typed settings store over the existing SharedPreferences ("Settings").
 *
 * Reuses the consolidated SharedPreferences the codebase already uses (via
 * [Preference]); adds read-once init, async (apply) writes, and StateFlow for
 * live-observed keys. New keys only; old keys are kept for one release.
 */
object SettingsRepository {

    private const val PREFS = "Settings"

    private var initialized = false

    // --- Font size (Float sp, clamped 6..40) ---
    private const val KEY_FONT_SIZE_SP = "terminal_font_size_sp"
    private const val KEY_FONT_SIZE_MIGRATED = "terminal_font_size_sp_migrated"
    private val _terminalFontSizeSp = MutableStateFlow(13f)
    val terminalFontSizeSp: StateFlow<Float> = _terminalFontSizeSp.asStateFlow()

    // --- Voice input (new keys only) ---
    private const val KEY_VOICE_ENABLED = "voice_input_enabled"
    private const val KEY_KEYBOARD_INPUT_MODE = "keyboard_input_mode"
    private const val KEY_VOICE_PREVIEW = "voice_preview"
    private const val KEY_VOICE_LANGUAGE = "voice_language"
    private const val KEY_VOICE_TRAILING_SPACE = "voice_trailing_space"
    private const val KEY_VOICE_MIC_BUTTON = "voice_mic_button_visible"

    private val _voiceEnabled = MutableStateFlow(false)
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled.asStateFlow()

    private val _keyboardInputMode = MutableStateFlow(0)
    val keyboardInputMode: StateFlow<Int> = _keyboardInputMode.asStateFlow()

    private val _voicePreview = MutableStateFlow(true)
    val voicePreview: StateFlow<Boolean> = _voicePreview.asStateFlow()

    private val _voiceLanguage = MutableStateFlow("")
    val voiceLanguage: StateFlow<String> = _voiceLanguage.asStateFlow()

    private val _voiceTrailingSpace = MutableStateFlow(true)
    val voiceTrailingSpace: StateFlow<Boolean> = _voiceTrailingSpace.asStateFlow()

    private val _voiceMicButtonVisible = MutableStateFlow(true)
    val voiceMicButtonVisible: StateFlow<Boolean> = _voiceMicButtonVisible.asStateFlow()

    fun init(context: Context? = null) {
        if (initialized) return
        val prefs = (context ?: application)?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?: return
        // read-once
        migrateFont(prefs)
        _terminalFontSizeSp.value = prefs.getFloat(KEY_FONT_SIZE_SP, 13f).coerceIn(6f, 40f)
        _voiceEnabled.value = prefs.getBoolean(KEY_VOICE_ENABLED, false)
        _keyboardInputMode.value = prefs.getInt(KEY_KEYBOARD_INPUT_MODE, 0)
        _voicePreview.value = prefs.getBoolean(KEY_VOICE_PREVIEW, true)
        _voiceLanguage.value = prefs.getString(KEY_VOICE_LANGUAGE, "") ?: ""
        _voiceTrailingSpace.value = prefs.getBoolean(KEY_VOICE_TRAILING_SPACE, true)
        _voiceMicButtonVisible.value = prefs.getBoolean(KEY_VOICE_MIC_BUTTON, true)
        initialized = true
    }

    private fun prefs(): android.content.SharedPreferences? =
        (application)?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun migrateFont(prefs: android.content.SharedPreferences) {
        if (prefs.getBoolean(KEY_FONT_SIZE_MIGRATED, false)) return
        if (!prefs.contains(KEY_FONT_SIZE_SP)) {
            val old = runCatching { prefs.getInt("terminal_font_size", 13) }.getOrDefault(13)
            prefs.edit().putFloat(KEY_FONT_SIZE_SP, old.toFloat().coerceIn(6f, 40f)).apply()
        }
        prefs.edit().putBoolean(KEY_FONT_SIZE_MIGRATED, true).apply()
    }

    fun migrate() = init()

    // --- Font size ---
    fun getTerminalFontSizeSp(): Float = _terminalFontSizeSp.value

    fun setTerminalFontSizeSp(value: Float) {
        val clamped = value.coerceIn(6f, 40f)
        _terminalFontSizeSp.value = clamped
        runCatching { prefs()?.edit()?.putFloat(KEY_FONT_SIZE_SP, clamped)?.apply() }
    }

    // --- Voice ---
    fun isVoiceEnabled(): Boolean = _voiceEnabled.value
    fun setVoiceEnabled(value: Boolean) {
        _voiceEnabled.value = value
        runCatching { prefs()?.edit()?.putBoolean(KEY_VOICE_ENABLED, value)?.apply() }
    }

    fun getKeyboardInputMode(): Int = _keyboardInputMode.value
    fun setKeyboardInputMode(value: Int) {
        _keyboardInputMode.value = value
        runCatching { prefs()?.edit()?.putInt(KEY_KEYBOARD_INPUT_MODE, value)?.apply() }
    }

    fun isVoicePreview(): Boolean = _voicePreview.value
    fun setVoicePreview(value: Boolean) {
        _voicePreview.value = value
        runCatching { prefs()?.edit()?.putBoolean(KEY_VOICE_PREVIEW, value)?.apply() }
    }

    fun getVoiceLanguage(): String = _voiceLanguage.value
    fun setVoiceLanguage(value: String) {
        _voiceLanguage.value = value
        runCatching { prefs()?.edit()?.putString(KEY_VOICE_LANGUAGE, value)?.apply() }
    }

    fun isVoiceTrailingSpace(): Boolean = _voiceTrailingSpace.value
    fun setVoiceTrailingSpace(value: Boolean) {
        _voiceTrailingSpace.value = value
        runCatching { prefs()?.edit()?.putBoolean(KEY_VOICE_TRAILING_SPACE, value)?.apply() }
    }

    fun isVoiceMicButtonVisible(): Boolean = _voiceMicButtonVisible.value
    fun setVoiceMicButtonVisible(value: Boolean) {
        _voiceMicButtonVisible.value = value
        runCatching { prefs()?.edit()?.putBoolean(KEY_VOICE_MIC_BUTTON, value)?.apply() }
    }

    /** Reset in-memory state for tests. */
    fun resetForTests() {
        initialized = false
        _terminalFontSizeSp.value = 13f
        _voiceEnabled.value = false
        _keyboardInputMode.value = 0
        _voicePreview.value = true
        _voiceLanguage.value = ""
        _voiceTrailingSpace.value = true
        _voiceMicButtonVisible.value = true
    }
}
