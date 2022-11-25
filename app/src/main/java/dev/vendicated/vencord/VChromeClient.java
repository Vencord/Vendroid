package dev.vendicated.vencord;

import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;

import java.util.Locale;

public class VChromeClient extends WebChromeClient {
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
}
