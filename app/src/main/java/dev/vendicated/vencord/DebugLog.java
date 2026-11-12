package dev.vendicated.vencord;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * In-memory, thread-safe ring buffer with every step Vendroid takes (download, injection, page load, JS errors).
 * It is shown in the in-app log panel (the "LOG" button) so problems can be read, copied and shared straight from the phone.
 */
public final class DebugLog {
    public interface Listener {
        /** Called on whatever thread produced the line. Must be cheap. */
        void onChanged();
    }

    private static final int MAX_LINES = 800;
    private static final int MAX_LINE_LENGTH = 3000;
    private static final long START = System.currentTimeMillis();

    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static volatile Listener listener;

    private static final Pattern URL_QUERY = Pattern.compile("(https?://[^\\s?#\"']+)\\?[^\\s\"']*");
    private static final Pattern TOKEN = Pattern.compile(
            "mfa\\.[A-Za-z0-9_-]{20,}|[A-Za-z0-9_-]{23,28}\\.[A-Za-z0-9_-]{6,7}\\.[A-Za-z0-9_-]{27,}");
    private static final Pattern LONG_ID = Pattern.compile("\\b\\d{17,20}\\b");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private DebugLog() {}

    /**
     * Strips anything that could identify the user or grant access before a log leaves the device: URL query strings
     * (signed links), Discord-token-shaped strings, snowflake ids and e-mail addresses.
     */
    public static String sanitize(String text) {
        if (text == null) return "";
        text = URL_QUERY.matcher(text).replaceAll("$1?<query removed>");
        text = TOKEN.matcher(text).replaceAll("<token removed>");
        text = LONG_ID.matcher(text).replaceAll("<id>");
        text = EMAIL.matcher(text).replaceAll("<email>");
        return text;
    }

    /** @param level one of E (error), W (warning), I (info), D (debug) */
    public static void add(char level, String message) {
        if (message == null) message = "null";
        if (message.length() > MAX_LINE_LENGTH) message = message.substring(0, MAX_LINE_LENGTH) + "… (cut)";

        var line = String.format(Locale.ENGLISH, "%7.2fs %c %s", (System.currentTimeMillis() - START) / 1000.0, level, message);
        synchronized (LINES) {
            if (LINES.size() >= MAX_LINES) LINES.pollFirst();
            LINES.addLast(line);
        }

        var l = listener;
        if (l != null) l.onChanged();
    }

    public static String dump() {
        var sb = new StringBuilder();
        synchronized (LINES) {
            for (var line : LINES) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    public static void clear() {
        synchronized (LINES) {
            LINES.clear();
        }
        var l = listener;
        if (l != null) l.onChanged();
    }

    public static void setListener(Listener l) {
        listener = l;
    }

    /** One-line summary of a throwable including its cause chain and the first few stack frames. */
    public static String describe(Throwable t) {
        var sb = new StringBuilder();
        int depth = 0;
        for (Throwable c = t; c != null && depth < 4; c = c.getCause(), depth++) {
            if (depth > 0) sb.append("\n  caused by: ");
            sb.append(c.getClass().getName()).append(": ").append(c.getMessage());
            var st = c.getStackTrace();
            for (int i = 0; i < Math.min(3, st.length); i++) sb.append("\n    at ").append(st[i]);
        }
        return sb.toString();
    }
}
