package com.rk.terminal.ui.screens.downloader

import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Shared client for leloush-x/wolfi-os-rootfs releases.
 * Always resolves the newest release via the GitHub API.
 */
object WolfiRepo {
    const val REPO = "leloush-x/wolfi-os-rootfs"
    const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"

    fun assetNameForAbi(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull {
            it in listOf("arm64-v8a", "x86_64")
        } ?: throw RuntimeException("Wolfi does not support ARM32 (armv7). Use Alpine on this device.")
        return when (abi) {
            "arm64-v8a" -> "wolfi-rootfs-aarch64.tar.gz"
            "x86_64" -> "wolfi-rootfs-x86_64.tar.gz"
            else -> throw RuntimeException("Unsupported ABI: $abi")
        }
    }

    /** Blocking network call — invoke on Dispatchers.IO. Returns (tag, downloadUrl). */
    fun fetchLatest(): Pair<String, String> {
        val assetName = assetNameForAbi()
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
