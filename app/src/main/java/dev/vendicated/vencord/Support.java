package dev.vendicated.vencord;

import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Developer contact info and the "report a problem" pipeline.
 *
 * Reports never go straight to a Discord webhook (that URL would have to be shipped inside the app). They go to a tiny
 * relay (see the support/ folder) that holds the webhook secret and only accepts reports that carry a signed support
 * code with an expiry date. The developer hands those codes out; the app only checks the shape and expiry locally.
 */
public final class Support {
    public static final String DEV_NAME = "FYODOR";
    public static final String TIKTOK_USER = "V6LL9V";
    public static final String INSTAGRAM_USER = "V6IL9V";
    public static final String DISCORD_USER = "V6LL9V";

    private static final int MAX_LOG_CHARS = 250_000;

    private Support() {}

    public static String tiktokUrl() {
        return "https://www.tiktok.com/@" + TIKTOK_USER;
    }

    public static String instagramUrl() {
        return "https://www.instagram.com/" + INSTAGRAM_USER + "/";
    }

    public static final class Code {
        public final String endpoint;
        public final long expiresAt;
        public final String raw;

        Code(String raw, String endpoint, long expiresAt) {
            this.raw = raw;
            this.endpoint = endpoint;
            this.expiresAt = expiresAt;
        }
    }

    public static final class CodeException extends Exception {
        public final boolean expired;

        CodeException(String message, boolean expired) {
            super(message);
            this.expired = expired;
        }
    }

    /**
     * Support codes look like {@code FC1.<payload>.<signature>}; the payload is base64url JSON {"u": relay url, "e": expiry in
     * unix seconds, "n": nonce}. The signature can only be checked by the relay, which owns the secret.
     */
    public static Code parseCode(String input, long nowSeconds) throws CodeException {
        if (input == null) throw new CodeException("empty", false);
        var raw = input.replaceAll("\\s+", "");
        var parts = raw.split("\\.");
        if (parts.length != 3 || !"FC1".equals(parts[0]) || parts[1].isEmpty() || parts[2].isEmpty()) {
            throw new CodeException("malformed", false);
        }

        String endpoint;
        long expiresAt;
        try {
            var json = new String(Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP), StandardCharsets.UTF_8);
            var o = new JSONObject(json);
            endpoint = o.getString("u");
            expiresAt = o.getLong("e");
        } catch (Exception ex) {
            throw new CodeException("unreadable", false);
        }

        if (!endpoint.startsWith("https://") || endpoint.length() > 200 || endpoint.contains("@") || endpoint.contains(" ")) {
            throw new CodeException("bad endpoint", false);
        }
        while (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
        if (expiresAt <= nowSeconds) throw new CodeException("expired", true);

        return new Code(raw, endpoint, expiresAt);
    }

    /** Joins the metadata header and the (already sanitised) log into the text file that gets attached to the report. */
    public static String buildLogFile(String meta, String sanitizedLog) {
        var log = sanitizedLog == null ? "" : sanitizedLog;
        if (log.length() > MAX_LOG_CHARS) log = "…(older lines cut)\n" + log.substring(log.length() - MAX_LOG_CHARS);
        return meta + "\n\n===== LOG =====\n" + log;
    }

    /** @throws IOException with a short human-readable message when the relay refuses or can't be reached */
    public static void send(Code code, String description, String contact, String meta, String logFile) throws IOException {
        var boundary = "----FyoCord" + UUID.randomUUID().toString().replace("-", "");
        var body = new ByteArrayOutputStream();
        field(body, boundary, "code", code.raw);
        field(body, boundary, "description", description);
        field(body, boundary, "contact", contact == null ? "" : contact);
        field(body, boundary, "meta", meta);
        file(body, boundary, "log", "fyocord-log.txt", logFile);
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var bytes = body.toByteArray();

        var conn = (HttpURLConnection) new URL(code.endpoint + "/report").openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(bytes.length);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setRequestProperty("User-Agent", "FyoCord-Android");
            try (var os = conn.getOutputStream()) {
                os.write(bytes);
            }

            int status = conn.getResponseCode();
            if (status / 100 == 2) return;

            switch (status) {
                case 401:
                    throw new IOException("invalid support code");
                case 410:
                    throw new IOException("support code expired");
                case 413:
                    throw new IOException("report too large");
                case 429:
                    throw new IOException("too many reports, try again in a minute");
                default:
                    throw new IOException("server answered " + status + " " + readShort(conn.getErrorStream()));
            }
        } finally {
            conn.disconnect();
        }
    }

    private static void field(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void file(ByteArrayOutputStream out, String boundary, String name, String filename, String content) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename
                + "\"\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String readShort(InputStream is) {
        if (is == null) return "";
        try (is) {
            var buf = new byte[300];
            int n = is.read(buf);
            return n > 0 ? new String(buf, 0, n, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim() : "";
        } catch (IOException ex) {
            return "";
        }
    }
}
