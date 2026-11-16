package app.revanced.extension.syncforreddit;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local metadata for single images; albums optionally use Imgur's own API. */
public final class FixImgurPatch {
    private static final String LOCAL = "https://revanced.invalid/imgur/";
    private static final Pattern ID = Pattern.compile("^([A-Za-z0-9]+)");

    private FixImgurPatch() {}

    // Replaced with the optional patch option at patch time.
    public static String getClientId() {
        return "";
    }

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
        return getClientId().isEmpty() ? LOCAL + "album/" + id(source)
                : "https://api.imgur.com/3/album/" + id(source);
    }

    public static Map<String, String> albumHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "sync_for_reddit");
        if (!getClientId().isEmpty()) {
            headers.put("Authorization", "Client-ID " + getClientId());
        }
        return headers;
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
