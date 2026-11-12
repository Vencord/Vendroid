package dev.vendicated.vencord;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent log of deleted / edited messages. Entries are reported by the page (see vencord_mobile.js), written to a
 * small JSON-lines file in the app's private storage and never leave the device.
 */
public final class MessageLog {
    public static final String TYPE_DELETE = "delete";
    public static final String TYPE_EDIT = "edit";
    public static final String TYPE_BULK = "bulk";

    public static final class Entry {
        public String type = TYPE_DELETE;
        public String author = "";
        public String authorId = "";
        public String channel = "";
        public String guild = "";
        public String content = "";
        /** For edits: the text after the edit ({@link #content} is the text before). */
        public String newContent = "";
        public String attachments = "";
        public long time;

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("t", type).put("au", author).put("aid", authorId)
                    .put("chn", channel).put("gn", guild)
                    .put("c", content).put("nc", newContent)
                    .put("att", attachments).put("ts", time);
        }

        String toJsonSafe() {
            try {
                return toJson().toString();
            } catch (JSONException ex) {
                return "{}";
            }
        }

        static Entry fromJson(JSONObject o) {
            var e = new Entry();
            e.type = o.optString("t", TYPE_DELETE);
            e.author = o.optString("au", "");
            e.authorId = o.optString("aid", "");
            e.channel = o.optString("chn", "");
            e.guild = o.optString("gn", "");
            e.content = o.optString("c", "");
            e.newContent = o.optString("nc", "");
            // attachments may arrive as an array of names from the page
            var att = o.opt("att");
            if (att instanceof JSONArray) {
                var arr = (JSONArray) att;
                var sb = new StringBuilder();
                for (int i = 0; i < arr.length(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(arr.optString(i));
                }
                e.attachments = sb.toString();
            } else {
                e.attachments = o.optString("att", "");
            }
            e.time = o.optLong("ts", System.currentTimeMillis());
            return e;
        }
    }

    public interface Listener {
        void onChanged();
    }

    private static final int MAX_ENTRIES = 3000;
    private static final int TRIM_SLACK = 200;
    private static final int MAX_FIELD = 2000;
    private static final Object LOCK = new Object();
    private static final ArrayList<Entry> ENTRIES = new ArrayList<>();
    private static File file;
    private static boolean loaded = false;
    private static volatile Listener listener;

    private MessageLog() {}

    public static void setListener(Listener l) {
        listener = l;
    }

    public static void init(Context context) {
        synchronized (LOCK) {
            if (loaded) return;
            file = new File(context.getFilesDir(), "message-log.jsonl");
            loaded = true;
            if (!file.isFile()) return;
            try (var reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    try {
                        ENTRIES.add(Entry.fromJson(new JSONObject(line)));
                    } catch (JSONException ignored) {
                        // skip a damaged line
                    }
                }
            } catch (IOException ex) {
                Logger.e("Could not read the message log", ex);
            }
            trimLocked();
            Logger.i("Message log loaded: " + ENTRIES.size() + " entries");
        }
    }

    /** Called from the JS bridge thread with one JSON event. */
    public static void add(String json) {
        Entry e;
        try {
            e = Entry.fromJson(new JSONObject(json));
        } catch (JSONException ex) {
            Logger.w("Ignoring a malformed message event: " + ex.getMessage());
            return;
        }
        e.content = cut(e.content);
        e.newContent = cut(e.newContent);

        synchronized (LOCK) {
            ENTRIES.add(e);
            try {
                if (file != null) {
                    try (var os = new FileOutputStream(file, true)) {
                        os.write((e.toJsonSafe() + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                }
            } catch (IOException ex) {
                Logger.e("Could not write the message log", ex);
            }
            if (ENTRIES.size() > MAX_ENTRIES + TRIM_SLACK) {
                trimLocked();
                rewriteLocked();
            }
        }
        var l = listener;
        if (l != null) l.onChanged();
    }

    /** Newest first. {@code type} null = everything, TYPE_DELETE also includes bulk deletes. */
    public static List<Entry> snapshot(String type) {
        var out = new ArrayList<Entry>();
        synchronized (LOCK) {
            for (int i = ENTRIES.size() - 1; i >= 0; i--) {
                var e = ENTRIES.get(i);
                if (type == null
                        || (TYPE_DELETE.equals(type) && (TYPE_DELETE.equals(e.type) || TYPE_BULK.equals(e.type)))
                        || type.equals(e.type)) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    public static int count() {
        synchronized (LOCK) {
            return ENTRIES.size();
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            ENTRIES.clear();
            if (file != null && file.isFile() && !file.delete()) Logger.w("Could not delete the message log file");
        }
        var l = listener;
        if (l != null) l.onChanged();
    }

    private static String cut(String s) {
        if (s == null) return "";
        return s.length() > MAX_FIELD ? s.substring(0, MAX_FIELD) + "…" : s;
    }

    private static void trimLocked() {
        while (ENTRIES.size() > MAX_ENTRIES) ENTRIES.remove(0);
    }

    private static void rewriteLocked() {
        if (file == null) return;
        try (var os = new FileOutputStream(file, false)) {
            for (var e : ENTRIES) os.write((e.toJsonSafe() + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            Logger.e("Could not rewrite the message log", ex);
        }
    }
}
