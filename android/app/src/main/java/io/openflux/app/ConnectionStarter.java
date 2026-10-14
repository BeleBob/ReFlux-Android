package io.openflux.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;

/**
 * Starts the selected profile's connection in the saved mode without the
 * app's screen: from the Quick Settings tile, and after an update installed
 * from inside the app (UpdateReceiver).
 */
final class ConnectionStarter {
    private ConnectionStarter() {
    }

    /**
     * Starts the connection; false when it cannot start without the app
     * (no profile, a profile the app must fix, or no tunnel consent yet).
     */
    static boolean start(Context context) {
        if (isExitMode(context)) return startExit(context);
        boolean proxyMode = isProxyMode(context);
        // First-time tunnel consent can only be granted through an activity.
        if (!proxyMode && VpnService.prepare(context) != null) return false;
        return startConnection(context, proxyMode);
    }

    static boolean isProxyMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.SETTINGS_PREFS_NAME, Context.MODE_PRIVATE);
        return "proxy".equals(prefs.getString("connection_mode", "tunnel"));
    }

    static boolean isExitMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.SETTINGS_PREFS_NAME, Context.MODE_PRIVATE);
        return "exit".equals(prefs.getString("connection_mode", "tunnel"));
    }

    private static Profile selectedProfile(Context context) {
        ProfileStore store = new ProfileStore(new SecureSettings(context));
        long selectedId = store.getSelectedId();
        for (Profile p : store.load()) if (p.id == selectedId) return p;
        return null;
    }

    private static boolean startExit(Context context) {
        Profile selected = selectedProfile(context);
        if (selected == null) return false;
        Intent intent = new Intent(context, OpenFluxExitService.class).setAction(OpenFluxExitService.ACTION_START);
        selected.putConnectionExtras(intent, new ScriptStore(context));
        context.startForegroundService(intent);
        return true;
    }

    private static boolean startConnection(Context context, boolean proxyMode) {
        SecureSettings secureSettings = new SecureSettings(context);
        Profile selected = selectedProfile(context);
        if (selected == null) return false;
        // The app says what is wrong with a JS carrier; nothing else can.
        if (selected.scriptProblem(context, new ScriptStore(context)) != null) return false;
        if (!"oneme".equals(selected.transportType) && !"script".equals(selected.transportType)
                && (selected.documentUrl == null || selected.documentUrl.isEmpty())) return false;

        SharedPreferences prefs = context.getSharedPreferences(MainActivity.SETTINGS_PREFS_NAME, Context.MODE_PRIVATE);
        if (proxyMode) {
            boolean lanAccess = prefs.getBoolean("proxy_lan_access", false);
            boolean authEnabled = prefs.getBoolean("proxy_auth_enabled", false);
            Intent intent = new Intent(context, OpenFluxProxyService.class);
            intent.setAction(OpenFluxProxyService.ACTION_START);
            selected.putConnectionExtras(intent, new ScriptStore(context));
            intent.putExtra(OpenFluxProxyService.EXTRA_PORT, prefs.getInt("proxy_port", 1080));
            intent.putExtra(OpenFluxProxyService.EXTRA_LAN_ACCESS, lanAccess);
            if (lanAccess && authEnabled) {
                intent.putExtra(OpenFluxProxyService.EXTRA_USERNAME, prefs.getString("proxy_username", ""));
                intent.putExtra(OpenFluxProxyService.EXTRA_PASSWORD, secureSettings.getString("proxy_password", ""));
            }
            context.startForegroundService(intent);
        } else {
            Intent intent = new Intent(context, OpenFluxTunnelService.class);
            intent.setAction(OpenFluxTunnelService.ACTION_START);
            selected.putConnectionExtras(intent, new ScriptStore(context));
            intent.putExtra(OpenFluxTunnelService.EXTRA_DNS_SERVER, prefs.getString("dns_server", ""));
            intent.putExtra(OpenFluxTunnelService.EXTRA_MTU, prefs.getInt("mtu", 1400));
            context.startForegroundService(intent);
        }
        return true;
    }
}
