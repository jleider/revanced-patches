package android.net;

/** JVM stand-in for the bits of android.net.Uri the extensions use (android.jar's version throws "Stub!"). */
public final class Uri {
    private final java.net.URI uri;
    private Uri(java.net.URI uri) { this.uri = uri; }
    public static Uri parse(String s) {
        try { return new Uri(new java.net.URI(s)); } catch (Exception e) { return new Uri(java.net.URI.create("invalid:x")); }
    }
    public String getHost() { return uri.getHost(); }
    public String getPath() { return uri.getPath(); }
    public String getScheme() { return uri.getScheme(); }
    public String getQueryParameter(String key) {
        String q = uri.getRawQuery();
        if (q == null) return null;
        for (String part : q.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv[0].equals(key)) return kv.length > 1 ? java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8) : "";
        }
        return null;
    }
    @Override public String toString() { return uri.toString(); }
}
