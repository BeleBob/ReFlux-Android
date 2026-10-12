package io.openflux.app;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.PasswordTransformationMethod;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONObject;

import android.util.Base64;

import io.openflux.bridge.mobile.Mobile;

public final class MainActivity extends ThemedActivity {
    final HomePage homePage = new HomePage(this);
    final SettingsPage settingsPage = new SettingsPage(this);
    final ProfileEditor profileEditor = new ProfileEditor(this);
    final AppPicker appPicker = new AppPicker(this);
    final LogsPage logsPage = new LogsPage(this);
    // Named explicitly (instead of the implicit per-Activity-class file from
    // getPreferences()) so OpenFluxTileService can read the connection mode
    // and network settings without depending on Activity.getPreferences()'s
    // undocumented file-naming behavior.
    static final String SETTINGS_PREFS_NAME = "openflux_settings";
    private static final int TUNNEL_PERMISSION_REQUEST = 42;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 43;
    private static final int NODE_WIZARD_REQUEST = 44;
    private static final int QR_IMAGE_REQUEST = 45;
    static final int SCRIPT_SETTINGS_REQUEST = 47;
    static final int DEFAULT_MTU = 1400;
    private static final int PAGE_HOME = 0;
    static final int PAGE_PROFILES = 1;
    private static final int PAGE_LOGS = 2;
    static final int PAGE_SETTINGS = 3;
    static final String MODE_TUNNEL = "tunnel";
    static final String MODE_PROXY = "proxy";
    // The phone serves as an l4 exit node for other clients.
    static final String MODE_EXIT = "exit";
    static final int DEFAULT_PROXY_PORT = 1080;
    static final String MAIN_REPO_URL = "https://github.com/p1neappleXpress/OpenFlux";
    static final String FORK_REPO_URL = "https://github.com/damnurmum/OpenFlux-Android";
    private static final String FORK_REPO_SLUG = "damnurmum/OpenFlux-Android";
    private static final int VERSION_CHECK_PENDING = 0;
    private static final int VERSION_CHECK_LATEST = 1;
    private static final int VERSION_CHECK_OUTDATED = 2;

    final Handler handler = new Handler(Looper.getMainLooper());
    boolean autoScroll = true;
    int currentPage = PAGE_HOME;

    private FrameLayout root;
    private View navScrim;
    private View navDeadZone;
    private FrameLayout content;
    private String documentUrl;
    private String encryptionSecret;
    private String transportType = "yandex";
    private String codec = "batched";
    private String maxToken = "";
    private String maxUid = "";
    ProfileStore profileStore;
    List<Profile> profiles = new ArrayList<>();
    long selectedProfileId = -1;
    // The pinned save button of the current page, if any; it stays down
    // behind the keyboard instead of riding up with it.
    View pinnedSave;
    boolean keyboardOpen;
    boolean profileEditorOpen;
    private PopupWindow profileDropdown;
    String dnsServer;
    int mtu;
    boolean killSwitchEnabled = true;
    String connectionMode = MODE_TUNNEL;
    int proxyPort = DEFAULT_PROXY_PORT;
    boolean proxyLanAccess;
    boolean proxyAuthEnabled;
    String proxyUsername = "";
    String proxyPassword = "";
    String logs = "";
    String lastShownError = "";
    private SecureSettings secureSettings;
    private boolean shellAnimated;
    private int navBottomInset;
    private int gestureInset = -1;
    private TextView versionBadge;
    private String appVersion = "";
    private String latestVersion;
    private int versionCheckState = VERSION_CHECK_PENDING;

    SharedPreferences appFilterPrefs;
    String appFilterMode = AppFilter.MODE_OFF;
    final LinkedHashSet<String> selectedApps = new LinkedHashSet<>();
    SharedPreferences domainFilterPrefs;
    final LinkedHashSet<String> enabledDomainPresets = new LinkedHashSet<>();
    final LinkedHashSet<String> customDomains = new LinkedHashSet<>();

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            homePage.updateStatus();
            String pending = Mobile.readLogs();
            if (pending != null && !pending.isEmpty()) appendLog(pending);
            handler.postDelayed(this, 500);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        vibrator = getSystemService(Vibrator.class);
        SharedPreferences prefs = getSharedPreferences(SETTINGS_PREFS_NAME, MODE_PRIVATE);
        secureSettings = new SecureSettings(this);
        // Older prototype builds used plain preferences. Remove those values:
        // connection credentials now live only in the Keystore-backed store.
        prefs.edit().remove("document_url").remove("connection_document_url")
                .remove("show_sensitive_logs").remove("join_celebration").apply();
        documentUrl = secureSettings.getString("document_url", "");
        encryptionSecret = secureSettings.getString("encryption_secret", "");
        profileStore = new ProfileStore(secureSettings);
        profiles = profileStore.load();
        selectedProfileId = profileStore.getSelectedId();
        migrateLegacyProfileIfNeeded();
        applySelectedProfileToFields();
        dnsServer = prefs.getString("dns_server", "");
        mtu = prefs.getInt("mtu", DEFAULT_MTU);
        killSwitchEnabled = prefs.getBoolean("kill_switch", true);
        String savedMode = prefs.getString("connection_mode", MODE_TUNNEL);
        connectionMode = MODE_PROXY.equals(savedMode) || MODE_EXIT.equals(savedMode) ? savedMode : MODE_TUNNEL;
        proxyPort = prefs.getInt("proxy_port", DEFAULT_PROXY_PORT);
        proxyLanAccess = prefs.getBoolean("proxy_lan_access", false);
        proxyAuthEnabled = prefs.getBoolean("proxy_auth_enabled", false);
        proxyUsername = prefs.getString("proxy_username", "");
        proxyPassword = secureSettings.getString("proxy_password", "");
        autoScroll = prefs.getBoolean("auto_scroll", true);
        darkMode = prefs.contains("dark_mode")
                ? prefs.getBoolean("dark_mode", isSystemDark())
                : isSystemDark();
        appFilterPrefs = getSharedPreferences(AppFilter.PREFS_NAME, MODE_PRIVATE);
        appFilterMode = appFilterPrefs.getString(AppFilter.KEY_MODE, AppFilter.MODE_OFF);
        appPicker.showSystemApps = appFilterPrefs.getBoolean("show_system_apps", false);
        selectedApps.addAll(appFilterPrefs.getStringSet(AppFilter.KEY_PACKAGES, Collections.emptySet()));
        domainFilterPrefs = getSharedPreferences(DomainFilter.PREFS_NAME, MODE_PRIVATE);
        enabledDomainPresets.addAll(DomainFilter.loadEnabledPresets(domainFilterPrefs));
        customDomains.addAll(DomainFilter.loadCustomDomains(domainFilterPrefs));
        appVersion = readAppVersion();
        applyPalette();
        configureSystemBars();
        buildShell();
        showPage(PAGE_HOME);
        appendLog("Готово. При первом запуске Android запросит разрешение на туннель.");
        checkForUpdates();
        requestNotificationPermissionIfNeeded();
        handleShareIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShareIntent(intent);
    }

    private void handleShareIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) return;
        importShareLink(intent.getData().toString());
        // Not again on a configuration change.
        intent.setAction(Intent.ACTION_MAIN);
    }

    private void scanShareQr() {
        new IntentIntegrator(this)
                .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                .setPrompt("Наведите камеру на QR-код OpenFlux")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
                .initiateScan();
    }

    private void pickShareQrImage() {
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.setType("image/*");
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(pick, "Выберите фото с QR-кодом"), QR_IMAGE_REQUEST);
    }

    private void importQrImage(Uri uri) {
        new Thread(() -> {
            String result = null;
            String error = null;
            Bitmap bitmap = null;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream stream = getContentResolver().openInputStream(uri)) {
                    if (stream == null) throw new IllegalArgumentException("Не удалось открыть фото");
                    BitmapFactory.decodeStream(stream, null, bounds);
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    throw new IllegalArgumentException("Файл не является изображением");
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;
                while (Math.max(bounds.outWidth / options.inSampleSize,
                        bounds.outHeight / options.inSampleSize) > 2048) options.inSampleSize *= 2;
                try (InputStream stream = getContentResolver().openInputStream(uri)) {
                    if (stream == null) throw new IllegalArgumentException("Не удалось открыть фото");
                    bitmap = BitmapFactory.decodeStream(stream, null, options);
                }
                if (bitmap == null) throw new IllegalArgumentException("Не удалось прочитать фото");
                int width = bitmap.getWidth(), height = bitmap.getHeight();
                int[] pixels = new int[width * height];
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
                BinaryBitmap image = new BinaryBitmap(new HybridBinarizer(
                        new RGBLuminanceSource(width, height, pixels)));
                java.util.Map<DecodeHintType, Object> hints = new java.util.EnumMap<>(DecodeHintType.class);
                hints.put(DecodeHintType.POSSIBLE_FORMATS,
                        java.util.Collections.singletonList(BarcodeFormat.QR_CODE));
                hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
                result = new MultiFormatReader().decode(image, hints).getText();
            } catch (Exception e) {
                error = e instanceof com.google.zxing.NotFoundException
                        ? "На фото нет QR-кода" : "Не удалось прочитать фото: " + e.getMessage();
            } finally {
                if (bitmap != null) bitmap.recycle();
            }
            String link = result, problem = error;
            runOnUiThread(() -> {
                if (link != null) importShareLink(link);
                else Toast.makeText(this, problem, Toast.LENGTH_LONG).show();
            });
        }, "read-share-qr-image").start();
    }

    // Turns an openflux:// link into a profile after the user confirms: the
    // link carries the exit's key, so nothing is saved silently.
    private void importShareLink(String link) {
        Profile p;
        try {
            JSONObject read = new JSONObject(Mobile.readShareLink(link.trim()));
            if (!read.has("config")) {
                // A dialog, not a toast: Android cuts a toast to two lines.
                new AlertDialog.Builder(this, darkMode
                        ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                        : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                        .setTitle("Не удалось добавить профиль")
                        .setMessage(shareLinkProblem(read, link.trim()))
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }
            p = Profile.fromShare(read.getJSONObject("config"));
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось прочитать QR: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this, darkMode
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("Добавить профиль?")
                .setMessage(p.name + "\n" + profileTransportSummary(p) + (p.stream
                        ? "\n\nВ коде адрес PHP-ноды: добавляйте QR только от тех, кому доверяете."
                        : "\n\nВ коде ключ шифрования ноды: добавляйте QR только от тех, кому доверяете."))
                .setPositiveButton("Добавить", (dialog, which) -> {
                    profiles.add(p);
                    profileStore.save(profiles);
                    if (isConnectionRunning()) showPage(PAGE_PROFILES);
                    else selectProfile(p.id);
                    appendLog("Профиль «" + p.name + "» добавлен по QR");
                    Toast.makeText(this, "Профиль добавлен", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // Why the core rejected a link (share.Result's code and param), in the
    // user's words; the core's English detail is the fallback.
    private static String shareLinkProblem(JSONObject r, String link) {
        String param = r.optString("param");
        switch (r.optString("code")) {
            case "not_link": return "Это не ссылка OpenFlux";
            // Any openflux:// link not under v1/: a newer format, or a start
            // mangled on the way. Shows the start (no further: the payload
            // carries the key), so a report says which.
            case "unsupported_version": return "Ссылка начинается с «" + link.substring(0, Math.min(14, link.length()))
                    + "…», а приложение понимает «openflux://v1/…». Скопируйте её заново; если начало верное, ссылка из более новой версии OpenFlux";
            case "case_changed": return "В ссылке по дороге поменялся регистр букв: скопируйте её заново";
            case "damaged": return "Ссылка повреждена или обрезана: скопируйте её заново";
            case "too_large": return "Ссылка слишком большая";
            case "bad_payload":
            case "bad_config": return "В ссылке нет настроек подключения";
            case "no_transports": return "В ссылке не указан ни один транспорт";
            case "several_need_session": return "Несколько транспортов работают только в режиме Session, а в ссылке он выключен";
            case "session_secret": return "Для Session нужен ключ шифрования не короче " + param + " символов";
            case "short_secret": return "Ключ шифрования в ссылке короче " + param + " символов";
            case "unknown_codec": return "Неизвестный кодек «" + param + "»: обновите приложение";
            case "not_shareable": return "MAX нельзя передать ссылкой: токен привязан к одному аккаунту";
            case "unknown_transport": return "Неизвестный транспорт «" + param + "»: обновите приложение";
            case "direct_no_dial": return "Direct: в ссылке нет адреса ноды";
            case "direct_needs_session": return "Direct работает только в режиме Session";
            case "unknown_mode": return "Неизвестный режим «" + param + "»: обновите приложение";
            case "stream_transport": return "Режим без сервера работает только через Cups.online или Mail.ru, а в ссылке "
                    + Profile.transportLabel(param);
            case "stream_one_transport": return "В режиме без сервера должен быть ровно один транспорт";
            case "stream_plain_only": return "В режиме без сервера нет ключа шифрования и Session, а в ссылке они есть";
            default: return "Не удалось прочитать ссылку: " + r.optString("error");
        }
    }

    // Android 13+ requires this runtime permission to actually display any
    // notification, including a foreground service's - without it the tunnel
    // and proxy services still run fine, they just show no ongoing
    // notification (no status, no speed indicator) for the user to see.
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST
                && (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            appendLog("Без разрешения на уведомления статус подключения не будет показан в шторке.");
        }
    }

    @Override public void onBackPressed() {
        if (currentPage == PAGE_SETTINGS && settingsPage.settingsDetailOpen) {
            settingsPage.closeSettingsDetail();
            return;
        }
        if (currentPage == PAGE_PROFILES && profileEditorOpen) {
            closeProfileEditor();
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onStart() {
        super.onStart();
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    @Override protected void onStop() {
        handler.removeCallbacks(refresh);
        // An animator keeps ticking off-screen; onStart's refresh restarts it.
        homePage.setRingPulsing(false);
        super.onStop();
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(background);
        window.setNavigationBarColor(background);
        // API 29+ draws its own translucent scrim behind the gesture handle
        // for contrast by default, regardless of setNavigationBarColor - it
        // shows up as a mismatched grey strip under our own dark nav bar.
        // targetSdk 35 also enforces edge-to-edge (setNavigationBarColor is
        // ignored there), so our own root background showing through behind
        // the handle is what actually determines the color on those devices.
        if (Build.VERSION.SDK_INT >= 29) {
            window.setNavigationBarContrastEnforced(false);
        }
        window.getDecorView().setSystemUiVisibility(darkMode ? 0
                : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private void buildShell() {
        int side = dp(20);
        int top = dp(16);

        // Nav is a floating, fully-rounded pill (Telegram-style) with side
        // margins - its own row here, not overlapping content, header+content
        // get the usual side margins via body.
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(side, top, side, 0);
        body.addView(buildCompactHeader(), new LinearLayout.LayoutParams(-1, -2));

        content = new FrameLayout(this);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        contentParams.topMargin = dp(16);
        body.addView(content, contentParams);

        // root is a FrameLayout so content can scroll behind the floating nav
        // pill: body fills the whole screen, a gradient scrim fades content
        // out just above the pill, and the pill itself draws on top of that.
        root = new FrameLayout(this);
        root.setBackgroundColor(background);
        root.addView(body, new FrameLayout.LayoutParams(-1, -1));
        // Fade is purely decorative and taller than the pill (it tapers out
        // above it) - it must NOT intercept clicks, or it blocks buttons that
        // are still visible (just fading a little) higher up in that taper.
        navScrim = new View(this);
        navScrim.setBackground(navScrimDrawable());
        navScrim.setClickable(false);
        root.addView(navScrim, navScrimLayoutParams());
        // Dead zone is a separate element sized to exactly the pill's own
        // footprint (not the taller fade) - only that band is blocked from
        // clicks reaching whatever content scrolled behind it.
        navDeadZone = new View(this);
        navDeadZone.setClickable(true);
        root.addView(navDeadZone, navDeadZoneLayoutParams());
        root.addView(buildBottomNav(), navLayoutParams());
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int bottom = insets.getSystemWindowInsetBottom();
            // The first call happens before any keyboard can be open, so that
            // bottom inset is purely the gesture-nav bar - remember it as the
            // baseline. With windowSoftInputMode=adjustResize, opening the
            // keyboard later inflates this same bottom inset by far more than
            // any gesture bar ever is; treat that as "keyboard open" and hide
            // the pill instead of letting it get dragged up with it.
            if (gestureInset < 0) gestureInset = bottom;
            keyboardOpen = bottom > gestureInset + dp(50);
            navBottomInset = gestureInset;
            // Edge-to-edge (targetSdk 35) means the window is not resized for
            // the keyboard: lift the page above it ourselves. The shrinking
            // ScrollView then brings the focused field back into view.
            body.setPadding(side, top + insets.getSystemWindowInsetTop(), side, keyboardOpen ? bottom : 0);
            if (pinnedSave != null) pinnedSave.setVisibility(keyboardOpen ? View.GONE : View.VISIBLE);
            View nav = root.getChildAt(root.getChildCount() - 1);
            navScrim.setVisibility(keyboardOpen ? View.GONE : View.VISIBLE);
            navDeadZone.setVisibility(keyboardOpen ? View.GONE : View.VISIBLE);
            nav.setVisibility(keyboardOpen ? View.GONE : View.VISIBLE);
            navScrim.setLayoutParams(navScrimLayoutParams());
            navDeadZone.setLayoutParams(navDeadZoneLayoutParams());
            nav.setLayoutParams(navLayoutParams());
            return insets;
        });

        setContentView(root);
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(shellAnimated ? 200 : 340).start();
        shellAnimated = true;
        disableFocusHighlight(root);
    }

    // API 26+ draws its own grey "default focus highlight" rectangle behind
    // any focusable view on top of whatever background/ripple it already has
    // - visible here as a washed-out grey box around icons. Kill it tree-wide
    // since every page gets rebuilt from scratch on each tab/page switch.
    private void disableFocusHighlight(View view) {
        view.setDefaultFocusHighlightEnabled(false);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                disableFocusHighlight(group.getChildAt(i));
            }
        }
    }

    private View buildCompactHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(dp(54));
        header.setPadding(0, dp(5), 0, dp(5));

        ImageView logo = new ImageView(this);
        logo.setContentDescription("Логотип OpenFlux");
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setBackground(rounded(Color.rgb(79, 124, 255), Color.TRANSPARENT, 0, 10));
        logo.setImageResource(R.drawable.ic_openflux_foreground);
        logo.setClipToOutline(true);
        header.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titlesParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titlesParams.leftMargin = dp(12);
        TextView title = text("OpenFlux", 21, text, true);
        TextView subtitle = text("Зашифрованный туннель", 12, secondary, false);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles, titlesParams);

        versionBadge = text("", 10, accent, true);
        versionBadge.setGravity(Gravity.CENTER);
        versionBadge.setPadding(dp(9), dp(5), dp(9), dp(5));
        header.addView(versionBadge);
        updateVersionBadge();
        return header;
    }

    private void updateVersionBadge() {
        if (versionBadge == null) return;
        String base = appVersion.isEmpty() ? "-" : appVersion;
        int fg;
        int bg;
        String label;
        switch (versionCheckState) {
            case VERSION_CHECK_LATEST:
                fg = darkMode ? Color.rgb(129, 201, 149) : Color.rgb(24, 128, 56);
                bg = darkMode ? Color.rgb(30, 46, 36) : Color.rgb(230, 245, 234);
                label = base + " | Последняя версия";
                break;
            case VERSION_CHECK_OUTDATED:
                fg = darkMode ? Color.rgb(253, 214, 99) : Color.rgb(249, 171, 0);
                bg = darkMode ? Color.rgb(56, 46, 20) : Color.rgb(255, 243, 224);
                label = base + " | Доступно обновление";
                break;
            case VERSION_CHECK_PENDING:
            default:
                fg = accent;
                bg = darkMode ? Color.rgb(38, 50, 68) : Color.rgb(232, 240, 254);
                label = base;
        }
        versionBadge.setText(label);
        versionBadge.setTextColor(fg);
        versionBadge.setBackground(rounded(bg, Color.TRANSPARENT, 0, 12));
    }

    private String readAppVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName != null ? info.versionName : "";
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private void checkForUpdates() {
        new Thread(() -> {
            String latest = fetchLatestGithubVersion();
            if (latest == null || latest.isEmpty()) return;
            handler.post(() -> {
                latestVersion = latest;
                versionCheckState = compareVersions(appVersion, latest) >= 0
                        ? VERSION_CHECK_LATEST : VERSION_CHECK_OUTDATED;
                updateVersionBadge();
            });
        }).start();
    }

    private String fetchLatestGithubVersion() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://api.github.com/repos/" + FORK_REPO_SLUG + "/releases/latest");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "OpenFlux-Android");
            if (connection.getResponseCode() != 200) return null;
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }
            String tag = new JSONObject(body.toString()).optString("tag_name", "");
            return tag.startsWith("v") ? tag.substring(1) : tag;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private int compareVersions(String a, String b) {
        String[] partsA = a.split("\\.");
        String[] partsB = b.split("\\.");
        int length = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < length; i++) {
            int valueA = versionPart(partsA, i);
            int valueB = versionPart(partsB, i);
            if (valueA != valueB) return Integer.compare(valueA, valueB);
        }
        return 0;
    }

    private int versionPart(String[] parts, int index) {
        if (index >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[index].replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Floating rounded pill, not a full-width bar - side + bottom margins,
    // with the bottom one padded out by whatever gesture-nav inset is
    // currently known (see buildShell()'s insets listener).
    private FrameLayout.LayoutParams navLayoutParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, navHeight(), Gravity.BOTTOM);
        params.leftMargin = dp(12);
        params.rightMargin = dp(12);
        params.bottomMargin = dp(10) + navBottomInset;
        return params;
    }

    private int navHeight() {
        return dp(68);
    }

    // Gradient scrim behind the pill: transparent at the top, fading down to
    // the app background, so content scrolling behind the pill fades out
    // instead of being clipped by a flat rectangle. Purely decorative -
    // taller than the pill itself, so it must stay non-clickable.
    private FrameLayout.LayoutParams navScrimLayoutParams() {
        int height = navHeight() + dp(10) + navBottomInset + dp(48);
        return new FrameLayout.LayoutParams(-1, height, Gravity.BOTTOM);
    }

    // Dead zone: full width (unlike the floating pill, which has side
    // margins), but only as tall as the pill's own footprint - it stops
    // exactly where the pill starts, so it never blocks clicks on content
    // still visible higher up in the fade above it.
    private FrameLayout.LayoutParams navDeadZoneLayoutParams() {
        int height = navHeight() + dp(10) + navBottomInset;
        return new FrameLayout.LayoutParams(-1, height, Gravity.BOTTOM);
    }

    private Drawable navScrimDrawable() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, background});
        return drawable;
    }

    private View buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(4), dp(6), dp(4), dp(6));
        nav.setBackground(rounded(surface, border, 1, 34));
        nav.setElevation(dp(4));
        nav.addView(navItem(R.drawable.ic_home, "Главная", PAGE_HOME), weighted());
        nav.addView(navItem(R.drawable.ic_public, "Профили", PAGE_PROFILES), weighted());
        nav.addView(navItem(R.drawable.ic_terminal, "Логи", PAGE_LOGS), weighted());
        nav.addView(navItem(R.drawable.ic_settings, "Настройки", PAGE_SETTINGS), weighted());
        return nav;
    }

    private View navItem(int icon, String label, int page) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setBackground(ripple(Color.TRANSPARENT, 14));
        item.setContentDescription(label);
        boolean active = page == currentPage;

        // Active tab's icon sits on its own colored pill, not just a tinted
        // glyph on the bar's background.
        FrameLayout iconWrap = new FrameLayout(this);
        if (active) iconWrap.setBackground(rounded(accent, Color.TRANSPARENT, 0, 18));
        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        image.setImageTintList(ColorStateList.valueOf(active ? Color.WHITE : secondary));
        iconWrap.addView(image, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        item.addView(iconWrap, new LinearLayout.LayoutParams(dp(48), dp(40)));
        if (active) {
            iconWrap.setScaleX(0.6f);
            iconWrap.setScaleY(0.6f);
            iconWrap.animate().scaleX(1f).scaleY(1f).setDuration(280)
                    .setInterpolator(new OvershootInterpolator(4f)).start();
        }
        item.setOnClickListener(v -> {
            if (page == currentPage) return;
            tap(v);
            showPage(page);
        });
        return item;
    }

    void showPage(int page) {
        captureSettings();
        if (page != PAGE_SETTINGS) settingsPage.settingsDetailOpen = false;
        if (page != PAGE_PROFILES) profileEditorOpen = false;
        currentPage = page;
        pinnedSave = null;
        View pageView = page == PAGE_HOME ? homePage.buildHomePage()
                : page == PAGE_PROFILES ? buildProfilesPage()
                : page == PAGE_LOGS ? logsPage.buildLogsPage() : settingsPage.buildSettingsPage();
        crossfadeContent(pageView);
        View oldNav = root.getChildAt(root.getChildCount() - 1);
        ViewGroup.LayoutParams navParams = oldNav.getLayoutParams();
        root.removeView(oldNav);
        root.addView(buildBottomNav(), navParams);
        homePage.updateStatus();
        disableFocusHighlight(root);
    }

    // crossfadeContent swaps the FrameLayout's page content with a short fade
    // + rise instead of an instant cut, used for both outer tab switches and
    // Settings sub-tab switches.
    private void crossfadeContent(View newView) {
        int staleCount = content.getChildCount();
        View[] stale = new View[staleCount];
        for (int i = 0; i < staleCount; i++) stale[i] = content.getChildAt(i);

        newView.setAlpha(0f);
        newView.setTranslationY(dp(8));
        content.addView(newView, new FrameLayout.LayoutParams(-1, -1));
        newView.animate().alpha(1f).translationY(0f).setDuration(280).setStartDelay(40)
                .setInterpolator(EMPHASIZED).withLayer().start();

        for (View old : stale) {
            old.animate().cancel();
            old.animate().alpha(0f).setDuration(140).withLayer()
                    .withEndAction(() -> content.removeView(old)).start();
        }
    }

    // migrateLegacyProfileIfNeeded turns a pre-0.6.0 install's single global
    // document URL / secret into the first profile, so an existing user's
    // connection keeps working without having to re-enter anything.
    private void migrateLegacyProfileIfNeeded() {
        if (!profiles.isEmpty()) return;
        if (!isValidDocumentUrl(documentUrl)) return;
        Profile migrated = new Profile();
        migrated.id = System.currentTimeMillis();
        migrated.name = "Профиль 1";
        migrated.icon = "ic_public";
        migrated.transportType = "yandex";
        migrated.documentUrl = documentUrl;
        migrated.encryptionSecret = encryptionSecret;
        profiles.add(migrated);
        selectedProfileId = migrated.id;
        profileStore.save(profiles);
        profileStore.setSelectedId(selectedProfileId);
    }

    Profile selectedProfile() {
        for (Profile p : profiles) if (p.id == selectedProfileId) return p;
        return null;
    }

    // applySelectedProfileToFields refreshes the plain documentUrl/
    // encryptionSecret/transportType fields that toggleConnection/startTunnel/
    // startProxy already read, from whichever profile is currently selected.
    void applySelectedProfileToFields() {
        Profile p = selectedProfile();
        if (p != null) {
            documentUrl = p.documentUrl;
            encryptionSecret = p.encryptionSecret;
            transportType = p.transportType;
            codec = p.codec;
            maxToken = p.maxToken;
            maxUid = p.maxUid;
        } else {
            documentUrl = "";
            encryptionSecret = "";
            transportType = "yandex";
            codec = "batched";
            maxToken = "";
            maxUid = "";
        }
    }

    int profileTransportIcon(Profile profile) {
        if (profile == null) return R.drawable.ic_public;
        String type = profile.transportType;
        int priority = profile.priority;
        if (profile.session) {
            for (Profile.Transport transport : profile.extraTransports) {
                if (transport.priority > priority) {
                    type = transport.type;
                    priority = transport.priority;
                }
            }
        }
        switch (type) {
            case "yandex":
            case "vyandex":
            case "boards":
            case "telemost": return R.drawable.ic_yandex;
            case "mailru": return R.drawable.ic_mailru;
            case "cupsonline":
            case "script": return R.drawable.ic_code;
            case "oneme": return R.drawable.ic_max;
            case "direct": return R.drawable.ic_link;
            default: return R.drawable.ic_public;
        }
    }

    String transportLabel(String type) {
        return Profile.transportLabel(type);
    }

    String carrierLabel(String type, String scriptId) {
        return "script".equals(type) ? "JS: " + scriptId : transportLabel(type);
    }

    String profileTransportSummary(Profile p) {
        if (p.stream) return "Без сервера: " + transportLabel(p.transportType);
        if (p.session) {
            StringBuilder summary = new StringBuilder("Session: ").append(carrierLabel(p.transportType, p.scriptId));
            for (Profile.Transport t : p.extraTransports) summary.append(" + ").append(carrierLabel(t.type, t.scriptId));
            return summary.toString();
        }
        return transportLabel(p.transportType);
    }

    void selectProfile(long id) {
        if (id != selectedProfileId && isConnectionRunning()) {
            Toast.makeText(this, "Сначала отключитесь, затем меняйте профиль", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedProfileId = id;
        profileStore.setSelectedId(id);
        applySelectedProfileToFields();
        showPage(PAGE_HOME);
    }

    void closeProfileEditor() {
        profileEditorOpen = false;
        showPage(PAGE_PROFILES);
    }

    void deleteProfile(Profile profile) {
        if (isConnectionRunning() && profile.id == selectedProfileId) {
            Toast.makeText(this, "Нельзя удалить активный профиль во время подключения", Toast.LENGTH_SHORT).show();
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Удалить профиль?")
                .setMessage("«" + profile.name + "» будет удалён без возможности восстановления.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (dialog, which) -> {
                    profiles.remove(profile);
                    profileStore.save(profiles);
                    if (profile.id == selectedProfileId) {
                        selectedProfileId = profiles.isEmpty() ? -1 : profiles.get(0).id;
                        profileStore.setSelectedId(selectedProfileId);
                        applySelectedProfileToFields();
                    }
                    Toast.makeText(this, "Профиль удалён", Toast.LENGTH_SHORT).show();
                    profileEditorOpen = false;
                    showPage(PAGE_PROFILES);
                })
                .show();
    }

    void showProfileDropdown(View anchor) {
        if (profiles.isEmpty()) {
            showPage(PAGE_PROFILES);
            return;
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), dp(4), dp(4), dp(4));
        View selectedRow = null;
        for (Profile p : profiles) {
            boolean selected = p.id == selectedProfileId;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(ripple(Color.TRANSPARENT, 8));
            row.setClickable(true);
            row.addView(iconBubble(profileTransportIcon(p), selected),
                    new LinearLayout.LayoutParams(dp(34), dp(34)));
            TextView nameView = text(p.name, 14, text, selected);
            nameView.setSingleLine(true);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
            nameParams.leftMargin = dp(12);
            nameParams.rightMargin = dp(8);
            row.addView(nameView, nameParams);
            if (selected) row.addView(icon(R.drawable.ic_check, accent), new LinearLayout.LayoutParams(dp(18), dp(18)));
            row.setOnClickListener(v -> {
                tap(v);
                selectProfile(p.id);
                if (profileDropdown != null) profileDropdown.dismiss();
            });
            content.addView(row, new LinearLayout.LayoutParams(-1, dp(56)));
            if (selected) selectedRow = row;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setBackground(rounded(surface, border, 1, 12));
        scroll.setClipToOutline(true);
        scroll.addView(content);
        int popupWidth = anchor.getWidth();
        content.measure(View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupHeight = Math.min(content.getMeasuredHeight(), dp(176));
        PopupWindow popup = new PopupWindow(scroll, popupWidth, popupHeight, true);
        popup.setElevation(dp(8));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        profileDropdown = popup;
        popup.setOnDismissListener(() -> profileDropdown = null);
        popup.showAsDropDown(anchor, 0, dp(4));
        View focusRow = selectedRow;
        if (focusRow != null) scroll.post(() -> scroll.scrollTo(0, Math.max(0, focusRow.getTop() - dp(16))));
    }

    private void showProfileActionMenu(View anchor) {
        showActionMenu(anchor,
                new String[]{"Добавить профиль", "Установить ноду на VPS"},
                new String[]{"Настроить транспорт и подключение", "Создать канал на своём сервере"},
                new int[]{R.drawable.ic_add, R.drawable.ic_terminal},
                new Runnable[]{() -> profileEditor.openProfileEditor(null), this::openNodeWizard});
    }

    private void openNodeWizard() {
        if (OpenFluxTunnelService.isRunning() || OpenFluxProxyService.isRunning()
                || OpenFluxExitService.isRunning()) {
            Toast.makeText(this, "Отключите OpenFlux перед настройкой ноды", Toast.LENGTH_LONG).show();
            return;
        }
        startActivityForResult(new Intent(this, NodeWizardActivity.class), NODE_WIZARD_REQUEST);
    }

    private void showQrActionMenu(View anchor) {
        showActionMenu(anchor,
                new String[]{"Сканировать камерой", "Выбрать фото из галереи"},
                new String[]{"Наведите камеру на QR-код", "Прочитать QR-код на снимке"},
                new int[]{R.drawable.ic_qr_scan, R.drawable.ic_image},
                new Runnable[]{this::scanShareQr, this::pickShareQrImage});
    }

    private void showActionMenu(View anchor, String[] titles, String[] details, int[] icons, Runnable[] actions) {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(5), dp(5), dp(5), dp(5));
        menu.setBackground(rounded(surface, border, 1, 12));
        PopupWindow popup = new PopupWindow(menu, dp(280), -2, true);
        popup.setElevation(dp(8));
        popup.setOutsideTouchable(true);
        for (int i = 0; i < titles.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(10), dp(10), dp(10));
            row.setBackground(ripple(Color.TRANSPARENT, 8));
            row.addView(iconBubble(icons[i]), new LinearLayout.LayoutParams(dp(38), dp(38)));
            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1f);
            copyParams.leftMargin = dp(12);
            copy.addView(text(titles[i], 14, text, true));
            copy.addView(text(details[i], 11, secondary, false));
            row.addView(copy, copyParams);
            row.setOnClickListener(v -> {
                tap(v);
                popup.dismiss();
                actions[index].run();
            });
            menu.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        popup.showAsDropDown(anchor, anchor.getWidth() - dp(280), dp(4));
    }

    void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show();
        }
    }

    // Content now scrolls behind the floating nav pill (for the fade effect
    // behind it), so every scrollable page needs this much extra bottom room
    // or its last item - often a Save button - ends up stuck under the pill.
    @Override int navClearance() {
        return navHeight() + dp(10) + navBottomInset + dp(16);
    }

    private View buildProfilesPage() {
        LinearLayout page = page();
        if (profileEditorOpen) {
            page.addView(profileEditor.buildProfileEditorHeader());
            LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(-1, 0, 1f);
            editorParams.topMargin = dp(16);
            View editor = wrapScroll(profileEditor.buildProfileEditor());
            // The save button is pinned below instead, above the nav pill.
            scrollBottom(editor, dp(8));
            page.addView(editor, editorParams);
            Button save = primaryButton("Сохранить профиль", () -> profileEditor.saveProfileFromEditor(
                    profileEditor.profileNameInput.getText().toString().trim(),
                    profileEditor.urlInput.getText().toString().trim(),
                    profileEditor.encryptionInput.getText().toString().trim(),
                    profileEditor.maxTokenInput.getText().toString().trim(),
                    profileEditor.maxUidInput.getText().toString().trim()));
            LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(52));
            saveParams.topMargin = dp(10);
            saveParams.bottomMargin = navClearance();
            page.addView(save, saveParams);
            pinnedSave = save;
            if (keyboardOpen) save.setVisibility(View.GONE);
            return page;
        }

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Профили", 25, text, true));
        titles.addView(text("Наборы параметров для разных серверов", 12, secondary, false), matchWrap());
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageButton scanButton = iconButton(R.drawable.ic_qr_scan, "Загрузить QR-код");
        scanButton.setOnClickListener(v -> {
            tap(v);
            showQrActionMenu(v);
        });
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        scanParams.rightMargin = dp(8);
        header.addView(scanButton, scanParams);
        ImageButton addButton = iconButton(R.drawable.ic_add, "Добавить профиль");
        addButton.setOnClickListener(v -> {
            tap(v);
            showProfileActionMenu(v);
        });
        header.addView(addButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        page.addView(header);

        if (profiles.isEmpty()) {
            TextView empty = text("Пока нет ни одного профиля. Нажмите + и добавьте первый.", 13, secondary, false);
            LinearLayout.LayoutParams emptyParams = matchWrap();
            emptyParams.topMargin = dp(24);
            page.addView(empty, emptyParams);
            return wrapScroll(page);
        }

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackground(rounded(surface, border, 1, 12));
        for (int i = 0; i < profiles.size(); i++) {
            list.addView(buildProfileListRow(profiles.get(i)));
            if (i < profiles.size() - 1) addDivider(list, 0);
        }
        LinearLayout.LayoutParams listParams = matchWrap();
        listParams.topMargin = dp(20);
        page.addView(list, listParams);
        return wrapScroll(page);
    }

    private View buildProfileListRow(Profile p) {
        boolean selected = p.id == selectedProfileId;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(6), dp(10));
        row.setBackground(ripple(Color.TRANSPARENT, 0));
        row.setClickable(true);
        row.setFocusable(true);
        row.addView(iconBubble(profileTransportIcon(p), selected),
                new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1f);
        copyParams.leftMargin = dp(16);
        copy.addView(text(p.name, 15, text, selected));
        copy.addView(text(profileTransportSummary(p) + (selected ? " · активен" : ""), 12, secondary, false));
        row.addView(copy, copyParams);
        ImageButton editButton = iconButton(R.drawable.ic_settings, "Изменить профиль «" + p.name + "»");
        editButton.setOnClickListener(v -> {
            tap(v);
            profileEditor.openProfileEditor(p);
        });
        row.addView(editButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
        ImageButton deleteButton = iconButton(R.drawable.ic_delete, "Удалить профиль «" + p.name + "»");
        deleteButton.setOnClickListener(v -> {
            tap(v);
            deleteProfile(p);
        });
        row.addView(deleteButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
        row.setOnClickListener(v -> {
            bounce(v);
            selectProfile(p.id);
        });
        return row;
    }

    static int parsePriority(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // The scrolling page content that holds view, so the whole page
    // reflows together.
    @Override ViewGroup transitionRoot(View view) {
        View child = view;
        for (android.view.ViewParent p = view.getParent(); p instanceof ViewGroup; p = p.getParent()) {
            if (p instanceof ScrollView || p == content) return child instanceof ViewGroup ? (ViewGroup) child : null;
            child = (View) p;
        }
        return null;
    }

    void switchTheme(boolean checked) {
        if (darkMode == checked) return;
        captureSettings();
        View oldRoot = root;
        oldRoot.animate().cancel();
        oldRoot.animate().alpha(0.15f).setDuration(110).withEndAction(() -> {
            darkMode = checked;
            getSharedPreferences(SETTINGS_PREFS_NAME, MODE_PRIVATE).edit().putBoolean("dark_mode", darkMode).apply();
            applyPalette();
            configureSystemBars();
            buildShell();
            showPage(currentPage);
        }).start();
    }

    // Called on every navigation (showPage), not just when leaving a settings
    // page with unsaved edits - so it must NOT commit the Network/Mode draft
    // fields (applyNetworkSettings/applyModeSettings do that, only when the
    // save button is pressed). The log buffer is kept separately from the
    // filtered view, so switching pages must not copy the visible text back.
    void captureSettings() {
        profileEditor.urlInput = null;
        profileEditor.encryptionInput = null;
        profileEditor.profileNameInput = null;
        settingsPage.dnsInput = null;
        settingsPage.mtuInput = null;
        settingsPage.proxyPortInput = null;
        settingsPage.proxyUsernameInput = null;
        settingsPage.proxyPasswordInput = null;
        logsPage.logView = null;
        logsPage.logScroll = null;
    }

    void persistSettings() {
        secureSettings.putString("document_url", documentUrl);
        secureSettings.putString("encryption_secret", encryptionSecret);
        secureSettings.putString("proxy_password", proxyPassword);
        getSharedPreferences(SETTINGS_PREFS_NAME, MODE_PRIVATE).edit()
                .remove("connection_document_url")
                .putString("dns_server", dnsServer)
                .putInt("mtu", mtu)
                .putBoolean("kill_switch", killSwitchEnabled)
                .putString("connection_mode", connectionMode)
                .putInt("proxy_port", proxyPort)
                .putBoolean("proxy_lan_access", proxyLanAccess)
                .putBoolean("proxy_auth_enabled", proxyAuthEnabled)
                .putString("proxy_username", proxyUsername)
                .putBoolean("auto_scroll", autoScroll)
                .putBoolean("dark_mode", darkMode)
                .commit();
    }

    boolean isProxyMode() {
        return MODE_PROXY.equals(connectionMode);
    }

    boolean isExitMode() {
        return MODE_EXIT.equals(connectionMode);
    }

    static String modeLabel(String mode) {
        if (MODE_PROXY.equals(mode)) return "Прокси (SOCKS5)";
        if (MODE_EXIT.equals(mode)) return "Выходная нода (L4)";
        return "Туннель (весь трафик)";
    }

    boolean isConnectionRunning() {
        if (isExitMode()) return OpenFluxExitService.isRunning();
        return isProxyMode() ? OpenFluxProxyService.isRunning() : OpenFluxTunnelService.isRunning();
    }

    String connectionStatus() {
        if (isExitMode()) return OpenFluxExitService.getStatus();
        return isProxyMode() ? OpenFluxProxyService.getStatus() : OpenFluxTunnelService.getStatus();
    }

    String connectionLastError() {
        if (isExitMode()) return OpenFluxExitService.getLastError();
        return isProxyMode() ? OpenFluxProxyService.getLastError() : OpenFluxTunnelService.getLastError();
    }

    long connectionStartedAt() {
        if (isExitMode()) return OpenFluxExitService.getConnectedAtMillis();
        return isProxyMode() ? OpenFluxProxyService.getConnectedAtMillis() : OpenFluxTunnelService.getConnectedAtMillis();
    }

    void stopConnection(String mode) {
        Intent stop;
        if (MODE_EXIT.equals(mode)) {
            stop = new Intent(this, OpenFluxExitService.class).setAction(OpenFluxExitService.ACTION_STOP);
        } else if (MODE_PROXY.equals(mode)) {
            stop = new Intent(this, OpenFluxProxyService.class).setAction(OpenFluxProxyService.ACTION_STOP);
        } else {
            stop = new Intent(this, OpenFluxTunnelService.class).setAction(OpenFluxTunnelService.ACTION_STOP);
        }
        startService(stop);
    }

    void toggleConnection() {
        if (isConnectionRunning()) {
            stopConnection(connectionMode);
            appendLog("Запрошена остановка: " + modeLabel(connectionMode));
            return;
        }
        String valueProblem = profileEditor.transportValueProblem(transportType,
                "oneme".equals(transportType) ? (maxToken == null ? "" : maxToken) : documentUrl, isExitMode());
        if (valueProblem != null) {
            Toast.makeText(this, valueProblem, Toast.LENGTH_LONG).show();
            showPage(PAGE_PROFILES);
            return;
        }
        Profile selected = selectedProfile();
        String scriptProblem = selected == null ? null : selected.scriptProblem(new ScriptStore(this));
        if (scriptProblem != null) {
            Toast.makeText(this, scriptProblem, Toast.LENGTH_LONG).show();
            return;
        }
        if (encryptionSecret != null && !encryptionSecret.isEmpty() && encryptionSecret.length() < 16) {
            Toast.makeText(this, "Ключ шифрования профиля должен быть не короче 16 символов, либо пустым", Toast.LENGTH_LONG).show();
            showPage(PAGE_PROFILES);
            return;
        }
        if (isProxyMode() && proxyLanAccess && proxyAuthEnabled
                && (proxyUsername.isEmpty() || proxyPassword.isEmpty())) {
            Toast.makeText(this, "Укажите логин и пароль для авторизации прокси", Toast.LENGTH_LONG).show();
            settingsPage.openSettingsDetail(SettingsPage.SETTINGS_MODE);
            return;
        }
        persistSettings();
        if (isExitMode()) {
            Intent intent = new Intent(this, OpenFluxExitService.class).setAction(OpenFluxExitService.ACTION_START);
            putProfileExtras(intent);
            startForegroundService(intent);
            appendLog("Запуск выходной ноды…");
            return;
        }
        if (isProxyMode()) {
            startProxy();
            return;
        }
        Intent permission = VpnService.prepare(this);
        if (permission != null) startActivityForResult(permission, TUNNEL_PERMISSION_REQUEST);
        else startTunnel();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == QR_IMAGE_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                importQrImage(data.getData());
            }
            return;
        }
        if (requestCode == NODE_WIZARD_REQUEST) {
            profiles = profileStore.load();
            selectedProfileId = profileStore.getSelectedId();
            applySelectedProfileToFields();
            int page = data == null ? PAGE_PROFILES
                    : data.getIntExtra(NodeWizardActivity.EXTRA_NAV_PAGE, PAGE_PROFILES);
            showPage(page >= PAGE_HOME && page <= PAGE_SETTINGS ? page : PAGE_PROFILES);
            return;
        }
        IntentResult scan = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (scan != null) {
            if (scan.getContents() != null) importShareLink(scan.getContents());
            return;
        }
        if (settingsPage.scriptsPanel != null && settingsPage.scriptsPanel.onActivityResult(requestCode, resultCode, data)) return;
        if (requestCode == SCRIPT_SETTINGS_REQUEST) {
            profileEditor.onScriptSettings(resultCode == RESULT_OK && data != null
                    ? data.getStringExtra(CaptchaActivity.EXTRA_SUBMITTED) : null);
        }
        if (requestCode == TUNNEL_PERMISSION_REQUEST && resultCode == RESULT_OK) startTunnel();
        else if (requestCode == TUNNEL_PERMISSION_REQUEST) appendLog("[ERROR] Разрешение на создание туннеля не выдано");
    }

    private void putProfileExtras(Intent intent) {
        Profile p = selectedProfile();
        if (p != null) {
            p.putConnectionExtras(intent, new ScriptStore(this));
            return;
        }
        intent.putExtra(OpenFluxTunnelService.EXTRA_DOCUMENT_URL, documentUrl);
        intent.putExtra(OpenFluxTunnelService.EXTRA_ENCRYPTION_SECRET, encryptionSecret);
        intent.putExtra(OpenFluxTunnelService.EXTRA_TRANSPORT_TYPE, transportType);
        intent.putExtra(OpenFluxTunnelService.EXTRA_CODEC, codec);
        intent.putExtra(OpenFluxTunnelService.EXTRA_MAX_TOKEN, maxToken);
        intent.putExtra(OpenFluxTunnelService.EXTRA_MAX_UID, maxUid);
    }

    private void startTunnel() {
        Intent intent = new Intent(this, OpenFluxTunnelService.class);
        intent.setAction(OpenFluxTunnelService.ACTION_START);
        putProfileExtras(intent);
        intent.putExtra(OpenFluxTunnelService.EXTRA_DNS_SERVER, dnsServer);
        intent.putExtra(OpenFluxTunnelService.EXTRA_MTU, mtu);
        startForegroundService(intent);
        appendLog("Запуск туннеля…");
    }

    private void startProxy() {
        Intent intent = new Intent(this, OpenFluxProxyService.class);
        intent.setAction(OpenFluxProxyService.ACTION_START);
        putProfileExtras(intent);
        intent.putExtra(OpenFluxProxyService.EXTRA_PORT, proxyPort);
        intent.putExtra(OpenFluxProxyService.EXTRA_LAN_ACCESS, proxyLanAccess);
        if (proxyLanAccess && proxyAuthEnabled) {
            intent.putExtra(OpenFluxProxyService.EXTRA_USERNAME, proxyUsername);
            intent.putExtra(OpenFluxProxyService.EXTRA_PASSWORD, proxyPassword);
        }
        startForegroundService(intent);
        appendLog("Запуск прокси…");
    }

    // getLocalIpAddress finds this device's IPv4 address on whatever network
    // it's currently attached to (Wi-Fi, a hotspot it joined, Ethernet, ...)
    // by scanning network interfaces directly, so it works the same way
    // regardless of connection type and needs no extra permission.
    String getLocalIpAddress() {
        try {
            for (NetworkInterface intf : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!intf.isUp() || intf.isLoopback()) continue;
                for (InetAddress address : Collections.list(intf.getInetAddresses())) {
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException ignored) {
        }
        return null;
    }

    boolean isValidDocumentUrl(String value) {
        if (value == null || !value.startsWith("https://")) return false;
        try {
            android.net.Uri uri = android.net.Uri.parse(value);
            return uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    void appendLog(String value) {
        String timestamp = "[" + new java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
                .format(new java.util.Date()) + "]";
        String[] incoming = value.split("\n", -1);
        StringBuilder stamped = new StringBuilder();
        for (int i = 0; i < incoming.length; i++) {
            if (i > 0) stamped.append('\n');
            stamped.append(timestamp).append(' ').append(incoming[i]);
        }
        value = stamped.toString();
        if (!logs.isEmpty()) logs += "\n";
        logs += value;
        if (logs.length() > 60000) logs = logs.substring(logs.length() - 40000);
        logsPage.renderLogs();
    }

}
