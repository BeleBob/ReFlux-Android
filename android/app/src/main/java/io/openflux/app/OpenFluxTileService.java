package io.openflux.app;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

// Quick Settings tile that toggles whichever connection mode (Tunnel or
// Proxy) is currently selected in the app, using whichever profile is
// currently selected there. Tunnel mode needs Android's one-time system
// consent dialog, which a TileService cannot show itself, so if that
// permission hasn't been
// granted yet (or no profile is configured) the tile opens the app instead
// of failing silently.
public final class OpenFluxTileService extends TileService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            updateTile();
            handler.postDelayed(this, 1000);
        }
    };

    // The app's language on Android before 13 (see Lang).
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override public void onStartListening() {
        super.onStartListening();
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    @Override public void onStopListening() {
        handler.removeCallbacks(refresh);
        super.onStopListening();
    }

    @Override public void onClick() {
        super.onClick();
        boolean proxyMode = isProxyMode();
        boolean exitMode = isExitMode();
        boolean running = exitMode ? OpenFluxExitService.isRunning()
                : proxyMode ? OpenFluxProxyService.isRunning() : OpenFluxTunnelService.isRunning();
        if (running) {
            Intent stop = exitMode
                    ? new Intent(this, OpenFluxExitService.class).setAction(OpenFluxExitService.ACTION_STOP)
                    : new Intent(this, proxyMode ? OpenFluxProxyService.class : OpenFluxTunnelService.class)
                            .setAction(proxyMode ? OpenFluxProxyService.ACTION_STOP : OpenFluxTunnelService.ACTION_STOP);
            startService(stop);
            updateTile();
            return;
        }
        if (!ConnectionStarter.start(this)) {
            // No profile, one the app must fix, or no tunnel consent yet:
            // that takes the app's screen.
            openApp();
            return;
        }
        updateTile();
    }

    private boolean isProxyMode() {
        return ConnectionStarter.isProxyMode(this);
    }

    private boolean isExitMode() {
        return ConnectionStarter.isExitMode(this);
    }

    private void openApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(pendingIntent);
        } else {
            startActivityAndCollapse(intent);
        }
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean proxyMode = isProxyMode();
        boolean exitMode = isExitMode();
        boolean running = exitMode ? OpenFluxExitService.isRunning()
                : proxyMode ? OpenFluxProxyService.isRunning() : OpenFluxTunnelService.isRunning();
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_openflux_notification));
        tile.setLabel("ReFlux");
        tile.setState(running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= 29) {
            tile.setSubtitle(exitMode ? getString(R.string.mode_exit_short) : proxyMode ? getString(R.string.mode_proxy_short) : getString(R.string.mode_tunnel_short));
        }
        tile.updateTile();
    }
}
