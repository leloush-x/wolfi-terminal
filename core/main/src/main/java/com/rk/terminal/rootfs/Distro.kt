package com.rk.terminal.rootfs

import com.rk.terminal.core.WorkingMode

/**
 * The Linux distributions a session can run.
 *
 * Alpine ships inside the APK; the others are downloaded from the
 * wolfi-os-rootfs releases. The id doubles as the on-disk directory name and
 * as the rootfs tarball name, so the two can never drift apart.
 */
enum class Distro(
    val id: String,
    val displayName: String,
    val workingMode: Int,
    /** SharedPreferences key holding the installed rootfs version tag. */
    val versionKey: String
) {
    ALPINE("alpine", "Alpine", WorkingMode.ALPINE, "alpine_version"),
    WOLFI("wolfi", "Wolfi", WorkingMode.WOLFI, "wolfi_version"),
    DEBIAN("debian", "Debian", WorkingMode.DEBIAN, "debian_version"),
    VOID("void", "Void", WorkingMode.VOID, "void_version");

    /** Tarball name inside the app's files dir. */
    val tarName: String get() = "$id.tar.gz"

    /** Alpine is the only distro bundled in the APK. */
    val isBundled: Boolean get() = this == ALPINE

    /**
     * Init scripts that belong to this distro. They are dropped when the
     * rootfs is replaced, so the next session re-copies the current ones
     * from the app assets.
     */
    val initScripts: List<String>
        get() = if (isBundled) {
            listOf("init-host", "init-host-chroot", "init")
        } else {
            listOf("init-$id-host", "init-$id-host-chroot", "init-$id")
        }

    companion object {
        /** The distro a working mode refers to, or null for the Android host shell. */
        fun fromWorkingMode(mode: Int): Distro? = entries.firstOrNull { it.workingMode == mode }
    }
}
