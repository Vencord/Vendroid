package dev.vendicated.vencord;

import android.util.Log;

public final class Logger {
    private static final String TAG = "Vencord";

    public static void e(String message) {
        Log.e(TAG, message);
    }
    public static void e(String message, Throwable e) {
        Log.e(TAG, message, e);
    }

    public static void w(String message) {
        Log.w(TAG, message);
    }

    public static void i(String message) {
        Log.i(TAG, message);
    }

    public static void d(String message) {
        Log.d(TAG, message);
    }
}
