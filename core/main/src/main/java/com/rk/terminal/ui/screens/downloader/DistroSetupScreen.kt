package com.rk.terminal.ui.screens.downloader

import android.os.Build
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.rk.libcommons.localDir
import com.rk.libcommons.toast
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.terminal.rootfs.Distro
import com.rk.terminal.rootfs.DistroRepo
import com.rk.terminal.rootfs.Rootfs
import com.rk.terminal.rootfs.RootfsExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Installs a distro: copies the bundled asset or downloads the latest
 * release, then unpacks the rootfs on the device.
 *
 * Unpacking here rather than inside the session is what makes the first
 * session usable right away — busybox tar under proot traps every syscall
 * and takes minutes for a full rootfs.
 */
@Composable
fun DistroSetupScreen(
    modifier: Modifier = Modifier,
    distro: Distro = Distro.ALPINE,
    onCancel: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val displayName = distro.displayName
    val installingStr = stringResource(strings.installing)
    val setupFailedStr = stringResource(strings.setup_failed)
    val cancelStr = stringResource(strings.cancel)

    var progress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf(installingStr) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
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
                downloadState.releaseTag = null
                val archive = context.filesDir.child(distro.tarName)
                val partFile = File(context.filesDir, "${distro.tarName}.part")

                if (distro.isBundled) {
                    withContext(Dispatchers.Main) { statusText = "Copying $displayName…" }
                    copyBundledAsset(context, distro, partFile)
                } else {
                    withContext(Dispatchers.Main) { statusText = "Resolving latest $displayName release…" }
                    val (tag, url) = DistroRepo.fetchLatest(distro)
                    withContext(Dispatchers.Main) { statusText = "Downloading $displayName $tag…" }
                    downloadArchive(url, partFile, downloadState) { fraction ->
                        progress = fraction
                        statusText = "Downloading $displayName $tag… ${(fraction * 100).toInt()}%"
                    }
                    downloadState.releaseTag = tag
                }

                if (cancelled) throw InterruptedException("cancelled")
                withContext(Dispatchers.Main) { statusText = "Unpacking $displayName…" }
                unpack(context, distro, partFile)
                if (archive.exists()) archive.delete()
                if (!partFile.renameTo(archive)) partFile.copyTo(archive, overwrite = true)
                downloadState.releaseTag?.let { Settings.setRootfsVersion(distro, it) }

                withContext(Dispatchers.Main) {
                    progress = 1f
                    onComplete()
                }
            } catch (e: InterruptedException) {
                withContext(Dispatchers.Main) {
                    if (!cancelled) error = e.message
                }
            } catch (e: Exception) {
                if (cancelled) return@withContext
                withContext(Dispatchers.Main) {
                    error = e.javaClass.simpleName + ": " + e.message
                    statusText = setupFailedStr.format(e.message)
                    toast(setupFailedStr.format(e.message))
                }
            } finally {
                if (!cancelled && error == null) {
                    runCatching { File(context.filesDir, "${distro.tarName}.part").delete() }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (error != null) {
                Text("Setup Failed: $error", color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.Center) {
                    OutlinedButton(onClick = {
                        cancelled = true
                        downloadState.connection?.disconnect()
                        onCancel()
                    }) { Text(cancelStr) }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(onClick = {
                        cancelled = false
                        attempt += 1
                    }) { Text("Retry") }
                }
            } else {
                Text(statusText, style = MaterialTheme.typography.bodyLarge)
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
                        runCatching { File(context.filesDir, "${distro.tarName}.part").delete() }
                        withContext(Dispatchers.Main) { onCancel() }
                    }
                }) { Text(cancelStr) }
            }
        }
    }
}

/** The bundled rootfs asset for this device, e.g. "alpine-aarch64.tar.gz.rootfs". */
private fun bundledAssetName(distro: Distro): String {
    val abis = Build.SUPPORTED_ABIS
    val abi = abis.firstOrNull { it in listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        ?: throw RuntimeException("Unsupported CPU architectures: ${abis.joinToString()}")
    val arch = when (abi) {
        "arm64-v8a" -> "aarch64"
        "armeabi-v7a" -> "armhf"
        "x86_64" -> "x86_64"
        else -> throw RuntimeException("Unsupported ABI: $abi")
    }
    return "${distro.id}-$arch.tar.gz.rootfs"
}

private fun copyBundledAsset(context: android.content.Context, distro: Distro, partFile: File) {
    context.assets.open(bundledAssetName(distro)).use { input ->
        FileOutputStream(partFile).use { output ->
            input.copyTo(output)
        }
    }
}

private fun downloadArchive(
    url: String,
    partFile: File,
    state: DownloadState,
    onProgress: (Float) -> Unit
) {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        setRequestProperty("User-Agent", "WolfiTerminal")
        connectTimeout = 15000
        readTimeout = 30000
        instanceFollowRedirects = true
    }
    state.connection = conn
    try {
        val offset = partFile.takeIf { it.exists() }?.length() ?: 0L
        if (offset > 0) conn.setRequestProperty("Range", "bytes=$offset-")
        val response = conn.responseCode
        val append = offset > 0 && response == HttpURLConnection.HTTP_PARTIAL
        if (response != HttpURLConnection.HTTP_OK && !append) {
            throw RuntimeException("Download failed: HTTP ${conn.responseCode}")
        }
        val total = (conn.contentLengthLong.takeIf { it > 0 }?.let { it + if (append) offset else 0L })
        var downloaded = offset.takeIf { append } ?: 0L
        conn.inputStream.use { input ->
            FileOutputStream(partFile, append).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (total != null) {
                        onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
    } finally {
        state.connection = null
        conn.disconnect()
    }
}

/**
 * Drops the old system files and unpacks the new rootfs.
 *
 * When the unpack fails the system is left empty, so the session's init
 * script falls back to unpacking the archive with busybox tar.
 */
private fun unpack(context: android.content.Context, distro: Distro, archive: File) {
    Rootfs.clearSystem(context, distro)
    val target = context.localDir().child(distro.id)
    try {
        RootfsExtractor.install(archive, target)
    } catch (e: Exception) {
        target.listFiles()?.forEach {
            if (it.name != "root" && it.name != "tmp") it.deleteRecursively()
        }
        throw e
    }
}

private class DownloadState {
    @Volatile
    var connection: HttpURLConnection? = null

    @Volatile
    var releaseTag: String? = null
}
