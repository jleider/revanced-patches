package app.revanced.extension.syncforreddit;

import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reddit removed r/random and r/randnsfw (404). Fetch a pool of subreddits instead and hand
 * RandomRequest's parser the {"data":{"children":[{"data":{"subreddit":...}}]}} it expects.
 *
 * @noinspection unused
 */
public final class FixRandomSubredditPatch {
    private static final String SFW = "https://oauth.reddit.com/subreddits/popular.json?limit=100&revanced_random=sfw";
    private static final String NSFW =
            "https://oauth.reddit.com/subreddits/search.json?q=nsfw&include_over_18=on&limit=100&revanced_random=nsfw";
    private static final Random RANDOM = new Random();
    /** A link to r/random, r/randnsfw or Sync's own r/random_nsfw; group 1 is the name. */
    private static final Pattern RANDOM_LINK = Pattern.compile(
            "(?i)^(?:https?://(?:[a-z0-9-]+\\.)?reddit\\.com)?/?r/(random|randnsfw|random_nsfw)/?(?:[?#].*)?$");

    private FixRandomSubredditPatch() {}

    public static String rewriteUrl(String url) {
        if (url == null || !url.contains("reddit.com/")) return url;
        String path = url.split("\\?", 2)[0];
        if (path.endsWith("/r/random.json")) return SFW;
        if (path.endsWith("/r/randnsfw.json")) return NSFW;
        return url;
    }

    /** Throws on an empty pool so the existing parser reports a ParseError. */
    public static String convert(String body, String url) throws JSONException {
        if (url == null || !url.contains("revanced_random=")) return body;
        boolean nsfw = url.contains("revanced_random=nsfw");
        JSONArray children = new JSONObject(body).getJSONObject("data").getJSONArray("children");
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (int i = 0; i < children.length(); i++) {
            JSONObject data = children.getJSONObject(i).optJSONObject("data");
            if (data == null || !data.has("over18")) continue;
            String name = data.optString("display_name");
            if (!name.matches("[A-Za-z0-9_]{2,21}")) continue;
            if (data.optBoolean("over18") == nsfw) names.add(name);
        }
        if (names.isEmpty()) throw new JSONException("No random subreddit candidates");
        List<String> pool = new ArrayList<>(names);
        String pick = pool.get(RANDOM.nextInt(pool.size()));
        return new JSONObject().put("data", new JSONObject().put("children",
                new JSONArray().put(new JSONObject().put("data",
                        new JSONObject().put("subreddit", pick))))).toString();
    }

    /** The random subreddit a link points to ("random" or "random_nsfw"), or null. */
    static String randomName(String link) {
        if (link == null) return null;
        Matcher m = RANDOM_LINK.matcher(link.trim());
        if (!m.find()) return null;
        return m.group(1).equalsIgnoreCase("random") ? "random" : "random_nsfw";
    }

    /**
     * MainActivity opens a subreddit link given to it (an external reddit.com/r/... link) as a plain
     * listing, which 404s for r/random. Reporting "not a subreddit link" sends it through Sync's link
     * handler instead, which shows the Random sheet.
     */
    public static boolean isListableSubredditLink(boolean isSubredditLink, String link) {
        return isSubredditLink && randomName(link) == null;
    }

    /** LinkHandler's subreddit name: Reddit's r/randnsfw is Sync's random_nsfw. */
    public static String normalizeSubreddit(String name) {
        return "randnsfw".equalsIgnoreCase(name) ? "random_nsfw" : name;
    }

    /**
     * The Random sheet needs one of Sync's activities. A random link arriving with another context
     * (IntentActivity when Sync was already running) is handed to MainActivity, which shows it.
     * True = handled here.
     */
    public static boolean openRandomInSync(Context context, String link) {
        if (context == null || randomName(link) == null) return false;
        try {
            Class<?> base = Class.forName("com.laurencedawson.reddit_sync.ui.activities.BaseActivity");
            if (base.isInstance(context)) return false;
            // MainActivity, not HomeActivity: HomeActivity roots Sync's task, so starting it again only
            // brings the task to the front and the link is dropped.
            Intent intent = new Intent().setClassName(context, "com.laurencedawson.reddit_sync.ui.activities.MainActivity")
                    .putExtra("link", link);
            if (!(context instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
}
