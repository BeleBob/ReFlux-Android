package io.openflux.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.openflux.bridge.mobile.Mobile;

/**
 * Extra channels: other profiles (an official OpenFlux link, say) whose
 * nodes the tunnel mode shares sites with, next to the profile in use.
 * Each new site goes to the node with the fewest connections and stays
 * there (the core's Mobile.startExtra), so pages that open many
 * connections at once (videos, feeds) load over all of them.
 */
final class ExtraChannels {
    static final int MAX = 4;
    private static final String KEY_PROFILES = "extra_profile_ids";

    private ExtraChannels() {
    }

    static Set<Long> ids(Context context) {
        Set<Long> out = new LinkedHashSet<>();
        for (String s : prefs(context).getStringSet(KEY_PROFILES, Collections.emptySet())) {
            try {
                out.add(Long.parseLong(s));
            } catch (NumberFormatException ignored) {
                // Not ours.
            }
        }
        return out;
    }

    static void setIds(Context context, Set<Long> ids) {
        Set<String> s = new LinkedHashSet<>();
        for (Long id : ids) s.add(String.valueOf(id));
        prefs(context).edit().putStringSet(KEY_PROFILES, s).apply();
    }

    /** Whether p can be an extra channel next to the profile with mainId. */
    static boolean eligible(Profile p, long mainId) {
        return p.id != mainId && !p.stream && !p.encryptionSecret.isEmpty();
    }

    /** The chosen profiles that can run next to the one with mainSecret. */
    static List<Profile> chosen(Context context, String mainSecret) {
        Set<Long> ids = ids(context);
        List<Profile> out = new ArrayList<>();
        if (ids.isEmpty()) return out;
        for (Profile p : new ProfileStore(new SecureSettings(context)).load()) {
            if (!ids.contains(p.id) || p.stream || p.encryptionSecret.isEmpty()) continue;
            if (mainSecret != null && p.encryptionSecret.equals(mainSecret)) continue;
            out.add(p);
        }
        return out;
    }

    /** Connects p as an extra channel: "", Mobile.ExtraNoTunnel or what went wrong. */
    static String start(Context context, Profile p) {
        String id = String.valueOf(p.id);
        try {
            String r = p.session
                    ? Mobile.startExtraSession(id, p.sessionTransportsJson(new ScriptStore(context)), p.encryptionSecret)
                    : Mobile.startExtra(id, p.transportType, p.documentUrl, p.encryptionSecret, p.codec, p.maxToken, p.maxUid);
            return r == null ? "" : r;
        } catch (Exception e) {
            return String.valueOf(e.getMessage());
        }
    }

    /** The extra channels the core runs now, by profile id. */
    static Set<Long> running() {
        Set<Long> out = new LinkedHashSet<>();
        String ids = Mobile.extraIDs();
        if (ids == null || ids.isEmpty()) return out;
        for (String s : ids.split(",")) {
            try {
                out.add(Long.parseLong(s));
            } catch (NumberFormatException ignored) {
                // Not ours.
            }
        }
        return out;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(MainActivity.SETTINGS_PREFS_NAME, Context.MODE_PRIVATE);
    }
}
