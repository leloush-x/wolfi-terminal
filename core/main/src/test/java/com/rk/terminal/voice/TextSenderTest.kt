package com.rk.terminal.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSenderTest {

    @Test
    fun `plain text gets trailing space`() {
        assertEquals("hello ", TextSender.build("hello", false, true))
    }

    @Test
    fun `multi word preserved`() {
        assertEquals("hello world ", TextSender.build("hello world", false, true))
    }

    @Test
    fun `emoji preserved by code point`() {
        val e = "😀"
        assertEquals("$e ", TextSender.build(e, false, true))
    }

    @Test
    fun `newline becomes space when not bracketed`() {
        assertEquals("a b ", TextSender.build("a\nb", false, true))
    }

    @Test
    fun `crlf becomes space when not bracketed`() {
        assertEquals("a b ", TextSender.build("a\r\nb", false, true))
    }

    @Test
    fun `esc stripped`() {
        assertEquals("ab ", TextSender.build("a\u001Bb", false, true))
    }

    @Test
    fun `control chars stripped except tab`() {
        assertEquals("ab ", TextSender.build("a\u0001\u0007b", false, true))
        // tab kept
        assertTrue(TextSender.build("a\tb", false, true).contains("\t"))
    }

    @Test
    fun `bracketed wraps and keeps newlines`() {
        val out = TextSender.build("a\nb", true, true)
        assertTrue(out.startsWith("\u001B[200~"))
        assertTrue(out.endsWith("\u001B[201~"))
        assertTrue(out.contains("a\nb"))
    }

    @Test
    fun `trailing space false`() {
        assertEquals("hello", TextSender.build("hello", false, false))
    }

    @Test
    fun `empty input empty output`() {
        assertEquals("", TextSender.build("", false, true))
    }
}
