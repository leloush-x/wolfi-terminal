package com.rk.terminal.ui.screens.settings

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.libcommons.toast
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.terminal.root.SheveryManager
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.components.SettingsToggle
import com.rk.terminal.ui.routes.MainActivityRoutes
import com.rk.terminal.ui.screens.downloader.AlpineSetupScreen
import com.rk.terminal.ui.screens.downloader.WolfiDownloadScreen
import com.rk.terminal.ui.screens.downloader.WolfiRepo
import com.rk.terminal.ui.screens.terminal.CustomSessions
import com.rk.terminal.ui.screens.terminal.ExecMode
import com.rk.terminal.ui.screens.terminal.Rootfs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    title: @Composable () -> Unit,
    description: @Composable () -> Unit = {},
    startWidget: (@Composable () -> Unit)? = null,
    endWidget: (@Composable () -> Unit)? = null,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    PreferenceTemplate(
        modifier = modifier.combinedClickable(
            enabled = isEnabled,
            indication = ripple(),
            interactionSource = interactionSource,
            onClick = onClick
        ),
        contentModifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 16.dp)
            .padding(start = 16.dp),
        title = title,
        description = description,
        startWidget = startWidget,
        endWidget = endWidget,
        applyPaddings = false
    )
}

object WorkingMode {
    const val ALPINE = 0
    const val ANDROID = 1
    const val WOLFI = 2
}

object InputMode {
    const val DEFAULT = 0
    const val TYPE_NULL = 1
    const val VISIBLE_PASSWORD = 2
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(
    navController: NavController,
    mainActivity: MainActivity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedWorkingMode by remember { mutableIntStateOf(Settings.working_Mode) }
    var selectedInputMode by remember { mutableIntStateOf(Settings.input_mode) }
    var selectedExecMode by remember { mutableStateOf(Rootfs.execMode.value) }
    var customSessions by remember { mutableStateOf(CustomSessions.getAll()) }
    var showAddCustomSession by remember { mutableStateOf(false) }
    var defaultIsCustom by remember { mutableStateOf(Settings.default_is_custom) }
    var defaultCustomId by remember { mutableStateOf(CustomSessions.getDefaultId()) }
    var showWolfiDownloader by remember { mutableStateOf(false) }
    var showAlpineSetup by remember { mutableStateOf(false) }
    val wolfiScope = rememberCoroutineScope()
    var wolfiVer by remember { mutableStateOf(Settings.wolfi_version) }
    var latestWolfiTag by remember { mutableStateOf<String?>(null) }
    var checkingWolfi by remember { mutableStateOf(false) }
    var wolfiUpdateMsg by remember { mutableStateOf<String?>(null) }
    var selectedLoginShell by remember { mutableStateOf(Settings.login_shell) }

    LaunchedEffect(Unit) {
        SheveryManager.detectManager(context)
        SheveryManager.refresh()
    }

    fun selectWolfi() {
        defaultIsCustom = false
        Settings.default_is_custom = false
        selectedWorkingMode = WorkingMode.WOLFI
        Settings.working_Mode = WorkingMode.WOLFI
    }

    fun selectAlpine() {
        defaultIsCustom = false
        Settings.default_is_custom = false
        selectedWorkingMode = WorkingMode.ALPINE
        Settings.working_Mode = WorkingMode.ALPINE
    }

    if (showWolfiDownloader || showAlpineSetup) {
        if (showWolfiDownloader) {
            WolfiDownloadScreen(
                modifier = modifier,
                onCancel = { showWolfiDownloader = false },
                onComplete = {
                    showWolfiDownloader = false
                    selectWolfi()
                    wolfiScope.launch(Dispatchers.IO) {
                        // Fresh system files from the new tarball on next session.
                        // Keeps /root home. Running Wolfi sessions must be restarted.
                        Rootfs.clearWolfiSystem(context)
                        withContext(Dispatchers.Main) {
                            wolfiVer = Settings.wolfi_version
                            latestWolfiTag = Settings.wolfi_version.ifBlank { null }
                            wolfiUpdateMsg = "Updated — restart Wolfi sessions to use it"
                            toast("Wolfi updated — restart Wolfi sessions")
                        }
                    }
                }
            )
        }
        if (showAlpineSetup) {
            AlpineSetupScreen(
                modifier = modifier,
                onCancel = { showAlpineSetup = false },
                onComplete = {
                    showAlpineSetup = false
                    selectAlpine()
                }
            )
        }
        if (showAddCustomSession) {
            CustomSessionDialog(
                onDismiss = { showAddCustomSession = false },
                onSave = { name, shellPath ->
                    if (name.isNotBlank() && shellPath.isNotBlank()) {
                        CustomSessions.add(name, shellPath)
                        customSessions = CustomSessions.getAll()
                    }
                    showAddCustomSession = false
                }
            )
        }
        return
    }

    PreferenceLayout(
        label = stringResource(strings.settings),
        modifier = modifier,
        onBack = { navController.popBackStack() }
    ) {
        PreferenceGroup(heading = stringResource(strings.default_working_mode)) {
            WorkingModeOption(
                title = "Alpine",
                description = stringResource(strings.alpine_desc),
                selected = !defaultIsCustom && selectedWorkingMode == WorkingMode.ALPINE
            ) {
                if (Rootfs.isRootfsInstalled(context)) {
                    selectAlpine()
                } else {
                    showAlpineSetup = true
                }
            }
            WorkingModeOption(
                title = "Wolfi",
                description = stringResource(strings.wolfi_desc),
                selected = !defaultIsCustom && selectedWorkingMode == WorkingMode.WOLFI
            ) {
                if (Rootfs.isWolfiRootfsInstalled(context)) {
                    selectWolfi()
                } else {
                    showWolfiDownloader = true
                }
            }
            WorkingModeOption(
                title = "Android",
                description = stringResource(strings.android_desc),
                selected = !defaultIsCustom && selectedWorkingMode == WorkingMode.ANDROID
            ) {
                defaultIsCustom = false
                Settings.default_is_custom = false
                selectedWorkingMode = WorkingMode.ANDROID
                Settings.working_Mode = WorkingMode.ANDROID
            }
            customSessions.forEach { session ->
                WorkingModeOption(
                    title = session.name,
                    description = session.shellPath,
                    selected = defaultIsCustom && defaultCustomId == session.id
                ) {
                    defaultIsCustom = true
                    defaultCustomId = session.id
                    Settings.default_is_custom = true
                    CustomSessions.setDefault(session.id)
                }
            }
        }

        PreferenceGroup(heading = "Execution Mode") {
            ExecModeOption("Chroot", "Requires root, faster, real bind mounts", ExecMode.CHROOT, selectedExecMode) {
                selectedExecMode = it
                Rootfs.setExecMode(it)
            }
            ExecModeOption("Chroot (Shevery)", "Root via Shevery manager, real bind mounts", ExecMode.SHEVERY, selectedExecMode) {
                selectedExecMode = it
                Rootfs.setExecMode(it)
            }
            ExecModeOption("Proot", "No root required, slightly slower", ExecMode.PROOT, selectedExecMode) {
                selectedExecMode = it
                Rootfs.setExecMode(it)
            }
        }

        PreferenceGroup(heading = "Root access (Shevery / Shizuku)") {
            val mgrInstalled = SheveryManager.isManagerInstalled()
            val granted = SheveryManager.permissionGranted.value
            val fullRoot = SheveryManager.hasFullRootAccess
            SettingsCard(
                title = { Text(SheveryManager.statusLine()) },
                description = {
                    Text(
                        when {
                            fullRoot -> "Mounts, chroot and elevated shells allowed"
                            granted -> "Granted, but daemon is not root — chroot unavailable"
                            else -> "Tap to refresh status"
                        }
                    )
                },
                onClick = {
                    SheveryManager.detectManager(context)
                    SheveryManager.refresh()
                }
            )
            if (!granted) {
                SettingsCard(
                    title = { Text("Grant manager permission") },
                    description = { Text("Ask ${SheveryManager.managerLabel} for full access") },
                    onClick = { SheveryManager.ensurePermission(context) }
                )
            }
            SettingsCard(
                title = {
                    Text(
                        if (mgrInstalled) "Open ${SheveryManager.managerLabel} manager"
                        else "Get Shevery manager"
                    )
                },
                description = {
                    Text(
                        if (mgrInstalled) "Start the server and allow this app"
                        else "Required for root access and chroot"
                    )
                },
                onClick = { SheveryManager.openManager(context) }
            )
            SettingsToggle(
                label = stringResource(strings.use_shizuku),
                description = stringResource(strings.use_shizuku_desc),
                showSwitch = true,
                default = Settings.auto_rish,
                sideEffect = { Settings.auto_rish = it }
            )
        }

        PreferenceGroup(heading = "Wolfi updates") {
            val wolfiInstalled = Rootfs.isWolfiRootfsInstalled(context)
            SettingsCard(
                title = {
                    Text(
                        "Installed: ${
                            wolfiVer.ifBlank {
                                if (wolfiInstalled) "unknown version" else "not installed"
                            }
                        }"
                    )
                },
                description = {
                    Text(
                        latestWolfiTag?.let { "Latest release: $it" }
                            ?: (wolfiUpdateMsg ?: "Wolfi Linux rootfs")
                    )
                },
                onClick = {}
            )
            if (wolfiInstalled) {
                SettingsCard(
                    title = { Text(if (checkingWolfi) "Checking…" else "Check for updates") },
                    onClick = {
                        if (checkingWolfi) return@SettingsCard
                        checkingWolfi = true
                        wolfiUpdateMsg = null
                        wolfiScope.launch(Dispatchers.IO) {
                            try {
                                val latest = WolfiRepo.fetchLatest()
                                withContext(Dispatchers.Main) {
                                    checkingWolfi = false
                                    latestWolfiTag = latest.first
                                    wolfiUpdateMsg =
                                        if (wolfiVer.isBlank() || wolfiVer != latest.first) {
                                            "Update available: ${latest.first}"
                                        } else {
                                            "Up to date (${latest.first})"
                                        }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    checkingWolfi = false
                                    wolfiUpdateMsg = "Check failed: ${e.message}"
                                }
                            }
                        }
                    },
                    isEnabled = !checkingWolfi
                )
                if (latestWolfiTag != null && (wolfiVer.isBlank() || wolfiVer != latestWolfiTag)) {
                    SettingsCard(
                        title = { Text("Download update ($latestWolfiTag)") },
                        description = { Text("Replaces system files, keeps /root home. Restart Wolfi sessions after.") },
                        onClick = { showWolfiDownloader = true }
                    )
                }
            }
        }

        PreferenceGroup(heading = "Login shell") {
            fun selectShell(value: String) {
                selectedLoginShell = value
                Settings.login_shell = value
            }
            WorkingModeOption(
                title = "Distro default",
                description = "ash on Alpine, sh on Wolfi",
                selected = selectedLoginShell.isBlank()
            ) { selectShell("") }
            WorkingModeOption(
                title = "bash",
                description = "/bin/bash (if missing: apk add bash)",
                selected = selectedLoginShell == "/bin/bash"
            ) { selectShell("/bin/bash") }
            WorkingModeOption(
                title = "sh",
                description = "/bin/sh",
                selected = selectedLoginShell == "/bin/sh"
            ) { selectShell("/bin/sh") }
            WorkingModeOption(
                title = "ash",
                description = "/bin/ash",
                selected = selectedLoginShell == "/bin/ash"
            ) { selectShell("/bin/ash") }
        }

        PreferenceGroup(heading = stringResource(strings.input_mode)) {
            InputModeOption(stringResource(strings.input_mode_default), stringResource(strings.input_mode_default_desc), InputMode.DEFAULT, selectedInputMode) {
                selectedInputMode = it
                Settings.input_mode = it
            }
            InputModeOption(stringResource(strings.input_mode_type_null), stringResource(strings.input_mode_type_null_desc), InputMode.TYPE_NULL, selectedInputMode) {
                selectedInputMode = it
                Settings.input_mode = it
            }
            InputModeOption(stringResource(strings.input_mode_visible_password), stringResource(strings.input_mode_visible_password_desc), InputMode.VISIBLE_PASSWORD, selectedInputMode) {
                selectedInputMode = it
                Settings.input_mode = it
            }
        }

        PreferenceGroup(heading = "Custom Sessions") {
            customSessions.forEach { session ->
                SettingsCard(
                    title = { Text(session.name) },
                    description = { Text(session.shellPath) },
                    onClick = {},
                    endWidget = {
                        IconButton(onClick = {
                            CustomSessions.remove(session.id)
                            customSessions = CustomSessions.getAll()
                            defaultCustomId = CustomSessions.getDefaultId()
                            defaultIsCustom = Settings.default_is_custom
                        }) {
                            Icon(imageVector = Icons.Outlined.Delete, contentDescription = null)
                        }
                    }
                )
            }
            SettingsCard(
                title = { Text("Add Custom Session") },
                onClick = { showAddCustomSession = true },
                endWidget = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            )
        }

        PreferenceGroup {
            SettingsCard(
                title = { Text(stringResource(strings.customizations)) },
                onClick = { navController.navigate(MainActivityRoutes.Customization.route) },
                endWidget = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            )
        }

        PreferenceGroup {
            SettingsToggle(
                label = stringResource(strings.seccomp),
                description = stringResource(strings.seccomp_desc),
                showSwitch = true,
                default = Settings.seccomp,
                sideEffect = { Settings.seccomp = it }
            )

            SettingsToggle(
                label = stringResource(strings.all_file_access),
                description = stringResource(strings.all_file_access_desc),
                showSwitch = false,
                default = false,
                sideEffect = {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:${context.packageName}".toUri())
                    } else {
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                    }
                    runCatching { context.startActivity(intent) }.onFailure {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        }
                    }
                }
            )
        }
    }

    if (showAddCustomSession) {
        CustomSessionDialog(
            onDismiss = { showAddCustomSession = false },
            onSave = { name, shellPath ->
                if (name.isNotBlank() && shellPath.isNotBlank()) {
                    CustomSessions.add(name, shellPath)
                    customSessions = CustomSessions.getAll()
                }
                showAddCustomSession = false
            }
        )
    }
}

@Composable
private fun WorkingModeOption(title: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    SettingsCard(
        title = { Text(title) },
        description = { Text(description) },
        startWidget = {
            RadioButton(
                modifier = Modifier.padding(start = 8.dp),
                selected = selected,
                onClick = onSelect
            )
        },
        onClick = onSelect
    )
}

@Composable
private fun InputModeOption(title: String, description: String, mode: Int, currentMode: Int, onSelect: (Int) -> Unit) {
    SettingsCard(
        title = { Text(title) },
        description = { Text(description) },
        startWidget = {
            RadioButton(
                modifier = Modifier.padding(start = 8.dp),
                selected = currentMode == mode,
                onClick = { onSelect(mode) }
            )
        },
        onClick = { onSelect(mode) }
    )
}

@Composable
private fun ExecModeOption(title: String, description: String, mode: ExecMode, currentMode: ExecMode?, onSelect: (ExecMode) -> Unit) {
    SettingsCard(
        title = { Text(title) },
        description = { Text(description) },
        startWidget = {
            RadioButton(
                modifier = Modifier.padding(start = 8.dp),
                selected = currentMode == mode,
                onClick = { onSelect(mode) }
            )
        },
        onClick = { onSelect(mode) }
    )
}
