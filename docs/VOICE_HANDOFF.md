# Voice Input Handoff / Fix Summary

## 1. Context rebuild findings

- Repo: `github.com/leloush-x/wolfi-terminal` (ReTerminal fork).
- Branch merged: `fix/voice-input-default` -> `main` via PR #14.
- Merge commit: `b30ffdf086910407d73c5cd6285743010703e87e`.
- CI run (green): https://github.com/leloush-x/wolfi-terminal/actions/runs/39402775426
  (Unit Tests + Build APK + Publish Release all success).
- Pre-fix tag: `pre-voice-fix` (on `b7c2562`).
- Voice code was already on `main` (commit `780df0b` "feat: voice input ..."),
  and the latest rolling release APK contains it.

### Voice-related commits/branches
- `agent/lead/f3-voice-input` (merged PR #10) added `TextSender`, `VoiceMicControl`,
  settings keys.
- `Settings.input_mode` / `SettingsRepository.keyboardInputMode` controlled IME mode.
- `TerminalBackEnd.shouldEnforceCharBasedInput()` mapped mode 0 (default) to the
  legacy `VISIBLE_PASSWORD | NO_SUGGESTIONS` input type.

## 2. Diagnosis

Added temporary `WOLFI_IME` logging in the IME path (later removed). Observed cause:

- (a) **EditorInfo**: the default input mode (0) produced
  `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | TYPE_TEXT_FLAG_NO_SUGGESTIONS`. Gboard and
  the Redmi default keyboard treat this like a password field and hide/disable the
  built-in mic. `TYPE_NULL` (mode 1) also fails to expose the mic on several IMEs.
  This is the primary cause.
- (c) secondary: composing/correction handling needed a single send + clear so
  dictated text is not duplicated. Multi-character commits must not be re-sent as
  composing text and must not press Enter.

## 3. What changed

- Vendored `com.termux.view.TerminalView` (+ support classes/resources) so
  `onCreateInputConnection` can be customized without changing the external
  dependency's published jar.
- `TerminalViewClient.getInputMode()` default added (0/1/2).
- `TerminalBackEnd.getInputMode()` returns `Settings.input_mode`.
- `onCreateInputConnection`:
  - mode 0 (default): `TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_NO_SUGGESTIONS`
  - mode 1: `TYPE_NULL`
  - mode 2: `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | TYPE_TEXT_FLAG_NO_SUGGESTIONS`
  - `imeOptions = IME_FLAG_NO_FULLSCREEN | IME_FLAG_NO_EXTRACT_UI`
- `InputConnection`:
  - single code point keeps legacy char-based path (Enter/Ctrl/Alt/arrows).
  - multi-code-point commits are delivered once, stripped of control chars
    (<0x20 except tab) and ESC/C1; newlines become spaces unless bracketed paste
    is enabled (kept then), wrapped in `ESC[200~ ... ESC[201~` when DECSET 2004
    is set. A dictated phrase never sends Enter.
  - `deleteSurroundingText` sends backspaces (`KEYCODE_DEL`).
  - buffer cleared after each commit.
- No new setting, toggle, permission, or network use. Existing input-mode
  setting still maps to the three presets, with the recommended default (0)
  now producing the mic-friendly text type.

## 4. Per-keyboard (expected)

- Gboard: mic visible; dictated phrase appears at cursor; no Enter.
- Xiaomi/Redmi default: mic visible; phrase appears; no Enter.
- Samsung/SwiftKey: legacy mode 2 still available; default text type keeps typing.
- Hardware keyboard: single-char path unchanged; Ctrl/Alt/arrows/Backspace/Enter work.

## 5. Tests

- Existing Robolectric tests remain green (`TextSenderTest`, etc.).
- CI: https://github.com/leloush-x/wolfi-terminal/actions/runs/39402775426

## 6. Install

- APK: https://github.com/leloush-x/wolfi-terminal/releases/latest/download/wolfi-terminal-latest.apk
- CI run: 39402775426, commit `b30ffdf086910407d73c5cd6285743010703e87e`.

## 7. Rollback

```
git revert b30ffdf086910407d73c5cd6285743010703e87e
```
or reset to tag `pre-voice-fix`.