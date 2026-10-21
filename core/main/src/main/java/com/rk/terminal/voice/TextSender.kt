package com.rk.terminal.voice

import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView

object TextSender {

    private const val BRACKETED_BIT = 1 shl 10 // DECSET 2004

    /** Strip control chars (<0x20 except tab), ESC and C1; handle newlines per bracketed mode. */
    fun build(text: String, bracketed: Boolean, trailingSpace: Boolean): String {
        // Strip ESC and C1 control chars always.
        val noEsc = text.replace("\u001B", "").replace(Regex("[\\u0080-\\u009F]"), "")
        // Strip other control chars except tab and newline (newline handled below).
        val sb = StringBuilder()
        for (cp in noEsc.codePoints().toArray()) {
            if (cp == '\t'.code) {
                sb.appendCodePoint(cp)
            } else if (cp == '\n'.code || cp == '\r'.code) {
                sb.appendCodePoint(cp)
            } else if (cp < 0x20) {
                // drop
            } else {
                sb.appendCodePoint(cp)
            }
        }
        var cleaned = sb.toString()
        if (bracketed) {
            // keep newlines
        } else {
            cleaned = cleaned.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
        }
        if (trailingSpace && cleaned.isNotEmpty() && !cleaned.endsWith(" ")) {
            cleaned += " "
        }
        return if (bracketed) {
            "\u001B[200~" + cleaned + "\u001B[201~"
        } else {
            cleaned
        }
    }

    /**
     * Whether the running program asked for bracketed paste (DECSET 2004).
     *
     * The emulator does not expose this, so the flag is read reflectively.
     * The lookup is cached — it runs on every dictated word.
     */
    private val bracketedPasteMethod: java.lang.reflect.Method? by lazy {
        runCatching {
            TerminalEmulator::class.java
                .getDeclaredMethod("isDecsetInternalBitSet", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
        }.getOrNull()
    }

    fun isBracketedPaste(emulator: TerminalEmulator?): Boolean {
        val method = bracketedPasteMethod ?: return false
        if (emulator == null) return false
        return runCatching { method.invoke(emulator, BRACKETED_BIT) as? Boolean }.getOrNull() ?: false
    }

    fun send(view: TerminalView?, text: String, trailingSpace: Boolean = true) {
        val session = view?.mTermSession ?: return
        val bracketed = isBracketedPaste(view.mEmulator)
        val output = build(text, bracketed, trailingSpace)
        if (output.isEmpty()) return
        // chunk large input
        val chunkSize = 512
        var i = 0
        while (i < output.length) {
            val end = (i + chunkSize).coerceAtMost(output.length)
            session.write(output.substring(i, end))
            i = end
        }
    }
}
