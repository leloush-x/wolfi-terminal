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
import com.rk.terminal.github.GitHubSettingsSection
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
    const val DEBIAN = 3
    const val VOID = 4
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
    var showDebianDownloader by remember { mutableStateOf(false) }
    var showVoidDownloader by remember { mutableStateOf(false) }
    var showAlpineSetup by remember { mutableStateOf(false) }
    val wolfiScope = rememberCoroutineScope()
    var wolfiVer by remember { mutableStateOf(Settings.wolfi_version) }
    var latestWolfiTag by remember { mutableStateOf<String?>(null) }
    var checkingWolfi by remember { mutableStateOf(false) }
    var wolfiUpdateMsg by remember { mutableStateOf<String?>(null) }
    var debianVer by remember { mutableStateOf(Settings.debian_version) }
    var latestDebianTag by remember { mutableStateOf<String?>(null) }
    var checkingDebian by remember { mutableStateOf(false) }
    var debianUpdateMsg by remember { mutableStateOf<String?>(null) }
    var voidVer by remember { mutableStateOf(Settings.void_version) }
    var latestVoidTag by remember { mutableStateOf<String?>(null) }
    var checkingVoid by remember { mutableStateOf(false) }
    var voidUpdateMsg by remember { mutableStateOf<String?>(null) }
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

    fun selectDebian() {
        defaultIsCustom = false
        Settings.default_is_custom = false
        selectedWorkingMode = WorkingMode.DEBIAN
        Settings.working_Mode = WorkingMode.DEBIAN
    }

    fun selectVoid() {
        defaultIsCustom = false
        Settings.default_is_custom = false
        selectedWorkingMode = WorkingMode.VOID
        Settings.working_Mode = WorkingMode.VOID
    }

    if (showWolfiDownloader || showDebianDownloader || showVoidDownloader || showAlpineSetup) {
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
        if (showDebianDownloader) {
            WolfiDownloadScreen(
                modifier = modifier,
                distro = "debian",
                onCancel = { showDebianDownloader = false },
                onComplete = {
                    showDebianDownloader = false
                    selectDebian()
                    wolfiScope.launch(Dispatchers.IO) {
                        Rootfs.clearDebianSystem(context)
                        withContext(Dispatchers.Main) {
                            debianVer = Settings.debian_version
                            latestDebianTag = Settings.debian_version.ifBlank { null }
                            debianUpdateMsg = "Updated — restart Debian sessions to use it"
                            toast("Debian updated — restart Debian sessions")
                        }
                    }
                }
            )
        }
        if (showVoidDownloader) {
            WolfiDownloadScreen(
                modifier = modifier,
                distro = "void",
                onCancel = { showVoidDownloader = false },
                onComplete = {
                    showVoidDownloader = false
                    selectVoid()
                    wolfiScope.launch(Dispatchers.IO) {
                        Rootfs.clearVoidSystem(context)
                        withContext(Dispatchers.Main) {
                            voidVer = Settings.void_version
                            latestVoidTag = Settings.void_version.ifBlank { null }
                            voidUpdateMsg = "Updated — restart Void sessions to use it"
                            toast("Void updated — restart Void sessions")
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
                title = "Debian",
                description = stringResource(strings.debian_desc),
                selected = !defaultIsCustom && selectedWorkingMode == WorkingMode.DEBIAN
            ) {
                if (Rootfs.isDebianRootfsInstalled(context)) {
                    selectDebian()
                } else {
                    showDebianDownloader = true
                }
            }
            WorkingModeOption(
                title = "Void",
                description = stringResource(strings.void_desc),
                selected = !defaultIsCustom && selectedWorkingMode == WorkingMode.VOID
            ) {
                if (Rootfs.isVoidRootfsInstalled(context)) {
                    selectVoid()
                } else {
                    showVoidDownloader = true
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
            ExecModeOption(
                "Chroot (Shevery)",
                when {
                    SheveryManager.hasFullRootAccess ->
                        "Root via Shevery manager, real bind mounts"
                    SheveryManager.permissionGranted.value ->
                        "Shevery is ADB-mode here — distros run via Proot"
                    else ->
                        "Root via Shevery manager — needs grant + root daemon"
                },
                ExecMode.SHEVERY,
                selectedExecMode
            ) {
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

                PreferenceGroup(heading = "Debian updates") {
            val debianInstalled = Rootfs.isDebianRootfsInstalled(context)
            SettingsCard(
                title = {
                    Text(
                        "Installed: ${
                            debianVer.ifBlank {
                                if (debianInstalled) "unknown version" else "not installed"
                            }
                        }"
                    )
                },
                description = {
                    Text(
                        latestDebianTag?.let { "Latest release: $it" }
                            ?: (debianUpdateMsg ?: "Debian rootfs")
                    )
                },
                onClick = {}
            )
            if (debianInstalled) {
                SettingsCard(
                    title = { Text(if (checkingDebian) "Checking…" else "Check for updates") },
                    onClick = {
                        if (checkingDebian) return@SettingsCard
                        checkingDebian = true
                        debianUpdateMsg = null
                        wolfiScope.launch(Dispatchers.IO) {
                            try {
                                val latest = WolfiRepo.fetchLatestFor("debian")
                                withContext(Dispatchers.Main) {
                                    checkingDebian = false
                                    latestDebianTag = latest.first
                                    debianUpdateMsg =
                                        if (debianVer.isBlank() || debianVer != latest.first) {
                                            "Update available: ${latest.first}"
                                        } else {
                                            "Up to date (${latest.first})"
                                        }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    checkingDebian = false
                                    debianUpdateMsg = "Check failed: ${e.message}"
                                }
                            }
                        }
                    },
                    isEnabled = !checkingDebian
                )
                if (latestDebianTag != null && (debianVer.isBlank() || debianVer != latestDebianTag)) {
                    SettingsCard(
                        title = { Text("Download update ($latestDebianTag)") },
                        description = { Text("Replaces system files, keeps /root home. Restart Debian sessions after.") },
                        onClick = { showDebianDownloader = true }
                    )
                }
            }
        }

        PreferenceGroup(heading = "Void updates") {
            val voidInstalled = Rootfs.isVoidRootfsInstalled(context)
            SettingsCard(
                title = {
                    Text(
                        "Installed: ${
                            voidVer.ifBlank {
                                if (voidInstalled) "unknown version" else "not installed"
                            }
                        }"
                    )
                },
                description = {
                    Text(
                        latestVoidTag?.let { "Latest release: $it" }
                            ?: (voidUpdateMsg ?: "Void rootfs")
                    )
                },
                onClick = {}
            )
            if (voidInstalled) {
                SettingsCard(
                    title = { Text(if (checkingVoid) "Checking…" else "Check for updates") },
                    onClick = {
                        if (checkingVoid) return@SettingsCard
                        checkingVoid = true
                        voidUpdateMsg = null
                        wolfiScope.launch(Dispatchers.IO) {
                            try {
                                val latest = WolfiRepo.fetchLatestFor("void")
                                withContext(Dispatchers.Main) {
                                    checkingVoid = false
                                    latestVoidTag = latest.first
                                    voidUpdateMsg =
                                        if (voidVer.isBlank() || voidVer != latest.first) {
                                            "Update available: ${latest.first}"
                                        } else {
                                            "Up to date (${latest.first})"
                                        }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    checkingVoid = false
                                    voidUpdateMsg = "Check failed: ${e.message}"
                                }
                            }
                        }
                    },
                    isEnabled = !checkingVoid
                )
                if (latestVoidTag != null && (voidVer.isBlank() || voidVer != latestVoidTag)) {
                    SettingsCard(
                        title = { Text("Download update ($latestVoidTag)") },
                        description = { Text("Replaces system files, keeps /root home. Restart Void sessions after.") },
                        onClick = { showVoidDownloader = true }
                    )
                }
            }
        }

        GitHubSettingsSection(navController = navController, mainActivity = mainActivity)

        SftpSettingsSection(mainActivity = mainActivity)

        PreferenceGroup(heading = "Login shell") {
            fun selectShell(value: String) {
                selectedLoginShell = value
                Settings.login_shell = value
            }
            WorkingModeOption(
                title = "Distro default",
                description = "ash on Alpine, sh on Wolfi/Debian/Void",
                selected = selectedLoginShell.isBlank()
            ) { selectShell("") }
            WorkingModeOption(
                title = "bash",
                description = "/bin/bash (Alpine: apk add bash; Debian: apt install bash; Void: xbps-install bash)",
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
