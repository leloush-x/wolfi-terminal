# Wolfi Terminal upgrade plan

## 1. Stability and release safety
- [x] Install crash handling at application startup and persist complete reports.
- [x] Report ANRs to the crash log without throwing on the watchdog thread.
- [x] Make Git metadata generation work in source archives without `.git`.
- [x] Reject rootfs hardlinks that resolve outside the extraction directory.
- [x] Preserve interrupted downloads, resume with HTTP Range, and save the installed version after successful setup.
- [x] Require real credentials for release signing; never silently use the committed test key.
- [x] Make preference caches safe for concurrent access.
- [ ] Encrypt GitHub credentials with Android Keystore and exclude credentials from backup.

## 2. Build and startup performance
- [x] Enable R8 and resource shrinking; the debug build passes with the added keep rules. A signed release still needs GitHub signing secrets.
- [ ] Reduce packaged rootfs assets with ABI-aware delivery.
- [x] Defer custom font loading, downsample/cache backgrounds, and move distro install checks off the composition thread.
- [x] Throttle download progress and debounce pinch-zoom preference writes.
- [ ] Move all settings to one typed store; bell playback now uses a cached SoundPool.

## 3. Network and update delivery
- [x] Remove GitHub API release lookups; resolve latest assets by redirect and cache for ten minutes.
- [x] Publish SHA-256 manifests with rootfs releases and verify downloaded archives before extraction.
- [ ] Add ETag-based update checks and notifications with a versioned rollback release.
- [ ] Remove successful archives when safe and measure zstd before adopting it.

## 4. Product and maintenance
- [x] Add searchable settings and collapsible groups; open matching groups as the user searches.
- [x] Make native Android speech dictation available by default, with microphone permission requested on first use.
- [ ] Start first-run setup with bundled Alpine and fetch other distros in the background.
- [x] Add settings search and collapsible groups, and show friendly setup errors.
- [ ] Add session controls and move uncommon preferences into an Advanced group.
- [ ] Review edge-to-edge behavior, accessibility, and running-session notifications.
- [ ] Consolidate CI, pin actions, remove stale repositories/dependencies, and correct docs.
- [x] Add a regression test for a hardlink source that escapes the extraction root.
- [ ] Add update-version comparison tests with the update-notification worker.

## Initial implementation scope
The current upgrade also adds native speech dictation, searchable settings, startup and terminal performance fixes, and verified resumable rootfs downloads. A separate rootfs release workflow now publishes checksums. App update notifications, encrypted credentials, ABI splits, background distro installation, and session controls remain follow-up work. Signed app releases require valid GitHub signing secrets and are only attempted through manual workflow dispatch.
