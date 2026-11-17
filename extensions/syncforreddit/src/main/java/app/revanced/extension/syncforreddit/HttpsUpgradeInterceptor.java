package app.revanced.extension.syncforreddit;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownServiceException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

import app.revanced.extension.shared.Logger;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Upgrades plain http:// requests to https:// on every OkHttp client in the app (Volley, Glide,
 * ExoPlayer, link previews). Sync and Reddit data still contain http links, and some helpers
 * downgrade on purpose (Imgur's https -> http rewrite). If a host's HTTPS doesn't connect at all,
 * the request falls back to http for that host for the rest of the session, so nothing that works
 * today stops working.
 */
@SuppressWarnings("unused")
public final class HttpsUpgradeInterceptor implements Interceptor {
    /** Hosts whose HTTPS failed to connect this session. */
    static final Set<String> HTTP_ONLY = Collections.synchronizedSet(new HashSet<String>());
    private static final int HTTPS_CONNECT_TIMEOUT_MS = 5000;

    /** Injection point. Called at the start of OkHttpClient.Builder.build(): append once, after the app's own interceptors. */
    public static OkHttpClient.Builder install(OkHttpClient.Builder builder) {
        try {
            List<Interceptor> interceptors = builder.interceptors();
            for (Interceptor i : interceptors) {
                if (i instanceof HttpsUpgradeInterceptor) {
                    return builder;
                }
            }
            interceptors.add(new HttpsUpgradeInterceptor());
            return builder;
        } catch (Exception ex) {
            Logger.printException(() -> "Could not install the HTTPS upgrade interceptor", ex);
            return builder;
        }
    }

    /** The https:// form of a plain http URL on the default port, or null if it shouldn't be upgraded. */
    static String upgrade(String url, String host, int port) {
        if (url == null || host == null || !url.regionMatches(true, 0, "http://", 0, 7)) {
            return null;
        }
        // Services on explicit nonstandard ports may have different TLS settings.
        if (port != 80 || !upgradableHost(host)) {
            return null;
        }
        String rest = url.substring(7);
        // Drop an explicit ":80" so the https URL uses 443.
        int slash = rest.indexOf('/');
        String authority = slash < 0 ? rest : rest.substring(0, slash);
        if (authority.endsWith(":80")) {
            rest = authority.substring(0, authority.length() - 3) + (slash < 0 ? "" : rest.substring(slash));
        }
        return "https://" + rest;
    }

    static boolean upgradableHost(String host) {
        host = host.toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
                || host.endsWith(".invalid") || host.endsWith(".test")) {
            return false;
        }
        // IP literals: private ones have no certificates; public ones rarely have IP certificates.
        if (host.indexOf(':') >= 0 || host.matches("[0-9.]+")) {
            return false;
        }
        return host.indexOf('.') > 0;
    }

    /**
     * Failures where HTTPS itself didn't work, so retrying over http is safe. Only failures before the
     * request could have been sent count for every method: refused/unreachable port, TLS handshake
     * failure (bad or missing certificate), cleartext policy. Other TLS errors and timeouts can happen
     * after the request was written, so they only count for idempotent GET/HEAD: a POST is never
     * sent twice.
     */
    static boolean httpsUnavailable(IOException e, String method) {
        if (e instanceof ConnectException || e instanceof NoRouteToHostException
                || e instanceof SSLHandshakeException || e instanceof SSLPeerUnverifiedException
                || e instanceof UnknownServiceException) {
            return true;
        }
        boolean idempotent = "GET".equals(method) || "HEAD".equals(method);
        return idempotent && (e instanceof SSLException || e instanceof SocketTimeoutException);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        HttpUrl url = request.url();
        if (url.isHttps()) {
            return chain.proceed(request);
        }
        String host = url.host();
        String https = upgrade(url.toString(), host, url.port());
        HttpUrl upgraded = https == null ? null : HttpUrl.parse(https);
        if (upgraded == null || HTTP_ONLY.contains(host.toLowerCase(Locale.ROOT))) {
            return chain.proceed(request);
        }
        try {
            int timeout = Math.min(chain.connectTimeoutMillis(), HTTPS_CONNECT_TIMEOUT_MS);
            return chain.withConnectTimeout(timeout, TimeUnit.MILLISECONDS)
                    .proceed(request.newBuilder().url(upgraded).build());
        } catch (IOException e) {
            if (chain.call().isCanceled() || !httpsUnavailable(e, request.method())) {
                throw e;
            }
            if (HTTP_ONLY.add(host.toLowerCase(Locale.ROOT))) {
                Logger.printDebug(() -> "HTTPS is unavailable for " + host + ". Using HTTP for this session.");
            }
            return chain.proceed(request);
        }
    }
}
