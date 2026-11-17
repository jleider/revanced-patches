package app.revanced.extension.syncforreddit;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Offline unit checks for the extension helpers that rewrite Reddit/media URLs and adapt responses.
 * Run by scripts/unit_tests.sh (plain javac + org.json + android.jar + the JVM stubs in this tree).
 */
public final class ExtensionHarness {
    private static int checks, failures;

    private static void eq(Object got, Object want, String what) {
        checks++;
        if (want == null ? got != null : !want.equals(got)) {
            failures++;
            System.out.println("FAIL " + what + "\n     got:  " + got + "\n     want: " + want);
        }
    }

    private static void check(boolean ok, String what) { eq(ok, true, what); }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) fixtures = args[0];
        combinedSubreddits();
        otherDiscussions();
        randomSubreddit();
        gallery();
        imgur();
        linkPreviews();
        loginRedirect();
        previewInterceptor();
        gfycatBlock();
        sLinkGuard();
        redditImageUrls();
        httpsUpgrade();
        redditVideo();
        redditStatus();
        moreCommentsFailureKeepsRow();
        tumblr();
        youTube();
        previewSafety();
        System.out.println((failures == 0 ? "PASS" : "FAIL") + ": " + (checks - failures) + "/" + checks + " extension checks");
        if (failures > 0) System.exit(1);
    }

    static String fixtures = "tests/fixtures";

    static void imgurEmbed() {
        try {
            // Real embed page of imgur.com/a/xa2J11z (1 MP4 + 2 JPGs), recorded 2026-11-16.
            byte[] page = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(fixtures, "imgur_album_embed.html"));
            org.json.JSONArray images = new org.json.JSONObject(new String(FixImgurPatch.albumJson(page), "UTF-8"))
                    .getJSONObject("data").getJSONArray("images");
            eq(images.length(), 3, "imgur embed: all album images");
            eq(images.getJSONObject(0).getString("mp4"), "https://i.imgur.com/dTZpJcl.mp4", "imgur embed: animated item plays MP4");
            eq(images.getJSONObject(1).getString("link"), "https://i.imgur.com/QHMGTpr.jpg", "imgur embed: still image keeps its extension");
            check(!images.getJSONObject(1).has("mp4"), "imgur embed: still image has no mp4");
            eq(images.getJSONObject(1).getInt("width"), 3199, "imgur embed: width (parser calls getInt)");
            check(images.getJSONObject(1).isNull("description"), "imgur embed: null description stays null");
        } catch (Exception e) {
            check(false, "imgur embed fixture: " + e);
        }
        check(FixImgurPatch.albumJson("<html>no album here</html>".getBytes()) == null, "imgur embed: page without images left alone");
        check(FixImgurPatch.albumJson("var images = {\"count\":0,\"images\":[]};".getBytes()) == null, "imgur embed: empty album left alone");
        check(FixImgurPatch.albumJson("images = {\"count\":1,\"images\":[{\"hash\":\"../x\",\"ext\":\".jpg\"}]}".getBytes()) == null, "imgur embed: bad hash dropped");
        check(FixImgurPatch.albumJson(null) == null, "imgur embed: null body");
    }

    static void sLinkGuard() {
        // The shared /s/ resolver sends the bearer token to whatever URL it is given.
        eq(SLinkGuard.safeSLink("https://www.reddit.com/r/pics/s/AbC123"), "https://www.reddit.com/r/pics/s/AbC123", "slink: canonical");
        eq(SLinkGuard.safeSLink("https://reddit.com/r/pics/s/AbC123/"), "https://reddit.com/r/pics/s/AbC123", "slink: bare host, trailing slash");
        eq(SLinkGuard.safeSLink("http://www.reddit.com/r/pics/s/AbC123"), "https://www.reddit.com/r/pics/s/AbC123", "slink: http upgraded, token never in clear");
        eq(SLinkGuard.safeSLink("https://www.reddit.com/r/pics/s/AbC123#bypass"), "https://www.reddit.com/r/pics/s/AbC123#bypass", "slink: #bypass kept");
        eq(SLinkGuard.safeSLink("https://www.reddit.com/r/pics/s/AbC123?utm_source=share"), "https://www.reddit.com/r/pics/s/AbC123?utm_source=share", "slink: query kept");
        eq(SLinkGuard.safeSLink("https://WWW.Reddit.COM/r/pics/s/AbC123"), "https://www.reddit.com/r/pics/s/AbC123", "slink: host case");
        eq(SLinkGuard.safeSLink("https://evil.example/x.reddit.com/r/a/s/b"), null, "slink: reddit.com in path of foreign host");
        eq(SLinkGuard.safeSLink("https://notreddit.com/r/a/s/b"), null, "slink: suffix-lookalike host");
        eq(SLinkGuard.safeSLink("https://reddit.com.evil.example/r/a/s/b"), null, "slink: prefix-lookalike host");
        eq(SLinkGuard.safeSLink("https://www.reddit.com@evil.example/r/a/s/b"), null, "slink: userinfo trick");
        eq(SLinkGuard.safeSLink("https://evil.example/r/a/s/b#.reddit.com/r/a/s/b"), null, "slink: fragment trick");
        eq(SLinkGuard.safeSLink("https://www.reddit.com:8443/r/a/s/b"), null, "slink: odd port");
        eq(SLinkGuard.safeSLink("ftp://www.reddit.com/r/a/s/b"), null, "slink: non-http scheme");
        eq(SLinkGuard.safeSLink("https://www.reddit.com/r/a/s/b/../../x"), null, "slink: extra path");
        eq(SLinkGuard.safeSLink("https://www.reddit.com/r/pics/comments/abc"), null, "slink: not a share link");
        eq(SLinkGuard.safeSLink("not a url"), null, "slink: garbage");
        eq(SLinkGuard.safeSLink(null), null, "slink: null");
    }

    static void combinedSubreddits() {
        String o = "https://oauth.reddit.com/";
        eq(FixCombinedSubredditsPatch.fixUrl(o + "r/pics+funny/hot.json?limit=25&after=t3_x"), o + "r/pics%2Bfunny/hot.json?limit=25&after=t3_x", "multisub: listing");
        eq(FixCombinedSubredditsPatch.fixUrl(o + "r/a+b/search.json?q=cute+cat&restrict_sr=on"), o + "r/a%2Bb/search.json?q=cute+cat&restrict_sr=on", "multisub: '+' in query untouched");
        eq(FixCombinedSubredditsPatch.fixUrl(o + "search.json?q=a+b"), o + "search.json?q=a+b", "multisub: global search untouched");
        eq(FixCombinedSubredditsPatch.fixUrl(o + "r/pics/hot.json"), o + "r/pics/hot.json", "multisub: single sub untouched");
        eq(FixCombinedSubredditsPatch.fixUrl("https://example.com/r/a+b"), "https://example.com/r/a+b", "multisub: non-reddit host untouched");
        eq(FixCombinedSubredditsPatch.fixUrl(o + "r/a+b"), o + "r/a%2Bb", "multisub: no query");
        eq(FixCombinedSubredditsPatch.fixUrl(o + "r/a+b#frag"), o + "r/a%2Bb#frag", "multisub: fragment kept");
        eq(FixCombinedSubredditsPatch.fixUrl(null), null, "multisub: null");
        eq(FixCombinedSubredditsPatch.fixUrl("https://ssl.reddit.com/api/v1/access_token"), "https://ssl.reddit.com/api/v1/access_token", "multisub: token endpoint untouched");
    }

    static void otherDiscussions() {
        String enc = java.net.URLEncoder.encode("https://www.kyivpost.com/post/89765?a=1&b=2", java.nio.charset.StandardCharsets.UTF_8);
        String out = FixOtherDiscussionsPatch.rewriteUrl("https://ssl.reddit.com/submit.json?url=" + enc);
        check(out.startsWith("https://oauth.reddit.com/search.json?q="), "dupes: rewritten to OAuth search");
        eq(java.net.URLDecoder.decode(out.split("q=")[1].split("&")[0], java.nio.charset.StandardCharsets.UTF_8),
                "url:https://www.kyivpost.com/post/89765?a=1&b=2", "dupes: link decoded once and re-encoded as one q value");
        String paged = FixOtherDiscussionsPatch.rewriteUrl("https://ssl.reddit.com/submit.json?url=" + enc + "&after=t3_abc12&limit=25");
        check(paged.contains("&after=t3_abc12"), "dupes: pagination kept");
        check(!paged.contains("limit=25&limit"), "dupes: no duplicate limit");
        check(FixOtherDiscussionsPatch.rewriteUrl("https://www.reddit.com/submit.json?url=" + enc).startsWith("https://oauth.reddit.com/search.json?q="),
                "dupes: runtime www form (after Spoof client's ssl->www rewrite)");
        eq(FixOtherDiscussionsPatch.rewriteUrl("https://evilreddit.com/submit.json?url=x"), "https://evilreddit.com/submit.json?url=x", "dupes: lookalike host untouched");
        eq(FixOtherDiscussionsPatch.rewriteUrl("https://oauth.reddit.com/r/pics/hot.json"), "https://oauth.reddit.com/r/pics/hot.json", "dupes: unrelated URL untouched");
        String junk = FixOtherDiscussionsPatch.rewriteUrl("https://ssl.reddit.com/submit.json?url=x&after=javascript:alert(1)");
        check(!junk.contains("javascript"), "dupes: malformed after dropped");
    }

    // The partial-failure case 0naught raised on upstream MR !6999: a failed request must not consume
    // the "load more" row. Each of our requests is all-or-nothing; an error body is handed back
    // unchanged so Sync's parser rejects it and keeps the row for a retry.
    static void moreCommentsFailureKeepsRow() {
        for (String bad : new String[]{"<html>503 Service Unavailable</html>", "{\"error\":503}", "", "[]"}) {
            eq(FixMoreCommentsPatch.convertListing(bad, "t1_parent|~aaa,bbb"), bad, "more comments: failed request passes through: " + bad);
        }
    }

    static void randomSubreddit() throws Exception {
        eq(FixRandomSubredditPatch.randomName("https://www.reddit.com/r/random"), "random", "random link: www");
        eq(FixRandomSubredditPatch.randomName("https://reddit.com/r/randnsfw/"), "random_nsfw", "random link: randnsfw");
        eq(FixRandomSubredditPatch.randomName("https://old.reddit.com/r/Random?utm=x"), "random", "random link: old + query");
        eq(FixRandomSubredditPatch.randomName("/r/random_nsfw"), "random_nsfw", "random link: Sync's own name");
        eq(FixRandomSubredditPatch.randomName("https://www.reddit.com/r/randomthoughts"), null, "random link: r/randomthoughts is a real subreddit");
        eq(FixRandomSubredditPatch.randomName("https://www.reddit.com/r/random/comments/abc/x"), null, "random link: post links untouched");
        eq(FixRandomSubredditPatch.randomName("https://reddit.com.evil.example/r/random"), null, "random link: lookalike host");
        check(!FixRandomSubredditPatch.isListableSubredditLink(true, "https://www.reddit.com/r/random"), "random link: not opened as a listing");
        check(FixRandomSubredditPatch.isListableSubredditLink(true, "https://www.reddit.com/r/android"), "random link: other subreddits still listings");
        check(!FixRandomSubredditPatch.isListableSubredditLink(false, "https://www.reddit.com/r/android"), "random link: non-subreddit links unchanged");
        eq(FixRandomSubredditPatch.normalizeSubreddit("RandNSFW"), "random_nsfw", "random link: randnsfw normalized");
        eq(FixRandomSubredditPatch.normalizeSubreddit("android"), "android", "random link: other names unchanged");
        check(!FixRandomSubredditPatch.openRandomInSync(null, "https://www.reddit.com/r/random"), "random link: no context -> not handled");
        String sfw = FixRandomSubredditPatch.rewriteUrl("https://oauth.reddit.com/r/random.json");
        String nsfw = FixRandomSubredditPatch.rewriteUrl("https://oauth.reddit.com/r/randnsfw.json");
        check(sfw.contains("/subreddits/popular.json") && sfw.contains("revanced_random=sfw"), "random: sfw URL");
        check(nsfw.contains("include_over_18=on") && nsfw.contains("revanced_random=nsfw"), "random: nsfw URL");
        eq(FixRandomSubredditPatch.rewriteUrl("https://oauth.reddit.com/r/randomthoughts/hot.json"), "https://oauth.reddit.com/r/randomthoughts/hot.json", "random: r/randomthoughts untouched");
        eq(FixRandomSubredditPatch.rewriteUrl("https://oauth.reddit.com/r/random/hot.json"), "https://oauth.reddit.com/r/random/hot.json", "random: listing URL untouched");
        JSONArray children = new JSONArray()
                .put(sub("pics", false)).put(sub("nsfwsub", true)).put(sub("bad name!", false))
                .put(new JSONObject().put("data", new JSONObject().put("display_name", "noflag")));
        String body = new JSONObject().put("data", new JSONObject().put("children", children)).toString();
        for (int i = 0; i < 20; i++) {
            String picked = new JSONObject(FixRandomSubredditPatch.convert(body, sfw)).getJSONObject("data")
                    .getJSONArray("children").getJSONObject(0).getJSONObject("data").getString("subreddit");
            eq(picked, "pics", "random: sfw picks only over18=false with a valid name");
            String n = new JSONObject(FixRandomSubredditPatch.convert(body, nsfw)).getJSONObject("data")
                    .getJSONArray("children").getJSONObject(0).getJSONObject("data").getString("subreddit");
            eq(n, "nsfwsub", "random: nsfw picks only over18=true");
        }
        eq(FixRandomSubredditPatch.convert("{\"x\":1}", "https://oauth.reddit.com/r/pics/hot.json"), "{\"x\":1}", "random: other responses untouched");
        boolean threw = false;
        try { FixRandomSubredditPatch.convert("{\"data\":{\"children\":[]}}", sfw); } catch (org.json.JSONException e) { threw = true; }
        check(threw, "random: empty pool -> JSONException (parser turns it into ParseError)");
    }

    private static JSONObject sub(String name, boolean over18) throws Exception {
        return new JSONObject().put("kind", "t5").put("data", new JSONObject().put("display_name", name).put("over18", over18));
    }

    /** A post listing with a two-item gallery: items in a non-alphabetical order, one non-ASCII caption. */
    private static final String GALLERY_POST =
            "{\"kind\":\"Listing\",\"data\":{\"children\":[{\"kind\":\"t3\",\"data\":{\"id\":\"abc123\","
            + "\"gallery_data\":{\"items\":[{\"media_id\":\"second\",\"caption\":\"Caf\u00e9 \u2014 \u732b\","
            + "\"outbound_url\":\"https://example.com/?a=1&b=2\"},{\"media_id\":\"first\",\"caption\":\"Plain\"}]},"
            + "\"media_metadata\":{\"first\":{\"s\":{\"mp4\":\"https://i.redd.it/first.mp4\"}},"
            + "\"second\":{\"s\":{\"u\":\"https://i.redd.it/second.jpg?a=1&b=2\"}}}}}]}}";

    // URL and response cases partly adapted from 0naught's tests in upstream MR !7000.
    static void gallery() throws Exception {
        String info = "https://oauth.reddit.com/api/info.json?id=t3_";
        eq(FixGalleryPatch.rewriteGalleryUrl(info + "abc123"), "https://oauth.reddit.com/comments/abc123?limit=1&depth=0", "gallery: info -> comments");
        eq(FixGalleryPatch.rewriteGalleryUrl(info + "longpostid123456"), "https://oauth.reddit.com/comments/longpostid123456?limit=1&depth=0", "gallery: long post id");
        for (String url : new String[]{null, "", info, info + "abc&other=1", info + "t3_x,t3_y",
                "https://example.com/api/info.json?id=t3_abc", "https://oauth.reddit.com/comments/abc.json",
                "https://oauth.reddit.com/r/pics/hot.json"}) {
            check(FixGalleryPatch.rewriteGalleryUrl(url) == url, "gallery: left unchanged: " + url);
        }
        String body = " \n[" + GALLERY_POST + ",{\"kind\":\"Listing\",\"data\":{\"children\":[{\"kind\":\"t1\"}]}}]";
        JSONObject unwrapped = new JSONObject(FixGalleryPatch.unwrapPostListing(body));
        check(unwrapped.similar(new JSONObject(GALLERY_POST)), "gallery: post listing unchanged by the round trip");
        org.json.JSONArray items = unwrapped.getJSONObject("data").getJSONArray("children").getJSONObject(0)
                .getJSONObject("data").getJSONObject("gallery_data").getJSONArray("items");
        eq(items.getJSONObject(0).getString("media_id"), "second", "gallery: item order kept");
        eq(items.getJSONObject(0).getString("caption"), "Caf\u00e9 \u2014 \u732b", "gallery: non-ASCII caption kept");
        eq(FixGalleryPatch.unwrapPostListing(GALLERY_POST), GALLERY_POST, "gallery: object passes through");
        for (String bad : new String[]{"", "<html>Forbidden</html>", "{\"error\":403}", "[]", "[", "[null]",
                "[{\"data\":{\"children\":[]}}]", "[{\"data\":{\"children\":[{}]}}]"}) {
            eq(FixGalleryPatch.unwrapPostListing(bad), bad, "gallery: malformed passes through: " + bad);
        }
        eq(FixGalleryPatch.unwrapPostListing(null), null, "gallery: null passes through");
    }

    static void imgur() {
        check(FixImgurPatch.singleUrl("https://imgur.com/AbC123").endsWith("image/AbC123.jpg"), "imgur: page -> jpg");
        check(FixImgurPatch.singleUrl("https://i.imgur.com/AbC123.gifv").endsWith("image/AbC123.mp4"), "imgur: gifv -> mp4");
        check(FixImgurPatch.singleUrl("https://i.imgur.com/AbC123.gif?1").endsWith("image/AbC123.mp4"), "imgur: gif with query -> mp4");
        String local = FixImgurPatch.localResponse(FixImgurPatch.singleUrl("https://i.imgur.com/AbC123.jpg"));
        check(local != null && local.contains("\"link\":\"https://i.imgur.com/AbC123.jpg\""), "imgur: local image JSON");
        eq(FixImgurPatch.localResponse("https://oauth.reddit.com/hot.json"), null, "imgur: other URLs go to network");
        eq(FixImgurPatch.albumUrl("https://imgur.com/a/funny-cat-pics-AbC12d"), "https://imgur.com/a/AbC12d/embed?pub=true", "imgur: album slug URL -> embed page of trailing id");
        eq(FixImgurPatch.albumUrl("https://imgur.com/gallery/some-title-XyZ9"), "https://imgur.com/a/XyZ9/embed?pub=true", "imgur: gallery slug URL -> embed page");
        eq(FixImgurPatch.albumUrl("https://imgur.com/a/AbC12d"), "https://imgur.com/a/AbC12d/embed?pub=true", "imgur: plain album id");
        check(!FixImgurPatch.albumHeaders().containsKey("Authorization"), "imgur: no API key sent");
        imgurEmbed();
        check(FixImgurPatch.singleUrl("https://imgur.com/AbC123?foo=bar#x").endsWith("image/AbC123.jpg"), "imgur: query/fragment ignored");
        eq(FixImgurPatch.localResponse("https://revanced.invalid/imgur/image/..%2Fx.jpg"), "{}", "imgur: path tricks rejected");
    }

    static void linkPreviews() {
        String html = "<html><head><meta name=\"twitter:image\" content=\"https://i.imgur.com/a.jpg\">"
                + "<meta data-x=\"1\" property=\"og:image\" content=\"https://i.imgur.com/b.jpg?fb&amp;x=1\"></head>";
        eq(FixLinkPreviewsPatch.extractImage(html), "https://i.imgur.com/b.jpg?fb&x=1", "preview: og:image preferred, &amp; unescaped");
        eq(FixLinkPreviewsPatch.extractImage("<meta content='/img/c.png' property='og:image'>"), "/img/c.png", "preview: attribute order and quotes");
        eq(FixLinkPreviewsPatch.resolve("https://example.com/news/x", "/img/c.png"), "https://example.com/img/c.png", "preview: relative resolved");
        eq(FixLinkPreviewsPatch.resolve("https://example.com/", "//cdn.example.com/d.jpg"), "https://cdn.example.com/d.jpg", "preview: protocol-relative");
        eq(FixLinkPreviewsPatch.resolve("https://example.com/", "javascript:alert(1)"), null, "preview: non-http rejected");
        eq(FixLinkPreviewsPatch.extractImage("<html><head><title>x</title></head>"), null, "preview: none");
        eq(FixLinkPreviewsPatch.pageFor("imgur-album-AbC12"), "https://imgur.com/a/AbC12", "preview: imgur album key");
        eq(FixLinkPreviewsPatch.pageFor("imgur-album-../x"), null, "preview: bad album id");
        eq(FixLinkPreviewsPatch.pageFor("gfycat-Slug"), null, "preview: gfycat key dead");
        eq(FixLinkPreviewsPatch.pageFor("https://gfycat.com/x"), null, "preview: gfycat page dead");
        eq(FixLinkPreviewsPatch.pageFor("file:///etc/passwd"), null, "preview: non-http rejected");
        eq(FixLinkPreviewsPatch.pageFor("https://tenor.com/view/x-1"), "https://tenor.com/view/x-1", "preview: tenor page");
        // Cache: found entries live 6h, misses 15 min, and failures can be forgotten.
        FixLinkPreviewsPatch.remember("https://p/1", "https://i/1.jpg", 0);
        eq(FixLinkPreviewsPatch.cached("https://p/1", 60_000), "https://i/1.jpg", "preview cache: hit");
        eq(FixLinkPreviewsPatch.cached("https://p/1", 7 * 3600_000L), null, "preview cache: found entry expires");
        FixLinkPreviewsPatch.remember("https://p/2", null, 0);
        eq(FixLinkPreviewsPatch.cached("https://p/2", 60_000), "", "preview cache: negative hit");
        eq(FixLinkPreviewsPatch.cached("https://p/2", 16 * 60_000L), null, "preview cache: miss expires sooner");
        FixLinkPreviewsPatch.remember("https://p/3", "https://i/3.jpg", 0);
        FixLinkPreviewsPatch.forget("https://p/3");
        eq(FixLinkPreviewsPatch.cached("https://p/3", 1), null, "preview cache: forget after failed image");
    }

    /** Drives the real interceptor with a fake OkHttp chain: every failure must be an IOException. */
    static void previewInterceptor() throws Exception {
        FixLinkPreviewsPatch.fetchThroughChainForTests = true;
        String proxy = "https://ap.syncforreddit.com/image?url=";
        for (String og : new String[]{"http://x.example:99999/a.jpg", "https://foo_bar:1x/a.jpg", "https://ok.example/a.jpg"}) {
            FakeChain chain = new FakeChain(proxy + "https://site.example/page-" + og.hashCode(),
                    "<html><head><meta property=\"og:image\" content=\"" + og + "\"></head>", "text/html", 404);
            String outcome;
            try {
                okhttp3.Response r = new FixLinkPreviewsPatch().intercept(chain);
                outcome = "response " + r.code();
            } catch (java.io.IOException e) {
                outcome = "IOException";
            } catch (RuntimeException e) {
                outcome = "RuntimeException " + e;
            }
            eq(outcome, "IOException", "preview interceptor: og:image " + og + " fails as IOException, never crashes");
        }
        // A good page + image returns the image as the answer to the original proxy request.
        FakeChain ok = new FakeChain(proxy + "https://site.example/good",
                "<meta property=\"og:image\" content=\"https://cdn.example/i.png\">", "image/png", 200);
        okhttp3.Response r = new FixLinkPreviewsPatch().intercept(ok);
        eq(r.code(), 200, "preview interceptor: image served");
        eq(r.request().url().toString(), proxy + "https://site.example/good", "preview interceptor: bound to the original request");
        eq(ok.requested, java.util.Arrays.asList("https://site.example/good", "https://cdn.example/i.png"), "preview interceptor: page then image");
        // Non-proxy requests pass straight through, untouched.
        FakeChain pass = new FakeChain("https://i.redd.it/x.jpg", "", "image/jpeg", 200);
        new FixLinkPreviewsPatch().intercept(pass);
        eq(pass.requested, java.util.Collections.singletonList("https://i.redd.it/x.jpg"), "preview interceptor: other images pass through");
    }

    /** Chain that throws `failure` for https requests and answers http ones; call() is never canceled. */
    static final class UpgradeChain implements okhttp3.Interceptor.Chain {
        final okhttp3.Request request; final java.io.IOException failure;
        final java.util.List<String> requested = new java.util.ArrayList<>();
        boolean canceled;
        UpgradeChain(String url, String method, java.io.IOException failure) {
            okhttp3.Request.Builder b = new okhttp3.Request.Builder().url(url);
            if ("POST".equals(method)) b.post(okhttp3.RequestBody.create("x", null));
            this.request = b.build(); this.failure = failure;
        }
        @Override public okhttp3.Request request() { return request; }
        @Override public okhttp3.Response proceed(okhttp3.Request r) throws java.io.IOException {
            requested.add(r.method() + " " + r.url());
            if (r.url().isHttps() && failure != null) throw failure;
            return new okhttp3.Response.Builder().request(r).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("ok")
                    .body(okhttp3.ResponseBody.create("", null)).build();
        }
        @Override public okhttp3.Connection connection() { return null; }
        @Override public okhttp3.Call call() {
            return (okhttp3.Call) java.lang.reflect.Proxy.newProxyInstance(okhttp3.Call.class.getClassLoader(),
                    new Class<?>[]{okhttp3.Call.class}, (proxy, m, args) -> m.getName().equals("isCanceled") ? canceled : null);
        }
        @Override public int connectTimeoutMillis() { return 10000; }
        @Override public okhttp3.Interceptor.Chain withConnectTimeout(int t, java.util.concurrent.TimeUnit u) { return this; }
        @Override public int readTimeoutMillis() { return 0; }
        @Override public okhttp3.Interceptor.Chain withReadTimeout(int t, java.util.concurrent.TimeUnit u) { return this; }
        @Override public int writeTimeoutMillis() { return 0; }
        @Override public okhttp3.Interceptor.Chain withWriteTimeout(int t, java.util.concurrent.TimeUnit u) { return this; }
    }

    enum FakeResult { SUCCESS, SERVICE_MISSING, SERVICE_VERSION_UPDATE_REQUIRED, SERVICE_DISABLED, SERVICE_INVALID, NETWORK_ERROR }

    static void youTube() {
        check(FixYouTubePatch.unavailable(FakeResult.SERVICE_MISSING), "youtube: missing player service handled");
        check(FixYouTubePatch.unavailable(FakeResult.SERVICE_VERSION_UPDATE_REQUIRED), "youtube: 'update required' handled (current YouTube has no service)");
        check(!FixYouTubePatch.unavailable(FakeResult.NETWORK_ERROR), "youtube: other errors left to Sync");
        check(!FixYouTubePatch.unavailable(FakeResult.SERVICE_DISABLED), "youtube: Sync's own SERVICE_DISABLED path unchanged");
        check(!FixYouTubePatch.unavailable(null), "youtube: null");
        eq(YouTubeWebPlayer.videoId("https://www.youtube.com/watch?v=K3elJwmxsRA"), "K3elJwmxsRA", "youtube: watch URL");
        eq(YouTubeWebPlayer.videoId("https://youtu.be/K3elJwmxsRA?t=42"), "K3elJwmxsRA", "youtube: youtu.be");
        eq(YouTubeWebPlayer.videoId("https://m.youtube.com/watch?feature=share&amp;v=K3elJwmxsRA"), "K3elJwmxsRA", "youtube: v not first, escaped &");
        eq(YouTubeWebPlayer.videoId("https://www.youtube.com/shorts/K3elJwmxsRA"), "K3elJwmxsRA", "youtube: shorts");
        eq(YouTubeWebPlayer.videoId("https://www.youtube.com/live/K3elJwmxsRA?si=x"), "K3elJwmxsRA", "youtube: live");
        eq(YouTubeWebPlayer.videoId("https://www.youtube.com/channel/UCxyz"), null, "youtube: channel is not a video");
        eq(YouTubeWebPlayer.videoId("https://evil.example/watch?v=K3elJwmxsRA"), null, "youtube: other hosts");
        eq(YouTubeWebPlayer.startSeconds("https://youtu.be/x?t=90"), 90, "youtube: t=90");
        eq(YouTubeWebPlayer.startSeconds("https://www.youtube.com/watch?v=x&t=1m30s"), 90, "youtube: t=1m30s");
        eq(YouTubeWebPlayer.startSeconds("https://www.youtube.com/watch?v=x&t=1h2m3s"), 3723, "youtube: t=1h2m3s");
        eq(YouTubeWebPlayer.startSeconds("https://www.youtube.com/watch?v=x"), 0, "youtube: no start");
        eq(YouTubeWebPlayer.startSeconds("https://youtu.be/K3elJwmxsRA?t=2147483648"), 86400, "youtube: int overflow doesn't crash (capped)");
        eq(YouTubeWebPlayer.startSeconds("https://youtu.be/K3elJwmxsRA?t=99999999999999999999999"), 0, "youtube: absurd t ignored");
        eq(YouTubeWebPlayer.startSeconds("https://youtu.be/K3elJwmxsRA?t=99999999999h"), 86400, "youtube: huge hours capped");
        String page = YouTubeWebPlayer.html("K3elJwmxsRA", 90);
        check(page.contains("videoId:'K3elJwmxsRA'") && page.contains("start:90") && page.contains("origin:'https://www.reddit.com'"), "youtube: embed page carries id, start and origin");
        // Ad blocking
        check(YouTubeWebPlayer.isAdRequest("googleads.g.doubleclick.net", "/pagead/id"), "youtube ads: doubleclick");
        check(YouTubeWebPlayer.isAdRequest("www.youtube.com", "/pagead/adview"), "youtube ads: /pagead/");
        check(YouTubeWebPlayer.isAdRequest("www.youtube.com", "/api/stats/ads"), "youtube ads: ad stats");
        check(YouTubeWebPlayer.isAdRequest("www.youtube.com", "/get_midroll_info"), "youtube ads: midroll");
        check(!YouTubeWebPlayer.isAdRequest("rr3---sn-x.googlevideo.com", "/videoplayback"), "youtube ads: the video stream is never blocked");
        check(!YouTubeWebPlayer.isAdRequest("www.youtube.com", "/youtubei/v1/player"), "youtube ads: player request itself allowed (ads stripped from it)");
        check(!YouTubeWebPlayer.isAdRequest("www.youtube.com", "/embed/K3elJwmxsRA"), "youtube ads: embed page allowed");
        check(!YouTubeWebPlayer.isAdRequest("notdoubleclick.example", "/"), "youtube ads: unrelated host");
        String injected = YouTubeWebPlayer.inject("<!DOCTYPE html><html><head><script nonce=x>player()</script></head></html>");
        check(injected.indexOf("adPlacements") < injected.indexOf("player()") && injected.indexOf("<head>") < injected.indexOf("adPlacements"), "youtube ads: script injected first in <head>");
        check(YouTubeWebPlayer.inject("<p>no head</p>").startsWith("<script>"), "youtube ads: injected without <head> too");
        check(YouTubeWebPlayer.embedUrl("K3elJwmxsRA", 90).startsWith("https://www.youtube.com/embed/K3elJwmxsRA?") && YouTubeWebPlayer.embedUrl("K3elJwmxsRA", 90).contains("start=90"), "youtube ads: embed URL");
        check(YouTubeWebPlayer.AD_SCRIPT.contains("/youtubei\\/v1\\/player/") , "youtube ads: player-response regex escaped for JS");
        check(YouTubeWebPlayer.AD_SCRIPT.contains(YouTubeWebPlayer.WAIT_TEXT) && YouTubeWebPlayer.AD_SCRIPT.contains("SYNC_YT_WAIT_NOTICE"), "youtube ads: wait notice included");
        check(YouTubeWebPlayer.WAIT_TEXT.indexOf('\'') < 0 && YouTubeWebPlayer.STALL_TEXT.indexOf('\'') < 0, "youtube ads: notice texts safe in a JS string");
    }

    static org.json.JSONObject incident(String id, String name, String status, String impact, String... comps) throws Exception {
        org.json.JSONArray c = new org.json.JSONArray();
        for (String n : comps) c.put(new org.json.JSONObject().put("name", n));
        return new org.json.JSONObject().put("id", id).put("name", name).put("status", status).put("impact", impact)
                .put("resolved_at", status.equals("resolved") ? "2026-11-16T03:00:00.000-08:00" : org.json.JSONObject.NULL)
                .put("created_at", "2026-11-16T02:00:00.000-08:00").put("components", c)
                .put("incident_updates", new org.json.JSONArray().put(new org.json.JSONObject().put("id", "u-" + id)
                        .put("body", "We are investigating.").put("created_at", "2026-11-16T02:30:00.000-08:00")));
    }

    static void redditStatus() throws Exception {
        long now = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.US).parse("2026-11-16T03:00:00-0800").getTime();
        org.json.JSONObject none = new org.json.JSONObject();
        org.json.JSONObject ios = incident("a", "Apple iOS App - Apple Account login issues", "investigating", "minor", "Native Mobile Apps");
        org.json.JSONObject web = incident("b", "Slow page loads", "investigating", "minor", "Desktop Web", "Mobile Web");
        org.json.JSONObject done = incident("c", "Elevated errors", "resolved", "major", "Native Mobile Apps");
        org.json.JSONObject comments = incident("d", "Comments failing to load", "identified", "minor", "Comment Processing");
        org.json.JSONObject majorIos = incident("e", "iOS and site outage", "investigating", "major", "Native Mobile Apps");
        org.json.JSONObject bare = incident("f", "Investigating elevated errors", "investigating", "none");
        eq(FixRedditStatusPatch.relevantIncident(new org.json.JSONArray().put(ios).put(web).put(done), none, now), null, "status: iOS-only, web-only and resolved incidents ignored");
        eq(FixRedditStatusPatch.relevantIncident(new org.json.JSONArray().put(ios).put(comments), none, now).optString("id"), "d", "status: comment processing incident shown");
        eq(FixRedditStatusPatch.relevantIncident(new org.json.JSONArray().put(majorIos), none, now).optString("id"), "e", "status: major incidents shown whatever they name");
        eq(FixRedditStatusPatch.relevantIncident(new org.json.JSONArray().put(bare), none, now).optString("id"), "f", "status: incident without components shown");
        org.json.JSONObject api = incident("g", "Elevated errors on desktop and API", "investigating", "minor", "Desktop Web", "Reddit Infrastructure");
        eq(FixRedditStatusPatch.relevantIncident(new org.json.JSONArray().put(api), none, now).optString("id"), "g", "status: affected API component beats a client-sounding title");
        org.json.JSONObject nativeOnly = incident("h", "Feeds failing to load", "investigating", "minor", "Native Mobile Apps");
        eq(FixRedditStatusPatch.relevantIncident(new org.json.JSONArray().put(nativeOnly), none, now).optString("id"), "h", "status: app-only component with a generic title shown");
        org.json.JSONObject seen = FixRedditStatusPatch.record(none, comments, now);
        eq(FixRedditStatusPatch.relevantIncident(new org.json.JSONArray().put(comments), seen, now + 60_000), null, "status: shown incident not repeated");
        eq(FixRedditStatusPatch.relevantIncident(new org.json.JSONArray().put(comments), seen, now + FixRedditStatusPatch.RESHOW_MS).optString("id"), "d", "status: reshown after RESHOW_MS");
        org.json.JSONObject updated = new org.json.JSONObject(comments.toString());
        updated.getJSONArray("incident_updates").getJSONObject(0).put("id", "u-d2");
        eq(FixRedditStatusPatch.relevantIncident(new org.json.JSONArray().put(updated), seen, now + 60_000).optString("id"), "d", "status: reshown on a new update");
        org.json.JSONObject old = FixRedditStatusPatch.record(seen, web, now + 8L * 24 * 3600 * 1000);
        check(!old.has("d") && old.has("b"), "status: week-old entries dropped");
        String d = FixRedditStatusPatch.describe(comments, now);
        check(d.startsWith("Comments failing to load (minor impact, identified)\nAffected: Comment Processing"), "status: title line: " + d);
        check(d.contains("Latest update, 30 min ago: We are investigating."), "status: latest update with age: " + d);
        eq(FixRedditStatusPatch.ago("2026-11-16T10:00:00Z", now), "1 h ago", "status: Z offset");
        eq(FixRedditStatusPatch.ago("garbage", now), "", "status: unparseable time");
        check(FixRedditStatusPatch.healthy("not json"), "status: unreadable page is healthy");
        eq(FixRedditStatusPatch.message("orig"), "orig", "status: original text until an incident is found");
        check(!FixRedditStatusPatch.healthy(new org.json.JSONObject().put("incidents", new org.json.JSONArray().put(comments)).toString()), "status: relevant incident -> sheet");
        check(FixRedditStatusPatch.message("orig").startsWith("Comments failing to load"), "status: sheet shows the incident");
    }

    static void tumblr() throws Exception {
        String[] a = FixTumblrPatch.blogAndId("https://staff.tumblr.com/post/811987751056605184/some-slug");
        check(a != null && a[0].equals("staff") && a[1].equals("811987751056605184"), "tumblr: blog.tumblr.com/post/id");
        a = FixTumblrPatch.blogAndId("https://www.tumblr.com/markrosewater/832645643147395072/i-dont?source=share");
        check(a != null && a[0].equals("markrosewater") && a[1].equals("832645643147395072"), "tumblr: www.tumblr.com/blog/id");
        a = FixTumblrPatch.blogAndId("https://www.tumblr.com/blog/view/staff/811987751056605184");
        check(a != null && a[0].equals("staff"), "tumblr: /blog/view/blog/id");
        eq(FixTumblrPatch.blogAndId("https://evil.example/post/1"), null, "tumblr: other hosts ignored");
        eq(FixTumblrPatch.blogAndId("https://x.tumblr.com.evil.example/post/1"), null, "tumblr: lookalike host ignored");
        eq(FixTumblrPatch.blogAndId("https://staff.tumblr.com/post/abc"), null, "tumblr: non-numeric id ignored");
        String api = FixTumblrPatch.apiUrl("https://staff.tumblr.com/post/811987751056605184/x");
        check(api.startsWith("https://staff.tumblr.com/api/read/json?id=811987751056605184&"), "tumblr: v1 URL " + api);
        eq(FixTumblrPatch.apiUrl("https://example.com/x"), "https://example.com/x", "tumblr: unparseable URL unchanged");
        String images = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(fixtures, "tumblr_v1_images.js")), "UTF-8");
        org.json.JSONArray photos = new org.json.JSONObject(FixTumblrPatch.convert(images, api))
                .getJSONObject("response").getJSONArray("posts").getJSONObject(0).getJSONArray("photos");
        check(photos.length() > 0, "tumblr: images found in the post body");
        boolean big = true, small = true, unique = true;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < photos.length(); i++) {
            String o = photos.getJSONObject(i).getJSONObject("original_size").getString("url");
            String t = photos.getJSONObject(i).getJSONArray("alt_sizes").getJSONObject(0).getString("url");
            big &= o.matches("https://64\\.media\\.tumblr\\.com/.*/s(1280x1920|2048x3072|540x810|640x960)/.*");
            small &= t.contains("/s") && !t.contains("u_c1");
            unique &= seen.add(o.split("/s\\d")[0]);
        }
        check(big, "tumblr: largest size used " + photos);
        check(small && unique, "tumblr: one entry per image, no avatars");
        String text = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(fixtures, "tumblr_v1_text.js")), "UTF-8");
        boolean threw = false;
        try { FixTumblrPatch.convert(text, "https://markrosewater.tumblr.com/api/read/json?id=832055028126777344&revanced_tumblr=1"); } catch (org.json.JSONException e) { threw = true; }
        check(threw, "tumblr: text post -> parse error (and the browser)");
        eq(FixTumblrPatch.convert("{}", "https://api.example/other"), "{}", "tumblr: other requests untouched");
        String legacy = "var tumblr_api_read = {\"posts\":[{\"type\":\"photo\",\"photo-url-1280\":\"https://64.media.tumblr.com/tumblr_abc_1280.jpg\",\"photo-url-75\":\"https://64.media.tumblr.com/tumblr_abc_75sq.jpg\",\"photo-url-500\":\"https://64.media.tumblr.com/tumblr_abc_500.jpg\",\"photos\":[]}]};";
        java.util.List<String[]> one = FixTumblrPatch.images(legacy);
        check(one.size() == 1 && one.get(0)[0].endsWith("_1280.jpg") && one.get(0)[1].endsWith("_75sq.jpg"), "tumblr: legacy photo post");
        String set = "var tumblr_api_read = {\"posts\":[{\"type\":\"photo\",\"photos\":[{\"photo-url-1280\":\"https://64.media.tumblr.com/tumblr_a_1280.jpg\",\"photo-url-100\":\"https://64.media.tumblr.com/tumblr_a_100.jpg\"},{\"photo-url-1280\":\"https://64.media.tumblr.com/tumblr_b_1280.jpg\"}]}]};";
        check(FixTumblrPatch.images(set).size() == 2, "tumblr: legacy photoset");
    }

    static void redditVideo() {
        String dash = "https://v.redd.it/8yhty8ueyz1i1/DASHPlaylist.mpd";
        eq(FixRedditVideoPatch.videoSource("https://v.redd.it/8yhty8ueyz1i1/HLSPlaylist.m3u8"), dash, "video: HLS playlist -> DASH");
        eq(FixRedditVideoPatch.videoSource("https://v.redd.it/8yhty8ueyz1i1/HLSPlaylist.m3u8?a=1797440617%2CMzE&amp;v=1&f=sd"), dash, "video: signed hls_url -> DASH");
        eq(FixRedditVideoPatch.videoSource("https://v.redd.it/8yhty8ueyz1i1/CMAF_1080.mp4?source=fallback"), dash, "video: direct MP4 -> DASH (audio)");
        eq(FixRedditVideoPatch.videoSource(dash), dash, "video: DASH stays DASH");
        eq(FixRedditVideoPatch.videoSource("https://i.imgur.com/a.mp4"), "https://i.imgur.com/a.mp4", "video: other hosts untouched");
        eq(FixRedditVideoPatch.videoSource("https://v.redd.it.evil/x/HLSPlaylist.m3u8"), "https://v.redd.it.evil/x/HLSPlaylist.m3u8", "video: lookalike host untouched");
        eq(FixRedditVideoPatch.videoSource("https://v.redd.it/a/b/HLSPlaylist.m3u8"), "https://v.redd.it/a/b/HLSPlaylist.m3u8", "video: unexpected path untouched");
        eq(FixRedditVideoPatch.videoSource(null), null, "video: null");
    }

    static void httpsUpgrade() throws Exception {
        eq(HttpsUpgradeInterceptor.upgrade("http://i.imgur.com/a.gif?x=1", "i.imgur.com", 80), "https://i.imgur.com/a.gif?x=1", "https: plain upgrade");
        eq(HttpsUpgradeInterceptor.upgrade("http://example.com:80/a", "example.com", 80), "https://example.com/a", "https: explicit :80 dropped");
        eq(HttpsUpgradeInterceptor.upgrade("http://example.com", "example.com", 80), "https://example.com", "https: no path");
        eq(HttpsUpgradeInterceptor.upgrade("http://example.com:8080/a", "example.com", 8080), null, "https: odd port left alone");
        eq(HttpsUpgradeInterceptor.upgrade("http://192.168.1.10/a", "192.168.1.10", 80), null, "https: IP literal left alone");
        eq(HttpsUpgradeInterceptor.upgrade("http://printer.local/a", "printer.local", 80), null, "https: .local left alone");
        eq(HttpsUpgradeInterceptor.upgrade("http://localhost/a", "localhost", 80), null, "https: localhost left alone");
        eq(HttpsUpgradeInterceptor.upgrade("https://example.com/a", "example.com", 443), null, "https: already https");

        UpgradeChain ok = new UpgradeChain("http://upgrade-ok.example/a.jpg", "GET", null);
        new HttpsUpgradeInterceptor().intercept(ok);
        eq(ok.requested, java.util.Collections.singletonList("GET https://upgrade-ok.example/a.jpg"), "https: request goes out over https only");

        UpgradeChain tls = new UpgradeChain("http://no-tls.example/a", "GET", new javax.net.ssl.SSLHandshakeException("bad cert"));
        new HttpsUpgradeInterceptor().intercept(tls);
        eq(tls.requested, java.util.Arrays.asList("GET https://no-tls.example/a", "GET http://no-tls.example/a"), "https: TLS failure falls back to http");
        UpgradeChain again = new UpgradeChain("http://no-tls.example/b", "GET", new javax.net.ssl.SSLHandshakeException("bad cert"));
        new HttpsUpgradeInterceptor().intercept(again);
        eq(again.requested, java.util.Collections.singletonList("GET http://no-tls.example/b"), "https: host remembered as http-only");

        UpgradeChain post = new UpgradeChain("http://slow-post.example/a", "POST", new java.net.SocketTimeoutException("read"));
        boolean threw = false;
        try { new HttpsUpgradeInterceptor().intercept(post); } catch (java.net.SocketTimeoutException e) { threw = true; }
        check(threw && post.requested.size() == 1, "https: POST is never re-sent after a timeout");
        UpgradeChain postTls = new UpgradeChain("http://tls-after-send.example/a", "POST", new javax.net.ssl.SSLException("connection reset after write"));
        threw = false;
        try { new HttpsUpgradeInterceptor().intercept(postTls); } catch (javax.net.ssl.SSLException e) { threw = true; }
        check(threw && postTls.requested.size() == 1, "https: POST is not re-sent after a TLS error that may follow the write");
        UpgradeChain postHandshake = new UpgradeChain("http://no-cert-post.example/a", "POST", new javax.net.ssl.SSLHandshakeException("no cert"));
        new HttpsUpgradeInterceptor().intercept(postHandshake);
        eq(postHandshake.requested.size(), 2, "https: POST falls back after a handshake failure (nothing was sent)");
        UpgradeChain get = new UpgradeChain("http://slow-get.example/a", "GET", new java.net.SocketTimeoutException("connect"));
        new HttpsUpgradeInterceptor().intercept(get);
        eq(get.requested.size(), 2, "https: GET timeout falls back");
        UpgradeChain dns = new UpgradeChain("http://gone.example/a", "GET", new java.net.UnknownHostException("gone"));
        threw = false;
        try { new HttpsUpgradeInterceptor().intercept(dns); } catch (java.net.UnknownHostException e) { threw = true; }
        check(threw && dns.requested.size() == 1, "https: unknown host is not retried over http");
        UpgradeChain cancel = new UpgradeChain("http://canceled.example/a", "GET", new java.io.IOException("Canceled"));
        cancel.canceled = true;
        threw = false;
        try { new HttpsUpgradeInterceptor().intercept(cancel); } catch (java.io.IOException e) { threw = true; }
        check(threw && cancel.requested.size() == 1, "https: canceled call is not retried");

        okhttp3.OkHttpClient.Builder b = new okhttp3.OkHttpClient.Builder();
        HttpsUpgradeInterceptor.install(b);
        HttpsUpgradeInterceptor.install(b);
        eq(b.interceptors().size(), 1, "https: install is idempotent (newBuilder() copies)");
    }

    static void previewSafety() throws Exception {
        String[] priv = {"127.0.0.1", "10.0.0.5", "172.16.3.4", "192.168.1.1", "169.254.169.254", "100.64.0.1",
                "0.0.0.0", "224.0.0.1", "::1", "fe80::1", "fd00::1", "::ffff:192.168.1.1", "255.255.255.255"};
        for (String ip : priv)
            check(FixLinkPreviewsPatch.isPrivate(java.net.InetAddress.getByName(ip)), "preview dns: " + ip + " is private");
        for (String ip : new String[]{"151.101.1.140", "8.8.8.8", "2a04:4e42::396", "100.128.0.1"})
            check(!FixLinkPreviewsPatch.isPrivate(java.net.InetAddress.getByName(ip)), "preview dns: " + ip + " is public");
        boolean blocked = false;
        try { FixLinkPreviewsPatch.PUBLIC_ONLY_DNS.lookup("localhost"); } catch (java.net.UnknownHostException e) { blocked = true; }
        check(blocked, "preview dns: localhost refused");
        blocked = false;
        try { FixLinkPreviewsPatch.PUBLIC_ONLY_DNS.lookup("127.0.0.1"); } catch (java.net.UnknownHostException e) { blocked = true; }
        check(blocked, "preview dns: IP literal on loopback refused");

        // IP-literal hosts bypass DNS in OkHttp; the per-hop check must catch them (and redirects to them).
        check(FixLinkPreviewsPatch.blockedHop("192.168.1.1", null, false), "preview hop: private IPv4 literal");
        check(FixLinkPreviewsPatch.blockedHop("127.0.0.1", null, true), "preview hop: loopback literal even via proxy");
        check(FixLinkPreviewsPatch.blockedHop("::1", null, false), "preview hop: IPv6 loopback literal");
        check(FixLinkPreviewsPatch.blockedHop("example.com", java.net.InetAddress.getByName("10.1.2.3"), false), "preview hop: name resolving to LAN (rebinding)");
        check(!FixLinkPreviewsPatch.blockedHop("example.com", java.net.InetAddress.getByName("10.0.2.2"), true), "preview hop: proxy address itself is not the target");
        check(!FixLinkPreviewsPatch.blockedHop("example.com", java.net.InetAddress.getByName("93.184.215.14"), false), "preview hop: public address allowed");
        check(!FixLinkPreviewsPatch.blockedHop("8.8.8.8", null, false), "preview hop: public IPv4 literal allowed");
        // Redirects are followed by hand and every hop is checked before it is requested.
        java.util.List<String> hops = new java.util.ArrayList<>();
        okhttp3.Interceptor.Chain redirecting = new FakeChain("https://site.example/r", "", "image/png", 200) {
            @Override public okhttp3.Response proceed(okhttp3.Request r) {
                hops.add(r.url().toString());
                String to = r.url().encodedPath().equals("/r") ? "http://192.168.1.1/admin"
                        : r.url().encodedPath().equals("/ok") ? "/img.png" : null;
                okhttp3.Response.Builder b = new okhttp3.Response.Builder().request(r).protocol(okhttp3.Protocol.HTTP_1_1)
                        .message("x").body(okhttp3.ResponseBody.create("", null));
                return to != null ? b.code(302).header("Location", to).build() : b.code(200).header("Content-Type", "image/png").build();
            }
        };
        boolean stopped = false;
        try { FixLinkPreviewsPatch.fetch(redirecting, new okhttp3.Request.Builder().url("https://site.example/r").build()); }
        catch (java.net.UnknownHostException e) { stopped = true; }
        check(stopped && hops.equals(java.util.Collections.singletonList("https://site.example/r")), "preview fetch: redirect to a LAN IP is refused before it is requested");
        hops.clear();
        okhttp3.Response followed = FixLinkPreviewsPatch.fetch(redirecting, new okhttp3.Request.Builder().url("https://site.example/ok").build());
        eq(followed.request().url().toString(), "https://site.example/img.png", "preview fetch: relative redirect followed");
        stopped = false;
        try { FixLinkPreviewsPatch.fetch(redirecting, new okhttp3.Request.Builder().url("http://10.0.0.1/x").build()); }
        catch (java.net.UnknownHostException e) { stopped = true; }
        check(stopped, "preview fetch: private IP literal refused on the first request");
        // Oversized preview image fails and is remembered as "no preview": by Content-Length, and also
        // without one (chunked/compressed), by counting the decoded bytes.
        FixLinkPreviewsPatch.fetchThroughChainForTests = true;
        String proxy = "https://ap.syncforreddit.com/image?url=";
        for (String key : new String[]{"huge", "chunked"}) {
            final boolean declared = key.equals("huge");
            okhttp3.Interceptor.Chain big = new FakeChain(proxy + "https://site.example/" + key,
                    "<meta property=\"og:image\" content=\"https://cdn.example/" + key + ".png\">", "image/png", 200) {
                @Override public okhttp3.Response proceed(okhttp3.Request r) {
                    okhttp3.Response res = super.proceed(r);
                    if (r.url().host().startsWith("site.")) return res;
                    byte[] payload = new byte[declared ? 10 : FixLinkPreviewsPatch.MAX_IMAGE_BYTES + 1];
                    okhttp3.ResponseBody body = declared
                            ? okhttp3.ResponseBody.create(payload, okhttp3.MediaType.parse("image/png"))
                            : new okhttp3.ResponseBody() {   // unknown length, like chunked/gzip
                                final okio.Buffer buf = new okio.Buffer().write(payload);
                                @Override public okhttp3.MediaType contentType() { return okhttp3.MediaType.parse("image/png"); }
                                @Override public long contentLength() { return -1; }
                                @Override public okio.BufferedSource source() { return buf; }
                            };
                    okhttp3.Response.Builder b = res.newBuilder().body(body);
                    return declared ? b.header("Content-Length", "99999999").build() : b.build();
                }
            };
            boolean failed = false;
            try { new FixLinkPreviewsPatch().intercept(big); } catch (java.io.IOException e) { failed = e.getMessage().contains("too large"); }
            check(failed, "preview: image over the size cap is refused (" + key + ")");
            eq(FixLinkPreviewsPatch.cached("https://site.example/" + key, System.currentTimeMillis()), "", "preview: oversized image cached as no preview (" + key + ")");
        }
    }

    static void redditImageUrls() throws Exception {
        String esc = "https://preview.redd.it/z5vaj1j1ks1i1.jpeg?width=140&amp;height=108&amp;auto=webp&amp;s=f06ab17ae849cc7c8530cf3c409eecd1fbd23ff5";
        String raw = "https://preview.redd.it/z5vaj1j1ks1i1.jpeg?width=140&height=108&auto=webp&s=f06ab17ae849cc7c8530cf3c409eecd1fbd23ff5";
        FakeChain chain = new FakeChain(esc, "", "image/jpeg", 200);
        new RedditImageUrlInterceptor().intercept(chain);
        eq(chain.requested.get(0), raw, "image urls: preview.redd.it &amp; unescaped on the wire");
        eq(RedditImageUrlInterceptor.fix("https://external-preview.redd.it/a.png?x=1&amp;amp;s=2", "external-preview.redd.it"),
                "https://external-preview.redd.it/a.png?x=1&s=2", "image urls: double escape");
        eq(RedditImageUrlInterceptor.fix(raw, "preview.redd.it"), null, "image urls: clean URL untouched");
        eq(RedditImageUrlInterceptor.fix("https://example.com/a?b=1&amp;c=2", "example.com"), null, "image urls: other hosts untouched");
        eq(RedditImageUrlInterceptor.fix("https://notredd.it/a?b=1&amp;c=2", "notredd.it"), null, "image urls: lookalike host untouched");
        FakeChain other = new FakeChain("https://i.imgur.com/a.jpg?x=1&amp;y=2", "", "image/jpeg", 200);
        new RedditImageUrlInterceptor().intercept(other);
        check(other.requested.get(0).contains("&amp;"), "image urls: non-Reddit request passed through as is");
    }

    static void gfycatBlock() throws Exception {
        for (String url : new String[]{"https://thumbs.gfycat.com/X-poster.jpg", "https://gfycat.com/favicon.ico"}) {
            FakeChain chain = new FakeChain(url, "", "image/jpeg", 200);
            boolean blocked = false;
            try { new GfycatBlockInterceptor().intercept(chain); } catch (java.io.IOException e) { blocked = true; }
            check(blocked && chain.requested.isEmpty(), "gfycat block: " + url + " fails locally");
        }
        FakeChain ok = new FakeChain("https://i.redd.it/x.jpg", "", "image/jpeg", 200);
        new GfycatBlockInterceptor().intercept(ok);
        eq(ok.requested.size(), 1, "gfycat block: other hosts pass");
        check(!GfycatBlockInterceptor.blocked("notgfycat.com"), "gfycat block: lookalike host passes");
    }

    /** Minimal OkHttp chain: pages get `html` (status 200), image URLs get an image with `imageStatus`. */
    static class FakeChain implements okhttp3.Interceptor.Chain {
        final okhttp3.Request request;
        final String html, imageType;
        final int imageStatus;
        final java.util.List<String> requested = new java.util.ArrayList<>();

        FakeChain(String url, String html, String imageType, int imageStatus) {
            this.request = new okhttp3.Request.Builder().url(url).build();
            this.html = html; this.imageType = imageType; this.imageStatus = imageStatus;
        }
        @Override public okhttp3.Request request() { return request; }
        @Override public okhttp3.Response proceed(okhttp3.Request r) {
            requested.add(r.url().toString());
            boolean page = r.url().host().startsWith("site.");
            return new okhttp3.Response.Builder().request(r).protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(page ? 200 : imageStatus).message("x")
                    .header("Content-Type", page ? "text/html" : imageType)
                    .body(okhttp3.ResponseBody.create(page ? html : "img", okhttp3.MediaType.parse(page ? "text/html" : imageType)))
                    .build();
        }
        @Override public okhttp3.Connection connection() { return null; }
        @Override public okhttp3.Call call() { return null; }
        @Override public int connectTimeoutMillis() { return 0; }
        @Override public okhttp3.Interceptor.Chain withConnectTimeout(int t, java.util.concurrent.TimeUnit u) { return this; }
        @Override public int readTimeoutMillis() { return 0; }
        @Override public okhttp3.Interceptor.Chain withReadTimeout(int t, java.util.concurrent.TimeUnit u) { return this; }
        @Override public int writeTimeoutMillis() { return 0; }
        @Override public okhttp3.Interceptor.Chain withWriteTimeout(int t, java.util.concurrent.TimeUnit u) { return this; }
    }

    static void loginRedirect() {
        check(FixLoginRedirectPatch.isRedditHomepage("https://www.reddit.com/"), "login: homepage");
        check(FixLoginRedirectPatch.isRedditHomepage("https://reddit.com"), "login: bare host");
        check(!FixLoginRedirectPatch.isRedditHomepage("https://www.reddit.com/api/v1/authorize?x"), "login: authorize page is not home");
        check(!FixLoginRedirectPatch.isRedditHomepage("https://evilreddit.com/"), "login: lookalike host");
        check(!FixLoginRedirectPatch.isRedditHomepage(null), "login: null");
    }
}
