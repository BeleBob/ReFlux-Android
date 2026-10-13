package io.openflux.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.openflux.bridge.mobile.Mobile;

import org.json.JSONObject;

public final class OpenFluxTunnelService extends VpnService {
    public static final String ACTION_START = "io.openflux.app.START";
    public static final String ACTION_STOP = "io.openflux.app.STOP";
    public static final String EXTRA_DOCUMENT_URL = "document_url";
    public static final String EXTRA_ENCRYPTION_SECRET = "encryption_secret";
    public static final String EXTRA_TRANSPORT_TYPE = "transport_type";
    public static final String EXTRA_DNS_SERVER = "dns_server";
    public static final String EXTRA_MTU = "mtu";
    public static final String EXTRA_CODEC = "codec";
    public static final String EXTRA_MAX_TOKEN = "max_token";
    public static final String EXTRA_MAX_UID = "max_uid";
    public static final String EXTRA_SESSION_TRANSPORTS = "session_transports";
    public static final String EXTRA_STREAM = "stream";

    private static final String CHANNEL_ID = "openflux_tunnel";
    private static final int NOTIFICATION_ID = 7;
    private static volatile boolean running;
    private static volatile String status = Status.STOPPED;
    private static volatile String lastError = "";
    // Lives on the service, not the Activity: MainActivity can be destroyed
    // and recreated (low memory, long time away) while this foreground
    // service keeps running, and the uptime shown on Home must survive that.
    private static volatile long connectedAtMillis;

    private final ExecutorService workers = Executors.newFixedThreadPool(3);
    private final Object outputLock = new Object();
    private final AtomicInteger generation = new AtomicInteger();
    // The intent the tunnel was last started with: a configuration the
    // server offers restarts it with this one's DNS and MTU.
    private volatile Intent lastStart;
    private final AtomicBoolean applyingOffer = new AtomicBoolean();

    // The internet check through the tunnel (checkTunnel): when nothing came
    // in for PROBE_EVERY_MS, two addresses are fetched through the tunnel,
    // one in Russia and one abroad; PROBE_FAILURES checks in a row with
    // neither answering bring the tunnel up again.
    private static final long PROBE_EVERY_MS = 3 * 60_000;
    private static final long PROBE_RETRY_MS = 30_000;
    private static final int PROBE_FAILURES = 3;
    private static final String[] PROBE_URLS = {
            "http://ya.ru/", "http://connectivitycheck.gstatic.com/generate_204"};
    private final AtomicBoolean probing = new AtomicBoolean();
    private volatile long lastProbeAt;
    private volatile long lastProbeReceived;
    private volatile int probeFailures;
    private final AtomicBoolean awaitingCaptcha = new AtomicBoolean();
    // Session mode (Mobile.startSession): the profile's transport list as
    // JSON, or empty for the classic single-transport mode.
    private volatile String sessionTransports = "";
    // Stream mode (Mobile.startStreamPacket, the PHP exit): its transport
    // type, or empty for the other modes.
    private volatile String streamType = "";
    private volatile boolean active;
    private ParcelFileDescriptor tunnel;
    private FileInputStream tunnelInput;
    private FileOutputStream tunnelOutput;

    private final AtomicLong bytesSent = new AtomicLong();
    private final AtomicLong bytesReceived = new AtomicLong();
    private final Handler notificationHandler = new Handler(Looper.getMainLooper());
    // 0 means "currently connected" - set the first time healthChecker
    // observes a drop, used to measure how long we've been down for the
    // Kill Switch off opt-out watchdog below.
    private volatile long disconnectedSinceMs;
    private static final long KILL_SWITCH_GRACE_MS = 30_000;
    // Pauses between attempts to bring the tunnel up (startTunnel).
    private static final long CONNECT_RETRY_MIN_MS = 2_000;
    private static final long CONNECT_RETRY_MAX_MS = 60_000;
    private long lastSampledSent;
    private long lastSampledReceived;
    private long lastSampledAt;
    // Read by MainActivity's Home screen (getSentPerSec/getReceivedPerSec)
    // to show live speed there too, not just in the notification.
    private static volatile long sentPerSec;
    private static volatile long receivedPerSec;
    private final Runnable speedUpdater = new Runnable() {
        @Override public void run() {
            // The core's lines get their time now, with the screen closed too.
            LogBuffer.drain();
            long now = SystemClock.elapsedRealtime();
            long elapsedMs = Math.max(1, now - lastSampledAt);
            long sent = bytesSent.get();
            long received = bytesReceived.get();
            sentPerSec = (sent - lastSampledSent) * 1000 / elapsedMs;
            receivedPerSec = (received - lastSampledReceived) * 1000 / elapsedMs;
            lastSampledSent = sent;
            lastSampledReceived = received;
            lastSampledAt = now;
            String speeds = "↑ " + formatSpeed(OpenFluxTunnelService.this, sentPerSec) + "   ↓ " + formatSpeed(OpenFluxTunnelService.this, receivedPerSec);
            // The carrier traffic goes through right now; follows failover.
            String carrier = Profile.carriersLabel(OpenFluxTunnelService.this, Mobile.currentTransports(), streamType);
            updateNotification(carrier.isEmpty() ? speeds : speeds + " · " + carrier);
            notificationHandler.postDelayed(this, 1000);
        }
    };

    // healthChecker keeps Status.CONNECTED honest: without it, status is set
    // once on initial connect and never revisited, so if the underlying
    // Yandex Docs transport drops and silently retries (it keeps retrying
    // on its own - see transport.DefaultConfig's MaxReconnectAttempts) the
    // UI would keep showing a green "connected" while every packet is
    // actually being dropped (Mobile.send fails silently, so nothing leaks
    // unencrypted - the TUN just stops passing data). This surfaces that
    // state honestly instead of just failing silently.
    //
    // It also implements the Kill Switch opt-out: with it on (default) the
    // above drop-not-leak behavior is simply left alone. With it off, a
    // watchdog here tears the tunnel down after KILL_SWITCH_GRACE_MS of
    // continuous disconnection, restoring normal device routing instead of
    // blocking network access indefinitely.
    private final Runnable healthChecker = new Runnable() {
        @Override public void run() {
            if (running && CaptchaActivity.pending() && awaitingCaptcha.compareAndSet(false, true)) {
                // Captcha hit on a mid-session reconnect, not during the initial connect.
                int session = generation.get();
                new Thread(() -> {
                    try { awaitCaptcha(session); }
                    finally { awaitingCaptcha.set(false); }
                }).start();
            }
            if (running) {
                boolean connected = Mobile.isConnected();
                if (!connected) {
                    long now = SystemClock.elapsedRealtime();
                    if (disconnectedSinceMs == 0L) disconnectedSinceMs = now;
                    boolean killSwitch = getSharedPreferences(MainActivity.SETTINGS_PREFS_NAME, MODE_PRIVATE)
                            .getBoolean("kill_switch", true);
                    if (!killSwitch && now - disconnectedSinceMs >= KILL_SWITCH_GRACE_MS) {
                        lastError = getString(R.string.tunnel_reconnect_failed);
                        stopTunnel();
                        return;
                    }
                    if (Status.CONNECTED.equals(status)) {
                        status = Status.CONNECTING;
                    }
                    lastError = killSwitch
                            ? getString(R.string.tunnel_killswitch_reconnecting)
                            : getString(R.string.transport_reconnecting);
                } else {
                    disconnectedSinceMs = 0L;
                    if (Status.CONNECTING.equals(status) && active) {
                        status = Status.CONNECTED;
                        lastError = getString(R.string.transport_restored);
                    }
                }
            }
            if (running && active) checkConfigOffer();
            if (running && active && Mobile.isConnected()) checkTunnel();
            notificationHandler.postDelayed(this, 2000);
        }
    };

    public static boolean isRunning() { return running; }
    public static String getStatus() { return status; }
    public static String getLastError() { return lastError; }
    public static long getConnectedAtMillis() { return connectedAtMillis; }
    public static long getSentPerSec() { return sentPerSec; }
    public static long getReceivedPerSec() { return receivedPerSec; }

    // Package-private so MainActivity's Home screen can format the same
    // numbers this service already tracks (getSentPerSec/getReceivedPerSec).
    static String formatSpeed(Context context, long bytesPerSecond) {
        if (bytesPerSecond < 1024) return context.getString(R.string.speed_bps, bytesPerSecond);
        if (bytesPerSecond < 1024 * 1024) return context.getString(R.string.speed_kbps, bytesPerSecond / 1024.0);
        return context.getString(R.string.speed_mbps, bytesPerSecond / (1024.0 * 1024.0));
    }

    private void startSpeedUpdates() {
        bytesSent.set(0);
        bytesReceived.set(0);
        lastSampledSent = 0;
        lastSampledReceived = 0;
        lastSampledAt = SystemClock.elapsedRealtime();
        disconnectedSinceMs = 0L;
        notificationHandler.removeCallbacks(speedUpdater);
        notificationHandler.post(speedUpdater);
        notificationHandler.removeCallbacks(healthChecker);
        notificationHandler.postDelayed(healthChecker, 2000);
    }

    private void stopSpeedUpdates() {
        notificationHandler.removeCallbacks(speedUpdater);
        notificationHandler.removeCallbacks(healthChecker);
        sentPerSec = 0;
        receivedPerSec = 0;
    }

    // The app's language on Android before 13 (see Lang).
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopTunnel();
            return START_NOT_STICKY;
        }
        if (running) return START_STICKY;

        // A foreground service started via startForegroundService() must call
        // startForeground() right away - any early stopSelf() before that
        // (e.g. on a validation error below) would otherwise crash the app
        // with ForegroundServiceDidNotStartInTimeException.
        createNotificationChannel();
        shownNotification = getString(R.string.status_connecting);
        startForeground(NOTIFICATION_ID, notification(shownNotification));

        String transportTypeExtra = intent == null ? null : intent.getStringExtra(EXTRA_TRANSPORT_TYPE);
        final String transportType = transportTypeExtra == null || transportTypeExtra.isEmpty()
                ? "yandex" : transportTypeExtra;
        String sessionExtra = intent == null ? null : intent.getStringExtra(EXTRA_SESSION_TRANSPORTS);
        sessionTransports = sessionExtra == null ? "" : sessionExtra;
        boolean stream = intent != null && intent.getBooleanExtra(EXTRA_STREAM, false);
        streamType = stream ? transportType : "";
        // MAX takes a token; Cups.online a base64 room code, checked by the core.
        boolean needsUrl = !"oneme".equals(transportType) && !"cupsonline".equals(transportType)
                && sessionTransports.isEmpty();

        String url = intent == null ? null : intent.getStringExtra(EXTRA_DOCUMENT_URL);
        if (needsUrl && (url == null || !url.startsWith("https://"))) {
            lastError = getString(R.string.err_bad_doc_url);
            status = Status.ERROR;
            running = false;
            stopSelf();
            return START_NOT_STICKY;
        }
        if (url == null) url = "";
        String dnsServer = intent.getStringExtra(EXTRA_DNS_SERVER);
        String encryptionSecretExtra = intent.getStringExtra(EXTRA_ENCRYPTION_SECRET);
        final String encryptionSecret = encryptionSecretExtra == null ? "" : encryptionSecretExtra;
        if (!encryptionSecret.isEmpty() && encryptionSecret.length() < 16) {
            lastError = getString(R.string.err_short_secret);
            status = Status.ERROR;
            running = false;
            stopSelf();
            return START_NOT_STICKY;
        }
        String codecExtra = intent.getStringExtra(EXTRA_CODEC);
        final String codec = codecExtra == null || codecExtra.isEmpty() ? "batched" : codecExtra;
        String maxTokenExtra = intent.getStringExtra(EXTRA_MAX_TOKEN);
        final String maxToken = maxTokenExtra == null ? "" : maxTokenExtra;
        String maxUidExtra = intent.getStringExtra(EXTRA_MAX_UID);
        final String maxUid = maxUidExtra == null ? "" : maxUidExtra;
        String selectedDns = DnsServer.effective(dnsServer);
        if (selectedDns == null) {
            lastError = getString(R.string.err_dns_ipv4);
            status = Status.ERROR;
            stopSelf();
            return START_NOT_STICKY;
        }
        int mtu = Math.max(576, Math.min(1500, intent.getIntExtra(EXTRA_MTU, 1400)));

        active = true;
        running = true;
        status = Status.CONNECTING;
        lastError = "";
        int session = generation.incrementAndGet();
        int selectedMtu = mtu;
        String finalUrl = url;
        lastStart = new Intent(intent);
        workers.execute(() -> startTunnel(transportType, finalUrl, encryptionSecret, codec, maxToken, maxUid, selectedDns, selectedMtu, session));
        return START_STICKY;
    }

    private String startCarrier(String transportType, String url, String encryptionSecret, String codec, String maxToken, String maxUid) {
        if (!streamType.isEmpty()) return Mobile.startStreamPacket(streamType, url);
        String specs = sessionTransports;
        return specs.isEmpty()
                ? Mobile.start(transportType, url, encryptionSecret, codec, maxToken, maxUid)
                : Mobile.startSession(specs, encryptionSecret);
    }

    private void startTunnel(String transportType, String url, String encryptionSecret, String codec, String maxToken, String maxUid, String dnsServerParam, int mtu, int session) {
        if (!isCurrent(session)) return;
        CaptchaActivity.initCookieStore(this);
        // A start that fails, or a carrier that does not come up, is tried
        // again after a growing pause (CONNECT_RETRY_MIN_MS up to
        // CONNECT_RETRY_MAX_MS) for as long as the tunnel is on: no
        // network at the moment, a document the server is slow to open or
        // a node restarting must not leave the user with a stopped tunnel.
        // Only a check the person did not pass stops it.
        long retryMs = CONNECT_RETRY_MIN_MS;
        while (true) {
            String error = startCarrier(transportType, url, encryptionSecret, codec, maxToken, maxUid);
            // Some transports (Volga) fail Start outright on a captcha; retry
            // with the solved cookies, which the next Start replays.
            while (error != null && !error.isEmpty() && awaitCaptcha(session)) {
                error = startCarrier(transportType, url, encryptionSecret, codec, maxToken, maxUid);
            }
            if (error == null || error.isEmpty()) {
                for (int attempt = 0; isCurrent(session) && !Mobile.isConnected() && attempt < 120; attempt++) {
                    // Only the phone's own checks block connecting; the node's are
                    // handled by the health checker once the tunnel is up.
                    if (CaptchaActivity.pending() && Mobile.pendingCaptchaProxy().isEmpty()) {
                        if (!awaitCaptcha(session)) {
                            if (isCurrent(session)) fail(session, getString(R.string.check_failed));
                            return;
                        }
                        attempt = 0;
                    }
                    try { Thread.sleep(250); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (!isCurrent(session)) return;
                if (Mobile.isConnected()) break;
                error = getString(R.string.transport_timeout);
            }
            if (!isCurrent(session)) return;
            Mobile.stop();
            lastError = getString(R.string.retry_in, error, retryMs / 1000);
            status = Status.CONNECTING;
            for (long waited = 0; waited < retryMs && isCurrent(session); waited += 250) {
                try { Thread.sleep(250); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (!isCurrent(session)) return;
            retryMs = Math.min(retryMs * 2, CONNECT_RETRY_MAX_MS);
        }
        lastError = "";

        try {
            Builder builder = new Builder()
                    .setSession("ReFlux")
                    .setMtu(mtu)
                    .addAddress("10.10.10.2", 24)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer(dnsServerParam);
            applyAppFilter(builder);
            ParcelFileDescriptor established = builder.establish();
            if (established == null) throw new IOException(getString(R.string.err_no_tun));
            synchronized (outputLock) {
                if (!isCurrent(session)) {
                    established.close();
                    return;
                }
                tunnel = established;
                tunnelInput = new FileInputStream(established.getFileDescriptor());
                tunnelOutput = new FileOutputStream(established.getFileDescriptor());
            }
        } catch (IOException | IllegalArgumentException exception) {
            fail(session, exception.getMessage());
            return;
        }

        if (!isCurrent(session)) return;
        status = Status.CONNECTED;
        if (connectedAtMillis == 0L) connectedAtMillis = System.currentTimeMillis();
        startSpeedUpdates();
        FileInputStream input = tunnelInput;
        FileOutputStream output = tunnelOutput;
        workers.execute(() -> readOutgoingPackets(session, input));
        workers.execute(() -> writeIncomingPackets(session, output));
    }

    // applyAppFilter routes traffic per the user's "Приложения" settings tab:
    // whitelist mode tunnels only the selected apps, blacklist mode tunnels
    // everything except the selected apps, and "off" tunnels everything.
    // Android forbids calling both addAllowedApplication and
    // addDisallowedApplication on the same Builder, so the two modes are
    // mutually exclusive branches below. Our own package must never enter
    // the tunnel: Go opens the Yandex connection inside this process, so
    // routing our own traffic through TUN would loop it back on itself.
    private void applyAppFilter(Builder builder) {
        SharedPreferences prefs = getSharedPreferences(AppFilter.PREFS_NAME, MODE_PRIVATE);
        String mode = prefs.getString(AppFilter.KEY_MODE, AppFilter.MODE_OFF);
        Set<String> packages = prefs.getStringSet(AppFilter.KEY_PACKAGES, Collections.emptySet());

        if (AppFilter.MODE_WHITELIST.equals(mode) && !packages.isEmpty()) {
            for (String packageName : packages) {
                if (packageName.equals(getPackageName())) continue;
                try {
                    builder.addAllowedApplication(packageName);
                } catch (PackageManager.NameNotFoundException ignored) {
                    // App was uninstalled since it was selected; skip it.
                }
            }
            return;
        }

        try {
            builder.addDisallowedApplication(getPackageName());
        } catch (PackageManager.NameNotFoundException neverThrown) {
            // We are this package; it always exists.
            throw new AssertionError(neverThrown);
        }
        if (AppFilter.MODE_BLACKLIST.equals(mode)) {
            for (String packageName : packages) {
                if (packageName.equals(getPackageName())) continue;
                try {
                    builder.addDisallowedApplication(packageName);
                } catch (PackageManager.NameNotFoundException ignored) {
                    // App was uninstalled since it was selected; skip it.
                }
            }
        }
    }

    private void readOutgoingPackets(int session, FileInputStream input) {
        byte[] buffer = new byte[32767];
        try {
            while (isCurrent(session)) {
                int length = input.read(buffer);
                if (length <= 0) {
                    awaitReadable(input.getFD());
                    continue;
                }
                byte[] packet = Arrays.copyOf(buffer, length);
                if (isIpv4Tcp(packet) || isIpv4Udp(packet)) {
                    String error = Mobile.send(packet);
                    if (error != null && !error.isEmpty() && isCurrent(session)) {
                        lastError = getString(R.string.err_send_packet, error);
                    } else {
                        bytesSent.addAndGet(packet.length);
                    }
                }
            }
        } catch (IOException exception) {
            if (isCurrent(session)) fail(session, getString(R.string.err_read_tun, exception.getMessage()));
        }
    }

    // The TUN descriptor establish() returns is non-blocking: with nothing to
    // send, read() returns 0 at once and the loop above would spin a whole
    // core. Waits until the device has a packet (or a second passes, so a
    // stopped session is noticed).
    private static void awaitReadable(FileDescriptor fd) {
        StructPollfd poll = new StructPollfd();
        poll.fd = fd;
        poll.events = (short) OsConstants.POLLIN;
        try {
            Os.poll(new StructPollfd[]{poll}, 1000);
        } catch (ErrnoException ignored) {
            // EINTR and the like: the loop reads again.
        }
    }

    private void writeIncomingPackets(int session, FileOutputStream output) {
        try {
            while (isCurrent(session)) {
                // Blocks until a packet arrives; polling read() with a 2ms sleep
                // woke the CPU ~500 times a second and drained the battery.
                byte[] packet = Mobile.readTimeout(1000);
                if (packet == null || packet.length == 0) {
                    // readTimeout returns at once before the core is started.
                    Thread.sleep(2);
                    continue;
                }
                inject(session, output, packet);
                bytesReceived.addAndGet(packet.length);
            }
        } catch (IOException exception) {
            if (isCurrent(session)) fail(session, getString(R.string.err_write_tun, exception.getMessage()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void inject(int session, FileOutputStream output, byte[] packet) throws IOException {
        if (!isCurrent(session) || output == null || packet == null) return;
        synchronized (outputLock) {
            if (isCurrent(session)) output.write(packet);
        }
    }

    private boolean isCurrent(int session) {
        return active && generation.get() == session;
    }

    private boolean awaitCaptcha(int session) {
        if (!CaptchaActivity.pending()) return false;
        if (!Mobile.pendingCaptchaProxy().isEmpty()) {
            // The node's own document carrier is stuck; the tunnel itself
            // keeps working over another transport, so the status stays.
            lastError = getString(R.string.node_yandex_check);
            boolean solved = CaptchaActivity.awaitIfPending(this, () -> isCurrent(session));
            lastError = solved ? getString(R.string.cookies_sent) : "";
            return solved;
        }
        status = Status.CHECK;
        lastError = Mobile.pendingCaptchaOwn() || !Mobile.pendingCaptchaHTML().isEmpty()
                ? getString(R.string.err_transport_setup)
                : getString(R.string.err_yandex_check);
        boolean solved = CaptchaActivity.awaitIfPending(this, () -> isCurrent(session));
        if (solved) {
            status = Status.CONNECTING;
            lastError = "";
        }
        return solved;
    }

    private static boolean isIpv4Tcp(byte[] packet) {
        return packet.length >= 20 && (packet[0] >>> 4) == 4 && (packet[9] & 0xff) == 6;
    }

    // DNS and other IPv4 UDP take the same transport path to the exit node.
    private static boolean isIpv4Udp(byte[] packet) {
        return packet.length >= 20 && (packet[0] >>> 4) == 4 && (packet[9] & 0xff) == 17;
    }

    private synchronized void fail(int session, String message) {
        if (!isCurrent(session)) return;
        lastError = message == null ? getString(R.string.err_unknown) : message;
        status = Status.ERROR;
        connectedAtMillis = 0L;
        generation.incrementAndGet();
        active = false;
        stopSpeedUpdates();
        closeTunnel();
        Mobile.stop();
        running = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    // A ReFlux exit tells the client its current configuration (the core's
    // ConfigOffer) on every connection and after every change. A newer one
    // than the active profile's goes into the profile, and when it connects
    // differently (a dead document replaced on the server, a backup added)
    // the tunnel comes up again with it: the offer came over a document that
    // still works, so nobody has to send the person a new link.
    private void checkConfigOffer() {
        String offer = Mobile.pendingConfigOffer();
        if (offer == null || offer.isEmpty() || !applyingOffer.compareAndSet(false, true)) return;
        workers.execute(() -> {
            try { applyConfigOffer(offer); }
            catch (Exception e) { lastError = getString(R.string.err_apply_offer, e.getMessage()); }
            finally { applyingOffer.set(false); }
        });
    }

    private void applyConfigOffer(String offer) throws Exception {
        JSONObject o = new JSONObject(offer);
        long version = o.getLong("version");
        ProfileStore store = new ProfileStore(new SecureSettings(this));
        List<Profile> profiles = store.load();
        long selected = store.getSelectedId();
        Profile profile = null;
        for (Profile p : profiles) if (p.id == selected) profile = p;
        Intent started = lastStart;
        if (profile == null || started == null || version <= profile.configVersion) return;
        // The selected profile may not be the one running (picked while
        // connected): the offer belongs to the channel with this key.
        if (!profile.encryptionSecret.equals(started.getStringExtra(EXTRA_ENCRYPTION_SECRET))) return;
        Profile offered = Profile.fromShare(new JSONObject(Mobile.parseShareLink(Links.toCore(o.getString("link")))));
        boolean same = profile.sameConnection(offered);
        profile.takeConnection(offered);
        profile.configVersion = version;
        store.save(profiles);
        ProfileStore.markChanged(this);
        if (same) return;
        Intent next = new Intent(started);
        profile.putConnectionExtras(next, new ScriptStore(this));
        // On the main thread, where the service is started normally.
        notificationHandler.post(() -> {
            restartWith(next);
            lastError = getString(R.string.tunnel_offer_applied);
        });
    }

    // The carrier being attached says the channel is up, not that the
    // internet answers through it. While traffic comes in, it does; when
    // nothing came in for a while, the tunnel is asked to fetch a page (the
    // core's ProbeTunnel, through the exit), and a tunnel that stopped
    // carrying traffic is brought up again.
    private void checkTunnel() {
        long now = SystemClock.elapsedRealtime();
        long wait = probeFailures > 0 ? PROBE_RETRY_MS : PROBE_EVERY_MS;
        if (lastProbeAt == 0) lastProbeAt = now;
        if (now - lastProbeAt < wait) return;
        long received = bytesReceived.get();
        if (received != lastProbeReceived) {
            // Traffic came in: the tunnel works.
            lastProbeAt = now;
            lastProbeReceived = received;
            probeFailures = 0;
            return;
        }
        if (!probing.compareAndSet(false, true)) return;
        lastProbeAt = now;
        workers.execute(() -> {
            try { probeTunnel(); }
            finally { probing.set(false); }
        });
    }

    private void probeTunnel() {
        boolean unavailable = true;
        for (String url : PROBE_URLS) {
            try {
                JSONObject r = new JSONObject(Mobile.probeTunnel(url, 10_000));
                if (r.optBoolean("ok")) {
                    probeFailures = 0;
                    lastProbeReceived = bytesReceived.get();
                    return;
                }
                if (!r.optBoolean("unavailable")) unavailable = false;
            } catch (Exception e) {
                unavailable = false;
            }
        }
        if (unavailable) return; // no Session tunnel to check with
        if (++probeFailures < PROBE_FAILURES) return;
        probeFailures = 0;
        Intent next = lastStart;
        if (next == null) return;
        notificationHandler.post(() -> {
            restartWith(new Intent(next));
            lastError = getString(R.string.tunnel_probe_failed);
        });
    }

    // Brings the tunnel down and up again with next, without stopping the
    // service: the notification and the foreground state stay.
    private synchronized void restartWith(Intent next) {
        if (!running) return;
        generation.incrementAndGet();
        active = false;
        stopSpeedUpdates();
        closeTunnel();
        Mobile.stop();
        running = false;
        onStartCommand(next, 0, 0);
    }

    private synchronized void stopTunnel() {
        status = Status.STOPPING;
        generation.incrementAndGet();
        active = false;
        stopSpeedUpdates();
        closeTunnel();
        Mobile.stop();
        running = false;
        status = Status.STOPPED;
        lastError = "";
        connectedAtMillis = 0L;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy() {
        generation.incrementAndGet();
        active = false;
        stopSpeedUpdates();
        closeTunnel();
        Mobile.stop();
        running = false;
        if (!Status.ERROR.equals(status)) status = Status.STOPPED;
        connectedAtMillis = 0L;
        workers.shutdownNow();
        super.onDestroy();
    }

    private void closeTunnel() {
        synchronized (outputLock) {
            if (tunnel != null) {
                try { tunnel.close(); } catch (IOException ignored) { }
                tunnel = null;
            }
            tunnelInput = null;
            tunnelOutput = null;
        }
    }

    private void createNotificationChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "ReFlux Tunnel", NotificationManager.IMPORTANCE_LOW));
    }

    private Notification notification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, OpenFluxTunnelService.class).setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(
                this, 0, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("ReFlux")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_openflux_notification)
                .setOngoing(true)
                .setContentIntent(content)
                .addAction(R.drawable.ic_power, getString(R.string.disconnect), stopIntent)
                .build();
    }

    // Re-posting an unchanged notification every second still wakes
    // SystemUI to redraw it; idle speeds stay the same text.
    private String shownNotification = "";

    private void updateNotification(String text) {
        if (text.equals(shownNotification)) return;
        shownNotification = text;
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(text));
    }
}
