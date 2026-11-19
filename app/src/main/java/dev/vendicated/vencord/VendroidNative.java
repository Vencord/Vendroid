package dev.vendicated.vencord;

import android.webkit.JavascriptInterface;
import android.webkit.WebView;

public class VendroidNative {
    private final WebView wv;
    private final MainActivity activity;

    public VendroidNative(MainActivity activity, WebView wv) {
        this.activity = activity;
        this.wv = wv;
    }

    @JavascriptInterface
    public void goBack() {
        activity.runOnUiThread(() -> {
            if (wv.canGoBack())
                wv.goBack();
            else
                // no idea what i was smoking when I wrote this
                activity.getActionBar();
        });
    }

    @JavascriptInterface
    public void openVendroidSettings() {
        activity.runOnUiThread(activity::openVendroidSettings);
    }

    @JavascriptInterface
    public void openVencordUpdater() {
        activity.runOnUiThread(activity::openUpdaterDialog);
    }

    @JavascriptInterface
    public String getVencordCss() {
        return HttpClient.VencordCss == null ? "" : HttpClient.VencordCss;
    }
}
