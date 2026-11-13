package dev.vendicated.vencord;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import org.json.JSONObject;

/**
 * Discord's web client uses the browser Notification API, which a WebView doesn't have, so nothing would ever pop up.
 * The page tells us about messages that deserve a notification (DMs and mentions that aren't muted) and we show them.
 */
final class MessageNotifier {
    private MessageNotifier() {}

    private static final String CHANNEL_ID = "fyocord_messages";

    static void show(Context context, String json) {
        try {
            // while the app is on screen Discord shows the message itself
            if (MainActivity.foreground) return;
            var nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null || (Build.VERSION.SDK_INT >= 24 && !nm.areNotificationsEnabled())) return;

            var o = new JSONObject(json);
            var channelKey = o.optString("id", "");
            var url = o.optString("url", "");
            var uri = Uri.parse(url);
            if (!"https".equals(uri.getScheme()) || !"discord.com".equals(uri.getHost())) return;

            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL_ID) == null) {
                var ch = new NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_HIGH);
                ch.setShowBadge(true);
                nm.createNotificationChannel(ch);
            }

            var tap = new Intent(Intent.ACTION_VIEW, uri)
                    .setClass(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
            int id = 1000 + (channelKey.hashCode() & 0x3FFFFFFF) % 100000;
            var pending = PendingIntent.getActivity(context, id, tap, flags);

            var title = cut(o.optString("title", "Discord"), 80);
            var body = cut(o.optString("body", ""), 400);

            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context, CHANNEL_ID) : new Notification.Builder(context);
            b.setSmallIcon(R.drawable.ic_stat_vendroid)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setWhen(System.currentTimeMillis());
            if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
            nm.notify(id, b.build());
        } catch (Exception ex) {
            Logger.w("Could not show a message notification: " + ex.getMessage());
        }
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
