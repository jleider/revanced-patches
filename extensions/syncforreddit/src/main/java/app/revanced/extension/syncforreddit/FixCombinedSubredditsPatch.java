package app.revanced.extension.syncforreddit;

/**
 * oauth.reddit.com/r/a+b/hot now answers 301 -> https://www.reddit.com/ (the homepage, an HTML
 * block page for API clients). The same listing with the '+' percent-encoded, /r/a%2Bb/hot, still
 * returns the combined feed for every sort and for restricted search. Only the path is touched:
 * in the query string '+' means a space (search terms).
 *
 * @noinspection unused
 */
public final class FixCombinedSubredditsPatch {
    private FixCombinedSubredditsPatch() {}

    public static String fixUrl(String url) {
        if (url == null || url.indexOf('+') < 0) return url;
        int scheme = url.indexOf("://");
        if (scheme < 0) return url;
        int pathStart = url.indexOf('/', scheme + 3);
        if (pathStart < 0) return url;
        String host = url.substring(scheme + 3, pathStart).toLowerCase(java.util.Locale.ROOT);
        if (!(host.equals("reddit.com") || host.endsWith(".reddit.com"))) return url;
        int queryStart = url.indexOf('?', pathStart);
        int pathEnd = queryStart < 0 ? url.length() : queryStart;
        String path = url.substring(pathStart, pathEnd);
        if (path.indexOf('+') < 0) return url;
        return url.substring(0, pathStart) + path.replace("+", "%2B") + url.substring(pathEnd);
    }
}
