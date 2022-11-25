package dev.vendicated.vencord;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.webkit.*;

import java.util.Locale;

public class VChromeClient extends WebChromeClient {
    private final MainActivity activity;

    public VChromeClient(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage msg) {
        var m = String.format(Locale.ENGLISH, "[Javascript] %s @ %d: %s", msg.message(), msg.lineNumber(), msg.sourceId());
        switch (msg.messageLevel()) {
            case DEBUG:
                Logger.d(m);
                break;
            case ERROR:
                Logger.e(m);
                break;
            case WARNING:
                Logger.w(m);
                break;
            default:
                Logger.i(m);
                break;
        }
        return true;
    }

    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
        if (activity.filePathCallback != null)
            activity.filePathCallback.onReceiveValue(null);

        activity.filePathCallback = filePathCallback;

        var i = fileChooserParams.createIntent();
        try {
            activity.startActivityForResult(i, MainActivity.FILECHOOSER_RESULTCODE);
        } catch (ActivityNotFoundException ex) {
            activity.filePathCallback = null;
            return false;
        }

        return true;
    }
}
