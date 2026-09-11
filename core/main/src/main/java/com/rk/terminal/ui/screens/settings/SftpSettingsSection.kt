package com.rk.terminal.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.ClipboardUtils
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.libcommons.toast
import com.rk.settings.Settings
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.screens.terminal.TerminalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SftpSettingsSection(
    mainActivity: MainActivity,
    terminalViewModel: TerminalViewModel = viewModel(mainActivity)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sftpEnabled by remember { mutableStateOf(Settings.sftp_enabled) }
    var sftpPortText by remember { mutableStateOf(Settings.sftp_port.toString()) }
    var deviceIp by remember { mutableStateOf<String?>(null) }
    var serverRunning by remember { mutableStateOf<Boolean?>(null) }
    var working by remember { mutableStateOf(false) }

    val port = SftpManager.effectivePort(sftpPortText)
    val portValid = sftpPortText.toIntOrNull() in SftpManager.MIN_PORT..SftpManager.MAX_PORT

    fun probeStatus() {
        scope.launch(Dispatchers.IO) {
            val up = SftpManager.isListening(port)
            withContext(Dispatchers.Main) { serverRunning = up }
        }
    }

    fun refreshIp() {
        scope.launch(Dispatchers.IO) {
            val ip = SftpManager.getDeviceIp(context.applicationContext)
            withContext(Dispatchers.Main) { deviceIp = ip }
        }
    }

    LaunchedEffect(Unit) {
        refreshIp()
        if (Settings.sftp_enabled) probeStatus()
    }

    fun sendToSession(cmd: String): Boolean {
        val session = terminalViewModel.terminalView?.currentSession ?: return false
        if (!session.isRunning) return false
        session.write(if (cmd.endsWith("\n")) cmd else "$cmd\n")
        return true
    }

    fun startServer() {
        if (!sendToSession(SftpManager.startCommand(port))) {
            toast("No live session — open terminal first")
            return
        }
        working = true
        serverRunning = null
        scope.launch {
            var up = false
            repeat(20) {
                delay(500)
                up = withContext(Dispatchers.IO) { SftpManager.isListening(port) }
                if (up) return@repeat
            }
            working = false
            serverRunning = up
            toast(if (up) "sshd listening on $port" else "Start timed out — check terminal output")
        }
    }

    fun stopServer() {
        if (!sendToSession(SftpManager.stopCommand(port))) {
            toast("No live session — open terminal first")
            return
        }
        working = true
        scope.launch {
            var up = true
            repeat(12) {
                delay(500)
                up = withContext(Dispatchers.IO) { SftpManager.isListening(port) }
                if (!up) return@repeat
            }
            working = false
            serverRunning = up
            if (!up) toast("sshd stopped")
        }
    }

    PreferenceGroup(heading = "SFTP Server") {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "SFTP Server",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            when (serverRunning) {
                                true -> "Running on port $port"
                                false -> if (sftpEnabled) "Enabled, server stopped" else "Tap to enable file transfer"
                                null -> if (sftpEnabled) "Checking status…" else "Tap to enable file transfer"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = sftpEnabled,
                        onCheckedChange = {
                            sftpEnabled = it
                            Settings.sftp_enabled = it
                            if (it) {
                                if (deviceIp == null) refreshIp()
                                probeStatus()
                            } else {
                                sendToSession(SftpManager.stopCommand(port))
                                serverRunning = false
                            }
                        }
                    )
                }

                if (sftpEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = sftpPortText,
                            onValueChange = { value ->
                                if (value.all { it.isDigit() } && value.length <= 5) {
                                    sftpPortText = value
                                    value.toIntOrNull()?.let { p ->
                                        if (p in SftpManager.MIN_PORT..SftpManager.MAX_PORT) {
                                            Settings.sftp_port = p
                                        }
                                    }
                                }
                            },
                            label = { Text("Port (1024–65535)") },
                            singleLine = true,
                            isError = !portValid,
                            supportingText = {
                                if (!portValid) Text("Invalid — using ${SftpManager.DEFAULT_PORT}")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        IconButton(onClick = {
                            refreshIp()
                            probeStatus()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh IP and status")
                        }
                    }

                    val ip = deviceIp
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Connection URL",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (ip == null) {
                                    Text("Detecting IP…", style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    Text(
                                        SftpManager.connectionUrl(ip, port),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    val url = SftpManager.connectionUrl(ip ?: "127.0.0.1", port)
                                    ClipboardUtils.copyText("sftp-url", url)
                                    toast("SFTP URL copied")
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy URL",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                val cmd = SftpManager.sftpCommand(ip ?: "127.0.0.1", port)
                                ClipboardUtils.copyText("sftp-cmd", cmd)
                                toast("sftp command copied")
                            },
                            label = { Text("Copy sftp command") }
                        )
                        AssistChip(
                            onClick = {
                                val cmd = SftpManager.sshCommand(ip ?: "127.0.0.1", port)
                                ClipboardUtils.copyText("ssh-cmd", cmd)
                                toast("ssh command copied")
                            },
                            label = { Text("Copy ssh command") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (serverRunning == true) {
                            OutlinedButton(
                                onClick = { stopServer() },
                                enabled = !working,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Stop server")
                            }
                        } else {
                            Button(
                                onClick = { startServer() },
                                enabled = !working,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (working) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Starting…")
                                } else {
                                    Text("Start server")
                                }
                            }
                        }
                    }

                    Text(
                        "Requires openssh in the distro (apk add openssh). Login as root — set a password first with passwd. Only devices on your network can reach it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
