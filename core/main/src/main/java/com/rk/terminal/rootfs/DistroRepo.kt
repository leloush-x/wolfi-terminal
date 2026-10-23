package com.rk.terminal.rootfs

import android.os.Build
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

/** Resolves release assets through GitHub's latest-release redirect, without API requests. */
object DistroRepo {
    const val REPO = "leloush-x/wolfi-os-rootfs"
    private const val CACHE_MILLIS = 10 * 60 * 1000L
    private val cache = ConcurrentHashMap<String, CachedAsset>()

    fun assetNameForAbi(distro: Distro): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull {
            it in listOf("arm64-v8a", "x86_64")
        } ?: throw RuntimeException(
            "${distro.displayName} does not support ARM32 (armv7). Use Alpine on this device."
        )
        return when (abi) {
            "arm64-v8a" -> "${distro.id}-rootfs-aarch64.tar.gz"
            "x86_64" -> "${distro.id}-rootfs-x86_64.tar.gz"
            else -> throw RuntimeException("Unsupported ABI: $abi")
        }
    }

    /** Blocking network call — invoke on Dispatchers.IO. Returns (tag, downloadUrl). */
    fun fetchLatest(distro: Distro): Pair<String, String> {
        val assetName = assetNameForAbi(distro)
        val key = "$assetName:${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}"
        val now = System.currentTimeMillis()
        cache[key]?.takeIf { now - it.fetchedAt < CACHE_MILLIS }?.let {
            return it.tag to it.url
        }

        val url = "https://github.com/$REPO/releases/latest/download/$assetName"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            setRequestProperty("User-Agent", "WolfiTerminal")
            connectTimeout = 15000
            readTimeout = 15000
            instanceFollowRedirects = false
        }
        try {
            val code = conn.responseCode
            val location = conn.getHeaderField("Location")
            if (code !in 300..399 || location.isNullOrBlank()) {
                throw RuntimeException("Latest rootfs asset unavailable: HTTP $code")
            }
            val resolved = URL(URL(url), location).toString()
            val path = URL(resolved).path
            val marker = "/releases/download/"
            val markerAt = path.indexOf(marker)
            if (markerAt < 0) {
                throw RuntimeException("Could not resolve latest rootfs release")
            }
            val tag = URLDecoder.decode(path.substring(markerAt + marker.length).substringBefore('/'), "UTF-8")
            if (tag.isBlank()) throw RuntimeException("Latest rootfs release has no tag")
            cache[key] = CachedAsset(tag, resolved, now)
            return tag to resolved
        } finally {
            conn.disconnect()
        }
    }

    /** Reads the SHA256SUMS asset from a versioned rootfs release. */
    fun fetchChecksum(tag: String, assetName: String): String {
        val encodedTag = java.net.URLEncoder.encode(tag, "UTF-8").replace("+", "%20")
        val url = "https://github.com/$REPO/releases/download/$encodedTag/SHA256SUMS"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "WolfiTerminal")
            connectTimeout = 15000
            readTimeout = 15000
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw RuntimeException("Rootfs checksums unavailable: HTTP ${conn.responseCode}")
            }
            val expectedName = "./$assetName"
            val matchingLine = conn.inputStream.bufferedReader().useLines { lines ->
                lines.firstOrNull { line -> line.trim().split(Regex("\\s+"), limit = 2).getOrNull(1) in setOf(assetName, expectedName) }
            } ?: throw RuntimeException("Checksum for $assetName is missing")
            val checksum = matchingLine.trim().substringBefore(' ').lowercase()
            if (!checksum.matches(Regex("[0-9a-f]{64}"))) {
                throw RuntimeException("Invalid checksum for $assetName")
            }
            return checksum
        } finally {
            conn.disconnect()
        }
    }

    private data class CachedAsset(val tag: String, val url: String, val fetchedAt: Long)
}
