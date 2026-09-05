package com.rk.terminal.ui.screens.terminal

import android.content.Context
import com.rk.libcommons.alpineDir
import com.rk.libcommons.alpineHomeDir
import com.rk.libcommons.child
import com.rk.libcommons.createFileIfNot
import com.rk.libcommons.localBinDir
import com.rk.libcommons.localDir
import com.rk.libcommons.localLibDir
import com.rk.libcommons.toast
import com.rk.libcommons.wolfiDir
import com.rk.libcommons.wolfiHomeDir
import com.rk.settings.Settings
import com.rk.terminal.App.Companion.getTempDir
import com.rk.terminal.BuildConfig
import com.rk.terminal.root.SheveryManager
import com.rk.terminal.ui.screens.settings.WorkingMode
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

object MkSession {
    private var warnedMissingBash = false
    private var warnedRishDenied = false

    /**
     * Locate the rish executable installed via the Shevery / Shizuku manager
     * ("Use in terminal apps"). Honors [Settings.rish_path]: absolute path is
     * used directly, a bare name is searched on PATH plus the app bin dir.
     * Null when rish is not set up.
     */
    fun resolveRish(context: Context): File? {
        val pref = Settings.rish_path.trim()
        val names = buildList {
            if (pref.isNotBlank()) add(pref)
            if (pref != "rish") add("rish")
        }
        for (name in names) {
            if (name.contains("/")) {
                File(name).takeIf { it.canExecute() }?.let { return it }
            } else {
                val pathDirs =
                    (System.getenv("PATH") ?: "/system/bin:/system/xbin").split(":")
                for (dir in pathDirs) {
                    File(dir, name).takeIf { it.canExecute() }?.let { return it }
                }
                context.localBinDir().child(name).takeIf { it.canExecute() }?.let { return it }
            }
        }
        return null
    }

    fun createSession(
        context: Context,
        sessionClient: TerminalSessionClient,
        sessionId: String,
        workingMode: Int,
        pendingCommand: PendingCommand? = null
    ): TerminalSession {
        with(context) {
            val envVariables = mapOf(
                "ANDROID_ART_ROOT" to System.getenv("ANDROID_ART_ROOT"),
                "ANDROID_DATA" to System.getenv("ANDROID_DATA"),
                "ANDROID_I18N_ROOT" to System.getenv("ANDROID_I18N_ROOT"),
                "ANDROID_ROOT" to System.getenv("ANDROID_ROOT"),
                "ANDROID_RUNTIME_ROOT" to System.getenv("ANDROID_RUNTIME_ROOT"),
                "ANDROID_TZDATA_ROOT" to System.getenv("ANDROID_TZDATA_ROOT"),
                "BOOTCLASSPATH" to System.getenv("BOOTCLASSPATH"),
                "DEX2OATBOOTCLASSPATH" to System.getenv("DEX2OATBOOTCLASSPATH"),
                "EXTERNAL_STORAGE" to System.getenv("EXTERNAL_STORAGE")
            )

            val workingDir = pendingCommand?.workingDir ?: if (workingMode == WorkingMode.WOLFI) {
                wolfiHomeDir().path
            } else {
                alpineHomeDir().path
            }

            val useChroot = Rootfs.execMode.value == ExecMode.CHROOT ||
                Rootfs.execMode.value == ExecMode.SHEVERY
            val wantShevery = Rootfs.execMode.value == ExecMode.SHEVERY

            // Elevated session via rish: the manager daemon (root) owns the
            // privileged side while the app keeps the pty, so the shell stays
            // fully interactive. Anything unavailable -> plain local shell.
            // Cached manager state only (no binder IPC on session start).
            val rishBin: File? = if (
                wantShevery ||
                (Settings.auto_rish && workingMode == WorkingMode.ANDROID)
            ) {
                resolveRish(this@with)
            } else {
                null
            }
            val sheveryChrootReady = wantShevery && rishBin != null &&
                SheveryManager.permissionGranted.value &&
                SheveryManager.serverUid.value == 0

            if (wantShevery && !sheveryChrootReady && pendingCommand == null &&
                (workingMode == WorkingMode.ALPINE || workingMode == WorkingMode.WOLFI)
            ) {
                when {
                    rishBin == null ->
                        toast("rish not found — in Shevery: 'Use in terminal apps', then retry")
                    !SheveryManager.permissionGranted.value ->
                        toast("Grant access in Shevery first (Settings → Root access)")
                    else ->
                        toast("Shevery must run as root (uid 0) for chroot")
                }
            }

            val loginShell = Settings.login_shell
            if (loginShell.isNotBlank() && loginShell.endsWith("bash") &&
                (workingMode == WorkingMode.ALPINE || workingMode == WorkingMode.WOLFI) &&
                !warnedMissingBash
            ) {
                val root = if (workingMode == WorkingMode.WOLFI) wolfiDir() else alpineDir()
                if (!root.child("bin/bash").exists()) {
                    warnedMissingBash = true
                    toast("bash not found — install it first: apk add bash")
                }
            }

            fun installAssetBin(name: String, asset: String) {
                // Always refresh: these are app-managed scripts, rewriting
                // propagates fixes (e.g. new rootfs handling) to existing installs.
                localBinDir().child(name).apply {
                    createFileIfNot()
                    assets.open(asset).bufferedReader().use { it.readText() }.let {
                        writeText(it)
                    }
                }
            }

            installAssetBin("init-host", "init-host.sh")
            installAssetBin("init-host-chroot", "init-host-chroot.sh")
            installAssetBin("init-wolfi-host", "init-wolfi-host.sh")
            installAssetBin("init-wolfi-host-chroot", "init-wolfi-host-chroot.sh")
            installAssetBin("init", "init.sh")
            installAssetBin("init-wolfi", "init-wolfi.sh")

            val initFile: File = localBinDir().child("init-host")
            val initChrootFile: File = localBinDir().child("init-host-chroot")
            val initWolfiFile: File = localBinDir().child("init-wolfi-host")
            val initWolfiChrootFile: File = localBinDir().child("init-wolfi-host-chroot")

            localBinDir().child("rm").apply {
                if (exists().not()) {
                    createFileIfNot()
                    assets.open("rm-wrapper.sh").bufferedReader().use { it.readText() }.let {
                        writeText(it)
                    }
                    setExecutable(true)
                }
            }

            val env = mutableListOf(
                "PATH=${System.getenv("PATH")}:/sbin:${localBinDir().absolutePath}",
                "HOME=/sdcard",
                "PUBLIC_HOME=${getExternalFilesDir(null)?.absolutePath}",
                "COLORTERM=truecolor",
                "TERM=xterm-256color",
                "LANG=C.UTF-8",
                "BIN=${localBinDir()}",
                "DEBUG=${BuildConfig.DEBUG}",
                "PREFIX=${filesDir.parentFile!!.path}",
                "LD_LIBRARY_PATH=${localLibDir().absolutePath}",
                "LINKER=${if (File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"}",
                "NATIVE_LIB_DIR=${applicationInfo.nativeLibraryDir}",
                "PKG=${packageName}",
                "RISH_APPLICATION_ID=${packageName}",
                "PKG_PATH=${applicationInfo.sourceDir}",
                "PROOT_TMP_DIR=${getTempDir(this).child(sessionId).also { if (it.exists().not()) it.mkdirs() }}",
                "TMPDIR=${getTempDir(this).absolutePath}",
                "PROOT_LOADER=${applicationInfo.nativeLibraryDir}/libloader.so",
                "PROOT=${applicationInfo.nativeLibraryDir}/libproot.so",
                "CHROOT=${if (File("/system/bin/chroot").exists()) "/system/bin/chroot" else "/system/xbin/chroot"}",
                "USE_CHROOT=${if (useChroot) "1" else "0"}",
                "LOGIN_SHELL=$loginShell",
            )

            val loader32 = "${applicationInfo.nativeLibraryDir}/libloader32.so"
            if (File(loader32).exists()) {
                env.add("PROOT_LOADER_32=$loader32")
            }

            env.addAll(envVariables.map { "${it.key}=${it.value}" })

            localDir().child("stat").apply {
                if (exists().not()) {
                    writeText(TerminalUtils.stat)
                }
            }

            localDir().child("vmstat").apply {
                if (exists().not()) {
                    writeText(TerminalUtils.vmstat)
                }
            }

            pendingCommand?.env?.let {
                env.addAll(it)
            }

            val args: Array<String>
            val shell: String
            var wrappingRish = false
            if (pendingCommand == null) {
                if (workingMode == WorkingMode.ALPINE) {
                    val targetInit = if (useChroot) initChrootFile else initFile
                    if (sheveryChrootReady) {
                        wrappingRish = true
                        shell = rishBin!!.absolutePath
                        args = arrayOf("-c", targetInit.absolutePath)
                    } else {
                        shell = "/system/bin/sh"
                        args = arrayOf("-c", targetInit.absolutePath)
                    }
                } else if (workingMode == WorkingMode.WOLFI) {
                    val targetInit = if (useChroot) initWolfiChrootFile else initWolfiFile
                    if (sheveryChrootReady) {
                        wrappingRish = true
                        shell = rishBin!!.absolutePath
                        args = arrayOf("-c", targetInit.absolutePath)
                    } else {
                        shell = "/system/bin/sh"
                        args = arrayOf("-c", targetInit.absolutePath)
                    }
                } else {
                    // ANDROID host shell, optionally elevated via rish.
                    if (rishBin != null && Settings.auto_rish &&
                        SheveryManager.hasElevatedAccess
                    ) {
                        wrappingRish = true
                        shell = rishBin.absolutePath
                        args = arrayOf()
                    } else {
                        if (Settings.auto_rish && rishBin != null && !warnedRishDenied &&
                            !SheveryManager.hasElevatedAccess
                        ) {
                            warnedRishDenied = true
                            toast("Shevery access missing — Android shell started unelevated")
                        }
                        shell = "/system/bin/sh"
                        args = arrayOf()
                    }
                }
            } else {
                args = pendingCommand.args
                shell = pendingCommand.shell
            }
            if (wrappingRish) {
                // Pass our env (PREFIX, BIN, ...) to the privileged shell.
                env.add("RISH_PRESERVE_ENV=1")
            }

            return TerminalSession(
                shell,
                workingDir,
                args,
                env.toTypedArray(),
                TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
                sessionClient,
            )
        }
    }

    fun buildCustomPendingCommand(context: Context, custom: CustomSession): PendingCommand {
        val scriptFile = File(custom.shellPath)
        val sysSh = File("/system/bin/sh")

        val shell: String
        val args: Array<String>

        if (sysSh.canExecute()) {
            shell = sysSh.absolutePath
            args = arrayOf("-c", scriptFile.absolutePath)
        } else {
            val proot = "${context.applicationInfo.nativeLibraryDir}/libproot.so"
            shell = proot
            args = arrayOf(
                "-r", "/",
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sdcard",
                "-0",
                "sh", scriptFile.absolutePath
            )
        }

        return PendingCommand(
            shell = shell,
            args = args,
            workingDir = scriptFile.parentFile?.absolutePath ?: "/sdcard/WolfiTerminal",
            env = null
        )
    }

    fun buildScriptPendingCommand(
        context: Context,
        script: File,
        workingMode: Int,
        custom: CustomSession? = null
    ): PendingCommand {
        val workingDir = script.parentFile?.absolutePath
        return if (custom != null) {
            val sysSh = File("/system/bin/sh")
            if (sysSh.canExecute()) {
                PendingCommand(
                    shell = sysSh.absolutePath,
                    args = arrayOf("-c", "'${custom.shellPath}' '${script.absolutePath}'"),
                    workingDir = workingDir,
                    env = null
                )
            } else {
                val proot = "${context.applicationInfo.nativeLibraryDir}/libproot.so"
                PendingCommand(
                    shell = proot,
                    args = arrayOf(
                        "-r", "/",
                        "-b", "/dev",
                        "-b", "/proc",
                        "-b", "/sdcard",
                        "-0",
                        "sh", custom.shellPath, script.absolutePath
                    ),
                    workingDir = workingDir,
                    env = null
                )
            }
        } else if (workingMode == WorkingMode.ALPINE || workingMode == WorkingMode.WOLFI) {
            val execMode = Rootfs.execMode.value
            val useChroot = execMode == ExecMode.CHROOT || execMode == ExecMode.SHEVERY
            val binName = when {
                workingMode == WorkingMode.WOLFI && useChroot -> "init-wolfi-host-chroot"
                workingMode == WorkingMode.WOLFI -> "init-wolfi-host"
                useChroot -> "init-host-chroot"
                else -> "init-host"
            }
            val initFile = context.localBinDir().child(binName)
            // One-shot script in "Chroot (Shevery)" mode: run the same init
            // through rish when the manager grants root; otherwise local su.
            val rishBin = if (execMode == ExecMode.SHEVERY) resolveRish(context) else null
            if (rishBin != null && SheveryManager.permissionGranted.value &&
                SheveryManager.serverUid.value == 0
            ) {
                PendingCommand(
                    shell = rishBin.absolutePath,
                    args = arrayOf("-c", initFile.absolutePath, "sh", script.absolutePath),
                    workingDir = workingDir,
                    env = listOf("RISH_PRESERVE_ENV=1")
                )
            } else {
                PendingCommand(
                    shell = "/system/bin/sh",
                    args = arrayOf("-c",initFile.absolutePath,"sh",script.absolutePath),
                    workingDir = workingDir,
                    env = null
                )
            }
        } else {
            PendingCommand(
                shell = "/system/bin/sh",
                args = arrayOf("-c", script.absolutePath),
                workingDir = workingDir,
                env = null
            )
        }
    }
}

data class PendingCommand(
    val shell: String,
    val args: Array<String>,
    val workingDir: String?,
    val env: List<String>?
)
