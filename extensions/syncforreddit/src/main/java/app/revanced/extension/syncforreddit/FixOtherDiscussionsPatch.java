package app.revanced.extension.syncforreddit;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.revanced.extension.shared.Logger;

/**
 * "Other discussions" used the logged-out https://ssl.reddit.com/submit.json?url=<url>, which now
 * 301s to www.reddit.com (an HTML block page). OAuth search with url:<url> returns the posts of
 * that link as a single Listing, the shape OAuthPostsRequest already parses, and keeps the token.
 */
@SuppressWarnings("unused")
public final class FixOtherDiscussionsPatch {
    // Accept either Reddit hostname because the Spoof client patch rewrites ssl.reddit.com.
    private static final Pattern PREFIX =
            Pattern.compile("^https://(?:[a-z]+\\.)?reddit\\.com/submit\\.json\\?url=");

    private FixOtherDiscussionsPatch() {
    }

    /** Injection point. */
    public static String rewriteUrl(String url) {
        try {
            if (url == null) {
                return null;
            }
            Matcher prefix = PREFIX.matcher(url);
            if (!prefix.find()) {
                return url;
            }
            String rest = url.substring(prefix.end());
            String after = null;
            int split = rest.lastIndexOf("&after=");
            if (split >= 0) {
                String tail = rest.substring(split + "&after=".length());
                int end = tail.indexOf('&');
                after = end < 0 ? tail : tail.substring(0, end);
                rest = rest.substring(0, split);
            }
            // Sync stores the link URLEncoder-encoded ("seen###<encoded>").
            String link = URLDecoder.decode(rest.trim(), "UTF-8");
            StringBuilder out = new StringBuilder("https://oauth.reddit.com/search.json?q=")
                    .append(URLEncoder.encode("url:" + link, "UTF-8"))
                    .append("&type=link&include_over_18=on&limit=25");
            if (after != null && after.matches("t3_[a-z0-9]+")) {
                out.append("&after=").append(after);
            }
            return out.toString();
        } catch (Exception ex) {
            Logger.printException(() -> "Could not rewrite the other-discussions URL", ex);
            return url;
        }
    }
}
