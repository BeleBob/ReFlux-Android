package io.openflux.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Updating the app from inside it. The newest GitHub release's APK for this
 * phone's processor is downloaded, checked against the release's SHA256SUMS
 * and handed to the system installer, which installs it only when it is
 * signed with the key of the installed app.
 */
final class AppUpdate {
    /** The newest release, as far as updating needs it. */
    static final class Release {
        final String version; // "1.1.4"
        final String notes; // the release text: Russian, "---", English
        final String apkName;
        final String apkUrl;
        final long apkSize;
        final String sumsUrl;

        Release(String version, String notes, String apkName, String apkUrl, long apkSize, String sumsUrl) {
            this.version = version;
            this.notes = notes;
            this.apkName = apkName;
            this.apkUrl = apkUrl;
            this.apkSize = apkSize;
            this.sumsUrl = sumsUrl;
        }
    }

    /** Download progress; cancelled() stops the download. */
    interface Progress {
        void update(long done, long total);

        boolean cancelled();
    }

    /** A failure the person can be told about in their language. */
    static final class Failure extends IOException {
        final int message;

        Failure(int message) {
            super();
            this.message = message;
        }
    }

    private static final String DIR = "updates";
    private static final int TIMEOUT_MS = 15000;

    private AppUpdate() {
    }

    /** The newest release of repo ("owner/name"), or null without one. */
    static Release fetchLatest(String repo) throws Exception {
        JSONObject r = new JSONObject(read(get("https://api.github.com/repos/" + repo + "/releases/latest")));
        String tag = r.optString("tag_name", "");
        String version = tag.startsWith("v") ? tag.substring(1) : tag;
        if (version.isEmpty()) return null;
        JSONArray assets = r.optJSONArray("assets");
        JSONObject apk = pickApk(assets);
        String sums = null;
        for (int i = 0; assets != null && i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            if ("SHA256SUMS".equals(a.optString("name"))) sums = a.optString("browser_download_url");
        }
        return new Release(version, r.optString("body", ""),
                apk == null ? null : apk.optString("name"),
                apk == null ? null : apk.optString("browser_download_url"),
                apk == null ? 0 : apk.optLong("size"), sums);
    }

    // The APK built for this phone's processor (ReFlux-android-<abi>-release.apk),
    // else the universal one.
    private static JSONObject pickApk(JSONArray assets) throws Exception {
        if (assets == null) return null;
        for (String abi : Build.SUPPORTED_ABIS) {
            JSONObject a = asset(assets, "ReFlux-android-" + abi + "-release.apk");
            if (a != null) return a;
        }
        return asset(assets, "ReFlux-android-universal-release.apk");
    }

    private static JSONObject asset(JSONArray assets, String name) throws Exception {
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            if (name.equals(a.optString("name"))) return a;
        }
        return null;
    }

    /**
     * Downloads r's APK into the app's cache, checked against SHA256SUMS,
     * and returns it. An earlier download of the same file that checks out
     * is used again.
     */
    static File download(Context context, Release r, Progress progress) throws Exception {
        if (r.apkUrl == null || r.apkUrl.isEmpty()) throw new Failure(R.string.update_no_apk);
        if (r.sumsUrl == null || r.sumsUrl.isEmpty()) throw new Failure(R.string.update_no_sums);
        String want = expectedHash(read(get(r.sumsUrl)), r.apkName);
        if (want == null) throw new Failure(R.string.update_no_sums);

        File dir = new File(context.getCacheDir(), DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File apk = new File(dir, r.apkName);
        if (apk.isFile() && want.equals(sha256(apk))) return apk;
        clean(context);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);

        File part = new File(dir, r.apkName + ".part");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        HttpURLConnection c = get(r.apkUrl);
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part)) {
            long total = c.getContentLengthLong() > 0 ? c.getContentLengthLong() : r.apkSize;
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            for (int n; (n = in.read(buf)) > 0; ) {
                if (progress.cancelled()) throw new InterruptedException();
                out.write(buf, 0, n);
                digest.update(buf, 0, n);
                done += n;
                progress.update(done, total);
            }
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            throw e;
        } finally {
            c.disconnect();
        }
        if (!want.equals(hex(digest.digest()))) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            throw new Failure(R.string.update_bad_hash);
        }
        if (!part.renameTo(apk)) throw new IOException("cannot rename " + part);
        return apk;
    }

    // The hash SHA256SUMS ("<hex>  <name>" per line) gives name, or null.
    static String expectedHash(String sums, String name) {
        for (String line : sums.split("\n")) {
            String[] f = line.trim().split("\\s+");
            if (f.length == 2 && f[1].replaceFirst("^\\*", "").equals(name) && f[0].length() == 64) {
                return f[0].toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    /** Removes downloaded updates (an installed one is no longer needed). */
    static void clean(Context context) {
        File[] files = new File(context.getCacheDir(), DIR).listFiles();
        if (files == null) return;
        for (File f : files) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    /** Whether Android lets this app hand APKs to the installer now. */
    static boolean canInstall(Context context) {
        return context.getPackageManager().canRequestPackageInstalls();
    }

    /** The settings screen where the person lets this app install apps. */
    static Intent installPermissionSettings(Context context) {
        return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.getPackageName()));
    }

    /** The system installer for apk. */
    static Intent installIntent(Context context, File apk) {
        Uri uri = androidx.core.content.FileProvider.getUriForFile(
                context, context.getPackageName() + ".files", apk);
        return new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /**
     * The part of the release text in the person's language, as plain text:
     * the notes are Russian, a "---" line, then English. The list of APKs
     * is left out: the app picks the right one itself.
     */
    static String notesFor(String body, boolean russian) {
        String text = body.replace("\r", "");
        int split = text.indexOf("\n---\n");
        if (split >= 0) text = russian ? text.substring(0, split) : text.substring(split + 5);
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\n")) {
            String l = line.trim();
            if (l.startsWith("### ")) {
                String head = l.substring(4).toLowerCase(Locale.ROOT);
                if (head.contains("apk")) break;
                l = l.substring(4);
            }
            l = l.replace("**", "").replace("`", "");
            if (l.startsWith("- ")) l = "• " + l.substring(2);
            out.append(l).append('\n');
        }
        String s = out.toString().replaceAll("\n{3,}", "\n\n").trim();
        return s.length() > 1500 ? s.substring(0, 1500).trim() + "…" : s;
    }

    private static HttpURLConnection get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "ReFlux-Android");
        if (url.startsWith("https://api.github.com/")) {
            c.setRequestProperty("Accept", "application/vnd.github+json");
        }
        int code = c.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            c.disconnect();
            throw new IOException("HTTP " + code);
        }
        return c;
    }

    private static String read(HttpURLConnection c) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) body.append(line).append('\n');
        } finally {
            c.disconnect();
        }
        return body.toString();
    }

    private static String sha256(File f) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.io.FileInputStream(f)) {
            byte[] buf = new byte[64 * 1024];
            for (int n; (n = in.read(buf)) > 0; ) digest.update(buf, 0, n);
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder(b.length * 2);
        for (byte x : b) s.append(String.format(Locale.ROOT, "%02x", x));
        return s.toString();
    }
}
