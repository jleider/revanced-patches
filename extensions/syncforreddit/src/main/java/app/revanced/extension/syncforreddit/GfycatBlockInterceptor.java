package app.revanced.extension.syncforreddit;

import java.io.IOException;
import java.util.Locale;

import app.revanced.extension.shared.Logger;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Response;

/**
 * Glide still requests dead Gfycat assets that Sync derives before Fix Gfycat repairs a post:
 * the thumbs.gfycat.com poster it preloads while parsing, and gfycat.com/favicon.ico for link
 * badges. Fail them locally instead of doing a DNS lookup for a host that no longer exists.
 */
@SuppressWarnings("unused")
public final class GfycatBlockInterceptor implements Interceptor {
    /** Injection point. */
    public static OkHttpClient.Builder addInterceptor(OkHttpClient.Builder builder) {
        try {
            return builder.addInterceptor(new GfycatBlockInterceptor());
        } catch (Exception ex) {
            Logger.printException(() -> "Could not install the Gfycat interceptor", ex);
            return builder;
        }
    }

    static boolean blocked(String host) {
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("gfycat.com") || host.endsWith(".gfycat.com");
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        if (blocked(chain.request().url().host())) {
            throw new IOException("Gfycat has shut down");
        }
        return chain.proceed(chain.request());
    }
}
