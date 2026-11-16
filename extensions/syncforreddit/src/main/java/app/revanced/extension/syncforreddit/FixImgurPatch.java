package app.revanced.extension.syncforreddit;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Sync's Imgur proxy (images.syncforreddit.com) is gone. Single images need no lookup: their direct
 * i.imgur.com URL is built locally. Albums load Imgur's public embed page, whose inline
 * {@code var images = {...}} JSON lists every image; it is converted to the Imgur API shape Sync's
 * album parser already reads. No API key, no Imgur API.
 */
public final class FixImgurPatch {
    private static final String LOCAL = "https://revanced.invalid/imgur/";
    private static final Pattern ID = Pattern.compile("^([A-Za-z0-9]+)");

    private FixImgurPatch() {}

    private static final Pattern EMBED_IMAGES = Pattern.compile("\\bimages\\s*=\\s*(\\{\\s*\"count\")");

    private static String id(String source) {
        String path = source.split("[?#\\s]", 2)[0];
        String[] parts = path.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            String part = parts[i];
            if (part.isEmpty() || part.equals("new") || part.equals("all")
                    || part.equals("embed") || part.equals("noscript")
                    || part.equals("horizontal") || part.equals("layout") || part.equals("blog")) continue;
            // Current share URLs append a title slug: /a/funny-cat-pics-AbC12d, /gallery/title-AbC12d.
            int dash = part.lastIndexOf('-');
            if (dash >= 0) part = part.substring(dash + 1);
            Matcher match = ID.matcher(part);
            return match.find() ? match.group(1) : "";
        }
        return "";
    }

    public static String singleUrl(String source) {
        String path = source.split("[?#\\s]", 2)[0].toLowerCase(Locale.ROOT);
        boolean animated = path.endsWith(".gifv") || path.endsWith(".gif")
                || path.endsWith(".mp4") || path.endsWith(".webm");
        return LOCAL + "image/" + id(source) + (animated ? ".mp4" : ".jpg");
    }

    public static String albumUrl(String source) {
        String id = id(source);
        return id.isEmpty() ? LOCAL + "album/" : "https://imgur.com/a/" + id + "/embed?pub=true";
    }

    public static Map<String, String> albumHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "sync_for_reddit");
        return headers;
    }

    /**
     * Called first in ImgurGalleryRequest.parseNetworkResponse. Converts an Imgur embed page into
     * {"data":{"images":[{link, mp4?, width, height, title, description}]}}; returns null to leave the
     * body alone (Sync's parser then reports its usual error and opens the album in the browser).
     */
    public static byte[] albumJson(byte[] body) {
        if (body == null) return null;
        try {
            String page = new String(body, StandardCharsets.UTF_8);
            Matcher m = EMBED_IMAGES.matcher(page);
            if (!m.find()) return null;
            JSONObject embed = (JSONObject) new JSONTokener(page.substring(m.start(1))).nextValue();
            JSONArray in = embed.optJSONArray("images");
            if (in == null || in.length() == 0) return null;
            JSONArray out = new JSONArray();
            for (int i = 0; i < in.length(); i++) {
                JSONObject item = in.optJSONObject(i);
                if (item == null) continue;
                String hash = item.optString("hash");
                if (!hash.matches("[A-Za-z0-9]+")) continue;
                String ext = item.optString("ext", ".jpg").toLowerCase(Locale.ROOT);
                if (!ext.matches("\\.[a-z0-9]+")) ext = ".jpg";
                boolean video = item.optBoolean("animated") || item.optBoolean("prefer_video")
                        || ext.equals(".mp4") || ext.equals(".gif") || ext.equals(".gifv") || ext.equals(".webm");
                JSONObject image = new JSONObject();
                image.put("link", "https://i.imgur.com/" + hash + (video ? ".mp4" : ext));
                if (video) image.put("mp4", "https://i.imgur.com/" + hash + ".mp4");
                image.put("width", item.optInt("width"));
                image.put("height", item.optInt("height"));
                image.put("title", item.isNull("title") ? JSONObject.NULL : item.optString("title"));
                image.put("description", item.isNull("description") ? JSONObject.NULL : item.optString("description"));
                out.put(image);
            }
            if (out.length() == 0) return null;
            return new JSONObject().put("data", new JSONObject().put("images", out))
                    .toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** null lets Volley proceed normally; local URLs must never enter the network queue. */
    public static String localResponse(String url) {
        if (!url.startsWith(LOCAL)) return null;
        // The existing album parser turns this into a ParseError, delivered normally by Volley.
        if (!url.startsWith(LOCAL + "image/")) return "{}";
        String file = url.substring((LOCAL + "image/").length());
        if (!file.matches("[A-Za-z0-9]+\\.(jpg|mp4)")) return "{}";
        String link = "https://i.imgur.com/" + file;
        // SingleImageRequest only reads link, so animated media must also use MP4 there.
        return "{\"data\":{\"link\":\"" + link + "\",\"title\":null,\"description\":null"
                + (file.endsWith(".mp4") ? ",\"mp4\":\"" + link + "\"" : "") + "}}";
    }
}
