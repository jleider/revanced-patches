package app.revanced.extension.syncforreddit;

import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Before every Redgifs lookup Sync calls GET https://api.redgifs.com/info (GrabIpRequest) to learn the
 * device's public IP for the {@code user-addr} query parameter. Redgifs removed that endpoint (404), so
 * the chain stopped before the video lookup. The lookup works with any or no {@code user-addr}, so the
 * endpoint is answered locally with an empty address; nothing is sent.
 */
public final class RedgifsInfo {
    private RedgifsInfo() {}

    static boolean isInfo(Request request) {
        return request.url().host().equals("api.redgifs.com") && request.url().encodedPath().equals("/info")
                && request.method().equals("GET");
    }

    /** The local /info answer, or null for any other request. */
    public static Response emulate(Request request) {
        if (!isInfo(request)) return null;
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Type", "application/json")
                .body(ResponseBody.create("{\"remote-addr\":\"\"}", MediaType.get("application/json")))
                .build();
    }
}
