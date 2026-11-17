package app.revanced.extension.syncforreddit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import app.revanced.extension.shared.Logger;

/**
 * Adapts comment permalink listings to Sync's morechildren parser (/api/morechildren is blocked).
 *
 * A row from Reddit usually needs only one request. Fetch the post for a top-level row or the parent
 * comment for a nested row, then return every requested comment found in that listing.
 * If the parent listing omits some comments, the next row fetches them one permalink at a time.
 * The {@link #ANCHORED} prefix rides on an existing ID so Sync's "load N more" count stays right.
 * A malformed response is returned unchanged so Sync's own parser fails and keeps the row.
 */
@SuppressWarnings("unused")
public final class FixMoreCommentsPatch {
    /** Prefix on the first child id of rows we emit. Comment ids are [a-z0-9]+, so it can't collide. */
    static final String ANCHORED = "~";

    private FixMoreCommentsPatch() {
    }

    // The parent is absent from OAuthCommentsMoreRequest's arguments. Carry it in
    // its unused GET form map, rather than keeping process-global request state.
    /** Injection point. */
    public static String encodeChildren(String children, String parent) {
        return parent + "|" + (children == null ? "" : children);
    }

    private static String parent(String encoded) {
        int split = encoded.indexOf('|');
        return split < 0 ? "" : encoded.substring(0, split);
    }

    private static boolean anchored(String encoded) {
        return encoded.substring(encoded.indexOf('|') + 1).trim().startsWith(ANCHORED);
    }

    private static LinkedHashSet<String> ids(String encoded) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String id : encoded.substring(encoded.indexOf('|') + 1).split(",")) {
            id = id.trim();
            if (id.startsWith(ANCHORED)) {
                id = id.substring(ANCHORED.length());
            }
            if (id.matches("[a-z0-9]+")) {
                result.add(id);
            }
        }
        return result;
    }

    /** Injection point. */
    public static String buildUrl(String post, String encoded, String sort) {
        try {
            Set<String> children = ids(encoded);
            String parent = parent(encoded);
            String anchor;
            if (anchored(encoded) && !children.isEmpty()) {
                anchor = children.iterator().next();
            } else {
                // A single parent listing can cover several requested comments in batch mode.
                anchor = parent.startsWith("t1_") ? parent.substring(3) : "";
            }
            String url = "https://oauth.reddit.com/comments/" + post;
            if (anchor.matches("[a-z0-9]+")) {
                url += "/_/" + anchor;
            }
            // Reddit spends limit=500 on nested replies first. For a top-level batch, depth=3 reaches
            // ~35% more hidden top-level comments (measured on a 1.3k-comment thread); deeper replies
            // become ordinary "View more" rows that batch per parent when tapped.
            int depth = anchor.isEmpty() ? 3 : 10;
            url += "?limit=500&depth=" + depth + "&context=0&raw_json=1&always_show_media=1&profile_img=true";
            if (sort != null && sort.matches("[A-Za-z_]+")) {
                url += "&sort=" + sort.toLowerCase(Locale.ENGLISH);
            }
            return url;
        } catch (Exception ex) {
            Logger.printException(() -> "Could not build the more-comments URL", ex);
            // The constructor needs a URL for the request. Preserve its existing failure path.
            throw ex;
        }
    }

    /** Injection point. */
    public static String convert(String body, Map<String, String> params) {
        if (params == null) {
            return body;
        }
        return convertListing(body, params.get("children"));
    }

    /** Converts a response without Android dependencies so it can also be tested with JVM fixtures. */
    public static String convertListing(String body, String encoded) {
        try {
            if (encoded == null) {
                return body;
            }
            boolean anchoredMode = anchored(encoded);
            LinkedHashSet<String> requested = ids(encoded);
            JSONArray listing = new JSONArray(body);
            JSONArray roots = listing.getJSONObject(1).getJSONObject("data").getJSONArray("children");
            JSONArray output = new JSONArray();
            Set<String> emitted = new LinkedHashSet<>();
            Set<String> found = new LinkedHashSet<>();
            select(roots, requested, output, emitted, found, 0);
            // Anchored mode fetched only the first id: skip it even when it's missing (deleted or
            // blocked), so automatic top-level loading can't retry the same comment forever.
            if (anchoredMode && !requested.isEmpty()) {
                requested.remove(requested.iterator().next());
            }
            requested.removeAll(found);
            for (int i = 0; i < output.length(); i++) {
                JSONObject thing = output.getJSONObject(i);
                if ("t1".equals(thing.optString("kind"))) {
                    requested.remove(thing.getJSONObject("data").optString("id"));
                }
            }
            if (!requested.isEmpty() && parent(encoded).matches("t[13]_[a-z0-9]+")) {
                JSONArray children = new JSONArray();
                for (String id : requested) {
                    children.put(children.length() == 0 ? ANCHORED + id : id);
                }
                JSONObject data = new JSONObject();
                data.put("id", "_");
                data.put("name", "t1__");
                data.put("parent_id", parent(encoded));
                data.put("count", requested.size());
                data.put("children", children);
                output.put(new JSONObject().put("kind", "more").put("data", data));
            }
            return new JSONObject().put("json", new JSONObject().put("data",
                    new JSONObject().put("things", output))).toString();
        } catch (Exception ex) {
            Logger.printException(() -> "Could not convert the more-comments listing", ex);
            // The original body lets Sync report the parse error and keep the row for a retry.
            return body == null ? "" : body;
        }
    }

    private static JSONArray replies(JSONObject data) {
        JSONObject listing = data.optJSONObject("replies");
        JSONObject nested = listing == null ? null : listing.optJSONObject("data");
        return nested == null ? null : nested.optJSONArray("children");
    }

    private static void select(JSONArray nodes, Set<String> requested, JSONArray output,
                               Set<String> emitted, Set<String> found, int depth) throws Exception {
        if (nodes == null || depth > 100) {
            return;
        }
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
        if (depth > 100) {
            return;
        }
        String kind = thing.optString("kind");
        if (!"t1".equals(kind) && !"more".equals(kind)) {
            return;
        }
        JSONObject data = thing.getJSONObject("data");
        if (data.optString("id").isEmpty() || data.optString("parent_id").isEmpty()) {
            return;
        }
        String key = kind + ":" + data.getString("id") + ":" + data.getString("parent_id");
        if (!emitted.add(key)) {
            return;
        }
        JSONArray children = replies(data);
        data.put("replies", "");
        if ("more".equals(kind) && data.optJSONArray("children") == null) {
            data.put("children", new JSONArray());
        }
        output.put(thing);
        if (children != null) {
            for (int i = 0; i < children.length(); i++) {
                flatten(children.getJSONObject(i), output, emitted, depth + 1);
            }
        }
    }
}
