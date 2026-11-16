package app.revanced.extension.syncforreddit;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import org.json.JSONArray;
import org.json.JSONObject;

/** Run with a desktop org.json jar and the fixtures directory as argument. */
public final class MoreCommentsHarness {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static JSONArray things(String body, String encoded) throws Exception {
        return new JSONObject(FixMoreCommentsPatch.convertListing(body, encoded))
                .getJSONObject("json").getJSONObject("data").getJSONArray("things");
    }
    private static JSONObject comment(String id, String parent, JSONArray replies) throws Exception {
        return new JSONObject().put("kind", "t1").put("data", new JSONObject()
                .put("id", id).put("parent_id", parent).put("link_id", "t3_post")
                .put("replies", new JSONObject().put("data", new JSONObject().put("children", replies))));
    }
    private static String listing(JSONArray roots) throws Exception {
        return new JSONArray().put(new JSONObject()).put(new JSONObject().put("data",
                new JSONObject().put("children", roots))).toString();
    }
    public static void main(String[] args) throws Exception {
        JSONObject fixture = new JSONObject(new String(Files.readAllBytes(Paths.get(args[0], "more_stub.json")), "UTF-8"));
        JSONObject more = fixture.getJSONObject("more");
        String child = more.getJSONArray("children").getString(0);
        String parent = more.getString("parent_id");
        String encoded = FixMoreCommentsPatch.encodeChildren(child, parent);
        String real = new String(Files.readAllBytes(Paths.get(args[0], "more_parent_permalink.json")), "UTF-8");
        JSONArray result = things(real, encoded);
        check(result.length() == 1, "Fixture includes only requested child, excluding parent");
        check(result.getJSONObject(0).getJSONObject("data").getString("id").equals(child), "Fixture child id");
        check(result.getJSONObject(0).getJSONObject("data").getString("parent_id").equals(parent), "Fixture parent preserved");
        check(result.getJSONObject(0).getJSONObject("data").getString("replies").isEmpty(), "Replies cleared");

        JSONObject truncated = new JSONObject().put("kind", "more").put("data", new JSONObject()
                .put("id", "_").put("parent_id", "t1_b").put("count", 0).put("children", new JSONArray()));
        JSONObject b = comment("b", "t1_a", new JSONArray().put(truncated));
        JSONObject a = comment("a", "t3_post", new JSONArray().put(b));
        String body = listing(new JSONArray().put(comment("unrelated", "t3_post", new JSONArray())).put(a));
        // Batch mode (a row as Reddit sent it): every requested comment present in one response.
        result = things(body, "t3_post|a,b,c");
        check(result.length() == 4, "Root, descendant, deep more, remaining sibling");
        check(result.getJSONObject(0).getJSONObject("data").getString("id").equals("a"), "Preorder root");
        check(result.getJSONObject(1).getJSONObject("data").getString("id").equals("b"), "Preorder descendant, once");
        check(result.getJSONObject(2).getString("kind").equals("more"), "Keep continue-thread stub");
        check(result.getJSONObject(3).getJSONObject("data").getJSONArray("children").toString().equals("[\"~c\"]"), "Remaining IDs exclude returned ones and switch to anchored mode");
        check(result.getJSONObject(3).getJSONObject("data").getInt("count") == 1, "Count excludes nothing extra");
        check(result.getJSONObject(3).getJSONObject("data").getString("parent_id").equals("t3_post"), "Residual parent");
        JSONObject c = comment("c", "t3_post", new JSONArray());
        result = things(listing(new JSONArray().put(a).put(c)), "t3_post|a,c");
        check(result.length() == 4 && result.getJSONObject(3).getJSONObject("data").getString("id").equals("c"), "Batch returns several roots from one response");

        // Batch mode never skips ids: an empty response re-emits everything, anchored.
        result = things(listing(new JSONArray()), "t1_parent|gone,next");
        check(result.length() == 1 && result.getJSONObject(0).getJSONObject("data").getJSONArray("children").toString().equals("[\"~gone\",\"next\"]"), "Batch miss re-emits all ids in anchored mode");
        // Anchored mode skips the attempted id when it's missing (deleted/blocked), keeps the rest.
        result = things(listing(new JSONArray()), "t1_parent|~gone,next");
        check(result.length() == 1 && result.getJSONObject(0).getJSONObject("data").getJSONArray("children").toString().equals("[\"~next\"]"), "Anchored miss skips attempted id, other ids retained");
        result = things(listing(new JSONArray()), "t1_parent|~gone");
        check(result.length() == 0, "Anchored chain terminates");

        // Malformed responses are handed back unchanged so Sync's parser fails and keeps the row.
        check(FixMoreCommentsPatch.convertListing("<html>403</html>", encoded).equals("<html>403</html>"), "HTML left for ParseError");
        check(FixMoreCommentsPatch.convertListing("{}", encoded).equals("{}"), "Malformed listing left for ParseError");
        check(FixMoreCommentsPatch.convertListing("[{},{}]", encoded).equals("[{},{}]"), "Missing children left for ParseError");
        check(FixMoreCommentsPatch.convertListing(real, null).equals(real), "Null metadata left unchanged");
        check(FixMoreCommentsPatch.convert(real, null).equals(real), "Null params left unchanged");

        // URLs: batch fetches the parent once; anchored fetches the first remaining id.
        String url = FixMoreCommentsPatch.buildUrl("post", "t3_post|a,b", "NEW");
        check(url.contains("/comments/post?") && url.contains("depth=3") && url.endsWith("&sort=new"), "Top-level batch fetches the whole post, shallow, with sort");
        check(FixMoreCommentsPatch.buildUrl("post", "t3_post|~a", null).contains("depth=10"), "Anchored keeps full depth");
        check(FixMoreCommentsPatch.buildUrl("post", "t1_parent|a,b", "top").contains("/comments/post/_/parent?"), "Nested batch fetches the parent comment");
        check(FixMoreCommentsPatch.buildUrl("post", "t3_post|~a,b", "top").contains("/comments/post/_/a?"), "Anchored fetches first remaining id");
        check(FixMoreCommentsPatch.buildUrl("post", "t1_parent|", "confidence").contains("/_/parent?"), "Empty children parent fallback");
        check(!FixMoreCommentsPatch.buildUrl("post", "t3_post|a", "x&y=1").contains("x&y"), "Sort is sanitised");
        System.out.println("PASS: " + checks + " assertions (real fixtures and synthetic edge cases)");
    }
}
