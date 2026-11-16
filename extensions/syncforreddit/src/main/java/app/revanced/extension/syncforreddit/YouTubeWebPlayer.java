package app.revanced.extension.syncforreddit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * In-app YouTube playback without the YouTube Android Player API (removed from current YouTube apps).
 * Sync's CustomYouTubeVideoActivity gets its content replaced by a WebView hosting YouTube's IFrame
 * embed player. The page is loaded with a reddit.com base URL, so the embed receives a Referer/origin
 * (YouTube refuses embeds without one, "error 153"). Fullscreen uses WebChromeClient.onShowCustomView.
 * Videos whose owner disabled embedding (IFrame API errors 101/150/153) open in the YouTube app.
 * <p>
 * With "Block YouTube ads" (patch option, default on) the embed page itself is the main document:
 * it is fetched here with the reddit.com referer, its CSP header is dropped and a script is injected
 * that removes ad placements from the player data (initial response, fetch and JSON.parse), so the
 * player has no ads to show; requests to ad and ad-tracking hosts are refused as a backstop.
 *
 * @noinspection unused
 */
public final class YouTubeWebPlayer {
    private static final String SERVICE_ACTION = "com.google.android.youtube.api.service.START";
    private static final String ERROR_TAG = "SYNC_YT_ERROR:";
    private static final Pattern ID = Pattern.compile(
            "(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/|live/|v/)|youtu\\.be/|youtube-nocookie\\.com/embed/)([A-Za-z0-9_-]{11})");
    private static final Pattern T = Pattern.compile("[?&#](?:t|start|time_continue)=([0-9hms]+)");

    private static final String REFERER = "https://www.reddit.com/";
    private static volatile OkHttpClient client;

    private YouTubeWebPlayer() {}

    /** Replaced at patch time with the "Block YouTube ads" option. */
    public static boolean blockAds() {
        return true;
    }

    /** Shown over the player while YouTube withholds the video after its ads were removed. */
    static final String WAIT_TEXT = "Ad blocked \u00b7 YouTube delays the video a few seconds\u2026 ";
    static final String STALL_TEXT = "Waiting for YouTube\u2026 ";

    private static final String WAIT_NOTICE = "var last=-1,since=0,el=null,shown=0;"
            + "function hide(){if(el)el.hidden=true;}"
            + "setInterval(function(){var p=document.getElementById('movie_player');if(!p||!A)return;"
            + "var v=p.querySelector('video');if(!v)return;var st=p.getPlayerState?p.getPlayerState():-1,now=Date.now(),c=v.currentTime;"
            + "if(st===0||st===2||st===5||p.classList.contains('ad-showing')||c!==last){last=c;since=now;hide();return;}"
            + "var w=Math.floor((now-since)/1000);if(w<2)return;"
            + "if(!el){el=document.createElement('div');el.id='sync-yt-wait';el.setAttribute('style','position:absolute!important;left:50%!important;top:calc(50% + 34px)!important;"
            + "transform:translateX(-50%)!important;z-index:2147483647!important;width:max-content!important;max-width:80%!important;"
            + "padding:6px 12px!important;border-radius:14px!important;background:rgba(0,0,0,.75)!important;color:#fff!important;"
            + "font:13px/1.35 Roboto,Arial,sans-serif!important;text-align:center!important;pointer-events:none!important');}"
            + "if(el.parentNode!==p)p.appendChild(el);"
            + "el.textContent=(c<0.5?'" + WAIT_TEXT + "':'" + STALL_TEXT + "')+w+'s';el.hidden=false;"
            + "if(!shown){shown=1;console.log('SYNC_YT_WAIT_NOTICE');}},250);";

    /**
     * Injected first into the embed page (same origin as the player): strip ads, report errors.
     * When ads were removed, YouTube's server withholds the video for roughly the length of the
     * skipped ad (~10 s). A notice with a seconds counter then explains the stalled spinner; it
     * shows only while the video is not advancing (never when paused, ended or cued) and hides as
     * soon as playback moves.
     */
    static final String AD_SCRIPT = "(function(){"
            + "var K=['adPlacements','playerAds','adSlots','adBreakHeartbeatParams','adBreakParams'],A=0;"
            + "function s(o){try{if(o&&typeof o==='object'){for(var i=0;i<K.length;i++){if(K[i] in o){delete o[K[i]];A=1;console.log('SYNC_YT_AD_REMOVED:'+K[i]);}}"
            + "if(o.playerResponse)s(o.playerResponse);}}catch(e){}return o;}"
            + "var P=JSON.parse;JSON.parse=function(){return s(P.apply(this,arguments));};"
            + "var R;try{Object.defineProperty(window,'ytInitialPlayerResponse',{configurable:true,"
            + "get:function(){return R;},set:function(v){R=s(v);}});}catch(e){}"
            + "var F=window.fetch;if(F){window.fetch=function(){var a=arguments;return F.apply(this,a).then(function(r){"
            + "try{var u=(a[0]&&a[0].url)||String(a[0]);if(/\\/youtubei\\/v1\\/player/.test(u)){"
            + "return r.clone().json().then(function(j){return new Response(JSON.stringify(s(j)),"
            + "{status:r.status,statusText:r.statusText,headers:r.headers});}).catch(function(){return r;});}}catch(e){}"
            + "return r;});};}"
            + "var t=setInterval(function(){var p=document.getElementById('movie_player');"
            + "if(p&&p.addEventListener){clearInterval(t);p.addEventListener('onError',function(e){console.log('" + "SYNC_YT_ERROR:" + "'+e);});"
            + "if(p.playVideo)p.playVideo();}},300);"
            + WAIT_NOTICE
            + "})();";

    /** The embed page URL for the main-document (ad-free) mode. */
    static String embedUrl(String id, int start) {
        return "https://www.youtube.com/embed/" + id + "?autoplay=1&playsinline=1&rel=0&fs=1&enablejsapi=1&start=" + start
                + "&origin=https%3A%2F%2Fwww.reddit.com";
    }

    /** Puts the ad script before any of the page's own scripts. */
    static String inject(String html) {
        String tag = "<script>" + AD_SCRIPT + "</script>";
        int head = html.toLowerCase(Locale.ROOT).indexOf("<head");
        if (head >= 0) {
            int end = html.indexOf('>', head);
            if (end > 0) return html.substring(0, end + 1) + tag + html.substring(end + 1);
        }
        return tag + html;
    }

    /** Ad serving and ad tracking requests (never the video itself). */
    static boolean isAdRequest(String host, String path) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        path = path == null ? "" : path;
        if (host.endsWith("doubleclick.net") || host.endsWith("googlesyndication.com")
                || host.endsWith("googleadservices.com") || host.equals("imasdk.googleapis.com")) return true;
        boolean yt = host.equals("youtube.com") || host.endsWith(".youtube.com");
        return yt && (path.startsWith("/pagead/") || path.startsWith("/api/stats/ads") || path.startsWith("/ptracking")
                || path.startsWith("/get_midroll_info") || path.startsWith("/pcs/activeview")
                || path.startsWith("/youtubei/v1/player/ad_break"));
    }

    static boolean isEmbedPage(Uri url) {
        return "https".equals(url.getScheme()) && "www.youtube.com".equals(url.getHost())
                && url.getPath() != null && url.getPath().startsWith("/embed/");
    }

    private static OkHttpClient client() {
        if (client == null) {
            synchronized (YouTubeWebPlayer.class) {
                if (client == null) client = new OkHttpClient();
            }
        }
        return client;
    }

    private static WebResourceResponse empty() {
        return new WebResourceResponse("text/plain", "utf-8", 204, "No Content", new HashMap<>(), new ByteArrayInputStream(new byte[0]));
    }

    /** Fetches the embed page with the reddit.com referer and returns it with the ad script injected. */
    private static WebResourceResponse injectedEmbedPage(String url, String userAgent) {
        try (Response r = client().newCall(new Request.Builder().url(url)
                .header("Referer", REFERER).header("User-Agent", userAgent).build()).execute()) {
            if (r.body() == null) return null;
            String html = inject(r.body().string());
            android.util.Log.i("SyncYouTube", "ad-free embed page loaded (" + r.code() + ")");
            Map<String, String> headers = new HashMap<>();
            for (String name : r.headers().names()) {
                String n = name.toLowerCase(Locale.ROOT);
                if (n.startsWith("content-security-policy") || n.equals("content-length") || n.equals("content-encoding")
                        || n.equals("transfer-encoding") || n.equals("content-type")) continue;
                headers.put(name, r.header(name));
            }
            String reason = r.message().isEmpty() ? "OK" : r.message();
            return new WebResourceResponse("text/html", "utf-8", r.code(), reason, headers,
                    new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;   // let the WebView load it normally (with ads)
        }
    }

    /** The 11-character video id of a YouTube URL, or null. */
    static String videoId(String url) {
        if (url == null) return null;
        Matcher m = ID.matcher(url.replace("&amp;", "&"));
        return m.find() ? m.group(1) : null;
    }

    /** Start offset in seconds from t=/start= (90, 1m30s, 1h2m3s), or 0; never throws, capped at a day. */
    static int startSeconds(String url) {
        if (url == null) return 0;
        Matcher m = T.matcher(url.replace("&amp;", "&"));
        if (!m.find()) return 0;
        String t = m.group(1).toLowerCase(Locale.ROOT);
        long total = 0;
        try {
            if (t.matches("\\d+")) {
                total = Long.parseLong(t);
            } else {
                Matcher part = Pattern.compile("(\\d+)([hms])").matcher(t);
                while (part.find()) {
                    long n = Long.parseLong(part.group(1));
                    total += part.group(2).equals("h") ? n * 3600 : part.group(2).equals("m") ? n * 60 : n;
                }
            }
        } catch (NumberFormatException e) {
            return 0;   // absurd values (more digits than a long): start at the beginning
        }
        return (int) Math.max(0, Math.min(total, 86_400));
    }

    /** The page hosting the IFrame player. Errors are reported through console messages (no JS bridge). */
    static String html(String id, int start) {
        return "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{width:100%;height:100%}</style>"
                + "</head><body><div id=p></div><script>"
                + "var tag=document.createElement('script');tag.src='https://www.youtube.com/iframe_api';document.head.appendChild(tag);"
                + "function onYouTubeIframeAPIReady(){window.player=new YT.Player('p',{videoId:'" + id + "',"
                + "playerVars:{autoplay:1,playsinline:1,rel:0,fs:1,start:" + start + ",origin:'https://www.reddit.com'},"
                + "events:{onReady:function(e){e.target.playVideo();},"
                + "onError:function(e){console.log('" + ERROR_TAG + "'+e.data);}}});}"
                + "</script></body></html>";
    }

    static boolean playerServiceAvailable(Activity activity) {
        try {
            Intent intent = new Intent(SERVICE_ACTION).setPackage("com.google.android.youtube");
            return !activity.getPackageManager().queryIntentServices(intent, 0).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** Lifecycle state shared by the clients: fallbacks fire once, and only while the screen is in front. */
    static final class State {
        boolean resumed = true;
        boolean done;   // fallback taken or activity destroyed

        boolean mayLeave(Activity activity) {
            return !done && resumed && !activity.isFinishing() && !activity.isDestroyed();
        }
    }

    static void openExternally(Activity activity, String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
            // nothing can open it
        }
        activity.finish();
    }

    /**
     * Called first in CustomYouTubeVideoActivity's player setup. True = the web player took over, so
     * Sync must not initialize Google's player. False = the Player API service exists: Sync's own path.
     */
    @SuppressLint("SetJavaScriptEnabled")
    public static boolean show(Activity activity) {
        if (playerServiceAvailable(activity)) return false;
        String url = activity.getIntent() == null ? null : activity.getIntent().getStringExtra("url");
        String id = videoId(url);
        if (id == null) return false;   // Sync shows "Invalid ID"

        State state = new State();
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.BLACK);
        WebView web = new WebView(activity);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        boolean adFree = blockAds();
        String userAgent = s.getUserAgentString();
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (!adFree) return null;
                Uri u = request.getUrl();
                if (isAdRequest(u.getHost(), u.getPath())) return empty();
                if (request.isForMainFrame() && "GET".equals(request.getMethod()) && isEmbedPage(u))
                    return injectedEmbedPage(u.toString(), userAgent);
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Links inside the player (title, "Watch on YouTube", channel) leave Sync.
                if (request.isForMainFrame()) {
                    if (state.mayLeave(activity)) {
                        state.done = true;
                        openExternally(activity, request.getUrl().toString());
                    }
                    return true;
                }
                return false;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            private View fullscreen;
            private CustomViewCallback callback;

            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (fullscreen != null) { cb.onCustomViewHidden(); return; }
                fullscreen = view;
                callback = cb;
                root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                web.setVisibility(View.GONE);
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }

            @Override
            public void onHideCustomView() {
                if (fullscreen == null) return;
                root.removeView(fullscreen);
                fullscreen = null;
                web.setVisibility(View.VISIBLE);
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                if (callback != null) callback.onCustomViewHidden();
                callback = null;
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                String text = message.message();
                if (text != null && text.startsWith(ERROR_TAG)) {
                    // 2 bad id, 5 HTML5 error, 100 not found/private, 101/150/153 embedding not allowed.
                    // Only while the user is still on this screen, and only once.
                    if (state.mayLeave(activity)) {
                        state.done = true;
                        openExternally(activity, url);
                    }
                    return true;
                }
                return false;
            }
        });

        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activity.setContentView(root);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (adFree) {
            Map<String, String> referer = new HashMap<>();
            referer.put("Referer", REFERER);
            web.loadUrl(embedUrl(id, startSeconds(url)), referer);
        } else {
            web.loadDataWithBaseURL(REFERER, html(id, startSeconds(url)), "text/html", "utf-8", null);
        }
        tieToLifecycle(activity, web, state);
        return true;
    }

    /** Pause with the activity and destroy with it, so audio never outlives the screen. */
    private static void tieToLifecycle(Activity activity, WebView web, State state) {
        Application app = activity.getApplication();
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityPaused(Activity a) {
                if (a != activity) return;
                state.resumed = false;
                // WebView.onPause doesn't stop JavaScript; pause the video through the player API too.
                web.evaluateJavascript("window.player&&window.player.pauseVideo&&window.player.pauseVideo()", null);
                web.onPause();
            }
            @Override public void onActivityResumed(Activity a) {
                if (a != activity) return;
                state.resumed = true;
                web.onResume();
            }
            @Override public void onActivityDestroyed(Activity a) {
                if (a != activity) return;
                state.done = true;
                app.unregisterActivityLifecycleCallbacks(this);
                // Detach callbacks before tearing down, so late page events can't reach a dead activity.
                web.setWebChromeClient(null);
                web.setWebViewClient(new WebViewClient());
                web.stopLoading();
                web.loadUrl("about:blank");
                web.destroy();
            }
            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override public void onActivityStarted(Activity a) { }
            @Override public void onActivityStopped(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
        });
    }
}
