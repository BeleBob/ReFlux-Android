package io.openflux.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;

import io.openflux.bridge.mobile.Mobile;

/**
 * The routing lists (the core's routing data file): which sites and
 * addresses are Russian, blocked, advertising, on the mobile whitelists.
 * The APK carries a copy; the app swaps in the copy ReFlux rebuilds daily
 * (the "routing-data" release) about once a week. The download is checked
 * against its SHA-256 and must load before it replaces the file in use.
 */
final class RoutingData {
    static final String FILE = "routing.dat.gz";
    private static final String ASSET = "routing.dat.gz";
    private static final String BASE = "https://github.com/BeleBob/ReFlux/releases/download/routing-data/";
    private static final String PREFS = "reflux_routing_data";
    private static final String KEY_ASSET_VERSION = "asset_version";
    private static final String KEY_CHECKED = "checked";
    static final long CHECK_EVERY_MS = 7L * 24 * 60 * 60 * 1000;
    private static final int TIMEOUT_MS = 20_000;
    private static final AtomicBoolean updating = new AtomicBoolean();

    private RoutingData() {
    }

    static File file(Context context) {
        return new File(context.getFilesDir(), FILE);
    }

    /** The file in use, copied from the APK when there is none or the app got newer lists. */
    static synchronized File ensure(Context context) {
        File f = file(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int version = versionCode(context);
        if (f.isFile() && prefs.getInt(KEY_ASSET_VERSION, 0) >= version) return f;
        // A downloaded file newer than the APK's copy stays.
        if (f.isFile() && generated(f).compareTo(generatedAsset(context)) >= 0) {
            prefs.edit().putInt(KEY_ASSET_VERSION, version).apply();
            return f;
        }
        try (InputStream in = context.getAssets().open(ASSET)) {
            write(in, f);
            prefs.edit().putInt(KEY_ASSET_VERSION, version).apply();
        } catch (IOException ignored) {
            // No copy in this build: the core then knows only the own sites.
        }
        return f;
    }

    private static int versionCode(Context context) {
        try {
            return (int) context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }

    /** When the lists in use were built ("2026-10-18T09:40:12Z"), "" if unknown. */
    static String generated(File f) {
        try {
            String info = Mobile.routingDataInfo(f.getAbsolutePath());
            return info == null || info.isEmpty() ? "" : new JSONObject(info).optString("generated");
        } catch (Exception e) {
            return "";
        }
    }

    private static String generatedAsset(Context context) {
        File tmp = new File(context.getCacheDir(), "routing-asset.dat.gz");
        try (InputStream in = context.getAssets().open(ASSET)) {
            write(in, tmp);
            return generated(tmp);
        } catch (IOException e) {
            return "";
        } finally {
            tmp.delete();
        }
    }

    /** Downloads newer lists in the background when the last check was a week ago (or force). */
    static void maybeUpdate(Context context, boolean force, Runnable done) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!force && now - prefs.getLong(KEY_CHECKED, 0) < CHECK_EVERY_MS) return;
        if (!updating.compareAndSet(false, true)) return;
        new Thread(() -> {
            try {
                update(app);
                prefs.edit().putLong(KEY_CHECKED, System.currentTimeMillis()).apply();
            } catch (Exception ignored) {
                // Offline or GitHub unreachable: the next start tries again.
            } finally {
                updating.set(false);
                if (done != null) done.run();
            }
        }, "routing-data").start();
    }

    private static void update(Context context) throws Exception {
        String want = read(BASE + FILE + ".sha256").trim().split("\\s+")[0].toLowerCase(java.util.Locale.ROOT);
        if (want.length() != 64) throw new IOException("bad checksum file");
        File current = ensure(context);
        if (current.isFile() && want.equals(sha256(current))) return;
        File tmp = new File(context.getCacheDir(), "routing-download.dat.gz");
        HttpURLConnection c = get(BASE + FILE);
        try (InputStream in = c.getInputStream()) {
            write(in, tmp);
        } finally {
            c.disconnect();
        }
        try {
            if (!want.equals(sha256(tmp))) throw new IOException("checksum mismatch");
            if (generated(tmp).isEmpty()) throw new IOException("the lists do not load");
            synchronized (RoutingData.class) {
                File f = file(context);
                if (!tmp.renameTo(f)) {
                    try (InputStream in = new FileInputStream(tmp)) {
                        write(in, f);
                    }
                }
            }
        } finally {
            tmp.delete();
        }
    }

    private static void write(InputStream in, File to) throws IOException {
        File part = new File(to.getPath() + ".part");
        try (OutputStream out = new FileOutputStream(part)) {
            byte[] buf = new byte[64 * 1024];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        }
        if (!part.renameTo(to)) throw new IOException("cannot replace " + to.getName());
    }

    private static HttpURLConnection get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "ReFlux-Android");
        int code = c.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            c.disconnect();
            throw new IOException("HTTP " + code);
        }
        return c;
    }

    private static String read(String url) throws IOException {
        HttpURLConnection c = get(url);
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) body.append(line).append('\n');
        } finally {
            c.disconnect();
        }
        return body.toString();
    }

    private static String sha256(File f) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[64 * 1024];
            for (int n; (n = in.read(buf)) > 0; ) digest.update(buf, 0, n);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
