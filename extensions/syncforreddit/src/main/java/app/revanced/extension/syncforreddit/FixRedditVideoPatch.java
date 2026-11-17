package app.revanced.extension.syncforreddit;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import app.revanced.extension.shared.Logger;

/**
 * Sync plays every v.redd.it video through {@code /<id>/HLSPlaylist.m3u8}. Reddit now answers 403 for
 * the HLS playlist of many new videos (signed or not), while {@code /<id>/DASHPlaylist.mpd} works for
 * old and new videos and carries the audio track. Sync's ExoPlayer includes the DASH module and its
 * source factory (ExoplayerUtils) already picks DASH for v.redd.it ".mpd" URLs.
 */
@SuppressWarnings("unused")
public final class FixRedditVideoPatch {
    private FixRedditVideoPatch() {
    }

    /** Injection point. Prefers the DASH playlist when Sync creates a Reddit video source. */
    public static String videoSource(String url) {
        try {
            String dash = dashFor(url);
            return dash == null ? url : dash;
        } catch (Exception ex) {
            Logger.printException(() -> "Could not build the Reddit video playlist URL", ex);
            return url;
        }
    }

    /** The DASH playlist for a v.redd.it HLS playlist or direct file URL, or null for anything else. */
    static String dashFor(String url) {
        if (url == null) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(url.trim().replace("&amp;", "&"));
        } catch (URISyntaxException ignored) {
            return null;
        }
        if (!"v.redd.it".equalsIgnoreCase(uri.getHost())) {
            return null;
        }
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            return null;
        }
        String[] parts = uri.getPath().split("/");
        // Only direct video and playlist URLs have a single path segment after the ID.
        if (parts.length != 3 || !parts[1].matches("[A-Za-z0-9]+")) {
            return null;
        }
        String file = parts[2].toLowerCase(Locale.ROOT);
        if (!file.endsWith(".m3u8") && !file.endsWith(".mp4")) {
            return null;
        }
        return "https://v.redd.it/" + parts[1] + "/DASHPlaylist.mpd";
    }
}
