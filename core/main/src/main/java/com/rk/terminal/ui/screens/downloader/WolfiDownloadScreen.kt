package com.rk.terminal.ui.screens.downloader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.libcommons.child
import com.rk.libcommons.toast
import com.rk.resources.strings
import com.rk.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun WolfiDownloadScreen(
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val installingStr = stringResource(strings.installing)
    val setupFailedStr = stringResource(strings.setup_failed)
    val cancelStr = stringResource(strings.cancel)

    var progress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf(installingStr) }
    var versionTag by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var cancelled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val downloadState = remember { DownloadState() }

    LaunchedEffect(attempt) {
        if (attempt < 0) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    error = null
                    progress = 0f
                    statusText = installingStr
                }
                withContext(Dispatchers.Main) {
                    statusText = "Resolving latest Wolfi release…"
                }
                val (tag, url) = WolfiRepo.fetchLatest()
                withContext(Dispatchers.Main) {
                    versionTag = tag
                    statusText = "Downloading Wolfi $tag…"
                }
                val outputFile = context.filesDir.child("wolfi.tar.gz")
                val partFile = File(outputFile.parent, "wolfi.tar.gz.part")
                downloadState.connection?.disconnect()
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "WolfiTerminal")
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                }
                downloadState.connection = conn
                try {
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                        throw RuntimeException("Download failed: HTTP ${conn.responseCode}")
                    }
                    val total = conn.contentLengthLong.takeIf { it > 0 }
                    var downloaded = 0L
                    conn.inputStream.use { input ->
                        FileOutputStream(partFile).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                if (cancelled) throw InterruptedException("cancelled")
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (total != null) {
                                    val p = (downloaded.toFloat() / total).coerceIn(0f, 1f)
                                    withContext(Dispatchers.Main) {
                                        progress = p
                                        statusText = "Downloading Wolfi $tag… ${(p * 100).toInt()}%"
                                    }
                                }
                            }
                        }
                    }
                    if (cancelled) throw InterruptedException("cancelled")
                    if (outputFile.exists()) outputFile.delete()
                    if (!partFile.renameTo(outputFile)) {
                        partFile.copyTo(outputFile, overwrite = true)
                        partFile.delete()
                    }
                } finally {
                    downloadState.connection = null
                    conn.disconnect()
                }
                withContext(Dispatchers.Main) {
                    progress = 1f
                    Settings.wolfi_version = tag
                    onComplete()
                }
            } catch (e: InterruptedException) {
                withContext(Dispatchers.Main) {
                    runCatching {
                        File(context.filesDir, "wolfi.tar.gz.part").delete()
                    }
                    if (!cancelled) error = e.message
                }
            } catch (e: Exception) {
                if (cancelled) return@withContext
                withContext(Dispatchers.Main) {
                    runCatching {
                        File(context.filesDir, "wolfi.tar.gz.part").delete()
                    }
                    error = e.javaClass.simpleName + ": " + e.message
                    statusText = setupFailedStr.format(e.message)
                    toast(setupFailedStr.format(e.message))
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (error != null) {
                Text(
                    "Setup Failed: $error",
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.Center) {
                    OutlinedButton(onClick = {
                        cancelled = true
                        downloadState.connection?.disconnect()
                        runCatching {
                            File(context.filesDir, "wolfi.tar.gz.part").delete()
                        }
                        onCancel()
                    }) { Text(cancelStr) }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(onClick = {
                        cancelled = false
                        attempt += 1
                    }) { Text("Retry") }
                }
            } else {
                val tag = versionTag
                Text(
                    if (tag != null) "Downloading Wolfi $tag…" else statusText,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    )
                } else {
                    CircularProgressIndicator()
                }
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(onClick = {
                    cancelled = true
                    downloadState.connection?.disconnect()
                    scope.launch(Dispatchers.IO) {
                        runCatching {
                            File(context.filesDir, "wolfi.tar.gz.part").delete()
                        }
                        withContext(Dispatchers.Main) { onCancel() }
                    }
                }) { Text(cancelStr) }
            }
        }
    }
}

private class DownloadState {
    @Volatile
    var connection: HttpURLConnection? = null
}
