# Contributing to Wolfi Terminal

Thanks for helping out. This is an Android/Kotlin app, so the short version is: **you rarely need a local Android SDK** — CI runs the tests on every push.

## Prerequisites

- **JDK 17** (CI uses Temurin 17)
- Android SDK with **compileSdk 37** if you build locally — Android Studio handles this for you

Gradle itself is bootstrapped by the wrapper (`gradle/wrapper`, currently 9.4.1), so no separate Gradle install is needed.

## Building

```sh
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # release APK -> app/build/outputs/apk/release/
```

## Testing

```sh
./gradlew testDebugUnitTest
```

Every individual test result is printed (`testLogging` is enabled in the root `build.gradle.kts`), so a run that discovers **zero** tests is obvious in the log — a task that finds no tests still reports success.

Current suite: **49 tests**, mostly `ShortcutBindingTest` and `TerminalUtilsTest`.

### Writing tests

- Use `@RunWith(RobolectricTestRunner::class)` with `application = Application::class`.
- **Do not let `App.onCreate` run.** It starts `ANRWatchDog` and reaches for the network, which hangs a test run. That is exactly why the tests pin a plain `Application`.
- If a class under test initialises Compose `MutableState` at object scope, reset it in `@After` — Robolectric shares static state across tests.
- `KeyEvent.keyCodeToString()` is stubbed by Robolectric and returns the numeric code (`"54"`), never `"KEYCODE_V"`. Assert the contract that holds both under Robolectric and on a device (modifier order, joining, key-token flow-through) rather than a literal key name.

## Project layout

| Module | What it is |
|---|---|
| `:app` | Application module, manifest, signing, release config |
| `:core:main` | Terminal UI, sessions, settings, root access — where almost all changes land |
| `:core:components` | Shared Compose components |
| `:core:resources` | String resources |
| `:core:proot` | proot native bits |

Dependencies live in `gradle/libs.versions.toml`. **Only add an entry you actually reference** — the catalog was pruned from 155 entries to 53 because 102 were never used by any build script.

## How CI works

`.github/workflows/android.yml`:

- **Unit Tests** — runs `./gradlew testDebugUnitTest` on **every push, any branch**. Your PR gets a real check, not a promise.
- **Build APK** — runs only on `main`, and alongside the tests.
- **Publish Release** — needs *both* the tests and the build to succeed. Nothing is released unless tests pass. Because it only runs on `main`, PRs show it as `skipped`.

Reports are uploaded as an artifact only when the test job fails.

## Commit messages

Conventional prefixes, one concern per commit:

```
feat:    user-visible functionality
fix:     something that was broken
refactor: same behaviour, better structure
test:    tests only
build:   Gradle / dependency / catalog changes
ci:      workflow changes
chore:   everything else (gitignore, untracking files)
docs:    documentation
```

## Pull requests

- **One concern per PR.** A catalog prune and an elevation refactor belong in separate PRs — review and revert both become trivial.
- **Linear history.** PRs are merged with *rebase*, not a merge commit, so `git log` stays readable. Keep commits separate and meaningful rather than squashing everything into one.
- Explain **why**, and say what you verified. If you could not run something locally, say so — CI is the verification and that is fine.

## Signing

Local builds sign with the shared testkey (`app/testkey.keystore`) unless you supply a real one:

```sh
# either an env var pointing at a properties file...
export SIGNING_PROPERTIES=/path/to/signing.properties

# ...or just drop signing.properties in the repo root
```

The file needs `keyAlias`, `keyPassword`, `storePassword`, `storeFile`. `signing.properties`, `*.keystore` and `*.jks` are gitignored (the testkey is deliberately not, so a fresh clone can still build). CI decodes `secrets.KEYSTORE` / `secrets.PROP` into `/tmp` instead — never hardcode a path to your own machine.

## Things that must not break

- **The shortcut preference contract.** `ShortcutAction` values are persisted in `SharedPreferences` and must keep round-tripping across app updates — the tests exist specifically to guard this.
- **Root access never breaks session creation.** If rish, the Shevery/Shizuku grant, or local `su` is missing, sessions start unelevated rather than failing.
- **`su` and Shizuku are different mechanisms.** Detection for local `su` lives in `core/root/RootAccess.kt`; the manager connection lives in `core/root/SheveryManager.kt`. Keep them separate.

## Reporting bugs

Include your device, Android version, and — for anything root-related — which manager you use (Shevery or Shizuku), whether it reports `uid 0` or `uid 2000`, and which execution mode you had selected (proot / chroot / Chroot (Shevery)).
