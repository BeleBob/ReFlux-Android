package io.openflux.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.openflux.bridge.mobile.Mobile;

// The connection test on the home screen: whether the server answers,
// which channels carry traffic, and whether sites open through the server.
// Runs on a worker thread (the site checks take up to seconds); the home
// screen shows the lines.
final class ConnectionTest {
    private ConnectionTest() { }

    static final int OK = 0;
    static final int WARN = 1;
    static final int FAIL = 2;

    static final class Line {
        final int state;
        final String text;

        Line(int state, String text) {
            this.state = state;
            this.text = text;
        }
    }

    // The sites asked through the server: a Russian one and a world one.
    private static final String[][] SITES = {
            {"ya.ru", "http://ya.ru/"},
            {"google.com", "http://connectivitycheck.gstatic.com/generate_204"},
    };

    static List<Line> run(Context c, Profile profile, boolean proxyMode) {
        List<Line> lines = new ArrayList<>();
        boolean connected = proxyMode ? Mobile.proxyIsConnected() : Mobile.isConnected();
        if (!connected) {
            lines.add(new Line(FAIL, c.getString(R.string.test_session_fail)));
        } else {
            lines.add(new Line(OK, c.getString("classic".equals(Mobile.connectionMode())
                    ? R.string.test_session_classic : R.string.test_session_ok)));
        }
        carriers(c, profile, lines);
        if (connected) sites(c, lines);
        return lines;
    }

    // The worst state of the lines, for the verdict.
    static int worst(List<Line> lines) {
        int worst = OK;
        for (Line l : lines) worst = Math.max(worst, l.state);
        return worst;
    }

    private static void carriers(Context c, Profile profile, List<Line> lines) {
        long now = System.currentTimeMillis();
        try {
            JSONArray carriers = new JSONArray(Mobile.carrierStatus());
            for (int i = 0; i < carriers.length(); i++) {
                JSONObject t = carriers.getJSONObject(i);
                String name = Profile.carriersLabel(c, t.optString("name"), "");
                if (t.optBoolean("live")) {
                    long heard = t.optLong("heard_ms");
                    String text = c.getString(R.string.test_carrier_live, name);
                    if (heard > 0) text += " · " + c.getString(R.string.test_carrier_heard, Math.max(0, (now - heard) / 1000));
                    lines.add(new Line(OK, text));
                } else if (t.optBoolean("attached")) {
                    lines.add(new Line(WARN, c.getString(R.string.test_carrier_attached, name)));
                } else if ("mailru".equals(t.optString("type")) && !MailruLogin.signedIn()) {
                    lines.add(new Line(WARN, c.getString(R.string.test_carrier_login, name)));
                } else {
                    lines.add(new Line(FAIL, c.getString(R.string.test_carrier_down, name)));
                }
            }
        } catch (Exception e) {
            // No Session status: the line above already says how it is.
        }
        if (profile != null && profile.mailruLeftOut()) {
            lines.add(new Line(OK, c.getString(R.string.test_mailru_off)));
        }
    }

    private static void sites(Context c, List<Line> lines) {
        for (String[] site : SITES) {
            try {
                JSONObject r = new JSONObject(Mobile.probeTunnel(site[1], 10_000));
                if (r.optBoolean("unavailable")) {
                    lines.add(new Line(WARN, c.getString(R.string.test_site_unavailable)));
                    return;
                }
                if (r.optBoolean("ok")) {
                    lines.add(new Line(OK, c.getString(R.string.test_site_ok, site[0], r.optLong("ms"))));
                } else {
                    lines.add(new Line(FAIL, c.getString(R.string.test_site_fail, site[0], r.optString("error"))));
                }
            } catch (Exception e) {
                lines.add(new Line(FAIL, c.getString(R.string.test_site_fail, site[0], String.valueOf(e.getMessage()))));
            }
        }
    }
}
