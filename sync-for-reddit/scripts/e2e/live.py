#!/usr/bin/env python3
"""Find live Reddit posts for the e2e tests at run time, instead of hard-coding post URLs that drift
(posts get removed, media expires, galleries get deleted).

Each finder returns what a scenario needs (a permalink, plus media ids where relevant), caches it for
the run, and raises NotFound when Reddit currently has no suitable post; the scenario is then
reported as SKIP (inconclusive), not as a failure of the patch.

    python3 -I scripts/e2e/live.py [finder ...]     # print what each finder picks (default: all)
    python3 -I scripts/e2e/live.py --get finder [N] # print only the result (or its Nth field), for scripts
"""
import concurrent.futures
import threading
import functools
import importlib.util
import json
import re
import sys
import urllib.parse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]   # sync-for-reddit/


class NotFound(Exception):
    pass


def singleflight(fn):
    lock = threading.RLock()
    cached = functools.lru_cache(maxsize=None)(fn)
    @functools.wraps(fn)
    def call(*args, **kwargs):
        with lock:
            return cached(*args, **kwargs)
    call.cache_info = cached.cache_info
    return call


@singleflight
def _probe():
    spec = importlib.util.spec_from_file_location("probe_endpoints", ROOT / "scripts/probe_endpoints.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


@singleflight
def _headers():
    p = _probe()
    return {"Authorization": "bearer " + p.get_token(p.load_secrets()["REDDIT_CLIENT_ID"])}


def http(url, headers=None):
    return _probe().http("GET", url, headers)


def listing(path, pages=1):
    """Posts from an oauth.reddit.com listing path; removed and NSFW posts first filtered/sorted out."""
    posts, after = [], None
    for _ in range(pages):
        sep = "&" if "?" in path else "?"
        st, body = http(f"https://oauth.reddit.com{path}{sep}limit=100&raw_json=1" + (f"&after={after}" if after else ""), _headers())
        data = ((_probe().js(body) or {}).get("data") or {}) if st == 200 else {}
        posts += [c["data"] for c in data.get("children", []) if c.get("kind") == "t3"]
        after = data.get("after")
        if not after:
            break
    posts = [p for p in posts if not p.get("removed_by_category") and p.get("author") != "[deleted]"]
    return sorted(posts, key=lambda p: bool(p.get("over_18")))   # prefer SFW


def search_self_posts(queries, must_contain):
    """Self posts whose text contains `must_contain` (Reddit search is fuzzy, so filter locally)."""
    for query in ([queries] if isinstance(queries, str) else queries):
        for sort, t in (("new", "year"), ("relevance", "year"), ("relevance", "all")):
            q = urllib.parse.quote(query)
            for p in listing(f"/search?q={q}&type=link&sort={sort}&t={t}", pages=2):
                if p.get("is_self") and must_contain in (p.get("selftext") or ""):
                    return p
    raise NotFound(f"no self post containing {must_contain!r}")


def _first(posts, pred, what):
    for p in posts:
        try:
            if pred(p):
                return p
        except (KeyError, TypeError, ValueError):
            continue
    raise NotFound(what)


def _video_id(p):
    rv = (p.get("secure_media") or {}).get("reddit_video") or (p.get("media") or {}).get("reddit_video") or {}
    m = re.match(r"https://v\.redd\.it/([A-Za-z0-9]+)/([A-Za-z0-9_]+\.mp4)", rv.get("fallback_url", ""))
    return (m.group(1), m.group(2), rv) if m else None


# --- finders -------------------------------------------------------------------------------------

@singleflight
def link_post():
    """A link post (not self, not Reddit media) with a big comment thread."""
    for sub in ("worldnews", "news", "politics", "technology"):
        try:
            return _first(listing(f"/r/{sub}/hot"), lambda p: not p["is_self"] and "redd" not in p["domain"]
                          and p["num_comments"] >= 800, "big link thread")["permalink"]
        except NotFound:
            continue
    raise NotFound("link post with 800+ comments")


@singleflight
def big_thread():
    """Any thread big enough to have nested 'View more' rows."""
    return _first(listing("/r/AskReddit/hot"), lambda p: p["num_comments"] >= 500, "500+ comment thread")["permalink"]


@singleflight
def removed_comment_thread():
    """A thread with a [removed]/[deleted] top-level comment near the top (for 'Restore comment')."""
    for p in listing("/r/AskReddit/hot")[:15] + listing("/r/news/hot")[:15]:
        # Default ("best"/confidence) order, as Sync opens threads, so the comment is near the top on screen.
        st, body = http(f"https://oauth.reddit.com/comments/{p['id']}?limit=25&depth=1&sort=confidence&raw_json=1", _headers())
        comments = (_probe().js(body) or [None, {}])[1].get("data", {}).get("children", []) if st == 200 else []
        if any(c.get("kind") == "t1" and c["data"].get("body") in ("[removed]", "[deleted]") for c in comments[:5]):
            return p["permalink"]
    raise NotFound("thread with a [removed] comment near the top")


@singleflight
def gallery():
    """Deep link Sync treats as a gallery."""
    p = _first(listing("/r/pics/hot") + listing("/r/mildlyinteresting/hot"),
               lambda p: p.get("is_gallery") and len(p.get("gallery_data", {}).get("items", [])) >= 2, "gallery post")
    return f"https://www.reddit.com/r/{p['subreddit']}/comments/{p['id']}?is_gallery=true"


@singleflight
def imgur_direct():
    return _first(listing("/domain/i.imgur.com/top?t=month") + listing("/domain/i.imgur.com/new"),
                  lambda p: re.search(r"\.(jpe?g|png)$", p["url"]), "i.imgur.com image post")["permalink"]


@singleflight
def imgur_gif():
    return _first(listing("/domain/i.imgur.com/top?t=year") + listing("/domain/i.imgur.com/new"),
                  lambda p: re.search(r"\.(gifv|gif|mp4)$", p["url"]), "i.imgur.com gif post")["permalink"]


@singleflight
def imgur_page():
    return _first(listing("/domain/imgur.com/top?t=month") + listing("/domain/imgur.com/new"),
                  lambda p: re.match(r"https?://(www\.|m\.)?imgur\.com/[A-Za-z0-9]{5,8}$", p["url"]), "imgur.com page post")["permalink"]


def _imgur_album_count(url):
    m = re.search(r"imgur\.com/(?:a|gallery)/(?:[^/?#]*-)?([A-Za-z0-9]{5,8})\b", url)
    if not m:
        return 0
    st, body = http(f"https://imgur.com/a/{m.group(1)}/embed?pub=true")
    found = re.search(rb'\bimages\s*=\s*\{\s*"count"\s*:\s*(\d+)', body or b"") if st == 200 else None
    return int(found.group(1)) if found else 0


@singleflight
def imgur_album():
    """(permalink, image count) for an Imgur album link post with 2-20 images."""
    for p in listing("/domain/imgur.com/new") + listing("/domain/imgur.com/top?t=month"):
        if re.search(r"imgur\.com/(a|gallery)/", p["url"]):
            n = _imgur_album_count(p["url"])
            if 2 <= n <= 20:
                return p["permalink"], n
    raise NotFound("multi-image Imgur album post")


@singleflight
def imgur_album_url():
    """(album URL, image count) of the post imgur_album() picked."""
    post, count = imgur_album()
    for p in listing("/domain/imgur.com/new") + listing("/domain/imgur.com/top?t=month"):
        if p["permalink"] == post:
            return p["url"], count
    raise NotFound("imgur album URL")


@singleflight
def imgur_gif_id():
    """Imgur id of a current i.imgur.com animated post (for direct .gif/.mp4 checks)."""
    for p in listing("/domain/i.imgur.com/top?t=year") + listing("/domain/i.imgur.com/new"):
        m = re.match(r"https?://i\.imgur\.com/([A-Za-z0-9]{5,8})\.(gifv|gif|mp4)$", p["url"])
        if m:
            return m.group(1)
    raise NotFound("i.imgur.com animated post")


@singleflight
def tenor_selftext():
    return search_self_posts("tenor.com/view", "tenor.com/view")["permalink"]


@singleflight
def http_tenor_selftext():
    """Self post linking http:// (not https) Tenor, which the inline preview fetched over port 80."""
    return search_self_posts(['"http://tenor.com"', "http://tenor.com/view"], "http://tenor.com/view")["permalink"]


@singleflight
def imgur_album_selftext():
    return search_self_posts("imgur.com/a/", "imgur.com/a/")["permalink"]


@singleflight
def deviantart():
    return _first(listing("/domain/deviantart.com/new") + listing("/domain/deviantart.com/top?t=month"),
                  lambda p: "deviantart.com/" in p["url"], "DeviantArt link post")["permalink"]


@singleflight
def gfycat_with_copy():
    """(permalink, v.redd.it id) for a Gfycat post Reddit archived as a video."""
    for path in ("/domain/gfycat.com/top?t=all", "/domain/gfycat.com/top?t=year", "/domain/gfycat.com/hot"):
        for p in listing(path, pages=2):
            rv = ((p.get("preview") or {}).get("reddit_video_preview") or {}).get("fallback_url", "")
            m = re.match(r"https://v\.redd\.it/([A-Za-z0-9]+)/", rv)
            if m and http(f"https://v.redd.it/{m.group(1)}/DASHPlaylist.mpd")[0] == 200:
                return p["permalink"], m.group(1)
    raise NotFound("Gfycat post with a Reddit video copy")


@singleflight
def gfycat_without_copy():
    for path in ("/domain/gfycat.com/top?t=all", "/domain/gfycat.com/top?t=year"):
        for p in listing(path, pages=2):
            if not (p.get("preview") or {}).get("reddit_video_preview"):
                return p["permalink"]
    raise NotFound("Gfycat post without a Reddit copy")


@singleflight
def reddit_video():
    """(permalink, v.redd.it id) for a current Reddit-hosted video post."""
    for p in listing("/r/videos/hot") + listing("/domain/v.redd.it/hot"):
        v = _video_id(p)
        if p.get("is_video") and v and http(f"https://v.redd.it/{v[0]}/DASHPlaylist.mpd")[0] == 200:
            return p["permalink"], v[0]
    raise NotFound("v.redd.it video post")


@singleflight
def reddit_video_with_audio():
    """(v.redd.it id, direct MP4 file name) for a video with sound, both checked live."""
    for p in listing("/domain/v.redd.it/hot") + listing("/r/videos/hot"):
        v = _video_id(p)
        if v and v[2].get("has_audio"):
            vid, mp4, _ = v
            st, body = http(f"https://v.redd.it/{vid}/DASHPlaylist.mpd")
            if st == 200 and b'contentType="audio"' in (body or b"") and http(f"https://v.redd.it/{vid}/{mp4}")[0] == 200:
                return vid, mp4
    raise NotFound("v.redd.it video with audio")


@singleflight
def youtube_post():
    """A link post to a YouTube video (prefers ones the oEmbed endpoint confirms are embeddable)."""
    def embeddable(p):
        if not re.search(r"(youtube\.com/watch\?v=|youtu\.be/)[\w-]{11}", p["url"]):
            return False
        st, _ = http("https://www.youtube.com/oembed?format=json&url=" + urllib.parse.quote(p["url"], safe=""))
        return st == 200   # 401/403 = embedding disabled, 404 = gone
    return _first(listing("/domain/youtube.com/hot") + listing("/domain/youtu.be/hot") + listing("/r/videos/hot"),
                  embeddable, "embeddable YouTube link post")["permalink"]


@singleflight
def tumblr_photo_post():
    """A link post to a Tumblr post (blog.tumblr.com/post/<id>) that has images, checked with the keyless v1 API."""
    def has_images(p):
        m = re.match(r"https?://([a-z0-9-]+)\.tumblr\.com/post/(\d+)", p["url"])
        if not m or m.group(1) == "www":
            return False
        st, body = http(f"https://{m.group(1)}.tumblr.com/api/read/json?id={m.group(2)}")
        text = (body or b"").decode("utf-8", "replace").replace("\\/", "/")
        return st == 200 and re.search(r"media\.tumblr\.com/[0-9a-f]{32}/[0-9a-f]{16}-\w{2}/s(?:640x960|1280x1920|2048x3072)/", text)
    return _first(listing("/domain/tumblr.com/top?t=month") + listing("/domain/tumblr.com/hot") + listing("/domain/tumblr.com/top?t=year"),
                  has_images, "Tumblr post with images")["permalink"]


@singleflight
def redgifs():
    """A post linking Redgifs (no Redgifs links are ever committed)."""
    return _first(listing("/domain/redgifs.com/top?t=week") + listing("/domain/redgifs.com/hot"),
                  lambda p: "redgifs.com/watch/" in p["url"], "Redgifs link post")["permalink"]


FINDERS = {name: fn for name, fn in globals().items()
           if callable(fn) and getattr(fn, "cache_info", None) and not name.startswith("_")}


def prefetch(names):
    def find(name):
        try:
            return FINDERS[name]()
        except NotFound:
            return None  # the scenario still reports the original NotFound as SKIP
    if names:
        with concurrent.futures.ThreadPoolExecutor(max_workers=min(6, len(names))) as pool:
            list(pool.map(find, names))

if __name__ == "__main__":
    if sys.argv[1:2] == ["--get"]:
        try:
            result = FINDERS[sys.argv[2]]()
        except NotFound as e:
            sys.exit(f"NOT FOUND: {e}")
        print(result[int(sys.argv[3])] if len(sys.argv) > 3 else result)
        sys.exit(0)
    names = sys.argv[1:] or [n for n in FINDERS if n != "redgifs"]
    failed = 0
    def find(name):
        try:
            return f"{name:24} {FINDERS[name]()}", False
        except NotFound as e:
            return f"{name:24} NOT FOUND: {e}", True
    with concurrent.futures.ThreadPoolExecutor(max_workers=min(6, len(names))) as pool:
        for line, missing in pool.map(find, names):
            print(line)
            failed += missing
    sys.exit(1 if failed else 0)
