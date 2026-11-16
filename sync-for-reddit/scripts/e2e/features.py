#!/usr/bin/env python3
"""Feature e2e suite: one scenario per patch, plus side-effect checks on shared hooks.

Every scenario drives the real UI (uiautomator2) on the --debug build, then asserts on:
  * Sync's request log (logcat tags URL / RedditRequest / reddit_sync, enabled by "Enable debug logging"),
  * Glide/Volley errors in logcat,
  * the hosts the app connected to (scripts/e2e/netcap.sh: passthrough proxy, nothing decrypted),
  * crashes and the foreground activity.

    scripts/e2e/features.sh [scenario ...]     # default: all; names listed in SCENARIOS
Fixture posts live in tests/fixtures/e2e_posts.json.
"""
import json
import re
import subprocess
import tempfile
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import driver as D  # noqa: E402

PKG = D.PKG
ROOT = D.ROOT
POSTS = json.loads((ROOT / "tests/fixtures/e2e_posts.json").read_text())
NETCAP = str(ROOT / "scripts/e2e/netcap.sh")
HOSTLOG = Path(tempfile.mkstemp(prefix="sync-hosts.")[1])   # portable (BSD and GNU)
DEAD_HOSTS = ["gfycat.com", "ap.syncforreddit.com", "images.syncforreddit.com", "revanced.invalid",
              # app-measurement.com is left out: the capture is device-wide and Google Play services
              # uploads other apps' analytics there. The telemetry scenario checks Sync's own state.
              "api.pushshift.io", "crashlytics", "firebase-settings",
              "cloudfunctions.net", "ssl.reddit.com"]


def adb(*args, check=False):
    return subprocess.run(["adb", *args], capture_output=True, text=True, check=check).stdout


class Fail(Exception):
    pass


class Ctx:
    def __init__(self, d):
        self.d = d
        self.cleanup = []   # run after the scenario's health check, pass or fail

    def cold(self):
        adb("shell", "am", "force-stop", PKG)
        adb("logcat", "-c")
        adb("logcat", "-b", "crash", "-c")
        HOSTLOG.write_text("")

    def open(self, path):
        self.cold()
        url = path if path.startswith("http") else "https://www.reddit.com" + path
        adb("shell", "am", "start", "-W", "-a", "android.intent.action.VIEW", "-d", url, "-p", PKG)
        time.sleep(6)
        self.dismiss_outage()

    def dismiss_outage(self):
        # Sync polls reddit.statuspage.io and shows a "Reddit servers are having issues" bottom
        # sheet during incidents. It covers the screen, so close it; main() reports the incident.
        if self.d(textContains="servers are having issues").exists:
            self.d(text="Close").click_exists(timeout=2)
            time.sleep(1)

    def home(self):
        self.cold()
        D.home(self.d)
        time.sleep(5)
        self.dismiss_outage()

    def log(self):
        return adb("logcat", "-d")

    def urls(self):
        # Sync's debug logger prints request URLs in several message formats; collect every
        # Reddit API/media URL that appears anywhere in this scenario's log.
        found = re.findall(r"https?://(?:oauth|www|ssl|old)\.reddit\.com/[^\s\"'<>]+", self.log())
        return list(dict.fromkeys(found))

    def hosts(self):
        return {line.split()[2].rsplit(":", 1)[0] for line in HOSTLOG.read_text().splitlines() if len(line.split()) > 2}

    # --- assertions
    def expect(self, ok, what):
        if not ok:
            raise Fail(what)

    def expect_url(self, pattern, what=None, timeout=15):
        # Poll: a restored baseline has no cached token, so the first request can take a while.
        end = time.time() + timeout
        while True:
            urls = self.urls()
            if any(re.search(pattern, u) for u in urls):
                return
            if time.time() > end:
                raise Fail((what or f"no request matching {pattern}") + f"; saw {urls[-6:]}")
            time.sleep(1)

    def expect_no_url(self, pattern, what=None):
        bad = [u for u in self.urls() if re.search(pattern, u)]
        self.expect(not bad, (what or f"unexpected request {pattern}") + f": {bad[:3]}")

    def expect_ui(self, timeout=8, **sel):
        end = time.time() + timeout
        while True:
            self.dismiss_outage()
            if self.d(**sel).wait(timeout=1):
                return
            if time.time() > end:
                raise Fail(f"UI element {sel} not shown")

    def expect_no_ui(self, **sel):
        self.expect(not self.d(**sel).exists, f"UI element {sel} should not be shown")

    def healthy(self):
        crash = adb("logcat", "-d", "-b", "crash")
        self.expect(PKG not in crash, "crash: " + " ".join(crash.split("\n")[:3])[:200])
        self.expect(adb("shell", "pidof", PKG).strip() != "", "process died")
        dead = sorted(h for h in self.hosts() if any(x in h for x in DEAD_HOSTS))
        self.expect(not dead, f"connected to dead/telemetry hosts: {dead}")
        self.expect("theme-beta" not in adb("logcat", "-d", "-s", "reddit_sync:D", "RedditRequest:D"),
                    "Reddit HTML block page in a response")

    def tap_media(self):
        D.tap(self.d, resourceId=PKG + ":id/image")
        time.sleep(5)

    def top_activity(self):
        return adb("shell", "dumpsys", "activity", "activities")


SCENARIOS = {}


def scenario(fn):
    SCENARIOS[fn.__name__] = fn
    return fn


# ---------------------------------------------------------------- patches

@scenario
def combined_subreddits(c):
    c.open("/r/pics+funny")
    c.expect_url(r"oauth\.reddit\.com/r/pics%2Bfunny/hot\.json", "combined feed not requested with %2B")
    c.expect_ui(resourceId=PKG + ":id/title")
    c.expect_no_ui(textContains="Error loading page")


@scenario
def random_subreddit(c):
    c.home()
    seen = set()
    for _ in range(2):
        D.tap(c.d, text="Random")
        time.sleep(5)
        title = c.d(resourceId=PKG + ":id/toolbar_title_large")
        c.expect(title.exists, "no subreddit opened after Random")
        name = title.get_text()
        c.expect(name.lower() not in ("random", "frontpage"), f"Random opened '{name}'")
        seen.add(name)
        c.d.press("back")
        time.sleep(2)
    c.expect_url(r"subreddits/popular\.json\?limit=100&revanced_random=sfw")
    c.expect_no_url(r"/r/random\.json")


@scenario
def other_discussions(c):
    c.open(POSTS["link_post"])
    D.tap(c.d, resourceId=PKG + ":id/menu_comments_more")
    D.tap(c.d, textContains="Discussions")
    time.sleep(6)
    c.expect_url(r"oauth\.reddit\.com/search\.json\?q=url%3A", "other discussions did not use url: search")
    c.expect_ui(resourceId=PKG + ":id/title")


@scenario
def gallery(c):
    c.open(POSTS.get("gallery", "") or "/r/pics/comments/" + json.loads(
        (ROOT / "tests/fixtures/gallery_comments_endpoint.json").read_text())[0]["data"]["children"][0]["data"]["id"] + "?is_gallery=true")
    c.expect_no_url(r"api/info\.json", "gallery still uses blocked /api/info")
    c.expect("Could not load gallery" not in c.log(), "gallery failed to load")


@scenario
def imgur_direct(c):
    c.open(POSTS["imgur_direct"])
    c.tap_media()
    c.expect("ImageViewer" in c.top_activity() or "SingleImageActivity" in c.top_activity(), "image viewer did not open")


@scenario
def imgur_page(c):
    # imgur.com/<id> posts: Sync rewrites these to i.imgur.com while parsing (no Imgur API call),
    # so this only guards direct Imgur loading through the Glide interceptors.
    c.open(POSTS["imgur_page"])
    c.tap_media()
    log = c.log()
    c.expect("UnknownHostException" not in log and "revanced.invalid" not in "".join(c.hosts()),
             "imgur local response leaked to the network")
    c.expect("i.imgur.com" in " ".join(c.hosts()) or "i.imgur.com" in log, "imgur image was never fetched")


@scenario
def imgur_gif(c):
    c.open(POSTS["imgur_gif"])
    c.tap_media()
    c.expect("i.imgur.com" in " ".join(c.hosts()) or "i.imgur.com" in c.log(), "imgur gif was never fetched")


@scenario
def imgur_album(c):
    # Fix Imgur: albums load imgur.com/a/<id>/embed (no API key) and the inline JSON is converted for
    # Sync's album parser, so the album must open in Sync's own viewer (MultiImageActivity with a
    # page counter) instead of falling back to the browser. Also the regression test for the review's
    # P1: the local revanced.invalid URL must never reach the network.
    c.open(POSTS["imgur_album"])
    c.tap_media()
    time.sleep(4)
    c.expect("revanced.invalid" not in " ".join(c.hosts()), "local Imgur request leaked to the network")
    c.expect("imgur.com" in " ".join(c.hosts()), "the album embed page was never requested")
    top = re.search(r"topResumedActivity=\S+ \S+ (\S+)", c.top_activity())
    c.expect(top and "MultiImageActivity" in top.group(1), f"album did not open in Sync's viewer: {top and top.group(1)}")
    # imgur_album is a 4-image album, so the viewer shows a page counter.
    c.expect_ui(textMatches=r"^\d+ / 4$")


@scenario
def gfycat_with_copy(c):
    c.open(POSTS["gfycat_with_copy"])
    c.tap_media()
    time.sleep(3)
    vid = POSTS["gfycat_with_copy_mp4"].split("/")[0]
    c.expect(f"v.redd.it/{vid}/HLSPlaylist.m3u8" in c.log(), "Reddit's copy of the Gfycat video was not played via HLS")


def audio_players():
    """Started players owned by Sync's uid in AudioService's playback configurations."""
    uid = adb("shell", "pm", "list", "packages", "-U", PKG).split("uid:")[-1].strip()
    out = adb("shell", "dumpsys", "audio")
    return [l for l in out.splitlines() if "AudioPlaybackConfiguration" in l and f"u/pid:{uid}/" in l
            and "state:started" in l]


@scenario
def reddit_mp4_audio(c):
    # Direct v.redd.it file links (DASH_*.mp4 / CMAF_*.mp4) must play the HLS playlist, which has
    # the audio track. Sync's own id extraction kept ".mp4" and built a dead playlist URL; the
    # first Gfycat fix played the silent video-only MP4 instead.
    vid = POSTS["reddit_video_with_audio"]
    c.open(f"https://v.redd.it/{vid}/CMAF_480.mp4")
    time.sleep(6)
    log = c.log()
    c.expect(f"v.redd.it/{vid}/HLSPlaylist.m3u8" in log, "direct MP4 link did not switch to the HLS playlist")
    c.expect(not re.search(rf"{vid}\.mp4", log), "broken Sync playlist id (with .mp4) used")
    c.expect(audio_players(), "no audio player started for Sync")


@scenario
def slink_token_guard(c):
    # Fix /s/ links' shared resolver matched ".*reddit\.com/r/x/s/y" and sent a HEAD request with the
    # user's bearer token to any host. A lookalike link must take Sync's normal path instead.
    # (SEND text/plain reaches LinkHelper, the hooked method, from any app.) The shared resolver
    # always logs "Failed to resolve <link>" when it tried; example.com returns no Location.
    bad = "https://example.com/x.reddit.com/r/pics/s/AbC123"
    c.cold()
    # Two Sync activities take SEND (Submit and Intent); name the link handler or a chooser opens.
    adb("shell", "am", "start", "-W", "-a", "android.intent.action.SEND", "-t", "text/plain",
        "--es", "android.intent.extra.TEXT", bad, "-n", PKG + "/.ui.activities.IntentActivity")
    time.sleep(6)
    c.dismiss_outage()
    log = c.log()
    c.expect(not re.search(r"[Rr]esolv\w* https://example\.com", log), "lookalike /s/ link was resolved with the token")


# Port-80 connections the OS makes on its own (captive-portal checks); not Sync's.
OS_HTTP_HOSTS = ("connectivitycheck.gstatic.com", "www.google.com", "clients3.google.com", "play.googleapis.com")


@scenario
def https_upgrade(c):
    # Upgrade connections to HTTPS. Sync already rewrites most media to https itself (e.g. Imgur), so
    # the reliable http source is a link preview: http_tenor_selftext links "http://tenor.com/view/...",
    # which the inline preview fetched over port 80 before the patch (proven by negative_control.sh).
    c.open(POSTS["http_tenor_selftext"])
    c.d.swipe(540, 1800, 540, 700, 0.3)
    time.sleep(8)
    plain = sorted({l.split()[2] for l in HOSTLOG.read_text().splitlines()
                    if len(l.split()) > 2 and l.split()[2].endswith(":80") and not l.split()[2].startswith(OS_HTTP_HOSTS)})
    c.expect(not plain, f"plain http connections: {plain}")
    c.expect(any(h.startswith("tenor.com") or h.startswith("media.tenor.com") or h.startswith("media1.tenor.com")
                 for h in c.hosts()), "the Tenor preview was never fetched (fixture or scroll problem)")


@scenario
def gfycat_without_copy(c):
    c.open(POSTS["gfycat_without_copy"])
    t = time.time()
    c.tap_media()
    c.expect(time.time() - t < 15, "Gfycat failure took too long")


@scenario
def link_previews(c):
    for key in ("tenor_selftext", "imgur_album_selftext"):
        c.open(POSTS[key])
        c.d.swipe(540, 1800, 540, 700, 0.3)
        time.sleep(5)
        failed = re.findall(r"Load failed for https://ap\.syncforreddit\.com/image\?url=(\S+)", c.log())
        real = [u for u in failed if "embed.js" not in u and "search/" not in u]
        c.expect(not real, f"{key}: preview failed for {real[:2]}")
        c.healthy()


@scenario
def deviantart(c):
    c.open(POSTS["deviantart"])
    c.tap_media()
    log = c.log()
    c.expect("http://backend.deviantart.com" not in log, "DeviantArt oEmbed still uses http")
    c.expect("backend.deviantart.com" in " ".join(c.hosts()) or "deviantart" in log, "DeviantArt lookup never happened")


@scenario
def restore_comment_hidden(c):
    # "Restore comment" is offered only on [deleted]/[removed] comments. imgur_page's thread has a
    # [removed] top comment. The dialog must open (so the check isn't vacuous) without the item.
    c.open(POSTS["imgur_page"])
    removed = c.d(resourceId=PKG + ":id/comment_row_content", textMatches=r"\[(removed|deleted)\]")
    c.expect(removed.wait(timeout=8), "fixture thread has no [removed]/[deleted] comment any more")
    removed.click()
    D.tap(c.d, timeout=5, resourceId=PKG + ":id/comment_actions_more")
    c.expect_ui(textStartsWith="Comment options")
    c.expect_no_ui(text="Restore comment")


@scenario
def load_more_comments(c):
    # Sync auto-loads top-level "View more (N)" rows when they scroll into view, and the user taps
    # nested ones. Either way the request must be a comment permalink, never /api/morechildren.
    thread = json.loads((ROOT / "tests/fixtures/more_stub.json").read_text())["link_id"]
    c.open(f"https://www.reddit.com/comments/{thread}")
    c.expect_ui(resourceId=PKG + ":id/comment_row_content")
    permalink = r"oauth\.reddit\.com/comments/[a-z0-9]+(/_/[a-z0-9]+)?\?limit=500&depth=\d+"
    rows = lambda: {e.get_text() for e in c.d(resourceId=PKG + ":id/comment_row_content") if e.get_text()}
    clicked = None
    for _ in range(30):
        more = c.d(textMatches=r"View more \(\d+\)")
        if more.exists:
            clicked = (more.get_text(), more.info["bounds"])
            before = rows()
            more.click()
            time.sleep(5)
            break
        if any(re.search(permalink, u) for u in c.urls()):
            break  # auto-loaded
        c.d.swipe(540, 1900, 540, 600, 0.2)
        time.sleep(0.5)
    c.expect_url(permalink, "load more did not request a comment permalink")
    c.expect_no_url(r"api/morechildren", "load more still uses blocked morechildren")
    requests = [u for u in c.urls() if re.search(permalink, u)]
    c.expect(len(requests) <= 6, f"load more fired {len(requests)} requests (chaining?)")
    if clicked:
        # The request alone proves nothing: if the response could not be parsed, Sync keeps the old
        # row. The tapped row must be replaced and comments not on screen before must appear.
        text, bounds = clicked
        still = [e for e in c.d(text=text) if e.info["bounds"] == bounds]
        c.expect(not still, f"'{text}' row is still there: the loaded comments were not inserted")
        c.expect(rows() - before, "no new comments appeared after load more")


@scenario
def load_more_big_thread(c):
    # Big thread: scrolling to the bottom triggers top-level auto-loading. Batch mode should
    # fetch the post once per "View more" row instead of one request per hidden comment.
    c.open(POSTS["link_post"])
    c.expect_ui(resourceId=PKG + ":id/comment_row_content")
    for _ in range(60):
        c.d.swipe(540, 2000, 540, 300, 0.1)
    time.sleep(6)
    permalink = r"oauth\.reddit\.com/comments/[a-z0-9]+(/_/[a-z0-9]+)?\?limit=500&depth=\d+"
    requests = [u for u in c.urls() if re.search(permalink, u)]
    c.expect(requests, "no load-more request after scrolling a 1000+ comment thread")
    c.expect_no_url(r"api/morechildren")
    # First request must be one batch for the whole post; Reddit caps a response at ~300 roots, so
    # the tail beyond that is fetched per comment only while the row stays on screen.
    c.expect(re.search(r"/comments/[a-z0-9]+\?limit=500&depth=3", requests[0]), f"first load-more was not a batch: {requests[0]}")
    c.expect(len(requests) <= 15, f"{len(requests)} load-more requests for one scroll burst")
    c.expect(c.d(resourceId=PKG + ":id/comment_row_content").exists, "comments vanished")


@scenario
def telemetry(c):
    # Firebase Analytics debug logging makes Sync's SDK state attributable (the host capture is
    # device-wide, so app-measurement.com traffic can't be pinned on Sync).
    adb("shell", "setprop", "debug.firebase.analytics.app", PKG)
    adb("shell", "setprop", "log.tag.FA", "VERBOSE")
    try:
        c.home()
        time.sleep(6)
        pid = adb("shell", "pidof", PKG).strip()
        fa = [l for l in adb("logcat", "-d", "-s", "FA:V").splitlines() if f" {pid} " in l]
        c.expect(any("App measurement deactivated via the manifest" in l for l in fa),
                 "Sync's Firebase Analytics is not deactivated: " + " | ".join(fa[:3]))
        c.expect(not any("Logging event" in l and "not sent" not in l for l in fa), "Sync logged an analytics event")
    finally:
        adb("shell", "setprop", "debug.firebase.analytics.app", ".none.")
        adb("shell", "setprop", "log.tag.FA", "INFO")
    log = c.log()
    c.expect("hmm detected" not in log, "hmmm report fired")
    bad = [h for h in c.hosts() if re.search(r"crashlytics|firebase-settings|cloudfunctions", h)]
    c.expect(not bad, f"telemetry hosts contacted: {bad}")


# ---------------------------------------------------------------- side effects of shared hooks

@scenario
def search_plus_in_query(c):
    # The RedditRequest URL rewrite (combined subs) must not touch '+' in query strings:
    # Sync encodes spaces in search terms as '+'.
    c.home()
    D.tap(c.d, resourceId=PKG + ":id/menu_search")
    D.set_text(c.d, "cats dogs", resourceId=PKG + ":id/search_edit_text")
    c.d.press("enter")
    time.sleep(6)
    c.expect_url(r"oauth\.reddit\.com/search\.json\?q=cats\+dogs&", "search query '+' was altered")
    c.expect_no_url(r"q=cats%2Bdogs", "search query '+' was percent-encoded")


@scenario
def user_profile(c):
    c.open("/user/spez")
    c.expect_url(r"user/spez", "profile not requested")
    c.expect_no_ui(textContains="Error loading page")


@scenario
def subreddit_and_wiki(c):
    c.open("/r/Android")
    c.expect_url(r"(?i)oauth\.reddit\.com/r/android/hot\.json")
    c.open("/r/Android/wiki/index")
    c.expect_url(r"(?i)oauth\.reddit\.com/(r/android/)?wiki/index")


@scenario
def images_load(c):
    # The link-preview interceptor sits on Glide's OkHttp client: ordinary images must still load.
    c.open("/r/pics")
    time.sleep(4)
    failed = [l for l in c.log().splitlines() if "Load failed for http" in l and "ap.syncforreddit.com" not in l]
    c.expect(len(failed) <= 1, f"{len(failed)} ordinary image loads failed: {failed[:2]}")


def set_int_pref(name, value):
    """Set an <int> in Sync's main shared prefs via root (app stopped). The baseline restore undoes it."""
    f = f"/data/data/{PKG}/shared_prefs/{PKG}_preferences.xml"
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "su", "0", "sed", "-i",
        f"'/name=\"{name}\"/d; s#<map>#<map>\\n    <int name=\"{name}\" value=\"{value}\" />#'", f)


@scenario
def images_load_list_layout(c):
    # List layout (ui_mode_new=1) shows 140px thumbnails from preview.images[].resolutions, which
    # Sync gets HTML-escaped (no raw_json=1). preview.redd.it answers "&amp;" queries with 403 since
    # 2026-11-16; Fix Reddit image URLs unescapes them. Card layout (the baseline) doesn't hit this.
    set_int_pref("ui_mode_new", 1)
    c.cleanup.append(lambda: subprocess.run([str(ROOT / "scripts/e2e/app_state.sh"), "restore"], capture_output=True))
    c.open("/r/pics")
    c.expect_ui(resourceId=PKG + ":id/holder_list_title")
    time.sleep(4)
    forbidden = [l for l in c.log().splitlines() if "status code: 403" in l]
    failed = [l for l in c.log().splitlines() if "Load failed for https://preview.redd.it" in l]
    c.expect(not failed and not forbidden, f"{len(failed)} preview.redd.it thumbnails failed ({len(forbidden)} 403s): {failed[:1]}")


def reddit_status():
    """Non-operational components on reddit.statuspage.io (what Sync's outage sheet uses), or ''."""
    import urllib.request
    try:
        with urllib.request.urlopen("https://reddit.statuspage.io/api/v2/summary.json", timeout=10) as r:
            d = json.load(r)
        return ", ".join(f"{c['name']}: {c['status']}" for c in d["components"] if c["status"] != "operational")
    except Exception as e:
        return f"status check failed: {e}"


def main(names):
    names = names or list(SCENARIOS)
    unknown = [n for n in names if n not in SCENARIOS]
    if unknown:
        sys.exit(f"unknown scenario(s): {unknown}; known: {list(SCENARIOS)}")
    # Fail fast on a device without network (e.g. airplane mode left on by fuzzing).
    if adb("shell", "settings", "get", "global", "airplane_mode_on").strip() == "1":
        sys.exit("FAIL: emulator is in airplane mode (scripts/e2e/monkey.sh resets this; or: adb shell cmd connectivity airplane-mode disable)")
    if "1 received" not in adb("shell", "ping", "-c", "1", "-W", "3", "8.8.8.8"):
        sys.exit("FAIL: emulator has no network")
    status = reddit_status()
    if status:
        print(f"WARN Reddit reports an incident ({status}); failures may be Reddit's, re-run later")
    subprocess.run([NETCAP, "start", str(HOSTLOG)], check=True, capture_output=True)
    d = D.connect()
    # Sync's outage sheet can appear at any time while reddit.statuspage.io reports an incident.
    d.watcher("reddit-outage").when("//*[contains(@text, 'servers are having issues')]").when("Close").click()
    d.watcher.start(0.5)
    c = Ctx(d)
    passed = failed = 0
    try:
        for name in names:
            t = time.time()
            try:
                SCENARIOS[name](c)
                c.healthy()
                print(f"PASS {name} ({time.time() - t:.0f}s)")
                passed += 1
            except (Fail, RuntimeError) as e:
                print(f"FAIL {name}: {e}")
                try:
                    D.screenshot(d, f"features-{name}.png")
                except Exception:
                    pass
                failed += 1
            finally:
                while c.cleanup:
                    c.cleanup.pop()()
    finally:
        d.watcher.stop()
        subprocess.run([NETCAP, "stop"], capture_output=True)
        adb("shell", "am", "force-stop", PKG)
    print(f"{passed} passed, {failed} failed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main(sys.argv[1:])
