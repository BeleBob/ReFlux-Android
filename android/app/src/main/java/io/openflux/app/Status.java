package io.openflux.app;

import android.content.Context;

// The state of a connection service (tunnel, proxy, exit node). Services
// keep one of these; the screen shows label() in the app's language.
final class Status {
    static final String STOPPED = "stopped";
    static final String CONNECTING = "connecting";
    static final String CONNECTED = "connected";
    // The exit node runs, no client is connected yet.
    static final String WAITING = "waiting";
    // A captcha or a script's settings page waits for the person.
    static final String CHECK = "check";
    static final String STOPPING = "stopping";
    static final String ERROR = "error";

    private Status() {
    }

    // Between stopped and connected: the button pulses amber.
    static boolean busy(String status) {
        return CONNECTING.equals(status) || WAITING.equals(status)
                || CHECK.equals(status) || STOPPING.equals(status);
    }

    static String label(Context context, String status) {
        switch (status == null ? STOPPED : status) {
            case CONNECTING: return context.getString(R.string.status_connecting);
            case CONNECTED: return context.getString(R.string.status_connected);
            case WAITING: return context.getString(R.string.status_waiting);
            case CHECK: return context.getString(R.string.status_check);
            case STOPPING: return context.getString(R.string.status_stopping);
            case ERROR: return context.getString(R.string.status_error);
            default: return context.getString(R.string.status_stopped);
        }
    }
}
