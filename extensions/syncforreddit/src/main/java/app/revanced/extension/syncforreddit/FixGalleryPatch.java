package app.revanced.extension.syncforreddit;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Reddit now answers /api/info (and /by_id) with an HTML 403 block page, which broke
 * Sync's gallery viewer. /comments/&lt;id&gt; still works and its first element is a
 * Listing with the post, in exactly the shape /api/info used to return.
 * <p>
 * The post-id and host checks and the "leave malformed responses to Sync" rule follow the
 * equivalent fixes proposed upstream by Bryan Ulate (!6999) and 0naught (!7000).
 *
 * @noinspection unused
 */
public final class FixGalleryPatch {
    private static final String INFO_URL = "https://oauth.reddit.com/api/info.json?id=t3_";

    /** https://oauth.reddit.com/api/info.json?id=t3_ID -> https://oauth.reddit.com/comments/ID?limit=1&depth=0 */
    public static String rewriteGalleryUrl(String url) {
        if (url == null || !url.startsWith(INFO_URL)) return url;
        String id = url.substring(INFO_URL.length());
        int end = indexOfAny(id, '?', '#');
        if (end >= 0) id = id.substring(0, end);
        if (!id.matches("[a-zA-Z0-9]+")) return url;   // not a single post id: leave it to Sync
        return "https://oauth.reddit.com/comments/" + id + "?limit=1&depth=0";
    }

    /**
     * [postListing, commentListing] -> postListing (JSON string). Anything else, including a listing
     * without a post, is returned unchanged so Sync's own parser reports the error.
     */
    public static String unwrapPostListing(String body) {
        if (body == null) return null;
        String trimmed = body.trim();
        if (!trimmed.startsWith("[")) return body;
        try {
            JSONObject listing = new JSONArray(trimmed).getJSONObject(0);
            listing.getJSONObject("data").getJSONArray("children").getJSONObject(0).getJSONObject("data");
            return listing.toString();
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
