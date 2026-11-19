package dev.vendicated.vencord;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

/** Shared updater UI for the app and the launcher shortcut. */
public final class VencordUpdaterDialog {
    private VencordUpdaterDialog() {}

    public static void show(Activity activity, Runnable onUpdated, Runnable onDismiss) {
        var view = activity.getLayoutInflater().inflate(R.layout.dialog_vencord_updater, null);
        var autoUpdateSwitch = (Switch) view.findViewById(R.id.auto_update_switch);
        var status = (TextView) view.findViewById(R.id.update_status);
        var updateButton = (Button) view.findViewById(R.id.update_button);

        autoUpdateSwitch.setChecked(VendroidSettings.isAutoUpdatesEnabled(activity));
        autoUpdateSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                VendroidSettings.setAutoUpdatesEnabled(activity, isChecked));

        var dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.vencord_updater)
                .setView(view)
                .setPositiveButton(android.R.string.ok, null);
        if (onDismiss != null) dialog.setOnDismissListener(ignored -> onDismiss.run());
        dialog.show();

        checkForUpdates(activity, status, updateButton, onUpdated);
    }

    private static void checkForUpdates(Activity activity, TextView status, Button updateButton, Runnable onUpdated) {
        status.setText(R.string.checking_for_updates);
        updateButton.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                var release = VencordUpdater.fetchLatestRelease();
                var installedHash = VendroidSettings.getInstalledHash(activity);

                activity.runOnUiThread(() -> {
                    if (activity.isFinishing()) return;
                    if (release.hash.equals(installedHash)) {
                        setUpdateStatus(status, activity.getString(R.string.up_to_date, release.title), release.hash);
                        return;
                    }

                    var installed = installedHash == null ? activity.getString(R.string.unknown_version) : "DevBuild " + installedHash;
                    setUpdateStatus(status, activity.getString(R.string.update_available, release.title, installed),
                            release.hash, installedHash);
                    updateButton.setText(R.string.update);
                    updateButton.setEnabled(true);
                    updateButton.setVisibility(View.VISIBLE);
                    updateButton.setOnClickListener(v -> installUpdate(activity, release, status, updateButton, onUpdated));
                });
            } catch (IOException ex) {
                Logger.e("Failed to check for Vencord updates", ex);
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing()) status.setText(activity.getString(R.string.update_check_failed, ex.getMessage()));
                });
            }
        }).start();
    }

    private static void setUpdateStatus(TextView status, CharSequence text, String... hashes) {
        var spannable = new SpannableString(text);
        var raw = text.toString();
        for (var hash : hashes) {
            if (hash == null || hash.isEmpty()) continue;
            var index = raw.indexOf(hash);
            while (index >= 0) {
                spannable.setSpan(new URLSpan(Constants.COMMIT_URL + hash), index, index + hash.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                index = raw.indexOf(hash, index + hash.length());
            }
        }
        status.setText(spannable);
        status.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private static void installUpdate(Activity activity, VencordUpdater.Release release, TextView status,
                                      Button updateButton, Runnable onUpdated) {
        updateButton.setEnabled(false);
        updateButton.setText(R.string.updating);
        new Thread(() -> {
            try {
                VencordUpdater.installRelease(activity, release);
                VencordUpdater.loadBundles(activity);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing()) return;
                    setUpdateStatus(status, activity.getString(R.string.updated_to, release.title), release.hash);
                    updateButton.setVisibility(View.GONE);
                    Toast.makeText(activity, activity.getString(R.string.updated_to, release.title), Toast.LENGTH_SHORT).show();
                    if (onUpdated != null) onUpdated.run();
                });
            } catch (IOException ex) {
                Logger.e("Failed to update Vencord", ex);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing()) return;
                    status.setText(activity.getString(R.string.update_failed, ex.getMessage()));
                    updateButton.setText(R.string.retry);
                    updateButton.setEnabled(true);
                });
            }
        }).start();
    }
}
