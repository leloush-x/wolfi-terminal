package com.rk.terminal.rootfs

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.rk.libcommons.child
import com.rk.libcommons.localBinDir
import com.rk.libcommons.localDir
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
        isInstalled.value = isDistroInstalled(context, Distro.ALPINE)
    }

    /**
     * True when the distro's system files are on disk — either already
     * extracted, or still packed in its tarball waiting for the first session.
     */
    fun isDistroInstalled(context: Context, distro: Distro): Boolean {
        val dir = context.localDir().child(distro.id)
        val extracted = dir.exists() && dir.list()?.any { it != "root" && it != "tmp" } == true
        return extracted || archive(context, distro).exists()
    }

    fun archive(context: Context, distro: Distro): File = context.filesDir.child(distro.tarName)

    /**
     * Wipes the extracted system files (keeps /root home and tmp) plus cached
     * init scripts, so the next session re-extracts fresh from the tarball.
     * User-installed packages under usr/local and opt are backed up into
     * /root/.preserved-* so the init script can restore them after extraction.
     * Call on a background thread after downloading a rootfs update.
     */
    fun clearSystem(context: Context, distro: Distro) {
        val dir = context.localDir().child(distro.id)
        val root = dir.child("root")
        backupPreserved(dir.child("usr").child("local"), root.child(".preserved-usrlocal"))
        backupPreserved(dir.child("opt"), root.child(".preserved-opt"))
        dir.listFiles()?.forEach {
            if (it.name != "root" && it.name != "tmp") it.deleteRecursively()
        }
        distro.initScripts.forEach {
            context.localBinDir().child(it).takeIf { f -> f.exists() }?.delete()
        }
    }

    private fun backupPreserved(src: File, dst: File) {
        runCatching {
            if (!src.exists()) return
            if (dst.exists()) dst.deleteRecursively()
            dst.parentFile?.mkdirs()
            src.copyRecursively(dst, overwrite = true)
        }
    }
}
