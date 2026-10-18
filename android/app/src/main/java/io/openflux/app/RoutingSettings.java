package io.openflux.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Set;

import io.openflux.bridge.mobile.Mobile;

/**
 * The "Маршрутизация" settings: the mode (everything through the tunnel,
 * Russia direct, or the mobile whitelists direct), ad blocking and the
 * person's own direct sites. The core routes by them (Mobile.setRouting) in
 * the tunnel and the proxy modes alike.
 */
final class RoutingSettings {
    static final String MODE_TUNNEL = "tunnel";
    static final String MODE_RUSSIA = "russia";
    static final String MODE_WHITELIST = "whitelist";
    static final String[] MODES = {MODE_TUNNEL, MODE_RUSSIA, MODE_WHITELIST};

    private static final String PREFS = "reflux_routing";
    private static final String KEY_MODE = "mode";
    private static final String KEY_ADS = "ads";

    private RoutingSettings() {
    }

    static String mode(Context context) {
        String m = prefs(context).getString(KEY_MODE, MODE_TUNNEL);
        for (String known : MODES) if (known.equals(m)) return m;
        return MODE_TUNNEL;
    }

    static boolean ads(Context context) {
        return prefs(context).getBoolean(KEY_ADS, false);
    }

    /** The own direct sites (kept where the domain presets kept them). */
    static Set<String> direct(Context context) {
        return DomainFilter.loadCustomDomains(context.getSharedPreferences(DomainFilter.PREFS_NAME, Context.MODE_PRIVATE));
    }

    static void save(Context context, String mode, boolean ads, Set<String> direct) {
        prefs(context).edit().putString(KEY_MODE, mode).putBoolean(KEY_ADS, ads).apply();
        context.getSharedPreferences(DomainFilter.PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putStringSet(DomainFilter.KEY_CUSTOM_DOMAINS, new LinkedHashSet<>(direct))
                .apply();
    }

    static String configJson(Context context) {
        try {
            JSONObject o = new JSONObject();
            o.put("mode", mode(context));
            o.put("ads", ads(context));
            JSONArray direct = new JSONArray();
            for (String d : direct(context)) direct.put(d);
            o.put("direct", direct);
            return o.toString();
        } catch (JSONException e) {
            return "";
        }
    }

    /**
     * Hands the settings to the core for the next start; dnsServer is the
     * server the device's DNS goes to through the tunnel ("" for the
     * default). Returns "" or what is wrong.
     */
    static String apply(Context context, String dnsServer) {
        String path = RoutingData.ensure(context).getAbsolutePath();
        String error = Mobile.setRouting(configJson(context), path, dnsServer == null ? "" : dnsServer);
        RoutingData.maybeUpdate(context, false, null);
        return error == null ? "" : error;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
