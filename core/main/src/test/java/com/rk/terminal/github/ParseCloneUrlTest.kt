package com.rk.terminal.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [GitHubManager.parseCloneUrl] feeds straight into a shell command
 * (`git clone <result>`), so it has to be permissive about hosts — any git
 * server, not just GitHub — while being strict about shape, because a
 * half-typed paste must never reach the pty.
 */
class ParseCloneUrlTest {

    @Test
    fun `blank input is rejected`() {
        assertNull(GitHubManager.parseCloneUrl(""))
        assertNull(GitHubManager.parseCloneUrl("   "))
    }

    @Test
    fun `full https URL is kept verbatim`() {
        assertEquals(
            "https://github.com/owner/repo",
            GitHubManager.parseCloneUrl("https://github.com/owner/repo")
        )
    }

    @Test
    fun `trailing slash and .git suffix are preserved`() {
        assertEquals(
            "https://github.com/owner/repo",
            GitHubManager.parseCloneUrl("https://github.com/owner/repo/")
        )
        assertEquals(
            "https://github.com/owner/repo.git",
            GitHubManager.parseCloneUrl("https://github.com/owner/repo.git")
        )
    }

    @Test
    fun `scheme-less github URL gains https`() {
        assertEquals(
            "https://github.com/owner/repo",
            GitHubManager.parseCloneUrl("github.com/owner/repo")
        )
    }

    @Test
    fun `owner shorthand resolves to github`() {
        assertEquals(
            "https://github.com/owner/repo.git",
            GitHubManager.parseCloneUrl("owner/repo")
        )
    }

    @Test
    fun `scp syntax is preserved`() {
        assertEquals(
            "git@github.com:owner/repo.git",
            GitHubManager.parseCloneUrl("git@github.com:owner/repo.git")
        )
    }

    @Test
    fun `host is not assumed to be github`() {
        assertEquals(
            "https://gitlab.com/group/project",
            GitHubManager.parseCloneUrl("gitlab.com/group/project")
        )
    }

    @Test
    fun `input with whitespace is rejected`() {
        assertNull(GitHubManager.parseCloneUrl("https://github.com/owner/repo name"))
    }

    @Test
    fun `pastes that are not repositories are rejected`() {
        assertNull(GitHubManager.parseCloneUrl("just-a-word"))
        // Three segments with no host-looking first part: not resolvable.
        assertNull(GitHubManager.parseCloneUrl("a/b/c"))
        // Normalized but has no path to clone.
        assertNull(GitHubManager.parseCloneUrl("https://github.com"))
    }
}
