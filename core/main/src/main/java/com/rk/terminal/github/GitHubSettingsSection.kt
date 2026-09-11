package com.rk.terminal.github

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.libcommons.toast
import com.rk.settings.Settings
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.routes.MainActivityRoutes
import com.rk.terminal.ui.screens.settings.SettingsCard
import com.rk.terminal.ui.screens.terminal.TerminalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GitHubSettingsSection(
    navController: NavController,
    mainActivity: MainActivity,
    terminalViewModel: TerminalViewModel = viewModel(mainActivity)
) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf(Settings.github_username) }
    var token by remember { mutableStateOf(Settings.github_token) }
    var showToken by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var verifiedUser by remember { mutableStateOf<String?>(null) }

    fun save() {
        Settings.github_username = username.trim()
        Settings.github_token = token.trim()
        toast("GitHub settings saved")
    }

    fun verify() {
        val t = token.trim()
        if (t.isBlank()) {
            toast("Paste a personal access token first")
            return
        }
        verifying = true
        verifiedUser = null
        scope.launch(Dispatchers.IO) {
            try {
                val login = GitHubManager.validateToken(t)
                withContext(Dispatchers.Main) {
                    verifying = false
                    if (login != null) {
                        verifiedUser = login
                        username = login
                        Settings.github_username = login
                        Settings.github_token = t
                        toast("Token valid — $login")
                    } else {
                        toast("Invalid token (401)")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    verifying = false
                    toast("Verify failed: ${e.message}")
                }
            }
        }
    }

    fun loginInTerminal() {
        val t = token.trim()
        if (t.isBlank()) {
            toast("Paste a personal access token first")
            return
        }
        Settings.github_username = username.trim()
        Settings.github_token = t
        val terminal = terminalViewModel.terminalView
        val ok = GitHubManager.sendToTerminal(terminal, GitHubManager.ghLoginCommand(t))
        if (ok) {
            toast("gh login sent to terminal")
            // Take user back so they watch the login animation live.
            navController.popBackStack()
        } else {
            toast("No live session — open terminal first")
        }
    }

    PreferenceGroup(heading = "GitHub") {
        SettingsCard(
            title = {
                Text(
                    when {
                        verifiedUser != null -> "Connected as $verifiedUser"
                        username.isNotBlank() -> "@$username"
                        else -> "Not configured"
                    }
                )
            },
            description = {
                Text(
                    if (token.isBlank()) "Add username + personal access token"
                    else "Token saved • repos include private"
                )
            },
            onClick = {}
        )

        Column(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("GitHub username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it; verifiedUser = null },
                label = { Text("Personal access token (classic, repo scope)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                            if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            if (verifying) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            verifiedUser?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Token valid — $it", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { save() }, modifier = Modifier.weight(1f)) { Text("Save") }
                OutlinedButton(onClick = { verify() }, enabled = !verifying) {
                    if (verifying) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Verify")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { loginInTerminal() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Login in terminal (gh)")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        if (username.isBlank() && token.isBlank()) {
                            toast("Add username or token first")
                            return@OutlinedButton
                        }
                        save()
                        navController.navigate(MainActivityRoutes.GitHubRepos.route)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("View my repos") }
                OutlinedButton(
                    onClick = {
                        val terminal = terminalViewModel.terminalView
                        GitHubManager.sendToTerminal(terminal, GitHubManager.ghLogoutCommand())
                        Settings.github_token = ""
                        token = ""
                        verifiedUser = null
                        toast("Logged out")
                    }
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Create token: github.com → Settings → Developer settings → PAT (classic) with repo scope. Login runs gh auth login inside your current session.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
