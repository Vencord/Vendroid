package dev.vendicated.vencord;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.KeyEvent;
import android.view.WindowInsets;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.Toast;

import java.io.IOException;
import java.util.Objects;

public class MainActivity extends Activity {
    public static final int FILECHOOSER_RESULTCODE = 8485;
    private boolean wvInitialized = false;
    private WebView wv;
    private String defaultUserAgent;

    public ValueCallback<Uri[]> filePathCallback;

    @SuppressLint("SetJavaScriptEnabled") // mad? watch this swag
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // https://developer.chrome.com/docs/devtools/remote-debugging/webviews/
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);

        setContentView(R.layout.activity_main);

        wv = findViewById(R.id.webview);

        applyWindowInsets();
        explodeAndroid();

        var isDarkMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        wv.setBackgroundColor(isDarkMode ? 0xff313338 : 0xffffffff);

        wv.setWebViewClient(new VWebviewClient());
        wv.setWebChromeClient(new VChromeClient(this));

        var s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);

        defaultUserAgent = s.getUserAgentString();
        applyUserAgent();

        wv.addJavascriptInterface(new VendroidNative(this, wv), "VendroidNative");
        try {
            HttpClient.fetchVencord(this);
        } catch (IOException ex) {
            Logger.e("Failed to fetch Vencord", ex);
            Toast.makeText(this, R.string.vencord_download_failed, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Intent intent = getIntent();
        if (Objects.equals(intent.getAction(), Intent.ACTION_VIEW)) {
            Uri data = intent.getData();
            if (data != null) handleUrl(intent.getData());
        } else {
            wv.loadUrl(DiscordBranch.getWebUrl(this) + "channels/@me");
        }

        wvInitialized = true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && wv != null) {
            runOnUiThread(() -> wv.evaluateJavascript("VencordMobile.onBackPress()", r -> {
                if ("false".equals(r))
                    this.onBackPressed ();
            }));
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode != FILECHOOSER_RESULTCODE || filePathCallback == null)
            return;

        if (resultCode != RESULT_OK || intent == null) {
            filePathCallback.onReceiveValue(null);
        } else {
            Uri[] uris;
            try {
                var clipData = intent.getClipData();
                if (clipData != null) { // multiple items
                    uris = new Uri[clipData.getItemCount()];
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        uris[i] = clipData.getItemAt(i).getUri();
                    }
                } else { // single item
                    uris = new Uri[] { intent.getData() };
                }
            } catch (Exception ex) {
                Logger.e("Error during file upload", ex);
                uris = null;
            }

            filePathCallback.onReceiveValue(uris);
        }
        filePathCallback = null;
    }

    private void applyWindowInsets() {
        var root = findViewById(R.id.root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                var bars = insets.getInsets(
                        WindowInsets.Type.systemBars()
                                | WindowInsets.Type.displayCutout()
                                | WindowInsets.Type.ime()
                );
                left = bars.left;
                top = bars.top;
                right = bars.right;
                bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(left, top, right, bottom);
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ? WindowInsets.CONSUMED
                    : insets.consumeSystemWindowInsets();
        });
    }

    private void explodeAndroid() {
        StrictMode.setThreadPolicy(
                new StrictMode.ThreadPolicy.Builder()
                        // trolley
                        .permitNetwork()
                        .build()
        );
    }

    private void applyUserAgent() {
        var s = wv.getSettings();
        s.setUserAgentString(
                VendroidSettings.isDesktopUiEnabled(this)
                        ? VendroidSettings.desktopUserAgent(defaultUserAgent)
                        : defaultUserAgent
        );
    }

    public void openVendroidSettings() {
        var view = getLayoutInflater().inflate(R.layout.dialog_vendroid_settings, null);

        Switch desktopUiSwitch = view.findViewById(R.id.desktop_ui_switch);
        desktopUiSwitch.setChecked(VendroidSettings.isDesktopUiEnabled(this));
        desktopUiSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            VendroidSettings.setDesktopUiEnabled(this, isChecked);
            applyUserAgent();
            wv.reload();
        });

        RadioGroup branchRadio = view.findViewById(R.id.branch_group);
        branchRadio.check(DiscordBranch.getRadioButtonId(this));

        final var dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.vendroid_settings)
                .setView(view)
                .setPositiveButton(android.R.string.ok, null)
                .show();

        branchRadio.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == DiscordBranch.getRadioButtonId(this)) return;

            DiscordBranch.setFromRadioButtonId(this, checkedId);
            Toast.makeText(this, R.string.switching_discord_branch, Toast.LENGTH_SHORT).show();
            wv.loadUrl(DiscordBranch.getWebUrl(this) + "channels/@me");
            dialog.dismiss();
        });
    }

    public void openUpdaterDialog() {
        VencordUpdaterDialog.show(this, wv::reload, null);
    }

    public void handleUrl(Uri url) {
        if (url != null) {
            if (!Utils.isDiscordUrl(url)) return;
            if (!wvInitialized) {
                wv.loadUrl(url.toString());
            } else {
                wv.evaluateJavascript("Vencord.Webpack.Common.NavigationRouter.transitionTo(\"" + url.getPath() + "\")", null);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri data = intent.getData();
        if (data != null) handleUrl(data);
    }
}
