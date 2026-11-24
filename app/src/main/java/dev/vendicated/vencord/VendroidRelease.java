package dev.vendicated.vencord;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

final class VendroidRelease {
    private static final String RELEASES_URL = "https://github.com/Vencord/Vendroid/releases";
    private static final String LATEST_URL = RELEASES_URL + "/latest/download/latest.json";
    private static final int MAX_METADATA_BYTES = 1024 * 1024;
    private static final Pattern RELEASE_TAG = Pattern.compile("v[0-9][A-Za-z0-9._-]*");

    final int versionCode;
    final String versionName;
    final String changelog;
    private final String tag;

    private VendroidRelease(int versionCode, String versionName, String changelog, String tag) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.changelog = changelog;
        this.tag = tag;
    }

    boolean shouldPrompt(int installedVersion, int skippedVersion) {
        return versionCode > installedVersion && versionCode != skippedVersion;
    }

    String downloadUrl() {
        return RELEASES_URL + "/download/" + tag + "/Vendroid-" + tag + ".apk";
    }

    static VendroidRelease fetchLatest() throws IOException {
        return fetch(URI.create(LATEST_URL).toURL());
    }

    static VendroidRelease fetch(URL url) throws IOException {
        var connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setRequestProperty("User-Agent", Constants.USER_AGENT);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            var status = connection.getResponseCode();
            // Existing releases do not have the metadata asset. No API fallback is needed.
            if (status == HttpURLConnection.HTTP_NOT_FOUND) return null;
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Unexpected response for Vendroid release metadata: " + status);
            }

            try (var input = connection.getInputStream()) {
                return parse(readMetadata(input));
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readMetadata(InputStream input) throws IOException {
        try (var output = new ByteArrayOutputStream()) {
            var buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > MAX_METADATA_BYTES - output.size()) {
                    throw new IOException("Vendroid release metadata is too large");
                }
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static VendroidRelease parse(String metadata) throws IOException {
        try {
            var json = new JSONObject(metadata);
            var code = json.opt("versionCode");
            var versionName = json.opt("versionName");
            var changelog = json.opt("changelog");
            var tag = json.opt("tag");

            if (!(code instanceof Integer || code instanceof Long)
                    || !(versionName instanceof String) || ((String) versionName).trim().isEmpty()
                    || !(changelog instanceof String)
                    || !(tag instanceof String) || !RELEASE_TAG.matcher((String) tag).matches()) {
                throw new IOException("Invalid Vendroid release metadata");
            }

            var versionCode = ((Number) code).longValue();
            if (versionCode <= 0 || versionCode > Integer.MAX_VALUE) {
                throw new IOException("Invalid Vendroid release version code");
            }

            return new VendroidRelease((int) versionCode, (String) versionName, ((String) changelog).trim(), (String) tag);
        } catch (JSONException ex) {
            throw new IOException("Failed to parse Vendroid release metadata", ex);
        }
    }
}
