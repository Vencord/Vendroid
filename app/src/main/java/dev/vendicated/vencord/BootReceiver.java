package dev.vendicated.vencord;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Brings background mode back after a reboot or an app update, so FyoCord keeps logging without being opened. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        var action = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;

        var prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        // only for people who use it: background mode on, and logged in at least once
        if (prefs.getBoolean(MainActivity.PREF_BACKGROUND, true) && prefs.getBoolean(MainActivity.PREF_EVER_READY, false)) {
            KeepAliveService.start(context);
        }
    }
}
