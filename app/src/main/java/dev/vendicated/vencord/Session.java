package dev.vendicated.vencord;

import android.content.Context;
import android.content.MutableContextWrapper;
import android.net.Uri;
import android.webkit.ValueCallback;
import android.webkit.WebView;

/**
 * Everything that has to outlive the activity. The WebView (and with it the Discord connection and Vencord) lives here,
 * so swiping the app away or leaving it doesn't drop the session while background mode is on. The activity only
 * attaches to it while it is on screen.
 */
public final class Session {
    private Session() {}

    static volatile Context appContext;
    static WebView webView;
    static MutableContextWrapper webContext;
    /** The UI currently showing the session, null while the app is in the background without an activity. */
    static volatile MainActivity activity;

    /** The WebView was told to load Discord. */
    static volatile boolean initialized = false;
    /** Vencord's bundle is running inside the page (also true on the login screen). */
    static volatile boolean injected = false;
    /** Logged in and Vencord hooked into the Discord client. */
    static volatile boolean ready = false;
    static volatile boolean pageFinished = false;

    /** A discord link the app was opened with before Vencord finished loading. */
    static Uri pendingUrl;
    static ValueCallback<Uri[]> filePathCallback;

    static void resetPageState() {
        injected = false;
        ready = false;
        pageFinished = false;
    }

    static void onVencordInjected() {
        injected = true;
        var a = activity;
        if (a != null) a.onVencordInjected();
    }

    static void onVencordReady() {
        ready = true;
        var a = activity;
        if (a != null) a.onVencordReady();
    }

    static void onPageFinished() {
        pageFinished = true;
        var a = activity;
        if (a != null) a.onPageFinished();
    }

    static void onMainFrameError() {
        var a = activity;
        if (a != null) a.onMainFrameError();
    }

    static void onLoadProgress(int progress) {
        var a = activity;
        if (a != null) a.onLoadProgress(progress);
    }

    static void showHub() {
        var a = activity;
        if (a != null) a.showHub("report");
    }

    /** Tears the WebView down for good (used when background mode is switched off while no activity is showing it). */
    static void release() {
        var wv = webView;
        webView = null;
        webContext = null;
        initialized = false;
        resetPageState();
        if (wv != null) {
            try {
                wv.destroy();
            } catch (Exception ex) {
                Logger.e("Failed to destroy the WebView", ex);
            }
        }
    }
}
