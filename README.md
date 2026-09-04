# Wolfi Terminal

**Wolfi Terminal** is a sleek, Material 3-inspired Android terminal emulator with **Wolfi Linux** and **Alpine Linux** built in — proot or chroot, multiple sessions, virtual keys. Built on [Termux's](https://github.com/termux/termux-app) robust TerminalView. Forked from [ReTerminal](https://github.com/RohitKushvaha01/ReTerminal).

Download the latest APK from the [Releases Section](https://github.com/leloush-x/wolfi-terminal/releases/latest) — one rolling release page, always the newest build (`wolfi-terminal-latest.apk`).

Direct download: https://github.com/leloush-x/wolfi-terminal/releases/latest/download/wolfi-terminal-latest.apk

# Distros

| Distro | Base | Install |
|--------|------|---------|
| 🐺 Wolfi | glibc, `apk`, preinstalled `fastfetch` `curl` `git` | On-demand download from [wolfi-os-rootfs](https://github.com/leloush-x/wolfi-os-rootfs) latest release (Settings → Default Working mode → Wolfi) |
| 🏔️ Alpine | musl, bundled in the APK | Automatic on first launch |
| 🤖 Android | Host shell | Built in |

Wolfi needs arm64 or x86_64 (no ARM32 — use Alpine on older devices).

# Features
- [x] Basic Terminal
- [x] Virtual Keys
- [x] Multiple Sessions (Alpine / Wolfi / Android / custom)
- [x] Wolfi Linux support (glibc, on-demand latest rootfs)
- [x] Alpine Linux support (bundled)
- [x] proot (no root) or chroot (rooted, faster) execution modes
- [x] Configurable Keyboard Shortcuts (Paste, Session Management)
- [x] Direct script launch (open `.sh` files with the terminal)

# Screenshots
<div>
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width="32%" />
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/02.jpg" width="32%" />
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/03.jpg" width="32%" />
</div>

# Build

```sh
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/
```

CI builds and publishes to the rolling `latest` release on every push to `main` (or manual trigger).

# Credits
- [ReTerminal](https://github.com/RohitKushvaha01/ReTerminal) — base app
- [Wolfi](https://github.com/wolfi-dev/os) via [wolfi-os-rootfs](https://github.com/leloush-x/wolfi-os-rootfs) — Wolfi rootfs builds
- [Termux](https://github.com/termux/termux-app) — TerminalView/Emulator
