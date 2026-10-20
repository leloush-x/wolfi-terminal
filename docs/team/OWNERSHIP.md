# Ownership (Wave 0)

Single agent (LEAD) executes all tasks sequentially. File ownership is logical
by task; no two tasks edit the same file in the same PR.

Shared files (single named owner per task):
- AndroidManifest.xml -> C1 (manifest review)
- gradle/libs.versions.toml -> B1 only if a dependency is unavoidable (none planned)
- strings.xml (all locales) -> F3 (voice settings strings) / F1 (text size strings) with per-task owners; serialized
- Settings repository (new file) -> B1 owner
- TerminalView/input files (TerminalViewLayout, TerminalBackEnd, TerminalViewModel) -> F1 for text size, F3 for voice input; serialized (F1 first, then F3)
- Workflow files -> C2

Task -> owned files (primary):
- B1 (settings repo): core/main/.../settings/SettingsRepository.kt (new), Preference (unchanged facade), tests.
- F1 (text size): Customization.kt font section, TerminalBackEnd.onScale, TerminalViewLayout (text size apply), Settings.terminal_font_size (already Int; widen to Float via new key terminal_font_size_sp).
- F2 (smoothness): Compose screens with recomposition issues; Dispatchers.IO moves.
- F3 (voice): new files VoiceInput.kt/TextSender.kt, extra-keys mic button, settings voice section, input-mode handling.
- C1: dead code, warnings, manifest, proguard.
- C2: workflow.