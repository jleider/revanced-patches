package app.revanced.extension.syncforreddit;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sync opened Tumblr posts through api.tumblr.com/v2 with a built-in API key that Tumblr revoked.
 * Instead, the blog's keyless v1 endpoint ({@code <blog>.tumblr.com/api/read/json?id=<post>}) is
 * read and its images are handed to TumblrRequest's parser in the v2 shape it expects, so they open in
 * Sync's normal image viewer. A post without images opens in the browser.
 *
 * @noinspection unused
 */
public final class FixTumblrPatch {
    private static final String MARKER = "revanced_tumblr=1";
    private static final Pattern BLOG = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final Pattern ID = Pattern.compile("\\d{1,20}");
    /** One media.tumblr.com image URL; group 1 identifies the image across its sizes. */
    private static final Pattern MEDIA = Pattern.compile(
            "https://(?:\\d+\\.)?media\\.tumblr\\.com/([0-9a-f]{32}/[0-9a-f]{16}-[0-9a-z]{2})/s(\\d+)x(\\d+)[^\\s\"'<>\\\\,]*");

    private FixTumblrPatch() {}

    /** Replaces TumblrRequest's v2 URL builder: the keyless v1 URL for the post, or the post itself. */
    public static String apiUrl(String postUrl) {
        String[] blogAndId = blogAndId(postUrl);
        if (blogAndId == null) return postUrl;
        return "https://" + blogAndId[0] + ".tumblr.com/api/read/json?id=" + blogAndId[1] + "&" + MARKER;
    }

    /** {blog, postId} of blog.tumblr.com/post/ID/… or www.tumblr.com/blog/ID/…, or null. */
    static String[] blogAndId(String url) {
        if (url == null) return null;
        Matcher m = Pattern.compile("(?i)^https?://([^/?#]+)/([^?#]*)").matcher(url.trim());
        if (!m.find()) return null;
        String host = m.group(1).toLowerCase(Locale.ROOT);
        String[] seg = m.group(2).split("/");
        String blog = null, id = null;
        if (host.equals("tumblr.com") || host.equals("www.tumblr.com")) {
            if (seg.length >= 2 && !seg[0].equals("blog")) { blog = seg[0]; id = seg[1]; }
            else if (seg.length >= 4 && seg[0].equals("blog") && seg[1].equals("view")) { blog = seg[2]; id = seg[3]; }
        } else if (host.endsWith(".tumblr.com")) {
            blog = host.substring(0, host.length() - ".tumblr.com".length());
            for (int i = 0; i + 1 < seg.length; i++) if (seg[i].equals("post")) { id = seg[i + 1]; break; }
        }
        if (blog == null || id == null || !BLOG.matcher(blog).matches() || !ID.matcher(id).matches()) return null;
        return new String[]{blog, id};
    }

    /** TumblrRequest's response body -> v2 JSON with the post's photos. Throws when there are none. */
    public static String convert(String body, String requestUrl) throws JSONException {
        if (requestUrl == null || !requestUrl.contains(MARKER)) return body;
        List<String[]> images = images(body);
        if (images.isEmpty()) {
            openInBrowser(requestUrl);
            throw new JSONException("Tumblr post has no images");
        }
        JSONArray photos = new JSONArray();
        for (String[] img : images) {
            photos.put(new JSONObject()
                    .put("original_size", new JSONObject().put("url", img[0]))
                    .put("alt_sizes", new JSONArray().put(new JSONObject().put("url", img[1]))));
        }
        return new JSONObject().put("response", new JSONObject().put("posts",
                new JSONArray().put(new JSONObject().put("photos", photos)))).toString();
    }

    /** {largest, smallest} URL per image of the post, in order. Avatars and blog chrome are excluded. */
    static List<String[]> images(String body) throws JSONException {
        String json = body.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end < start) throw new JSONException("Not a Tumblr v1 response");
        JSONArray posts = new JSONObject(json.substring(start, end + 1)).optJSONArray("posts");
        JSONObject post = posts != null && posts.length() > 0 ? posts.getJSONObject(0) : null;
        if (post == null) return new ArrayList<>();
        StringBuilder text = new StringBuilder();
        JSONArray photos = post.optJSONArray("photos");
        if (photos != null && photos.length() > 0) {
            for (int i = 0; i < photos.length(); i++) text.append(photos.getJSONObject(i).toString()).append('\n');
        } else {
            text.append(post.optString("photo-url-1280")).append('\n');
        }
        for (String field : new String[]{"regular-body", "photo-caption", "answer", "link-description", "quote-text"}) {
            text.append(post.optString(field)).append('\n');
        }
        // id -> {largest url, largest area, smallest url, smallest area}
        Map<String, Object[]> byImage = new LinkedHashMap<>();
        Matcher m = MEDIA.matcher(text.toString().replace("\\/", "/"));
        while (m.find()) {
            String url = m.group(0);
            if (url.contains("u_c1")) continue;   // square avatar crops
            long area = Long.parseLong(m.group(2)) * Long.parseLong(m.group(3));
            Object[] e = byImage.get(m.group(1));
            if (e == null) byImage.put(m.group(1), new Object[]{url, area, url, area});
            else {
                if (area > (Long) e[1]) { e[0] = url; e[1] = area; }
                if (area < (Long) e[3]) { e[2] = url; e[3] = area; }
            }
        }
        List<String[]> out = new ArrayList<>();
        for (Object[] e : byImage.values()) out.add(new String[]{(String) e[0], (String) e[2]});
        return out;
    }

    private static void openInBrowser(String requestUrl) {
        try {
            Matcher m = Pattern.compile("^https://([^.]+)\\.tumblr\\.com/api/read/json\\?id=(\\d+)").matcher(requestUrl);
            if (!m.find()) return;
            Context c = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if (c == null) return;
            c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tumblr.com/" + m.group(1) + "/" + m.group(2)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ignored) {
            // no browser or no context: Sync shows its own error
        }
    }
}
