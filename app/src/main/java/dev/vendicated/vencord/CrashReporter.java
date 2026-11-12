package dev.vendicated.vencord;

import android.content.Context;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;

/**
 * Remembers why the app died. The stack trace of an uncaught exception is written to a file; the next start shows it
 * (see MainActivity) so a crash can be reported even when the app can't get as far as showing its normal screen.
 */
final class CrashReporter {
    private CrashReporter() {}

    private static File file(Context c) {
        return new File(c.getFilesDir(), "last-crash.txt");
    }

    static void install(Context context) {
        final var app = context.getApplicationContext();
        final var previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                var sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                var text = "Crash at " + new Date() + " on thread " + thread.getName() + "\n\n" + sw;
                Files.write(file(app).toPath(), text.getBytes(StandardCharsets.UTF_8));
            } catch (Throwable ignored) {
                // nothing more we can do while dying
            }
            if (previous != null) previous.uncaughtException(thread, throwable);
        });
    }

    /** The trace of the previous crash, or null. */
    static String pending(Context context) {
        try {
            var f = file(context);
            if (!f.exists()) return null;
            var text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            return text.length() > 6000 ? text.substring(0, 6000) : text;
        } catch (Throwable ex) {
            return null;
        }
    }

    static void clear(Context context) {
        //noinspection ResultOfMethodCallIgnored
        file(context).delete();
    }
}
