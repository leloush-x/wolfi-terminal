package com.rk.terminal.ui.screens.terminal

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.rk.libcommons.child
import com.rk.libcommons.debianDir
import com.rk.libcommons.localBinDir
import com.rk.libcommons.localDir
import com.rk.libcommons.voidDir
import com.rk.libcommons.wolfiDir
import com.rk.settings.Settings
import java.io.File

enum class ExecMode(val value: Int) {
    CHROOT(0),
    PROOT(1),
    /**
     * chroot with root from the Shevery / Shizuku manager: the session boots
     * through rish (elevated daemon) instead of local su. Requires the manager
     * granted + running as root (uid 0). Falls back to plain chroot/proot
     * behavior when unavailable — sessions never fail because of this.
     */
    SHEVERY(2);

    companion object {
        fun fromInt(v: Int): ExecMode? = entries.firstOrNull { it.value == v }
    }
}

object Rootfs {
    var isInstalled = mutableStateOf(false)
    var execMode = mutableStateOf(ExecMode.fromInt(Settings.exec_mode))

    fun setExecMode(mode: ExecMode) {
        execMode.value = mode
        Settings.exec_mode = mode.value
    }

    fun checkInstallation(context: Context) {
        isInstalled.value = isRootfsInstalled(context)
    }

    fun isRootfsInstalled(context: Context): Boolean {
        val alpineDir = context.localDir().child("alpine")
        val isExtracted = alpineDir.exists() && (alpineDir.list()?.any { it != "root" && it != "tmp" } == true)
        val isArchivePresent = context.filesDir.child("alpine.tar.gz").exists()
        return isExtracted || isArchivePresent
    }

    fun isWolfiRootfsInstalled(context: Context): Boolean {
        val dir: File = context.wolfiDir()
        val isExtracted = dir.exists() && (dir.list()?.any { it != "root" && it != "tmp" } == true)
        val isArchivePresent = context.filesDir.child("wolfi.tar.gz").exists()
        return isExtracted || isArchivePresent
    }

    fun wolfiArchive(context: Context): File = context.filesDir.child("wolfi.tar.gz")

    fun isDebianRootfsInstalled(context: Context): Boolean {
        val dir: File = context.debianDir()
        val isExtracted = dir.exists() && (dir.list()?.any { it != "root" && it != "tmp" } == true)
        val isArchivePresent = context.filesDir.child("debian.tar.gz").exists()
        return isExtracted || isArchivePresent
    }

    fun isVoidRootfsInstalled(context: Context): Boolean {
        val dir: File = context.voidDir()
        val isExtracted = dir.exists() && (dir.list()?.any { it != "root" && it != "tmp" } == true)
        val isArchivePresent = context.filesDir.child("void.tar.gz").exists()
        return isExtracted || isArchivePresent
    }

    fun debianArchive(context: Context): File = context.filesDir.child("debian.tar.gz")
    fun voidArchive(context: Context): File = context.filesDir.child("void.tar.gz")

    /**
     * Wipes the extracted Wolfi system (keeps /root home and tmp) plus cached
     * init scripts, so the next session re-extracts fresh from wolfi.tar.gz.
     * Call on a background thread after downloading a rootfs update.
     */
    fun clearWolfiSystem(context: Context) {
        context.wolfiDir().listFiles()?.forEach {
            if (it.name != "root" && it.name != "tmp") it.deleteRecursively()
        }
        arrayOf("init-wolfi-host", "init-wolfi-host-chroot", "init-wolfi").forEach {
            context.localBinDir().child(it).takeIf { f -> f.exists() }?.delete()
        }
    }

    fun clearDebianSystem(context: Context) {
        context.debianDir().listFiles()?.forEach {
            if (it.name != "root" && it.name != "tmp") it.deleteRecursively()
        }
        arrayOf("init-debian-host", "init-debian-host-chroot", "init-debian").forEach {
            context.localBinDir().child(it).takeIf { f -> f.exists() }?.delete()
        }
    }

    fun clearVoidSystem(context: Context) {
        context.voidDir().listFiles()?.forEach {
            if (it.name != "root" && it.name != "tmp") it.deleteRecursively()
        }
        arrayOf("init-void-host", "init-void-host-chroot", "init-void").forEach {
            context.localBinDir().child(it).takeIf { f -> f.exists() }?.delete()
        }
    }
}
