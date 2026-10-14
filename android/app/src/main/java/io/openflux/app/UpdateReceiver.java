package io.openflux.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Brings the connection back after an update installed from inside the app
 * (MainActivity's update): installing stops the running app and with it
 * the connection. Android lets a just-replaced app start its foreground
 * service from here.
 */
public final class UpdateReceiver extends BroadcastReceiver {
    /** Set when the update is handed to the installer with a connection up. */
    static final String KEY_RECONNECT = "reconnect_after_update";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        AppUpdate.clean(context);
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.SETTINGS_PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_RECONNECT, false)) return;
        prefs.edit().remove(KEY_RECONNECT).apply();
        try {
            ConnectionStarter.start(context);
        } catch (RuntimeException ignored) {
            // Not allowed now: the person connects from the app.
        }
    }
}
