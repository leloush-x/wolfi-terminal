# Architecture Notes (Wave 0 recon)

## Modules
- :app — manifest, signing, release config.
- :core:main — terminal UI, sessions, settings, root access; almost all changes land here.
- :core:components — shared Compose preference components.
- :core:resources — string resources (multi-locale).
- :core:proot — proot native bits.

## Entry & lifecycle
- App.kt (Application): sets `application`, inits TerminalUtils, SheveryManager, ANRWatchDog, UpdateManager, temp-dir cleanup via GlobalScope (C1: GlobalScope usage to review).
- MainActivity hosts TerminalScreen + nav host.

## Terminal path
- TerminalScreen -> TerminalViewLayout (AndroidView wrapping com.termux.view.TerminalView).
- TerminalBackEnd implements TerminalViewClient + TerminalSessionClient; MkSession creates TerminalSession per distro.
- TerminalViewModel holds WeakReference<TerminalView>, showVirtualKeys, wallAlpha, backgroundBlur.
- Font size: TerminalView.setTextSize(dpToPx(...)); pinch zoom in onScale() coerces 11..45 and sets size but does not persist (F1 gap). Settings.terminal_font_size default 13 (Int). Customization slider 10..20.
- Input mode: Settings.input_mode (DEFAULT/TYPE_NULL/VISIBLE_PASSWORD); shouldEnforceCharBasedInput = input_mode != 1.

## Settings storage
- Settings object (core/main/.../settings/Settings.kt) wraps `Preference` (SharedPreferences "Settings" + in-memory caches). ~60 typed properties. No Flow/StateFlow, no async read-once, no migration framework. Wave 1 target.

## Root access
- core/root/RootAccess.kt (local su detection) and SheveryManager.kt (manager connection) kept separate per CONTRIBUTING. Chroot/Shevery fallback to working session, never no terminal.

## Rootfs
- Wolfi/Debian/Void downloaded on demand; Alpine bundled. Per-distro version keys (wolfi_version etc.), update checks. Preserve.

## GitHub clone
- GitHubManager/GitHubReposScreen/GitHubSettingsSection; clone from pasted URL supported.

## Document provider
- AlpineDocumentProvider (SAF) multi-root.

## Extra keys / shortcuts
- virtual_keys_string JSON; ShortcutBinding serialized "CTRL|SHIFT|54" persisted in SharedPreferences; tests guard contract.

## Known cleanup candidates (C1)
- GlobalScope in App.kt; ANRWatchDog; StrictMode debug-only.
- TerminalUtils.init, TerminalUtils.typeface.
- MutableState at object scope in some screens (reset in @After per CONTRIBUTING).

## CI
- .github/workflows/android.yml: test on every push; build-release on main; publish gated on both. ~4 min.