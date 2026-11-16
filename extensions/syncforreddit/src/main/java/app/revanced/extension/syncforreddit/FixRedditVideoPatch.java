package app.revanced.extension.syncforreddit;

import java.net.URI;
import java.util.Locale;

/**
 * Sync plays every v.redd.it video through {@code /<id>/HLSPlaylist.m3u8}. Reddit now answers 403 for
 * the HLS playlist of many new videos (signed or not), while {@code /<id>/DASHPlaylist.mpd} works for
 * old and new videos and carries the audio track. Sync's ExoPlayer includes the DASH module and its
 * source factory (ExoplayerUtils) already picks DASH for v.redd.it ".mpd" URLs.
 *
 * @noinspection unused
 */
public final class FixRedditVideoPatch {
    private FixRedditVideoPatch() {}

    /** Called first in ExoplayerUtils' source factory: HLS v.redd.it playlist -> DASH playlist. */
    public static String videoSource(String url) {
        String dash = dashFor(url);
        return dash == null ? url : dash;
    }

    /** The DASH playlist for a v.redd.it HLS playlist or direct file URL, or null for anything else. */
    static String dashFor(String url) {
        if (url == null) return null;
        URI u;
        try { u = new URI(url.trim().replace("&amp;", "&")); } catch (Exception e) { return null; }
        if (u.getHost() == null || !"v.redd.it".equalsIgnoreCase(u.getHost())) return null;
        String scheme = u.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) return null;
        String[] parts = u.getPath() == null ? new String[0] : u.getPath().split("/");
        // ["", "<id>", "HLSPlaylist.m3u8" | "DASH_720.mp4" | "CMAF_480.mp4" | ...]
        if (parts.length != 3 || !parts[1].matches("[A-Za-z0-9]+")) return null;
        String file = parts[2].toLowerCase(Locale.ROOT);
        if (!file.endsWith(".m3u8") && !file.endsWith(".mp4")) return null;
        return "https://v.redd.it/" + parts[1] + "/DASHPlaylist.mpd";
    }
}
