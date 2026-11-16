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

    public static boolean patchResolveSLink(String link) {
        // The shared resolver matches ".*reddit\\.com/r/x/s/y" and sends the user's bearer token to
        // that URL, so "https://evil.example/reddit.com/r/a/s/b" or an http:// link would leak it.
        // Only hand it canonical HTTPS Reddit share links; anything else takes Sync's normal path.
        String safe = SLinkGuard.safeSLink(link);
        return safe != null && INSTANCE.resolveSLink(safe);
    }

    public static void patchSetAccessToken(String accessToken) {
        INSTANCE.setAccessToken(accessToken);
    }
}
