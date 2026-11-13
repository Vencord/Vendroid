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

    private static final String CHANNEL_ID = "fyocord_messages2"; // v2: channel sound settings can't be changed after creation
    private static int counter = 0;

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
                ch.enableVibration(true);
                ch.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                nm.createNotificationChannel(ch);
            }

            var tap = new Intent(Intent.ACTION_VIEW, uri)
                    .setClass(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
            // one notification per message (not per channel), so each message makes its own sound
            int id = 1000 + ((o.optString("mid", "").hashCode() ^ (++counter * 31)) & 0x3FFFFFFF) % 1_000_000;
            var pending = PendingIntent.getActivity(context, channelKey.hashCode() & 0xFFFFF, tap, flags);

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
                    .setGroup("fc_" + channelKey)

                    .setWhen(System.currentTimeMillis());
            if (Build.VERSION.SDK_INT >= 23) b.setLargeIcon(android.graphics.drawable.Icon.createWithResource(context, R.mipmap.ic_launcher));
            else b.setLargeIcon(android.graphics.BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher_foreground));
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
