package com.rk.terminal.ui.screens.settings

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * Helpers for the optional on-device SSH/SFTP daemon.
 *
 * The daemon is the distro's own `sshd` (OpenSSH), started inside the
 * currently running terminal session so it shares the proot/chroot network
 * namespace (localhost == app localhost). Auth is the distro's normal SSH
 * auth — log in as root after setting a password with `passwd`.
 */
object SftpManager {
    const val DEFAULT_PORT = 8022
    const val MIN_PORT = 1024
    const val MAX_PORT = 65535

    fun effectivePort(raw: String): Int =
        raw.toIntOrNull()?.takeIf { it in MIN_PORT..MAX_PORT } ?: DEFAULT_PORT

    /** Single-flight guard so auto-start fires once per session+port. */
    private val autoStarted = mutableSetOf<String>()

    @Synchronized
    fun markAutoStarted(key: String): Boolean = autoStarted.add(key)

    fun pidFile(port: Int): String = "/run/term-sshd-$port.pid"

    fun sftpCommand(ip: String, port: Int): String = "sftp -P $port root@$ip"

    fun sshCommand(ip: String, port: Int): String = "ssh -p $port root@$ip"

    fun connectionUrl(ip: String, port: Int): String = "sftp://root@$ip:$port"

    /** Idempotent start: no-ops when already running, prints an install hint when sshd is missing. */
    fun startCommand(port: Int): String {
        val d = "${'$'}"
        return "PORT=$port; PIDF=${pidFile(port)}\n" +
            "if ! command -v sshd >/dev/null 2>&1; then echo 'sshd not found — install it: apk add openssh'; exit 0; fi\n" +
            "if [ -f \"${d}PIDF\" ] && kill -0 \"$d(cat \"${d}PIDF\" 2>/dev/null)\" 2>/dev/null; then echo \"sshd already running on ${d}PORT\"; exit 0; fi\n" +
            "mkdir -p /run/sshd\n" +
            "ssh-keygen -A >/dev/null 2>&1\n" +
            "sshd -p \"${d}PORT\" -o \"PidFile=${d}PIDF\" -o PermitRootLogin=yes -o PasswordAuthentication=yes\n" +
            "sleep 1\n" +
            "if [ -f \"${d}PIDF\" ] && kill -0 \"$d(cat \"${d}PIDF\" 2>/dev/null)\" 2>/dev/null; then echo \"sshd listening on ${d}PORT — login as root (set a password with passwd)\"; else echo 'sshd failed to start — run sshd -T to diagnose'; fi"
    }

    fun stopCommand(port: Int): String {
        val d = "${'$'}"
        return "PORT=$port; PIDF=${pidFile(port)}\n" +
            "if [ -f \"${d}PIDF\" ]; then kill \"$d(cat \"${d}PIDF\" 2>/dev/null)\" 2>/dev/null; rm -f \"${d}PIDF\"; echo 'sshd stopped'; " +
            "else pkill -x sshd 2>/dev/null && echo 'sshd stopped' || echo 'sshd not running'; fi"
    }

    /** True when something accepts TCP on loopback:port. Call on Dispatchers.IO. */
    fun isListening(port: Int, timeoutMs: Int = 800): Boolean {
        return try {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), timeoutMs); true }
        } catch (_: Exception) {
            false
        }
    }

    /** Best-effort LAN IPv4. Call on Dispatchers.IO. Prefers wifi, then site-local, then any v4. */
    fun getDeviceIp(appContext: Context): String {
        try {
            @Suppress("DEPRECATION")
            val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ip = wifi?.connectionInfo?.ipAddress ?: 0
            if (ip != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ip and 0xff,
                    ip shr 8 and 0xff,
                    ip shr 16 and 0xff,
                    ip shr 24 and 0xff
                )
            }
        } catch (_: Exception) {
        }

        try {
            var fallback: String? = null
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val addresses = interfaces.nextElement().inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val host = address.hostAddress ?: continue
                        if (address.isSiteLocalAddress) return host
                        if (fallback == null) fallback = host
                    }
                }
            }
            if (fallback != null) return fallback
        } catch (_: Exception) {
        }

        return "127.0.0.1"
    }
}
