package app.revanced.extension.syncforreddit;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Sync polls reddit.statuspage.io at startup and shows "The Reddit servers are having issues" whenever
 * the newest incident is unresolved, on every launch, whatever it is about (an Apple login problem in
 * Reddit's own iOS app, for example). Instead: only unresolved incidents that can affect Sync count,
 * and the sheet says what is wrong. An incident is shown again when Reddit posts a new update to it,
 * or after {@link #RESHOW_MS} while it stays unresolved.
 *
 * @noinspection unused
 */
public final class FixRedditStatusPatch {
    private static final String PREFS = "revanced_reddit_status";
    private static final String SHOWN = "shown";
    /** An unresolved incident is shown again after this long, even without a new update. */
    static final long RESHOW_MS = 6 * 60 * 60 * 1000L;
    /** Incidents about one of Reddit's own clients, not the API Sync uses. */
    private static final Pattern OTHER_CLIENT = Pattern.compile(
            "(?i)\\b(ios|iphone|ipad|apple|android app|desktop|mobile web|old reddit|new reddit|shreddit|reddit app)\\b");
    /** Components that never affect a third-party client. */
    private static final List<String> IRRELEVANT = java.util.Arrays.asList("Spam Processing", "Desktop Web", "Mobile Web");

    private static volatile String details;

    private FixRedditStatusPatch() {}

    /** Replaces RedditStatusRequest's parser: true = healthy (no sheet). Never throws. */
    public static boolean healthy(String body) {
        try {
            long now = System.currentTimeMillis();
            JSONObject shown = shown();
            JSONObject incident = relevantIncident(new JSONObject(body).getJSONArray("incidents"), shown, now);
            if (incident == null) return true;
            details = describe(incident, now);
            remember(shown, incident, now);
            return false;
        } catch (Exception e) {
            return true;   // unreadable status page: say nothing rather than alarm
        }
    }

    /** The sheet's message: the incident's details, or Sync's own text. */
    public static String message(String original) {
        String d = details;
        return d != null ? d : original;
    }

    /**
     * The newest unresolved incident that can affect Sync and is due to be shown, or null. {@code shown}
     * maps incident id to {"u": latest update id when shown, "t": time shown}.
     */
    static JSONObject relevantIncident(JSONArray incidents, JSONObject shown, long now) {
        for (int i = 0; i < incidents.length(); i++) {
            JSONObject inc = incidents.optJSONObject(i);
            if (inc == null || !unresolved(inc)) continue;
            JSONObject seen = shown.optJSONObject(inc.optString("id"));
            if (seen != null && seen.optString("u").equals(latestUpdateId(inc))
                    && now - seen.optLong("t") < RESHOW_MS) continue;
            String impact = inc.optString("impact");
            boolean severe = impact.equals("major") || impact.equals("critical");
            if (!severe && OTHER_CLIENT.matcher(inc.optString("name")).find()) continue;
            JSONArray comps = inc.optJSONArray("components");
            if (comps != null && comps.length() > 0) {
                boolean any = false;
                for (int c = 0; c < comps.length(); c++) {
                    JSONObject comp = comps.optJSONObject(c);
                    if (comp != null && !IRRELEVANT.contains(comp.optString("name"))) any = true;
                }
                if (!any && !severe) continue;
            }
            return inc;
        }
        return null;
    }

    static boolean unresolved(JSONObject inc) {
        String resolved = inc.optString("resolved_at", "null");
        String status = inc.optString("status");
        return (resolved.isEmpty() || resolved.equals("null")) && !status.equals("resolved") && !status.equals("postmortem");
    }

    /** "Elevated errors (major impact, identified)\nAffected: …\n\nLatest update, 12 min ago: …" */
    static String describe(JSONObject inc, long now) {
        StringBuilder sb = new StringBuilder(inc.optString("name", "Reddit incident").trim());
        List<String> facts = new ArrayList<>();
        String impact = inc.optString("impact");
        if (!impact.isEmpty() && !impact.equals("none")) facts.add(impact + " impact");
        String status = inc.optString("status");
        if (!status.isEmpty()) facts.add(status);
        if (!facts.isEmpty()) sb.append(" (").append(join(facts)).append(')');
        JSONArray comps = inc.optJSONArray("components");
        if (comps != null && comps.length() > 0) {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            for (int c = 0; c < comps.length(); c++) {
                JSONObject comp = comps.optJSONObject(c);
                if (comp != null && !comp.optString("name").isEmpty()) names.add(comp.optString("name"));
            }
            if (!names.isEmpty()) sb.append("\nAffected: ").append(join(names));
        }
        JSONArray updates = inc.optJSONArray("incident_updates");
        JSONObject latest = updates != null && updates.length() > 0 ? updates.optJSONObject(0) : null;
        String when = ago(latest != null ? latest.optString("created_at") : inc.optString("created_at"), now);
        if (latest != null && !latest.optString("body").trim().isEmpty()) {
            String text = latest.optString("body").trim();
            if (text.length() > 400) text = text.substring(0, 397) + "…";
            sb.append("\n\nLatest update").append(when.isEmpty() ? "" : ", " + when).append(": ").append(text);
        } else if (!when.isEmpty()) {
            sb.append("\nStarted ").append(when);
        }
        sb.append("\n\nSync may still work, but some things could be broken until Reddit fixes this.");
        return sb.toString();
    }

    /** "5 min ago" / "3 h ago" / "2 d ago" from an ISO-8601 time with offset, or "". */
    static String ago(String iso, long now) {
        if (iso == null || iso.isEmpty()) return "";
        // 2026-11-16T02:02:29.579-08:00 -> 2026-11-16T02:02:29-0800 (the XXX pattern needs API 24).
        String t = iso.replaceAll("\\.\\d+", "").replaceAll("Z$", "+0000").replaceAll("([+-]\\d\\d):(\\d\\d)$", "$1$2");
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).parse(t);
            if (d != null) {
                long min = Math.max(0, (now - d.getTime()) / 60000);
                if (min < 1) return "just now";
                if (min < 60) return min + " min ago";
                if (min < 48 * 60) return (min / 60) + " h ago";
                return (min / 1440) + " d ago";
            }
        } catch (Exception ignored) {
            // unparseable: no age
        }
        return "";
    }

    private static String join(Iterable<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append(sb.length() == 0 ? "" : ", ").append(p);
        return sb.toString();
    }

    static String latestUpdateId(JSONObject inc) {
        JSONArray updates = inc.optJSONArray("incident_updates");
        JSONObject latest = updates != null && updates.length() > 0 ? updates.optJSONObject(0) : null;
        return latest != null ? latest.optString("id") : "";
    }

    private static SharedPreferences prefs() {
        try {
            Context c = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            return c == null ? null : c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        } catch (Throwable e) {
            return null;   // no app context (unit tests): nothing is remembered
        }
    }

    private static JSONObject shown() {
        SharedPreferences p = prefs();
        try {
            return new JSONObject(p == null ? "{}" : p.getString(SHOWN, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** Records the incident as shown now; drops entries older than a week so the map stays small. */
    static JSONObject record(JSONObject shown, JSONObject inc, long now) {
        try {
            JSONObject out = new JSONObject();
            java.util.Iterator<String> keys = shown.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                JSONObject v = shown.optJSONObject(k);
                if (v != null && now - v.optLong("t") < 7 * 24 * 3600 * 1000L) out.put(k, v);
            }
            out.put(inc.optString("id"), new JSONObject().put("u", latestUpdateId(inc)).put("t", now));
            return out;
        } catch (Exception e) {
            return shown;
        }
    }

    private static void remember(JSONObject shown, JSONObject inc, long now) {
        SharedPreferences p = prefs();
        if (p == null || inc.optString("id").isEmpty()) return;
        p.edit().putString(SHOWN, record(shown, inc, now).toString()).apply();
    }
}
