# Contracts (cross-cutting interfaces)

Defined before code. Dependents code against these only.

## SettingsRepository (B1)
- Location: com.rk.settings.SettingsRepository
- Backed by SharedPreferences "Settings" (existing), wrapped.
- `fun getBoolean(key, default): Boolean` etc. (typed accessors) — delegates to Preference for now.
- `val <key>Flow: StateFlow<T>` for live-observed keys (terminal_font_size, voice settings).
- `suspend fun setX(key, value)` writes async (apply) and updates StateFlow.
- `fun migrate()` idempotent, reads old keys, writes new keys; keeps old keys for one release.
- Read-once startup: `init(context)` loads cache once; subsequent reads from cache.

## TextSize (F1)
- Setting key: "terminal_font_size_sp" (Float), default 13f, clamp [6f,40f].
- Apply API: `TerminalView.setTextSize(px)` (existing) + `Settings.terminal_font_size` kept in sync for compat (Int).
- Pinch zoom writes back to terminal_font_size_sp.
- PTY resize: TerminalBackEnd.onScale persists and calls setTextSize.

## TextSender (F3)
- `object TextSender { fun send(view, text, bracketedPaste): Unit }` chunks on IO.
- Bracketed paste detection via DECSET 2004 tracked in TerminalBackEnd/terminal state.
- Strips control chars <0x20 except tab; strips ESC; newlines -> spaces unless bracketed paste.

## Voice settings keys (F3, new keys only)
- voice_input_enabled (Boolean, default false for upgrading users)
- keyboard_input_mode (Int: 0 classic, 1 text/voice-friendly)
- voice_preview (Boolean, default true)
- voice_language (String, default "")
- voice_trailing_space (Boolean, default true)
- voice_mic_button_visible (Boolean, default true)