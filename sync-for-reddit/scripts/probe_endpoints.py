#!/usr/bin/env python3
"""Contract check for every remote endpoint Sync for Reddit v23.06.30 depends on.

For each endpoint it records the HTTP status and whether the JSON fields Sync's parsers
read are present, then compares against the previous snapshot so API drift shows up as a
diff. Run before and after patch work:

    python3 -I scripts/probe_endpoints.py            # probe + compare to last snapshot
    python3 -I scripts/probe_endpoints.py --save     # also write a new snapshot

Exit status is non-zero if any endpoint marked `must` (one the patched app relies on)
fails its check.

Credentials come from sync-for-reddit/secrets.env (REDDIT_CLIENT_ID, optional
IMGUR_CLIENT_ID). It only uses an app-only "installed_client" token: no user login,
read-only GETs, plus one read-only POST to /api/morechildren.
"""
import datetime as dt
import json
import re
import pathlib
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[1]   # sync-for-reddit/
SNAPDIR = ROOT / "tests" / "snapshots"
UA = "android:sync-patch-probe:v0.1 (personal research)"


def load_secrets():
    env = {}
    for line in (ROOT / "secrets.env").read_text().splitlines():
        if "=" in line and not line.strip().startswith("#"):
            k, v = line.split("=", 1)
            env[k.strip()] = v.strip()
    return env


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    # Reddit answers blocked OAuth calls with a 301 to the www homepage; following it
    # would turn a failure into a fake 200. Surface the 3xx instead.
    def redirect_request(self, *a, **k):
        return None


_opener = urllib.request.build_opener(_NoRedirect)
FIXDIR = ROOT / "tests" / "fixtures"


def http(method, url, headers=None, data=None, timeout=20):
    body = urllib.parse.urlencode(data).encode() if data else None
    try:
        req = urllib.request.Request(url, data=body, method=method, headers={"User-Agent": UA, **(headers or {})})
    except ValueError as e:   # malformed/empty URL: report as a failed request, never abort the probe
        return 0, str(e).encode()
    try:
        with _opener.open(req, timeout=timeout) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
    except Exception as e:  # DNS failure, timeout, TLS…
        return 0, str(e).encode()


def get_token(client_id):
    import base64
    auth = base64.b64encode(f"{client_id}:".encode()).decode()
    st, body = http("POST", "https://www.reddit.com/api/v1/access_token",
                    {"Authorization": f"Basic {auth}"},
                    {"grant_type": "https://oauth.reddit.com/grants/installed_client",
                     "device_id": uuid.uuid4().hex[:30]})
    if st != 200:
        sys.exit(f"token request failed: {st} {body[:200]!r}")
    return json.loads(body)["access_token"]


def js(body):
    try:
        return json.loads(body)
    except Exception:
        return None


def dig(obj, path):
    """dig(obj, "data.children.0.data") -> value or raises."""
    for part in path.split("."):
        obj = obj[int(part)] if isinstance(obj, list) else obj[part]
    return obj


def has(obj, *paths):
    missing = []
    for p in paths:
        try:
            dig(obj, p)
        except Exception:
            missing.append(p)
    return missing


def live():
    """scripts/e2e/live.py: test posts are found at run time, never hard-coded."""
    import importlib.util
    spec = importlib.util.spec_from_file_location("live", ROOT / "scripts/e2e/live.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    save = "--save" in sys.argv
    sec = load_secrets()
    tok = get_token(sec["REDDIT_CLIENT_ID"])
    H = {"Authorization": f"bearer {tok}"}
    O = "https://oauth.reddit.com"
    results = []

    def rec(name, sync_class, must, status, ok, note=""):
        results.append({"name": name, "class": sync_class, "must": must,
                        "status": status, "ok": bool(ok), "note": note})

    # --- Listings (feed) -------------------------------------------------------
    st, b = http("GET", f"{O}/r/pics/hot?limit=100", H)
    d = js(b)
    miss = has(d, "data.children.0.data.id", "data.after") if d else ["<not json>"]
    rec("subreddit listing /r/<sub>/hot", "OAuthPostsRequest", True, st, st == 200 and not miss, ",".join(miss))
    posts = [c["data"] for c in d["data"]["children"]] if d and not miss else []
    gallery = next((p for p in posts if p.get("is_gallery")), None)
    busiest = max(posts, key=lambda p: p.get("num_comments", 0)) if posts else None

    st, b = http("GET", f"{O}/hot?limit=5", H)
    rec("frontpage /hot", "OAuthPostsRequest", True, st, st == 200 and js(b) is not None)

    st, b = http("GET", f"{O}/r/pics+aww/hot?limit=5", H)
    rec("multi-sub listing /r/a+b/hot", "OAuthPostsRequest", False, st, st == 200 and js(b) is not None,
        "app-only token redirects to www (301); re-check with a user token")

    # --- Gallery ----------------------------------------------------------------
    if gallery:
        gid = gallery["id"]
        st, b = http("GET", f"{O}/api/info.json?id=t3_{gid}", H)
        rec("gallery via /api/info.json (Sync y8.q)", "OAuthGalleryRequest(y8.q)", False, st, st == 200)
        st, b = http("GET", f"{O}/comments/{gid}?limit=1", H)
        d = js(b)
        if save and d:
            FIXDIR.mkdir(parents=True, exist_ok=True)
            (FIXDIR / "gallery_comments_endpoint.json").write_bytes(b)
        ok = False
        note = ""
        if d:
            p = d[0]["data"]["children"][0]["data"]
            items = p.get("gallery_data", {}).get("items", [])
            mm = p.get("media_metadata") or {}
            bad = [i["media_id"] for i in items
                   # Sync's gallery parser (y8.q) reads only s.mp4 or s.u.
                   if not (mm.get(i["media_id"], {}).get("s", {}).get("u") or mm.get(i["media_id"], {}).get("s", {}).get("mp4"))]
            ok = bool(items) and not bad
            note = f"{len(items)} items, {len(bad)} without s.u/s.mp4"
        rec("gallery via /comments/<id> (patched path)", "patch: gallery", True, st, st == 200 and ok, note)
    else:
        rec("gallery sample", "-", True, 0, False, "no gallery post found in r/pics hot")

    # --- Comments / more children -----------------------------------------------
    if busiest:
        pid = busiest["id"]
        st, b = http("GET", f"{O}/comments/{pid}?limit=50", H)
        d = js(b)
        rec("comments /comments/<id>", "OAuthCommentsRequest", True, st, st == 200 and isinstance(d, list) and len(d) == 2)

        def mores(x):
            if isinstance(x, dict):
                if x.get("kind") == "more" and x["data"].get("children"):
                    yield x["data"]
                for v in x.values():
                    yield from mores(v)
            elif isinstance(x, list):
                for v in x:
                    yield from mores(v)

        more = next(mores(d), None) if d else None
        if more:
            ch = ",".join(more["children"][:20])
            st, b = http("POST", f"{O}/api/morechildren?always_show_media=1&profile_img=true", H,
                         {"link_id": f"t3_{pid}", "children": ch, "api_type": "json"})
            rec("morechildren POST (Sync y8.f)", "OAuthCommentsMoreRequest(y8.f)", False, st,
                st == 200 and not has(js(b), "json.data.things"))
            parent = more.get("parent_id", "")
            if parent.startswith("t1_"):
                st, b = http("GET", f"{O}/comments/{pid}/_/{parent[3:]}?limit=500&depth=10", H)
                rec("more via parent permalink (patched path)", "patch: morecomments", True, st,
                    st == 200 and isinstance(js(b), list))
                if save and st == 200:
                    FIXDIR.mkdir(parents=True, exist_ok=True)
                    (FIXDIR / "more_parent_permalink.json").write_bytes(b)
                    (FIXDIR / "more_stub.json").write_text(json.dumps({"link_id": pid, "more": more}))
            else:
                rec("more via parent permalink (patched path)", "patch: morecomments", False, 0, False,
                    "sampled stub is top-level; no comment parent to test")
        else:
            rec("morechildren", "-", False, 0, False, "no more-stub found")

    # --- Other Reddit endpoints Sync calls ---------------------------------------
    for name, path, cls in [
        ("subreddit about", "/r/pics/about", "OAuthSubredditInfoRequest"),
        ("user about /user/<u>/about", "/user/spez/about", "OAuthUserInfoRequest"),
        ("user about /u/<u>/about (unpatched)", "/u/spez/about", "OAuthUserInfoRequest"),
        ("search", "/search?q=cats&limit=5", "OAuthPostsRequest(y8.k0)"),
        ("subreddit search", "/subreddits/search?q=cats&limit=5", "OAuthPostsRequest(y8.k0)"),
        ("wiki", "/r/redditsync/wiki/index", "OAuthWikiRequest"),
        ("duplicates (informational; Sync uses submit.json)", f"/duplicates/{busiest['id'] if busiest else 'x'}?limit=5", "-"),
    ]:
        st, b = http("GET", O + path, H)
        rec(name, cls, False, st, st == 200 and js(b) is not None)

    # --- Replacement endpoints the patches call (must keep working) ----------------
    st, b = http("GET", f"{O}/r/pics%2Baww/hot.json?limit=50", H)
    subs = {c["data"].get("subreddit", "").lower() for c in (js(b) or {}).get("data", {}).get("children", [])}
    rec("combined subs /r/a%2Bb/hot (patch: multisub)", "patch: multisub", True, st,
        st == 200 and {"pics", "aww"} <= subs, ",".join(sorted(subs)))
    st, b = http("GET", f"{O}/subreddits/popular.json?limit=100", H)
    pool = [c["data"] for c in (js(b) or {}).get("data", {}).get("children", [])]
    rec("random pool /subreddits/popular (patch: random)", "patch: random", True, st,
        st == 200 and sum(1 for x in pool if "over18" in x and x.get("display_name")) >= 20, f"{len(pool)} subs")
    st, b = http("GET", f"{O}/subreddits/search.json?q=nsfw&include_over_18=on&limit=100", H)
    pool = [c["data"] for c in (js(b) or {}).get("data", {}).get("children", [])]
    rec("random nsfw pool (patch: random)", "patch: random", False, st,
        st == 200 and any(x.get("over18") for x in pool), f"{sum(1 for x in pool if x.get('over18'))} over18; intermittently 0 with app-only tokens; verify logged in")
    link = next((p for p in posts if not p.get("is_self") and "reddit.com" not in p.get("url", "")), None)
    if link:
        st, b = http("GET", f"{O}/search.json?q={urllib.parse.quote('url:' + link['url'], safe='')}&type=link&include_over_18=on&limit=25", H)
        ids = [c["data"]["id"] for c in (js(b) or {}).get("data", {}).get("children", [])]
        rec("other discussions url: search (patch: duplicates)", "patch: duplicates", True, st,
            st == 200 and link["id"] in ids, f"{len(ids)} results")
    if busiest:
        pid = busiest["id"]
        st, b = http("GET", f"{O}/comments/{pid}?limit=500&depth=10&context=0&raw_json=1", H)
        d = js(b)
        rec("more batch /comments/<post>?limit=500 (patch: morecomments)", "patch: morecomments", True, st,
            st == 200 and isinstance(d, list) and len(d) == 2)
        roots = [c["data"]["id"] for c in d[1]["data"]["children"] if c["kind"] == "t1"] if isinstance(d, list) else []
        if roots:
            st, b = http("GET", f"{O}/comments/{pid}/_/{roots[-1]}?limit=500&depth=10&context=0&raw_json=1", H)
            d = js(b)
            got = [c["data"]["id"] for c in d[1]["data"]["children"]] if isinstance(d, list) else []
            rec("more anchored /comments/<post>/_/<child> (patch: morecomments)", "patch: morecomments", True, st,
                st == 200 and roots[-1] in got)
    st, b = http("GET", "https://backend.deviantart.com/oembed?url=https://www.deviantart.com/team/art/Fella-Celebrates-100k-971957229")
    rec("DeviantArt oEmbed over https (patch: deviantart)", "patch: deviantart", True, st,
        st == 200 and bool((js(b) or {}).get("url")))
    L = live()
    try:
        album_url, album_count = L.imgur_album_url()
    except L.NotFound:
        album_url, album_count = None, 0
    st, b = http("GET", album_url) if album_url else (0, b"")
    rec("Imgur album page og:image (patch: link previews)", "patch: linkpreview", False, st,
        st == 200 and b"og:image" in (b or b""))

    # --- Live e2e test posts (scripts/e2e/live.py): a missing kind makes its scenario SKIP ----------
    for name, finder in L.FINDERS.items():
        if name in ("imgur_album_url", "imgur_gif_id"):
            continue
        try:
            found = finder()
            rec(f"e2e live post: {name}", "scripts/e2e/live.py", False, 200, True, str(found)[:60])
        except L.NotFound as e:
            rec(f"e2e live post: {name}", "scripts/e2e/live.py", False, 404, False, f"scenario will SKIP: {e}")

    # --- v.redd.it (Fix Reddit video playback: DASH; Fix Gfycat: direct MP4 links -> DASH) ----------
    try:
        _, vid = L.reddit_video()
        st, b = http("GET", f"https://v.redd.it/{vid}/DASHPlaylist.mpd")
        rec("v.redd.it DASH playlist (current video)", "patch: redditvideo", True, st, st == 200 and (b or b"").lstrip().startswith(b"<"))
        st, _ = http("GET", f"https://v.redd.it/{vid}/HLSPlaylist.m3u8")
        rec("v.redd.it HLS playlist (Sync's original path)", "CustomExoPlayerView", False, st, st == 200,
            "was 403 for some videos on 2026-11-16; patched app uses DASH")
    except L.NotFound as e:
        rec("v.redd.it DASH playlist (current video)", "patch: redditvideo", True, 0, False, str(e))

    # --- Reddit image CDN and HTML-escaped URLs (patch: Fix Reddit image URLs) ---------
    img = next((p for p in posts if (p.get("preview") or {}).get("images")), None)
    if img:
        res = img["preview"]["images"][0].get("resolutions") or [img["preview"]["images"][0]["source"]]
        esc = res[0]["url"]                    # no raw_json=1, so this is HTML-escaped
        if "&amp;" in esc:
            st, _ = http("GET", esc)
            rec("preview.redd.it with &amp; (as Sync sends it)", "Glide", False, st, st == 200,
                "403 since 2026-11-16; Fix Reddit image URLs unescapes it")
        st, _ = http("GET", esc.replace("&amp;", "&"))
        rec("preview.redd.it unescaped (patched)", "patch: imageurls", True, st, st == 200)

    # --- Redgifs (upstream Fix Redgifs API + our /info emulation) ------------------------
    st, _ = http("GET", "https://api.redgifs.com/info")
    rec("redgifs /info (Sync GrabIpRequest)", "GrabIpRequest", False, st, st == 200, "404 since 2026-11; answered locally by Fix Redgifs API")
    st, b = http("GET", "https://api.redgifs.com/v2/auth/temporary")
    rtok = (js(b) or {}).get("token")
    rec("redgifs temporary token (upstream patch)", "patch: redgifs", True, st, st == 200 and rtok)
    if rtok:
        st, b = http("GET", "https://api.redgifs.com/v2/gifs/search?order=trending&count=5", {"Authorization": f"Bearer {rtok}"})
        gid = (((js(b) or {}).get("gifs") or [{}])[0]).get("id")
        st, b = http("GET", f"https://api.redgifs.com/v2/gifs/{gid}?user-addr=&user-agent=Sync", {"Authorization": f"Bearer {rtok}"})
        rec("redgifs gif lookup with empty user-addr", "GrabRedgifRequest", True, st, st == 200 and not has(js(b), "gif.urls.hd"))

    # --- Third-party media hosts ---------------------------------------------------
    try:
        imgur_id = L.imgur_gif_id()
    except L.NotFound:
        imgur_id = None
    # Sync's own Imgur proxy (salted URLs, salt not reproduced here); informational, expected dead.
    st, _ = http("GET", "https://images.syncforreddit.com/")
    rec("imgur proxy images.syncforreddit.com (Sync s8.d/s8.b)", "ImgurSingleImageRequest", False, st, st == 200,
        "dev's proxy; expected dead")
    # Fix Imgur loads albums from the public embed page (no API key) and parses its inline JSON.
    embed = re.sub(r"^.*imgur\.com/(?:a|gallery)/(?:[^/?#]*-)?([A-Za-z0-9]{5,8}).*$", r"https://imgur.com/a/\1/embed?pub=true", album_url or "")
    st, b = http("GET", embed) if album_url else (0, b"")
    import re as _re
    m = _re.search(rb'\bimages\s*=\s*(\{\s*"count")', b or b"")
    n = 0
    if m:
        try:
            n = len(json.JSONDecoder().raw_decode((b or b"").decode("utf-8", "replace"), m.start(1))[0].get("images", []))
        except Exception:
            n = 0
    rec("imgur album embed page inline JSON (patched path)", "patch: imgur", True, st, st == 200 and n >= 2, f"{n} images")
    for name, url, must in [
        ("i.imgur.com direct gif", f"https://i.imgur.com/{imgur_id}.gif", True) if imgur_id else ("i.imgur.com direct gif (no live post)", "", False),
        ("i.imgur.com direct mp4", f"https://i.imgur.com/{imgur_id}.mp4", True) if imgur_id else ("i.imgur.com direct mp4 (no live post)", "", False),
        ("gfycat API", "https://api.gfycat.com/v1/gfycats/test", False),
        ("streamable API", "https://api.streamable.com/videos/abc", False),

        ("giphy CDN", "https://media.giphy.com/media/l0MYt5jPR6QX5pnqM/giphy.mp4", True),
    ]:
        if not url:   # no live post to test with: report, don't abort the probe
            rec(name, "-", False, 0, False, "no live post right now")
            continue
        st, _ = http("GET", url)
        rec(name, "-", must, st, st == 200)

    # --- Report -------------------------------------------------------------------
    prev = None
    snaps = sorted(SNAPDIR.glob("endpoints-*.json"))
    if snaps:
        prev = {r["name"]: r for r in json.loads(snaps[-1].read_text())["results"]}

    fails = 0
    print(f"{'':2} {'status':>6}  {'endpoint':55} note")
    for r in results:
        mark = "OK" if r["ok"] else ("!!" if r["must"] else "--")
        if r["must"] and not r["ok"]:
            fails += 1
        change = ""
        if prev and r["name"] in prev and (prev[r["name"]]["status"], prev[r["name"]]["ok"]) != (r["status"], r["ok"]):
            change = f"  [CHANGED from {prev[r['name']]['status']}/{'ok' if prev[r['name']]['ok'] else 'fail'}]"
        print(f"{mark:2} {r['status']:>6}  {r['name']:55} {r['note']}{change}")
    print(f"\n{fails} required check(s) failed. (!! = required by patched app, -- = known-broken/informational)")

    if save:
        SNAPDIR.mkdir(parents=True, exist_ok=True)
        out = SNAPDIR / f"endpoints-{dt.datetime.now():%Y%m%d-%H%M%S}.json"
        out.write_text(json.dumps({"taken": dt.datetime.now().isoformat(), "results": results}, indent=1))
        print(f"snapshot: {out}")
    sys.exit(1 if fails else 0)


if __name__ == "__main__":
    main()
