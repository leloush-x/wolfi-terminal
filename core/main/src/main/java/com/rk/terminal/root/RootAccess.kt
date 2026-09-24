package com.rk.terminal.root

import java.io.File

private val suPaths = listOf(
    "/system/bin/su",
    "/sbin/su",
    "/system/xbin/su",
    "/su/bin/su"
)

/**
 * Best-effort check that a usable `su` binary is on disk. SELinux can still
 * block an otherwise visible binary, so callers treat this as a hint and the
 * init script re-checks for real before it attempts a mount.
 */
fun isSuVisible(): Boolean = suPaths.any { File(it).canExecute() }

/**
 * Whether `su` actually grants root: runs `su -c id` and looks for uid=0.
 * Spawns a process and blocks on it, so call this off the main thread.
 */
fun hasRootAccess(): Boolean {
    if (suPaths.none { File(it).exists() }) return false
    return try {
        val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exited = process.waitFor()
        exited == 0 && output.contains("uid=0")
    } catch (e: Exception) {
        false
    }
}
