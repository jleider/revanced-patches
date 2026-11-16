# Sync for Reddit — ReVanced patches

Goal: keep the abandoned, closed-source **Sync for Reddit v23.06.30-13:39** (`com.laurencedawson.reddit_sync`) working against today's Reddit API by writing **ReVanced patches**. This folder lives in a fork of `revanced-patches`; the Sync work is developed on branch `sync-fixes` and offered upstream when ready. Everything here is shared publicly: no personal data, keys, tokens or absolute paths in committed files.

Detailed reverse-engineering notes, class maps, API findings and gotchas live in the skill **`../.claude/skills/sync-reddit-internals/SKILL.md`** (repo root). Load it before investigating Sync code or writing a patch, and add to it whenever you learn something non-obvious about the app.

## Layout

All paths are relative to this folder (`sync-for-reddit/`); scripts find it from their own location. Gitignored local data: `apk/`, `decompiled/`, `out/`, `tools/`, `secrets.env`, `.venv/`.

| Path | What |
|---|---|
| `env.sh` | **Source first in every shell.** Sets `SYNC_DIR`/`SYNC_REPO` from its location, finds JDK 17 and the Android SDK (or uses `JAVA_HOME`/`ANDROID_HOME`), unsets `JAVA_OPTS` (newer-JDK flags break JDK 17). |
| `secrets.env` | Local only (mode 600, gitignored; template `secrets.env.example`): `REDDIT_CLIENT_ID`, passed to Spoof client's "OAuth client ID" option at patch time and never compiled into the patches. Never print or commit it. |
| `apk/sync-23.06.30.apk` | Universal APK (all ABIs), the only patch input; bring your own (SHA-256 in README). |
| `decompiled/sync-23.06.30-readable/` | jadx output with original class names restored. Read this. |
| `decompiled/sync-23.06.30/` and `-smali/` | Raw jadx and apktool output with the real obfuscated names that fingerprints must match. |
| `..` (repo root) | The `revanced-patches` fork; upstream is the GitLab mirror (the GitHub repo was DMCA'd). Sync patches: `patches/src/main/kotlin/app/revanced/patches/reddit/customclients/sync/syncforreddit/`; extensions: `extensions/syncforreddit/`. |
| `tools/revanced-cli-6.0.0-all.jar` | Patcher CLI (download per README). `tools/lib/` caches pinned unit-test jars. |
| `scripts/` | Build, verify, probe and e2e tooling (below). |
| `tests/` | `expected-changes.txt` (class allowlist), `fixtures/` (recorded API responses), `snapshots/` (endpoint probe history). |
| `out/` | Build outputs: patched APKs, keystore, patch log. |

## Workflow: run after every patch change

1. `scripts/verify_patch.sh --debug` runs every gate (independent work runs concurrently):
   - **unit tests** (concurrent with the Gradle build): `scripts/unit_tests.sh`, offline JVM checks of every extension helper against fixtures.
   - **build and patch:** fails if any expected patch didn't apply.
   - **class allowlist** (concurrent with reachability and extension API, after one shared decode): only allowlisted classes may change.
   - **extension api:** `scripts/check_extension_api.py` fails if the extension calls a method or field missing from the app's own minified libraries (Sync ships OkHttp 5.0.0-alpha.8 with okio renamed to `ae.*`; the extension compiles against OkHttp 5.3.2). Self-tested by `test_check_extension_api.py`.
   - **reachability:** `scripts/check_reachability.py` fails if any injected extension call is unreachable in the patched smali, e.g. one hook branching over another.
   - **manifest:** the telemetry flags must be present.
   - **signature**, then **install**.
   - **ART verifier:** `scripts/art_verify.sh` runs dex2oat verify on the rooted emulator and fails if any class stops verifying.

   A new patch must be added to `EXPECTED_PATCHES` in the script, and the classes it touches to `tests/expected-changes.txt` with a comment naming the patch. A new extension helper gets cases in `extensions/syncforreddit/src/test/.../ExtensionHarness.java`. Runs take a lock (`out/.verify.lock`, owner PID inside, stale locks are taken over). `--no-install` skips the emulator steps, and the release build is verified without `--debug`. `--no-build` reuses the existing APK and patch log without rebuilding or repatching; `--debug --no-build --no-install` runs only host gates. Each stage prints `TIME`; parallel stage logs live in `out/*-stage.log`. Gradle build caching and configuration caching are enabled (validated with actual builds, including cache reuse).
2. `scripts/smoke.sh`: deep-link scenarios on the emulator. Fails on crashes, Reddit 403 block pages or gallery errors. Needs the `--debug` build ("Enable debug logging" patch).
3. `scripts/e2e/features.sh [scenario …]`: one UI scenario per patch, plus checks that shared hooks have no side effects (search `+`, profiles, wiki, ordinary image loads).
   - **Shards:** with no explicit serial, the wrapper starts/reuses its own headless read-only `sync34` pool. It sizes the pool from cores (2 per instance), total/available RAM (3 GiB per instance with headroom), and free disk (4 GiB per instance), capped at 4 automatically. Set `SYNC_EMULATORS=2` (allowed 1–8) to override; `SYNC_EMULATORS=2 scripts/e2e/features.sh [scenario …]` forces sharding even if a serial is inherited. Scenarios are divided round-robin and PASS/FAIL/SKIP lines are merged with one summary; any worker setup error also fails the run. Every worker restores its own baseline; screenshots and worker/boot logs are under `out/emulators/`.
   - **Pool lifecycle:** `SYNC_EMULATORS=2 scripts/e2e/emulators.sh up` installs the current `out/sync-patched-debug.apk` and restores the baseline on new instances. All instances use explicit serials, starting at free ports 5556/5558; occupied ports are skipped. `scripts/e2e/emulators.sh down` stops only recorded instances with matching process identities. The pool stays up between suites. If the SDK refuses multiple instances, the original AVD instance must also have been started read-only; never stop another session's instance. To resize or discard stale records, use `down` then `up`. Do not run other device tooling against pool members during a suite.
   - **Single device:** `ANDROID_SERIAL=emulator-NNNN scripts/e2e/features.sh [scenario …]` retains the single-device workflow when `SYNC_EMULATORS` is unset. Shell and direct Python device entry points require an emulator, verified by qemu properties; Python never defaults to a serial.
   - **Assertions:** Sync's request log, Glide errors, crashes, and the hosts the app connected to. Any connection to a dead or telemetry host fails the scenario.
   - **Host capture:** `scripts/e2e/netcap.sh` routes the emulator through a passthrough `mitmdump` proxy. Nothing is decrypted; only connection host names are recorded. Each serial has its own port (8899 plus half its offset from 5554), PID file and host log; `SYNC_NETCAP_PORT` can override the port for a single-device run.
   - **Test posts:** found live at run time by `scripts/e2e/live.py` (no hard-coded URLs). A scenario whose kind of post doesn't currently exist is reported as SKIP (inconclusive), and negative controls treat SKIP as inconclusive.
   - Request waits use a single logcat reader thread per worker, fenced by a scenario marker; media waits use source events and UI waits. Playback additionally requires advancing viewer time, or a started Sync audio player when audio is required. Negative/error observation windows without a reliable completion event remain bounded waits.
   - Live finder dependencies of selected scenarios are prefetched concurrently (up to 6 threads) with single-flight per-run caches; CLI multi-finder discovery also runs concurrently. NotFound remains SKIP/inconclusive.
   - Needs the `--debug` build. Every new patch gets a scenario here. Offline tooling checks: `.venv/bin/python -I scripts/e2e/test_tooling.py`.
   - `.venv/bin/python -I scripts/e2e/youtube_embed.py [--max N] [--shot DIR] [VIDEO_ID …]`: no emulator; runs the shipped `YouTubeWebPlayer.AD_SCRIPT` in headless Chromium against live Reddit YouTube posts and checks playback starts, plus the ad-wait notice (shown on a stall after ads were removed, hidden once playing). Run after changing the YouTube player script.
4. `scripts/e2e/login.sh` and `scripts/e2e/gallery_position.sh`: native UI via uiautomator2 selectors, Reddit WebView via Playwright CDP (`driver.py`). Set up with `python3 -m venv .venv && .venv/bin/pip install -r requirements.txt`; wrappers use that venv with `-I`. Login needs `SYNC_TEST_USER` / `SYNC_TEST_PASS` only in the environment; never write or log them. Requires `--debug` for WebView debugging. A redirect_uri failure means the OAuth app registration needs http://redditsync/auth. Gallery screenshots use `$SHOTS` (default `${TMPDIR:-/tmp}/sync-shots`). `ui.py` remains for smoke.sh only.
5. `scripts/negative_control.sh <old-ref> <scenario>…`: builds an older commit of the patches (via `git archive`, no worktree), installs it and requires each named scenario to **fail** there, then reinstalls the current debug build. Run it for every new scenario, so a test that can't detect its bug is caught. Patched debug APKs are cached by resolved commit SHA under `out/negctl/cache/`; repeat runs skip archive/build/patch. Delete the corresponding cached APK if patch options, credentials or input APK change. Negative controls always run on their explicitly selected single emulator.
6. `scripts/e2e/monkey.sh [events] [seed]`: random-input fuzz with host capture; fails on a crash, ANR or dead/telemetry host.
   - **App state is hermetic.** `scripts/e2e/app_state.sh restore` puts back `out/appstate/baseline.tgz` (onboarding done, Crashlytics off, default settings, logged out). features.sh and smoke.sh restore it first (`KEEP_STATE=1` skips), and monkey.sh restores it afterwards, because random taps change settings such as the post layout. `app_state.sh fresh` rebuilds the baseline after a reinstall or data wipe.
7. `python3 -I scripts/probe_endpoints.py [--save]`: reports which live test posts the e2e finders currently pick (a missing kind means that scenario will SKIP). Then: live check of every Reddit and third-party endpoint Sync uses, both the broken originals and the replacement endpoints each patch depends on. It's diagnostic, not a gate: report its drift, but don't fail work because Reddit is flaky.

Emulator: AVD `sync34` (Android 14, arm64, rootable). Boot it headless with `emulator -avd sync34 -no-window -no-audio -no-snapshot-save &`. Drive it with `scripts/emu.sh` or the uiautomator2 helper `scripts/e2e/driver.py`.

## Rules

- Fingerprint on stable things: `immutableClassDef.sourceFile == "OriginalName.java"` and string literals. Never on obfuscated names like `y8.f`.
- No third-party API keys in patches, and no new third-party API dependencies. Prefer Reddit-only, direct-CDN or public-page solutions (e.g. Imgur albums use the public embed page).
- "Enable debug logging" is for test builds only (logs can contain tokens).
- Turn off Crashlytics during onboarding on fresh installs.
- Work on `sync-fixes`. Push, open PRs or publish releases only when the user asks.
- Committed files must stay shareable: relative paths only, no personal names/emails/usernames, no secrets. Commits use the GitHub noreply author address.
- Commit messages follow upstream's conventional style (`fix(Sync for Reddit): …`, `feat(Sync for Reddit): …`); semantic-release uses them to version releases.
- Everything checked by hand (curl probes, old-build comparisons, fuzzing) goes into a script or scenario above, so it is rerun every time.
- Don't hardcode test URLs or post IDs: they drift (posts get removed, media expires). E2E scenarios find a suitable live post at run time (`scripts/e2e/live.py`) and fail as inconclusive if none exists. Recorded API responses for the offline unit tests are fine; they are data, not links.
- During a Reddit incident (`reddit.statuspage.io`), features.py prints a WARN; re-run failures later before blaming a patch.
