package app.revanced.extension.syncforreddit;

import org.json.JSONArray;
import org.json.JSONObject;

import app.revanced.extension.shared.Logger;

/**
 * Reddit now answers /api/info (and /by_id) with an HTML 403 block page, which broke
 * Sync's gallery viewer. /comments/&lt;id&gt; still works and its first element is a
 * Listing with the post, in exactly the shape /api/info used to return.
 * <p>
 * The post-id and host checks and the "leave malformed responses to Sync" rule follow the
 * equivalent fixes proposed upstream by Bryan Ulate (!6999) and 0naught (!7000).
 */
@SuppressWarnings("unused")
public final class FixGalleryPatch {
    private static final String INFO_URL = "https://oauth.reddit.com/api/info.json?id=t3_";

    /** Injection point. Rewrites a single-post API lookup to the corresponding comments listing. */
    public static String rewriteGalleryUrl(String url) {
        try {
            if (url == null || !url.startsWith(INFO_URL)) {
                return url;
            }
            String id = url.substring(INFO_URL.length());
            int end = indexOfAny(id, '?', '#');
            if (end >= 0) {
                id = id.substring(0, end);
            }
            if (!id.matches("[a-zA-Z0-9]+")) {
                // Sync handles lookups without a single post ID.
                return url;
            }
            return "https://oauth.reddit.com/comments/" + id + "?limit=1&depth=0";
        } catch (Exception ex) {
            Logger.printException(() -> "Could not rewrite the gallery URL", ex);
            return url;
        }
    }

    /**
     * Injection point.
     * Extracts the first listing from the comments response. Malformed responses and listings
     * without a post stay intact so Sync's parser can handle them.
     */
    public static String unwrapPostListing(String body) {
        try {
            if (body == null) {
                return null;
            }
            String trimmed = body.trim();
            if (!trimmed.startsWith("[")) {
                return body;
            }
            JSONObject listing = new JSONArray(trimmed).getJSONObject(0);
            listing.getJSONObject("data").getJSONArray("children").getJSONObject(0).getJSONObject("data");
            return listing.toString();
        } catch (Exception ex) {
            Logger.printException(() -> "Could not unwrap the gallery post listing", ex);
            return body;
        }
    }

    private static int indexOfAny(String s, char... chars) {
        for (int i = 0; i < s.length(); i++) {
            for (char c : chars) {
                if (s.charAt(i) == c) {
                    return i;
                }
            }
        }
        return -1;
    }
}
