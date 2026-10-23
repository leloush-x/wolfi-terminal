package com.rk.terminal.rootfs

import android.system.Os
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.GZIPInputStream

/**
 * Unpacks the distro rootfs archives (tar.gz) on the device.
 *
 * Doing it here instead of letting busybox `tar` do it inside proot is much
 * faster: proot traps and forwards every single syscall, which turns the
 * unpack of a ~100 MB rootfs into a minute of staring at a blank prompt on
 * the first session. Unpacking natively takes seconds, and the session's
 * init script then finds the system already in place and skips its own tar.
 *
 * The published archives come out of `docker export` and are rewritten by GNU
 * tar, so both GNU long names ("L"/"K") and PAX headers ("x") are handled.
 * Device nodes are skipped — the releases have /dev removed, and an app
 * cannot create them without root anyway.
 */
object RootfsExtractor {

    private const val BLOCK = 512
    private const val GZIP_BUFFER = 64 * 1024
    private const val MAX_HEADER_PAYLOAD = 1L shl 20
    private const val USTAR_MAGIC = "ustar"

    private const val TYPE_REGULAR = '0'
    private const val TYPE_HARDLINK = '1'
    private const val TYPE_SYMLINK = '2'
    private const val TYPE_CHAR_DEVICE = '3'
    private const val TYPE_BLOCK_DEVICE = '4'
    private const val TYPE_DIRECTORY = '5'
    private const val TYPE_FIFO = '6'
    private const val TYPE_LONG_NAME = 'L'
    private const val TYPE_LONG_LINK = 'K'
    private const val TYPE_PAX_EXTENDED = 'x'
    private const val TYPE_PAX_GLOBAL = 'g'

    /**
     * Unpacks [archive] into [target].
     *
     * Existing files are replaced, existing directories are merged into, so
     * the user's /root home survives a rootfs update the same way it did
     * when busybox tar did the unpacking.
     *
     * @throws Exception when the archive cannot be read. The caller should
     *   leave [target] empty in that case, so the session init script can
     *   fall back to unpacking it with busybox tar.
     */
    fun install(archive: File, target: File) {
        GZIPInputStream(archive.inputStream().buffered(GZIP_BUFFER), GZIP_BUFFER).use { gz ->
            extract(gz, target)
        }
    }

    private fun extract(input: InputStream, target: File) {
        target.mkdirs()
        val root = target.canonicalPath
        val header = ByteArray(BLOCK)
        var longName: String? = null
        var longLink: String? = null
        var paxName: String? = null
        var paxLink: String? = null

        while (readFully(input, header)) {
            if (isZeroBlock(header)) break
            // A tar header carries the "ustar" magic. Anything else means the
            // archive ended early or has a junk tail — the published rootfs
            // archives carry one that GNU tar itself rejects.
            if (!hasUstarMagic(header)) break

            val type = header[156].toInt().toChar()
            val size = readOctal(header, 124, 12)

            when (type) {
                TYPE_LONG_NAME -> {
                    longName = readPayloadString(input, size)
                    skipPadding(input, size)
                    continue
                }
                TYPE_LONG_LINK -> {
                    longLink = readPayloadString(input, size)
                    skipPadding(input, size)
                    continue
                }
                TYPE_PAX_EXTENDED -> {
                    val (path, linkPath) = parsePax(readPayload(input, size))
                    paxName = path
                    paxLink = linkPath
                    skipPadding(input, size)
                    continue
                }
                TYPE_PAX_GLOBAL -> {
                    skip(input, size)
                    skipPadding(input, size)
                    continue
                }
            }

            val rawName = paxName ?: longName ?: readString(header, 0, 100)
            val linkName = paxLink ?: longLink ?: readString(header, 157, 100)
            val mode = readOctal(header, 100, 8)
            longName = null
            longLink = null
            paxName = null
            paxLink = null

            val relative = rawName.removePrefix("./").trimStart('/')
            if (relative.isEmpty()) {
                skip(input, size)
                skipPadding(input, size)
                continue
            }

            val dest = File(target, relative)
            // Never write through a symlink that points outside the rootfs.
            if (!isInside(root, dest.parentFile)) {
                skip(input, size)
                skipPadding(input, size)
                continue
            }

            when (type) {
                TYPE_DIRECTORY -> {
                    dest.mkdirs()
                    chmod(dest, mode)
                    skip(input, size)
                }
                TYPE_REGULAR -> {
                    dest.parentFile?.mkdirs()
                    deleteIfExists(dest)
                    dest.outputStream().use { out -> copyExactly(input, out, size) }
                    chmod(dest, mode)
                }
                TYPE_SYMLINK -> {
                    dest.parentFile?.mkdirs()
                    deleteIfExists(dest)
                    symlink(linkName, dest)
                    skip(input, size)
                }
                TYPE_HARDLINK -> {
                    dest.parentFile?.mkdirs()
                    deleteIfExists(dest)
                    val source = File(target, linkName.removePrefix("./").trimStart('/'))
                    if (isInside(root, source)) hardlink(source, dest)
                    skip(input, size)
                }
                TYPE_CHAR_DEVICE, TYPE_BLOCK_DEVICE, TYPE_FIFO -> skip(input, size)
                else -> skip(input, size)
            }
            // Every payload is padded to a multiple of the block size.
            skipPadding(input, size)
        }
    }

    private fun isZeroBlock(block: ByteArray): Boolean {
        for (b in block) if (b.toInt() != 0) return false
        return true
    }

    /** True when the header carries the "ustar" magic at offset 257. */
    private fun hasUstarMagic(header: ByteArray): Boolean {
        for (i in 0 until 5) {
            if (header[257 + i].toInt() != USTAR_MAGIC[i].code) return false
        }
        return true
    }

    private fun isInside(root: String, dir: File?): Boolean {
        if (dir == null) return false
        val path = runCatching { dir.canonicalPath }.getOrNull() ?: return false
        return path == root || path.startsWith(root + File.separator)
    }

    private fun isSymlink(file: File): Boolean =
        runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)

    private fun deleteIfExists(file: File) {
        if (isSymlink(file)) {
            file.delete()
        } else if (file.exists()) {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
    }

    /** Creates a symlink, falling back to java.nio when Os is unavailable. */
    private fun symlink(target: String, link: File) {
        runCatching { Os.symlink(target, link.path) }
        if (!isSymlink(link)) {
            runCatching { Files.createSymbolicLink(link.toPath(), Paths.get(target)) }
        }
    }

    /** Creates a hard link, falling back to java.nio when Os is unavailable. */
    private fun hardlink(source: File, link: File) {
        runCatching { Os.link(source.path, link.path) }
        if (!link.exists()) {
            runCatching { Files.createLink(link.toPath(), source.toPath()) }
        }
    }

    private fun chmod(file: File, mode: Long) {
        runCatching { Files.setPosixFilePermissions(file.toPath(), posixPermissions(mode)) }
        // java.nio cannot express the sticky bit, so the raw mode is applied
        // on top of it.
        runCatching { Os.chmod(file.path, mode.toInt()) }
    }

    private fun posixPermissions(mode: Long): Set<PosixFilePermission> {
        val permissions = mutableSetOf<PosixFilePermission>()
        // The rwx bits of the tar mode, in octal. Kotlin has no octal
        // literals, so they are written in hex.
        if (mode and 0x100L != 0L) permissions += PosixFilePermission.OWNER_READ
        if (mode and 0x080L != 0L) permissions += PosixFilePermission.OWNER_WRITE
        if (mode and 0x040L != 0L) permissions += PosixFilePermission.OWNER_EXECUTE
        if (mode and 0x020L != 0L) permissions += PosixFilePermission.GROUP_READ
        if (mode and 0x010L != 0L) permissions += PosixFilePermission.GROUP_WRITE
        if (mode and 0x008L != 0L) permissions += PosixFilePermission.GROUP_EXECUTE
        if (mode and 0x004L != 0L) permissions += PosixFilePermission.OTHERS_READ
        if (mode and 0x002L != 0L) permissions += PosixFilePermission.OTHERS_WRITE
        if (mode and 0x001L != 0L) permissions += PosixFilePermission.OTHERS_EXECUTE
        return permissions
    }

    /** Reads a NUL-terminated name out of a header field. */
    private fun readString(header: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val limit = offset + length
        while (end < limit && header[end].toInt() != 0) end++
        return String(header, offset, end - offset, Charsets.UTF_8)
    }

    /**
     * Reads an octal header field. Sizes and modes are octal ASCII; the
     * base-256 variant GNU tar uses for huge values is rejected instead of
     * being misread.
     */
    private fun readOctal(header: ByteArray, offset: Int, length: Int): Long {
        if (header[offset].toInt() and 0x80 != 0) {
            throw IllegalStateException("base-256 tar headers are not supported")
        }
        var value = 0L
        var i = offset
        val limit = offset + length
        while (i < limit) {
            val c = header[i].toInt() and 0xFF
            if (c == 0) break
            if (c == ' '.code) {
                i++
                continue
            }
            if (c < '0'.code || c > '7'.code) break
            value = value * 8 + (c - '0'.code)
            i++
        }
        return value
    }

    /** Reads the PAX records of an extended header into a (path, linkPath) pair. */
    private fun parsePax(data: ByteArray): Pair<String?, String?> {
        var path: String? = null
        var linkPath: String? = null
        val text = String(data, Charsets.UTF_8)
        var i = 0
        while (i < text.length) {
            val space = text.indexOf(' ', i)
            if (space < 0) break
            val length = text.substring(i, space).toIntOrNull() ?: break
            if (length <= 0 || i + length > text.length) break
            val record = text.substring(space + 1, i + length).trimEnd('\n')
            val eq = record.indexOf('=')
            if (eq > 0) {
                when (record.substring(0, eq)) {
                    "path" -> path = record.substring(eq + 1)
                    "linkpath" -> linkPath = record.substring(eq + 1)
                }
            }
            i += length
        }
        return path to linkPath
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) return false
            read += n
        }
        return true
    }

    private fun readPayload(input: InputStream, size: Long): ByteArray {
        // Only used for long names and PAX headers — never for file data.
        require(size in 0..MAX_HEADER_PAYLOAD) { "tar header payload too large: $size" }
        val data = ByteArray(size.toInt())
        readFully(input, data)
        return data
    }

    private fun readPayloadString(input: InputStream, size: Long): String {
        val data = readPayload(input, size)
        var end = data.size
        while (end > 0 && data[end - 1].toInt() == 0) end--
        return String(data, 0, end, Charsets.UTF_8)
    }

    private fun copyExactly(input: InputStream, output: OutputStream, size: Long) {
        val buffer = ByteArray(GZIP_BUFFER)
        var remaining = size
        while (remaining > 0) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (n < 0) throw IllegalStateException("truncated tar archive")
            output.write(buffer, 0, n)
            remaining -= n
        }
    }

    private fun skip(input: InputStream, size: Long) {
        var remaining = size
        val buffer = ByteArray(GZIP_BUFFER)
        while (remaining > 0) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (n < 0) return
            remaining -= n
        }
    }

    /** Skips the zero padding a tar payload is padded with. */
    private fun skipPadding(input: InputStream, size: Long) {
        val padding = (BLOCK - (size % BLOCK)) % BLOCK
        if (padding > 0) skip(input, padding)
    }
}
