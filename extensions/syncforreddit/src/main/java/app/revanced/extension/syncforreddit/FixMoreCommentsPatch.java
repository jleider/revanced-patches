package app.revanced.extension.syncforreddit;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Adapts a bounded comment permalink listing to Sync's morechildren parser. */
public final class FixMoreCommentsPatch {
    private static final String EMPTY = "{\"json\":{\"data\":{\"things\":[]}}}";

    private FixMoreCommentsPatch() {}

    // The parent is absent from OAuthCommentsMoreRequest's arguments. Carry it in
    // its unused GET form map, rather than keeping process-global request state.
    public static String encodeChildren(String children, String parent) {
        return parent + "|" + (children == null ? "" : children);
    }

    private static String parent(String encoded) {
        int split = encoded.indexOf('|');
        return split < 0 ? "" : encoded.substring(0, split);
    }

    private static LinkedHashSet<String> ids(String encoded) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String id : encoded.substring(encoded.indexOf('|') + 1).split(",")) {
            if (id.matches("[a-z0-9]+")) result.add(id);
        }
        return result;
    }

    public static String buildUrl(String post, String encoded, String sort) {
        Set<String> children = ids(encoded);
        String anchor = children.isEmpty() ? parent(encoded).replace("t1_", "")
                : children.iterator().next();
        // A permalink loads a requested root even when the main post listing's
        // limit would hide it. Remaining sibling roots become another more row.
        String url = "https://oauth.reddit.com/comments/" + post;
        if (anchor.matches("[a-z0-9]+")) url += "/_/" + anchor;
        url += "?limit=500&depth=10&context=0&raw_json=1&always_show_media=1&profile_img=true";
        if (sort != null && sort.matches("[A-Za-z_]+")) {
            url += "&sort=" + sort.toLowerCase(Locale.ENGLISH);
        }
        return url;
    }

    public static String convert(String body, Map<String, String> params) {
        if (params == null) return EMPTY;
        return convertListing(body, params.get("children"));
    }

    /** Public for the standalone JVM fixture harness. No Android dependencies. */
    public static String convertListing(String body, String encoded) {
        try {
            if (encoded == null) return EMPTY;
            LinkedHashSet<String> requested = ids(encoded);
            JSONArray listing = new JSONArray(body);
            JSONArray roots = listing.getJSONObject(1).getJSONObject("data").getJSONArray("children");
            JSONArray output = new JSONArray();
            Set<String> emitted = new LinkedHashSet<>();
            Set<String> found = new LinkedHashSet<>();
            select(roots, requested, output, emitted, found, 0);
            // Only the first ID was fetched. Keep other roots for subsequent
            // requests; skip an unavailable first ID so automatic top-level
            // loading cannot retry the same deleted/blocked comment forever.
            if (!requested.isEmpty()) requested.remove(requested.iterator().next());
            requested.removeAll(found);
            for (int i = 0; i < output.length(); i++) {
                JSONObject thing = output.getJSONObject(i);
                if ("t1".equals(thing.optString("kind"))) {
                    requested.remove(thing.getJSONObject("data").optString("id"));
                }
            }
            if (!requested.isEmpty() && parent(encoded).matches("t[13]_[a-z0-9]+")) {
                JSONObject data = new JSONObject();
                data.put("id", "_");
                data.put("name", "t1__");
                data.put("parent_id", parent(encoded));
                data.put("count", requested.size());
                data.put("children", new JSONArray(requested));
                output.put(new JSONObject().put("kind", "more").put("data", data));
            }
            return new JSONObject().put("json", new JSONObject().put("data",
                    new JSONObject().put("things", output))).toString();
        } catch (Exception ignored) {
            return EMPTY;
        }
    }

    private static JSONArray replies(JSONObject data) {
        JSONObject listing = data.optJSONObject("replies");
        JSONObject nested = listing == null ? null : listing.optJSONObject("data");
        return nested == null ? null : nested.optJSONArray("children");
    }

    private static void select(JSONArray nodes, Set<String> requested, JSONArray output,
                               Set<String> emitted, Set<String> found, int depth) throws Exception {
        if (nodes == null || depth > 100) return;
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject thing = nodes.getJSONObject(i);
            JSONObject data = thing.getJSONObject("data");
            if ("t1".equals(thing.optString("kind")) && requested.contains(data.optString("id"))) {
                found.add(data.getString("id"));
                flatten(thing, output, emitted, depth);
            } else {
                select(replies(data), requested, output, emitted, found, depth + 1);
            }
        }
    }

    private static void flatten(JSONObject thing, JSONArray output, Set<String> emitted,
                                int depth) throws Exception {
        if (depth > 100) return;
        String kind = thing.optString("kind");
        if (!"t1".equals(kind) && !"more".equals(kind)) return;
        JSONObject data = thing.getJSONObject("data");
        if (data.optString("id").isEmpty() || data.optString("parent_id").isEmpty()) return;
        String key = kind + ":" + data.getString("id") + ":" + data.getString("parent_id");
        if (!emitted.add(key)) return;
        JSONArray children = replies(data);
        data.put("replies", "");
        if ("more".equals(kind) && data.optJSONArray("children") == null) data.put("children", new JSONArray());
        output.put(thing);
        if (children != null) {
            for (int i = 0; i < children.length(); i++) flatten(children.getJSONObject(i), output, emitted, depth + 1);
        }
    }
}
