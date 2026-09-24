package com.rk.terminal.ui.screens.terminal

import android.app.Application
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Local unit tests for [ShortcutBinding].
 *
 * The serialized form ("CTRL|SHIFT|54") is persisted in SharedPreferences, so
 * deserialize/serialize is the contract that must not break across app updates.
 *
 * Robolectric is required because [ShortcutBinding.matches],
 * [ShortcutBinding.toDisplayString] and the RESERVED/MODIFIER key sets touch
 * android.view.KeyEvent, which is a stub in the local unit-test classpath.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ShortcutBindingTest {

    // --- isEmpty -----------------------------------------------------------

    @Test
    fun `default binding is empty`() {
        assertTrue(ShortcutBinding().isEmpty)
    }

    @Test
    fun `binding with keyCode is not empty`() {
        assertFalse(ShortcutBinding(keyCode = KeyEvent.KEYCODE_V).isEmpty)
    }

    @Test
    fun `modifiers without keyCode is still empty`() {
        assertTrue(ShortcutBinding(ctrl = true, shift = true).isEmpty)
    }

    // --- serialize ---------------------------------------------------------

    @Test
    fun `serialize emits modifiers then keyCode`() {
        val binding = ShortcutBinding(ctrl = true, shift = true, keyCode = KeyEvent.KEYCODE_V)
        assertEquals("CTRL|SHIFT|${KeyEvent.KEYCODE_V}", binding.serialize())
    }

    @Test
    fun `serialize preserves alt`() {
        val binding = ShortcutBinding(alt = true, keyCode = KeyEvent.KEYCODE_C)
        assertEquals("ALT|${KeyEvent.KEYCODE_C}", binding.serialize())
    }

    @Test
    fun `serialize with no modifiers is just the keyCode`() {
        val binding = ShortcutBinding(keyCode = KeyEvent.KEYCODE_F)
        assertEquals(KeyEvent.KEYCODE_F.toString(), binding.serialize())
    }

    @Test
    fun `serialize of empty binding is empty string`() {
        assertEquals("", ShortcutBinding().serialize())
    }

    // --- deserialize -------------------------------------------------------

    @Test
    fun `deserialize blank yields empty binding`() {
        assertEquals(ShortcutBinding(), ShortcutBinding.deserialize(""))
        assertEquals(ShortcutBinding(), ShortcutBinding.deserialize("   "))
    }

    @Test
    fun `deserialize parses all three modifiers`() {
        val binding = ShortcutBinding.deserialize("CTRL|SHIFT|ALT|${KeyEvent.KEYCODE_V}")
        assertTrue(binding.ctrl)
        assertTrue(binding.shift)
        assertTrue(binding.alt)
        assertEquals(KeyEvent.KEYCODE_V, binding.keyCode)
        assertFalse(binding.isEmpty)
    }

    @Test
    fun `deserialize is order independent`() {
        val a = ShortcutBinding.deserialize("${KeyEvent.KEYCODE_V}|CTRL|SHIFT")
        val b = ShortcutBinding.deserialize("CTRL|SHIFT|${KeyEvent.KEYCODE_V}")
        assertEquals(a, b)
        assertTrue(a.ctrl)
        assertTrue(a.shift)
        assertEquals(KeyEvent.KEYCODE_V, a.keyCode)
    }

    @Test
    fun `deserialize ignores unknown tokens and yields empty binding`() {
        val binding = ShortcutBinding.deserialize("FOO|BAR")
        assertFalse(binding.ctrl)
        assertFalse(binding.shift)
        assertFalse(binding.alt)
        assertTrue(binding.isEmpty)
    }

    @Test
    fun `deserialize with non numeric keyCode yields empty binding`() {
        val binding = ShortcutBinding.deserialize("CTRL|NOT_A_NUMBER")
        assertTrue(binding.ctrl)
        assertTrue(binding.isEmpty)
    }

    @Test
    fun `deserialize with dangling separator yields empty keyCode`() {
        assertTrue(ShortcutBinding.deserialize("CTRL|SHIFT|").isEmpty)
    }

    // --- round trip --------------------------------------------------------

    @Test
    fun `serialize deserialize round trip preserves binding`() {
        val original = ShortcutBinding(ctrl = true, shift = true, alt = false, keyCode = KeyEvent.KEYCODE_N)
        val restored = ShortcutBinding.deserialize(original.serialize())
        assertEquals(original, restored)
        assertEquals(original.serialize(), restored.serialize())
    }

    @Test
    fun `round trip of empty binding stays empty`() {
        val empty = ShortcutBinding()
        val restored = ShortcutBinding.deserialize(empty.serialize())
        assertEquals(empty, restored)
        assertTrue(restored.isEmpty)
    }

    @Test
    fun `round trip for every ShortcutAction default`() {
        for (action in ShortcutAction.entries) {
            val restored = ShortcutBinding.deserialize(action.default.serialize())
            assertEquals("default for ${action.name} must round trip", action.default, restored)
        }
    }

    // --- reserved / modifier classification --------------------------------

    @Test
    fun `reserved keys are recognised`() {
        assertTrue(ShortcutBinding.isReservedKey(KeyEvent.KEYCODE_HOME))
        assertTrue(ShortcutBinding.isReservedKey(KeyEvent.KEYCODE_BACK))
        assertTrue(ShortcutBinding.isReservedKey(KeyEvent.KEYCODE_APP_SWITCH))
        assertTrue(ShortcutBinding.isReservedKey(KeyEvent.KEYCODE_POWER))
        assertTrue(ShortcutBinding.isReservedKey(KeyEvent.KEYCODE_MENU))
    }

    @Test
    fun `regular keys are not reserved`() {
        assertFalse(ShortcutBinding.isReservedKey(KeyEvent.KEYCODE_A))
        assertFalse(ShortcutBinding.isReservedKey(KeyEvent.KEYCODE_V))
        assertFalse(ShortcutBinding.isReservedKey(KeyEvent.KEYCODE_ENTER))
    }

    @Test
    fun `modifier keys are recognised`() {
        assertTrue(ShortcutBinding.isModifierKey(KeyEvent.KEYCODE_CTRL_LEFT))
        assertTrue(ShortcutBinding.isModifierKey(KeyEvent.KEYCODE_CTRL_RIGHT))
        assertTrue(ShortcutBinding.isModifierKey(KeyEvent.KEYCODE_SHIFT_LEFT))
        assertTrue(ShortcutBinding.isModifierKey(KeyEvent.KEYCODE_ALT_RIGHT))
        assertTrue(ShortcutBinding.isModifierKey(KeyEvent.KEYCODE_META_LEFT))
    }

    @Test
    fun `regular keys are not modifiers`() {
        assertFalse(ShortcutBinding.isModifierKey(KeyEvent.KEYCODE_A))
        assertFalse(ShortcutBinding.isModifierKey(KeyEvent.KEYCODE_V))
        assertFalse(ShortcutBinding.isModifierKey(KeyEvent.KEYCODE_UNKNOWN))
    }

    // --- ShortcutAction defaults -------------------------------------------

    @Test
    fun `every ShortcutAction has a unique prefKey`() {
        val keys = ShortcutAction.entries.map { it.prefKey }
        assertEquals("prefKeys must be unique", keys.size, keys.toSet().size)
    }

    @Test
    fun `every ShortcutAction default is bound to a key`() {
        for (action in ShortcutAction.entries) {
            assertFalse("${action.name} default must not be empty", action.default.isEmpty)
        }
    }

    @Test
    fun `known defaults match expected bindings`() {
        assertEquals(
            ShortcutBinding(ctrl = true, shift = true, keyCode = KeyEvent.KEYCODE_V),
            ShortcutAction.PASTE.default,
        )
        assertEquals(
            ShortcutBinding(ctrl = true, shift = true, keyCode = KeyEvent.KEYCODE_N),
            ShortcutAction.NEW_SESSION.default,
        )
        assertEquals(
            ShortcutBinding(ctrl = true, shift = true, keyCode = KeyEvent.KEYCODE_W),
            ShortcutAction.CLOSE_SESSION.default,
        )
    }

    // --- matches -----------------------------------------------------------

    private fun keyEvent(keyCode: Int, metaState: Int = 0): KeyEvent =
        KeyEvent.Builder()
            .setDownTime(0L)
            .setEventTime(0L)
            .setAction(KeyEvent.ACTION_DOWN)
            .setKeyCode(keyCode)
            .setMetaState(metaState)
            .build()

    @Test
    fun `matches when key and all modifiers agree`() {
        val binding = ShortcutBinding(ctrl = true, shift = true, keyCode = KeyEvent.KEYCODE_V)
        val event = keyEvent(
            KeyEvent.KEYCODE_V,
            KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON,
        )
        assertTrue(binding.matches(event))
    }

    @Test
    fun `matches requires exact modifier set`() {
        val binding = ShortcutBinding(ctrl = true, shift = true, keyCode = KeyEvent.KEYCODE_V)
        val ctrlOnly = keyEvent(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON)
        assertFalse("missing shift must not match", binding.matches(ctrlOnly))

        val ctrlShiftAlt = keyEvent(
            KeyEvent.KEYCODE_V,
            KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_ALT_ON,
        )
        assertFalse("extra alt must not match", binding.matches(ctrlShiftAlt))
    }

    @Test
    fun `matches requires exact key`() {
        val binding = ShortcutBinding(ctrl = true, shift = true, keyCode = KeyEvent.KEYCODE_V)
        val wrongKey = keyEvent(
            KeyEvent.KEYCODE_N,
            KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON,
        )
        assertFalse(binding.matches(wrongKey))
    }

    @Test
    fun `matches with no modifiers when binding has none`() {
        val binding = ShortcutBinding(keyCode = KeyEvent.KEYCODE_F)
        assertTrue(binding.matches(keyEvent(KeyEvent.KEYCODE_F)))
        assertFalse(binding.matches(keyEvent(KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON)))
    }

    @Test
    fun `empty binding never matches`() {
        val empty = ShortcutBinding()
        val event = keyEvent(
            KeyEvent.KEYCODE_V,
            KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON,
        )
        assertFalse(empty.matches(event))
        assertFalse(ShortcutBinding().matches(keyEvent(KeyEvent.KEYCODE_A)))
    }

    // --- fromKeyEvent ------------------------------------------------------

    @Test
    fun `fromKeyEvent captures key and modifiers`() {
        val event = keyEvent(
            KeyEvent.KEYCODE_C,
            KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON,
        )
        val binding = ShortcutBinding.fromKeyEvent(event)
        assertEquals(KeyEvent.KEYCODE_C, binding.keyCode)
        assertTrue(binding.ctrl)
        assertFalse(binding.shift)
        assertTrue(binding.alt)
    }

    @Test
    fun `fromKeyEvent round trips through serialize`() {
        val event = keyEvent(
            KeyEvent.KEYCODE_W,
            KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON,
        )
        val binding = ShortcutBinding.fromKeyEvent(event)
        assertEquals(binding, ShortcutBinding.deserialize(binding.serialize()))
        assertTrue(binding.matches(event))
    }

    // --- toDisplayString ---------------------------------------------------

    @Test
    fun `display string of empty binding`() {
        assertEquals("Not set", ShortcutBinding().toDisplayString())
    }

    @Test
    fun `display string joins modifiers with plus signs`() {
        val binding = ShortcutBinding(ctrl = true, shift = true, keyCode = KeyEvent.KEYCODE_V)
        assertEquals("Ctrl + Shift + V", binding.toDisplayString())
    }

    @Test
    fun `display string formats multi word key names`() {
        val binding = ShortcutBinding(keyCode = KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("Dpad left", binding.toDisplayString())
    }

    @Test
    fun `display string lowercases then capitalises key name`() {
        val binding = ShortcutBinding(alt = true, keyCode = KeyEvent.KEYCODE_ENTER)
        assertEquals("Alt + Enter", binding.toDisplayString())
    }
}
