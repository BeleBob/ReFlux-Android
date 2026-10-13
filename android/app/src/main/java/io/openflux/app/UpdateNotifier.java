package io.openflux.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reminds of an important update (AppUpdate) people who keep the tunnel on
 * and do not open the app: while it runs, the newest releases are checked
 * every CHECK_EVERY_MS, and a pending important update shows a notification
 * that opens the app, which offers the update at once.
 */
final class UpdateNotifier {
    private static final long CHECK_EVERY_MS = 12L * 60 * 60 * 1000;
    private static final String KEY_CHECKED = "update_bg_checked";
    private static final String CHANNEL_ID = "reflux_updates";
    private static final int NOTIFICATION_ID = 7301;
    private static final AtomicBoolean busy = new AtomicBoolean();

    private UpdateNotifier() {
    }

    /** Checks in the background when the last check is CHECK_EVERY_MS old. */
    static void maybeCheck(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.SETTINGS_PREFS_NAME, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long last = prefs.getLong(KEY_CHECKED, 0L);
        if (now - last >= 0 && now - last < CHECK_EVERY_MS) return;
        if (!busy.compareAndSet(false, true)) return;
        prefs.edit().putLong(KEY_CHECKED, now).apply();
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                check(app);
            } catch (Exception ignored) {
                // No network or no GitHub now: the next check tries again.
            } finally {
                busy.set(false);
            }
        }).start();
    }

    private static void check(Context context) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        String installed = info.versionName != null ? info.versionName : "";
        AppUpdate.Release release = AppUpdate.fetchLatest(MainActivity.APP_REPO_SLUG, installed);
        if (release == null || !release.important || AppUpdate.compare(installed, release.version) >= 0) return;

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, context.getString(R.string.update_channel), NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, NOTIFICATION_ID, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_openflux_notification)
                .setContentTitle(context.getString(R.string.update_title_important, release.version))
                .setContentText(context.getString(R.string.update_notify_text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build();
        manager.notify(NOTIFICATION_ID, notification);
    }
}
