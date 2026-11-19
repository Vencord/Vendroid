package dev.vendicated.vencord;

import android.app.Activity;
import android.content.res.Resources;

import androidx.annotation.NonNull;

import java.io.*;
import java.net.HttpURLConnection;
import java.util.Locale;

public class HttpClient {
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
                try(var es = conn.getErrorStream()) {
                    message = String.format(
                            Locale.ENGLISH,
                            "%d: %s (%s)\n%s",
                            conn.getResponseCode(),
                            conn.getResponseMessage(),
                            conn.getURL().toString(),
                            readAsText(es)
                    );
                } catch (IOException ex) {
                    message = "Error while building message lmao. Url is " + conn.getURL().toString();
                }
            }
            return message;
        }
    }

    public static String VencordRuntime;
    public static String VencordMobileRuntime;
    public static String VencordCss;

    public static void fetchVencord(Activity activity) throws IOException {
        if (VencordRuntime != null) return;

        var res = activity.getResources();
        try (var is = res.openRawResource(R.raw.vendroid_script)) {
            VencordMobileRuntime = readAsText(is);
        }

        var freshlyInstalled = VencordUpdater.ensureInstalled(activity);
        if (!freshlyInstalled && VendroidSettings.isAutoUpdatesEnabled(activity)) {
            VencordUpdater.autoUpdate(activity);
        }

        VencordUpdater.loadBundles(activity);
    }

    static String readAsText(InputStream is) throws IOException {
        if (is == null) return "";

        try (var baos = new ByteArrayOutputStream()) {
            int n;
            byte[] buf = new byte[16384]; // 16 KB
            while ((n = is.read(buf)) > -1) {
                baos.write(buf, 0, n);
            }
            baos.flush();

            //noinspection CharsetObjectCanBeUsed thank you so much android studio but no i do not want to use an sdk33 api ._.
            return baos.toString("UTF-8");
        }
    }
}
