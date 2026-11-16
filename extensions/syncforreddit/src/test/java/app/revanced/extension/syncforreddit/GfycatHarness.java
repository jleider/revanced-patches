package app.revanced.extension.syncforreddit;

import android.content.ContentValues;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.json.JSONObject;

/** Plain javac: compile with desktop org.json, the test ContentValues, and FixGfycatPatch. */
public final class GfycatHarness {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static ContentValues values() {
        ContentValues v = new ContentValues();
        v.put("url", "https://gfycat.com/Example");
        v.put("raw_url", "https://gfycat.com/Example");
        v.put("permalink", "/r/gifs/comments/example/");
        v.put("preview_url", "https://thumbs.gfycat.com/Example-poster.jpg");
        v.put("thumbnail", "https://thumbs.gfycat.com/Example-small.gif");
        return v;
    }
    public static void main(String[] args) throws Exception {
        String[] ids = {"89oo9p", "do8t38", "7s6y7w"};
        String[] mp4s = {"https://v.redd.it/0hp59oq3mpr81/DASH_480.mp4", "https://v.redd.it/12qpe9hlnsw91/DASH_240.mp4", null};
        for (int i = 0; i < ids.length; i++) {
            JSONObject post = new JSONObject(Files.readString(Paths.get(args[0], "gfycat_" + ids[i] + ".json")));
            ContentValues v = values();
            FixGfycatPatch.repairPost(post, v);
            check("https://gfycat.com/Example".equals(v.getAsString("raw_url")), "raw URL preserved");
            check("/r/gifs/comments/example/".equals(v.getAsString("permalink")), "permalink preserved");
            check(!FixGfycatPatch.isGfycat(v.getAsString("preview_url")), "preview not Gfycat");
            check(!FixGfycatPatch.isGfycat(v.getAsString("thumbnail")), "thumbnail not Gfycat");
            if (mp4s[i] != null) {
                check(mp4s[i].equals(v.getAsString("url")), "fixture MP4 " + ids[i]);
                check(mp4s[i].equals(v.getAsString("video_preview")), "autoplay MP4");
                check("4".equals(v.getAsString("sync_type")), "video type");
            } else {
                check(v.getAsString("video_preview") == null, "no dead autoplay");
                check("9".equals(v.getAsString("sync_type")), "link with Reddit preview");
            }
        }
        check(FixGfycatPatch.isGfycat("https://api.gfycat.com/v1/gfycats/X"), "API blocked");
        check(FixGfycatPatch.isGfycat("https://www.GFYCAT.com/X"), "page blocked case insensitive");
        check(!FixGfycatPatch.isGfycat("https://gfycat.com.evil.test/X"), "host boundary");
        check(!FixGfycatPatch.isGfycat("https://redgifs.com/watch/X"), "Redgifs unchanged");
        check(!FixGfycatPatch.isRedditMp4("http://v.redd.it/x/DASH_480.mp4"), "HTTPS only");
        check(!FixGfycatPatch.isRedditMp4("https://v.redd.it.evil/x.mp4"), "exact Reddit host");
        check(FixGfycatPatch.isRedditMp4("https://v.redd.it/x/DASH_480.mp4?a=1&amp;b=2"), "query supported");
        check("https://v.redd.it/0hp59oq3mpr81/DASHPlaylist.mpd".equals(FixGfycatPatch.redditStream("https://v.redd.it/0hp59oq3mpr81/DASH_480.mp4?source=fallback")), "DASH mp4 -> DASH playlist (keeps audio)");
        check("https://v.redd.it/abc123/DASHPlaylist.mpd".equals(FixGfycatPatch.redditStream("https://v.redd.it/abc123/CMAF_720.mp4")), "CMAF mp4 -> DASH playlist");
        check(FixGfycatPatch.redditStream("https://v.redd.it/abc123/HLSPlaylist.m3u8") == null, "playlist link left to Sync");
        check(FixGfycatPatch.redditStream("https://v.redd.it/abc123") == null, "bare id left to Sync");
        check(FixGfycatPatch.redditStream("https://v.redd.it/a/b/DASH_480.mp4") == null, "unexpected path depth rejected");
        check(FixGfycatPatch.redditStream("https://v.redd.it.evil/abc/DASH_480.mp4") == null, "foreign host rejected");
        JSONObject post = new JSONObject("{\"url\":\"https://gfycat.com/X\",\"preview\":{\"reddit_video_preview\":{\"fallback_url\":\"https://v.redd.it/x/DASH_480.mp4?a=1&amp;b=2\"}}}");
        ContentValues v = values();
        FixGfycatPatch.repairPost(post, v);
        check(v.getAsString("url").endsWith("?a=1&b=2"), "HTML unescaped");
        post = new JSONObject("{\"url\":\"https://gfycat.com/X\",\"thumbnail\":\"self\"}");
        v = values();
        FixGfycatPatch.repairPost(post, v);
        check("3".equals(v.getAsString("sync_type")), "no usable preview plain link");
        check(v.getAsString("preview_url") == null, "dead preview removed");
        post.put("url", "https://redgifs.com/watch/X");
        v = values();
        FixGfycatPatch.repairPost(post, v);
        check("https://gfycat.com/Example".equals(v.getAsString("url")), "non-Gfycat untouched");
        System.out.println("PASS: " + checks + " Gfycat checks");
    }
}
