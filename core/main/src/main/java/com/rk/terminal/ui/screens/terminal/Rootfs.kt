package com.rk.terminal.ui.screens.terminal

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.rk.libcommons.child
import com.rk.libcommons.localBinDir
import com.rk.libcommons.localDir
import com.rk.libcommons.wolfiDir
import com.rk.settings.Settings
import java.io.File

enum class ExecMode(val value: Int) {
    CHROOT(0),
    PROOT(1);

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
}
