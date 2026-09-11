package com.rk.terminal.ui.screens.settings

import android.content.Context
import android.net.wifi.WifiManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.blankj.utilcode.util.ClipboardUtils
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.libcommons.toast
import com.rk.settings.Settings
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.screens.terminal.TerminalViewModel
import java.net.Inet4Address
import java.net.NetworkInterface

@Composable
fun SftpSettingsSection(
    navController: NavController,
    mainActivity: MainActivity,
    terminalViewModel: TerminalViewModel = viewModel(mainActivity)
) {
    val context = LocalContext.current
    var sftpEnabled by remember { mutableStateOf(Settings.sftp_enabled) }
    var sftpPort by remember { mutableStateOf(Settings.sftp_port.toString()) }
    var deviceIp by remember { mutableStateOf("") }

    fun getDeviceIp(): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ip = wifiManager?.connectionInfo?.ipAddress
            if (ip != null && ip != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ip and 0xff,
                    ip shr 8 and 0xff,
                    ip shr 16 and 0xff,
                    ip shr 24 and 0xff
                )
            }
        } catch (_: Exception) {}

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: ""
                    }
                }
            }
        } catch (_: Exception) {}

        return "127.0.0.1"
    }

    fun refreshIp() {
        deviceIp = getDeviceIp()
    }

    fun getSftpUrl(): String {
        val ip = deviceIp.ifBlank { getDeviceIp() }
        val port = sftpPort.toIntOrNull() ?: 8022
        return "sftp://$ip:$port"
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
                            if (sftpEnabled) "Running on port ${sftpPort.toIntOrNull() ?: 8022}"
                            else "Tap to enable file transfer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = sftpEnabled,
                        onCheckedChange = {
                            sftpEnabled = it
                            Settings.sftp_enabled = it
                            if (it) refreshIp()
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
                            value = sftpPort,
                            onValueChange = { value ->
                                if (value.all { it.isDigit() } && value.length <= 5) {
                                    sftpPort = value
                                    value.toIntOrNull()?.let { port ->
                                        if (port in 1024..65535) {
                                            Settings.sftp_port = port
                                        }
                                    }
                                }
                            },
                            label = { Text("Port") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        IconButton(onClick = { refreshIp() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh IP")
                        }
                    }

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
                                Text(
                                    getSftpUrl(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            IconButton(
                                onClick = {
                                    ClipboardUtils.copyText("sftp-url", getSftpUrl())
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
                                val cmd = "sftp ${getSftpUrl()}"
                                ClipboardUtils.copyText("sftp-cmd", cmd)
                                toast("sftp command copied")
                            },
                            label = { Text("Copy sftp command") }
                        )
                        AssistChip(
                            onClick = {
                                val cmd = "ssh ${getSftpUrl().replace("sftp://", "")}"
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
                        OutlinedButton(
                            onClick = {
                                terminalViewModel.terminalView?.let { view ->
                                    val session = view.currentSession
                                    if (session != null) {
                                        val cmd = "nohup sftp-server -p ${sftpPort.toIntOrNull() ?: 8022} &\n"
                                        session.write(cmd)
                                        toast("SFTP server started")
                                    } else {
                                        toast("No terminal session")
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Start server")
                        }
                    }

                    Text(
                        "Connect from any SFTP client (FileZilla, Cyberduck, etc.) using the URL above. Default root: /sdcard",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
