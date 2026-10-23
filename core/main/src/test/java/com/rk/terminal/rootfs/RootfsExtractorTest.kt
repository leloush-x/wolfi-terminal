package com.rk.terminal.rootfs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * The extractor is a tar parser, so it is checked against a real GNU tar
 * archive rather than a hand-built one: the archive holds a directory, a
 * plain file, an executable file, a relative symlink and an absolute one.
 */
class RootfsExtractorTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `extracts directories files permissions and symlinks`() {
        val archive = File(temp.newFolder(), "rootfs.tar.gz")
        archive.writeBytes(Base64.getDecoder().decode(REFERENCE_ARCHIVE))

        val target = File(temp.newFolder(), "rootfs")
        RootfsExtractor.install(archive, target)

        assertTrue("directory was not created", File(target, "dir").isDirectory)
        assertEquals(
            "hello world\n",
            File(target, "dir/file.txt").readText()
        )
        assertTrue(
            "executable bit was not applied",
            File(target, "bin/run.sh").canExecute()
        )
        assertTrue(
            "relative symlink was not created",
            Files.isSymbolicLink(File(target, "link").toPath())
        )
        assertEquals(
            "dir/file.txt",
            Files.readSymbolicLink(File(target, "link").toPath()).toString()
        )
        assertTrue(
            "absolute symlink was not created",
            Files.isSymbolicLink(File(target, "abslink").toPath())
        )
        assertEquals(
            "/bin/busybox",
            Files.readSymbolicLink(File(target, "abslink").toPath()).toString()
        )
    }

    @Test
    fun `replaces existing files and keeps the home directory`() {
        val archive = File(temp.newFolder(), "rootfs.tar.gz")
        archive.writeBytes(Base64.getDecoder().decode(REFERENCE_ARCHIVE))

        val target = File(temp.newFolder(), "rootfs")
        File(target, "root").mkdirs()
        File(target, "root/keepme").writeText("mine")
        File(target, "dir").mkdirs()
        File(target, "dir/file.txt").writeText("stale")

        RootfsExtractor.install(archive, target)

        assertEquals("the home directory was wiped", "mine", File(target, "root/keepme").readText())
        assertEquals("the stale file was not replaced", "hello world\n", File(target, "dir/file.txt").readText())
    }

    @Test
    fun `does not create hardlinks whose source escapes the rootfs`() {
        val outside = File(temp.root, "outside").apply { writeText("keep") }
        val archive = File(temp.root, "escape.tar.gz")
        archive.outputStream().use { file ->
            GZIPOutputStream(file).use { gzip ->
                val header = ByteArray(512)
                "escape".toByteArray().copyInto(header, 0)
                "0000644\u0000".toByteArray().copyInto(header, 100)
                "0000000\u0000".toByteArray().copyInto(header, 108)
                "0000000\u0000".toByteArray().copyInto(header, 116)
                "00000000000\u0000".toByteArray().copyInto(header, 124)
                "00000000000\u0000".toByteArray().copyInto(header, 136)
                "ustar\u0000".toByteArray().copyInto(header, 257)
                "00".toByteArray().copyInto(header, 263)
                "../outside".toByteArray().copyInto(header, 157)
                header[156] = '1'.code.toByte()
                gzip.write(header)
                gzip.write(ByteArray(1024))
            }
        }

        val target = File(temp.root, "rootfs")
        RootfsExtractor.install(archive, target)

        assertEquals("keep", outside.readText())
        assertTrue("escaped hardlink was created", !File(target, "escape").exists())
    }

    private companion object {
        /** `tar -czf` of a dir, an executable, and two symlinks. */
        val REFERENCE_ARCHIVE = (
            "H4sIAAAAAAAAA+2Wy2oDIRSGXecpLNl7OePleTLJBKUygs6Q9O07MasOtCGBM23Rb6Og4JFPf2X85BMnqIgFq3VpF9Zt6UsNxgjT" +
                "SWmIkEJZTajGLevOnKdDopSkGKef5j0a/6ew4v/sw8CmK9IGb4KNUt/7l2rlH6QWhAqccr5SuX83hBDpJaZw2v12LY3tYbz34x/M" +
                "f2Nb/m/B3X+aR5Yd1hoP/QOs/EsDquX/FuzfygHIbjccXaTOt1egKhgPfnzHXaPcf2ufyP9OdEAo4H9Nq7//jB/6jHwEXvCvYXn/" +
                "oURTP+ePPl6xiqvcf6PRqJdPBEzWPgAUAAA="
            )
    }
}
