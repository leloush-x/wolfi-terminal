package com.rk.terminal.ui.screens.downloader

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.libcommons.*
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.terminal.root.hasRootAccess
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.screens.settings.SettingsCard
import com.rk.terminal.ui.screens.settings.WorkingMode
import com.rk.terminal.ui.screens.terminal.ExecMode
import com.rk.terminal.ui.screens.terminal.Rootfs
import com.rk.terminal.ui.screens.terminal.TerminalScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

@Composable
fun SetupScreen(
    modifier: Modifier = Modifier,
    mainActivity: MainActivity,
    navController: NavHostController
) {
    val context = LocalContext.current
    val installingStr = stringResource(strings.installing)
    val setupFailedStr = stringResource(strings.setup_failed)

    var isSetupComplete by remember { mutableStateOf(Rootfs.isRootfsInstalled(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    var rootChecked by remember { mutableStateOf(false) }
    var showExecModeDialog by remember { mutableStateOf(false) }
    var extractionStarted by remember { mutableStateOf(false) }

    // First-launch distro picker state. Old installs already have a rootfs,
    // so they skip the chooser and follow the original Alpine flow.
    val alpinePresent = remember { Rootfs.isRootfsInstalled(context) }
    val wolfiPresent = remember { Rootfs.isWolfiRootfsInstalled(context) }
    val debianPresent = remember { Rootfs.isDebianRootfsInstalled(context) }
    val voidPresent = remember { Rootfs.isVoidRootfsInstalled(context) }
    val needsChoice = !alpinePresent && !wolfiPresent && !debianPresent && !voidPresent
    var distroChoice by remember {
        mutableIntStateOf(
            when (Settings.working_Mode) {
                WorkingMode.WOLFI -> WorkingMode.WOLFI
                WorkingMode.DEBIAN -> WorkingMode.DEBIAN
                WorkingMode.VOID -> WorkingMode.VOID
                else -> WorkingMode.ALPINE
            }
        )
    }
    var choiceMade by remember { mutableStateOf(!needsChoice) }
    var wolfiDone by remember { mutableStateOf(wolfiPresent) }
    var debianDone by remember { mutableStateOf(debianPresent) }
    var voidDone by remember { mutableStateOf(voidPresent) }

    fun startAlpineInstall() {
        if (isSetupComplete) {
            Rootfs.isInstalled.value = true
        } else {
            extractionStarted = true
        }
    }

    LaunchedEffect(Unit) {
        if (Rootfs.execMode.value != null) {
            rootChecked = true
            val preselectedReady = when (Settings.working_Mode) {
                WorkingMode.WOLFI -> wolfiPresent
                WorkingMode.DEBIAN -> debianPresent
                WorkingMode.VOID -> voidPresent
                else -> isSetupComplete
            }
            if (isSetupComplete || preselectedReady) {
                Rootfs.isInstalled.value = true
            } else if (!needsChoice) {
                extractionStarted = true
            }
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            val hasRoot = hasRootAccess()
            withContext(Dispatchers.Main) {
                rootChecked = true
                if (hasRoot) {
                    showExecModeDialog = true
                } else {
                    Rootfs.setExecMode(ExecMode.PROOT)
                    if (isSetupComplete) {
                        Rootfs.isInstalled.value = true
                    } else if (!needsChoice) {
                        extractionStarted = true
                    }
                }
            }
        }
    }

    if (showExecModeDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Root Access Detected") },
            text = {
                Text("Root access was found. Run the terminal with chroot (faster, requires root for every session) or proot (no root required, slightly slower)? You can change this later in Settings.")
            },
            confirmButton = {
                TextButton(onClick = {
                    Rootfs.setExecMode(ExecMode.CHROOT)
                    showExecModeDialog = false
                    if (isSetupComplete) {
                        Rootfs.isInstalled.value = true
                    } else if (!needsChoice) {
                        extractionStarted = true
                    }
                }) { Text("Chroot") }
            },
            dismissButton = {
                TextButton(onClick = {
                    Rootfs.setExecMode(ExecMode.PROOT)
                    showExecModeDialog = false
                    if (isSetupComplete) {
                        Rootfs.isInstalled.value = true
                    } else if (!needsChoice) {
                        extractionStarted = true
                    }
                }) { Text("Proot") }
            }
        )
    }

    LaunchedEffect(extractionStarted) {
        if (!extractionStarted || isSetupComplete) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
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
                val assetName = "alpine-$alpineArch.tar.gz.rootfs"
                val outputFile = context.filesDir.child("alpine.tar.gz")
                if (!outputFile.exists() || outputFile.length() == 0L) {
                    context.assets.open(assetName).use { input ->
                        FileOutputStream(outputFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    Rootfs.isInstalled.value = true
                    isSetupComplete = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = e.javaClass.simpleName + ": " + e.message
                    toast(setupFailedStr.format(e.message))
                }
            }
        }
    }

    val distroReady = when (distroChoice) {
        WorkingMode.WOLFI -> wolfiDone
        WorkingMode.DEBIAN -> debianDone
        WorkingMode.VOID -> voidDone
        else -> isSetupComplete
    }
    val ready = distroReady && Rootfs.execMode.value != null

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!ready) {
            when {
                error != null -> {
                    Text("Setup Failed: $error", color = MaterialTheme.colorScheme.error)
                }
                !rootChecked -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Checking root access...", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator()
                    }
                }
                showExecModeDialog -> {
                    // Dialog above handles this state; nothing else to show.
                }
                needsChoice && !choiceMade -> {
                    DistroChooser(
                        onPickAlpine = {
                            distroChoice = WorkingMode.ALPINE
                            Settings.default_is_custom = false
                            Settings.working_Mode = WorkingMode.ALPINE
                            choiceMade = true
                            startAlpineInstall()
                        },
                        onPickWolfi = {
                            distroChoice = WorkingMode.WOLFI
                            choiceMade = true
                        },
                        onPickDebian = {
                            distroChoice = WorkingMode.DEBIAN
                            choiceMade = true
                        },
                        onPickVoid = {
                            distroChoice = WorkingMode.VOID
                            choiceMade = true
                        }
                    )
                }
                (distroChoice == WorkingMode.WOLFI && !wolfiDone) ||
                    (distroChoice == WorkingMode.DEBIAN && !debianDone) ||
                    (distroChoice == WorkingMode.VOID && !voidDone) -> {
                    val dlDistro = when (distroChoice) {
                        WorkingMode.DEBIAN -> "debian"
                        WorkingMode.VOID -> "void"
                        else -> "wolfi"
                    }
                    WolfiDownloadScreen(
                        distro = dlDistro,
                        onCancel = { choiceMade = false },
                        onComplete = {
                            when (distroChoice) {
                                WorkingMode.WOLFI -> wolfiDone = true
                                WorkingMode.DEBIAN -> debianDone = true
                                WorkingMode.VOID -> voidDone = true
                            }
                            Settings.default_is_custom = false
                            Settings.working_Mode = distroChoice
                            Rootfs.isInstalled.value = true
                        }
                    )
                }
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(installingStr, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator()
                    }
                }
            }
        } else {
            TerminalScreen(mainActivity = mainActivity, navController = navController)
        }
    }
}

@Composable
private fun DistroChooser(
    onPickAlpine: () -> Unit,
    onPickWolfi: () -> Unit,
    onPickDebian: () -> Unit,
    onPickVoid: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Choose your Linux", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "This will be your default. You can change it later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        PreferenceGroup {
            SettingsCard(
                title = { Text("Wolfi") },
                description = { Text(stringResource(strings.wolfi_desc)) },
                onClick = onPickWolfi
            )
            SettingsCard(
                title = { Text("Alpine") },
                description = { Text(stringResource(strings.alpine_desc)) },
                onClick = onPickAlpine
            )
            SettingsCard(
                title = { Text("Debian") },
                description = { Text(stringResource(strings.debian_desc)) },
                onClick = onPickDebian
            )
            SettingsCard(
                title = { Text("Void") },
                description = { Text(stringResource(strings.void_desc)) },
                onClick = onPickVoid
            )
        }
    }
}
