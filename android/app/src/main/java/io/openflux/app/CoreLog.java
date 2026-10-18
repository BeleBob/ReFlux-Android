package io.openflux.app;

import android.content.Context;

import io.openflux.bridge.mobile.Mobile;

/**
 * How much the core logs. By default only what matters: connections,
 * errors, cups rooms and the like (the core's status lines). The verbose
 * log ("Подробный журнал") writes every packet and its encryption, hundreds
 * of lines a second during a video; that costs the phone time and held the
 * packets up (the core's log list took the same lock as the packets to the
 * device), so videos stalled. It is for finding faults.
 */
final class CoreLog {
    static final String KEY_VERBOSE = "verbose_log";

    private CoreLog() {
    }

    /** Sets the core's log level for its next start. */
    static void apply(Context context) {
        boolean verbose = context.getSharedPreferences(MainActivity.SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_VERBOSE, false);
        Mobile.setDebugLevel(verbose ? 2 : 0);
    }
}
