package com.rk.terminal.core

/**
 * Which operating system a session runs.
 *
 * The values are persisted in SharedPreferences under "workingMode", so they
 * must never be reordered or reused.
 */
object WorkingMode {
    /** Alpine, bundled with the app. */
    const val ALPINE = 0

    /** The phone's own shell, no rootfs involved. */
    const val ANDROID = 1

    /** Wolfi, downloaded on demand. */
    const val WOLFI = 2

    /** Debian, downloaded on demand. */
    const val DEBIAN = 3

    /** Void, downloaded on demand. */
    const val VOID = 4
}
