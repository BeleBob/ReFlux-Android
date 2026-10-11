package io.openflux.app;

import android.content.Context;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

// Persists the saved profile list and the currently selected profile id
// through the same Android Keystore-backed store used for the legacy single
// document URL / secret pair.
final class ProfileStore {
    private static final String KEY_PROFILES = "profiles_json";
    private static final String KEY_SELECTED = "selected_profile_id";
    // When something other than the screen (the tunnel service taking the
    // server's configuration) last changed the profiles: plain preferences,
    // since the screen looks every half second and it is no secret.
    private static final String META_PREFS = "profiles_meta";
    private static final String KEY_CHANGED = "profiles_changed_at";

    private final SecureSettings settings;

    ProfileStore(SecureSettings settings) {
        this.settings = settings;
    }

    List<Profile> load() {
        List<Profile> result = new ArrayList<>();
        String raw = settings.getString(KEY_PROFILES, "");
        if (raw.isEmpty()) return result;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                result.add(Profile.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    void save(List<Profile> profiles) {
        try {
            JSONArray array = new JSONArray();
            for (Profile profile : profiles) array.put(profile.toJson());
            settings.putString(KEY_PROFILES, array.toString());
        } catch (Exception ignored) {
        }
    }

    long getSelectedId() {
        try {
            return Long.parseLong(settings.getString(KEY_SELECTED, "-1"));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static void markChanged(Context context) {
        context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_CHANGED, System.currentTimeMillis()).apply();
    }

    static long changedAt(Context context) {
        return context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE).getLong(KEY_CHANGED, 0);
    }

    void setSelectedId(long id) {
        settings.putString(KEY_SELECTED, Long.toString(id));
    }
}
