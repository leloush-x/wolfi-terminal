package com.rk.terminal.ui.screens.settings

import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.libcommons.toast
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.settings.SettingsRepository
import com.rk.terminal.core.InputMode
import com.rk.terminal.core.WorkingMode
import com.rk.terminal.github.GitHubSettingsSection
import com.rk.terminal.rootfs.Distro
import com.rk.terminal.rootfs.DistroRepo
import com.rk.terminal.rootfs.ExecMode
import com.rk.terminal.rootfs.Rootfs
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.components.SettingsToggle
import com.rk.terminal.ui.routes.MainActivityRoutes
import com.rk.terminal.ui.screens.downloader.DistroSetupScreen
import com.rk.terminal.ui.screens.terminal.CustomSessions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    mainActivity: MainActivity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val query = searchQuery.trim()
    fun matches(vararg terms: String) = query.isBlank() || terms.any { it.contains(query, ignoreCase = true) }
    var selectedWorkingMode by remember { mutableIntStateOf(Settings.working_Mode) }
    var selectedInputMode by remember { mutableIntStateOf(Settings.input_mode) }
    var selectedExecMode by remember { mutableStateOf(Rootfs.execMode.value) }
    var voiceKeyboardMode by remember { mutableIntStateOf(SettingsRepository.getKeyboardInputMode()) }
    var voicePreview by remember { mutableStateOf(SettingsRepository.isVoicePreview()) }
    var voiceTrailingSpace by remember { mutableStateOf(SettingsRepository.isVoiceTrailingSpace()) }
    var voiceLanguage by remember { mutableStateOf(SettingsRepository.getVoiceLanguage()) }
    var keepScreenOn by remember { mutableStateOf(SettingsRepository.isKeepScreenOn()) }
    var wakeLock by remember { mutableStateOf(SettingsRepository.isWakeLock()) }
    var customSessions by remember { mutableStateOf(CustomSessions.getAll()) }
    var showAddCustomSession by remember { mutableStateOf(false) }
    var defaultIsCustom by remember { mutableStateOf(Settings.default_is_custom) }
    var defaultCustomId by remember { mutableStateOf(CustomSessions.getDefaultId()) }
    var pendingInstall by remember { mutableStateOf<Distro?>(null) }
    var selectedLoginShell by remember { mutableStateOf(Settings.login_shell) }

    LaunchedEffect(Unit) {
        com.rk.terminal.root.SheveryManager.detectManager(context)
        com.rk.terminal.root.SheveryManager.refresh()
    }

    fun selectDistro(distro: Distro) {
        defaultIsCustom = false
        Settings.default_is_custom = false
        selectedWorkingMode = distro.workingMode
        Settings.working_Mode = distro.workingMode
    }

    val pending = pendingInstall
    if (pending != null) {
        DistroSetupScreen(
            modifier = modifier,
            distro = pending,
            onCancel = { pendingInstall = null },
            onComplete = {
                pendingInstall = null
                selectDistro(pending)
                toast("${pending.displayName} installed")
            }
        )
        return
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
        return
    }

    PreferenceLayout(
        label = stringResource(strings.settings),
        modifier = modifier,
        actions = {
            IconButton(onClick = {
                searchQuery = if (searchQuery.isBlank()) " " else ""
            }) {
                Icon(Icons.Filled.Search, contentDescription = "Search settings")
            }
        },
        onBack = { navController.popBackStack() },
        bottomBar = {
            if (searchQuery.isNotBlank()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    singleLine = true,
                    placeholder = { Text("Search settings") },
                    label = { Text("Filter settings") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search settings") },
                    trailingIcon = {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    ) {
        SettingsSection(stringResource(strings.default_working_mode), expanded = query.isNotBlank()) {
            if (matches("default", "working mode", "distro", "alpine", "wolfi", "debian", "void", "android", "session")) {
            Distro.entries.forEach { distro ->
                WorkingModeOption(
                    title = distro.displayName,
                    description = stringResource(distro.descriptionRes()),
                    selected = !defaultIsCustom && selectedWorkingMode == distro.workingMode
                ) {
                    if (Rootfs.isDistroInstalled(context, distro)) {
                        selectDistro(distro)
                    } else {
                        pendingInstall = distro
                    }
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
        }

        SettingsSection("Execution Mode", expanded = query.isNotBlank()) {
            if (matches("execution", "chroot", "proot", "mode")) {
            ExecModeOption("Chroot", "Requires root, faster, real bind mounts", ExecMode.CHROOT, selectedExecMode) {
                selectedExecMode = it
                Rootfs.setExecMode(it)
            }
            ExecModeOption(
                "Chroot (Shevery)",
                when {
                    com.rk.terminal.root.SheveryManager.hasFullRootAccess ->
                        "Root via Shevery manager, real bind mounts"
                    com.rk.terminal.root.SheveryManager.permissionGranted.value ->
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
        }

        SettingsSection("Root access (Shevery / Shizuku)", expanded = query.isNotBlank()) {
            if (matches("root", "shevery", "shizuku", "permission", "elevated")) {
            val mgrInstalled = com.rk.terminal.root.SheveryManager.isManagerInstalled()
            val granted = com.rk.terminal.root.SheveryManager.permissionGranted.value
            val fullRoot = com.rk.terminal.root.SheveryManager.hasFullRootAccess
            SettingsCard(
                title = { Text(com.rk.terminal.root.SheveryManager.statusLine()) },
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
                    com.rk.terminal.root.SheveryManager.detectManager(context)
                    com.rk.terminal.root.SheveryManager.refresh()
                }
            )
            if (!granted) {
                SettingsCard(
                    title = { Text("Grant manager permission") },
                    description = { Text("Ask ${com.rk.terminal.root.SheveryManager.managerLabel} for full access") },
                    onClick = { com.rk.terminal.root.SheveryManager.ensurePermission(context) }
                )
            }
            SettingsCard(
                title = {
                    Text(
                        if (mgrInstalled) "Open ${com.rk.terminal.root.SheveryManager.managerLabel} manager"
                        else "Get Shevery manager"
                    )
                },
                description = {
                    Text(
                        if (mgrInstalled) "Start the server and allow this app"
                        else "Required for root access and chroot"
                    )
                },
                onClick = { com.rk.terminal.root.SheveryManager.openManager(context) }
            )
            SettingsToggle(
                label = stringResource(strings.use_shizuku),
                description = stringResource(strings.use_shizuku_desc),
                showSwitch = true,
                default = Settings.auto_rish,
                sideEffect = { Settings.auto_rish = it }
            )
            }
        }

        SettingsSection(stringResource(strings.screen_and_power), expanded = query.isNotBlank()) {
            if (matches("screen", "power", "wake", "lock")) {
            SettingsToggle(
                label = stringResource(strings.keep_screen_on),
                description = stringResource(strings.keep_screen_on_desc),
                showSwitch = true,
                default = keepScreenOn,
                sideEffect = {
                    keepScreenOn = it
                    SettingsRepository.setKeepScreenOn(it)
                }
            )
            SettingsToggle(
                label = stringResource(strings.wake_lock),
                description = stringResource(strings.wake_lock_desc),
                showSwitch = true,
                default = wakeLock,
                sideEffect = {
                    wakeLock = it
                    SettingsRepository.setWakeLock(it)
                }
            )
            }
        }

        SettingsSection(stringResource(strings.distro_updates), expanded = query.isNotBlank()) {
            if (matches("distro", "update", "release", "version", "download")) {
            Distro.entries.filter { !it.isBundled }.forEach { distro ->
                DistroUpdateSection(distro = distro, onDownload = { pendingInstall = it })
            }
            }
        }

        if (matches("github", "repository", "token", "account")) {
            SettingsSection("GitHub", expanded = query.isNotBlank()) {
                GitHubSettingsSection(navController = navController, mainActivity = mainActivity)
            }
        }

        if (query.isBlank() || matches("customization", "theme", "background", "font", "color", "appearance")) {
            SettingsSection(stringResource(strings.customizations), expanded = query.isNotBlank()) {
                SettingsCard(
                    title = { Text(stringResource(strings.customizations)) },
                    onClick = { navController.navigate(MainActivityRoutes.Customization.route) },
                    endWidget = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = "Open customizations",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                )
            }
        }

        if (matches("sftp", "ssh", "file transfer", "server")) {
            SettingsSection("SFTP", expanded = query.isNotBlank()) {
                SftpSettingsSection(mainActivity = mainActivity)
            }
        }

        SettingsSection("Login shell", expanded = query.isNotBlank()) {
            if (matches("login", "shell", "bash", "ash", "sh")) {
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
        }

        SettingsSection(stringResource(strings.input_mode), expanded = query.isNotBlank()) {
            if (matches("input", "keyboard", "password", "text")) {
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
        }

        SettingsSection(stringResource(strings.voice_input), expanded = query.isNotBlank()) {
            if (matches("voice", "speech", "microphone", "mic", "language", "dictation")) {
            SettingsCard(
                title = { Text("Voice typing") },
                description = { Text("Use Android speech recognition from the terminal mic button. Microphone permission is requested only when you tap it.") },
                onClick = { }
            )
            run {
                InputModeOption(
                    stringResource(strings.voice_keyboard_classic),
                    stringResource(strings.voice_keyboard_classic_desc),
                    0,
                    voiceKeyboardMode
                ) {
                    voiceKeyboardMode = it
                    SettingsRepository.setKeyboardInputMode(it)
                }
                InputModeOption(
                    stringResource(strings.voice_keyboard_friendly),
                    stringResource(strings.voice_keyboard_friendly_desc),
                    1,
                    voiceKeyboardMode
                ) {
                    voiceKeyboardMode = it
                    SettingsRepository.setKeyboardInputMode(it)
                }
                SettingsToggle(
                    label = stringResource(strings.voice_preview),
                    description = stringResource(strings.voice_preview_desc),
                    showSwitch = true,
                    default = voicePreview,
                    sideEffect = {
                        voicePreview = it
                        SettingsRepository.setVoicePreview(it)
                    }
                )
                SettingsToggle(
                    label = stringResource(strings.voice_trailing_space),
                    description = stringResource(strings.voice_trailing_space_desc),
                    showSwitch = true,
                    default = voiceTrailingSpace,
                    sideEffect = {
                        voiceTrailingSpace = it
                        SettingsRepository.setVoiceTrailingSpace(it)
                    }
                )
                PreferenceTemplate(
                    title = { Text(stringResource(strings.voice_language)) },
                    description = {
                        OutlinedTextField(value = voiceLanguage, onValueChange = {
                            voiceLanguage = it
                            SettingsRepository.setVoiceLanguage(it)
                        }, singleLine = true)
                    }
                )
            }
            }
        }

        SettingsSection("Custom Sessions", expanded = query.isNotBlank()) {
            if (matches("custom", "session", "shell", "command")) {
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
        }

        SettingsSection("Storage permissions", expanded = query.isNotBlank()) {
            if (matches("storage", "permission", "files", "access")) {
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
    }
}

/** One distro's version row: shows what is installed and offers the update. */
@Composable
private fun DistroUpdateSection(distro: Distro, onDownload: (Distro) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Read fresh on every composition so a freshly installed rootfs shows
    // its new version without a restart.
    val installedVersion = Settings.getRootfsVersion(distro)
    var latestTag by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val isInstalled = Rootfs.isDistroInstalled(context, distro)

    SettingsCard(
        title = { Text(distro.displayName) },
        description = {
            Text(
                when {
                    latestTag != null && (installedVersion.isBlank() || installedVersion != latestTag) ->
                        "Update available: $latestTag"
                    message != null -> message!!
                    !isInstalled -> "Not installed"
                    installedVersion.isBlank() -> "Installed — tap to check for updates"
                    else -> "Installed: $installedVersion — tap to check for updates"
                }
            )
        },
        onClick = {
            if (checking) return@SettingsCard
            checking = true
            message = null
            scope.launch(Dispatchers.IO) {
                try {
                    val (tag, _) = DistroRepo.fetchLatest(distro)
                    withContext(Dispatchers.Main) {
                        checking = false
                        latestTag = tag
                        message = if (installedVersion.isBlank() || installedVersion != tag) {
                            "Update available: $tag"
                        } else {
                            "Up to date ($tag)"
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        checking = false
                        message = "Check failed: ${e.message}"
                    }
                }
            }
        },
        isEnabled = !checking
    )

    val tag = latestTag
    if (isInstalled && tag != null && (installedVersion.isBlank() || installedVersion != tag)) {
        SettingsCard(
            title = { Text(stringResource(strings.download_update, tag)) },
            description = { Text("Replaces system files, keeps /root home. Restart ${distro.displayName} sessions after.") },
            onClick = { onDownload(distro) }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    expanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var open by remember(title) { mutableStateOf(expanded) }
    LaunchedEffect(expanded) { if (expanded) open = true }
    PreferenceGroup(heading = title) {
        TextButton(
            onClick = { open = !open },
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "$title, ${if (open) "expanded" else "collapsed"}"
            }
        ) {
            Text(title, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ExpandMore, contentDescription = null)
        }
        if (open) content()
    }
    Spacer(Modifier.height(8.dp))
}

@StringRes
private fun Distro.descriptionRes(): Int = when (this) {
    Distro.ALPINE -> strings.alpine_desc
    Distro.WOLFI -> strings.wolfi_desc
    Distro.DEBIAN -> strings.debian_desc
    Distro.VOID -> strings.void_desc
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
