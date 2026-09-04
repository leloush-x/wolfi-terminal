package com.rk.terminal.ui.screens.downloader

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.libcommons.child
import com.rk.libcommons.toast
import com.rk.resources.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Copies the bundled Alpine rootfs asset to files/alpine.tar.gz.
 * Same look and cancel/retry behavior as WolfiDownloadScreen.
 * Used when Alpine was never installed (e.g. Wolfi picked at first launch).
 */
@Composable
fun AlpineSetupScreen(
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val installingStr = stringResource(strings.installing)
    val setupFailedStr = stringResource(strings.setup_failed)
    val cancelStr = stringResource(strings.cancel)

    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var cancelled by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        if (attempt < 0) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { error = null }
                val abis = Build.SUPPORTED_ABIS
                val abi = abis.firstOrNull {
                    it in listOf("arm64-v8a", "armeabi-v7a", "x86_64")
                } ?: throw RuntimeException("Unsupported CPU architectures: ${abis.joinToString()}")
                val alpineArch = when (abi) {
                    "arm64-v8a" -> "aarch64"
                    "armeabi-v7a" -> "armhf"
                    "x86_64" -> "x86_64"
                    else -> throw RuntimeException("Unsupported ABI: $abi")
                }
                val outputFile = context.filesDir.child("alpine.tar.gz")
                val partFile = File(outputFile.parent, "alpine.tar.gz.part")
                if (!outputFile.exists() || outputFile.length() == 0L) {
                    context.assets.open("alpine-$alpineArch.tar.gz.rootfs").use { input ->
                        FileOutputStream(partFile).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                if (cancelled) throw InterruptedException("cancelled")
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    if (cancelled) throw InterruptedException("cancelled")
                    if (outputFile.exists()) outputFile.delete()
                    if (!partFile.renameTo(outputFile)) {
                        partFile.copyTo(outputFile, overwrite = true)
                        partFile.delete()
                    }
                }
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: InterruptedException) {
                withContext(Dispatchers.Main) {
                    runCatching {
                        File(context.filesDir, "alpine.tar.gz.part").delete()
                    }
                    if (!cancelled) error = e.message
                }
            } catch (e: Exception) {
                if (cancelled) return@withContext
                withContext(Dispatchers.Main) {
                    runCatching {
                        File(context.filesDir, "alpine.tar.gz.part").delete()
                    }
                    error = e.javaClass.simpleName + ": " + e.message
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
                        runCatching {
                            File(context.filesDir, "alpine.tar.gz.part").delete()
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
                Text("Setting up Alpine…", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(onClick = {
                    cancelled = true
                    runCatching {
                        File(context.filesDir, "alpine.tar.gz.part").delete()
                    }
                    onCancel()
                }) { Text(cancelStr) }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    installingStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
