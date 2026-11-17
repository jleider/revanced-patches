package app.revanced.extension.syncforreddit;

import com.laurencedawson.reddit_sync.ui.activities.WebViewActivity;

import app.revanced.extension.shared.fixes.slink.BaseFixSLinksPatch;

/** @noinspection unused*/
public class FixSLinksPatch extends BaseFixSLinksPatch {
    static {
        INSTANCE = new FixSLinksPatch();
    }

    private FixSLinksPatch() {
        webViewActivityClass = WebViewActivity.class;
    }

    /** Injection point. */
    public static boolean patchResolveSLink(String link) {
        try {
            // The shared resolver attaches the bearer token using a broad URL match.
            // Check for a canonical HTTPS Reddit share link before handing it over.
            String safeLink = SLinkGuard.safeSLink(link);
            return safeLink != null && INSTANCE.resolveSLink(safeLink);
        } catch (Exception ex) {
            app.revanced.extension.shared.Logger.printException(() -> "Could not resolve the Reddit share link", ex);
            return false;
        }
    }

    public static void patchSetAccessToken(String accessToken) {
        INSTANCE.setAccessToken(accessToken);
    }
}
