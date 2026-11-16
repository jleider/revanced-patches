package app.revanced.extension.syncforreddit;

import org.json.JSONArray;

/**
 * Reddit now answers /api/info (and /by_id) with an HTML 403 block page, which broke
 * Sync's gallery viewer. /comments/&lt;id&gt; still works and its first element is a
 * Listing with the post, in exactly the shape /api/info used to return.
 *
 * @noinspection unused
 */
public final class FixGalleryPatch {
    private static final String INFO_PREFIX = "/api/info.json?id=t3_";

    /** https://oauth.reddit.com/api/info.json?id=t3_ID -> https://oauth.reddit.com/comments/ID?limit=1&depth=0 */
    public static String rewriteGalleryUrl(String url) {
        if (url == null) return null;
        int i = url.indexOf(INFO_PREFIX);
        if (i < 0) return url;
        String id = url.substring(i + INFO_PREFIX.length());
        int end = indexOfAny(id, '?', '&', '/', '#');
        if (end >= 0) id = id.substring(0, end);
        return url.substring(0, i) + "/comments/" + id + "?limit=1&depth=0";
    }

    /** [postListing, commentListing] -> postListing (JSON string); anything else is returned unchanged. */
    public static String unwrapPostListing(String body) {
        if (body == null) return null;
        String trimmed = body.trim();
        if (!trimmed.startsWith("[")) return body;
        try {
            return new JSONArray(trimmed).getJSONObject(0).toString();
        } catch (Exception e) {
            return body;
        }
    }

    private static int indexOfAny(String s, char... chars) {
        for (int i = 0; i < s.length(); i++) {
            for (char c : chars) if (s.charAt(i) == c) return i;
        }
        return -1;
    }
}
