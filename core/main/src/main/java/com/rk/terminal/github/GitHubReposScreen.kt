package com.rk.terminal.github

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.blankj.utilcode.util.ClipboardUtils
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.libcommons.toast
import com.rk.settings.Settings
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.screens.terminal.TerminalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubReposScreen(
    navController: NavController,
    mainActivity: MainActivity,
    modifier: Modifier = Modifier,
    terminalViewModel: TerminalViewModel = viewModel(mainActivity),
    mainViewModel: com.rk.terminal.ui.activities.terminal.MainViewModel = viewModel(mainActivity)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var repos by remember { mutableStateOf<List<GitHubRepo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var cloning by remember { mutableStateOf<String?>(null) }

    val username = remember { GitHubManager.username() }
    val token = remember { GitHubManager.token() }

    fun load() {
        if (username.isBlank() && token.isBlank()) {
            loading = false
            error = "Add your GitHub username or token in Settings → GitHub first."
            return
        }
        loading = true
        error = null
        scope.launch(Dispatchers.IO) {
            try {
                val list = GitHubManager.fetchRepos(
                    username = username,
                    token = token.ifBlank { null }
                )
                withContext(Dispatchers.Main) {
                    repos = list
                    loading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loading = false
                    error = e.message ?: "Load failed"
                }
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    fun clone(repo: GitHubRepo) {
        val cmd = GitHubManager.cloneCommand(repo, token.ifBlank { null })
        val terminal = terminalViewModel.terminalView
        cloning = repo.fullName
        scope.launch {
            // Brief animated feedback so the tap feels alive even before the
            // command lands in the pty.
            kotlinx.coroutines.delay(450)
            val ok = GitHubManager.sendToTerminal(terminal, cmd)
            cloning = null
            if (ok) {
                toast("Cloning ${repo.fullName} in terminal")
                navController.popBackStack()
            } else {
                // No live session — keep command on clipboard so nothing is lost.
                ClipboardUtils.copyText("git-clone", cmd)
                toast("No live session — clone command copied")
            }
        }
    }

    PreferenceLayout(
        label = if (username.isBlank()) "GitHub repos" else "@$username repos",
        modifier = modifier,
        scrollState = null,
        onBack = { navController.popBackStack() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search repos") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                )
                IconButton(onClick = { load() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }

            if (loading) {
                LoadingRow("Fetching repos…")
            }

            error?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Failed to load", fontWeight = FontWeight.Bold)
                        Text(it, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { load() }) { Text("Retry") }
                    }
                }
            }

            if (!loading && error == null) {
                val filtered = if (query.isBlank()) repos else repos.filter {
                    it.fullName.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true)
                }
                Text(
                    "${filtered.size} repos",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (filtered.isEmpty()) {
                    Text(
                        "No repos found. Check username / token in Settings → GitHub.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.fullName }) { repo ->
                        RepoCard(
                            repo = repo,
                            isCloning = cloning == repo.fullName,
                            onClone = { clone(repo) },
                            onCopy = {
                                ClipboardUtils.copyText("git-clone", GitHubManager.cloneCommand(repo, token.ifBlank { null }))
                                toast("Clone command copied")
                            },
                            onOpen = {
                                runCatching {
                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, repo.htmlUrl.toUri()))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingRow(text: String) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, modifier = Modifier.alpha(alpha), style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun RepoCard(
    repo: GitHubRepo,
    isCloning: Boolean,
    onClone: () -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = null
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    repo.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = {},
                    label = { Text(if (repo.private) "Private" else "Public") }
                )
            }
            if (repo.description.isNotBlank()) {
                Text(
                    repo.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repo.language?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("${repo.stars}", style = MaterialTheme.typography.labelMedium)
                }
                Text("⑂ ${repo.forks}", style = MaterialTheme.typography.labelMedium)
                repo.defaultBranch.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isCloning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Sending to terminal…", style = MaterialTheme.typography.labelMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onClone, enabled = !isCloning, modifier = Modifier.weight(1f)) {
                    if (isCloning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Cloning…")
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clone")
                    }
                }
                OutlinedButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                }
                OutlinedButton(onClick = onOpen) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
