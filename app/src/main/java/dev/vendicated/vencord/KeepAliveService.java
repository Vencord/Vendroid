package dev.vendicated.vencord;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/**
 * "Background mode", in the same spirit as Termux: a foreground service with a persistent notification (and a Stop
 * action) that keeps the process, and with it the Discord session, alive when the app is minimised, swiped away or the
 * screen is off. Optionally holds a partial CPU wake lock and a Wi-Fi lock so the connection isn't put to sleep.
 */
public class KeepAliveService extends Service {
    private static final String CHANNEL_ID = "fyocord_background";
    private static final int NOTIFICATION_ID = 1;
    private static final String ACTION_STOP = "dev.vendicated.vencord.STOP";
    private static final String ACTION_REPOST = "dev.vendicated.vencord.REPOST";

    private final Handler watchdog = new Handler(Looper.getMainLooper());
    private final Runnable watch = new Runnable() {
        @Override
        public void run() {
            // the access may have run out or been revoked while nobody was looking
            if (!Access.isValid(KeepAliveService.this)) {
                lockdown();
                return;
            }
            if (Access.dueForRecheck(KeepAliveService.this)) {
                var app = getApplicationContext();
                new Thread(() -> {
                    if (!Access.recheck(app)) watchdog.post(KeepAliveService.this::lockdown);
                }, "access-recheck").start();
            }
            watchdog.postDelayed(this, 2 * 60_000L);
        }
    };

    private void lockdown() {
        Logger.w("Access ended: shutting the background session down");
        var a = Session.activity;
        if (a != null) a.onAccessLost();
        else {
            Session.release();
            Access.clear(this);
        }
        stopForeground(true);
        stopSelf();
    }

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    public static void start(Context context) {
        var intent = new Intent(context, KeepAliveService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (Exception ex) {
            Logger.e("Could not start the background service", ex);
        }
    }

    public static void stop(Context context) {
        try {
            context.stopService(new Intent(context, KeepAliveService.class));
        } catch (Exception ex) {
            Logger.e("Could not stop the background service", ex);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            // "Stop" in the notification: switch background mode off, and drop the session if no screen is showing it
            getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit().putBoolean(MainActivity.PREF_BACKGROUND, false).apply();
            new Handler(Looper.getMainLooper()).post(() -> {
                if (Session.activity == null) Session.release();
                var a = Session.activity;
                if (a != null) a.onBackgroundModeChanged();
            });
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (Build.VERSION.SDK_INT >= 26) {
            var channel = new NotificationChannel(CHANNEL_ID, getString(R.string.keepalive_title), NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            var nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }

        int piFlags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        var open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), piFlags);
        var stop = PendingIntent.getService(this, 1,
                new Intent(this, KeepAliveService.class).setAction(ACTION_STOP), piFlags);
        // if the notification is swiped away it comes straight back
        var repost = PendingIntent.getService(this, 2,
                new Intent(this, KeepAliveService.class).setAction(ACTION_REPOST), piFlags);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        b.setContentTitle(getString(R.string.keepalive_title))
                .setContentText(getString(R.string.keepalive_text))
                .setSmallIcon(R.drawable.ic_stat_vendroid)
                .setContentIntent(pending(open))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setDeleteIntent(repost)
                .addAction(new Notification.Action.Builder(R.drawable.ic_stat_vendroid, getString(R.string.keepalive_stop), stop).build());
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_MIN);

        try {
            startForeground(NOTIFICATION_ID, b.build());
        } catch (Exception ex) {
            Logger.e("startForeground failed", ex);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!Access.isValid(this)) {
            lockdown();
            return START_NOT_STICKY;
        }
        watchdog.removeCallbacks(watch);
        watchdog.postDelayed(watch, 60_000L);

        acquireLocks();
        Logger.i("Background mode: service running (wake lock " + (wakeLock != null && wakeLock.isHeld()) + ")");
        // after a reboot or when Android restarts us there is no screen: bring Discord up on our own
        Session.startHeadless(this);
        return START_STICKY;
    }

    private static PendingIntent pending(PendingIntent p) {
        return p;
    }

    private void acquireLocks() {
        try {
            if (wakeLock == null) {
                var pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fyocord:keepalive");
                    wakeLock.setReferenceCounted(false);
                }
            }
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();

            if (wifiLock == null) {
                var wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    wifiLock = wm.createWifiLock(
                            Build.VERSION.SDK_INT >= 29 ? WifiManager.WIFI_MODE_FULL_LOW_LATENCY : WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                            "fyocord:wifi");
                    wifiLock.setReferenceCounted(false);
                }
            }
            if (wifiLock != null && !wifiLock.isHeld()) wifiLock.acquire();
        } catch (Exception ex) {
            Logger.e("Could not take the wake / Wi-Fi lock", ex);
        }
    }

    private void releaseLocks() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Exception ex) {
            Logger.e("Could not release the wake / Wi-Fi lock", ex);
        }
    }

    @Override
    public void onDestroy() {
        watchdog.removeCallbacks(watch);
        releaseLocks();
        Logger.i("Background mode: service stopped");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
