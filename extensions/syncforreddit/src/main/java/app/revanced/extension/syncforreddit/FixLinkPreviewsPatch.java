package app.revanced.extension.syncforreddit;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.UnknownServiceException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Dns;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Sync's image previews for links (Ultra "website previews", inline Imgur album / Tenor thumbnails)
 * load https://ap.syncforreddit.com/image?url=&lt;link&gt; through Glide's OkHttp client. That server
 * now answers 401. This interceptor does its job on the device instead: fetch the start of the
 * linked page, read og:image / twitter:image, and return that image. No third-party service.
 * <p>
 * The phone now fetches pages a post or comment links to, so the fetches use their own client:
 * DNS refuses private, loopback, link-local and carrier-NAT addresses, redirects are followed by hand
 * with every hop checked before connecting (IP literals skip DNS), images are read here with a hard
 * 10 MB cap on the decoded bytes (chunked or compressed responses included) before Glide sees them,
 * and the call has a 20 s timeout. http:// is tried over https first (the
 * Upgrade connections to HTTPS patch, with an http fallback so previews keep working).
 *
 * @noinspection unused
 */
public final class FixLinkPreviewsPatch implements Interceptor {
    private static final String PROXY = "https://ap.syncforreddit.com/image?url=";
    private static final int MAX_HTML_BYTES = 256 * 1024;
    static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final String NONE = "";
    private static final Pattern META = Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR = Pattern.compile(
            "([a-zA-Z:_-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private static final String[] KEYS = {
            "og:image:secure_url", "og:image", "og:image:url", "twitter:image", "twitter:image:src"};

    // page URL -> preview image URL ("" = page has none) with the time it was looked up.
    private static final long FOUND_TTL_MS = 6 * 60 * 60 * 1000L;
    private static final long NONE_TTL_MS = 15 * 60 * 1000L;
    private static final Map<String, Object[]> CACHE = new LinkedHashMap<String, Object[]>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Object[]> eldest) {
            return size() > 300;
        }
    };

    /** Refuses addresses on the device's own network: a link must not make the phone probe the LAN. */
    static final Dns PUBLIC_ONLY_DNS = new Dns() {
        @Override
        public List<InetAddress> lookup(String host) throws UnknownHostException {
            List<InetAddress> allowed = new ArrayList<>();
            for (InetAddress a : InetAddress.getAllByName(host)) if (!isPrivate(a)) allowed.add(a);
            if (allowed.isEmpty()) throw new UnknownHostException("Preview blocked: " + host + " is a private address");
            return allowed;
        }
    };

    /**
     * True if the request must not be made: an IP-literal host (OkHttp skips DNS for those, so
     * PUBLIC_ONLY_DNS never sees them) or a direct connection to a private address. Runs as a network
     * interceptor, so it sees every hop including redirects, and checks the address actually connected
     * to (covers DNS rebinding). Through a proxy only the host can be checked.
     */
    static boolean blockedHop(String host, InetAddress connected, boolean viaProxy) {
        if (host != null && (host.indexOf(':') >= 0 || host.matches("[0-9.]+"))) {
            try {
                if (isPrivate(InetAddress.getByName(host))) return true;   // literal: no DNS lookup
            } catch (UnknownHostException e) {
                return true;
            }
        }
        return !viaProxy && connected != null && isPrivate(connected);
    }

    static final Interceptor PUBLIC_ONLY_HOPS = chain -> {
        Request request = chain.request();
        InetAddress connected = null;
        boolean viaProxy = false;
        okhttp3.Connection connection = chain.connection();
        if (connection != null) {
            okhttp3.Route route = connection.route();
            viaProxy = route.proxy().type() != java.net.Proxy.Type.DIRECT;
            connected = route.socketAddress().getAddress();
        }
        if (blockedHop(request.url().host(), connected, viaProxy))
            throw new UnknownHostException("Preview blocked: " + request.url().host() + " is a private address");
        return chain.proceed(request);
    };

    static boolean isPrivate(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) return true;
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            int b0 = b[0] & 0xff, b1 = b[1] & 0xff;
            return b0 == 0 || (b0 == 100 && b1 >= 64 && b1 <= 127)        // 0/8, carrier-grade NAT 100.64/10
                    || (b0 == 192 && b1 == 0 && (b[2] & 0xff) == 0)       // 192.0.0/24
                    || (b0 == 198 && (b1 == 18 || b1 == 19)) || b0 >= 240; // benchmarking, reserved
        }
        if (a instanceof Inet6Address) {
            if ((b[0] & 0xfe) == 0xfc) return true;                         // unique local fc00::/7
            boolean mapped = true;                                          // ::ffff:a.b.c.d
            for (int i = 0; i < 10; i++) if (b[i] != 0) { mapped = false; break; }
            if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
                try {
                    return isPrivate(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException e) {
                    return true;
                }
            }
        }
        return false;
    }

    private static volatile OkHttpClient client;
    /** JVM tests serve responses from their fake chain instead of the network. */
    static boolean fetchThroughChainForTests;

    private static OkHttpClient client() {
        if (client == null) {
            synchronized (FixLinkPreviewsPatch.class) {
                if (client == null) client = new OkHttpClient.Builder()
                        .dns(PUBLIC_ONLY_DNS)
                        // Redirects are followed in fetch(), so every hop is checked before connecting.
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .addNetworkInterceptor(PUBLIC_ONLY_HOPS)
                        .callTimeout(20, TimeUnit.SECONDS)
                        .build();   // build() also adds the HTTPS upgrade interceptor in the app
            }
        }
        return client;
    }

    private static final int MAX_REDIRECTS = 5;

    /**
     * Sends the request and follows redirects by hand. Before each hop (the first request too) the
     * target is checked: only http(s), and no IP-literal private host. OkHttp skips DNS for IP
     * literals, so PUBLIC_ONLY_DNS alone can't stop them, and checking after connecting is too late.
     */
    static Response fetch(Chain chain, Request request) throws IOException {
        Request current = request;
        for (int hop = 0; ; hop++) {
            String scheme = current.url().scheme();
            if (!"http".equals(scheme) && !"https".equals(scheme))
                throw new UnknownServiceException("Preview blocked: " + scheme);
            if (blockedHop(current.url().host(), null, false))
                throw new UnknownHostException("Preview blocked: " + current.url().host() + " is a private address");
            Response response = fetchThroughChainForTests ? chain.proceed(current) : client().newCall(current).execute();
            if (!response.isRedirect()) return response;
            String location = response.header("Location");
            response.close();
            okhttp3.HttpUrl next = location == null ? null : current.url().resolve(location);
            if (next == null) throw new IOException("Preview redirect without a usable Location");
            if (hop >= MAX_REDIRECTS) throw new IOException("Preview: too many redirects");
            current = current.newBuilder().url(next).build();
        }
    }

    public static OkHttpClient.Builder addInterceptor(OkHttpClient.Builder builder) {
        return builder.addInterceptor(new FixLinkPreviewsPatch());
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        if (!request.url().toString().startsWith(PROXY)) return chain.proceed(request);
        try {
            return preview(chain, request);
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            // OkHttp rethrows non-IOExceptions from enqueued calls on its dispatcher thread, which
            // would kill the app (e.g. OkHttp rejecting an og:image URL that java.net.URI accepted).
            throw new IOException("Preview failed", e);
        }
    }

    static String cached(String page, long now) {
        synchronized (CACHE) {
            Object[] entry = CACHE.get(page);
            if (entry == null) return null;
            String image = (String) entry[0];
            long age = now - (Long) entry[1];
            if (age > (image.isEmpty() ? NONE_TTL_MS : FOUND_TTL_MS)) {
                CACHE.remove(page);
                return null;
            }
            return image;
        }
    }

    static void remember(String page, String image, long now) {
        synchronized (CACHE) {
            CACHE.put(page, new Object[]{image == null ? NONE : image, now});
        }
    }

    static void forget(String page) {
        synchronized (CACHE) {
            CACHE.remove(page);
        }
    }

    private Response preview(Chain chain, Request request) throws IOException {
        String page = pageFor(request.url().toString().substring(PROXY.length()));
        if (page == null) throw new IOException("No preview for this link");

        String image = cached(page, System.currentTimeMillis());
        if (image == null) {
            image = findImage(chain, page); // IOException (transient) propagates uncached
            remember(page, image, System.currentTimeMillis());
        }
        if (image == null || image.isEmpty()) throw new IOException("No preview image for " + page);

        Response response;
        try {
            response = fetch(chain, get(image).build());
        } catch (IOException | RuntimeException e) {
            forget(page);
            throw e;
        }
        String type = response.header("Content-Type", "");
        if (!response.isSuccessful() || !type.toLowerCase(Locale.ROOT).startsWith("image/")) {
            response.close();
            // e.g. an expired signed og:image URL: look the page up again next time.
            forget(page);
            throw new IOException("Preview image failed: " + response.code() + " " + type);
        }
        // Read the image here with a hard cap on the decoded bytes; Glide gets a bounded copy.
        byte[] bytes;
        try {
            bytes = readCapped(response, MAX_IMAGE_BYTES);
        } catch (IOException e) {
            remember(page, null, System.currentTimeMillis());   // too large: don't retry it
            throw e;
        }
        // Glide caches by its own key; hand back the image as the answer to the original request.
        return response.newBuilder().request(request)
                .removeHeader("Content-Length").removeHeader("Content-Encoding")
                .body(ResponseBody.create(bytes, response.body().contentType()))
                .build();
    }

    /** Reads the whole (decoded) body, failing as soon as it exceeds {@code max} bytes. Closes the response. */
    static byte[] readCapped(Response response, int max) throws IOException {
        try (Response r = response) {
            ResponseBody body = r.body();
            if (body == null) throw new IOException("Preview image has no body");
            long declared = body.contentLength();
            String header = r.header("Content-Length");
            if (header != null) {
                try { declared = Math.max(declared, Long.parseLong(header.trim())); } catch (NumberFormatException ignored) { }
            }
            // Reject early when the size is declared; otherwise the read below enforces the cap.
            if (declared > max) throw new IOException("Preview image too large: " + declared + " bytes");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(declared > 0 ? (int) Math.min(declared, max) : 64 * 1024);
            byte[] buffer = new byte[16 * 1024];
            InputStream in = body.byteStream();
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (out.size() + read > max) throw new IOException("Preview image too large: over " + max + " bytes");
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    /** Maps the proxy's url= value to the page to scrape, or null when there's nothing to preview. */
    static String pageFor(String value) {
        if (value == null || value.isEmpty()) return null;
        if (value.startsWith("imgur-album-")) {
            String id = value.substring("imgur-album-".length());
            return id.matches("[A-Za-z0-9]+") ? "https://imgur.com/a/" + id : null;
        }
        // Gfycat is gone; Redgifs needs its own API (handled by the Redgifs patch for playback).
        if (value.startsWith("gfycat-") || value.startsWith("redgifs-")) return null;
        if (!value.startsWith("https://") && !value.startsWith("http://")) return null;
        String host = host(value);
        if (host == null || host.equals("gfycat.com") || host.endsWith(".gfycat.com")) return null;
        return value;
    }

    private static String host(String url) {
        try {
            String host = new URI(url.replace(" ", "%20")).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }

    private static Request.Builder get(String url) {
        return new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android " + android.os.Build.VERSION.RELEASE
                        + "; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.8");
    }

    /** null = the page has no preview image (cached); IOException = transient failure (not cached). */
    private static String findImage(Chain chain, String page) throws IOException {
        try (Response response = fetch(chain, get(page)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5").build())) {
            ResponseBody body = response.body();
            if (response.code() >= 500 || response.code() == 429) throw new IOException("HTTP " + response.code());
            if (!response.isSuccessful() || body == null) return null;
            String type = response.header("Content-Type", "").toLowerCase(Locale.ROOT);
            // A direct image link previews as itself.
            if (type.startsWith("image/")) return page;
            if (!type.isEmpty() && !type.contains("html")) return null;
            String html = readPrefix(body.byteStream());
            return resolve(response.request().url().toString(), extractImage(html));
        } catch (java.net.UnknownServiceException e) {
            return null; // cleartext http:// blocked by the network security policy: permanent
        } catch (UnknownHostException e) {
            return null; // includes PUBLIC_ONLY_DNS refusing a private address: permanent
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            return null;
        }
    }

    private static String readPrefix(InputStream in) throws IOException {
        byte[] buffer = new byte[MAX_HTML_BYTES];
        int total = 0;
        while (total < buffer.length) {
            int read = in.read(buffer, total, buffer.length - total);
            if (read < 0) break;
            total += read;
            // og tags live in <head>; stop once it's over.
            if (total > 2048 && new String(buffer, Math.max(0, total - read - 8), read + Math.min(8, total - read),
                    StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT).contains("</head>")) break;
        }
        return new String(buffer, 0, total, StandardCharsets.UTF_8);
    }

    /** Public for the JVM test harness. Returns the best og/twitter image URL in the HTML, or null. */
    public static String extractImage(String html) {
        if (html == null) return null;
        Map<String, String> found = new LinkedHashMap<>();
        Matcher meta = META.matcher(html);
        while (meta.find()) {
            String key = null, content = null;
            Matcher attr = ATTR.matcher(meta.group());
            while (attr.find()) {
                String name = attr.group(1).toLowerCase(Locale.ROOT);
                String value = attr.group(3) != null ? attr.group(3) : attr.group(4) != null ? attr.group(4) : attr.group(5);
                if (name.equals("property") || name.equals("name") || name.equals("itemprop")) key = value.toLowerCase(Locale.ROOT).trim();
                else if (name.equals("content")) content = value.trim();
            }
            if (key != null && content != null && !content.isEmpty() && !found.containsKey(key)) found.put(key, content);
        }
        for (String key : KEYS) {
            String value = found.get(key);
            if (value != null) return unescape(value);
        }
        return null;
    }

    /** Public for the JVM test harness. */
    public static String resolve(String base, String image) {
        if (image == null) return null;
        try {
            String resolved = URI.create(base).resolve(image.replace(" ", "%20")).toString();
            return resolved.startsWith("https://") || resolved.startsWith("http://") ? resolved : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String unescape(String s) {
        return s.replace("&amp;", "&").replace("&#38;", "&").replace("&quot;", "\"")
                .replace("&#x2F;", "/").replace("&#47;", "/");
    }
}
