package app.revanced.extension.syncforreddit;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;

/**
 * Sync opened Tumblr posts through api.tumblr.com/v2 with a built-in API key that Tumblr revoked.
 * Instead, the blog's keyless v1 endpoint ({@code <blog>.tumblr.com/api/read/json?id=<post>}) is
 * read and its images are handed to TumblrRequest's parser in the v2 shape it expects, so they open in
 * Sync's normal image viewer. A post without images opens in the browser.
 */
@SuppressWarnings("unused")
public final class FixTumblrPatch {
    private static final String MARKER = "revanced_tumblr=1";
    private static final Pattern BLOG = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final Pattern ID = Pattern.compile("\\d{1,20}");
    /** One media.tumblr.com image URL; group 1 identifies the image across its sizes. */
    private static final Pattern MEDIA = Pattern.compile(
            "https://(?:\\d+\\.)?media\\.tumblr\\.com/([0-9a-f]{32}/[0-9a-f]{16}-[0-9a-z]{2})/s(\\d+)x(\\d+)[^\\s\"'<>\\\\,]*");

    private static final class PostReference {
        final String blog;
        final String id;

        PostReference(String blog, String id) {
            this.blog = blog;
            this.id = id;
        }
    }

    private static final class ImageSizes {
        final String largestUrl;
        final long largestSize;
        final String smallestUrl;
        final long smallestSize;

        ImageSizes(String largestUrl, long largestSize, String smallestUrl, long smallestSize) {
            this.largestUrl = largestUrl;
            this.largestSize = largestSize;
            this.smallestUrl = smallestUrl;
            this.smallestSize = smallestSize;
        }
    }

    private FixTumblrPatch() {
    }

    /** Injection point. Uses the keyless endpoint for supported Tumblr post URLs. */
    public static String apiUrl(String postUrl) {
        try {
            PostReference post = parsePostReference(postUrl);
            if (post == null) {
                return postUrl;
            }
            return "https://" + post.blog + ".tumblr.com/api/read/json?id=" + post.id + "&" + MARKER;
        } catch (Exception ex) {
            Logger.printException(() -> "Could not build the Tumblr API URL", ex);
            return postUrl;
        }
    }

    /** Extracts the blog and post ID from either Tumblr permalink format. */
    private static PostReference parsePostReference(String url) {
        if (url == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("(?i)^https?://([^/?#]+)/([^?#]*)").matcher(url.trim());
        if (!matcher.find()) {
            return null;
        }
        String host = matcher.group(1).toLowerCase(Locale.ROOT);
        String[] segments = matcher.group(2).split("/");
        String blog = null;
        String id = null;
        if (host.equals("tumblr.com") || host.equals("www.tumblr.com")) {
            if (segments.length >= 2 && !segments[0].equals("blog")) {
                blog = segments[0];
                id = segments[1];
            } else if (segments.length >= 4 && segments[0].equals("blog") && segments[1].equals("view")) {
                blog = segments[2];
                id = segments[3];
            }
        } else if (host.endsWith(".tumblr.com")) {
            blog = host.substring(0, host.length() - ".tumblr.com".length());
            for (int i = 0; i + 1 < segments.length; i++) {
                if (segments[i].equals("post")) {
                    id = segments[i + 1];
                    break;
                }
            }
        }
        if (blog == null || id == null || !BLOG.matcher(blog).matches() || !ID.matcher(id).matches()) {
            return null;
        }
        return new PostReference(blog, id);
    }

    /** Injection point. Adapts the post's photos to the v2 response that Sync expects. */
    public static String convert(String body, String requestUrl) throws JSONException {
        try {
            if (requestUrl == null || !requestUrl.contains(MARKER)) {
                return body;
            }
            List<ImageSizes> images = images(body);
            if (images.isEmpty()) {
                openInBrowser(requestUrl);
                throw new JSONException("Tumblr post has no images");
            }
            JSONArray photos = new JSONArray();
            for (ImageSizes image : images) {
                photos.put(new JSONObject()
                        .put("original_size", new JSONObject().put("url", image.largestUrl))
                        .put("alt_sizes", new JSONArray().put(new JSONObject().put("url", image.smallestUrl))));
            }
            return new JSONObject().put("response", new JSONObject().put("posts",
                    new JSONArray().put(new JSONObject().put("photos", photos)))).toString();
        } catch (Exception ex) {
            Logger.printException(() -> "Could not convert the Tumblr response", ex);
            // Sync's Volley parser reports this as a parse error.
            throw ex;
        }
    }

    /** Finds the largest and smallest variants of each post image in order. */
    private static List<ImageSizes> images(String body) throws JSONException {
        String json = body.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new JSONException("Not a Tumblr v1 response");
        }
        JSONArray posts = new JSONObject(json.substring(start, end + 1)).optJSONArray("posts");
        JSONObject post = posts != null && posts.length() > 0 ? posts.getJSONObject(0) : null;
        if (post == null) {
            return new ArrayList<>();
        }
        List<ImageSizes> images = new ArrayList<>();
        // Legacy photo posts and photosets carry their sizes as photo-url-<width> fields.
        JSONArray photos = post.optJSONArray("photos");
        if (photos != null && photos.length() > 0) {
            for (int i = 0; i < photos.length(); i++) {
                ImageSizes sizes = legacySizes(photos.optJSONObject(i));
                if (sizes != null) {
                    images.add(sizes);
                }
            }
        } else {
            ImageSizes sizes = legacySizes(post);
            if (sizes != null) {
                images.add(sizes);
            }
        }
        if (!images.isEmpty()) {
            return images;
        }
        // Neue Post Format posts carry image variants in HTML srcset attributes.
        StringBuilder text = new StringBuilder();
        for (String field : new String[]{"regular-body", "photo-caption", "answer", "link-description", "quote-text"}) {
            text.append(post.optString(field)).append('\n');
        }
        Map<String, ImageSizes> sizesById = new LinkedHashMap<>();
        Matcher matcher = MEDIA.matcher(text.toString().replace("\\/", "/"));
        while (matcher.find()) {
            String url = matcher.group(0);
            // Tumblr uses this crop for square avatars.
            if (url.contains("u_c1")) {
                continue;
            }
            long area = Long.parseLong(matcher.group(2)) * Long.parseLong(matcher.group(3));
            String imageId = matcher.group(1);
            ImageSizes sizes = sizesById.get(imageId);
            if (sizes == null) {
                sizesById.put(imageId, new ImageSizes(url, area, url, area));
                continue;
            }
            boolean larger = area > sizes.largestSize;
            boolean smaller = area < sizes.smallestSize;
            if (larger || smaller) {
                sizesById.put(imageId, new ImageSizes(
                        larger ? url : sizes.largestUrl, larger ? area : sizes.largestSize,
                        smaller ? url : sizes.smallestUrl, smaller ? area : sizes.smallestSize));
            }
        }
        return new ArrayList<>(sizesById.values());
    }

    /** Uses the width encoded in the legacy photo-url fields to rank variants. */
    private static ImageSizes legacySizes(JSONObject photo) {
        if (photo == null) {
            return null;
        }
        String largest = null;
        String smallest = null;
        int largestWidth = -1;
        int smallestWidth = Integer.MAX_VALUE;
        Iterator<String> keys = photo.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!key.matches("photo-url-\\d+")) {
                continue;
            }
            String url = photo.optString(key);
            if (!url.startsWith("https://") && !url.startsWith("http://")) {
                continue;
            }
            int width = Integer.parseInt(key.substring("photo-url-".length()));
            if (width > largestWidth) {
                largestWidth = width;
                largest = url;
            }
            if (width < smallestWidth) {
                smallestWidth = width;
                smallest = url;
            }
        }
        return largest == null ? null : new ImageSizes(largest, largestWidth, smallest, smallestWidth);
    }

    private static void openInBrowser(String requestUrl) {
        try {
            Matcher matcher = Pattern.compile("^https://([^.]+)\\.tumblr\\.com/api/read/json\\?id=(\\d+)").matcher(requestUrl);
            if (!matcher.find()) {
                return;
            }
            Context context = Utils.getContext();
            if (context == null) {
                return;
            }
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tumblr.com/" + matcher.group(1) + "/" + matcher.group(2)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ex) {
            // Sync still reports the parse error when the browser cannot be opened.
            Logger.printException(() -> "Could not open the Tumblr post in a browser", ex);
        }
    }
}
