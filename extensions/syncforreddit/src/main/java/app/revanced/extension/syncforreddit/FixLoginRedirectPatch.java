package app.revanced.extension.syncforreddit;

import android.net.Uri;
import android.webkit.WebView;

import app.revanced.extension.shared.Logger;

/**
 * Reddit's current login page ignores the OAuth "dest" parameter and lands on the
 * homepage after a successful login, so Sync's login WebView never reaches the
 * authorize (consent) page. Once the WebView finishes loading a bare reddit.com
 * homepage we re-open the authorize URL; the session cookie is set by then.
 *
 * Known limitation: if Android kills the process while the login dialog is open and later
 * restores it, the authorize URL isn't rebuilt and the fix is inactive until the dialog is
 * reopened.
 */
@SuppressWarnings("unused")
public final class FixLoginRedirectPatch {
    private static final int MAX_RETRIES = 3;
    // WebView can report progress 100 several times for one page, and loadUrl is async, so
    // getUrl() still shows the homepage for a moment after we redirect. Ignore repeats.
    private static final long DEBOUNCE_MS = 3000;
    private static volatile String authorizeUrl;
    private static int retries;
    private static long lastRedirectAt;

    /** Injection point. Called with the authorize URL Sync is about to load. */
    public static void rememberAuthorizeUrl(String url) {
        if (url != null && url.contains("/api/v1/authorize")) {
            authorizeUrl = url;
            retries = 0;
            lastRedirectAt = 0;
        }
    }

    /** Injection point. Called from the login WebChromeClient's onProgressChanged. */
    public static void onProgressChanged(WebView view, int progress) {
        try {
            if (progress != 100 || view == null || authorizeUrl == null) {
                return;
            }
            if (!isRedditHomepage(view.getUrl())) {
                return;
            }
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastRedirectAt < DEBOUNCE_MS) {
                return;
            }
            if (retries >= MAX_RETRIES) {
                return;
            }
            retries++;
            lastRedirectAt = now;
            view.loadUrl(authorizeUrl);
        } catch (Exception ex) {
            Logger.printException(() -> "Could not redirect the Reddit login", ex);
        }
    }

    static boolean isRedditHomepage(String url) {
        if (url == null) {
            return false;
        }
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        if (host == null || !(host.equals("reddit.com") || host.endsWith(".reddit.com"))) {
            return false;
        }
        String path = uri.getPath();
        return path == null || path.isEmpty() || path.equals("/");
    }
}
