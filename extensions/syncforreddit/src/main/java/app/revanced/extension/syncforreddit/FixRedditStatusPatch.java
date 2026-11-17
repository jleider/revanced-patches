package app.revanced.extension.syncforreddit;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;

/**
 * Sync polls reddit.statuspage.io at startup and shows "The Reddit servers are having issues" whenever
 * the newest incident is unresolved, on every launch, whatever it is about (an Apple login problem in
 * Reddit's own iOS app, for example). Instead: only unresolved incidents that can affect Sync count,
 * and the sheet says what is wrong. An incident is shown again when Reddit posts a new update to it,
 * or after {@link #RESHOW_MS} while it stays unresolved.
 */
@SuppressWarnings("unused")
public final class FixRedditStatusPatch {
    private static final String PREFS = "revanced_reddit_status";
    private static final String SHOWN = "shown";
    /** An unresolved incident is shown again after this long, even without a new update. */
    static final long RESHOW_MS = 6 * 60 * 60 * 1000L;
    /** Incidents about one of Reddit's own clients, not the API Sync uses. */
    private static final Pattern OTHER_CLIENT = Pattern.compile(
            "(?i)\\b(ios|iphone|ipad|apple|android app|desktop|mobile web|old reddit|new reddit|shreddit|reddit app)\\b");
    /** Components about Reddit's own clients or internals; they alone don't show that the API is affected. */
    private static final List<String> CLIENT_ONLY = Arrays.asList(
            "Desktop Web", "Mobile Web", "Native Mobile Apps", "Spam Processing");
    /** Components that never affect a third-party client. */
    private static final List<String> IRRELEVANT = Arrays.asList("Desktop Web", "Mobile Web", "Spam Processing");

    private static volatile String details;
    /** The incident found by the last check, recorded as shown only once its sheet is displayed. */
    private static volatile JSONObject pending;
    private static volatile JSONObject pendingShown;
    private static volatile long pendingAt;

    private FixRedditStatusPatch() {
    }

    /** Injection point. Returns whether the response indicates a healthy service, suppressing the outage sheet. Never throws. */
    public static boolean healthy(String body) {
        try {
            long now = System.currentTimeMillis();
            JSONObject shown = shown();
            JSONObject incident = relevantIncident(new JSONObject(body).getJSONArray("incidents"), shown, now);
            if (incident == null) {
                return true;
            }
            details = describe(incident, now);
            pendingShown = shown;
            pendingAt = now;
            // Record the incident only when Sync builds the sheet.
            pending = incident;
            return false;
        } catch (Exception ex) {
            Logger.printException(() -> "Could not read the Reddit status response", ex);
            return true;
        }
    }

    /** Injection point. The sheet's message: the incident's details, or Sync's own text. */
    public static String message(String original) {
        try {
            String d = details;
            JSONObject inc = pending;
            if (inc != null) {
                pending = null;
                remember(pendingShown, inc, pendingAt);
            }
            return d != null ? d : original;
        } catch (Exception ex) {
            Logger.printException(() -> "Could not prepare the Reddit incident message", ex);
            return original;
        }
    }

    /**
     * The newest unresolved incident that can affect Sync and is due to be shown, or null. {@code shown}
     * maps incident id to {"u": latest update id when shown, "t": time shown}.
     */
    static JSONObject relevantIncident(JSONArray incidents, JSONObject shown, long now) {
        for (int i = 0; i < incidents.length(); i++) {
            JSONObject inc = incidents.optJSONObject(i);
            if (inc == null || !unresolved(inc)) {
                continue;
            }
            JSONObject seen = shown.optJSONObject(inc.optString("id"));
            if (seen != null && seen.optString("u").equals(latestUpdateId(inc))
                    && now - seen.optLong("t") < RESHOW_MS) {
                continue;
            }
            if (affectsSync(inc)) {
                return inc;
            }
        }
        return null;
    }

    /**
     * A component beyond Reddit's own clients (comments, votes, infrastructure, media, an API) means Sync
     * is affected, whatever the title says. Otherwise major/critical incidents count; minor ones about
     * only the web, or titled after one of Reddit's own clients, don't.
     */
    static boolean affectsSync(JSONObject inc) {
        JSONArray comps = inc.optJSONArray("components");
        boolean hasComps = comps != null && comps.length() > 0;
        boolean webOnly = hasComps;
        if (hasComps) {
            for (int c = 0; c < comps.length(); c++) {
                JSONObject comp = comps.optJSONObject(c);
                String name = comp == null ? "" : comp.optString("name");
                if (!name.isEmpty() && !CLIENT_ONLY.contains(name)) {
                    return true;
                }
                if (!IRRELEVANT.contains(name)) {
                    webOnly = false;
                }
            }
        }
        String impact = inc.optString("impact");
        if (impact.equals("major") || impact.equals("critical")) {
            return true;
        }
        if (webOnly) {
            return false;
        }
        return !OTHER_CLIENT.matcher(inc.optString("name")).find();
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
        if (!impact.isEmpty() && !impact.equals("none")) {
            facts.add(impact + " impact");
        }
        String status = inc.optString("status");
        if (!status.isEmpty()) {
            facts.add(status);
        }
        if (!facts.isEmpty()) {
            sb.append(" (").append(join(facts)).append(')');
        }
        JSONArray comps = inc.optJSONArray("components");
        if (comps != null) {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            for (int c = 0; c < comps.length(); c++) {
                JSONObject comp = comps.optJSONObject(c);
                if (comp != null && !comp.optString("name").isEmpty()) {
                    names.add(comp.optString("name"));
                }
            }
            if (!names.isEmpty()) {
                sb.append("\nAffected: ").append(join(names));
            }
        }
        JSONArray updates = inc.optJSONArray("incident_updates");
        JSONObject latest = updates == null ? null : updates.optJSONObject(0);
        String when = ago(latest != null ? latest.optString("created_at") : inc.optString("created_at"), now);
        if (latest != null && !latest.optString("body").trim().isEmpty()) {
            String text = latest.optString("body").trim();
            if (text.length() > 400) {
                text = text.substring(0, 397) + "…";
            }
            sb.append("\n\nLatest update").append(when.isEmpty() ? "" : ", " + when).append(": ").append(text);
        } else if (!when.isEmpty()) {
            sb.append("\nStarted ").append(when);
        }
        sb.append("\n\nSync may still work, but some things could be broken until Reddit fixes this.");
        return sb.toString();
    }

    /** "5 min ago" / "3 h ago" / "2 d ago" from an ISO-8601 time with offset, or "". */
    static String ago(String iso, long now) {
        if (iso == null || iso.isEmpty()) {
            return "";
        }
        // Normalize the offset because the XXX date pattern requires API 24.
        String timestamp = iso.replaceAll("\\.\\d+", "").replaceAll("Z$", "+0000")
                .replaceAll("([+-]\\d\\d):(\\d\\d)$", "$1$2");
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).parse(timestamp);
            long minutes = Math.max(0, (now - date.getTime()) / 60000);
            if (minutes < 1) {
                return "just now";
            }
            if (minutes < 60) {
                return minutes + " min ago";
            }
            if (minutes < 48 * 60) {
                return (minutes / 60) + " h ago";
            }
            return (minutes / 1440) + " d ago";
        } catch (Exception ignored) {
            // Omit the age when Reddit supplies an unreadable timestamp.
            return "";
        }
    }

    private static String join(Iterable<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            sb.append(sb.length() == 0 ? "" : ", ").append(p);
        }
        return sb.toString();
    }

    static String latestUpdateId(JSONObject inc) {
        JSONArray updates = inc.optJSONArray("incident_updates");
        JSONObject latest = updates == null ? null : updates.optJSONObject(0);
        return latest != null ? latest.optString("id") : "";
    }

    private static SharedPreferences prefs() {
        try {
            Context context = Utils.getContext();
            return context == null ? null : context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not open the Reddit status preferences", ex);
            return null;
        }
    }

    private static JSONObject shown() {
        SharedPreferences preferences = prefs();
        try {
            return preferences == null ? new JSONObject() : new JSONObject(preferences.getString(SHOWN, "{}"));
        } catch (Exception ex) {
            Logger.printException(() -> "Could not read the previously shown Reddit incidents", ex);
            return new JSONObject();
        }
    }

    /** Records the incident as shown now; drops entries older than a week so the map stays small. */
    static JSONObject record(JSONObject shown, JSONObject inc, long now) {
        try {
            JSONObject out = new JSONObject();
            Iterator<String> keys = shown.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                JSONObject v = shown.optJSONObject(k);
                if (v != null && now - v.optLong("t") < 7 * 24 * 3600 * 1000L) {
                    out.put(k, v);
                }
            }
            out.put(inc.optString("id"), new JSONObject().put("u", latestUpdateId(inc)).put("t", now));
            return out;
        } catch (Exception ex) {
            Logger.printException(() -> "Could not record the Reddit incident", ex);
            return shown;
        }
    }

    private static void remember(JSONObject shown, JSONObject inc, long now) {
        SharedPreferences p = prefs();
        if (p == null || inc.optString("id").isEmpty()) {
            return;
        }
        p.edit().putString(SHOWN, record(shown, inc, now).toString()).apply();
    }
}
