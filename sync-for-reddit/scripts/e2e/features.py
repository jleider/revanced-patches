#!/usr/bin/env python3
"""Feature e2e suite: one scenario per patch, plus side-effect checks on shared hooks.

Every scenario drives the real UI (uiautomator2) on the --debug build, then asserts on:
  * Sync's request log (logcat tags URL / RedditRequest / reddit_sync, enabled by "Enable debug logging"),
  * Glide/Volley errors in logcat,
  * the hosts the app connected to (scripts/e2e/netcap.sh: passthrough proxy, nothing decrypted),
  * crashes and the foreground activity.

    scripts/e2e/features.sh [scenario ...]     # default: all; names listed in SCENARIOS
Test posts are found live at run time (scripts/e2e/live.py); a scenario whose post kind
currently doesn't exist on Reddit is reported as SKIP (inconclusive).
"""
import ast
import inspect
import json
import os
import threading
import uuid
import re
import subprocess
import tempfile
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import driver as D  # noqa: E402
import live as L  # noqa: E402  (live posts found at run time; nothing hard-coded)

PKG = D.PKG
ROOT = D.ROOT
NETCAP = str(ROOT / "scripts/e2e/netcap.sh")
if os.environ.get('SYNC_HOSTLOG'):
    HOSTLOG = Path(os.environ['SYNC_HOSTLOG'])
else:
    hostfd, hostpath = tempfile.mkstemp(prefix="sync-hosts.")
    os.close(hostfd)
    HOSTLOG = Path(hostpath)
DEAD_HOSTS = ["gfycat.com", "ap.syncforreddit.com", "images.syncforreddit.com", "revanced.invalid",
              # app-measurement.com is left out: the capture is device-wide and Google Play services
              # uploads other apps' analytics there. The telemetry scenario checks Sync's own state.
              "api.pushshift.io", "crashlytics", "firebase-settings",
              "cloudfunctions.net", "ssl.reddit.com"]


def adb(*args, check=False):
    serial = D.require_emulator()
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, check=check).stdout


class Fail(Exception):
    pass


class Logcat:
    """One stream per worker; markers fence scenario boundaries without stale matches."""
    def __init__(self):
        serial = D.require_emulator()
        self.condition = threading.Condition()
        self.lines = []
        self.process = subprocess.Popen(['adb', '-s', serial, 'logcat', '-T', '1'],
                                        stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                                        text=True, bufsize=1)
        self.thread = threading.Thread(target=self.read, daemon=True)
        self.thread.start()

    def read(self):
        for line in self.process.stdout:
            with self.condition:
                self.lines.append(line)
                self.condition.notify_all()

    def wait(self, pattern, timeout):
        deadline = time.monotonic() + timeout
        with self.condition:
            while not re.search(pattern, ''.join(self.lines)):
                remaining = deadline - time.monotonic()
                if remaining <= 0 or self.process.poll() is not None:
                    return False
                self.condition.wait(remaining)
        return True

    def reset(self):
        marker = uuid.uuid4().hex
        adb('shell', 'log', '-t', 'SyncE2E', marker, check=True)
        if not self.wait(marker, 5):
            raise RuntimeError('logcat stream did not acknowledge scenario boundary')
        with self.condition:
            index = next(i for i, line in enumerate(self.lines) if marker in line)
            self.lines = self.lines[index + 1:]

    def close(self):
        self.process.terminate()
        self.process.wait(timeout=5)
        self.thread.join(timeout=5)
        self.process.stdout.close()


class Ctx:
    def __init__(self, d):
        self.d = d
        self.events = Logcat()
        self.cleanup = []   # run after the scenario's health check, pass or fail

    def cold(self):
        adb("shell", "am", "force-stop", PKG)
        adb("logcat", "-c")
        adb("logcat", "-b", "crash", "-c")
        HOSTLOG.write_text("")
        self.events.reset()

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
            self.d(textContains="servers are having issues").wait_gone(timeout=2)

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
        # Use the streaming reader for wakeups; keep URL extraction/assertion semantics.
        self.events.wait(pattern, timeout)
        urls = self.urls()
        self.expect(any(re.search(pattern, u) for u in urls),
                    (what or f"no request matching {pattern}") + f"; saw {urls[-6:]}")

    def wait_log(self, pattern, timeout=15):
        # Callers retain their original assertions after waiting for their evidence.
        self.events.wait(pattern, timeout)

    def poll(self, predicate, timeout=10):
        deadline = time.monotonic() + timeout
        while not predicate() and time.monotonic() < deadline:
            threading.Event().wait(.2)

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

    def expect_playing(self, audio=False, timeout=20):
        if audio:
            self.poll(audio_players, timeout=timeout)
            self.expect(audio_players(), "no audio player started for Sync")
            return
        # Silent videos need visible progress, not just a logged source or TCP connection.
        timer = self.d(textMatches=r"\d\d:\d\d / \d\d:\d\d")
        self.expect(timer.wait(timeout=timeout), "video time not shown")
        def seconds(text):
            minutes, seconds = map(int, text.split(':'))
            return minutes * 60 + seconds
        initial, duration = map(seconds, timer.get_text().split(' / '))
        def advanced():
            if not timer.exists:
                return False
            current, total = map(seconds, timer.get_text().split(' / '))
            # Short clips loop; a wrap past the duration is forward progress too.
            # No seeking or playback taps occur between these two observations.
            return total == duration and duration > 0 and (current - initial) % duration > 0
        self.poll(advanced, timeout=timeout)
        self.expect(advanced(), "video playback time did not advance")

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
    c.open(L.link_post())
    D.tap(c.d, resourceId=PKG + ":id/menu_comments_more")
    D.tap(c.d, textContains="Discussions")
    c.expect_url(r"oauth\.reddit\.com/search\.json\?q=url%3A", "other discussions did not use url: search")
    c.expect_ui(resourceId=PKG + ":id/title")


@scenario
def gallery(c):
    c.open(L.gallery())
    c.expect_no_url(r"api/info\.json", "gallery still uses blocked /api/info")
    c.expect("Could not load gallery" not in c.log(), "gallery failed to load")


@scenario
def imgur_direct(c):
    c.open(L.imgur_direct())
    c.tap_media()
    c.expect("ImageViewer" in c.top_activity() or "SingleImageActivity" in c.top_activity(), "image viewer did not open")


@scenario
def imgur_page(c):
    # imgur.com/<id> posts: Sync rewrites these to i.imgur.com while parsing (no Imgur API call),
    # so this only guards direct Imgur loading through the Glide interceptors.
    c.open(L.imgur_page())
    c.tap_media()
    log = c.log()
    c.expect("UnknownHostException" not in log and "revanced.invalid" not in "".join(c.hosts()),
             "imgur local response leaked to the network")
    c.expect("i.imgur.com" in " ".join(c.hosts()) or "i.imgur.com" in log, "imgur image was never fetched")


@scenario
def imgur_gif(c):
    c.open(L.imgur_gif())
    c.tap_media()
    c.expect("i.imgur.com" in " ".join(c.hosts()) or "i.imgur.com" in c.log(), "imgur gif was never fetched")


@scenario
def imgur_album(c):
    # Fix Imgur: albums load imgur.com/a/<id>/embed (no API key) and the inline JSON is converted for
    # Sync's album parser, so the album must open in Sync's own viewer (MultiImageActivity with a
    # page counter) instead of falling back to the browser. Also the regression test for the review's
    # P1: the local revanced.invalid URL must never reach the network.
    album, count = L.imgur_album()
    c.open(album)
    c.tap_media()
    c.d(textMatches=rf"^\d+ / {count}$").wait(timeout=8)
    c.expect("revanced.invalid" not in " ".join(c.hosts()), "local Imgur request leaked to the network")
    c.expect("imgur.com" in " ".join(c.hosts()), "the album embed page was never requested")
    top = re.search(r"topResumedActivity=\S+ \S+ (\S+)", c.top_activity())
    c.expect(top and "MultiImageActivity" in top.group(1), f"album did not open in Sync's viewer: {top and top.group(1)}")
    # A multi-image album shows a page counter with the album's image count.
    c.expect_ui(textMatches=rf"^\d+ / {count}$")


@scenario
def gfycat_with_copy(c):
    post, vid = L.gfycat_with_copy()
    c.open(post)
    c.tap_media()
    c.wait_log(rf"(EXOPLAYER SOURCE|Source): https://v\.redd\.it/{vid}/DASHPlaylist\.mpd")
    c.expect(re.search(rf"(EXOPLAYER SOURCE|Source): https://v\.redd\.it/{vid}/DASHPlaylist\.mpd", c.log()), "Reddit's copy of the Gfycat video was not played via DASH")
    c.expect_playing()


def audio_players():
    """Started players owned by Sync's uid in AudioService's playback configurations."""
    uid = adb("shell", "pm", "list", "packages", "-U", PKG).split("uid:")[-1].strip()
    out = adb("shell", "dumpsys", "audio")
    return [l for l in out.splitlines() if "AudioPlaybackConfiguration" in l and f"u/pid:{uid}/" in l
            and "state:started" in l]


@scenario
def reddit_video_dash(c):
    # Fix Reddit video playback: every v.redd.it source plays through DASHPlaylist.mpd. On 2026-11-16
    # this fixture's HLS playlist answered 403 (signed or not) for several minutes while DASH worked,
    # so the old HLS path couldn't play it. The 403 was transient, so this can't be a negative control;
    # it checks that ExoPlayer only ever gets DASH sources for Reddit video and plays without errors.
    post, vid = L.reddit_video()
    c.open(post)
    c.tap_media()
    c.wait_log(rf"(EXOPLAYER SOURCE|Source): https://v\.redd\.it/{vid}/DASHPlaylist\.mpd")
    log = c.log()
    # Inline autoplay logs "EXOPLAYER SOURCE: <url>", the full-screen viewer "Source: <url>".
    sources = [l for l in log.splitlines() if re.search(r"(EXOPLAYER SOURCE|\bSource): ", l)]
    c.expect(any(f"https://v.redd.it/{vid}/DASHPlaylist.mpd" in l for l in sources), f"video not played through DASH: {sources[-2:]}")
    c.expect(not any("v.redd.it" in l and ".m3u8" in l for l in sources), "an HLS v.redd.it source still reached ExoPlayer")
    c.expect("Playback error" not in log, "ExoPlayer playback error")
    c.expect_playing()


@scenario
def reddit_mp4_audio(c):
    # Direct v.redd.it file links (DASH_*.mp4 / CMAF_*.mp4) must play the DASH playlist, which has
    # the audio track. Sync's own id extraction kept ".mp4" and built a dead playlist URL; the
    # first Gfycat fix played the silent video-only MP4 instead.
    vid, mp4 = L.reddit_video_with_audio()
    c.open(f"https://v.redd.it/{vid}/{mp4}")
    c.wait_log(rf"(EXOPLAYER SOURCE|Source): https://v\.redd\.it/{vid}/DASHPlaylist\.mpd")
    c.poll(audio_players, timeout=6)
    log = c.log()
    c.expect(re.search(rf"(EXOPLAYER SOURCE|Source): https://v\.redd\.it/{vid}/DASHPlaylist\.mpd", log), "direct MP4 link did not play the DASH playlist")
    c.expect(not re.search(rf"{vid}\.mp4", log), "broken Sync playlist id (with .mp4) used")
    c.expect(audio_players(), "no audio player started for Sync")
    c.expect_playing(audio=True)


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
    c.open(L.http_tenor_selftext())
    c.d.swipe(540, 1800, 540, 700, 0.3)
    time.sleep(8)
    plain = sorted({l.split()[2] for l in HOSTLOG.read_text().splitlines()
                    if len(l.split()) > 2 and l.split()[2].endswith(":80") and not l.split()[2].startswith(OS_HTTP_HOSTS)})
    c.expect(not plain, f"plain http connections: {plain}")
    c.expect(any(h.startswith("tenor.com") or h.startswith("media.tenor.com") or h.startswith("media1.tenor.com")
                 for h in c.hosts()), "the Tenor preview was never fetched (fixture or scroll problem)")


@scenario
def redgifs(c):
    # Fix Redgifs API: Sync calls GET api.redgifs.com/info before every lookup; Redgifs removed it (404,
    # "Error connecting to Redgifs"). It must be answered locally, then the lookup and playback proceed.
    c.open(L.redgifs())
    c.tap_media()
    c.wait_log(r"(EXOPLAYER SOURCE|Source): https://media\.redgifs\.com/\S+\.mp4")
    log = c.log()
    c.expect("Unexpected response code 404 for https://api.redgifs.com/info" not in log, "Redgifs /info still went to the network (404)")
    c.expect("Error connecting to Redgifs" not in log, "Sync reported 'Error connecting to Redgifs'")
    c.expect(re.search(r"(EXOPLAYER SOURCE|\bSource): https://media\.redgifs\.com/\S+\.mp4", log), "no Redgifs MP4 reached the player")
    c.expect("Playback error" not in log, "ExoPlayer playback error")
    c.expect_playing()


@scenario
def youtube_in_app(c):
    # Fix YouTube player: current YouTube apps no longer ship the Player API service Sync's player needs
    # (Google's player then shows "An error occurred while initializing the YouTube player" or "get the
    # YouTube app"). Disabling only that service inside YouTube (root) reproduces a current phone exactly:
    # YouTube installed, service missing. Sync must play the video in-app with the embed player.
    service = "com.google.android.youtube/com.google.android.youtube.api.service.YouTubeService"
    adb("shell", "su", "0", "pm", "disable", service)
    c.cleanup.append(lambda: adb("shell", "su", "0", "pm", "enable", service))
    c.expect("No services found" in adb("shell", "cmd", "package", "query-services", "-a",
                                        "com.google.android.youtube.api.service.START"),
             "could not disable YouTube's player service on the emulator")
    c.open(L.youtube_post())
    c.tap_media()
    c.expect_ui(timeout=15, className="android.webkit.WebView")
    c.poll(lambda: any("googlevideo.com" in h for h in c.hosts()), timeout=15)
    top = re.search(r"topResumedActivity=\S+ \S+ (\S+)", c.top_activity())
    top = top.group(1) if top else ""
    if "CustomYouTubeVideoActivity" not in top:
        # Embedding disabled by the uploader: the fallback opens the YouTube app; that's allowed but
        # doesn't test the player, so report it instead of passing silently.
        raise Fail(f"video left Sync ({top}); pick of a non-embeddable video? re-run")
    c.expect(not c.d(textContains="error occurred").exists and not c.d(textContains="YouTube app").exists,
             "Google's player error is shown")
    hosts = " ".join(c.hosts())
    c.expect("googlevideo.com" in hosts, f"no video stream from googlevideo.com (embed not playing): {hosts[:200]}")
    c.expect_playing(audio=True)
    # "Block YouTube ads" (default on): the embed page must be the injected, ad-free one. Removed ad
    # fields are logged (informational: whether a video has ads at all varies).
    log = c.log()
    c.expect("ad-free embed page loaded" in log, "the ad-free embed page was not used")
    removed = sorted(set(re.findall(r"SYNC_YT_AD_REMOVED:(\w+)", log)))
    print(f"  youtube_in_app: ad fields removed: {removed or 'none (video had no ads)'}")


@scenario
def gfycat_without_copy(c):
    c.open(L.gfycat_without_copy())
    t = time.time()
    c.tap_media()
    c.expect(time.time() - t < 15, "Gfycat failure took too long")


@scenario
def link_previews(c):
    for key, post in (("tenor_selftext", L.tenor_selftext()), ("imgur_album_selftext", L.imgur_album_selftext())):
        c.open(post)
        c.d.swipe(540, 1800, 540, 700, 0.3)
        time.sleep(5)
        failed = re.findall(r"Load failed for https://ap\.syncforreddit\.com/image\?url=(\S+)", c.log())
        real = [u for u in failed if "embed.js" not in u and "search/" not in u]
        c.expect(not real, f"{key}: preview failed for {real[:2]}")
        c.healthy()


@scenario
def deviantart(c):
    c.open(L.deviantart())
    c.tap_media()
    log = c.log()
    c.expect("http://backend.deviantart.com" not in log, "DeviantArt oEmbed still uses http")
    c.expect("backend.deviantart.com" in " ".join(c.hosts()) or "deviantart" in log, "DeviantArt lookup never happened")


@scenario
def restore_comment_hidden(c):
    # "Restore comment" is offered only on [deleted]/[removed] comments; the live thread has one near
    # the top. The dialog must open (so the check isn't vacuous) without the item.
    c.open(L.removed_comment_thread())
    removed = c.d(resourceId=PKG + ":id/comment_row_content", textMatches=r"\[(removed|deleted)\]")
    for _ in range(6):
        if removed.wait(timeout=3):
            break
        c.d.swipe(540, 1900, 540, 900, 0.3)
    c.expect(removed.exists, "no [removed]/[deleted] comment visible in the live thread")
    # The action bar opens below the comment: bring it toward the middle so the bar is on screen.
    if removed.info["bounds"]["top"] > 1200:
        c.d.swipe(540, removed.info["bounds"]["top"], 540, 700, 0.4)
        time.sleep(1)
    removed.click()
    D.tap(c.d, timeout=5, resourceId=PKG + ":id/comment_actions_more")
    c.expect_ui(textStartsWith="Comment options")
    c.expect_no_ui(text="Restore comment")


@scenario
def load_more_comments(c):
    # Sync auto-loads top-level "View more (N)" rows when they scroll into view, and the user taps
    # nested ones. Either way the request must be a comment permalink, never /api/morechildren.
    c.open(L.big_thread())
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
    c.open(L.link_post())
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
    # Each "View more" row is fetched once: one batch, then one request per distinct parent whose row
    # scrolled into view. The count depends on the live thread's shape, so the invariant is "no URL
    # twice" (a chaining/looping regression repeats URLs), plus a loose bound for one scroll burst.
    repeats = sorted({u for u in requests if requests.count(u) > 1})
    c.expect(not repeats, f"load-more requested the same comments repeatedly: {repeats[:3]}")
    c.expect(len(requests) <= 40, f"{len(requests)} load-more requests for one scroll burst")
    c.expect(c.d(resourceId=PKG + ":id/comment_row_content").exists, "comments vanished")


@scenario
def telemetry(c):
    # Firebase Analytics debug logging makes Sync's SDK state attributable (the host capture is
    # device-wide, so app-measurement.com traffic can't be pinned on Sync).
    adb("shell", "setprop", "debug.firebase.analytics.app", PKG)
    adb("shell", "setprop", "log.tag.FA", "VERBOSE")
    try:
        c.home()
        c.wait_log("App measurement deactivated via the manifest", timeout=10)
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
    D.require_emulator()
    # Discover only dependencies of the selected scenarios; workers find live posts concurrently.
    finders = set()
    for name in names:
        for node in ast.walk(ast.parse(inspect.getsource(SCENARIOS[name]))):
            if (isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute)
                    and isinstance(node.func.value, ast.Name) and node.func.value.id == 'L'
                    and node.func.attr in L.FINDERS):
                finders.add(node.func.attr)
    L.prefetch(finders)
    # Fail fast on a device without network (e.g. airplane mode left on by fuzzing).
    if adb("shell", "settings", "get", "global", "airplane_mode_on").strip() == "1":
        sys.exit("FAIL: emulator is in airplane mode (scripts/e2e/monkey.sh resets this; or: adb shell cmd connectivity airplane-mode disable)")
    if "1 received" not in adb("shell", "ping", "-c", "1", "-W", "3", "8.8.8.8"):
        sys.exit("FAIL: emulator has no network")
    status = reddit_status()
    if status:
        print(f"WARN Reddit reports an incident ({status}); failures may be Reddit's, re-run later")
    subprocess.run([NETCAP, "start", str(HOSTLOG)], check=True, capture_output=True)
    try:
        d = D.connect()
    except BaseException:
        subprocess.run([NETCAP, "stop"], capture_output=True)
        raise
    # Sync's outage sheet can appear at any time while reddit.statuspage.io reports an incident.
    d.watcher("reddit-outage").when("//*[contains(@text, 'servers are having issues')]").when("Close").click()
    d.watcher.start(0.5)
    try:
        c = Ctx(d)
    except BaseException:
        d.watcher.stop()
        subprocess.run([NETCAP, "stop"], capture_output=True)
        raise
    passed = failed = skipped = 0
    try:
        for name in names:
            t = time.time()
            try:
                SCENARIOS[name](c)
                c.healthy()
                print(f"PASS {name} ({time.time() - t:.0f}s)")
                passed += 1
            except L.NotFound as e:
                print(f"SKIP {name}: no suitable live post right now ({e})")
                skipped += 1
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
        c.events.close()
        d.watcher.stop()
        subprocess.run([NETCAP, "stop"], capture_output=True)
        adb("shell", "am", "force-stop", PKG)
    print(f"{passed} passed, {failed} failed" + (f", {skipped} skipped (inconclusive)" if skipped else ""))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main(sys.argv[1:])
