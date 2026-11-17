package app.revanced.extension.syncforreddit;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import app.revanced.extension.shared.Logger;

/**
 * Sync plays YouTube in-app through the YouTube Android Player API, which runs inside the YouTube app
 * (service action com.google.android.youtube.api.service.START). Current YouTube versions no longer
 * ship that service, so initialization fails with SERVICE_MISSING and Sync shows Google's "get the
 * YouTube app" dialog even though YouTube is installed; updating YouTube can't fix it.
 * <p>
 * For that failure this opens the link in the YouTube app (or browser) and closes Sync's player.
 * It deliberately does not take Sync's own SERVICE_INVALID path, which also switches off the
 * "open YouTube links in Sync" setting permanently.
 */
@SuppressWarnings("unused")
public final class FixYouTubePatch {
    private FixYouTubePatch() {
    }

    static boolean unavailable(Object result) {
        if (!(result instanceof Enum)) {
            return false;
        }
        String name = ((Enum<?>) result).name();
        return "SERVICE_MISSING".equals(name) || "SERVICE_VERSION_UPDATE_REQUIRED".equals(name);
    }

    /** Injection point. Returns whether Sync can skip its player initialization error dialog. */
    public static boolean openExternally(Activity activity, Object result) {
        try {
            if (!unavailable(result)) {
                return false;
            }
            Intent intent = activity.getIntent();
            String url = intent == null ? null : intent.getStringExtra("url");
            if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) {
                try {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ex) {
                    // Close the defunct player even when no app can handle the link.
                    Logger.printException(() -> "Could not open the YouTube video externally", ex);
                }
            }
            activity.finish();
            return true;
        } catch (Exception ex) {
            Logger.printException(() -> "Could not handle the YouTube player initialization failure", ex);
            return false;
        }
    }
}
