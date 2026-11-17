package app.revanced.extension.syncforreddit;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Validates /s/ share links before the shared resolver attaches the user's bearer token. */
public final class SLinkGuard {
    private SLinkGuard() {
    }

    /**
     * Returns {@code https://<host>/r/<sub>/s/<id>[#bypass]} for an http(s) link on reddit.com or a
     * subdomain with exactly that path, or null. HTTP is upgraded so the token is never sent in clear text.
     */
    public static String safeSLink(String link) {
        if (link == null) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(link.trim());
        } catch (URISyntaxException ignored) {
            return null;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        String path = uri.getRawPath();
        if (scheme == null || host == null || path == null) {
            return null;
        }
        if (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http")) {
            return null;
        }
        if (uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443 && uri.getPort() != 80)) {
            return null;
        }
        host = host.toLowerCase(Locale.ROOT);
        if (!host.equals("reddit.com") && !host.endsWith(".reddit.com")) {
            return null;
        }
        if (!path.matches("/r/[A-Za-z0-9_]+/s/[A-Za-z0-9]+/?")) {
            return null;
        }
        String fragment = uri.getRawFragment();
        if (fragment != null && !fragment.equals("bypass")) {
            return null;
        }
        String query = uri.getRawQuery();
        return "https://" + host + path.replaceAll("/$", "") + (query == null ? "" : "?" + query)
                + (fragment == null ? "" : "#bypass");
    }
}
