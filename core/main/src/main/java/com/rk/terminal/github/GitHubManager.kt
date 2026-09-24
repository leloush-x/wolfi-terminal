package com.rk.terminal.github

import com.rk.settings.Settings
import com.termux.view.TerminalView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GitHubRepo(
    val name: String,
    val fullName: String,
    val description: String,
    val private: Boolean,
    val htmlUrl: String,
    val cloneUrl: String,
    val sshUrl: String,
    val language: String?,
    val stars: Int,
    val forks: Int,
    val updatedAt: String,
    val defaultBranch: String
)

object GitHubManager {
    const val API_BASE = "https://api.github.com"

    fun username(): String = Settings.github_username.trim()

    fun token(): String = Settings.github_token.trim()

    fun isConfigured(): Boolean = username().isNotBlank() || token().isNotBlank()

    fun hasToken(): Boolean = token().isNotBlank()

    private fun openConnection(urlStr: String, token: String?): HttpURLConnection {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "WolfiTerminal")
            setRequestProperty("Accept", "application/vnd.github+json")
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
            connectTimeout = 15000
            readTimeout = 15000
        }
        return conn
    }

    private fun readBody(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return stream?.bufferedReader()?.use { it.readText() } ?: ""
    }

    /** Returns login name when the token is valid, null otherwise. Throws on network error. */
    fun validateToken(token: String): String? {
        var conn: HttpURLConnection? = null
        try {
            conn = openConnection("$API_BASE/user", token)
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = readBody(conn)
            return JSONObject(body).optString("login", "").ifBlank { null }
        } finally {
            conn?.disconnect()
        }
    }

    fun parseRepo(obj: JSONObject): GitHubRepo {
        return GitHubRepo(
            name = obj.optString("name"),
            fullName = obj.optString("full_name"),
            description = obj.optString("description", "").takeIf { it != "null" } ?: "",
            private = obj.optBoolean("private", false),
            htmlUrl = obj.optString("html_url"),
            cloneUrl = obj.optString("clone_url"),
            sshUrl = obj.optString("ssh_url"),
            language = obj.optString("language", "").takeIf { it.isNotBlank() && it != "null" },
            stars = obj.optInt("stargazers_count", 0),
            forks = obj.optInt("forks_count", 0),
            updatedAt = obj.optString("updated_at", ""),
            defaultBranch = obj.optString("default_branch", "main").ifBlank { "main" }
        )
    }

    /**
     * Blocking network call — invoke on Dispatchers.IO.
     * With a token uses /user/repos (includes private repos), otherwise public
     * repos of [username]. Paginates until an empty page (cap 10 pages).
     */
    fun fetchRepos(username: String, token: String?): List<GitHubRepo> {
        val out = mutableListOf<GitHubRepo>()
        var page = 1
        while (page <= 10) {
            val url = if (!token.isNullOrBlank()) {
                "$API_BASE/user/repos?per_page=100&page=$page&sort=updated"
            } else {
                "$API_BASE/users/$username/repos?per_page=100&page=$page&sort=updated"
            }
            var conn: HttpURLConnection? = null
            try {
                conn = openConnection(url, token)
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    val body = readBody(conn)
                    throw RuntimeException("GitHub API: HTTP ${conn.responseCode} $body")
                }
                val arr = JSONArray(readBody(conn))
                if (arr.length() == 0) break
                for (i in 0 until arr.length()) {
                    out.add(parseRepo(arr.getJSONObject(i)))
                }
                if (arr.length() < 100) break
                page++
            } finally {
                conn?.disconnect()
            }
        }
        return out
    }

    fun authedCloneUrl(repo: GitHubRepo, token: String?): String {
        val clean = token?.trim().orEmpty()
        if (clean.isBlank()) return repo.cloneUrl
        // PATs are URL-safe; inject for non-interactive clone over HTTPS.
        return repo.cloneUrl.replace("https://", "https://$clean@")
    }

    fun cloneCommand(repo: GitHubRepo, token: String?): String {
        return "git clone ${authedCloneUrl(repo, token)}"
    }

    /** `git@host:owner/repo.git` */
    private val scpLike = Regex("^[\\w.-]+@[\\w.-]+:[\\w./-]+$")

    /** `owner/repo` — shorthand that only GitHub can be assumed for. */
    private val shorthand = Regex("^[\\w.-]+/[\\w.-]+$")

    /** `scheme://host/path` — host may be empty (file:///...), path may not. */
    private val schemeUrl = Regex("^[a-zA-Z][\\w+.-]*://[^/\\s]*/[^\\s]+$")

    /**
     * Turn whatever the user pasted into something `git clone` accepts, or
     * null when it does not look like a repository at all.
     *
     * Deliberately host-agnostic — any git server works — but strict about
     * shape, because the result goes straight into a shell command. A bare
     * `owner/repo` is resolved to GitHub since nothing else can be assumed.
     */
    fun parseCloneUrl(raw: String): String? {
        val input = raw.trim().trimEnd('/')
        if (input.isEmpty() || input.any { it.isWhitespace() }) return null

        val candidate = when {
            input.contains("://") -> input
            scpLike.matches(input) -> input
            shorthand.matches(input) -> "https://github.com/$input.git"
            input.substringBefore('/').contains('.') -> "https://$input"
            else -> return null
        }

        // Reject pastes that normalized but still have no path, e.g.
        // "https://github.com" or a scheme with nothing after it.
        if (!schemeUrl.matches(candidate) && !scpLike.matches(candidate)) return null
        return candidate
    }

    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /**
     * One-shot login for the in-terminal `gh` CLI. Installs nothing — if `gh`
     * is missing the session prints an install hint (apk add github-cli).
     */
    fun ghLoginCommand(token: String): String {
        val q = shellQuote(token.trim())
        return "if ! command -v gh >/dev/null 2>&1; then echo 'gh not found — install it first: apk add github-cli'; else echo $q | gh auth login --with-token -h github.com && gh auth setup-git 2>/dev/null; gh api user -q .login 2>/dev/null && echo 'gh login OK'; fi"
    }

    fun ghLogoutCommand(): String = "gh auth logout -h github.com 2>/dev/null; echo 'gh logged out'"

    /** Paste text + Enter into the currently attached pty. False when no live session. */
    fun sendToTerminal(terminalView: TerminalView?, command: String): Boolean {
        val session = terminalView?.currentSession ?: return false
        if (!session.isRunning) return false
        val cmd = if (command.endsWith("\n")) command else "$command\n"
        session.write(cmd)
        return true
    }
}
