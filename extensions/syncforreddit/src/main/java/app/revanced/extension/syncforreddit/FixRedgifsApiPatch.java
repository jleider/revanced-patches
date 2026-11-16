package app.revanced.extension.syncforreddit;

import app.revanced.extension.shared.fixes.redgifs.BaseFixRedgifsApiPatch;
import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Response;

/**
 * @noinspection unused
 */
public class FixRedgifsApiPatch extends BaseFixRedgifsApiPatch {
    static {
        INSTANCE = new FixRedgifsApiPatch();
    }

    public String getDefaultUserAgent() {
        // To be filled in by patch
        return "";
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        // Redgifs removed /info, which Sync calls before every lookup; answer it locally.
        Response local = RedgifsInfo.emulate(chain.request());
        return local != null ? local : super.intercept(chain);
    }

    public static OkHttpClient install(OkHttpClient.Builder builder) {
        return builder.addInterceptor(INSTANCE).build();
    }
}
