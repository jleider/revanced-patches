package app.revanced.extension.baconreader.imgur;

import java.net.HttpURLConnection;

@SuppressWarnings("unused")
public final class FixImgurApiPatch {
    private FixImgurApiPatch() {
    }

    /**
     * The app uploads the raw file without a content type, so Android sends it as
     * "application/x-www-form-urlencoded" and Imgur's API answers 400 Bad Request.
     */
    public static void setUploadContentType(HttpURLConnection connection) {
        connection.setRequestProperty("Content-Type", "application/octet-stream");
    }
}
