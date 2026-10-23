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
- [x] Enable R8 and resource shrinking; verify keep rules and release builds.
- [ ] Reduce packaged rootfs assets with ABI-aware delivery.
- [ ] Defer expensive startup work, cache/downsample backgrounds, and remove main-thread disk work.
- [ ] Throttle download progress and persist pinch zoom after gesture completion.
- [ ] Evaluate a single typed settings store and bell sound pooling.

## 3. Network and update delivery
- [ ] Replace GitHub API polling with cached release resolution and conditional requests where useful.
- [ ] Publish checksum metadata and verify archives before extraction.
- [ ] Add ETag-based update checks and notifications with a versioned rollback release.
- [ ] Remove successful archives when safe and measure zstd before adopting it.

## 4. Product and maintenance
- [x] Add searchable settings and collapsible groups; open matching groups as the user searches.
- [x] Make native Android speech dictation available by default, with microphone permission requested on first use.
- [ ] Start first-run setup with bundled Alpine and fetch other distros in the background.
- [ ] Improve setup errors, session controls, settings search, and advanced options.
- [ ] Review edge-to-edge behavior, accessibility, and running-session notifications.
- [ ] Consolidate CI, pin actions, remove stale repositories/dependencies, and correct docs.
- [ ] Add focused tests for archive traversal and update version comparison.

## Initial implementation scope
The first pass addresses crash/ANR reporting, source-archive builds, hardlink traversal, retry-safe downloads, and release signing. Checksums and release update notifications depend on coordinated changes to the separate rootfs release publisher and app release workflow.
