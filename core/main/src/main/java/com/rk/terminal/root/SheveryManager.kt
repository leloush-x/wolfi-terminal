package com.rk.terminal.root

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import com.rk.libcommons.toast
import rikka.shizuku.Shizuku

/**
 * Connection layer for the Shevery / Shizuku privilege manager.
 *
 * Shevery (com.hamondev.shevery) is a Shizuku-compatible manager: it speaks the
 * same client API (dev.rikka.shizuku), so one integration covers both managers
 * (plus Sui, which the provider initializes automatically).
 *
 * What this gives the app:
 * - Detect which manager is installed (Shevery preferred, Shizuku fallback).
 * - Track binder lifecycle (alive / dead) without blocking session creation.
 * - Request / observe the manager permission ("full access" grant in the manager).
 * - Distinguish a root daemon (uid 0, can mount + chroot) from an ADB daemon
 *   (uid 2000, cannot chroot).
 *
 * All binder calls are guarded: if no manager is installed/running every query
 * simply reports "unavailable" and sessions fall back to proot / local-su
 * chroot. Nothing here may throw into callers.
 */
object SheveryManager {

    const val SHEVERY_PACKAGE = "com.hamondev.shevery"
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val SHEVERY_GITHUB = "https://github.com/HmnDev-Tech/shevery"

    /** Request code for [Shizuku.requestPermission]. */
    const val PERMISSION_REQUEST_CODE = 1001

    /** True once the manager binder has been delivered to this process. */
    var binderAlive = mutableStateOf(false)
        private set

    /** True once the user granted this app access inside the manager. */
    var permissionGranted = mutableStateOf(false)
        private set

    /**
     * Daemon uid: 0 = root (full access, can mount/chroot),
     * 2000 = ADB/shell (limited, cannot chroot), null = unknown.
     */
    var serverUid = mutableStateOf<Int?>(null)
        private set

    /** Detected manager package, Shevery preferred. Null when none installed. */
    var managerPackage = mutableStateOf<String?>(null)
        private set

    /** Full root access through the manager: binder + grant + root daemon. */
    val hasFullRootAccess: Boolean
        get() = binderAlive.value && permissionGranted.value && serverUid.value == 0

    /** Any usable elevated daemon (root or ADB), e.g. for an elevated shell. */
    val hasElevatedAccess: Boolean
        get() = binderAlive.value && permissionGranted.value && serverUid.value != null

    val managerLabel: String
        get() = when (managerPackage.value) {
            SHEVERY_PACKAGE -> "Shevery"
            SHIZUKU_PACKAGE -> "Shizuku"
            else -> "Shevery / Shizuku"
        }

    fun statusLine(): String = when {
        managerPackage.value == null ->
            "No manager installed — install Shevery for root access"
        !binderAlive.value ->
            "${managerLabel} installed — start it, then retry"
        !permissionGranted.value ->
            "${managerLabel} running — permission not granted yet"
        serverUid.value == 0 ->
            "${managerLabel} connected — root access (uid 0)"
        serverUid.value != null ->
            "${managerLabel} connected — ADB mode (uid ${serverUid.value}, no chroot)"
        else ->
            "${managerLabel} connected — checking…"
    }

    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        detectManager(context.applicationContext)
        try {
            Shizuku.addBinderReceivedListener { refresh() }
        } catch (_: Exception) {
        }
        try {
            Shizuku.addBinderDeadListener {
                binderAlive.value = false
                permissionGranted.value = false
                serverUid.value = null
            }
        } catch (_: Exception) {
        }
        try {
            Shizuku.addRequestPermissionResultListener { _, grantResult ->
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    refresh()
                } else {
                    permissionGranted.value = false
                }
            }
        } catch (_: Exception) {
        }
        refresh()
    }

    /** Re-read binder / permission / uid state. Safe to call from UI thread. */
    fun refresh() {
        try {
            if (Shizuku.isPreV11()) {
                binderAlive.value = false
                permissionGranted.value = false
                serverUid.value = null
                return
            }
        } catch (_: Exception) {
            binderAlive.value = false
            permissionGranted.value = false
            serverUid.value = null
            return
        }
        try {
            binderAlive.value = true
            permissionGranted.value =
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            serverUid.value = runCatching { Shizuku.getUid() }.getOrNull()
            if (serverUid.value == null) {
                binderAlive.value = false
                permissionGranted.value = false
            }
        } catch (_: IllegalStateException) {
            // Binder not delivered yet (no manager running).
            binderAlive.value = false
            permissionGranted.value = false
            serverUid.value = null
        } catch (_: Exception) {
            binderAlive.value = false
            permissionGranted.value = false
            serverUid.value = null
        }
    }

    /**
     * Ask the manager for access. Returns true only if already granted.
     * Otherwise the manager shows its grant dialog and the result arrives via
     * the permission listener (UI observes [permissionGranted]).
     */
    fun ensurePermission(context: Context): Boolean {
        refresh()
        if (permissionGranted.value) return true
        if (!binderAlive.value) {
            detectManager(context.applicationContext)
            toast("Start ${managerLabel} first, then grant access")
            openManager(context)
            return false
        }
        return try {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            false
        } catch (_: Exception) {
            false
        }
    }

    fun detectManager(context: Context) {
        managerPackage.value = when {
            isPackageInstalled(context, SHEVERY_PACKAGE) -> SHEVERY_PACKAGE
            isPackageInstalled(context, SHIZUKU_PACKAGE) -> SHIZUKU_PACKAGE
            else -> null
        }
    }

    fun isManagerInstalled(): Boolean = managerPackage.value != null

    /** Open the manager app so the user can start it / grant access. */
    fun openManager(context: Context) {
        val pkg = managerPackage.value
        if (pkg != null) {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(launch) }.onSuccess { return }
            }
            // Installed but no launcher entry: open its system settings page.
            runCatching {
                val settings = Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:$pkg".toUri()
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(settings)
            }.onSuccess { return }
        }
        runCatching {
            val web = Intent(
                Intent.ACTION_VIEW,
                SHEVERY_GITHUB.toUri()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(web)
        }.onFailure {
            toast("Install Shevery: $SHEVERY_GITHUB")
        }
    }

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(context: Context, pkg: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
