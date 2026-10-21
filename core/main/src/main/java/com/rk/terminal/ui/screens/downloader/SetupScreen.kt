package com.rk.terminal.ui.screens.downloader

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
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.terminal.rootfs.Distro
import com.rk.terminal.rootfs.ExecMode
import com.rk.terminal.rootfs.Rootfs
import com.rk.terminal.root.hasRootAccess
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.screens.settings.SettingsCard
import com.rk.terminal.ui.screens.terminal.TerminalScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SetupScreen(
    modifier: Modifier = Modifier,
    mainActivity: MainActivity,
    navController: NavHostController
) {
    val context = LocalContext.current

    var rootChecked by remember { mutableStateOf(false) }
    var showExecModeDialog by remember { mutableStateOf(false) }

    // First-launch distro picker state. Old installs already have a rootfs,
    // so they skip the chooser and go straight to the terminal.
    val installed = remember {
        mutableStateMapOf<Distro, Boolean>().apply {
            Distro.entries.forEach { put(it, Rootfs.isDistroInstalled(context, it)) }
        }
    }
    val needsChoice = installed.values.none { it }
    var distroChoice by remember {
        mutableStateOf(
            Distro.fromWorkingMode(Settings.working_Mode) ?: Distro.ALPINE
        )
    }
    var choiceMade by remember { mutableStateOf(!needsChoice) }

    LaunchedEffect(Unit) {
        if (Rootfs.execMode.value != null) {
            rootChecked = true
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
                }) { Text("Chroot") }
            },
            dismissButton = {
                TextButton(onClick = {
                    Rootfs.setExecMode(ExecMode.PROOT)
                    showExecModeDialog = false
                }) { Text("Proot") }
            }
        )
    }

    val ready = installed[distroChoice] == true && Rootfs.execMode.value != null

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            !rootChecked -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Checking root access...", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator()
                }
            }
            showExecModeDialog -> {
                // The dialog above handles this state.
            }
            needsChoice && !choiceMade -> {
                DistroChooser(
                    onPick = { distro ->
                        distroChoice = distro
                        choiceMade = true
                        Settings.default_is_custom = false
                        Settings.working_Mode = distro.workingMode
                    }
                )
            }
            installed[distroChoice] != true -> {
                DistroSetupScreen(
                    modifier = modifier,
                    distro = distroChoice,
                    onCancel = {
                        // Fall back to a distro that is already installed, so
                        // cancelling never leaves the user stuck here.
                        val fallback = Distro.entries.firstOrNull { installed[it] == true }
                        if (fallback != null) {
                            distroChoice = fallback
                            Settings.working_Mode = fallback.workingMode
                        } else {
                            choiceMade = false
                        }
                    },
                    onComplete = {
                        installed[distroChoice] = true
                        Settings.default_is_custom = false
                        Settings.working_Mode = distroChoice.workingMode
                        Rootfs.isInstalled.value = true
                    }
                )
            }
            ready -> {
                TerminalScreen(mainActivity = mainActivity, navController = navController)
            }
        }
    }
}

@Composable
private fun DistroChooser(onPick: (Distro) -> Unit) {
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
            Distro.entries.forEach { distro ->
                SettingsCard(
                    title = { Text(distro.displayName) },
                    description = {
                        Text(
                            when (distro) {
                                Distro.ALPINE -> stringResource(strings.alpine_desc)
                                Distro.WOLFI -> stringResource(strings.wolfi_desc)
                                Distro.DEBIAN -> stringResource(strings.debian_desc)
                                Distro.VOID -> stringResource(strings.void_desc)
                            }
                        )
                    },
                    onClick = { onPick(distro) }
                )
            }
        }
    }
}
