package dev.vendicated.vencord;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.webkit.*;

import java.util.Locale;

public class VChromeClient extends WebChromeClient {
    @Override
    public boolean onConsoleMessage(ConsoleMessage msg) {
        var m = String.format(Locale.ENGLISH, "[Javascript] %s @ %d: %s", msg.message(), msg.lineNumber(), msg.sourceId());
        char level;
        switch (msg.messageLevel()) {
            case DEBUG:
                level = 'D';
                break;
            case ERROR:
                level = 'E';
                break;
            case WARNING:
                level = 'W';
                break;
            default:
                level = 'I';
                break;
        }

        // Discord is very chatty. Only errors and anything mentioning Vencord end up in the in-app log panel
        var lower = m.toLowerCase(Locale.ROOT);
        boolean mirror = level == 'E' || lower.contains("vencord") || lower.contains("vendroid");
        if (mirror) {
            switch (level) {
                case 'E':
                    Logger.e(m);
                    break;
                case 'W':
                    Logger.w(m);
                    break;
                case 'D':
                    Logger.d(m);
                    break;
                default:
                    Logger.i(m);
                    break;
            }
        } else {
            Logger.logcatOnly(level, m);
        }
        return true;
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        super.onProgressChanged(view, newProgress);
        Session.onLoadProgress(newProgress);
    }

    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
        var activity = Session.activity;
        if (activity == null) return false; // nothing on screen to show a picker in

        if (Session.filePathCallback != null)
            Session.filePathCallback.onReceiveValue(null);

        Session.filePathCallback = filePathCallback;

        var i = fileChooserParams.createIntent();
        try {
            activity.startActivityForResult(i, MainActivity.FILECHOOSER_RESULTCODE);
        } catch (ActivityNotFoundException ex) {
            Session.filePathCallback = null;
            return false;
        }

        return true;
    }
}
