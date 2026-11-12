package dev.vendicated.vencord;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import java.io.IOException;

/**
 * Everything that has to outlive the activity. The WebView (and with it the Discord connection and Vencord) lives here,
 * so swiping the app away, locking the screen or even starting after a reboot works without any screen: the background
 * service creates and loads the session itself, the activity only attaches to it while it is on screen.
 */
public final class Session {
    private Session() {}

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static volatile Context appContext;
    static WebView webView;
    static MutableContextWrapper webContext;
    /** The UI currently showing the session, null while the app is in the background without an activity. */
    static volatile MainActivity activity;

    /** The WebView was told to load Discord. */
    static volatile boolean initialized = false;
    /** Vencord is being downloaded / read from the cache right now. */
    static volatile boolean loading = false;
    /** Vencord's bundle is running inside the page (also true on the login screen). */
    static volatile boolean injected = false;
    /** Logged in and Vencord hooked into the Discord client. */
    static volatile boolean ready = false;
    static volatile boolean pageFinished = false;

    /** A discord link the app was opened with before Vencord finished loading. */
    static Uri pendingUrl;
    static ValueCallback<Uri[]> filePathCallback;

    /** Creates the WebView if there isn't one yet. Main thread only. */
    @SuppressLint("SetJavaScriptEnabled")
    static void ensureWebView(Context context) {
        if (appContext == null) appContext = context.getApplicationContext();
        if (webView != null) return;

        webContext = new MutableContextWrapper(appContext);
        var wv = new WebView(webContext);
        wv.setWebViewClient(new VWebviewClient());
        wv.setWebChromeClient(new VChromeClient());
        var s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        wv.addJavascriptInterface(new VencordNative(), "VencordMobileNative");
        if (Build.VERSION.SDK_INT >= 26) {
            // keep the page's renderer at high priority even with no window, otherwise Android may freeze Discord's
            // heartbeat timers while the screen is off and the connection drops
            wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        }
        webView = wv;
        resetPageState();
        initialized = false;
    }

    /** Called by the background service: brings the whole session up without any screen. */
    static void startHeadless(Context context) {
        if (!Access.isValid(context)) {
            Logger.w("Headless start refused: no valid access code");
            return;
        }
        MAIN.post(() -> {
            ensureWebView(context);
            MessageLog.init(appContext);
            if (!initialized && !loading) {
                Logger.i("Headless start: loading the session without a screen");
                startLoading(appContext);
            }
        });
    }

    /** Fetches Vencord (cache first), then opens Discord. Reports progress to the activity if there is one. */
    static void startLoading(Context context) {
        if (loading || !Access.isValid(context)) return;
        loading = true;
        final var app = context.getApplicationContext();
        new Thread(() -> {
            try {
                HttpClient.fetchVencord(app, percent -> MAIN.post(() -> {
                    var a = activity;
                    if (a != null) a.onDownloadProgress(percent);
                }));
            } catch (IOException ex) {
                Logger.e("Failed to fetch Vencord", ex);
                MAIN.post(() -> {
                    loading = false;
                    var a = activity;
                    if (a != null) a.onLoadFailed();
                });
                return;
            }
            MAIN.post(() -> {
                loading = false;
                loadDiscord();
            });
        }, "vencord-download").start();
    }

    private static void loadDiscord() {
        var wv = webView;
        if (wv == null) return;
        initialized = true;
        resetPageState();
        var url = pendingUrl != null ? pendingUrl.toString() : Constants.DISCORD_URL;
        pendingUrl = null;
        Logger.i("Opening " + url + " in the WebView");
        var a = activity;
        if (a != null) a.onDiscordLoadStarted();
        wv.loadUrl(url);
    }

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
        var ctx = appContext;
        if (ctx != null) {
            // once logged in, background mode may bring the session back by itself after a reboot
            ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit().putBoolean(MainActivity.PREF_EVER_READY, true).apply();
        }
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
        loading = false;
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
