package com.rk.terminal.ui.screens.terminal

import android.app.Application
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.rk.libcommons.application
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Local unit tests for [TerminalUtils].
 *
 * [TerminalUtils] is an `object` whose initialisers read
 * `com.rk.settings.Settings`, which reaches the global `application` field and
 * throws if it is null. The field is normally set in App.onCreate, which is
 * deliberately NOT used here (it starts ANRWatchDog and UpdateManager), so the
 * tests install a plain Application first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TerminalUtilsTest {

    private var originalDarkText = false
    private var originalHasCustomBackground = false

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        // Touch TerminalUtils only after `application` is populated.
        originalDarkText = TerminalUtils.darkText.value
        originalHasCustomBackground = TerminalUtils.hasCustomBackground.value
    }

    @After
    fun tearDown() {
        TerminalUtils.darkText.value = originalDarkText
        TerminalUtils.hasCustomBackground.value = originalHasCustomBackground
    }

    // --- getNameOfWorkingMode ----------------------------------------------

    @Test
    fun `working mode 0 is alpine`() {
        assertEquals("alpine", TerminalUtils.getNameOfWorkingMode(0))
    }

    @Test
    fun `working mode 1 is android`() {
        assertEquals("android", TerminalUtils.getNameOfWorkingMode(1))
    }

    @Test
    fun `working mode 2 is wolfi`() {
        assertEquals("wolfi", TerminalUtils.getNameOfWorkingMode(2))
    }

    @Test
    fun `null working mode is unknown`() {
        assertEquals("unknown", TerminalUtils.getNameOfWorkingMode(null))
    }

    @Test
    fun `out of range working mode is unknown`() {
        assertEquals("unknown", TerminalUtils.getNameOfWorkingMode(-1))
        assertEquals("unknown", TerminalUtils.getNameOfWorkingMode(3))
        assertEquals("unknown", TerminalUtils.getNameOfWorkingMode(99))
    }

    // --- getViewColor ------------------------------------------------------

    @Test
    fun `view colour is black when text is dark`() {
        TerminalUtils.darkText.value = true
        assertEquals(Color.BLACK, TerminalUtils.getViewColor())
    }

    @Test
    fun `view colour is white when text is light`() {
        TerminalUtils.darkText.value = false
        assertEquals(Color.WHITE, TerminalUtils.getViewColor())
    }

    // --- getBackgroundColor ------------------------------------------------

    @Test
    fun `background is transparent when a custom background image is set`() {
        TerminalUtils.hasCustomBackground.value = true
        TerminalUtils.darkText.value = true
        assertEquals(Color.TRANSPARENT, TerminalUtils.getBackgroundColor())
    }

    @Test
    fun `background is dimmed white when text is dark`() {
        TerminalUtils.hasCustomBackground.value = false
        TerminalUtils.darkText.value = true
        val color = TerminalUtils.getBackgroundColor()
        assertEquals(DIMMED_ALPHA, Color.alpha(color))
        assertEquals(Color.WHITE and RGB_MASK, color and RGB_MASK)
    }

    @Test
    fun `background is dimmed black when text is light`() {
        TerminalUtils.hasCustomBackground.value = false
        TerminalUtils.darkText.value = false
        val color = TerminalUtils.getBackgroundColor()
        assertEquals(DIMMED_ALPHA, Color.alpha(color))
        assertEquals(Color.BLACK and RGB_MASK, color and RGB_MASK)
    }

    @Test
    fun `background colour is not fully opaque`() {
        TerminalUtils.hasCustomBackground.value = false
        TerminalUtils.darkText.value = true
        assertNotEquals(255, Color.alpha(TerminalUtils.getBackgroundColor()))
    }

    // --- getComposeColor ---------------------------------------------------

    @Test
    fun `compose colour tracks dark text flag`() {
        TerminalUtils.darkText.value = true
        assertEquals(androidx.compose.ui.graphics.Color.Black, TerminalUtils.getComposeColor())
        TerminalUtils.darkText.value = false
        assertEquals(androidx.compose.ui.graphics.Color.White, TerminalUtils.getComposeColor())
    }

    // --- init scripts written by MkSession ---------------------------------

    @Test
    fun `stat sample is non empty proc stat output`() {
        assertTrue(TerminalUtils.stat.contains("cpu"))
        assertTrue(TerminalUtils.stat.contains("btime"))
        assertTrue(TerminalUtils.stat.contains("processes"))
    }

    @Test
    fun `vmstat sample is non empty vm stat output`() {
        assertTrue(TerminalUtils.vmstat.contains("nr_free_pages"))
        assertTrue(TerminalUtils.vmstat.contains("pgpgin"))
    }

    private companion object {
        /** (255 * 0.3f).toInt() — the alpha TerminalUtils applies to the background. */
        const val DIMMED_ALPHA = 76
        const val RGB_MASK = 0x00FFFFFF
    }
}
