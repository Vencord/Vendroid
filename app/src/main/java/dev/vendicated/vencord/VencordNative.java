package dev.vendicated.vencord;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** The object the page sees as {@code window.VencordMobileNative}. Independent of the activity, so it works in the background too. */
public class VencordNative {
    private static final Set<String> OPENABLE_HOSTS = new HashSet<>(Arrays.asList(
            "www.tiktok.com", "tiktok.com", "vm.tiktok.com", "www.instagram.com", "instagram.com"));

    private final Handler ui = new Handler(Looper.getMainLooper());

    @JavascriptInterface
    public void goBack() {
        ui.post(() -> {
            var wv = Session.webView;
            if (wv != null && wv.canGoBack()) wv.goBack();
        });
    }

    /** Lets the page report what it is doing / what went wrong; it ends up in the log attached to bug reports. */
    @JavascriptInterface
    public void log(String level, String message) {
        char lv = level != null && !level.isEmpty() ? Character.toUpperCase(level.charAt(0)) : 'I';
        if (lv != 'E' && lv != 'W' && lv != 'D') lv = 'I';
        if (message == null) message = "";
        if (message.length() > 2000) message = message.substring(0, 2000) + "…";
        switch (lv) {
            case 'E':
                Logger.e("[page] " + message);
                break;
            case 'W':
                Logger.w("[page] " + message);
                break;
            case 'D':
                Logger.d("[page] " + message);
                break;
            default:
                Logger.i("[page] " + message);
                break;
        }
    }

    /** A deleted / edited message seen by the page (JSON, see vencord_mobile.js). Stored on the device only. */
    @JavascriptInterface
    public void messageEvent(String json) {
        if (json == null || json.length() > 20_000) return;
        MessageLog.add(json);
    }

    /** Opens the developer's TikTok / Instagram pages (and nothing else) in the matching app or browser. */
    @JavascriptInterface
    public void openUrl(String url) {
        try {
            var uri = Uri.parse(url);
            var host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equals(uri.getScheme()) || !OPENABLE_HOSTS.contains(host)) {
                Logger.w("Refused to open " + url);
                return;
            }
            var intent = new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            var ctx = Session.appContext;
            if (ctx != null) ctx.startActivity(intent);
        } catch (Exception ex) {
            Logger.w("openUrl failed: " + ex.getMessage());
        }
    }

    @JavascriptInterface
    public void copyText(String text) {
        ui.post(() -> {
            var ctx = Session.appContext;
            if (ctx == null || text == null) return;
            var cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("FyoCord", text));
                Toast.makeText(ctx, ctx.getString(R.string.copied_username, text), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** A message that should notify the user (JSON: id, title, body, url), see vencord_mobile.js. */
    @JavascriptInterface
    public void notify(String json) {
        var ctx = Session.appContext;
        if (ctx != null && json != null && json.length() < 20_000) MessageNotifier.show(ctx, json);
    }

    @JavascriptInterface
    public void toast(String message) {
        ui.post(() -> {
            var ctx = Session.appContext;
            if (ctx != null && message != null) Toast.makeText(ctx, message, Toast.LENGTH_LONG).show();
        });
    }

    /** Called by vencord_mobile.js as soon as Vencord's bundle is running in the page. */
    @JavascriptInterface
    public void onVencordInjected() {
        Session.onVencordInjected();
    }

    /** Called by vencord_mobile.js once the user is logged in and Vencord hooked into Discord's client. */
    @JavascriptInterface
    public void onVencordReady() {
        Session.onVencordReady();
    }
}
