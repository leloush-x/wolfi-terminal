package com.rk.terminal

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.StrictMode
import com.github.anrwatchdog.ANRWatchDog
import com.rk.crashhandler.CrashHandler
import com.rk.crashhandler.logErrorOrExit
import com.rk.libcommons.application
import com.rk.resources.Res
import com.rk.terminal.root.SheveryManager
import com.rk.terminal.ui.screens.terminal.TerminalUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

class App : Application() {

    companion object {
        fun getTempDir(context: Context): File {
            val tmp = File(context.cacheDir, "tmp")
            if (!tmp.exists()) {
                tmp.mkdir()
            }
            return tmp
        }
    }

    override fun onCreate() {
        super.onCreate()
        application = this
        Res.application = this
        CrashHandler.previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)
        com.rk.settings.SettingsRepository.init(this)
        TerminalUtils.init(this)
        SheveryManager.init(this)

        val crashLog = File(filesDir, "crash.log")
        if (crashLog.isFile && crashLog.length() > 0L) {
            android.util.Log.e("CrashHandler", "Previous crash report:\n${runCatching { crashLog.readText() }.getOrDefault("Unable to read crash report")}")
            crashLog.delete()
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            getTempDir(this@App).apply {
                if (exists() && listFiles().isNullOrEmpty().not()) {
                    deleteRecursively()
                }
            }
        }

        ANRWatchDog()
            .setReportMainThreadOnly()
            .setANRListener { error -> logErrorOrExit(error) }
            .start()


        if (BuildConfig.DEBUG) {
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder().apply {
                    detectAll()
                    penaltyLog()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        penaltyListener(Executors.newSingleThreadExecutor()) { violation ->
                            violation.printStackTrace()
                        }
                    }
                }.build()
            )
        }
    }
}
