# Sync for Reddit patches

ReVanced patches that keep **Sync for Reddit v23.06.30-13:39** (`com.laurencedawson.reddit_sync`) working
with today's Reddit. The app is abandoned and closed source; its backend services and several Reddit APIs
it relies on are gone. These patches replace them with Reddit's current endpoints or with work done on the
device, without any third-party API keys.

This folder lives in a fork of [ReVanced Patches](https://gitlab.com/ReVanced/revanced-patches). The patch
code is in the normal ReVanced places; this folder holds the docs, tests and tooling for the Sync work.

## Using the patches

You need your own copy of the app (the universal APK, not a split `.apkm` bundle):

| | |
|---|---|
| Package | `com.laurencedawson.reddit_sync` |
| Version | `v23.06.30-13:39` (versionCode 23033) |
| SHA-256 of the tested APK | `6469a8dcc8e2a097892f84e8962e94965713be327b18db6a3110ed96f7fa4190` |

You also need a Reddit OAuth client ID: create an **installed app** at
<https://www.reddit.com/prefs/apps> with redirect URI `http://redditsync/auth`, and enter its ID in the
**Spoof client** patch's "OAuth client ID" option.

**ReVanced Manager:** add this repository as a patch source (Settings → Sources, or the patch bundle
settings, depending on the Manager version), select Sync for Reddit and patch it. Releases of this repository
publish a `patches-<version>.rvp` bundle, the same format as ReVanced's own releases.

**ReVanced CLI:**

```sh
java -jar revanced-cli-6.0.0-all.jar patch -p patches-<version>.rvp -b \
  -e "Spoof client" -O "OAuth client ID=<your client id>" \
  -o sync-patched.apk sync-23.06.30.apk
```

The patched app is signed with a new key, so uninstall any differently signed copy of Sync first (this
removes its data).

## What the patches fix

Patches added or changed in this fork (all enabled by default unless noted):

| Patch | What it does |
|---|---|
| Fix galleries | Loads galleries through `/comments/` because Reddit blocks `/api/info`. |
| Fix load more comments | Loads hidden comments through comment permalinks; `morechildren` is blocked. |
| Fix login redirect | Login no longer lands on the Reddit homepage. |
| Fix Imgur | Builds direct image links locally; albums load from Imgur's public embed page. No API key. |
| Fix Gfycat | Plays Reddit's archived copies of Gfycat videos (with audio), fails fast when none exists. |
| Fix link previews | Builds website and album previews on the device; Sync's preview server is gone. Fetches refuse private/LAN addresses and are size- and time-limited. |
| Fix Reddit image URLs | Unescapes `&amp;` in `preview.redd.it` URLs, which Reddit now rejects with 403. |
| Fix combined subreddit feeds | `r/a+b` feeds no longer redirect to the homepage. |
| Fix other discussions | Finds other posts of the same link through Reddit search. |
| Fix random subreddit | Picks from Reddit's popular list; `r/random` was removed. |
| Fix DeviantArt embeds | Uses HTTPS for DeviantArt oEmbed lookups. |
| Hide restore comment | Hides the dead "restore comment" action (relied on Pushshift). |
| Upgrade connections to HTTPS | Requests `http://` URLs over HTTPS on every connection the app makes, falling back to http only for hosts without working HTTPS. |
| Disable telemetry | Stops Crashlytics, Firebase Analytics and the "hmmm" report from reaching the original developer's Firebase project. |
| Fix /s/ links (changed) | Only sends the login token to real Reddit share links over HTTPS. |
| Enable debug logging | Off by default. Test builds only: logs can contain tokens. |

Known limitations: Tumblr embeds (revoked API key), Pushshift-based features, and `r/random` deep links.

## Developing

Everything below runs from this folder; all paths are relative to it.

1. JDK 17, the Android SDK (build-tools, platform-tools, emulator, an Android 14 `google_apis` arm64/x86_64
   system image), Python 3, and the GitHub CLI logged in with `read:packages` (the ReVanced Gradle plugin
   comes from GitHub Packages: `gh auth refresh -h github.com -s read:packages`).
2. Local files (gitignored):
   - `apk/sync-23.06.30.apk`: your copy of the app (checksum above).
   - `tools/revanced-cli-6.0.0-all.jar`: the ReVanced CLI 6.0.0 release jar.
   - `secrets.env`: copy `secrets.env.example` and fill in your Reddit client ID (`chmod 600`).
   - `python3 -m venv .venv && .venv/bin/pip install -r requirements.txt` for the e2e tools.
3. `source env.sh` (finds JDK 17 and the SDK, or uses `JAVA_HOME` / `ANDROID_HOME`).
4. An emulator named `sync34` (Android 14, rootable `google_apis` image) for the on-device gates:
   `emulator -avd sync34 -no-window -no-audio -no-snapshot-save &`. After the first install, run
   `scripts/e2e/app_state.sh fresh` once to record a clean app state (Crashlytics off).

Then, after every change:

```sh
scripts/verify_patch.sh --debug   # unit tests, build, patch, class allowlist, API/reachability checks, ART verifier
scripts/e2e/features.sh           # one emulator scenario per patch
scripts/smoke.sh                  # deep-link smoke tests
scripts/negative_control.sh <old-commit> <scenario>   # prove a new scenario fails without the fix
python3 -I scripts/probe_endpoints.py                 # live check of every endpoint the patches use
```

[`CLAUDE.md`](CLAUDE.md) describes the workflow and rules in detail, and
[`../.claude/skills/sync-reddit-internals/SKILL.md`](../.claude/skills/sync-reddit-internals/SKILL.md) holds
the reverse-engineering notes (class map, Reddit API state, gotchas).

## License

GPLv3, like ReVanced Patches. Sync for Reddit is © its developer; this project ships patches only, never the
app or a patched APK.
