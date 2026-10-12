package io.openflux.app;

import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

// The app's language: the phone's (empty tag), Russian or English. Android
// 13+ keeps the choice itself (LocaleManager, also in the system's app
// settings) and gives every context of the app its resources; older
// versions keep it in the app's preferences, and each activity and service
// wraps its base context (attachBaseContext) to read them.
final class Lang {
    static final String SYSTEM = "";
    static final String[] TAGS = {SYSTEM, "ru", "en"};
    private static final String KEY = "language";

    private Lang() {
    }

    static String get(Context context) {
        if (Build.VERSION.SDK_INT >= 33) {
            LocaleList locales = context.getSystemService(LocaleManager.class).getApplicationLocales();
            return locales.isEmpty() ? SYSTEM : supported(locales.get(0).getLanguage());
        }
        return supported(prefs(context).getString(KEY, SYSTEM));
    }

    // Applies tag: Android 13+ recreates the running activities by itself,
    // older versions need the caller to rebuild its screen (recreate()).
    static void set(Context context, String tag) {
        tag = supported(tag);
        prefs(context).edit().putString(KEY, tag).apply();
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager.class).setApplicationLocales(
                    tag.isEmpty() ? LocaleList.getEmptyLocaleList() : LocaleList.forLanguageTags(tag));
        }
    }

    static Context wrap(Context base) {
        if (Build.VERSION.SDK_INT >= 33) return base;
        String tag = supported(prefs(base).getString(KEY, SYSTEM));
        if (tag.isEmpty()) return base;
        Configuration conf = new Configuration(base.getResources().getConfiguration());
        conf.setLocale(Locale.forLanguageTag(tag));
        return base.createConfigurationContext(conf);
    }

    // The language's name in itself, as pickers usually show it.
    static String label(Context context, String tag) {
        if (tag.isEmpty()) return context.getString(R.string.language_system);
        Locale locale = Locale.forLanguageTag(tag);
        String name = locale.getDisplayLanguage(locale);
        return name.isEmpty() ? tag : name.substring(0, 1).toUpperCase(locale) + name.substring(1);
    }

    private static String supported(String tag) {
        for (String t : TAGS) if (t.equals(tag)) return t;
        return SYSTEM;
    }

    private static android.content.SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(MainActivity.SETTINGS_PREFS_NAME, Context.MODE_PRIVATE);
    }
}
