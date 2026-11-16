package app.revanced.extension.syncforreddit;

/** Validates /s/ share links before the shared resolver attaches the user's bearer token. */
public final class SLinkGuard {
    private SLinkGuard() {}

    /**
     * Returns {@code https://<host>/r/<sub>/s/<id>[#bypass]} for an http(s) link on reddit.com or a
     * subdomain with exactly that path, or null. http is upgraded so the token never goes out in clear.
     */
    public static String safeSLink(String link) {
        if (link == null) return null;
        java.net.URI u;
        try { u = new java.net.URI(link.trim()); } catch (Exception e) { return null; }
        String scheme = u.getScheme(), host = u.getHost(), path = u.getRawPath();
        if (scheme == null || host == null || path == null) return null;
        if (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http")) return null;
        if (u.getRawUserInfo() != null || (u.getPort() != -1 && u.getPort() != 443 && u.getPort() != 80)) return null;
        host = host.toLowerCase(java.util.Locale.ROOT);
        if (!host.equals("reddit.com") && !host.endsWith(".reddit.com")) return null;
        if (!path.matches("/r/[A-Za-z0-9_]+/s/[A-Za-z0-9]+/?")) return null;
        String fragment = u.getRawFragment();
        if (fragment != null && !fragment.equals("bypass")) return null;
        String query = u.getRawQuery();
        return "https://" + host + path.replaceAll("/$", "") + (query == null ? "" : "?" + query)
                + (fragment == null ? "" : "#bypass");
    }
}
