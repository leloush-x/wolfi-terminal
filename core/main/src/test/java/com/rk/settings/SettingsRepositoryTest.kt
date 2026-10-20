package com.rk.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.rk.libcommons.application
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsRepositoryTest {

    private fun clearPrefs() {
        application?.getSharedPreferences("Settings", android.content.Context.MODE_PRIVATE)?.edit()?.clear()?.commit()
    }

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        clearPrefs()
        SettingsRepository.resetForTests()
        SettingsRepository.init(application)
    }

    @After
    fun tearDown() {
        clearPrefs()
        SettingsRepository.resetForTests()
    }

    @Test
    fun `defaults for missing keys`() {
        assertEquals(13f, SettingsRepository.getTerminalFontSizeSp(), 0.001f)
        assertFalse(SettingsRepository.isVoiceEnabled())
        assertEquals(0, SettingsRepository.getKeyboardInputMode())
        assertTrue(SettingsRepository.isVoicePreview())
        assertEquals("", SettingsRepository.getVoiceLanguage())
        assertTrue(SettingsRepository.isVoiceTrailingSpace())
        assertTrue(SettingsRepository.isVoiceMicButtonVisible())
    }

    @Test
    fun `round trip persists`() {
        SettingsRepository.setTerminalFontSizeSp(24f)
        assertEquals(24f, SettingsRepository.getTerminalFontSizeSp(), 0.001f)
        // re-read from prefs
        SettingsRepository.resetForTests()
        SettingsRepository.init(application)
        assertEquals(24f, SettingsRepository.getTerminalFontSizeSp(), 0.001f)
    }

    @Test
    fun `clamping on set`() {
        SettingsRepository.setTerminalFontSizeSp(100f)
        assertEquals(40f, SettingsRepository.getTerminalFontSizeSp(), 0.001f)
        SettingsRepository.setTerminalFontSizeSp(1f)
        assertEquals(6f, SettingsRepository.getTerminalFontSizeSp(), 0.001f)
    }

    @Test
    fun `migration from old int key`() {
        clearPrefs()
        SettingsRepository.resetForTests()
        application?.getSharedPreferences("Settings", android.content.Context.MODE_PRIVATE)?.edit()?.putInt("terminal_font_size", 18)?.commit()
        SettingsRepository.init(application)
        assertEquals(18f, SettingsRepository.getTerminalFontSizeSp(), 0.001f)
    }

    @Test
    fun `feature off default for upgrading users`() {
        assertFalse(SettingsRepository.isVoiceEnabled())
    }
}
