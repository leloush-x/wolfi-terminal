package com.rk.terminal.github

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.libcommons.child
import com.rk.libcommons.localDir
import com.rk.libcommons.toast
import com.rk.settings.Settings
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.screens.settings.WorkingMode
import com.rk.terminal.ui.screens.terminal.CustomSession
import com.rk.terminal.ui.screens.terminal.MkSession
import com.rk.terminal.ui.screens.terminal.Rootfs
import com.rk.terminal.ui.screens.terminal.TerminalBackEnd
import com.rk.terminal.ui.screens.terminal.TerminalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

const val BUN_EXAMPLE = """curl -fsSL https://bun.sh/install | bash
cat << 'EOF' >> ~/.bashrc
export BUN_INSTALL="$HOME/.bun"
export PATH="$BUN_INSTALL/bin:$PATH"
EOF
cat << 'EOF' >> ~/.profile
export BUN_INSTALL="$HOME/.bun"
export PATH="$BUN_INSTALL/bin:$PATH"
EOF
source ~/.bashrc 2>/dev/null || . ~/.profile
bun install -g opencode-ai"""

@Composable
fun SetupRunnerScreen(
    navController: NavController,
    mainActivity: MainActivity,
    modifier: Modifier = Modifier,
    terminalViewModel: TerminalViewModel = viewModel(mainActivity),
    mainViewModel: com.rk.terminal.ui.activities.terminal.MainViewModel = viewModel(mainActivity)
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var command by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(Settings.working_Mode) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var statusLine by remember { mutableStateOf<String?>(null) }

    fun run() {
        val script = command.trim()
        if (script.isBlank()) {
            toast("Paste a command first")
            return
        }
        if (mode == WorkingMode.WOLFI && !Rootfs.isWolfiRootfsInstalled(context)) {
            toast("Download Wolfi first: Settings > Default Working mode > Wolfi")
            return
        }
        if (mode == WorkingMode.ALPINE && !Rootfs.isRootfsInstalled(context)) {
            toast("Set up Alpine first: Settings > Default Working mode > Alpine")
            return
        }
        val binder = mainViewModel.sessionBinder
        val terminal = terminalViewModel.terminalView
        if (binder == null || terminal == null) {
            toast("Terminal not ready yet")
            return
        }
        running = true
        progress = 0f
        statusLine = "Preparing script…"
        scope.launch(Dispatchers.IO) {
            try {
                // Write verbatim to a file so heredocs / pipes / quotes survive intact.
                val scriptsDir = context.localDir().child("scripts").apply { mkdirs() }
                val file = File(scriptsDir, "setup-${System.currentTimeMillis()}.sh")
                file.writeText("#!/bin/sh\n$script\n")
                file.setExecutable(true)
                withContext(Dispatchers.Main) {
                    progress = 0.35f
                    statusLine = "Launching session…"
                }
                delay(350)
                withContext(Dispatchers.Main) {
                    val client = TerminalBackEnd(terminal, mainActivity)
                    val pending = MkSession.buildScriptPendingCommand(context, file, mode, null as CustomSession?)
                    val existing = binder.getService().sessionList.keys.toList()
                    var i = 1
                    var id = "setup$i"
                    while (id in existing) { i++; id = "setup$i" }
                    binder.createSession(id, client, mode, pending)
                    terminalViewModel.changeSession(context, binder, id)
                    progress = 1f
                    statusLine = "Running in $id — watch it live"
                    toast("Setup running in $id")
                    navController.popBackStack()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusLine = "Failed: ${e.message}"
                    toast("Setup failed: ${e.message}")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    running = false
                }
            }
        }
    }

    PreferenceLayout(
        label = "Quick setup",
        modifier = modifier,
        scrollState = null,
        onBack = { navController.popBackStack() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Paste any install command — it runs in a new terminal session so you watch every line live.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Setup command", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = {
                            clipboard.getText()?.text?.let { command = it } ?: toast("Clipboard empty")
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                        }
                        IconButton(onClick = { command = "" }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear")
                        }
                    }
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        placeholder = { Text("curl -fsSL https://… | bash") },
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { command = BUN_EXAMPLE }, label = { Text("bun + opencode") })
                        AssistChip(
                            onClick = { clipboard.getText()?.text?.let { command = it } },
                            label = { Text("Paste") },
                            leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null) }
                        )
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Run in", style = MaterialTheme.typography.titleSmall)
                    ModeRow("Alpine", WorkingMode.ALPINE, mode) { mode = it }
                    ModeRow("Wolfi", WorkingMode.WOLFI, mode) { mode = it }
                    ModeRow("Android", WorkingMode.ANDROID, mode) { mode = it }
                }
            }

            if (running || statusLine != null) {
                StatusCard(running = running, progress = progress, status = statusLine)
            }

            Button(
                onClick = { run() },
                enabled = !running && command.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (running) "Launching…" else "Run in terminal")
            }
            OutlinedButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Back to terminal") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ModeRow(title: String, value: Int, current: Int, onPick: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        RadioButton(selected = current == value, onClick = { onPick(value) })
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun StatusCard(running: Boolean, progress: Float, status: String?) {
    val transition = rememberInfiniteTransition(label = "setup-pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "a"
    )
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                status ?: "Working…",
                style = MaterialTheme.typography.bodyMedium,
                modifier = if (running) Modifier.alpha(alpha) else Modifier
            )
            LinearProgressIndicator(
                progress = { if (running) progress.coerceIn(0f, 1f) else 1f },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
