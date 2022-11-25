package dev.vendicated.vencord;

import androidx.annotation.NonNull;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class HttpClient {
    public static final String VENCORD_BUNDLE_URL = "https://github.com/Vendicated/Vencord/releases/download/devbuild/browser.js";

    public static final class HttpException extends IOException {
        private final HttpURLConnection conn;
        private String message;

        public HttpException(HttpURLConnection conn) {
            this.conn = conn;
        }

        @Override
        @NonNull
        public String getMessage() {
            if (message == null) {
                try {
                    message = String.format(
                            Locale.ENGLISH,
                            "%d: %s (%s)\n%s",
                            conn.getResponseCode(),
                            conn.getResponseMessage(),
                            conn.getURL().toString(),
                            readAsText(conn.getErrorStream())
                    );
                } catch (IOException ex) {
                    message = "Error while building message lmao. Url is " + conn.getURL().toString();
                }
            }
            return message;
        }
    }

    public static String vencord;

    public static void fetchVencord() throws IOException {
        if (vencord != null) return;

        var conn = fetch(VENCORD_BUNDLE_URL);
        try (var is = conn.getInputStream()) {
            vencord = readAsText(is);
        }
    }

    private static HttpURLConnection fetch(String url) throws IOException {
        var conn = (HttpURLConnection) new URL(url).openConnection();
        if (conn.getResponseCode() >= 300) {
            throw new HttpException(conn);
        }
        return conn;
    }

    private static String readAsText(InputStream is) throws IOException {
        try (var baos = new ByteArrayOutputStream()) {
            int n;
            byte[] buf = new byte[16384]; // 16 KB
            while ((n = is.read(buf)) > -1) {
                baos.write(buf, 0, n);
            }
            baos.flush();

            return baos.toString("UTF-8");
        }
    }
}
