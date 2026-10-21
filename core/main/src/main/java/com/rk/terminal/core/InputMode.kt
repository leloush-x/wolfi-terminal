package com.rk.terminal.core

/**
 * Soft-keyboard input type requested from the IME.
 *
 * Mirrors the input types the terminal view understands; see
 * `TerminalView.onCreateInputConnection`.
 */
object InputMode {
    /** Let the IME pick a normal text input type. */
    const val DEFAULT = 0

    /** No soft keyboard at all — for hardware-keyboard users. */
    const val TYPE_NULL = 1

    /** Visible-password style input, keeps the IME from learning the text. */
    const val VISIBLE_PASSWORD = 2
}
