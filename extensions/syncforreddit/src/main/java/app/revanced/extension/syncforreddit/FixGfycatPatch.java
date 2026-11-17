package app.revanced.extension.syncforreddit;

import android.content.ContentValues;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import app.revanced.extension.shared.Logger;

/** Gfycat closed in 2023. Only Reddit-hosted copies can still play. */
@SuppressWarnings("unused")
public final class FixGfycatPatch {
    private static URI uri(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new URI(value.replace("&amp;", "&"));
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    /** Injection point. */
    public static boolean isGfycat(String value) {
        try {
            URI url = uri(value);
            return url != null && isGfycatHost(url.getHost());
        } catch (Exception ex) {
            Logger.printException(() -> "Could not check the Gfycat URL", ex);
            return false;
        }
    }

    /** Injection point. */
    public static boolean isRedditMp4(String value) {
        try {
            URI url = uri(value);
            return url != null && "https".equalsIgnoreCase(url.getScheme())
                    && "v.redd.it".equalsIgnoreCase(url.getHost())
                    && url.getPath().toLowerCase(Locale.ROOT).endsWith(".mp4");
        } catch (Exception ex) {
            Logger.printException(() -> "Could not check the Reddit video URL", ex);
            return false;
        }
    }

    /**
     * Injection point.
     * Sync's viewer turns v.redd.it links into a stream playlist, but its id extraction keeps the ".mp4"
     * of direct DASH/CMAF file links and builds a dead URL. Returns the post's DASH playlist (video and
     * audio; HLS now 403s for many videos) for a direct v.redd.it MP4 file link, or null for anything else.
     */
    public static String redditStream(String value) {
        try {
            return isRedditMp4(value) ? FixRedditVideoPatch.dashFor(value) : null;
        } catch (Exception ex) {
            Logger.printException(() -> "Could not build the Reddit stream URL", ex);
            return null;
        }
    }

    private static boolean isGfycatHost(String host) {
        if (host == null) {
            return false;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return normalizedHost.equals("gfycat.com") || normalizedHost.endsWith(".gfycat.com");
    }

    private static String image(String value) {
        URI url = uri(value);
        if (url == null || url.getHost() == null || isGfycatHost(url.getHost())
                || !("https".equalsIgnoreCase(url.getScheme()) || "http".equalsIgnoreCase(url.getScheme()))) {
            return null;
        }
        return value.replace("&amp;", "&");
    }

    /** Injection point. */
    public static void repairPost(JSONObject post, ContentValues values) {
        try {
            if (!isGfycat(post.optString("url"))) {
                return;
            }
            JSONObject preview = post.optJSONObject("preview");
            JSONObject video = preview == null ? null : preview.optJSONObject("reddit_video_preview");
            String mp4 = video == null ? null : video.optString("fallback_url");
            JSONArray images = preview == null ? null : preview.optJSONArray("images");
            JSONObject first = images == null ? null : images.optJSONObject(0);
            JSONObject source = first == null ? null : first.optJSONObject("source");
            String replacement = source == null ? null : image(source.optString("url"));
            boolean sourceImage = replacement != null;
            if (replacement == null) {
                replacement = image(post.optString("thumbnail"));
            }
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
                if (replacement == null) {
                    values.remove("thumbnail");
                } else {
                    values.put("thumbnail", replacement);
                }
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
        } catch (Exception ex) {
            Logger.printException(() -> "Could not repair the Gfycat post", ex);
        }
    }
}
