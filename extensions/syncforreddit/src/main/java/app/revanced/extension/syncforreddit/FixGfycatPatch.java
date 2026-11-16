package app.revanced.extension.syncforreddit;

import android.content.ContentValues;
import java.net.URI;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Gfycat closed in 2023. Only Reddit-hosted copies can still play. */
public final class FixGfycatPatch {
    private static URI uri(String value) {
        try { return new URI(value.replace("&amp;", "&")); }
        catch (Exception ignored) { return null; }
    }
    public static boolean isGfycat(String value) {
        URI u = uri(value);
        if (u == null || u.getHost() == null) return false;
        String host = u.getHost().toLowerCase(Locale.ROOT);
        return host.equals("gfycat.com") || host.endsWith(".gfycat.com");
    }
    public static boolean isRedditMp4(String value) {
        URI u = uri(value);
        return u != null && "https".equalsIgnoreCase(u.getScheme())
                && "v.redd.it".equalsIgnoreCase(u.getHost())
                && u.getPath() != null && u.getPath().toLowerCase(Locale.ROOT).endsWith(".mp4");
    }
    /**
     * Sync's viewer turns v.redd.it links into a stream playlist, but its id extraction keeps the ".mp4"
     * of direct DASH/CMAF file links and builds a dead URL. Returns the post's DASH playlist (video and
     * audio; HLS now 403s for many videos) for a direct v.redd.it MP4 file link, or null for anything else.
     */
    public static String redditStream(String value) {
        return isRedditMp4(value) ? FixRedditVideoPatch.dashFor(value) : null;
    }
    private static String image(String value) {
        URI u = uri(value);
        if (u == null || u.getHost() == null || isGfycat(value)
                || !("https".equalsIgnoreCase(u.getScheme()) || "http".equalsIgnoreCase(u.getScheme()))) return null;
        return value.replace("&amp;", "&");
    }
    public static void repairPost(JSONObject post, ContentValues values) {
        if (!isGfycat(post.optString("url"))) return;
        JSONObject preview = post.optJSONObject("preview");
        JSONObject video = preview == null ? null : preview.optJSONObject("reddit_video_preview");
        String mp4 = video == null ? null : video.optString("fallback_url");
        JSONArray images = preview == null ? null : preview.optJSONArray("images");
        JSONObject first = images == null ? null : images.optJSONObject(0);
        JSONObject source = first == null ? null : first.optJSONObject("source");
        String replacement = source == null ? null : image(source.optString("url"));
        boolean sourceImage = replacement != null;
        if (replacement == null) replacement = image(post.optString("thumbnail"));
        String existing = image(values.getAsString("preview_url"));
        if (existing == null) {
            if (replacement != null) {
                values.put("preview_url", replacement);
                values.put("preview_width", sourceImage ? source.optInt("width") : 0);
                values.put("preview_height", sourceImage ? source.optInt("height") : 0);
            } else {
                values.remove("preview_url");
                values.remove("preview_width");
                values.remove("preview_height");
            }
        }
        if (image(values.getAsString("thumbnail")) == null) {
            if (replacement == null) values.remove("thumbnail");
            else values.put("thumbnail", replacement);
        }
        if (isRedditMp4(mp4)) {
            mp4 = mp4.replace("&amp;", "&");
            values.put("url", mp4);
            values.put("video_preview", mp4);
            values.put("sync_type", 4);
        } else {
            values.put("url", post.optString("url").replace("&amp;", "&"));
            values.remove("video_preview");
            values.put("sync_type", image(values.getAsString("preview_url")) != null ? 9 : 3);
        }
    }
}
