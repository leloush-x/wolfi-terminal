package com.rk.terminal.rootfs

import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for the rootfs releases in leloush-x/wolfi-os-rootfs.
 * Always resolves the newest release via the GitHub API.
 * Serves the wolfi, debian and void rootfs assets from the same repo:
 *   <distro>-rootfs-aarch64.tar.gz / <distro>-rootfs-x86_64.tar.gz
 */
object DistroRepo {
    const val REPO = "leloush-x/wolfi-os-rootfs"
    const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"

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
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(API_LATEST).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "WolfiTerminal")
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 15000
                readTimeout = 15000
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw RuntimeException("GitHub API: HTTP ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "latest").ifBlank { "latest" }
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val obj = assets.getJSONObject(i)
                    if (obj.optString("name") == assetName) {
                        val url = obj.optString("browser_download_url")
                        if (url.isNotBlank()) return tag to url
                    }
                }
            }
            throw RuntimeException("Asset $assetName not found in latest release ($tag)")
        } finally {
            conn?.disconnect()
        }
    }
}
