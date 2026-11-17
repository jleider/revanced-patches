"""Checks the in-app YouTube player's injected script (YouTubeWebPlayer.AD_SCRIPT) in headless Chromium.

The script is read from the extension source, so this tests exactly what ships. It mirrors the app: the
embed page is fetched with a reddit.com Referer, the script goes first in <head>, and ad hosts are
refused (YouTubeWebPlayer.isAdRequest). For each video it checks:

  - the script parses and the video starts within 45 s;
  - if ads were removed and the video stalled for 2 s or more, the "Ad blocked" wait notice was shown;
  - the notice is hidden once the video plays.

Videos come from live Reddit YouTube posts (scripts/e2e/live.py); nothing is hard-coded. It tries up
to --max videos and stops at the first that had real ads (adPlacements/playerAds/adSlots) (videos without ads only check playback).

    .venv/bin/python scripts/e2e/youtube_embed.py [--max 6] [--shot DIR] [VIDEO_ID ...]
"""
import argparse
import re
import sys
import time
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import live  # noqa: E402

from playwright.sync_api import sync_playwright  # noqa: E402

SRC = Path(__file__).resolve().parents[3] / "extensions/syncforreddit/src/main/java/app/revanced/extension/syncforreddit/YouTubeWebPlayer.java"
UA = ("Mozilla/5.0 (Linux; Android 16; Pixel 9 Pro Build/BP2A; wv) AppleWebKit/537.36 (KHTML, like Gecko) "
      "Version/4.0 Chrome/139.0.0.0 Mobile Safari/537.36")
AD_PATHS = ("/pagead/", "/api/stats/ads", "/ptracking", "/get_midroll_info", "/pcs/activeview", "/youtubei/v1/player/ad_break")


def java_constant(src, name):
    """A Java String constant built from literals and other constants, unescaped."""
    body = re.search(r"static final String " + name + r" = (.*?);\n", src, re.S).group(1)
    out = ""
    for tok in re.findall(r'"(?:[^"\\]|\\.)*"|[A-Z_]{3,}', body):
        out += (tok[1:-1].encode("latin1", "backslashreplace").decode("unicode_escape")
                if tok.startswith('"') else java_constant(src, tok))
    return out


def is_ad(url):
    m = re.match(r"https?://([^/]+)(/[^?]*)?", url)
    host, path = (m.group(1).lower(), m.group(2) or "") if m else ("", "")
    if host.endswith(("doubleclick.net", "googlesyndication.com", "googleadservices.com")) or host == "imasdk.googleapis.com":
        return True
    return (host == "youtube.com" or host.endswith(".youtube.com")) and path.startswith(AD_PATHS)


def video_ids(limit):
    ids = []
    for path in ("/domain/youtube.com/hot", "/domain/youtu.be/hot", "/r/videos/hot"):
        for p in live.listing(path):
            m = re.search(r"(?:youtube\.com/watch\?(?:.*&)?v=|youtu\.be/)([\w-]{11})", p["url"].replace("&amp;", "&"))
            if m and m.group(1) not in ids:
                st, _ = live.http("https://www.youtube.com/oembed?format=json&url="
                                  + urllib.request.quote("https://www.youtube.com/watch?v=" + m.group(1), safe=""))
                if st == 200:
                    ids.append(m.group(1))
            if len(ids) >= limit:
                return ids
    return ids


def check(browser, vid, script, shot_dir):
    page = browser.new_context(user_agent=UA, viewport={"width": 412, "height": 860}, is_mobile=True).new_page()
    logs = []
    page.on("console", lambda m: logs.append(m.text))

    def route(r):
        u = r.request.url
        if is_ad(u):
            return r.fulfill(status=204, body="")
        if r.request.is_navigation_request() and u.startswith("https://www.youtube.com/embed/"):
            html = urllib.request.urlopen(urllib.request.Request(u, headers={"Referer": "https://www.reddit.com/", "User-Agent": UA}),
                                          timeout=20).read().decode("utf-8")
            i = html.lower().find("<head")
            j = html.find(">", i) + 1 if i >= 0 else 0
            return r.fulfill(status=200, body=html[:j] + "<script>" + script + "</script>" + html[j:],
                             headers={"content-type": "text/html; charset=utf-8"})
        return r.continue_()

    page.route("**/*", route)
    t0 = time.time()
    page.goto(f"https://www.youtube.com/embed/{vid}?autoplay=1&playsinline=1&rel=0&fs=1&enablejsapi=1&start=0"
              "&origin=https%3A%2F%2Fwww.reddit.com", referer="https://www.reddit.com/", wait_until="domcontentloaded")
    started = None
    while time.time() - t0 < 45:
        if shot_dir and "SYNC_YT_WAIT_NOTICE" in logs and not (shot_dir / f"{vid}.png").exists():
            page.screenshot(path=str(shot_dir / f"{vid}.png"))
        if page.evaluate("() => { const v = document.querySelector('video'); return v ? v.currentTime : -1; }") > 1:
            started = time.time() - t0
            break
        time.sleep(0.25)
    time.sleep(1)
    visible = page.evaluate("() => { const e = document.getElementById('sync-yt-wait'); return !!e && !e.hidden; }")
    page.context.close()
    removed = sorted({m.group(1) for m in (re.match(r"SYNC_YT_AD_REMOVED:(\w+)", l) for l in logs) if m})
    errors = [l for l in logs if l.startswith("SYNC_YT_ERROR")]
    noticed = "SYNC_YT_WAIT_NOTICE" in logs
    fails = []
    if errors:
        fails.append(f"player error {errors[0]}")
    if started is None:
        fails.append("did not start within 45 s")
    if removed and started and started > 6 and not noticed:
        fails.append(f"ads removed and {started:.0f} s to start, but no wait notice")
    if visible:
        fails.append("wait notice still visible while playing")
    print(f"{'FAIL' if fails else 'PASS'} {vid}: start {('%.1fs' % started) if started else '-'}, "
          f"ads removed {removed or 'none'}, notice {'shown' if noticed else 'not shown'}"
          + (f"  <- {'; '.join(fails)}" if fails else ""))
    return not fails, bool(set(removed) & {"adPlacements", "playerAds", "adSlots"})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("ids", nargs="*")
    ap.add_argument("--max", type=int, default=6)
    ap.add_argument("--shot", type=Path)
    a = ap.parse_args()
    script = java_constant(SRC.read_text(), "AD_SCRIPT")
    if a.shot:
        a.shot.mkdir(parents=True, exist_ok=True)
    ids = a.ids or video_ids(a.max)
    if not ids:
        sys.exit("no embeddable YouTube videos found in live posts")
    ok, with_ads = True, 0
    with sync_playwright() as p:
        browser = p.chromium.launch(args=["--autoplay-policy=no-user-gesture-required"])
        for vid in ids:
            passed, had_ads = check(browser, vid, script, a.shot)
            ok &= passed
            with_ads += had_ads
            if had_ads and not a.ids:
                break
        browser.close()
    if not with_ads:
        print("note: none of the videos had ads, so the wait notice was not exercised")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
