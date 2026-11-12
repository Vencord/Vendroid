package dev.vendicated.vencord;

import android.util.Log;

/** Logs to logcat and mirrors everything into {@link DebugLog} so it shows up in the in-app log panel. */
public final class Logger {
    private static final String TAG = "Vencord";

    public static void e(String message) {
        Log.e(TAG, message);
        DebugLog.add('E', message);
    }
    public static void e(String message, Throwable e) {
        Log.e(TAG, message, e);
        DebugLog.add('E', message + "\n" + DebugLog.describe(e));
    }

    public static void w(String message) {
        Log.w(TAG, message);
        DebugLog.add('W', message);
    }

    public static void i(String message) {
        Log.i(TAG, message);
        DebugLog.add('I', message);
    }

    public static void d(String message) {
        Log.d(TAG, message);
        DebugLog.add('D', message);
    }

    /** Logcat only, not shown in the in-app panel (used for Discord's very chatty console output). */
    public static void logcatOnly(char level, String message) {
        switch (level) {
            case 'E':
                Log.e(TAG, message);
                break;
            case 'W':
                Log.w(TAG, message);
                break;
            case 'D':
                Log.d(TAG, message);
                break;
            default:
                Log.i(TAG, message);
                break;
        }
    }
}
