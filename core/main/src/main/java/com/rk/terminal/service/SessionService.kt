package com.rk.terminal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import com.rk.resources.drawables
import com.rk.settings.SettingsRepository
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.screens.terminal.CustomSessions
import com.rk.terminal.ui.screens.terminal.MkSession
import com.rk.terminal.ui.screens.terminal.PendingCommand
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class SessionService : Service() {
    private val sessions = hashMapOf<String, TerminalSession>()
    val sessionList = mutableStateMapOf<String, Int>()
    val sessionOrder = mutableStateListOf<String>()
    private val initialMode = CustomSessions.resolveDefaultSession()
    var currentSession = mutableStateOf(Pair("main", initialMode.first))
    var currentCustomSession = initialMode.second

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    inner class SessionBinder : Binder() {
        fun getService(): SessionService = this@SessionService

        fun terminateAllSessions() {
            sessions.values.forEach { it.finishIfRunning() }
            sessions.clear()
            sessionList.clear()
            sessionOrder.clear()
            updateNotification()
        }

        fun createSession(
            id: String,
            client: TerminalSessionClient,
            workingMode: Int,
            pendingCommand: PendingCommand? = null
        ): TerminalSession {
            return MkSession.createSession(
                context = this@SessionService,
                sessionClient = client,
                sessionId = id,
                workingMode = workingMode,
                pendingCommand = pendingCommand
            ).also {
                sessions[id] = it
                sessionList[id] = workingMode
                if (!sessionOrder.contains(id)) {
                    sessionOrder.add(id)
                }
                updateNotification()
            }
        }

        fun getSession(id: String): TerminalSession? = sessions[id]

        fun renameSession(oldId: String, newId: String): Boolean {
            val trimmed = newId.trim()
            if (trimmed.isEmpty()) return false
            if (trimmed == oldId) return true
            if (sessions.containsKey(trimmed)) return false

            val session = sessions.remove(oldId) ?: return false
            val mode = sessionList.remove(oldId) ?: com.rk.settings.Settings.working_Mode
            sessions[trimmed] = session
            sessionList[trimmed] = mode

            val idx = sessionOrder.indexOf(oldId)
            if (idx != -1) {
                sessionOrder[idx] = trimmed
            } else {
                sessionOrder.add(trimmed)
            }

            if (currentSession.value.first == oldId) {
                currentSession.value = Pair(trimmed, mode)
            }
            return true
        }

        fun moveSession(fromIndex: Int, toIndex: Int) {
            if (fromIndex in sessionOrder.indices && toIndex in sessionOrder.indices && fromIndex != toIndex) {
                val item = sessionOrder.removeAt(fromIndex)
                sessionOrder.add(toIndex, item)
            }
        }

        fun sortSessions(ascending: Boolean = true) {
            val sorted = if (ascending) sessionOrder.sorted() else sessionOrder.sortedDescending()
            sessionOrder.clear()
            sessionOrder.addAll(sorted)
        }

        fun terminateSession(id: String) {
            sessions[id]?.apply {
                if (emulator != null) {
                    finishIfRunning()
                }
            }
            sessions.remove(id)
            sessionList.remove(id)
            sessionOrder.remove(id)
            if (sessions.isEmpty()) {
                stopSelf()
            } else {
                updateNotification()
            }
        }
    }

    private val binder = SessionBinder()
    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        releaseWakeLock()
        serviceScope.cancel()
        sessions.values.forEach { it.finishIfRunning() }
        super.onDestroy()
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        serviceScope.launch {
            SettingsRepository.wakeLock.collect { enabled ->
                if (enabled) acquireWakeLock() else releaseWakeLock()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_EXIT -> {
                sessions.values.forEach { it.finishIfRunning() }
                stopSelf()
            }
            ACTION_TOGGLE_WAKE_LOCK -> {
                SettingsRepository.setWakeLock(!SettingsRepository.isWakeLock())
                updateNotification()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Holds a partial wake lock (and a high-performance wifi lock) so the
     * sessions keep running with the screen off, the way `termux-wake-lock`
     * does. The screen itself is kept on separately by the terminal view.
     */
    @Suppress("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$WAKE_LOCK_TAG:service").also {
            it.acquire()
        }
        runCatching {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wm.createWifiLock(mode, WAKE_LOCK_TAG).also { it.acquire() }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val exitIntent = Intent(this, SessionService::class.java).apply {
            action = ACTION_EXIT
        }
        val exitPendingIntent = PendingIntent.getService(
            this, 1, exitIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val wakeLocked = SettingsRepository.isWakeLock()
        val wakeIntent = Intent(this, SessionService::class.java).apply {
            action = ACTION_TOGGLE_WAKE_LOCK
        }
        val wakePendingIntent = PendingIntent.getService(
            this, 2, wakeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wolfi Terminal")
            .setContentText(getNotificationContentText())
            .setSmallIcon(drawables.terminal)
            .setContentIntent(pendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    null,
                    if (wakeLocked) "WAKE ON" else "WAKE OFF",
                    wakePendingIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    null,
                    "EXIT",
                    exitPendingIntent
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    private val CHANNEL_ID = "session_service_channel"

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Session Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification for Terminal Service"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateNotification() {
        val notification = createNotification()
        notificationManager.notify(1, notification)
    }

    private fun getNotificationContentText(): String {
        val count = sessions.size
        return if (count == 1) "1 session running" else "$count sessions running"
    }

    companion object {
        private const val WAKE_LOCK_TAG = "wolfiterminal"
        const val ACTION_EXIT = "com.rk.terminal.service.ACTION_EXIT"
        const val ACTION_TOGGLE_WAKE_LOCK = "com.rk.terminal.service.ACTION_TOGGLE_WAKE_LOCK"
    }
}
