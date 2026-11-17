package app.revanced.extension.syncforreddit;

import java.io.IOException;
import java.util.Locale;

import app.revanced.extension.shared.Logger;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Sync doesn't request raw_json=1 everywhere, so preview image URLs arrive HTML-escaped
 * ("?width=140&amp;height=108&amp;s=...") and Glide requests them that way. Reddit's image CDN
 * (preview.redd.it, external-preview.redd.it) started answering those with 403 in Nov 2026.
 */
@SuppressWarnings("unused")
public final class RedditImageUrlInterceptor implements Interceptor {
    /** Injection point. */
    public static OkHttpClient.Builder addInterceptor(OkHttpClient.Builder builder) {
        try {
            return builder.addInterceptor(new RedditImageUrlInterceptor());
        } catch (Exception ex) {
            Logger.printException(() -> "Could not install the Reddit image URL interceptor", ex);
            return builder;
        }
    }

    /** The unescaped URL for an escaped *.redd.it image URL, or null if nothing to change. */
    static String fix(String url, String host) {
        if (host == null || url == null || !url.contains("&amp;")) {
            return null;
        }
        host = host.toLowerCase(Locale.ROOT);
        if (!host.equals("redd.it") && !host.endsWith(".redd.it")
                && !host.endsWith(".redditmedia.com") && !host.endsWith(".redditstatic.com")) {
            return null;
        }
        String fixed = url;
        // Double-escaped "&amp;amp;" occurs in some fields; unescape until stable.
        while (fixed.contains("&amp;")) {
            fixed = fixed.replace("&amp;", "&");
        }
        return fixed;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        String fixed = fix(request.url().toString(), request.url().host());
        if (fixed != null) {
            HttpUrl url = HttpUrl.parse(fixed);
            if (url != null) {
                request = request.newBuilder().url(url).build();
            }
        }
        return chain.proceed(request);
    }
}
