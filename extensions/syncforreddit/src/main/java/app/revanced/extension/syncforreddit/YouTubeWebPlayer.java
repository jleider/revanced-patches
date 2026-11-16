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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-app YouTube playback without the YouTube Android Player API (removed from current YouTube apps).
 * Sync's CustomYouTubeVideoActivity gets its content replaced by a WebView hosting YouTube's IFrame
 * embed player. The page is loaded with a reddit.com base URL, so the embed receives a Referer/origin
 * (YouTube refuses embeds without one, "error 153"). Fullscreen uses WebChromeClient.onShowCustomView.
 * Videos whose owner disabled embedding (IFrame API errors 101/150/153) open in the YouTube app.
 *
 * @noinspection unused
 */
public final class YouTubeWebPlayer {
    private static final String SERVICE_ACTION = "com.google.android.youtube.api.service.START";
    private static final String ERROR_TAG = "SYNC_YT_ERROR:";
    private static final Pattern ID = Pattern.compile(
            "(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/|live/|v/)|youtu\\.be/|youtube-nocookie\\.com/embed/)([A-Za-z0-9_-]{11})");
    private static final Pattern T = Pattern.compile("[?&#](?:t|start|time_continue)=([0-9hms]+)");

    private YouTubeWebPlayer() {}

    /** The 11-character video id of a YouTube URL, or null. */
    static String videoId(String url) {
        if (url == null) return null;
        Matcher m = ID.matcher(url.replace("&amp;", "&"));
        return m.find() ? m.group(1) : null;
    }

    /** Start offset in seconds from t=/start= (90, 1m30s, 1h2m3s), or 0. */
    static int startSeconds(String url) {
        if (url == null) return 0;
        Matcher m = T.matcher(url.replace("&amp;", "&"));
        if (!m.find()) return 0;
        String t = m.group(1).toLowerCase(Locale.ROOT);
        if (t.matches("\\d+")) return Integer.parseInt(t);
        int total = 0;
        Matcher part = Pattern.compile("(\\d+)([hms])").matcher(t);
        while (part.find()) {
            int n = Integer.parseInt(part.group(1));
            total += part.group(2).equals("h") ? n * 3600 : part.group(2).equals("m") ? n * 60 : n;
        }
        return total;
    }

    /** The page hosting the IFrame player. Errors are reported through console messages (no JS bridge). */
    static String html(String id, int start) {
        return "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{width:100%;height:100%}</style>"
                + "</head><body><div id=p></div><script>"
                + "var tag=document.createElement('script');tag.src='https://www.youtube.com/iframe_api';document.head.appendChild(tag);"
                + "function onYouTubeIframeAPIReady(){new YT.Player('p',{videoId:'" + id + "',"
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

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Links inside the player (title, "Watch on YouTube", channel) leave Sync.
                if (request.isForMainFrame()) {
                    openExternally(activity, request.getUrl().toString());
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
                    openExternally(activity, url);
                    return true;
                }
                return false;
            }
        });

        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activity.setContentView(root);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web.loadDataWithBaseURL("https://www.reddit.com/", html(id, startSeconds(url)), "text/html", "utf-8", null);
        tieToLifecycle(activity, web);
        return true;
    }

    /** Pause with the activity and destroy with it, so audio never outlives the screen. */
    private static void tieToLifecycle(Activity activity, WebView web) {
        Application app = activity.getApplication();
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityPaused(Activity a) { if (a == activity) web.onPause(); }
            @Override public void onActivityResumed(Activity a) { if (a == activity) web.onResume(); }
            @Override public void onActivityDestroyed(Activity a) {
                if (a != activity) return;
                app.unregisterActivityLifecycleCallbacks(this);
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
