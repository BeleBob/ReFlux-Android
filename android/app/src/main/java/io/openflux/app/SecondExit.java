package io.openflux.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

import io.openflux.bridge.mobile.Mobile;

/**
 * The second channel: another profile (an official OpenFlux link, say)
 * whose node the tunnel mode shares sites with, next to the profile in
 * use. Each new site goes to the node with fewer connections and stays
 * there (the core's Mobile.startSecondary), so pages that open many
 * connections at once (videos, feeds) load over both.
 */
final class SecondExit {
    private static final String KEY_PROFILE = "second_profile_id";

    private SecondExit() {
    }

    static long profileId(Context context) {
        return prefs(context).getLong(KEY_PROFILE, 0L);
    }

    static void setProfileId(Context context, long id) {
        prefs(context).edit().putLong(KEY_PROFILE, id).apply();
    }

    /** The chosen second profile, or null (none, gone, or the one in use). */
    static Profile profile(Context context, String mainSecret) {
        long id = profileId(context);
        if (id == 0L) return null;
        List<Profile> profiles = new ProfileStore(new SecureSettings(context)).load();
        for (Profile p : profiles) {
            if (p.id != id) continue;
            if (p.stream || p.encryptionSecret.isEmpty()) return null;
            if (mainSecret != null && p.encryptionSecret.equals(mainSecret)) return null;
            return p;
        }
        return null;
    }

    /** Connects the second channel; "" or what went wrong. */
    static String start(Context context, Profile p) {
        if (p.session) {
            try {
                return nonNull(Mobile.startSecondarySession(p.sessionTransportsJson(new ScriptStore(context)), p.encryptionSecret));
            } catch (Exception e) {
                return String.valueOf(e.getMessage());
            }
        }
        return nonNull(Mobile.startSecondary(p.transportType, p.documentUrl, p.encryptionSecret, p.codec, p.maxToken, p.maxUid));
    }

    private static String nonNull(String s) {
        return s == null ? "" : s;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(MainActivity.SETTINGS_PREFS_NAME, Context.MODE_PRIVATE);
    }
}
