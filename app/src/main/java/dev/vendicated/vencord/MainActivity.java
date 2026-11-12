package dev.vendicated.vencord;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.MutableContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.webkit.WebView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    public static final int FILECHOOSER_RESULTCODE = 8485;
    private static final int SAVE_LOG_RESULTCODE = 8486;
    private static final int NOTIFICATION_PERMISSION_CODE = 8487;

    public static final String PREFS = "fyocord";
    public static final String PREF_BACKGROUND = "background_mode";
    public static final String PREF_WAKELOCK = "wake_lock";
    private static final String PREF_FAB_X = "fab_x";
    private static final String PREF_FAB_Y = "fab_y";
    private static final String PREF_CONTACT = "report_contact";
    private static final String PREF_CODE = "report_code";
    private static final String PREF_ASKED = "setup_asked";
    public static final String PREF_EVER_READY = "ever_ready";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private boolean destroyed = false;

    // loading screen
    private View loadingOverlay;
    private View logoText;
    private PixelBarView progressBar;
    private TextView statusText;
    private Button retryButton;
    private boolean overlayVisible = true;
    private ObjectAnimator indeterminateAnimator;
    private ObjectAnimator pulseAnimator;

    // floating button
    private View fab;
    private boolean fabAlert = false;

    // hub
    private View hubPanel;
    private View pageMessages, pageReport, pageAbout;
    private TextView tabMessages, tabReport, tabAbout;
    private TextView chipAll, chipDeleted, chipEdited;
    private TextView msgSummary, msgEmpty;
    private ListView msgList;
    private final ArrayList<MessageLog.Entry> shown = new ArrayList<>();
    private String filter = null; // null = all
    private EntryAdapter adapter;
    private EditText reportDescription, reportContact, reportCode;
    private TextView reportStatus;
    private Button reportSend, reportSave;
    private Button aboutBackground, aboutWakelock;
    private String pendingSaveContent;
    private boolean sending = false;

    private final Runnable readyTimeout = () -> {
        if (Session.injected || destroyed) return;
        Logger.e("FyoCord did not report in " + (Constants.READY_TIMEOUT_MS / 1000) + "s after the page finished loading (onVencordInjected never called).");
        Toast.makeText(this, R.string.vencord_failed_toast, Toast.LENGTH_LONG).show();
        setFabAlert(true);
        hideOverlay();
        runDiagnostics(null);
    };

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CrashReporter.install(this);
        var crash = CrashReporter.pending(this);
        if (crash != null) {
            showCrashScreen(crash);
            return;
        }

        // https://developer.chrome.com/docs/devtools/remote-debugging/webviews/
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);

        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        Session.appContext = getApplicationContext();
        Session.activity = this;
        MessageLog.init(getApplicationContext());

        bindLoadingViews();
        bindFab();
        bindHub();

        Logger.i("FyoCord started. Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + "), "
                + Build.MANUFACTURER + " " + Build.MODEL + ", WebView: " + webViewVersion()
                + ", debug build: " + BuildConfig.DEBUG);

        if (prefs.getBoolean(PREF_BACKGROUND, true)) KeepAliveService.start(this);

        attachWebView();

        Intent intent = getIntent();
        if (Objects.equals(intent.getAction(), Intent.ACTION_VIEW)) {
            handleUrl(intent.getData());
        }

        if (Session.initialized) {
            // reattached to a session that kept running in the background
            Logger.i("Reattached to the running session (injected=" + Session.injected + ", ready=" + Session.ready + ")");
            syncOverlayWithSession();
        } else if (Session.loading) {
            // the background service is already loading it, just show the progress
            showOverlay();
            setStatus(R.string.status_downloading, 0);
        } else {
            startLoading();
        }

        askSetupOnce();
    }

    /** Shown instead of the app when the previous run crashed, so the cause can be copied / sent instead of lost. */
    private void showCrashScreen(String trace) {
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * d);
        var column = new android.widget.LinearLayout(this);
        column.setOrientation(android.widget.LinearLayout.VERTICAL);
        column.setBackgroundColor(Color.parseColor("#050505"));
        column.setPadding(pad, pad * 2, pad, pad);

        var title = new TextView(this);
        title.setText("FyoCord crashed last time");
        title.setTextColor(Color.parseColor("#FF5C7A"));
        title.setTextSize(20);
        column.addView(title);

        var scroll = new android.widget.ScrollView(this);
        var body = new TextView(this);
        body.setText(trace);
        body.setTextColor(Color.parseColor("#D0D0D0"));
        body.setTextSize(11);
        body.setTypeface(android.graphics.Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        body.setPadding(0, pad, 0, pad);
        scroll.addView(body);
        column.addView(scroll, new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        var row = new android.widget.LinearLayout(this);
        var copy = new Button(this);
        copy.setText("Copy");
        copy.setOnClickListener(v -> copyToClipboard(trace));
        var share = new Button(this);
        share.setText("Share");
        share.setOnClickListener(v -> startActivity(Intent.createChooser(
                new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, trace), "FyoCord crash")));
        var retry = new Button(this);
        retry.setText("Try again");
        retry.setOnClickListener(v -> {
            CrashReporter.clear(this);
            recreate();
        });
        var lp = new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(copy, lp);
        row.addView(share, lp);
        row.addView(retry, lp);
        column.addView(row);
        setContentView(column);
    }

    // ------------------------------------------------------------------ WebView / session

    private void attachWebView() {
        var container = (FrameLayout) findViewById(R.id.web_container);
        Session.ensureWebView(this);
        Session.webContext.setBaseContext(this);

        var wv = Session.webView;
        var parent = wv.getParent();
        if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(wv);
        container.addView(wv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fab.bringToFront(); // the page must never end up on top of the floating button
    }

    private void detachWebView() {
        var wv = Session.webView;
        if (wv == null) return;
        var parent = wv.getParent();
        if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(wv);
        if (Session.webContext != null && Session.appContext != null) Session.webContext.setBaseContext(Session.appContext);
    }

    /** Shows the loading screen and (re)starts the shared session loading. Also used by the retry button. */
    private void startLoading() {
        retryButton.setVisibility(View.GONE);
        setFabAlert(false);
        showOverlay();
        setStatus(R.string.status_downloading, 0);
        Logger.i("Start loading (get FyoCord, then open Discord)");
        Session.startLoading(getApplicationContext());
    }

    // progress of the shared session loading (see Session)

    void onDownloadProgress(int percent) {
        runOnUiThread(() -> {
            if (!destroyed) setStatus(R.string.status_downloading, percent);
        });
    }

    void onDiscordLoadStarted() {
        runOnUiThread(() -> {
            if (!destroyed) setStatus(R.string.status_loading_discord, 0);
        });
    }

    void onLoadFailed() {
        runOnUiThread(() -> {
            if (destroyed) return;
            showError(R.string.status_download_failed);
            setFabAlert(true);
        });
    }

    private void syncOverlayWithSession() {
        if (Session.pageFinished || Session.ready || Session.injected) {
            overlayVisible = true; // make hideOverlay do its work
            hideOverlay();
        } else {
            showOverlay();
            setStatus(R.string.status_loading_discord, Session.webView != null ? Session.webView.getProgress() : 0);
        }
    }

    // callbacks from Session (always on the UI thread? not guaranteed, so post)

    void onVencordInjected() {
        runOnUiThread(() -> {
            if (destroyed) return;
            handler.removeCallbacks(readyTimeout);
            setStatus(R.string.status_starting_vencord, 100);
            hideOverlay();
        });
    }

    void onVencordReady() {
        runOnUiThread(() -> {
            if (destroyed) return;
            handler.removeCallbacks(readyTimeout);
            hideOverlay();
            if (Session.pendingUrl != null) {
                var u = Session.pendingUrl;
                Session.pendingUrl = null;
                handleUrl(u);
            }
        });
    }

    void onPageFinished() {
        runOnUiThread(() -> {
            if (destroyed) return;
            handler.removeCallbacks(readyTimeout);
            if (!Session.injected) handler.postDelayed(readyTimeout, Constants.READY_TIMEOUT_MS);
        });
    }

    void onMainFrameError() {
        runOnUiThread(() -> {
            if (destroyed) return;
            handler.removeCallbacks(readyTimeout);
            showError(R.string.status_load_failed);
            setFabAlert(true);
        });
    }

    void onLoadProgress(int progress) {
        runOnUiThread(() -> {
            if (destroyed || !overlayVisible || !Session.initialized || retryButton.getVisibility() == View.VISIBLE) return;
            setStatus(R.string.status_loading_discord, progress);
        });
    }

    /** Called by the background service when "Stop" is tapped in its notification. */
    void onBackgroundModeChanged() {
        runOnUiThread(() -> {
            if (!destroyed) updateSettingsButtons();
        });
    }

    // ------------------------------------------------------------------ loading screen

    private void bindLoadingViews() {
        loadingOverlay = findViewById(R.id.loading_overlay);
        logoText = findViewById(R.id.logo_text);
        progressBar = findViewById(R.id.progress_bar);
        statusText = findViewById(R.id.status_text);
        retryButton = findViewById(R.id.retry_button);
        retryButton.setOnClickListener(v -> startLoading());

        pulseAnimator = ObjectAnimator.ofFloat(findViewById(R.id.logo_image), "alpha", 1f, 0.7f);
        pulseAnimator.setDuration(1100);
        pulseAnimator.setRepeatMode(ObjectAnimator.REVERSE);
        pulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseAnimator.start();
    }

    /** @param percent 0-100, or negative for "unknown" (the bar sweeps back and forth) */
    private void setStatus(int statusRes, int percent) {
        if (percent < 0) {
            statusText.setText(getString(statusRes).replace("%1$d%%", "").trim());
            if (indeterminateAnimator == null) {
                indeterminateAnimator = ObjectAnimator.ofInt(progressBar, "progress", 5, 95);
                indeterminateAnimator.setDuration(1200);
                indeterminateAnimator.setRepeatMode(ObjectAnimator.REVERSE);
                indeterminateAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            }
            if (!indeterminateAnimator.isStarted()) indeterminateAnimator.start();
        } else {
            stopIndeterminate();
            statusText.setText(getString(statusRes, percent));
            progressBar.setProgress(percent);
        }
    }

    private void stopIndeterminate() {
        if (indeterminateAnimator != null) indeterminateAnimator.cancel();
    }

    private void showError(int messageRes) {
        if (destroyed) return;
        showOverlay();
        stopIndeterminate();
        statusText.setText(messageRes);
        progressBar.setProgress(0);
        retryButton.setVisibility(View.VISIBLE);
    }

    private void showOverlay() {
        overlayVisible = true;
        loadingOverlay.animate().cancel();
        loadingOverlay.setAlpha(1f);
        loadingOverlay.setVisibility(View.VISIBLE);
        if (pulseAnimator != null && !pulseAnimator.isStarted()) pulseAnimator.start();
    }

    private void hideOverlay() {
        if (!overlayVisible) return;
        overlayVisible = false;
        handler.removeCallbacks(readyTimeout);
        stopIndeterminate();
        loadingOverlay.animate().alpha(0f).setDuration(250)
                .withEndAction(() -> {
                    if (!overlayVisible) loadingOverlay.setVisibility(View.GONE);
                })
                .start();
    }

    // ------------------------------------------------------------------ floating button

    @SuppressLint("ClickableViewAccessibility")
    private void bindFab() {
        fab = findViewById(R.id.fab);
        final View root = findViewById(R.id.root);
        final float density = getResources().getDisplayMetrics().density;
        final float slop = 8 * density;

        fab.setOnTouchListener(new View.OnTouchListener() {
            float downRawX, downRawY, startTx, startTy;
            boolean dragging;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = e.getRawX();
                        downRawY = e.getRawY();
                        startTx = v.getTranslationX();
                        startTy = v.getTranslationY();
                        dragging = false;
                        v.setAlpha(1f);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = e.getRawX() - downRawX, dy = e.getRawY() - downRawY;
                        if (!dragging && Math.hypot(dx, dy) > slop) dragging = true;
                        if (dragging) {
                            v.setTranslationX(clamp(startTx + dx, 0, Math.max(0, root.getWidth() - v.getWidth())));
                            v.setTranslationY(clamp(startTy + dy, 0, Math.max(0, root.getHeight() - v.getHeight())));
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        v.setAlpha(0.85f);
                        if (dragging) {
                            snapFabToEdge(root);
                        } else {
                            v.performClick();
                            showHub(fabAlert ? "report" : "messages");
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        v.setAlpha(0.85f);
                        if (dragging) snapFabToEdge(root);
                        return true;
                    default:
                        return false;
                }
            }
        });

        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) root.post(this::placeFab);
        });
        root.post(this::placeFab);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private void placeFab() {
        if (destroyed) return;
        var root = findViewById(R.id.root);
        float fx = prefs.getFloat(PREF_FAB_X, 1f);
        float fy = prefs.getFloat(PREF_FAB_Y, 0.55f);
        fab.setTranslationX(clamp(fx, 0, 1) * Math.max(0, root.getWidth() - fab.getWidth()));
        fab.setTranslationY(clamp(fy, 0, 1) * Math.max(0, root.getHeight() - fab.getHeight()));
    }

    private void snapFabToEdge(View root) {
        float maxX = Math.max(1, root.getWidth() - fab.getWidth());
        float maxY = Math.max(1, root.getHeight() - fab.getHeight());
        float fx = fab.getTranslationX() > maxX / 2 ? 1f : 0f;
        float fy = clamp(fab.getTranslationY() / maxY, 0, 1);
        prefs.edit().putFloat(PREF_FAB_X, fx).putFloat(PREF_FAB_Y, fy).apply();
        fab.animate().translationX(fx * maxX).setDuration(180).start();
    }

    private void setFabAlert(boolean alert) {
        fabAlert = alert;
        if (fab != null) fab.setBackgroundResource(alert ? R.drawable.bg_fab_alert : R.drawable.bg_fab);
    }

    // ------------------------------------------------------------------ hub

    private void bindHub() {
        hubPanel = findViewById(R.id.hub_panel);
        pageMessages = findViewById(R.id.page_messages);
        pageReport = findViewById(R.id.page_report);
        pageAbout = findViewById(R.id.page_about);
        tabMessages = findViewById(R.id.tab_messages);
        tabReport = findViewById(R.id.tab_report);
        tabAbout = findViewById(R.id.tab_about);

        findViewById(R.id.hub_close).setOnClickListener(v -> hideHub());
        hubPanel.setOnClickListener(v -> { /* swallow taps outside the card */ });
        tabMessages.setOnClickListener(v -> selectTab("messages"));
        tabReport.setOnClickListener(v -> selectTab("report"));
        tabAbout.setOnClickListener(v -> selectTab("about"));

        bindMessages();
        bindReport();
        bindAbout();
    }

    void showHub(String tab) {
        runOnUiThread(() -> {
            if (destroyed) return;
            // the keyboard must be able to push the report form up, but only while this panel is open
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            hubPanel.setVisibility(View.VISIBLE);
            selectTab(tab);
        });
    }

    private void hideHub() {
        hideKeyboard();
        hubPanel.setVisibility(View.GONE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
    }

    private boolean hubOpen() {
        return hubPanel != null && hubPanel.getVisibility() == View.VISIBLE;
    }

    private void hideKeyboard() {
        var v = getCurrentFocus();
        if (v == null) return;
        var imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private void selectTab(String tab) {
        pageMessages.setVisibility("messages".equals(tab) ? View.VISIBLE : View.GONE);
        pageReport.setVisibility("report".equals(tab) ? View.VISIBLE : View.GONE);
        pageAbout.setVisibility("about".equals(tab) ? View.VISIBLE : View.GONE);
        styleSelectable(tabMessages, "messages".equals(tab), R.drawable.bg_tab_selected, R.drawable.bg_tab);
        styleSelectable(tabReport, "report".equals(tab), R.drawable.bg_tab_selected, R.drawable.bg_tab);
        styleSelectable(tabAbout, "about".equals(tab), R.drawable.bg_tab_selected, R.drawable.bg_tab);
        if ("messages".equals(tab)) refreshMessages();
        if ("about".equals(tab)) updateSettingsButtons();
    }

    // ---- messages tab

    private void bindMessages() {
        chipAll = findViewById(R.id.chip_all);
        chipDeleted = findViewById(R.id.chip_deleted);
        chipEdited = findViewById(R.id.chip_edited);
        msgSummary = findViewById(R.id.msg_summary);
        msgEmpty = findViewById(R.id.msg_empty);
        msgList = findViewById(R.id.msg_list);
        adapter = new EntryAdapter();
        msgList.setAdapter(adapter);

        chipAll.setOnClickListener(v -> setFilter(null));
        chipDeleted.setOnClickListener(v -> setFilter(MessageLog.TYPE_DELETE));
        chipEdited.setOnClickListener(v -> setFilter(MessageLog.TYPE_EDIT));
        findViewById(R.id.msg_clear).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage(R.string.clear)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    MessageLog.clear();
                    refreshMessages();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());

        MessageLog.setListener(() -> runOnUiThread(() -> {
            if (!destroyed && hubOpen() && pageMessages.getVisibility() == View.VISIBLE) refreshMessages();
        }));
        styleChips();
    }

    private void setFilter(String type) {
        filter = type;
        styleChips();
        refreshMessages();
    }

    private void styleChips() {
        styleSelectable(chipAll, filter == null, R.drawable.bg_chip_selected, R.drawable.bg_chip);
        styleSelectable(chipDeleted, MessageLog.TYPE_DELETE.equals(filter), R.drawable.bg_chip_selected, R.drawable.bg_chip);
        styleSelectable(chipEdited, MessageLog.TYPE_EDIT.equals(filter), R.drawable.bg_chip_selected, R.drawable.bg_chip);
    }

    /** White block with black text when selected, outlined with white text otherwise (the pixel look). */
    private void styleSelectable(TextView v, boolean selected, int selectedBg, int normalBg) {
        v.setBackgroundResource(selected ? selectedBg : normalBg);
        v.setTextColor(selected ? Color.BLACK : Color.WHITE);
    }

    private void refreshMessages() {
        shown.clear();
        shown.addAll(MessageLog.snapshot(filter));
        adapter.notifyDataSetChanged();
        msgEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        msgSummary.setText(getString(R.string.messages_summary, MessageLog.count()));
    }

    private final class EntryAdapter extends BaseAdapter {
        private final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            var e = shown.get(position);
            float d = getResources().getDisplayMetrics().density;

            var box = new android.widget.LinearLayout(MainActivity.this);
            box.setOrientation(android.widget.LinearLayout.VERTICAL);
            box.setBackgroundResource(R.drawable.bg_entry);
            int pad = (int) (12 * d);
            box.setPadding(pad, pad, pad, pad);

            boolean edit = MessageLog.TYPE_EDIT.equals(e.type);
            int typeRes = edit ? R.string.type_edited : MessageLog.TYPE_BULK.equals(e.type) ? R.string.type_bulk : R.string.type_deleted;

            var head = new TextView(MainActivity.this);
            head.setTextSize(12);
            head.setTextColor(Color.parseColor(edit ? "#FFB84D" : "#FF5C7A"));
            head.setText(getString(typeRes) + "  ·  " + dateFormat.format(new Date(e.time)));
            box.addView(head);

            var who = new TextView(MainActivity.this);
            who.setTextSize(14);
            who.setTextColor(Color.parseColor("#FFFFFF"));
            who.setTypeface(who.getTypeface(), android.graphics.Typeface.BOLD);
            var place = new StringBuilder(e.author.isEmpty() ? "?" : e.author);
            if (!e.channel.isEmpty()) place.append("  ›  ").append(e.guild.isEmpty() ? "" : e.guild + " / ").append(e.channel);
            who.setText(place.toString());
            who.setPadding(0, (int) (4 * d), 0, (int) (4 * d));
            box.addView(who);

            var body = new TextView(MainActivity.this);
            body.setTextSize(14);
            body.setTextColor(Color.parseColor("#D0D0D0"));
            body.setTextIsSelectable(false);
            var sb = new StringBuilder();
            if (edit) {
                sb.append(getString(R.string.edit_before)).append(": ").append(e.content)
                        .append("\n").append(getString(R.string.edit_after)).append(": ").append(e.newContent);
            } else {
                sb.append(e.content);
            }
            if (!e.attachments.isEmpty()) sb.append("\n📎 ").append(e.attachments);
            body.setText(sb.toString());
            box.addView(body);
            return box;
        }
    }

    // ---- report tab

    private void bindReport() {
        reportDescription = findViewById(R.id.report_description);
        reportContact = findViewById(R.id.report_contact);
        reportCode = findViewById(R.id.report_code);
        reportStatus = findViewById(R.id.report_status);
        reportSend = findViewById(R.id.report_send);
        reportSave = findViewById(R.id.report_save);

        reportContact.setText(prefs.getString(PREF_CONTACT, ""));
        reportCode.setText(prefs.getString(PREF_CODE, ""));

        reportSend.setOnClickListener(v -> sendReport());
        reportSave.setOnClickListener(v -> saveLogFile());
    }

    private void setReportStatus(CharSequence text, boolean error) {
        reportStatus.setVisibility(View.VISIBLE);
        reportStatus.setText(text);
        reportStatus.setTextColor(Color.parseColor(error ? "#FF5C7A" : "#3DDC97"));
    }

    private void sendReport() {
        if (sending) return;
        var description = reportDescription.getText().toString().trim();
        var contact = reportContact.getText().toString().trim();
        var codeText = reportCode.getText().toString().trim();

        if (description.length() < 5) {
            setReportStatus(getString(R.string.report_err_description), true);
            return;
        }
        if (codeText.isEmpty()) {
            setReportStatus(getString(R.string.report_err_code), true);
            return;
        }
        final Support.Code code;
        try {
            code = Support.parseCode(codeText, System.currentTimeMillis() / 1000);
        } catch (Support.CodeException ex) {
            setReportStatus(getString(ex.expired ? R.string.report_err_code_expired : R.string.report_err_code_invalid), true);
            return;
        }

        prefs.edit().putString(PREF_CONTACT, contact).putString(PREF_CODE, codeText).apply();
        sending = true;
        reportSend.setEnabled(false);
        setReportStatus(getString(R.string.report_sending), false);
        hideKeyboard();

        runDiagnostics(() -> {
            var meta = buildMeta(contact);
            var logFile = Support.buildLogFile(meta, DebugLog.sanitize(DebugLog.dump()));
            new Thread(() -> {
                String error = null;
                try {
                    Support.send(code, description, contact, meta, logFile);
                } catch (IOException ex) {
                    error = ex.getMessage() == null ? "error" : ex.getMessage();
                    Logger.w("Report not sent: " + error);
                }
                final String err = error;
                runOnUiThread(() -> {
                    sending = false;
                    if (destroyed) return;
                    reportSend.setEnabled(true);
                    if (err == null) {
                        setReportStatus(getString(R.string.report_sent), false);
                        reportDescription.setText("");
                    } else {
                        setReportStatus(getString(R.string.report_failed, err), true);
                    }
                });
            }, "report-send").start();
        });
    }

    private String buildMeta(String contact) {
        var sb = new StringBuilder();
        sb.append("FyoCord ").append(BuildConfig.VERSION_NAME).append(BuildConfig.DEBUG ? " (debug)" : "").append('\n');
        sb.append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append("), ")
                .append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("WebView: ").append(webViewVersion()).append('\n');
        sb.append("Locale: ").append(Locale.getDefault()).append('\n');
        sb.append("State: initialized=").append(Session.initialized).append(", pageFinished=").append(Session.pageFinished)
                .append(", injected=").append(Session.injected).append(", ready=").append(Session.ready).append('\n');
        sb.append("Background mode: ").append(prefs.getBoolean(PREF_BACKGROUND, true))
                .append(", wake lock: ").append(prefs.getBoolean(PREF_WAKELOCK, true)).append('\n');
        sb.append("Logged messages stored: ").append(MessageLog.count()).append(" (contents are never sent)\n");
        sb.append("Contact: ").append(DebugLog.sanitize(contact)).append('\n');
        sb.append("Time: ").append(new Date()).append('\n');
        return sb.toString();
    }

    private void saveLogFile() {
        pendingSaveContent = Support.buildLogFile(buildMeta(reportContact.getText().toString().trim()), DebugLog.sanitize(DebugLog.dump()));
        var intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TITLE, "fyocord-log.txt");
        try {
            startActivityForResult(intent, SAVE_LOG_RESULTCODE);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, R.string.log_save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    // ---- about / contact / settings tab

    private void bindAbout() {
        ((TextView) findViewById(R.id.about_dev_name)).setText(Support.DEV_NAME);

        var tiktok = (Button) findViewById(R.id.about_tiktok);
        tiktok.setText(getString(R.string.about_tiktok, Support.TIKTOK_USER));
        tiktok.setOnClickListener(v -> openExternal(Support.tiktokUrl()));

        var instagram = (Button) findViewById(R.id.about_instagram);
        instagram.setText(getString(R.string.about_instagram, Support.INSTAGRAM_USER));
        instagram.setOnClickListener(v -> openExternal(Support.instagramUrl()));

        var discord = (Button) findViewById(R.id.about_discord);
        discord.setText(getString(R.string.about_discord, Support.DISCORD_USER));
        discord.setOnClickListener(v -> {
            var wv = Session.webView;
            if (Session.ready && wv != null) {
                // sends the friend request from the user's own session; falls back to copying the name on failure
                wv.evaluateJavascript("window.__fcAddFriend&&window.__fcAddFriend()", null);
                hideHub();
            } else {
                copyToClipboard(Support.DISCORD_USER);
            }
        });

        aboutBackground = findViewById(R.id.about_background);
        aboutWakelock = findViewById(R.id.about_wakelock);
        aboutBackground.setOnClickListener(v -> {
            boolean on = !prefs.getBoolean(PREF_BACKGROUND, true);
            prefs.edit().putBoolean(PREF_BACKGROUND, on).apply();
            if (on) {
                KeepAliveService.start(this);
                askBatteryExemption();
            } else {
                KeepAliveService.stop(this);
            }
            updateSettingsButtons();
        });
        aboutWakelock.setOnClickListener(v -> {
            boolean on = !prefs.getBoolean(PREF_WAKELOCK, true);
            prefs.edit().putBoolean(PREF_WAKELOCK, on).apply();
            if (prefs.getBoolean(PREF_BACKGROUND, true)) {
                // restart so the service takes / drops its locks
                KeepAliveService.stop(this);
                KeepAliveService.start(this);
            }
            updateSettingsButtons();
        });
        findViewById(R.id.about_battery).setOnClickListener(v -> askBatteryExemption());
        findViewById(R.id.about_update).setOnClickListener(v -> {
            hideHub();
            HttpClient.invalidate(getApplicationContext());
            var wv = Session.webView;
            if (wv != null) wv.stopLoading();
            Session.initialized = false;
            Session.resetPageState();
            startLoading();
        });
        ((TextView) findViewById(R.id.about_version)).setText(getString(R.string.about_version, BuildConfig.VERSION_NAME));
        updateSettingsButtons();
    }

    private void updateSettingsButtons() {
        if (aboutBackground == null) return;
        boolean bg = prefs.getBoolean(PREF_BACKGROUND, true);
        boolean wl = prefs.getBoolean(PREF_WAKELOCK, true);
        aboutBackground.setText(bg ? R.string.about_bg_on : R.string.about_bg_off);
        aboutWakelock.setText(wl ? R.string.about_wake_on : R.string.about_wake_off);
        aboutWakelock.setEnabled(bg);
        aboutWakelock.setAlpha(bg ? 1f : 0.5f);
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException ex) {
            Logger.w("No app to open " + url);
        }
    }

    private void copyToClipboard(String text) {
        var cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("FyoCord", text));
            Toast.makeText(this, getString(R.string.copied_username, text), Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ first run / battery

    private void askSetupOnce() {
        if (prefs.getBoolean(PREF_ASKED, false)) return;
        prefs.edit().putBoolean(PREF_ASKED, true).apply();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
        } else {
            askBatteryExemption();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE && prefs.getBoolean(PREF_BACKGROUND, true)) {
            KeepAliveService.start(this); // the notification can show now
            askBatteryExemption();
        }
    }

    private void askBatteryExemption() {
        if (Build.VERSION.SDK_INT < 23) return;
        var pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) return;
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
        } catch (Exception ex) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ex2) {
                Logger.w("Could not open the battery settings: " + ex2.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ diagnostics

    private static String webViewVersion() {
        if (Build.VERSION.SDK_INT >= 26) {
            var pkg = WebView.getCurrentWebViewPackage();
            if (pkg != null) return pkg.packageName + " " + pkg.versionName;
        }
        return "unknown (API < 26)";
    }

    /** Writes a snapshot of the app + page state into the log; {@code done} runs afterwards (also if the page doesn't answer). */
    private void runDiagnostics(Runnable done) {
        var wv = Session.webView;
        final AtomicBoolean finished = new AtomicBoolean(false);
        final Runnable finish = () -> {
            if (finished.compareAndSet(false, true) && done != null) done.run();
        };

        Logger.i("--- DIAGNOSTICS ---");
        Logger.i("state: initialized=" + Session.initialized + ", pageFinished=" + Session.pageFinished
                + ", injected=" + Session.injected + ", ready=" + Session.ready
                + ", url=" + (wv == null ? "-" : DebugLog.sanitize(String.valueOf(wv.getUrl()))) + ", progress=" + (wv == null ? -1 : wv.getProgress()));
        var js = HttpClient.VencordRuntime;
        var css = HttpClient.VencordCss;
        var inj = HttpClient.VencordInjection;
        Logger.i("downloaded: js=" + (js == null ? "NO" : js.length() + " chars")
                + ", css=" + (css == null ? "NO" : css.length() + " chars")
                + ", injection=" + (inj == null ? "NO" : inj.length + " bytes"));
        Logger.i("cache:\n" + HttpClient.cacheInfo(getApplicationContext()));

        if (wv == null || destroyed) {
            finish.run();
            return;
        }
        handler.postDelayed(finish, 2500); // never wait for a dead page

        wv.evaluateJavascript("(function(){try{"
                        + "var r={Vencord:typeof Vencord,"
                        + "webpack:typeof Vencord!=='undefined'&&!!Vencord.Webpack,"
                        + "VencordMobile:typeof VencordMobile,"
                        + "inlineCss:!!document.getElementById('vencord-css'),"
                        + "nativeBridge:typeof window.VencordMobileNative};"
                        + "if(typeof Vencord!=='undefined'){"
                        + "var P=Vencord.Plugins;"
                        + "r.pluginsTotal=P&&P.plugins?Object.keys(P.plugins).length:null;"
                        + "var sp=P&&P.startedPlugins;"
                        + "r.pluginsStarted=sp?(sp.size!==undefined?sp.size:Object.keys(sp).length):null;"
                        + "}"
                        + "return JSON.stringify(r)}catch(e){return 'probe error: '+e}})()",
                r -> {
                    Logger.i("page probe: " + r);
                    finish.run();
                });
    }

    // ------------------------------------------------------------------ activity plumbing

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (hubOpen()) {
                hideHub();
                return true;
            }
            var wv = Session.webView;
            if (wv != null && Session.injected) {
                wv.evaluateJavascript("VencordMobile.onBackPress()", r -> {
                    // true = Vencord handled it. Anything else (false, or null if the script is missing/broken) = leave the app
                    if (!"true".equals(r)) onBackPressed();
                });
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    /** Back at the top level only minimises the app, so Discord stays loaded (and connected) and reopening is instant. */
    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == SAVE_LOG_RESULTCODE) {
            if (resultCode == RESULT_OK && intent != null && intent.getData() != null && pendingSaveContent != null) {
                try (var os = getContentResolver().openOutputStream(intent.getData())) {
                    Objects.requireNonNull(os).write(pendingSaveContent.getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(this, R.string.log_saved, Toast.LENGTH_SHORT).show();
                } catch (Exception ex) {
                    Logger.e("Failed to save the log file", ex);
                    Toast.makeText(this, R.string.log_save_failed, Toast.LENGTH_SHORT).show();
                }
            }
            pendingSaveContent = null;
            return;
        }

        var callback = Session.filePathCallback;
        if (requestCode != FILECHOOSER_RESULTCODE || callback == null)
            return;

        if (resultCode != RESULT_OK || intent == null) {
            callback.onReceiveValue(null);
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
                    uris = new Uri[]{intent.getData()};
                }
            } catch (Exception ex) {
                Logger.e("Error during file upload", ex);
                uris = null;
            }

            callback.onReceiveValue(uris);
        }
        Session.filePathCallback = null;
    }

    public void handleUrl(Uri url) {
        if (url == null || !"discord.com".equals(url.getAuthority())) return;

        var wv = Session.webView;
        if (!Session.initialized || wv == null) {
            // Vencord is still downloading, loadDiscord() will pick this up
            Session.pendingUrl = url;
        } else if (!Session.ready) {
            wv.loadUrl(url.toString());
        } else {
            var path = url.getPath();
            if (path == null) path = "/";
            wv.evaluateJavascript("Vencord.Webpack.Common.NavigationRouter.transitionTo(" + JSONObject.quote(path) + ")", null);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri data = intent.getData();
        if (data != null) handleUrl(data);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (prefs == null) return;
        // keep Discord's timers and sockets running while the app is minimised, if the user asked for background mode
        var wv = Session.webView;
        if (wv != null && prefs.getBoolean(PREF_BACKGROUND, true)) wv.resumeTimers();
    }

    @Override
    protected void onDestroy() {
        if (prefs == null) { // crash screen only
            super.onDestroy();
            return;
        }
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        DebugLog.setListener(null);
        MessageLog.setListener(null);
        if (pulseAnimator != null) pulseAnimator.cancel();
        stopIndeterminate();
        if (Session.activity == this) Session.activity = null;

        detachWebView();
        if (prefs.getBoolean(PREF_BACKGROUND, true)) {
            // the session (WebView, Discord connection, Vencord) lives on in Session, kept alive by the service
            KeepAliveService.start(this);
            var wv = Session.webView;
            if (wv != null) {
                wv.onResume();
                wv.resumeTimers();
            }
        } else {
            if (Session.filePathCallback != null) {
                Session.filePathCallback.onReceiveValue(null);
                Session.filePathCallback = null;
            }
            Session.release();
            KeepAliveService.stop(this);
        }
        super.onDestroy();
    }
}
