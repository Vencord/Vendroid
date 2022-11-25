package dev.vendicated.vencord;

import androidx.annotation.NonNull;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.StrictMode;
import android.webkit.WebView;

import java.io.IOException;

public class MainActivity extends Activity {
    private WebView wv;

    @SuppressLint("SetJavaScriptEnabled") // mad? watch this swag
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        var bar = this.getActionBar();
        if (bar != null) bar.hide();

        setContentView(R.layout.activity_main);

        wv = findViewById(R.id.webview);

        explodeAndroid();

        wv.setWebViewClient(new VWebviewClient());
        wv.setWebChromeClient(new VChromeClient());

        var s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        
        try {
            HttpClient.fetchVencord();
        } catch (IOException ex) {
            Logger.e("Failed to fetch Vencord", ex);
            return;
        }

        if (savedInstanceState == null)
            wv.loadUrl("https://discord.com/app");
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        wv.saveState(state);
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        wv.restoreState(state);
    }

    private void explodeAndroid() {
        StrictMode.setThreadPolicy(
                new StrictMode.ThreadPolicy.Builder()
                        // trolley
                        .permitNetwork()
                        .build()
        );
    }
}
