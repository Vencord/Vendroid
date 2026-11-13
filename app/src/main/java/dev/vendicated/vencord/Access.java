package dev.vendicated.vencord;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

/**
 * Who may use the app. The developer hands out access codes (see support/worker.js), each for one person and valid for a
 * set time. Nothing in the app works, and nothing is loaded or injected, until a valid code has been activated; when the
 * time is up the app locks itself again. The relay checks the signature and revocation, the phone remembers the expiry.
 */
final class Access {
    private Access() {}

    enum Problem {INVALID, EXPIRED, REVOKED, DEVICES, OFFLINE, SERVER}

    static final class AccessException extends Exception {
        final Problem problem;

        AccessException(Problem problem, String message) {
            super(message);
            this.problem = problem;
        }
    }

    private static final String P_CODE = "access_code";
    private static final String P_EXP = "access_exp";
    private static final String P_LABEL = "access_label";
    private static final String P_SEEN = "access_seen";
    private static final String P_CHECKED = "access_checked";

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
    }

    /** True while an activated code has not expired. Also false if the phone's clock was set back to stretch the time. */
    static boolean isValid(Context c) {
        var p = prefs(c);
        var code = p.getString(P_CODE, null);
        if (code == null || code.isEmpty()) return false;

        long nowMs = System.currentTimeMillis();
        long seen = p.getLong(P_SEEN, 0);
        if (nowMs < seen - 10 * 60_000L) return false; // clock moved backwards
        if (nowMs / 1000 >= p.getLong(P_EXP, 0)) return false;
        if (nowMs > seen) p.edit().putLong(P_SEEN, nowMs).apply();
        return true;
    }

    static long expiresAt(Context c) {
        return prefs(c).getLong(P_EXP, 0);
    }

    static String label(Context c) {
        return prefs(c).getString(P_LABEL, "");
    }

    /** The stored code, for talking to the relay (reports, re-checks). Null when locked. */
    static Support.Code code(Context c) {
        var raw = prefs(c).getString(P_CODE, null);
        if (raw == null) return null;
        try {
            return Support.parseCode(raw, 0);
        } catch (Support.CodeException ex) {
            return null;
        }
    }

    static void clear(Context c) {
        prefs(c).edit().remove(P_CODE).remove(P_EXP).remove(P_LABEL).remove(P_CHECKED).apply();
    }

    static boolean dueForRecheck(Context c) {
        return System.currentTimeMillis() - prefs(c).getLong(P_CHECKED, 0) > 2 * 60_000L;
    }

    /** Checks the code with the relay and stores it. Blocking: call from a background thread. */
    static void activate(Context c, String input) throws AccessException {
        Support.Code code;
        try {
            code = Support.parseCode(input, System.currentTimeMillis() / 1000);
        } catch (Support.CodeException ex) {
            throw new AccessException(ex.expired ? Problem.EXPIRED : Problem.INVALID, ex.getMessage());
        }

        var result = ask(c, code);
        long exp = result.optLong("exp", code.expiresAt);
        prefs(c).edit()
                .putString(P_CODE, code.raw)
                .putLong(P_EXP, exp)
                .putString(P_LABEL, result.optString("label", ""))
                .putLong(P_SEEN, System.currentTimeMillis())
                .putLong(P_CHECKED, System.currentTimeMillis())
                .apply();
        Logger.i("Access activated, valid until " + exp);
    }

    /**
     * Asks the relay whether the stored code still stands (it may have been revoked). Returns false if the code was
     * rejected (and clears it); network trouble never locks anybody out. Blocking.
     */
    static boolean recheck(Context c) {
        var code = code(c);
        if (code == null) return false;
        try {
            var result = ask(c, code);
            // the developer may have changed the end time from the dashboard
            prefs(c).edit().putLong(P_CHECKED, System.currentTimeMillis())
                    .putLong(P_EXP, result.optLong("exp", prefs(c).getLong(P_EXP, 0))).apply();
            return true;
        } catch (AccessException ex) {
            if (ex.problem == Problem.OFFLINE || ex.problem == Problem.SERVER) return true;
            Logger.w("Access code rejected by the relay: " + ex.getMessage());
            clear(c);
            return false;
        }
    }

    /** A random id for this install (not hardware-based), so the developer can limit devices per code. */
    private static String deviceId(Context c) {
        var p = prefs(c);
        var id = p.getString("device_id", null);
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
            p.edit().putString("device_id", id).apply();
        }
        return id;
    }

    private static JSONObject ask(Context c, Support.Code code) throws AccessException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(code.endpoint + "/activate").openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            byte[] body = ("code=" + URLEncoder.encode(code.raw, "UTF-8") + "&device=" + URLEncoder.encode(deviceId(c), "UTF-8")
                    + "&model=" + URLEncoder.encode(android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL, "UTF-8")).getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }
            int status = conn.getResponseCode();
            if (status == 200) {
                try (InputStream in = conn.getInputStream()) {
                    var text = new String(readAll(in), StandardCharsets.UTF_8);
                    return new JSONObject(text);
                }
            }
            switch (status) {
                case 401:
                    throw new AccessException(Problem.INVALID, "invalid");
                case 410:
                    throw new AccessException(Problem.EXPIRED, "expired");
                case 409:
                    throw new AccessException(Problem.DEVICES, "device limit");
                case 403:
                    throw new AccessException(Problem.REVOKED, "revoked");
                default:
                    throw new AccessException(Problem.SERVER, "HTTP " + status);
            }
        } catch (AccessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new AccessException(Problem.OFFLINE, String.valueOf(ex.getMessage()));
        } catch (Exception ex) {
            throw new AccessException(Problem.SERVER, String.valueOf(ex.getMessage()));
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        var out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > -1) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
