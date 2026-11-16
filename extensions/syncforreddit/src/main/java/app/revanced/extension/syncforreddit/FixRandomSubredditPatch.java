package app.revanced.extension.syncforreddit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

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
}
