package app.revanced.extension.syncforreddit;

import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * "Other discussions" used the logged-out https://ssl.reddit.com/submit.json?url=<url>, which now
 * 301s to www.reddit.com (an HTML block page). OAuth search with url:<url> returns the posts of
 * that link as a single Listing, the shape OAuthPostsRequest already parses, and keeps the token.
 *
 * @noinspection unused
 */
public final class FixOtherDiscussionsPatch {
    private static final String PREFIX = "https://ssl.reddit.com/submit.json?url=";

    private FixOtherDiscussionsPatch() {}

    public static String rewriteUrl(String url) {
        if (url == null || !url.startsWith(PREFIX)) return url;
        try {
            String rest = url.substring(PREFIX.length());
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
            if (after != null && after.matches("t3_[a-z0-9]+")) out.append("&after=").append(after);
            return out.toString();
        } catch (Exception e) {
            return url;
        }
    }
}
