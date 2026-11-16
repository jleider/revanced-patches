---
name: sync-reddit-internals
description: Reverse-engineering notes for Sync for Reddit v23.06.30-13:39 (com.laurencedawson.reddit_sync) and the workflow for writing/testing ReVanced patches against it. Use when investigating Sync behavior, locating classes for a fingerprint, or building/applying/testing patches in this workspace.
---

# Sync for Reddit internals & patch workflow

Sync is closed source and abandoned. The last release is **v23.06.30-13:39** (versionCode 23033, signer `CN=Laurence Dawson`, cert SHA-256 `0220976139f8d08d048ebd5bbf2f818c017e03aaab162403b92b6e9ade4d20eb`). Everything here was learned from decompiling that build. Obfuscated names (`y8.f`, `c9.c`, …) are specific to this build — never rely on them in a patch; fingerprint on strings/opcodes instead.

## Layout (paths relative to `sync-for-reddit/` in this repo)

- `env.sh` — **source this first.** Sets JDK 17, ANDROID_HOME, PATH, and `unset JAVA_OPTS` (flags meant for newer JDKs, e.g. `-XX:+UseCompactObjectHeaders`, make JDK 17 fail with "Unrecognized VM option").
- `apk/sync-23.06.30.apk` — universal APK (all ABIs), the patch target (bring your own; SHA-256 in `sync-for-reddit/README.md`). Split `.apkm` bundles don't work.
- `decompiled/sync-23.06.30/` — jadx Java output (`sources/`, `resources/`). Sync's own named code is under `sources/com/laurencedawson/reddit_sync/`; most logic lives in short obfuscated packages (`c9`, `x8`, `y8`, `y7`, `wc`, …). jadx keeps `/* compiled from: OriginalName.java */` comments — grep those to recover original class names.
- `decompiled/sync-23.06.30-readable/` — **read this one first.** Same jadx output, but classes are renamed to their original source-file names (`--deobf --use-source-name-as-class-name-alias always`), e.g. `x8.a` → `x8/RedditRequest`, `y8.f` → `y8/OAuthCommentsMoreRequest`, `c9.c` → `c9/PostParserHelper`. All 103 app-package classes recovered. Method and field names are still obfuscated, and the jadx `fNNNNN` field numbers differ from the raw output, so cross-reference by class, not by field name. Rename map: `decompiled/sync-23.06.30.jobf` (add hand renames there and rerun jadx with `--deobf-cfg-file-mode read`). Use the raw output or smali for the real obfuscated names a fingerprint must match.
- `decompiled/sync-23.06.30-smali/` — apktool output; use for writing fingerprints (ReVanced matches smali/dex, not Java).
- `..` (repo root) — fork of the **GitLab mirror** `https://gitlab.com/ReVanced/revanced-patches` (the GitHub repo was DMCA'd in March 2026). Sync patches: `patches/src/main/kotlin/app/revanced/patches/reddit/customclients/sync/`.
- `tools/revanced-cli-6.0.0-all.jar`

## Build & patch

```bash
source sync-for-reddit/env.sh     # from the repo root
ORG_GRADLE_PROJECT_githubPackagesUsername=$(gh api user -q .login) \
ORG_GRADLE_PROJECT_githubPackagesPassword=$(gh auth token) \
./gradlew build -x test          # -> patches/build/libs/patches-*.rvp
```

The build pulls the ReVanced gradle plugin and patcher from GitHub Packages, so the `gh` token needs `read:packages` (non-interactive refresh: `gh auth refresh -h github.com -s read:packages`).

## Emulator

- AVD `sync34`: Android 14, `google_apis` arm64, Pixel 6 profile; rootable (`adb root`).
- Headless boot: `emulator -avd sync34 -no-window -no-audio -no-snapshot-save &` then poll `adb shell getprop sys.boot_completed`.
- Homebrew's `avdmanager` ignores the SDK root; use `$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager`.

## Networking architecture

- HTTP is **Volley**. All authenticated Reddit requests extend `x8.a<T>` (abstract `Request<T>`):
  - headers come from `com.laurencedawson.reddit_sync.singleton.a.e(token)`
  - POST form params come from the `f48528s` map (`getParams()`)
  - `parseNetworkError` turns a `www-authenticate: Bearer realm="reddit", error="invalid_token"` header into `OAuthError`
- The base URL is hard-coded: `SettingsSingleton.g(ctx)` returns `"https://oauth.reddit.com/"`.
- Logging: `wc.i.e(...)` / `wc.i.f(tag, ...)` dump full response bodies and URLs — useful in logcat if logging is enabled.
- `wc.p.a(str)` HTML-unescapes Reddit strings (requests don't send `raw_json=1`, so URLs arrive with `&amp;`).
- Logged-out `www.reddit.com/*.json` now returns an HTML 403 block page; every live probe needs an OAuth bearer token.

## Feature map

### Load more comments — `y8.f` (orig. `OAuthCommentsMoreRequest`)
- `POST https://oauth.reddit.com/api/morechildren?always_show_media=1&profile_img=true`
- form: `link_id=t3_<post>`, `children=<comma ids>`, `api_type=json`, optional `sort`
- parses `json.data.things[]`; `kind` `t1` → comment, `more` → another more-stub; both via `c9.c.a(ctx, type(1|9), data, list, depth, …)`
- **The error listener silently calls the success callback**, so failures look like "nothing happened" in the UI.

### Galleries
- Feed parse (`c9.c`, orig. `PostParserHelper`): when `is_gallery` is set, the post URL is rewritten to `https://www.reddit.com/r/<sub>/comments/<id>?is_gallery=true` and `sync_type` is 6 (or 10 with a preview). The preview comes from `media_metadata[gallery_data.items[0].media_id].p[last]`.
- `y7.c` line ~232 detects gallery URLs by that `?is_gallery=true` suffix.
- Viewer request `y8.q`: `GET https://oauth.reddit.com/api/info.json?id=t3_<id>`, cached for 1h. Per item it reads `media_metadata[media_id].s.mp4`, else `.s.u`, plus `.p[0].u` (thumbnail), `caption` and `outbound_url`. Any exception → `VolleyError("Could not load gallery")`.

### GIF / media hosts
jadx's original-name comments identify the classes:

| Class | Original name | Endpoint | Status (probed 2026-11-15) |
|---|---|---|---|
| `c8.a` | ImageHelper | rewrites giphy → `media.giphy.com/media/<id>/giphy.mp4`, i.redd.it, imgflip, … | giphy CDN 200 |
| `c8.b` | PostPreviewHelper | `thumbs.gfycat.com`, `v.redd.it`, `i.ytimg.com` | gfycat dead |
| `f8.a` | ImgurImageHelper | ID extraction; `h()` maps `.mp4`/`.webm` → `.gifv` and https → http; `f()` = album/gallery check | — |
| `s8.d` | ImgurSingleImageRequest | `g8.d` → `https://images.syncforreddit.com/image/<md5(id+salt)>/<id>` | **DEAD (no DNS)** |
| `s8.b` | ImgurGalleryRequest | `g8.b` → `https://images.syncforreddit.com/a/<md5(id+salt)>/<id>` | **DEAD (no DNS)** |
| `u8.c` | (imgur upload) | `imgur-apiv3.p.rapidapi.com/3/image`, hard-coded keys | 401 (upstream spoof-client patch rewrites it to api.imgur.com) |
| `GrabGfycatRequest` | — | `api.gfycat.com/v1/gfycats/` | **DEAD** (gfycat shut down Sept 2023) |
| `r8.b` | CheckStreamableRequest | `api.streamable.com/videos/` | 200 |
| `r8.g`, `o8.h` | RedditVideoRequest / RedditVideoDescription | `v.redd.it/...` | not probed yet |
| `t8.a/b/c` | Redgifs | `api.redgifs.com/v2/...` (oauth/client → 405) | upstream FixRedgifsApiPatch exists |

- The proxy URLs are salted MD5 hashes of the image id (salt in `g8.d`; not reproduced here, the service is dead). The imgur proxy responses are imgur-API shaped (`data.link`, `data.mp4`, `data.images[]`), so pointing them at `api.imgur.com/3/image/<id>` and `/3/album/<id>` with a `Client-ID` header should parse unchanged.
- Direct `i.imgur.com/<id>.gif` and `.mp4` still return 200.
- Inline comment GIFs use `media_metadata` with `[gif](giphy|<id>)` markup (`c9.c` ~line 723).

## Existing upstream Sync patches (GitLab mirror)
spoof client (client id + `http://redditsync/auth` redirect + random UA + imgur URL), disable ads, disable piracy detection, fix thumbnails, fix video downloads, fix s-links, fix redgifs API, use user endpoint, disable the Sync-for-Lemmy bottom sheet.

## Guardrails / test loop (run after every patch change)

1. `scripts/verify_patch.sh [--debug] [--no-build]` builds the bundle and patches the APK. It fails if any expected patch didn't report "succeeded", or if any original class outside `tests/expected-changes.txt` changed. That catches a fingerprint that matched the wrong method. It then verifies the signature and installs to the emulator. When a new patch legitimately touches a class, add it to the allowlist with the patch name and original source file. The smali diff ignores re-dex noise (dropped static-field defaults).
2. `scripts/smoke.sh [frontpage|thread|gallery …]` needs the `--debug` build. It opens content through deep links and fails on a crash, a dead process, a Reddit HTML block page (`theme-beta`) or "Could not load gallery" in Sync's logs. Screenshots go to `$SHOTS` (default `${TMPDIR:-/tmp}/sync-shots`).
3. `python3 -I scripts/probe_endpoints.py [--save]` is a live contract check of every endpoint Sync uses (app-only token). It diffs against the last snapshot in `tests/snapshots/`, and `--save` refreshes `tests/fixtures/`. It's **diagnostic, not a gate**: Reddit outages or rate limits shouldn't fail a build. `!!` = required by the patched app, `--` = known-broken/informational.
4. `scripts/emu.sh shot|ui|tap|text|key` drives the emulator. `ui` prints clickable nodes with centre coordinates.

E2E: `scripts/e2e/login.sh` and `gallery_position.sh` use `.venv` Python with `-I` (install `scripts/e2e/requirements.txt`). Shared `driver.py` uses uiautomator2 selectors/waits/screenshots for native screens and Playwright CSS locators through shadow DOM for Reddit. The toolbar avatar is the toolbar's direct clickable ImageView child. CDP attaches to Sync's PID-specific `webview_devtools_remote_<pid>` socket with an ephemeral adb forward, removed on exit; no browser download needed. Use `connect_over_cdp(..., no_defaults=True)`: WebView rejects Playwright’s default Browser.setDownloadBehavior/context management command. "Enable debug logging" now also enables WebView debugging in `RedditApplication.onCreate` (sourceFile + startup string fingerprint). Credentials come only from `SYNC_TEST_USER` / `SYNC_TEST_PASS`; no login screenshots, traces or page dumps. Detect `Invalid or missing redirect_uri parameter` and fail clearly until the OAuth app registers `http://redditsync/auth`. Gallery screenshots go to `$SHOTS`, default `${TMPDIR:-/tmp}/sync-shots`. Legacy `ui.py` remains because `smoke.sh` uses it.

Gotchas learned the hard way:
- revanced-cli 6: `-O "Option Name=value"` uses the option's display **name** (e.g. `OAuth client ID`) and binds to the `-e` immediately before it. Put a patch's `-O` right after its own `-e`.
- `adb shell monkey` sometimes fails to launch Sync after a reinstall (exit -5); use `am start -n com.laurencedawson.reddit_sync/.ui.activities.HomeActivity.Default`.
- Turn Crashlytics off during onboarding on fresh installs (last settings page), so test sessions don't report to the old developer's Firebase.
- "Enable debug logging" (our patch, `use = false`) makes `Logger` (`wc.i`) log to logcat under tags `reddit_sync`, `URL`, `RedditRequest`. Debug builds only: logs can include tokens.
- Fingerprint on `custom { immutableClassDef.sourceFile == "OriginalName.java" }`. The source file names survive obfuscation and are stable for this build.

## Reddit API state (probed 2026-11-15, app-only installed_client token)

- 403 HTML block page: `/api/info`, `/by_id/*`, `/api/morechildren` (POST and GET), and every logged-out `www.reddit.com/*.json`.
- 301 → www: multi-sub listings `/r/a+b/hot`.
- 200: `/r/<sub>/hot`, `/hot`, `/comments/<id>`, `/comments/<id>/_/<commentId>`, about/search/wiki/duplicates, `/user/<u>/about` (but `/u/<u>/about` 301s to `/user/`; upstream "Use /user/ endpoint" patch).
- `/comments/<id>?limit=1` returns `[postListing, comments]`; the post at `[0].data.children[0].data` carries `gallery_data` and `media_metadata` in the exact shape `y8.q` parses. That's the gallery workaround.
- Not yet checked with a **user** token (logged-in Sync). Re-probe once logged in, since some 403s may be app-only restrictions.

### Findings 2026-11-15 (second pass)

- **Combined subs:** `oauth.reddit.com/r/a+b/<sort>` 301s to the www *homepage*. `/r/a%2Bb/<sort>` (encoded `+`) returns the real combined listing for every sort and for `search.json?restrict_sr=on`. Fixed for every request by "Fix combined subreddit feeds" in the `RedditRequest` (`x8.a`) constructor (p3 = URL). Every OAuth request class extends `x8.a`, so that constructor is the place for any global URL rewrite. "Fix other discussions" and "Fix random subreddit" also hook it.
- **Random:** `/r/random.json` and `/r/randnsfw.json` return 404 (Reddit removed them). `RandomRequest` (`r8.d`) only reads `data.children[0].data.subreddit`. The fix picks from `/subreddits/popular.json` (SFW) or `/subreddits/search.json?q=nsfw&include_over_18=on`, filtered on `over18`. The UI entry point is the home "Random" chip → `RandomProgressDialogBottomSheet`. A deep link to `r/random` instead opens a normal listing `r/random/hot` → 404 (still unfixed).
- **Other discussions:** `OAuthPostsRequest` (`y8.k0`) builds `https://ssl.reddit.com/submit.json?url=<URLEncoder-encoded link>` and strips the bearer in `getHeaders()` for that prefix. It now 301s to www (blocked). `oauth.reddit.com/search.json?q=url:<link>&type=link` returns a single Listing with the same posts as `/duplicates/<id>` (plus the original). `/api/info?url=` → 403, and oauth `/submit.json` → 400.
- **Telemetry:** besides Crashlytics/Analytics (manifest flags), `BaseActivity.p0` posts username, country, purchase token, SKU and installer to the Firebase callable `hmmm` (still deployed). It fires when Ultra looks active but the token lacks `.AO-`. The developer's Firebase project `us-central1-sync-for-reddit.cloudfunctions.net` hosts 21 callables (Ultra backup/restore, tags, paints, purchase verification).
- **Third-party status (live):** `ap.syncforreddit.com/image` (link-preview proxy) → 401 for a bare request (check what `wc.q` sends before calling it dead); `ap.syncforreddit.com/stats.json` 200; `todo.syncforreddit.com` assets 200; `raw.githubusercontent.com/laurencedawson/reddit-sync-development` assets/upload.json 200; Pushshift → 307 (restore-deleted-comment is effectively dead); Tumblr API key → 401; `http://backend.deviantart.com/oembed` → 301 to https (HttpURLConnection won't follow); the Amazon ad cloud function is still 200 (Disable ads should cover it).
- Also work with an app-only token: `/api/user_data_by_account_ids`, `/api/v1/user/<u>/trophies`, `/r/<s>/about/rules`, `/r/<s>/api/link_flair_v2`, `/api/v1/<s>/emojis/all`, `/domain/<d>`.
- Full endpoint inventory (static, from the endpoint-audit agent): every `y8`/`z8`/`a9`/`b9` request class, with method, URL and auth. Regenerate it by grepping `.source` for `OAuth*Request.java` if you need it.
- Testing: `scripts/e2e/driver.py` (uiautomator2) is handy for ad-hoc checks. `D.tap(d, text="Random")`, and `D.screenshot(d, "name.png")` needs the extension. `verify_patch.sh` now takes a lock (`out/.verify.lock`) and has `--no-install`.

### Gfycat archive fix (2026-11-15)

"Fix Gfycat" repairs PostParserHelper's final ContentValues (v2 JSON, v5 values, .locals 29): Gfycat links use `preview.reddit_video_preview.fallback_url` only for HTTPS `v.redd.it` MP4s, preserving `raw_url`/permalink. `preview_url` and `thumbnail` must not point to dead Gfycat images; use Reddit's image source/thumbnail. Types: 4 animated video, 9 link with preview, 3 plain link. ImageViewerFragment.X3 turns every `v.redd.it` link into `https://v.redd.it/<LinkHandler.k(url)>/HLSPlaylist.m3u8` (HLS carries the audio track). `LinkHandler.k` strips `/DASH_240…/DASH_1080` but keeps the `.mp4`, and doesn't know `CMAF_*`, so direct file links built a dead playlist. The hook now maps `v.redd.it/<id>/<anything>.mp4` to `/<id>/HLSPlaylist.m3u8` (`FixGfycatPatch.redditHls`) and calls the player directly. Don't play the MP4 itself: DASH/CMAF files are video-only, so audio is lost (that was the first version). Resolve Post's `video_preview` getter through AutoValue_Post's ContentValues serializer, and bypass enhanced autoplay's Gfycat proxy. RequestQueue.add rejects Gfycat hosts with normal Volley postError after bookkeeping, alongside the independent Imgur hook. Live fixtures: 89oo9p → 0hp59oq3mpr81/DASH_480.mp4; do8t38 → 12qpe9hlnsw91/DASH_240.mp4; 7s6y7w has no Reddit video copy. Desktop GfycatHarness uses org.json plus the test ContentValues substitute.
- **Link previews (Fix link previews):** `ap.syncforreddit.com/image?url=<raw link>` returns 401 even with Sync's exact request (no key or signature exists). It's loaded by Glide through `OkHttpHelper.a()` (`a8.a`, OkHttp 5 alpha, unminified, while okio is obfuscated to `ae.*`, so extensions must avoid okio types). Users: Ultra website previews (`WebsitePreviewHelper.b`, gated by `ultra_website_previews` + genuine `.AO-` purchase token) and inline Tenor/Imgur-album thumbnails in post/comment text (`SpannableTextViewStringBuilder`, not Ultra-gated, so testable on a non-Ultra emulator). The patch adds an OkHttp application interceptor that fetches the link's first 256 KB, reads `og:image`/`twitter:image` and returns that image. `imgur-album-<id>` maps to `imgur.com/a/<id>`. Gfycat/redgifs keys fail fast. Known misses: bot-protected sites (Reuters) and pages without og tags. Test posts: r/fallenlondon/comments/1xu5svk (Tenor), r/employedbykohls/comments/1xxg4ow (Imgur album).

### Patch-writing lessons from the 2026-11-16 code review (Opus + Sol)

- **Never branch to an existing instruction from an injected block** (`ExternalLabel(.., getInstruction(index))`) when another patch might inject at the same spot. Labels stay on the original instruction, so the later block becomes unreachable (Imgur + Gfycat in `RequestQueue.add`). End each block with its own `:label` + `nop` and fall through instead. `check_reachability.py` now fails this statically.
- **Extensions running inside OkHttp/Glide must never throw a `RuntimeException`.** OkHttp rethrows it on the dispatcher thread and kills the app. Wrap the body and convert it to an `IOException`.
- **Response adapters must not turn garbage into an empty success.** Return the body unchanged so Sync's own parser hits its `ParseError` path and keeps the UI state. An empty "things" list deletes the more-row.
- **Upstream Spoof client rewrites every `ssl.reddit.com` string in the dex, ours included,** so at runtime it's `www.reddit.com`. Match any Reddit host.
- **Resolve obfuscated members from stable anchors at patch time.** Post getters come from the `AutoValue_Post` serializer's column names (`commentMoreIds`→`u()`, `parent_id`→`t0()`, `video_preview`), and the request params field from `RedditRequest.getParams()`. Add a `registerCount` tripwire wherever an injection relies on free registers.
- **`composingFirstMethod { instructions(a, b) }` matches in order.** Two string predicates must appear in the method in that order, or the fingerprint finds nothing.
- **Load more comments:** Reddit returns at most ~300 top-level comments per `/comments/<post>` response, whatever `limit` is. `depth=3` reaches ~35% more hidden roots than `depth=10`. The tail of very large threads still needs one permalink per comment (anchored mode), bounded by Sync's auto-load only firing while the row is on screen.
- **Glide preloads dead hosts during parsing** (`thumbs.gfycat.com` posters, `<host>/favicon.ico` link badges) before any parser hook can repair the URL. Block those at the Glide OkHttp client.

### v.redd.it file layout (probed 2026-11-16)
- Newer uploads expose `CMAF_<h>.mp4` and `CMAF_AUDIO_<kbps>.mp4`; older ones `DASH_<h>.mp4` and `DASH_audio.mp4`. Requesting the wrong family returns 403, not 404.
- `HLSPlaylist.m3u8` and `DASHPlaylist.mpd` exist for both, including Gfycat archives (`reddit_video_preview`). The HLS master lists `TYPE=AUDIO` renditions when the video has sound.
- `reddit_video_preview.fallback_url` / `secure_media.reddit_video.fallback_url` are video-only.
- e2e audio check: `dumpsys audio` lists `AudioPlaybackConfiguration … u/pid:<uid>/… state:started` while Sync plays sound (`features.py` `audio_players()`).

### Findings 2026-11-16
- **preview.redd.it rejects `&amp;`.** Since 2026-11-16, `preview.redd.it` / `external-preview.redd.it` return 403 for HTML-escaped query strings (`?width=140&amp;height=108&amp;…&s=…`); the same URL with `&` is 200. Sync doesn't send `raw_json=1` on listings, so every feed thumbnail broke. "Fix Reddit image URLs" adds `RedditImageUrlInterceptor` to Glide's OkHttp client (`OkHttpHelper`, same hook point as `GfycatBlockInterceptor`). Bash gotcha when testing it: `${u//&amp;/&}` inserts the *match* for `&` (bash 5.2 `patsub_replacement`); use python or sed.
- **Outage sheet.** Sync polls `reddit.statuspage.io` and shows "The Reddit servers are having issues…" (`abstract_alert_dialog_bottom_sheet_*`, buttons Close / View details) during any incident, often several seconds after launch. It covers the UI and fails selector-based tests. `features.py` registers a uiautomator2 watcher plus a dismiss inside `expect_ui`, and prints a WARN with the incident's components at start; smoke.sh closes it too.
- **Fix /s/ links leaked the bearer token.** The upstream shared `BaseFixSLinksPatch` matches `.*reddit\.com/r/x/s/y` and sends `Authorization: Bearer` with a HEAD to whatever URL it gets, including `https://evil.example/reddit.com/r/a/s/b` and plain http. The exported `IntentActivity` passes any SEND text to the hooked `LinkHelper` method, so any installed app could trigger it. Sync's `FixSLinksPatch` now validates links with `SLinkGuard.safeSLink` (https/http on reddit.com or a subdomain, exact `/r/<sub>/s/<id>` path, no userinfo or odd port, http upgraded) before calling the shared resolver. e2e: `slink_token_guard`.
- **List layout** is `<int name="ui_mode_new" value="1" />` in the main prefs (`UiSingleton`); the baseline is cards. Only list/compact layouts load the escaped 140px `preview.redd.it` thumbnails, so `images_load_list_layout` sets it via root and restores the baseline in `c.cleanup`.
- **Security review** (2026-11-16, report in `out/security-review.md`): no bundled library has a reachable high-impact CVE. OkHttp 5.0.0-alpha.8, Gson 2.8.9, ExoPlayer 2.18.2, old Glide and jsoup are all obfuscated/repackaged (okio → `ae.*`), so library upgrades are infeasible; the OS patches TLS, WebView and media codecs. Open items: link-preview SSRF hardening, cleartext traffic (`usesCleartextTraffic=true`), exported Tasker `FireReceiver`/`EditActivity`, old snudown `libanddown.so` (JNI `Java_com_commonsware_cwac_anddown_AndDown_markdownToHtml`), unreachable trust-all `SHelper.enableAnySSL` in snacktory.
- **targetSdk** is 33. Raising it only opts into behaviour changes (edge-to-edge at 35, foreground service types, intent rules) that would each need patches in obfuscated UI code; OS security fixes don't depend on it. Not worth it while installs of targetSdk 33 are allowed.
- **Test flakiness causes seen 2026-11-16, none of them app bugs:** (1) monkey fuzzing switched the post layout from cards (`id/title`) to list (`id/holder_list_title`); fixed by the app-state baseline; (2) the gallery fixture post was removed by a moderator, and Sync's MultiImageActivity then closes itself ("Views: 0") back to MainActivity; (3) Reddit's outage sheet; (4) monkey reached the quick-settings shade and left the emulator in airplane mode + Do Not Disturb (Sync then shows "Your device is offline"). monkey.sh now resets device toggles and features.py fails fast without network. Look at the screenshot in `$SHOTS` and `probe_endpoints.py`'s fixture checks before suspecting a patch.
- **SEND intents** resolve to two Sync activities (`SubmitActivity`, `IntentActivity`) and open a chooser; name `-n <pkg>/.ui.activities.IntentActivity` to reach `LinkHelper`.
- **Logged-out NSFW is intermittent.** With an app-only token, `/subreddits/search.json?q=nsfw&include_over_18=on` flips between 0 and about 63 over-18 subreddits from one minute to the next (2026-11-16), and `/subreddits/popular`, `/subreddits/new` and `/r/all` return none at all. The random NSFW pool can only be verified reliably when logged in with NSFW enabled; the probe treats it as informational.

### HTTPS upgrade and preview hardening (2026-11-16)
- **One hook covers all networking.** Volley (`com/android/volley/toolbox/BasicNetwork`, modified to use OkHttp), Glide (`OkHttpStreamFetcher`), ExoPlayer (`OkHttpDataSource`) and `OkHttpHelper` all use OkHttp, whose `okhttp3` package is not obfuscated. "Upgrade connections to HTTPS" hooks `OkHttpClient$Builder.build()` (sourceFile `OkHttpClient.kt`) to append `HttpsUpgradeInterceptor` once per client (idempotent, since `newBuilder()` copies interceptors). It tries https on port 443 with a 5 s connect timeout. It falls back to http for that host for the session on TLS, connect or no-route errors, and on timeouts only for GET/HEAD so a POST is never re-sent. IP literals, `.local`, `localhost` and odd ports are left alone. WebView traffic is not covered.
- **Where Sync uses http:** hardly anywhere by itself. It rewrites http Imgur links in old posts (`http://i.imgur.com/...`) to https before loading them, and across the full suite plus 8,000 monkey events it made no port-80 connections. The reliable http source is link previews of `http://` URLs in post text, e.g. fixture `http_tenor_selftext` (`http://tenor.com/view/...`), which the old build fetched on `tenor.com:80`. `https_upgrade` uses it; old http Imgur posts pass on the old build too, so they prove nothing.
- **Minified OkHttp in the app:** class and method names match upstream 5.x, but `ResponseBody.create(byte[], …)` and other okio-based overloads only exist with `ae.*` types. Never build a response body in the extension; reuse the response you got. `check_extension_api.py` catches this.
- **Link previews** now use their own OkHttp client:
  - DNS (`PUBLIC_ONLY_DNS`) refuses loopback, private, link-local, CGNAT `100.64/10`, ULA `fc00::/7`, multicast, reserved and IPv4-mapped private addresses. Because this happens at DNS lookup, it also covers redirects.
  - 20 s call timeout and a 15 MB `Content-Length` cap on images.
  - The client gets the HTTPS upgrade through the `build()` hook.
  - JVM tests set `fetchThroughChainForTests` so the fake chain serves responses.
  - Behind the e2e mitm proxy the proxy resolves hosts, so the DNS guard is covered by unit tests only.
- **Imgur on Reddit today** (sampled 2026-11-16): 0.4% of r/all hot, 0.1% of top-of-year, 0.04% of comments, and no albums in the samples. Albums now load keylessly from the public embed page (below).

### Imgur albums without an API key (2026-11-16)
- `imgur.com/a/<id>/embed?pub=true` (any User-Agent) inlines `var images = {"count":N,"images":[{hash, ext, width, height, title, description, animated, prefer_video, ...}]}` for the whole album. The normal album page and `og:image` only name the first image.
- Fix Imgur points `ImgurGalleryRequest`'s URL at that page and converts it, as the first step of `parseNetworkResponse(NetworkResponse)` (`.locals 11`), into `{"data":{"images":[{link, mp4?, width, height, title, description}]}}`. It keeps the response headers via `NetworkResponse(byte[], Map)`. Animated items get `.mp4` links. If the page has no image list, it is left alone, so Sync's parser fails and the album opens in the browser as before.
- There is no Imgur API, key or patch option anymore. Single images were always keyless (direct `i.imgur.com` URL built locally).
- e2e: `imgur_album` is a 4-image album and requires Sync's MultiImageActivity with an "N / 4" counter. A single-item album shows no counter.

### Lessons from the 2026-11-16 Sol (xhigh) review
- **OkHttp skips DNS for IP literals** (`RouteSelector`), so a custom `Dns` alone never sees `http://192.168.1.1/`. Link previews therefore turn off automatic redirects, follow them by hand in `fetch()`, and check every hop (http/https only, no private IP literal) **before** connecting. A network interceptor runs after the TCP/TLS connect, so it is only a backstop.
- **Cap bodies by counting bytes, not by `Content-Length`.** Chunked and gzip responses have no usable length. `readCapped()` reads at most 10 MB of decoded bytes and returns `ResponseBody.create(byte[], MediaType)`, which survives in the app's minified OkHttp (the okio-typed overloads use `ae.*`).
- **HTTPS fallback and non-idempotent requests:** only connect-stage failures (refused, no route, TLS handshake/peer verification, cleartext policy) prove nothing was sent. Other `SSLException`s and timeouts can happen after the request was written, so they fall back only for GET/HEAD.
- **Tests must prove the effect, not the request.** `load_more_comments` now requires the tapped "View more (N)" row to be replaced and new comment text to appear. A malformed response makes Sync keep the old row while the URL checks still pass.
- **Negative controls** count only a scenario assertion (`FAIL <scenario>: ...`). Setup errors (unknown scenario, no network, harness exceptions) are reported as inconclusive.
- **Portability:** use Python `tempfile` or `mktemp "$TMPDIR/name.XXXXXX"`, not BSD-only `mktemp -t`. Find the `.rvp` after the Gradle build. mitmproxy 12 needs Python 3.12 or newer. `scripts/setup.sh` checks the toolchain and creates the local working files.
