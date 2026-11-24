package dev.vendicated.vencord;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.IOException;

/** Checks for updates to the Android app, separately from the Vencord bundles. */
final class VendroidUpdater {
    private VendroidUpdater() {}

    /** Checks once per activity launch without blocking the WebView or the UI thread. */
    static void checkOnLaunch(Activity activity) {
        // Debug builds have a different application ID and can't install the release APK as an update.
        if (BuildConfig.DEBUG) return;

        new Thread(() -> {
            try {
                var release = VendroidRelease.fetchLatest();
                if (release == null) return;

                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;

                    var skippedVersion = VendroidSettings.getSkippedAppVersion(activity);
                    if (release.shouldPrompt(BuildConfig.VERSION_CODE, skippedVersion)) {
                        showUpdate(activity, release);
                    }
                });
            } catch (IOException ex) {
                // An offline or unavailable release should not interfere with launching Vendroid.
                Logger.w("Failed to check for Vendroid updates: " + ex);
            }
        }, "Vendroid update check").start();
    }

    private static void showUpdate(Activity activity, VendroidRelease release) {
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.vendroid_update_title, release.versionName))
                .setMessage(release.changelog.isEmpty()
                        ? activity.getString(R.string.vendroid_no_release_notes)
                        : release.changelog)
                .setPositiveButton(R.string.update, (dialog, which) -> openDownload(activity, release))
                .setNegativeButton(R.string.vendroid_remind_later, null)
                .setNeutralButton(R.string.vendroid_skip_update, (dialog, which) ->
                        VendroidSettings.setSkippedAppVersion(activity, release.versionCode))
                .show();
    }

    private static void openDownload(Activity activity, VendroidRelease release) {
        try {
            var intent = new Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl()));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            activity.startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(activity, R.string.vendroid_no_browser, Toast.LENGTH_LONG).show();
        }
    }
}
