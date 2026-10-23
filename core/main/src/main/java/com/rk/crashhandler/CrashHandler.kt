package com.rk.crashhandler

import android.os.Build
import android.util.Log
import com.rk.libcommons.application
import java.io.PrintWriter
import java.io.StringWriter

object CrashHandler : Thread.UncaughtExceptionHandler {

    @Volatile var previous: Thread.UncaughtExceptionHandler? = null

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        logErrorOrExit(ex, thread.name)
        previous?.uncaughtException(thread, ex)
    }

}

fun logErrorOrExit(throwable: Throwable, threadName: String = Thread.currentThread().name) {
    runCatching {
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val report = buildString {
            appendLine("Thread: $threadName")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("App: ${com.rk.terminal.BuildConfig.VERSION_NAME} (${com.rk.terminal.BuildConfig.VERSION_CODE})")
            appendLine("Time: ${System.currentTimeMillis()}")
            appendLine(trace)
            appendLine("\n---\n")
        }
        application?.openFileOutput("crash.log", android.content.Context.MODE_APPEND)?.bufferedWriter()?.use {
            it.write(report)
        }
    }.onFailure { Log.e("CrashHandler", "Could not persist crash report", it) }
}
