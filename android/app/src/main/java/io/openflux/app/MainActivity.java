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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
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
import java.util.Locale;
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

public final class MainActivity extends Activity {
    // Named explicitly (instead of the implicit per-Activity-class file from
    // getPreferences()) so OpenFluxTileService can read the connection mode
    // and network settings without depending on Activity.getPreferences()'s
    // undocumented file-naming behavior.
    static final String SETTINGS_PREFS_NAME = "openflux_settings";
    private static final int TUNNEL_PERMISSION_REQUEST = 42;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 43;
    private static final int NODE_WIZARD_REQUEST = 44;
    private static final int QR_IMAGE_REQUEST = 45;
    private static final int SCRIPT_SETTINGS_REQUEST = 47;
    private static final int DEFAULT_MTU = 1400;
    private static final int PAGE_HOME = 0;
    private static final int PAGE_PROFILES = 1;
    private static final int PAGE_LOGS = 2;
    private static final int PAGE_SETTINGS = 3;
    private static final int LOG_FILTER_ALL = 0;
    private static final int LOG_FILTER_IMPORTANT = 1;
    private static final int LOG_FILTER_ERRORS = 2;
    // The settings sections (buildSettingsList).
    private static final int SETTINGS_CONNECTION = 0;
    private static final int SETTINGS_TRAFFIC = 1;
    private static final int SETTINGS_INTERFACE = 2;
    private static final int SETTINGS_ADVANCED = 3;
    // The traffic section's two tabs.
    private static final int TRAFFIC_APPS = 0;
    private static final int TRAFFIC_ROUTING = 1;
    // What a recreated activity (a new language) shows again.
    private static final String STATE_PAGE = "page";
    private static final String STATE_SETTINGS_OPEN = "settings_open";
    private static final String STATE_SETTINGS_TAB = "settings_tab";
    private static final String STATE_TRAFFIC_TAB = "traffic_tab";
    private static final String MODE_TUNNEL = "tunnel";
    private static final String MODE_PROXY = "proxy";
    // The phone serves as an l4 exit node for other clients.
    private static final String MODE_EXIT = "exit";
    private static final int DEFAULT_PROXY_PORT = 1080;
    private static final String MAIN_REPO_URL = "https://github.com/p1neappleXpress/OpenFlux";
    private static final String BASE_REPO_URL = "https://github.com/damnurmum/OpenFlux-Android";
    private static final String APP_REPO_URL = "https://github.com/BeleBob/ReFlux-Android";
    private static final String APP_REPO_SLUG = "BeleBob/ReFlux-Android";
    private static final String SERVER_REPO_URL = "https://github.com/BeleBob/ReFlux";
    private static final int VERSION_CHECK_PENDING = 0;
    private static final int VERSION_CHECK_LATEST = 1;
    private static final int VERSION_CHECK_OUTDATED = 2;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean darkMode;
    // The look's settings (Theme): the phone's, light or dark, and black.
    private String themeMode;
    private boolean blackBackground;
    private Theme.Colors colors;
    private boolean urlVisible;
    private boolean autoScroll = true;
    private int currentPage = PAGE_HOME;
    private int settingsSubTab = SETTINGS_CONNECTION;
    private int trafficTab = TRAFFIC_APPS;
    private boolean settingsDetailOpen;
    private int background;
    private int surface;
    private int text;
    private int secondary;
    private int border;
    private int accent;
    private int hint;
    private int logColor;

    private FrameLayout root;
    private View navScrim;
    private View navDeadZone;
    private FrameLayout content;
    private EditText urlInput;
    private EditText encryptionInput;
    private EditText maxTokenInput;
    private EditText maxUidInput;
    private View maxFieldsContainer;
    private EditText dnsInput;
    private EditText mtuInput;
    private EditText proxyPortInput;
    private EditText proxyUsernameInput;
    private EditText proxyPasswordInput;
    private ImageButton visibilityButton;
    private ImageButton encryptionVisibilityButton;
    private ImageButton proxyPasswordVisibilityButton;
    // The log screen while it is shown (buildLogsPage), else null.
    private RecyclerView logList;
    private LogAdapter logAdapter;
    private TextView logEmpty;
    private View logJump;
    // The LogBuffer entry the list has up to, and the search (lowercase).
    private long logShownSeq;
    private String logQuery = "";
    private LinearLayout tunnelButton;
    private ImageView tunnelPowerIcon;
    private TextView tunnelButtonText;
    private TextView uptimeView;
    private TextView speedView;
    private View ringWave;
    private String documentUrl;
    private String encryptionSecret;
    private String transportType = "yandex";
    private String codec = "batched";
    private String maxToken = "";
    private String maxUid = "";
    private ProfileStore profileStore;
    private List<Profile> profiles = new ArrayList<>();
    private long selectedProfileId = -1;
    // Material 3 "emphasized decelerate": quick start, long soft landing.
    private static final android.view.animation.Interpolator EMPHASIZED =
            new android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    private ImageView proxyShareQr;
    // The pinned save button of the current page, if any; it stays down
    // behind the keyboard instead of riding up with it.
    private View pinnedSave;
    private boolean keyboardOpen;
    // Exit mode: the home card with the QR clients scan to join this phone;
    // exitShareShown is the link (or error) it currently renders.
    private LinearLayout exitShareCard;
    private String exitShareShown;
    private boolean profileEditorOpen;
    private Long editingProfileId;
    private String editorTransportType = "yandex";
    private String editorCodec = "batched";
    private String editorMaxToken = "";
    private String editorMaxUid = "";
    private boolean editorSession;
    private boolean editorStream;
    // The main carrier's JS transport and its saved settings ("script" type).
    private String editorScriptId = "";
    private JSONObject editorSettings = new JSONObject();
    private View scriptSettingsButton;
    private ScriptsPanel scriptsPanel;
    // A settings page in flight: what the script declares, where the answer goes.
    private java.util.Set<String> pendingSettingsKeys;
    private String pendingSettingsPrimary = "";
    private java.util.function.BiConsumer<String, JSONObject> pendingSettingsSave;
    private int editorPriority = 50;
    private final List<Profile.Transport> editorExtras = new ArrayList<>();
    private View sessionFieldsContainer;
    private View codecSection;
    private View urlField;
    private TextView encryptionHintView;
    private View encryptionSection;
    private View streamHintView;
    private EditText priorityInput;
    private LinearLayout extrasList;
    private EditText profileNameInput;
    private PopupWindow profileDropdown;
    private String dnsServer;
    private int mtu;
    private boolean killSwitchEnabled = true;
    private String connectionMode = MODE_TUNNEL;
    private int proxyPort = DEFAULT_PROXY_PORT;
    private boolean proxyLanAccess;
    private boolean proxyAuthEnabled;
    private String proxyUsername = "";
    private String proxyPassword = "";
    // Draft state for the connection and traffic sections: edits only take
    // effect when the section's save button is pressed, not just by
    // navigating away (see applyNetworkSettings/applyModeSettings). Seeded
    // from the live fields each time the section opens (openSettingsDetail).
    private String editorDnsServer = "";
    private boolean editorDnsAuto = true;
    private int editorMtu;
    private boolean editorKillSwitchEnabled = true;
    private String editorConnectionMode = MODE_TUNNEL;
    private int editorProxyPort = DEFAULT_PROXY_PORT;
    private boolean editorProxyLanAccess;
    private boolean editorProxyAuthEnabled;
    private String editorAppFilterMode = AppFilter.MODE_OFF;
    private String editorCustomDomains = "";
    private final LinkedHashSet<String> editorSelectedApps = new LinkedHashSet<>();
    private int logFilter = LOG_FILTER_ALL;
    private String lastShownError = "";
    private boolean encryptionVisible;
    private boolean proxyPasswordVisible;
    private SecureSettings secureSettings;
    private boolean shellAnimated;
    private int navBottomInset;
    private int gestureInset = -1;
    private ObjectAnimator ringPulse;
    private int lastTunnelButtonFill = -1;
    private Vibrator vibrator;
    private String lastAnnouncedState = "";
    private TextView versionBadge;
    private String appVersion = "";
    private String latestVersion;
    private int versionCheckState = VERSION_CHECK_PENDING;
    private final WeakHashMap<View, AnimatorSet> bounceAnimators = new WeakHashMap<>();

    private SharedPreferences appFilterPrefs;
    private String appFilterMode = AppFilter.MODE_OFF;
    private final LinkedHashSet<String> selectedApps = new LinkedHashSet<>();
    private SharedPreferences domainFilterPrefs;
    private final LinkedHashSet<String> enabledDomainPresets = new LinkedHashSet<>();
    private final LinkedHashSet<String> customDomains = new LinkedHashSet<>();
    private final LinkedHashSet<String> editorEnabledDomainPresets = new LinkedHashSet<>();
    private EditText customDomainsInput;
    private List<AppEntry> installedAppsCache;
    private List<AppEntry> allInstalledAppsCache;
    private boolean showSystemApps;
    private boolean allInstalledAppsLoading;
    private final List<Consumer<List<AppEntry>>> allInstalledAppsCallbacks = new ArrayList<>();
    private boolean installedAppsLoading;
    private int appsSettingsGeneration;
    private final List<Consumer<List<AppEntry>>> installedAppsCallbacks = new ArrayList<>();

    // When the profiles were last read; the tunnel service may change them
    // since (ProfileStore.markChanged).
    private long profilesLoadedAt;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            reloadProfilesIfChanged();
            updateStatus();
            LogBuffer.drain();
            refreshLogs();
            handler.postDelayed(this, 500);
        }
    };

    // The app's language on Android before 13 (see Lang).
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

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
        profilesLoadedAt = ProfileStore.changedAt(this);
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
        themeMode = Theme.mode(this);
        blackBackground = Theme.black(this);
        appFilterPrefs = getSharedPreferences(AppFilter.PREFS_NAME, MODE_PRIVATE);
        appFilterMode = appFilterPrefs.getString(AppFilter.KEY_MODE, AppFilter.MODE_OFF);
        showSystemApps = appFilterPrefs.getBoolean("show_system_apps", false);
        selectedApps.addAll(appFilterPrefs.getStringSet(AppFilter.KEY_PACKAGES, Collections.emptySet()));
        domainFilterPrefs = getSharedPreferences(DomainFilter.PREFS_NAME, MODE_PRIVATE);
        enabledDomainPresets.addAll(DomainFilter.loadEnabledPresets(domainFilterPrefs));
        customDomains.addAll(DomainFilter.loadCustomDomains(domainFilterPrefs));
        appVersion = readAppVersion();
        applyPalette();
        configureSystemBars();
        buildShell();
        int page = state == null ? PAGE_HOME : state.getInt(STATE_PAGE, PAGE_HOME);
        if (page == PAGE_SETTINGS && state.getBoolean(STATE_SETTINGS_OPEN)) {
            trafficTab = state.getInt(STATE_TRAFFIC_TAB, TRAFFIC_APPS);
            openSettingsDetail(state.getInt(STATE_SETTINGS_TAB, SETTINGS_CONNECTION));
        } else {
            showPage(page);
        }
        if (state == null) appendLog(getString(R.string.log_ready));
        checkForUpdates();
        requestNotificationPermissionIfNeeded();
        handleShareIntent(getIntent());
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_PAGE, currentPage);
        out.putBoolean(STATE_SETTINGS_OPEN, settingsDetailOpen);
        out.putInt(STATE_SETTINGS_TAB, settingsSubTab);
        out.putInt(STATE_TRAFFIC_TAB, trafficTab);
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
                .setPrompt(getString(R.string.qr_scan_prompt))
                .setBeepEnabled(false)
                .setOrientationLocked(false)
                .initiateScan();
    }

    private void pickShareQrImage() {
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.setType("image/*");
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(pick, getString(R.string.qr_pick_photo)), QR_IMAGE_REQUEST);
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
                    if (stream == null) throw new IllegalArgumentException(getString(R.string.qr_photo_unopenable));
                    BitmapFactory.decodeStream(stream, null, bounds);
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    throw new IllegalArgumentException(getString(R.string.qr_not_image));
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;
                while (Math.max(bounds.outWidth / options.inSampleSize,
                        bounds.outHeight / options.inSampleSize) > 2048) options.inSampleSize *= 2;
                try (InputStream stream = getContentResolver().openInputStream(uri)) {
                    if (stream == null) throw new IllegalArgumentException(getString(R.string.qr_photo_unopenable));
                    bitmap = BitmapFactory.decodeStream(stream, null, options);
                }
                if (bitmap == null) throw new IllegalArgumentException(getString(R.string.qr_photo_unreadable));
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
                        ? getString(R.string.qr_none_on_photo) : getString(R.string.qr_photo_read_failed, e.getMessage());
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
                Toast.makeText(this, shareLinkProblem(read), Toast.LENGTH_LONG).show();
                return;
            }
            p = Profile.fromShare(read.getJSONObject("config"));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.qr_read_failed, e.getMessage()), Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this, darkMode
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle(getString(R.string.profile_add_question))
                .setMessage(p.name + "\n" + profileTransportSummary(p) + "\n\n"
                        + getString(p.stream ? R.string.qr_trust_stream : R.string.qr_trust_key))
                .setPositiveButton(getString(R.string.add), (dialog, which) -> {
                    profiles.add(p);
                    profileStore.save(profiles);
                    if (isConnectionRunning()) showPage(PAGE_PROFILES);
                    else selectProfile(p.id);
                    appendLog(getString(R.string.log_profile_added_qr, p.name));
                    Toast.makeText(this, getString(R.string.profile_added), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    // Why the core rejected a link (share.Result's code and param), in the
    // user's words; the core's English detail is the fallback.
    private String shareLinkProblem(JSONObject r) {
        String param = r.optString("param");
        switch (r.optString("code")) {
            case "not_link": return getString(R.string.link_not_link);
            case "unsupported_version": return getString(R.string.link_unsupported_version);
            case "case_changed": return getString(R.string.link_case_changed);
            case "damaged": return getString(R.string.link_damaged);
            case "too_large": return getString(R.string.link_too_large);
            case "bad_payload":
            case "bad_config": return getString(R.string.link_bad_config);
            case "no_transports": return getString(R.string.link_no_transports);
            case "several_need_session": return getString(R.string.link_several_need_session);
            case "session_secret": return getString(R.string.link_session_secret, param);
            case "short_secret": return getString(R.string.link_short_secret, param);
            case "unknown_codec": return getString(R.string.link_unknown_codec, param);
            case "not_shareable": return getString(R.string.link_not_shareable);
            case "unknown_transport": return getString(R.string.link_unknown_transport, param);
            case "direct_no_dial": return getString(R.string.link_direct_no_dial);
            case "direct_needs_session": return getString(R.string.link_direct_needs_session);
            case "unknown_mode": return getString(R.string.link_unknown_mode, param);
            case "stream_transport": return getString(R.string.link_stream_transport, Profile.transportLabel(this, param));
            case "stream_one_transport": return getString(R.string.link_stream_one_transport);
            case "stream_plain_only": return getString(R.string.link_stream_plain_only);
            default: return getString(R.string.link_read_failed, r.optString("error"));
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
            appendLog(getString(R.string.log_no_notifications));
        }
    }

    @Override public void onBackPressed() {
        if (currentPage == PAGE_SETTINGS && settingsDetailOpen) {
            closeSettingsDetail();
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
        setRingPulsing(false);
        super.onStop();
    }

    private void applyPalette() {
        colors = Theme.colors(this);
        darkMode = colors.dark;
        background = colors.background;
        surface = colors.surface;
        text = colors.text;
        secondary = colors.secondary;
        border = colors.border;
        accent = colors.accent;
        hint = colors.hint;
        logColor = colors.log;
    }

    private void configureSystemBars() {
        Window window = getWindow();
        // Seen behind the pages while they cross-fade and under the keyboard.
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(background));
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
        logo.setContentDescription(getString(R.string.logo_description));
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setBackground(rounded(Color.rgb(79, 124, 255), Color.TRANSPARENT, 0, 10));
        logo.setImageResource(R.drawable.ic_openflux_foreground);
        logo.setClipToOutline(true);
        header.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titlesParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titlesParams.leftMargin = dp(12);
        TextView title = text("ReFlux", 21, text, true);
        titles.addView(title);
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
                label = getString(R.string.version_latest, base);
                break;
            case VERSION_CHECK_OUTDATED:
                fg = darkMode ? Color.rgb(253, 214, 99) : Color.rgb(249, 171, 0);
                bg = darkMode ? Color.rgb(56, 46, 20) : Color.rgb(255, 243, 224);
                label = getString(R.string.version_outdated, base);
                break;
            case VERSION_CHECK_PENDING:
            default:
                fg = accent;
                bg = colors.infoSurface;
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
            URL url = new URL("https://api.github.com/repos/" + APP_REPO_SLUG + "/releases/latest");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "ReFlux-Android");
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
        nav.addView(navItem(R.drawable.ic_home, getString(R.string.nav_home), PAGE_HOME), weighted());
        nav.addView(navItem(R.drawable.ic_public, getString(R.string.nav_profiles), PAGE_PROFILES), weighted());
        nav.addView(navItem(R.drawable.ic_terminal, getString(R.string.nav_logs), PAGE_LOGS), weighted());
        nav.addView(navItem(R.drawable.ic_settings, getString(R.string.nav_settings), PAGE_SETTINGS), weighted());
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

    private void showPage(int page) {
        captureSettings();
        if (page != PAGE_SETTINGS) settingsDetailOpen = false;
        if (page != PAGE_PROFILES) profileEditorOpen = false;
        currentPage = page;
        pinnedSave = null;
        View pageView = page == PAGE_HOME ? buildHomePage()
                : page == PAGE_PROFILES ? buildProfilesPage()
                : page == PAGE_LOGS ? buildLogsPage() : buildSettingsPage();
        crossfadeContent(pageView);
        View oldNav = root.getChildAt(root.getChildCount() - 1);
        ViewGroup.LayoutParams navParams = oldNav.getLayoutParams();
        root.removeView(oldNav);
        root.addView(buildBottomNav(), navParams);
        updateStatus();
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

    private void openSettingsDetail(int tab) {
        if (settingsDetailOpen && settingsSubTab == tab) return;
        settingsDetailOpen = true;
        settingsSubTab = tab;
        if (tab == SETTINGS_CONNECTION) {
            editorConnectionMode = connectionMode;
            editorProxyPort = proxyPort;
            editorProxyLanAccess = proxyLanAccess;
            editorProxyAuthEnabled = proxyAuthEnabled;
            editorDnsServer = dnsServer;
            editorDnsAuto = dnsServer.isEmpty();
            editorMtu = mtu;
            editorKillSwitchEnabled = killSwitchEnabled;
        } else if (tab == SETTINGS_TRAFFIC) {
            editorAppFilterMode = appFilterMode;
            editorSelectedApps.clear();
            editorSelectedApps.addAll(selectedApps);
            editorEnabledDomainPresets.clear();
            editorEnabledDomainPresets.addAll(enabledDomainPresets);
            editorCustomDomains = String.join("\n", customDomains);
        }
        showPage(PAGE_SETTINGS);
    }

    private void closeSettingsDetail() {
        if (!settingsDetailOpen) return;
        settingsDetailOpen = false;
        showPage(PAGE_SETTINGS);
    }

    // migrateLegacyProfileIfNeeded turns a pre-0.6.0 install's single global
    // document URL / secret into the first profile, so an existing user's
    // connection keeps working without having to re-enter anything.
    private void migrateLegacyProfileIfNeeded() {
        if (!profiles.isEmpty()) return;
        if (!isValidDocumentUrl(documentUrl)) return;
        Profile migrated = new Profile();
        migrated.id = System.currentTimeMillis();
        migrated.name = getString(R.string.profile_default_name);
        migrated.icon = "ic_public";
        migrated.transportType = "yandex";
        migrated.documentUrl = documentUrl;
        migrated.encryptionSecret = encryptionSecret;
        profiles.add(migrated);
        selectedProfileId = migrated.id;
        profileStore.save(profiles);
        profileStore.setSelectedId(selectedProfileId);
    }

    private Profile selectedProfile() {
        for (Profile p : profiles) if (p.id == selectedProfileId) return p;
        return null;
    }

    // applySelectedProfileToFields refreshes the plain documentUrl/
    // encryptionSecret/transportType fields that toggleConnection/startTunnel/
    // startProxy already read, from whichever profile is currently selected.
    // The tunnel service rewrites the active profile when the server sends a
    // newer configuration (ConfigOffer): read it again instead of saving the
    // old copy over it later. An open profile editor waits for its close.
    private void reloadProfilesIfChanged() {
        long changed = ProfileStore.changedAt(this);
        if (changed <= profilesLoadedAt || profileEditorOpen || profileStore == null) return;
        profilesLoadedAt = changed;
        profiles = profileStore.load();
        selectedProfileId = profileStore.getSelectedId();
        applySelectedProfileToFields();
        if (currentPage == PAGE_PROFILES) showPage(PAGE_PROFILES);
    }

    private void applySelectedProfileToFields() {
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

    private int profileTransportIcon(Profile profile) {
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
            case "boards": return R.drawable.ic_yandex;
            case "mailru": return R.drawable.ic_mailru;
            case "cupsonline":
            case "script": return R.drawable.ic_code;
            case "oneme": return R.drawable.ic_max;
            case "direct": return R.drawable.ic_link;
            default: return R.drawable.ic_public;
        }
    }

    private String transportLabel(String type) {
        return Profile.transportLabel(this, type);
    }

    private String carrierLabel(String type, String scriptId) {
        return "script".equals(type) ? "JS: " + scriptId : transportLabel(type);
    }

    private String profileTransportSummary(Profile p) {
        if (p.stream) return getString(R.string.profile_serverless, transportLabel(p.transportType));
        if (p.session) {
            StringBuilder summary = new StringBuilder("Session: ").append(carrierLabel(p.transportType, p.scriptId));
            for (Profile.Transport t : p.extraTransports) summary.append(" + ").append(carrierLabel(t.type, t.scriptId));
            return summary.toString();
        }
        return transportLabel(p.transportType);
    }

    private void selectProfile(long id) {
        if (id != selectedProfileId && isConnectionRunning()) {
            Toast.makeText(this, getString(R.string.profile_disconnect_first), Toast.LENGTH_SHORT).show();
            return;
        }
        selectedProfileId = id;
        profileStore.setSelectedId(id);
        applySelectedProfileToFields();
        showPage(PAGE_HOME);
    }

    private void openProfileEditor(Profile existing) {
        editingProfileId = existing != null ? existing.id : null;
        editorTransportType = existing != null ? existing.transportType : "yandex";
        editorCodec = existing != null ? existing.codec : "batched";
        editorMaxToken = existing != null ? existing.maxToken : "";
        editorMaxUid = existing != null ? existing.maxUid : "";
        editorSession = existing != null && existing.session;
        editorStream = existing != null && existing.stream;
        editorScriptId = existing != null ? existing.scriptId : "";
        editorSettings = Profile.copyOf(existing != null ? existing.settings : null);
        editorPriority = existing != null ? existing.priority : 50;
        editorExtras.clear();
        if (existing != null) for (Profile.Transport t : existing.extraTransports) editorExtras.add(t.copy());
        profileEditorOpen = true;
        showPage(PAGE_PROFILES);
    }

    private void closeProfileEditor() {
        profileEditorOpen = false;
        showPage(PAGE_PROFILES);
    }

    private void saveProfileFromEditor(String name, String docUrl, String secret, String token, String uid) {
        if (name.isEmpty()) {
            Toast.makeText(this, getString(R.string.profile_need_name), Toast.LENGTH_SHORT).show();
            return;
        }
        if (editorStream && !"cupsonline".equals(editorTransportType) && !"mailru".equals(editorTransportType)) {
            Toast.makeText(this, getString(R.string.stream_transport_only), Toast.LENGTH_LONG).show();
            return;
        }
        boolean mainScript = "script".equals(editorTransportType);
        if (mainScript && editorScriptId.isEmpty()) {
            Toast.makeText(this, getString(R.string.script_pick), Toast.LENGTH_LONG).show();
            return;
        }
        if (mainScript && !editorSession) {
            Toast.makeText(this, getString(R.string.script_needs_session), Toast.LENGTH_LONG).show();
            return;
        }
        // The stream mode has no key; a hidden leftover one must not be saved.
        if (editorStream) secret = "";
        String valueProblem = transportValueProblem(editorTransportType,
                "oneme".equals(editorTransportType) ? token : docUrl, !editorStream);
        if (valueProblem != null) {
            Toast.makeText(this, valueProblem, Toast.LENGTH_LONG).show();
            return;
        }
        if (!secret.isEmpty() && secret.length() < 16) {
            Toast.makeText(this, getString(R.string.err_short_secret),
                    Toast.LENGTH_LONG).show();
            return;
        }
        int mainPriority = parsePriority(priorityInput != null ? priorityInput.getText().toString() : "", 50);
        if (editorSession) {
            if (secret.length() < 16) {
                Toast.makeText(this, getString(R.string.session_needs_secret),
                        Toast.LENGTH_LONG).show();
                return;
            }
            for (Profile.Transport t : editorExtras) {
                String problem = extraTransportProblem(t);
                if (problem != null) {
                    Toast.makeText(this, problem, Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }
        Profile target = null;
        if (editingProfileId != null) {
            for (Profile p : profiles) if (p.id == editingProfileId) { target = p; break; }
        }
        boolean isNew = target == null;
        if (isNew) {
            target = new Profile();
            target.id = System.currentTimeMillis();
            profiles.add(target);
        }
        target.name = name;
        target.transportType = editorTransportType;
        target.documentUrl = docUrl;
        target.encryptionSecret = secret;
        target.codec = editorCodec;
        target.maxToken = token;
        target.maxUid = uid;
        target.session = editorSession && !editorStream;
        target.scriptId = mainScript ? editorScriptId : "";
        target.settings = mainScript ? Profile.copyOf(editorSettings) : new JSONObject();
        target.stream = editorStream;
        target.priority = mainPriority;
        target.extraTransports.clear();
        for (Profile.Transport t : editorExtras) target.extraTransports.add(t.copy());
        profileStore.save(profiles);
        if (isNew && selectedProfile() == null) selectProfile(target.id);
        if (target.id == selectedProfileId) applySelectedProfileToFields();
        Toast.makeText(this, getString(R.string.profile_saved), Toast.LENGTH_SHORT).show();
        closeProfileEditor();
    }

    private void deleteProfile(Profile profile) {
        if (isConnectionRunning() && profile.id == selectedProfileId) {
            Toast.makeText(this, getString(R.string.profile_cannot_delete_active), Toast.LENGTH_SHORT).show();
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.profile_delete_question))
                .setMessage(getString(R.string.profile_delete_text, profile.name))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                    profiles.remove(profile);
                    profileStore.save(profiles);
                    if (profile.id == selectedProfileId) {
                        selectedProfileId = profiles.isEmpty() ? -1 : profiles.get(0).id;
                        profileStore.setSelectedId(selectedProfileId);
                        applySelectedProfileToFields();
                    }
                    Toast.makeText(this, getString(R.string.profile_deleted), Toast.LENGTH_SHORT).show();
                    profileEditorOpen = false;
                    showPage(PAGE_PROFILES);
                })
                .show();
    }

    private void showProfileDropdown(View anchor) {
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
                new String[]{getString(R.string.profile_add), getString(R.string.node_install_vps)},
                new String[]{getString(R.string.profile_add_detail), getString(R.string.node_install_detail)},
                new int[]{R.drawable.ic_add, R.drawable.ic_terminal},
                new Runnable[]{() -> openProfileEditor(null), this::openNodeWizard});
    }

    private void openNodeWizard() {
        if (OpenFluxTunnelService.isRunning() || OpenFluxProxyService.isRunning()
                || OpenFluxExitService.isRunning()) {
            Toast.makeText(this, getString(R.string.node_disconnect_first), Toast.LENGTH_LONG).show();
            return;
        }
        startActivityForResult(new Intent(this, NodeWizardActivity.class), NODE_WIZARD_REQUEST);
    }

    private void showQrActionMenu(View anchor) {
        showActionMenu(anchor,
                new String[]{getString(R.string.qr_scan_camera), getString(R.string.qr_pick_gallery),
                        getString(R.string.qr_paste)},
                new String[]{getString(R.string.qr_scan_camera_detail), getString(R.string.qr_pick_gallery_detail),
                        getString(R.string.qr_paste_detail)},
                new int[]{R.drawable.ic_qr_scan, R.drawable.ic_image, R.drawable.ic_link},
                new Runnable[]{this::scanShareQr, this::pickShareQrImage, this::pasteShareLink});
    }

    // A link copied from a chat (the client bot sends one): the way in on
    // the same phone, where its own screen's QR cannot be scanned.
    private void pasteShareLink() {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
        CharSequence copied = clip != null && clip.getItemCount() > 0 ? clip.getItemAt(0).coerceToText(this) : null;
        Matcher link = SHARE_LINK.matcher(copied == null ? "" : copied);
        if (!link.find()) {
            Toast.makeText(this, getString(R.string.qr_paste_none), Toast.LENGTH_LONG).show();
            return;
        }
        importShareLink(link.group());
    }

    private static final Pattern SHARE_LINK = Pattern.compile("openflux://\\S+");

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

    private View buildProfileSelectorRow() {
        Profile p = selectedProfile();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(rounded(surface, border, 1, 11));
        row.setClickable(true);
        row.setFocusable(true);
        row.addView(iconBubble(profileTransportIcon(p)),
                new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1f);
        copyParams.leftMargin = dp(14);
        copy.addView(text(p != null ? p.name : getString(R.string.profile_none), 15, text, true));
        copy.addView(text(p != null ? profileTransportSummary(p) : getString(R.string.profile_tap_to_create),
                12, secondary, false));
        row.addView(copy, copyParams);
        row.addView(icon(R.drawable.ic_chevron_right, hint), new LinearLayout.LayoutParams(dp(20), dp(20)));
        row.setOnClickListener(v -> {
            tap(v);
            if (profiles.isEmpty()) showPage(PAGE_PROFILES);
            else if (isConnectionRunning()) {
                Toast.makeText(this, getString(R.string.profile_disconnect_first), Toast.LENGTH_SHORT).show();
            } else showProfileDropdown(v);
        });
        return row;
    }

    private View buildHomePage() {
        if (ringPulse != null) {
            ringPulse.cancel();
            ringPulse = null;
        }
        lastTunnelButtonFill = -1;

        LinearLayout page = page();

        // Big circular connect button: two static concentric rings (in the
        // spirit of the original OpenFluxAndroid app's main toggle) plus a
        // slow expanding/fading "wave" ring behind them, tinted to match the
        // button's own current color (see animateTunnelButtonFill). Status
        // and connection time live inside the button itself, Happ-style,
        // instead of a separate status card.
        FrameLayout buttonStack = new FrameLayout(this);
        buttonStack.setClipChildren(false);
        ringWave = new View(this);
        ringWave.setBackground(rounded(Color.TRANSPARENT, accent, 2, 70));
        buttonStack.addView(ringWave, new FrameLayout.LayoutParams(dp(140), dp(140), Gravity.CENTER));
        View ringOuter = new View(this);
        ringOuter.setBackground(ringOutline());
        buttonStack.addView(ringOuter, new FrameLayout.LayoutParams(dp(200), dp(200), Gravity.CENTER));
        View ringMid = new View(this);
        ringMid.setBackground(ringOutline());
        buttonStack.addView(ringMid, new FrameLayout.LayoutParams(dp(170), dp(170), Gravity.CENTER));

        tunnelButton = new LinearLayout(this);
        tunnelButton.setOrientation(LinearLayout.VERTICAL);
        tunnelButton.setGravity(Gravity.CENTER);
        tunnelButton.setClickable(true);
        tunnelButton.setFocusable(true);
        tunnelButton.setElevation(dp(2));
        tunnelPowerIcon = icon(R.drawable.ic_power, Color.WHITE);
        tunnelButton.addView(tunnelPowerIcon, new LinearLayout.LayoutParams(dp(30), dp(30)));
        tunnelButtonText = text(getString(R.string.status_stopped), 13, Color.WHITE, true);
        tunnelButtonText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusTextParams = matchWrap();
        statusTextParams.topMargin = dp(8);
        tunnelButton.addView(tunnelButtonText, statusTextParams);
        uptimeView = text("", 11, Color.WHITE, false);
        uptimeView.setAlpha(0.85f);
        uptimeView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams uptimeParams = matchWrap();
        uptimeParams.topMargin = dp(2);
        tunnelButton.addView(uptimeView, uptimeParams);
        speedView = text("", 10, Color.WHITE, false);
        speedView.setAlpha(0.7f);
        speedView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams speedParams = matchWrap();
        speedParams.topMargin = dp(2);
        tunnelButton.addView(speedView, speedParams);
        tunnelButton.setOnClickListener(v -> {
            bounce(v);
            toggleConnection();
        });
        buttonStack.addView(tunnelButton, new FrameLayout.LayoutParams(dp(140), dp(140), Gravity.CENTER));

        LinearLayout.LayoutParams stackParams = new LinearLayout.LayoutParams(dp(200), dp(200));
        stackParams.gravity = Gravity.CENTER_HORIZONTAL;
        stackParams.topMargin = dp(16);
        page.addView(buttonStack, stackParams);
        staggerIn(buttonStack, 30);

        ringWave.setAlpha(0f);
        ringPulse = ObjectAnimator.ofPropertyValuesHolder(ringWave,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.45f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.45f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.5f, 0f));
        ringPulse.setDuration(2600);
        ringPulse.setRepeatCount(ValueAnimator.INFINITE);
        ringPulse.setInterpolator(new LinearInterpolator());
        // Started/stopped from updateStatus() via setRingPulsing() based on
        // connection state, not unconditionally here.

        LinearLayout.LayoutParams selectorParams = matchWrap();
        selectorParams.topMargin = dp(20);
        View profileSelector = buildProfileSelectorRow();
        page.addView(profileSelector, selectorParams);
        staggerIn(profileSelector, 80);

        TextView summaryTitle = label(getString(R.string.home_active_params));
        LinearLayout.LayoutParams summaryTitleParams = matchWrap();
        summaryTitleParams.topMargin = dp(24);
        summaryTitleParams.bottomMargin = dp(8);
        page.addView(summaryTitle, summaryTitleParams);
        View activeParams = paramsCard(activeParamRows());
        LinearLayout.LayoutParams activeParamsParams = matchWrap();
        activeParamsParams.bottomMargin = dp(8);
        page.addView(activeParams, activeParamsParams);
        staggerIn(activeParams, 130);

        exitShareCard = null;
        exitShareShown = null;
        if (MODE_EXIT.equals(connectionMode)) {
            exitShareCard = new LinearLayout(this);
            exitShareCard.setOrientation(LinearLayout.VERTICAL);
            exitShareCard.setGravity(Gravity.CENTER_HORIZONTAL);
            exitShareCard.setPadding(dp(16), dp(14), dp(16), dp(14));
            exitShareCard.setBackground(rounded(surface, border, 1, 11));
            exitShareCard.setVisibility(View.GONE);
            LinearLayout.LayoutParams shareParams = matchWrap();
            shareParams.topMargin = dp(16);
            shareParams.bottomMargin = dp(8);
            page.addView(exitShareCard, shareParams);
            refreshExitShareCard();
        }
        return wrapScroll(page);
    }

    // Shows the QR (link and image both come from the core) while the exit
    // runs; rebuilt only when the link changes, e.g. a new Wi-Fi address.
    private void refreshExitShareCard() {
        if (exitShareCard == null) return;
        String link = null;
        String error = null;
        if (OpenFluxExitService.isRunning()) {
            String ip = getLocalIpAddress();
            try {
                link = Mobile.exitShareLink(ip != null ? ip : "", "ReFlux " + Build.MODEL);
            } catch (Exception e) {
                error = e.getMessage();
            }
        }
        String shown = link != null ? link : error;
        if (java.util.Objects.equals(shown, exitShareShown)) return;
        exitShareShown = shown;
        exitShareCard.removeAllViews();
        if (shown == null) {
            exitShareCard.setVisibility(View.GONE);
            return;
        }
        exitShareCard.addView(text(getString(R.string.exit_qr_title), 11, secondary, true));
        if (link == null) {
            TextView problem = text(getString(R.string.qr_unavailable, error), 13, text, false);
            LinearLayout.LayoutParams problemParams = matchWrap();
            problemParams.topMargin = dp(6);
            exitShareCard.addView(problem, problemParams);
        } else {
            try {
                byte[] png = Mobile.shareQRPNG(link, dp(220));
                ImageView qrView = new ImageView(this);
                qrView.setImageBitmap(android.graphics.BitmapFactory.decodeByteArray(png, 0, png.length));
                qrView.setBackground(rounded(Color.WHITE, Color.WHITE, 0, 12));
                qrView.setPadding(dp(6), dp(6), dp(6), dp(6));
                LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(dp(232), dp(232));
                qrParams.topMargin = dp(10);
                exitShareCard.addView(qrView, qrParams);
            } catch (Exception e) {
                exitShareCard.addView(text(getString(R.string.qr_unavailable, e.getMessage()), 13, text, false), matchWrap());
            }
            TextView hint = text(getString(R.string.exit_qr_hint), 12, secondary, false);
            hint.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams hintParams = matchWrap();
            hintParams.topMargin = dp(10);
            exitShareCard.addView(hint, hintParams);
            Button copyButton = new Button(this);
            copyButton.setText(getString(R.string.copy_link));
            copyButton.setAllCaps(false);
            copyButton.setTextColor(accent);
            copyButton.setTextSize(13);
            copyButton.setStateListAnimator(null);
            copyButton.setBackground(ripple(Color.TRANSPARENT, 9));
            String copied = link;
            copyButton.setOnClickListener(v -> {
                bounce(v);
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("OpenFlux", copied));
                Toast.makeText(this, getString(R.string.link_copied), Toast.LENGTH_SHORT).show();
            });
            exitShareCard.addView(copyButton, new LinearLayout.LayoutParams(-1, dp(40)));
        }
        if (exitShareCard.getVisibility() != View.VISIBLE) {
            exitShareCard.setVisibility(View.VISIBLE);
            exitShareCard.setScaleX(0.9f);
            exitShareCard.setScaleY(0.9f);
            exitShareCard.setAlpha(0f);
            exitShareCard.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(320)
                    .setInterpolator(new OvershootInterpolator()).start();
        }
    }

    private String[][] activeParamRows() {
        if (MODE_EXIT.equals(connectionMode)) {
            return new String[][]{
                    {getString(R.string.param_mode), getString(R.string.mode_exit)},
                    {getString(R.string.param_exit), getString(R.string.param_exit_phone)},
                    {getString(R.string.param_lan_ip), getLocalIpAddress() != null ? getLocalIpAddress() : getString(R.string.param_unknown)},
            };
        }
        if (MODE_PROXY.equals(connectionMode)) {
            return new String[][]{
                    {getString(R.string.param_mode), getString(R.string.mode_proxy)},
                    {getString(R.string.param_local_port), String.valueOf(proxyPort)},
                    {getString(R.string.param_access), proxyAccessSummary()},
            };
        }
        Profile p = selectedProfile();
        if (p != null && p.stream) {
            return new String[][]{
                    {getString(R.string.param_mode), getString(R.string.mode_stream)},
                    {getString(R.string.param_via_node), getString(R.string.param_tcp_only)},
                    {getString(R.string.apps), appFilterSummary()},
            };
        }
        return new String[][]{
                {getString(R.string.param_mode), getString(R.string.mode_tunnel)},
                {getString(R.string.dns_server), dnsServer.isEmpty() ? getString(R.string.dns_default_value, DnsServer.DEFAULT) : dnsServer},
                {getString(R.string.mtu), String.valueOf(mtu)},
                {getString(R.string.apps), appFilterSummary()},
        };
    }

    private String proxyAccessSummary() {
        if (!proxyLanAccess) return getString(R.string.proxy_this_device);
        String localIp = getLocalIpAddress();
        String address = localIp != null ? localIp + ":" + proxyPort : getString(R.string.proxy_ip_unknown);
        return getString(proxyAuthEnabled ? R.string.proxy_with_password : R.string.proxy_without_password, address);
    }

    private String appFilterSummary() {
        if (AppFilter.MODE_WHITELIST.equals(appFilterMode)) return getString(R.string.apps_whitelist_count, selectedApps.size());
        if (AppFilter.MODE_BLACKLIST.equals(appFilterMode)) return getString(R.string.apps_blacklist_count, selectedApps.size());
        return getString(R.string.all);
    }

    private View paramsCard(String[][] rows) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(surface, border, 1, 11));
        for (int i = 0; i < rows.length; i++) {
            card.addView(paramRow(rows[i][0], rows[i][1]));
            if (i < rows.length - 1) addDivider(card, 0);
        }
        return card;
    }

    private View paramRow(String labelValue, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(11), dp(14), dp(11));
        row.addView(text(labelValue, 13, secondary, false), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView valueView = text(value, 13, text, true);
        valueView.setGravity(Gravity.END);
        row.addView(valueView, new LinearLayout.LayoutParams(-2, -2));
        return row;
    }

    private void staggerIn(View view, int delayMs) {
        view.setAlpha(0f);
        view.setTranslationY(dp(14));
        view.animate().alpha(1f).translationY(0f).setStartDelay(delayMs).setDuration(320)
                .setInterpolator(EMPHASIZED).withLayer().start();
    }

    private View buildLogsPage() {
        LinearLayout page = page();
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text(getString(R.string.logs_title), 25, text, true);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageButton clear = iconButton(R.drawable.ic_delete, getString(R.string.logs_clear));
        clear.setOnClickListener(v -> {
            tap(v);
            LogBuffer.clear();
            reloadLogs();
        });
        header.addView(clear, new LinearLayout.LayoutParams(dp(48), dp(48)));
        page.addView(header);

        TextView note = text(getString(R.string.logs_note_kept), 11, secondary, false);
        LinearLayout.LayoutParams noteParams = matchWrap();
        noteParams.topMargin = dp(4);
        page.addView(note, noteParams);

        LinearLayout.LayoutParams filterParams = matchWrap();
        filterParams.topMargin = dp(16);
        page.addView(buildLogFilters(), filterParams);

        // The search, then copy and share: what the list shows.
        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setBackground(rounded(surface, border, 1, 10));
        LinearLayout.LayoutParams searchIconParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        searchIconParams.leftMargin = dp(14);
        searchRow.addView(icon(R.drawable.ic_search, hint), searchIconParams);
        EditText search = settingInput(getString(R.string.logs_search), logQuery,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setPadding(dp(10), 0, dp(10), 0);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        bindText(search, value -> {
            String query = value.toLowerCase(Locale.ROOT);
            if (query.equals(logQuery)) return;
            logQuery = query;
            reloadLogs();
        });
        searchRow.addView(search, new LinearLayout.LayoutParams(0, -1, 1f));
        tools.addView(searchRow, new LinearLayout.LayoutParams(0, dp(46), 1f));
        ImageButton copy = iconButton(R.drawable.ic_copy, getString(R.string.logs_copy));
        copy.setOnClickListener(v -> {
            tap(v);
            copyLogs();
        });
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        copyParams.leftMargin = dp(6);
        tools.addView(copy, copyParams);
        ImageButton share = iconButton(R.drawable.ic_share, getString(R.string.logs_share));
        share.setOnClickListener(v -> {
            tap(v);
            shareLogs();
        });
        tools.addView(share, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout.LayoutParams toolsParams = matchWrap();
        toolsParams.topMargin = dp(10);
        page.addView(tools, toolsParams);

        FrameLayout box = new FrameLayout(this);
        box.setBackground(rounded(surface, border, 1, 10));
        logList = new RecyclerView(this);
        logList.setLayoutManager(new LinearLayoutManager(this));
        // New lines come every second: no animation for them.
        logList.setItemAnimator(null);
        logList.setClipToPadding(false);
        logList.setPadding(0, dp(8), 0, dp(8));
        logAdapter = new LogAdapter();
        logList.setAdapter(logAdapter);
        logList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView view, int dx, int dy) {
                updateLogJump();
            }
        });
        box.addView(logList, new FrameLayout.LayoutParams(-1, -1));
        logEmpty = text("", 13, secondary, false);
        logEmpty.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.addView(logEmpty, new FrameLayout.LayoutParams(-1, -2));
        // Back to the newest line after scrolling up through older ones.
        ImageButton jump = new ImageButton(this);
        jump.setImageResource(R.drawable.ic_arrow_down);
        jump.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        jump.setContentDescription(getString(R.string.logs_jump));
        jump.setPadding(dp(9), dp(9), dp(9), dp(9));
        GradientDrawable jumpShape = new GradientDrawable();
        jumpShape.setShape(GradientDrawable.OVAL);
        jumpShape.setColor(accent);
        jump.setBackground(jumpShape);
        jump.setElevation(dp(3));
        jump.setVisibility(View.GONE);
        jump.setOnClickListener(v -> {
            tap(v);
            if (logAdapter.getItemCount() > 0) logList.scrollToPosition(logAdapter.getItemCount() - 1);
            v.setVisibility(View.GONE);
        });
        FrameLayout.LayoutParams jumpParams = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.BOTTOM | Gravity.END);
        jumpParams.setMargins(0, 0, dp(12), dp(12));
        box.addView(jump, jumpParams);
        logJump = jump;

        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        logParams.topMargin = dp(12);
        // Unlike pages that are fine fading behind the pill, log text must
        // stay fully readable - the card's own bottom edge needs to end
        // above the pill, not extend behind it with scroll-padding tricks.
        logParams.bottomMargin = navClearance();
        page.addView(box, logParams);
        reloadLogs();
        return page;
    }

    private View buildLogFilters() {
        String[] labels = {getString(R.string.all), getString(R.string.logs_important), getString(R.string.logs_errors)};
        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(dp(4), dp(4), dp(4), dp(4));
        bar.setBackground(rounded(surface, border, 1, 14));
        TextView[] segments = new TextView[labels.length];
        Runnable paint = () -> {
            for (int i = 0; i < segments.length; i++) {
                boolean selected = logFilter == i;
                segments[i].setBackground(selected
                        ? rounded(tonal(0.18f), tonal(0.45f), 1, 11) : null);
                segments[i].setTextColor(selected ? accent : secondary);
                segments[i].setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                segments[i].setSelected(selected);
            }
        };
        for (int i = 0; i < labels.length; i++) {
            int filter = i;
            TextView segment = text(labels[i], 13, secondary, false);
            segment.setGravity(Gravity.CENTER);
            segment.setOnClickListener(v -> {
                if (logFilter == filter) return;
                tap(v);
                logFilter = filter;
                paint.run();
                reloadLogs();
            });
            segments[i] = segment;
            bar.addView(segment, new LinearLayout.LayoutParams(0, dp(42), 1f));
        }
        paint.run();
        return bar;
    }

    private View buildSettingsPage() {
        LinearLayout page = page();
        page.addView(buildSettingsHeader());

        if (!settingsDetailOpen) {
            LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(-1, 0, 1f);
            listParams.topMargin = dp(16);
            listParams.bottomMargin = dp(10);
            page.addView(buildSettingsList(), listParams);
            return page;
        }

        // The interface and advanced sections act at once.
        boolean showSave = settingsSubTab == SETTINGS_CONNECTION || settingsSubTab == SETTINGS_TRAFFIC;
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        contentParams.topMargin = dp(16);
        if (!showSave) contentParams.bottomMargin = dp(10);
        View subTab = buildSettingsSubTabContent();
        // The pinned save button already clears the nav pill; the scroll
        // needs no room of its own for it.
        if (showSave && subTab instanceof ScrollView) scrollBottom(subTab, dp(8));
        page.addView(subTab, contentParams);

        if (showSave) {
            Button save = primaryButton(getString(R.string.settings_save), () -> {
                if (settingsSubTab == SETTINGS_CONNECTION) {
                    if (!applyNetworkSettings()) return;
                    applyModeSettings();
                } else {
                    if (AppFilter.MODE_WHITELIST.equals(editorAppFilterMode) && editorSelectedApps.isEmpty()) {
                        Toast.makeText(this, getString(R.string.apps_whitelist_empty), Toast.LENGTH_LONG).show();
                        return;
                    }
                    applyAppsSettings();
                    applyRoutingSettings();
                }
                Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show();
            });
            LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(52));
            saveParams.topMargin = dp(14);
            // This button sits outside the scrollable section (as a fixed
            // footer), so it needs its own clearance from the nav pill -
            // the scroll section's internal padding doesn't cover it.
            saveParams.bottomMargin = navClearance();
            page.addView(save, saveParams);
            pinnedSave = save;
            if (keyboardOpen) save.setVisibility(View.GONE);
        }
        return page;
    }

    private View buildSettingsHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        if (settingsDetailOpen) {
            ImageButton back = iconButton(R.drawable.ic_arrow_back, getString(R.string.settings_back));
            back.setOnClickListener(v -> {
                bounce(v);
                closeSettingsDetail();
            });
            LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(44), dp(44));
            backParams.rightMargin = dp(6);
            header.addView(back, backParams);
            header.addView(text(settingsSectionTitle(settingsSubTab), 22, text, true),
                    new LinearLayout.LayoutParams(0, -2, 1f));
        } else {
            LinearLayout titles = new LinearLayout(this);
            titles.setOrientation(LinearLayout.VERTICAL);
            titles.addView(text(getString(R.string.nav_settings), 25, text, true));
            TextView hint = text(getString(R.string.settings_hint), 12, secondary, false);
            LinearLayout.LayoutParams hintParams = matchWrap();
            hintParams.topMargin = dp(4);
            titles.addView(hint, hintParams);
            header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        return header;
    }

    private String settingsSectionTitle(int tab) {
        switch (tab) {
            case SETTINGS_TRAFFIC: return getString(R.string.settings_traffic);
            case SETTINGS_INTERFACE: return getString(R.string.settings_interface);
            case SETTINGS_ADVANCED: return getString(R.string.settings_advanced);
            case SETTINGS_CONNECTION:
            default: return getString(R.string.settings_connection);
        }
    }

    private View buildSettingsList() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        // Inside the list, not on the scroll: see scrollBottom.
        list.setPadding(0, 0, 0, navClearance());
        list.addView(settingsListRow(R.drawable.ic_swap, getString(R.string.settings_connection),
                getString(R.string.settings_connection_detail, modeLabel(connectionMode)), SETTINGS_CONNECTION));
        list.addView(settingsListRow(R.drawable.ic_routing, getString(R.string.settings_traffic),
                getString(R.string.settings_traffic_detail, appFilterSummary()), SETTINGS_TRAFFIC));
        list.addView(settingsListRow(R.drawable.ic_dark_mode, getString(R.string.settings_interface),
                getString(R.string.settings_interface_detail, themeSummary(), Lang.label(this, Lang.get(this))),
                SETTINGS_INTERFACE));
        list.addView(settingsListRow(R.drawable.ic_code, getString(R.string.settings_advanced),
                getString(ScriptStore.experimental(this)
                        ? R.string.settings_advanced_detail_on : R.string.settings_advanced_detail_off),
                SETTINGS_ADVANCED));
        groupTiles(list);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        return scroll;
    }

    private void addDivider(LinearLayout parent) {
        addDivider(parent, dp(56));
    }

    private void addDivider(LinearLayout parent, int leftMargin) {
        View line = new View(this);
        line.setBackgroundColor(border);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.leftMargin = leftMargin;
        parent.addView(line, params);
    }

    private View settingsListRow(int iconRes, String titleValue, String detailValue, int tab) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setClickable(true);
        row.setFocusable(true);
        row.addView(iconBubble(iconRes), new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1f);
        copyParams.leftMargin = dp(16);
        copy.addView(text(titleValue, 15, text, true));
        copy.addView(text(detailValue, 12, secondary, false));
        row.addView(copy, copyParams);
        row.addView(icon(R.drawable.ic_chevron_right, hint), new LinearLayout.LayoutParams(dp(20), dp(20)));
        row.setOnClickListener(v -> {
            bounce(v);
            openSettingsDetail(tab);
        });
        return row;
    }

    private View buildSettingsSubTabContent() {
        switch (settingsSubTab) {
            case SETTINGS_TRAFFIC:
                return buildTrafficSettings();
            case SETTINGS_INTERFACE:
                return wrapScroll(buildInterfaceSettings());
            case SETTINGS_ADVANCED:
                return wrapScroll(buildAdvancedSettings());
            case SETTINGS_CONNECTION:
            default:
                return wrapScroll(buildConnectionSettings());
        }
    }

    // Traffic: which apps use the tunnel and which sites bypass it, as two
    // tabs over one draft that the save button keeps.
    private View buildTrafficSettings() {
        LinearLayout section = page();
        FrameLayout body = new FrameLayout(this);
        Runnable showTab = () -> {
            body.removeAllViews();
            View tab;
            if (trafficTab == TRAFFIC_ROUTING) {
                tab = wrapScroll(buildRoutingSettings());
                // The pinned save button already clears the nav pill.
                scrollBottom(tab, dp(8));
            } else {
                tab = buildAppsSettings();
            }
            body.addView(tab, new FrameLayout.LayoutParams(-1, -1));
        };
        section.addView(segmented(new String[]{getString(R.string.apps), getString(R.string.settings_routing)},
                trafficTab, i -> {
                    trafficTab = i;
                    showTab.run();
                }), matchWrap());
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        bodyParams.topMargin = dp(14);
        section.addView(body, bodyParams);
        showTab.run();
        return section;
    }

    // A section's heading: small capitals over its group.
    private void sectionLabel(LinearLayout parent, String title, boolean first) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = first ? 0 : dp(26);
        params.bottomMargin = dp(8);
        parent.addView(label(title), params);
    }

    // Connection: the operating mode with the proxy's options, the network
    // and what keeps the connection alive, under one save button.
    private View buildConnectionSettings() {
        LinearLayout section = page();
        sectionLabel(section, getString(R.string.settings_mode_label), true);
        TextView hint = text(
                getString(R.string.mode_hint),
                12, secondary, false);
        section.addView(hint, matchWrap());

        String[] modes = {MODE_TUNNEL, MODE_PROXY, MODE_EXIT};
        Runnable[] onModeChanged = new Runnable[1];
        View modeChoice = choiceList(new String[][]{
                {getString(R.string.mode_tunnel_short), getString(R.string.mode_tunnel_detail)},
                {getString(R.string.mode_proxy), getString(R.string.mode_proxy_detail)},
                {getString(R.string.mode_exit), getString(R.string.mode_exit_detail)},
        }, new int[]{R.drawable.ic_lock, R.drawable.ic_swap, R.drawable.ic_power}, java.util.Arrays.asList(modes).indexOf(editorConnectionMode), i -> {
            editorConnectionMode = modes[i];
            onModeChanged[0].run();
        });
        LinearLayout.LayoutParams modeGroupParams = matchWrap();
        modeGroupParams.topMargin = dp(14);
        section.addView(modeChoice, modeGroupParams);

        TextView exitHint = text(
                getString(R.string.mode_exit_hint),
                12, secondary, false);
        setInitialVisibility(exitHint, MODE_EXIT.equals(editorConnectionMode));
        LinearLayout.LayoutParams exitHintParams = matchWrap();
        exitHintParams.topMargin = dp(10);
        exitHintParams.leftMargin = dp(4);
        exitHintParams.rightMargin = dp(4);
        section.addView(exitHint, exitHintParams);

        boolean proxySelected = MODE_PROXY.equals(editorConnectionMode);

        proxyPortInput = settingInput(getString(R.string.port), String.valueOf(editorProxyPort), InputType.TYPE_CLASS_NUMBER);
        View portRow = settingRow(R.drawable.ic_swap, getString(R.string.proxy_port), proxyPortInput);
        setInitialVisibility(portRow, proxySelected);
        LinearLayout.LayoutParams portParams = matchWrap();
        portParams.topMargin = dp(14);
        section.addView(portRow, portParams);

        Switch lanSwitch = settingSwitch(R.drawable.ic_public, getString(R.string.proxy_lan),
                getString(R.string.proxy_lan_detail), editorProxyLanAccess);
        View lanRow = (View) lanSwitch.getTag();
        setInitialVisibility(lanRow, proxySelected);
        LinearLayout.LayoutParams lanParams = matchWrap();
        lanParams.topMargin = dp(8);
        section.addView(lanRow, lanParams);

        String localIp = getLocalIpAddress();
        TextView lanAddressHint = text(
                localIp != null
                        ? getString(R.string.proxy_lan_address, localIp + ":" + editorProxyPort)
                        : getString(R.string.proxy_no_ip),
                13, accent, true);
        lanAddressHint.setPadding(dp(14), dp(12), dp(14), dp(12));
        lanAddressHint.setBackground(rounded(colors.infoSurface,
                Color.TRANSPARENT, 0, 10));
        setInitialVisibility(lanAddressHint, proxySelected && editorProxyLanAccess);
        LinearLayout.LayoutParams lanAddressParams = matchWrap();
        lanAddressParams.topMargin = dp(8);
        section.addView(lanAddressHint, lanAddressParams);

        Switch authSwitch = settingSwitch(R.drawable.ic_lock, getString(R.string.proxy_auth),
                getString(R.string.proxy_auth_detail), editorProxyAuthEnabled);
        View authRow = (View) authSwitch.getTag();
        setInitialVisibility(authRow, proxySelected && editorProxyLanAccess);
        LinearLayout.LayoutParams authParams = matchWrap();
        authParams.topMargin = dp(8);
        section.addView(authRow, authParams);
        View lanWarningHint = fieldHint(
                getString(R.string.proxy_open_warning));
        setInitialVisibility(lanWarningHint, proxySelected && editorProxyLanAccess);
        section.addView(lanWarningHint);

        LinearLayout credentialsBlock = new LinearLayout(this);
        credentialsBlock.setOrientation(LinearLayout.VERTICAL);
        setInitialVisibility(credentialsBlock, proxySelected && editorProxyLanAccess && editorProxyAuthEnabled);
        LinearLayout.LayoutParams credentialsParams = matchWrap();
        credentialsParams.topMargin = dp(10);
        section.addView(credentialsBlock, credentialsParams);

        proxyUsernameInput = settingInput(getString(R.string.username), proxyUsername, InputType.TYPE_CLASS_TEXT);
        credentialsBlock.addView(iconTextField(R.drawable.ic_person, proxyUsernameInput, null),
                new LinearLayout.LayoutParams(-1, dp(FIELD_HEIGHT)));
        LinearLayout.LayoutParams passwordParams = new LinearLayout.LayoutParams(-1, dp(FIELD_HEIGHT));
        passwordParams.topMargin = dp(4);
        credentialsBlock.addView(buildProxyPasswordField(), passwordParams);

        Button generateCreds = new Button(this);
        generateCreds.setText(getString(R.string.proxy_generate_credentials));
        generateCreds.setAllCaps(false);
        generateCreds.setTextColor(accent);
        generateCreds.setTextSize(13);
        generateCreds.setStateListAnimator(null);
        generateCreds.setBackground(ripple(Color.TRANSPARENT, 9));
        generateCreds.setOnClickListener(v -> {
            bounce(v);
            generateProxyCredentials();
        });
        LinearLayout.LayoutParams generateParams = new LinearLayout.LayoutParams(-1, dp(44));
        generateParams.topMargin = dp(2);
        credentialsBlock.addView(generateCreds, generateParams);

        View shareCard = buildProxyShareCard();
        setInitialVisibility(shareCard, proxySelected);
        LinearLayout.LayoutParams shareParams = matchWrap();
        shareParams.topMargin = dp(14);
        section.addView(shareCard, shareParams);

        sectionLabel(section, getString(R.string.settings_network_label), false);
        section.addView(buildNetworkSettings(), matchWrap());

        sectionLabel(section, getString(R.string.reliability), false);

        LinearLayout batteryRow = cardRow(R.drawable.ic_power, getString(R.string.battery_optimization),
                getString(R.string.battery_optimization_detail));
        batteryRow.setClickable(true);
        batteryRow.setFocusable(true);
        batteryRow.setOnClickListener(v -> {
            bounce(v);
            requestIgnoreBatteryOptimizations();
        });
        section.addView(batteryRow, matchWrap());

        LinearLayout alwaysOnRow = cardRow(R.drawable.ic_lock, getString(R.string.always_on),
                getString(R.string.always_on_detail));
        alwaysOnRow.setClickable(true);
        alwaysOnRow.setFocusable(true);
        alwaysOnRow.setOnClickListener(v -> {
            bounce(v);
            startActivity(new Intent(Settings.ACTION_VPN_SETTINGS));
        });
        setInitialVisibility(alwaysOnRow, MODE_TUNNEL.equals(editorConnectionMode));
        LinearLayout.LayoutParams alwaysOnParams = matchWrap();
        alwaysOnParams.topMargin = dp(8);
        section.addView(alwaysOnRow, alwaysOnParams);

        onModeChanged[0] = () -> {
            boolean nowProxy = MODE_PROXY.equals(editorConnectionMode);
            setViewVisibleAnimated(exitHint, MODE_EXIT.equals(editorConnectionMode));
            setViewVisibleAnimated(portRow, nowProxy);
            setViewVisibleAnimated(lanRow, nowProxy);
            setViewVisibleAnimated(lanAddressHint, nowProxy && editorProxyLanAccess);
            setViewVisibleAnimated(authRow, nowProxy && editorProxyLanAccess);
            setViewVisibleAnimated(lanWarningHint, nowProxy && editorProxyLanAccess);
            setViewVisibleAnimated(credentialsBlock, nowProxy && editorProxyLanAccess && editorProxyAuthEnabled);
            setViewVisibleAnimated(shareCard, nowProxy);
            setViewVisibleAnimated(alwaysOnRow, MODE_TUNNEL.equals(editorConnectionMode));
        };

        lanSwitch.setOnCheckedChangeListener((button, checked) -> {
            tap(button);
            editorProxyLanAccess = checked;
            if (proxyShareQr != null) setViewVisibleAnimated(proxyShareQr, checked);
            setViewVisibleAnimated(lanAddressHint, checked);
            setViewVisibleAnimated(authRow, checked);
            setViewVisibleAnimated(lanWarningHint, checked);
            setViewVisibleAnimated(credentialsBlock, checked && editorProxyAuthEnabled);
        });

        authSwitch.setOnCheckedChangeListener((button, checked) -> {
            tap(button);
            editorProxyAuthEnabled = checked;
            setViewVisibleAnimated(credentialsBlock, checked);
        });

        return section;
    }

    // Commits the Mode/Proxy draft fields to the live settings. Called by the
    // generic "Сохранить настройки" button in buildSettingsPage() - editing
    // these fields and pressing back without it discards the draft.
    private void applyModeSettings() {
        // If the mode is actually changing while the OLD mode's service is
        // still connected, isConnectionRunning() below would start checking
        // the NEW mode's (not yet started) service and show "not connected" -
        // while the old service keeps running unseen in the background,
        // holding the VPN slot or the proxy port. Stop it explicitly instead
        // of orphaning it.
        boolean modeChanging = !editorConnectionMode.equals(connectionMode);
        String oldMode = connectionMode;
        boolean oldWasRunning = isConnectionRunning();

        connectionMode = editorConnectionMode;
        proxyLanAccess = editorProxyLanAccess;
        proxyAuthEnabled = editorProxyAuthEnabled;
        int newPort;
        try {
            newPort = Integer.parseInt(proxyPortInput.getText().toString().trim());
        } catch (NumberFormatException ignored) {
            newPort = DEFAULT_PROXY_PORT;
        }
        proxyPort = Math.max(1024, Math.min(65535, newPort));
        proxyUsername = proxyUsernameInput.getText().toString().trim();
        proxyPassword = proxyPasswordInput.getText().toString().trim();
        persistSettings();

        if (modeChanging && oldWasRunning) {
            stopConnection(oldMode);
            appendLog(getString(R.string.log_mode_changed, modeLabel(oldMode)));
        }
    }

    // buildProxyShareCard renders the socks:// link (and a QR encoding it)
    // that another device can use to add this proxy in an app like Happ or
    // Telegram, using whatever host/port/credentials are currently saved.
    // It reflects state as of when this settings screen was built, not live
    // as the user edits fields above - consistent with how other settings
    // here only take effect after being saved/reopened.
    private View buildProxyShareCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(rounded(surface, border, 1, 11));
        card.addView(text(getString(R.string.proxy_share_title), 12, secondary, false));

        String link = proxyShareLink();
        TextView linkView = text(link, 14, text, true);
        linkView.setTextIsSelectable(true);
        LinearLayout.LayoutParams linkParams = matchWrap();
        linkParams.topMargin = dp(6);
        card.addView(linkView, linkParams);

        Button copyButton = new Button(this);
        copyButton.setText(getString(R.string.copy_link));
        copyButton.setAllCaps(false);
        copyButton.setTextColor(accent);
        copyButton.setTextSize(13);
        copyButton.setStateListAnimator(null);
        copyButton.setBackground(ripple(Color.TRANSPARENT, 9));
        copyButton.setOnClickListener(v -> {
            bounce(v);
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("OpenFlux SOCKS5", link));
            }
            Toast.makeText(this, getString(R.string.link_copied), Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(-1, dp(40));
        copyParams.topMargin = dp(2);
        card.addView(copyButton, copyParams);

        Bitmap qr = generateQrBitmap(link, dp(180));
        proxyShareQr = null;
        if (qr != null) {
            // Only another device can scan it, which needs LAN access.
            ImageView qrView = new ImageView(this);
            qrView.setImageBitmap(qr);
            setInitialVisibility(qrView, editorProxyLanAccess);
            proxyShareQr = qrView;
            LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(dp(180), dp(180));
            qrParams.topMargin = dp(10);
            qrParams.gravity = Gravity.CENTER_HORIZONTAL;
            card.addView(qrView, qrParams);
        }

        return card;
    }

    private String proxyShareLink() {
        String host = proxyLanAccess ? getLocalIpAddress() : null;
        if (host == null) host = "127.0.0.1";
        String auth = "";
        if (proxyLanAccess && proxyAuthEnabled && !proxyUsername.isEmpty()) {
            auth = proxyUsername + ":" + proxyPassword + "@";
        }
        return "socks://" + auth + host + ":" + proxyPort;
    }

    private Bitmap generateQrBitmap(String content, int sizePx) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx);
            int foreground = darkMode ? Color.WHITE : Color.BLACK;
            int background = darkMode ? Color.BLACK : Color.WHITE;
            Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565);
            for (int x = 0; x < sizePx; x++) {
                for (int y = 0; y < sizePx; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? foreground : background);
                }
            }
            return bitmap;
        } catch (WriterException exception) {
            return null;
        }
    }

    private void requestIgnoreBatteryOptimizations() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null && powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            Toast.makeText(this, getString(R.string.battery_already), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception exception) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    // setInitialVisibility sets a view's starting visibility/alpha without
    // animating, for use when building a page (as opposed to
    // setViewVisibleAnimated, which is for reacting to a toggle afterwards).
    private void setInitialVisibility(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
        view.setAlpha(visible ? 1f : 0f);
    }

    private View buildProxyPasswordField() {
        proxyPasswordInput = settingInput(getString(R.string.password), proxyPassword,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        proxyPasswordInput.setTransformationMethod(
                proxyPasswordVisible ? null : PasswordTransformationMethod.getInstance());
        proxyPasswordVisibilityButton = iconButton(
                proxyPasswordVisible ? R.drawable.ic_visibility_off : R.drawable.ic_visibility,
                proxyPasswordVisible ? getString(R.string.password_hide) : getString(R.string.password_show));
        proxyPasswordVisibilityButton.setOnClickListener(v -> {
            tap(v);
            toggleProxyPasswordVisibility();
        });
        return iconTextField(R.drawable.ic_key, proxyPasswordInput, proxyPasswordVisibilityButton);
    }

    // iconTextField lays out a leading icon and an EditText that fills the
    // rest of the row (with a reasonable gap between them, rather than
    // settingRow's separate caption + far-right fixed-width value box, which
    // doesn't read well when the "value" is itself the thing being typed),
    // plus an optional trailing action button (e.g. a show/hide toggle).
    private View iconTextField(int iconRes, EditText input, ImageButton trailingButton) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, trailingButton != null ? dp(2) : dp(14), 0);
        row.setBackground(rounded(surface, border, 1, 10));
        row.addView(icon(iconRes, secondary), new LinearLayout.LayoutParams(dp(20), dp(20)));
        input.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, -1, 1f);
        inputParams.leftMargin = dp(12);
        row.addView(input, inputParams);
        if (trailingButton != null) {
            row.addView(trailingButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        }
        return floating(row, input);
    }

    private void toggleProxyPasswordVisibility() {
        int position = proxyPasswordInput.getSelectionStart();
        proxyPasswordVisible = !proxyPasswordVisible;
        proxyPasswordInput.setTransformationMethod(
                proxyPasswordVisible ? null : PasswordTransformationMethod.getInstance());
        proxyPasswordInput.setTypeface(Typeface.DEFAULT);
        proxyPasswordVisibilityButton.setImageResource(
                proxyPasswordVisible ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);
        proxyPasswordVisibilityButton.setContentDescription(
                proxyPasswordVisible ? getString(R.string.password_hide) : getString(R.string.password_show));
        proxyPasswordInput.setSelection(Math.max(0, Math.min(position, proxyPasswordInput.length())));
    }

    // Only fills the (still-unsaved) input fields - applyModeSettings() is
    // what actually commits them, same as editing the fields by hand would.
    private void generateProxyCredentials() {
        byte[] randomPass = new byte[16];
        new SecureRandom().nextBytes(randomPass);
        String generatedUser = "user" + (100 + new SecureRandom().nextInt(900));
        String generatedPass = Base64.encodeToString(randomPass, Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE);
        if (proxyUsernameInput != null) proxyUsernameInput.setText(generatedUser);
        if (proxyPasswordInput != null) {
            proxyPasswordInput.setText(generatedPass);
            proxyPasswordInput.setSelection(proxyPasswordInput.length());
        }
        Toast.makeText(this, getString(R.string.proxy_credentials_created_save), Toast.LENGTH_SHORT).show();
    }

    private View buildAboutSettings() {
        LinearLayout section = page();
        TextView intro = text(
                getString(R.string.about_intro),
                13, secondary, false);
        section.addView(intro, matchWrap());

        LinearLayout links = new LinearLayout(this);
        links.setOrientation(LinearLayout.VERTICAL);
        String version = appVersion.isEmpty() ? "-" : appVersion;
        links.addView(aboutLinkRow(R.drawable.ic_info, getString(R.string.about_version),
                versionCheckState == VERSION_CHECK_OUTDATED && latestVersion != null
                        ? getString(R.string.about_version_outdated, version, latestVersion)
                        : versionCheckState == VERSION_CHECK_LATEST
                        ? getString(R.string.about_version_latest, version) : version,
                APP_REPO_URL + "/releases/latest"), matchWrap());
        links.addView(aboutLinkRow(R.drawable.ic_github, getString(R.string.about_app),
                "BeleBob/ReFlux-Android", APP_REPO_URL), matchWrap());
        links.addView(aboutLinkRow(R.drawable.ic_github, getString(R.string.about_server),
                "BeleBob/ReFlux", SERVER_REPO_URL), matchWrap());
        links.addView(aboutLinkRow(R.drawable.ic_github, getString(R.string.about_core),
                "p1neappleXpress/OpenFlux", MAIN_REPO_URL), matchWrap());
        links.addView(aboutLinkRow(R.drawable.ic_github, getString(R.string.about_based_on),
                "damnurmum/OpenFlux-Android", BASE_REPO_URL), matchWrap());
        links.addView(aboutLinkRow(R.drawable.ic_telegram, getString(R.string.about_chat),
                "@openflux_chat", "https://t.me/openflux_chat"), matchWrap());
        links.addView(aboutLinkRow(R.drawable.ic_discord, "Discord OpenFlux",
                "discord.gg/openfluxx", "https://discord.gg/8a4S3QAh62"), matchWrap());
        groupTiles(links);
        LinearLayout.LayoutParams linksParams = matchWrap();
        linksParams.topMargin = dp(20);
        section.addView(links, linksParams);
        return section;
    }

    private View aboutLinkRow(int iconRes, String titleValue, String detailValue, String url) {
        LinearLayout row = cardRow(iconRes, titleValue, detailValue);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            bounce(v);
            openUrl(url);
        });
        return row;
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.link_open_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private View wrapScroll(View sectionContent) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(sectionContent, new ScrollView.LayoutParams(-1, -2));
        scrollBottom(scroll, navClearance());
        return scroll;
    }

    // The room under a scroll's content goes inside the content, not on the
    // ScrollView: a ScrollView counts its own padding as visible area when it
    // decides whether it can scroll, so with less overflow than the padding a
    // drag that starts on a button or a field would not move it.
    private static void scrollBottom(View scroll, int bottom) {
        View content = ((ScrollView) scroll).getChildAt(0);
        content.setPadding(content.getPaddingLeft(), content.getPaddingTop(), content.getPaddingRight(), bottom);
    }

    // Content now scrolls behind the floating nav pill (for the fade effect
    // behind it), so every scrollable page needs this much extra bottom room
    // or its last item - often a Save button - ends up stuck under the pill.
    private int navClearance() {
        return navHeight() + dp(10) + navBottomInset + dp(16);
    }

    private View buildProfilesPage() {
        LinearLayout page = page();
        if (profileEditorOpen) {
            page.addView(buildProfileEditorHeader());
            LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(-1, 0, 1f);
            editorParams.topMargin = dp(16);
            View editor = wrapScroll(buildProfileEditor());
            // The save button is pinned below instead, above the nav pill.
            scrollBottom(editor, dp(8));
            page.addView(editor, editorParams);
            Button save = primaryButton(getString(R.string.profile_save), () -> saveProfileFromEditor(
                    profileNameInput.getText().toString().trim(),
                    urlInput.getText().toString().trim(),
                    encryptionInput.getText().toString().trim(),
                    maxTokenInput.getText().toString().trim(),
                    maxUidInput.getText().toString().trim()));
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
        titles.addView(text(getString(R.string.nav_profiles), 25, text, true));
        titles.addView(text(getString(R.string.profiles_subtitle), 12, secondary, false), matchWrap());
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageButton scanButton = iconButton(R.drawable.ic_qr_scan, getString(R.string.qr_add_menu));
        scanButton.setOnClickListener(v -> {
            tap(v);
            showQrActionMenu(v);
        });
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        scanParams.rightMargin = dp(8);
        header.addView(scanButton, scanParams);
        ImageButton addButton = iconButton(R.drawable.ic_add, getString(R.string.profile_add));
        addButton.setOnClickListener(v -> {
            tap(v);
            showProfileActionMenu(v);
        });
        header.addView(addButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        page.addView(header);

        if (profiles.isEmpty()) {
            TextView empty = text(getString(R.string.profiles_empty), 13, secondary, false);
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
        copy.addView(text(selected ? getString(R.string.profile_active, profileTransportSummary(p))
                : profileTransportSummary(p), 12, secondary, false));
        row.addView(copy, copyParams);
        ImageButton editButton = iconButton(R.drawable.ic_settings, getString(R.string.profile_edit_named, p.name));
        editButton.setOnClickListener(v -> {
            tap(v);
            openProfileEditor(p);
        });
        row.addView(editButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
        ImageButton deleteButton = iconButton(R.drawable.ic_delete, getString(R.string.profile_delete_named, p.name));
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

    private View buildProfileEditorHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = iconButton(R.drawable.ic_arrow_back, getString(R.string.profiles_back));
        back.setOnClickListener(v -> {
            bounce(v);
            closeProfileEditor();
        });
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        backParams.rightMargin = dp(6);
        header.addView(back, backParams);
        header.addView(text(editingProfileId != null ? getString(R.string.profile_edit) : getString(R.string.profile_new), 22, text, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        return header;
    }

    private View buildProfileEditor() {
        Profile existing = null;
        if (editingProfileId != null) {
            for (Profile p : profiles) if (p.id == editingProfileId) { existing = p; break; }
        }
        String initialUrl = existing != null ? existing.documentUrl : "";
        String initialSecret = existing != null ? existing.encryptionSecret : "";

        LinearLayout section = page();

        FrameLayout nameField = new FrameLayout(this);
        nameField.setBackground(rounded(surface, border, 1, 10));
        profileNameInput = settingInput(getString(R.string.profile_name), existing != null ? existing.name : "",
                InputType.TYPE_CLASS_TEXT);
        profileNameInput.setPadding(dp(16), 0, dp(16), 0);
        nameField.addView(profileNameInput, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, dp(FIELD_HEIGHT));
        section.addView(floating(nameField, profileNameInput), nameParams);

        TextView modeLabel = label(getString(R.string.editor_mode));
        LinearLayout.LayoutParams modeLabelParams = matchWrap();
        modeLabelParams.topMargin = dp(18);
        modeLabelParams.bottomMargin = dp(8);
        section.addView(modeLabel, modeLabelParams);
        section.addView(buildSessionModeSelector(), matchWrap());

        TextView transportTypeLabel = label(getString(R.string.editor_transport));
        LinearLayout.LayoutParams transportTypeLabelParams = matchWrap();
        transportTypeLabelParams.topMargin = dp(18);
        transportTypeLabelParams.bottomMargin = dp(8);
        section.addView(transportTypeLabel, transportTypeLabelParams);
        section.addView(buildTransportTypeSelector(), matchWrap());

        LinearLayout.LayoutParams maxFieldsParams = matchWrap();
        maxFieldsParams.topMargin = dp(8);
        maxFieldsContainer = buildMaxFields(existing);
        maxFieldsContainer.setVisibility("oneme".equals(editorTransportType) ? View.VISIBLE : View.GONE);
        section.addView(maxFieldsContainer, maxFieldsParams);

        // Session always uses the batched codec, so the choice only exists in
        // classic mode.
        LinearLayout codecBox = new LinearLayout(this);
        codecBox.setOrientation(LinearLayout.VERTICAL);
        TextView codecLabel = label(getString(R.string.editor_codec));
        LinearLayout.LayoutParams codecLabelParams = matchWrap();
        codecLabelParams.topMargin = dp(18);
        codecLabelParams.bottomMargin = dp(8);
        codecBox.addView(codecLabel, codecLabelParams);
        codecBox.addView(buildCodecSelector(), matchWrap());
        codecSection = codecBox;
        section.addView(codecBox, matchWrap());

        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(-1, dp(FIELD_HEIGHT));
        urlParams.topMargin = dp(10);
        urlField = buildUrlField(initialUrl);
        // MAX has its own token field above; the link field would duplicate it.
        urlField.setVisibility("oneme".equals(editorTransportType) ? View.GONE : View.VISIBLE);
        section.addView(urlField, urlParams);

        Button scriptSettings = new Button(this);
        scriptSettings.setText(getString(R.string.transport_settings));
        scriptSettings.setAllCaps(false);
        scriptSettings.setTextColor(accent);
        scriptSettings.setTextSize(13);
        scriptSettings.setStateListAnimator(null);
        scriptSettings.setBackground(ripple(Color.TRANSPARENT, 9));
        scriptSettings.setOnClickListener(v -> {
            tap(v);
            openScriptSettings(editorScriptId, urlInput.getText().toString().trim(), editorSettings, (value, rest) -> {
                if (value != null) urlInput.setText(value);
                editorSettings = rest;
            });
        });
        scriptSettingsButton = scriptSettings;
        section.addView(scriptSettings, new LinearLayout.LayoutParams(-1, dp(44)));
        updateScriptSettingsButton();

        TextView streamHint = text(getString(R.string.stream_hint),
                11, secondary, false);
        streamHintView = streamHint;
        LinearLayout.LayoutParams streamHintParams = matchWrap();
        streamHintParams.topMargin = dp(8);
        streamHintParams.leftMargin = dp(4);
        streamHintParams.rightMargin = dp(4);
        section.addView(streamHint, streamHintParams);

        // The key, its hint and the generator; the stream mode has no key.
        LinearLayout encryptionBox = new LinearLayout(this);
        encryptionBox.setOrientation(LinearLayout.VERTICAL);
        encryptionSection = encryptionBox;
        section.addView(encryptionBox, matchWrap());
        LinearLayout.LayoutParams encryptionParams = new LinearLayout.LayoutParams(-1, dp(FIELD_HEIGHT));
        encryptionParams.topMargin = dp(8);
        encryptionBox.addView(buildEncryptionField(initialSecret), encryptionParams);
        TextView encryptionHint = text("", 11, secondary, false);
        encryptionHintView = encryptionHint;
        LinearLayout.LayoutParams encryptionHintParams = matchWrap();
        encryptionHintParams.topMargin = dp(5);
        encryptionHintParams.leftMargin = dp(4);
        encryptionHintParams.rightMargin = dp(4);
        encryptionBox.addView(encryptionHint, encryptionHintParams);
        Button generateKey = new Button(this);
        generateKey.setText(getString(R.string.generate_key));
        generateKey.setAllCaps(false);
        generateKey.setTextColor(accent);
        generateKey.setTextSize(13);
        generateKey.setStateListAnimator(null);
        generateKey.setBackground(ripple(Color.TRANSPARENT, 9));
        generateKey.setOnClickListener(v -> {
            tap(v);
            generateEncryptionSecret();
        });
        LinearLayout.LayoutParams generateParams = new LinearLayout.LayoutParams(-1, dp(44));
        generateParams.topMargin = dp(4);
        encryptionBox.addView(generateKey, generateParams);

        sessionFieldsContainer = buildSessionFields();
        section.addView(sessionFieldsContainer, matchWrap());
        applySessionVisibility();

        if (existing != null) {
            Profile toDelete = existing;
            Button delete = new Button(this);
            delete.setText(getString(R.string.profile_delete));
            delete.setAllCaps(false);
            delete.setTextColor(darkMode ? Color.rgb(242, 139, 130) : Color.rgb(217, 48, 37));
            delete.setTextSize(14);
            delete.setStateListAnimator(null);
            delete.setBackground(ripple(Color.TRANSPARENT, 9));
            delete.setOnClickListener(v -> {
                tap(v);
                deleteProfile(toDelete);
            });
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(-1, dp(44));
            deleteParams.topMargin = dp(6);
            deleteParams.bottomMargin = dp(12);
            section.addView(delete, deleteParams);
        }
        return section;
    }

    private View buildSessionModeSelector() {
        return segmented(new String[]{getString(R.string.editor_classic), "Session", getString(R.string.editor_serverless)},
                editorStream ? 2 : editorSession ? 1 : 0, i -> {
            editorSession = i == 1;
            editorStream = i == 2;
            applySessionVisibility();
        });
    }

    // The main field's label in the editor: in stream mode Cups.online takes
    // the room's link, not the classic exit's room code.
    private String editorValueLabel() {
        if ("script".equals(editorTransportType)) return scriptValueLabel(editorScriptId);
        return editorStream && "cupsonline".equals(editorTransportType)
                ? getString(R.string.cups_room_link) : transportValueLabel(editorTransportType);
    }

    // The profile field of a JS transport: its primary param's label.
    private String scriptValueLabel(String scriptId) {
        ScriptStore.Script script = new ScriptStore(this).byId(scriptId);
        org.json.JSONObject param = script == null ? null : script.primaryParam();
        String label = param == null ? "" : param.optString("label", param.optString("key"));
        return label.isEmpty() ? getString(R.string.script_value_hint) : label;
    }

    private void updateScriptSettingsButton() {
        if (scriptSettingsButton == null) return;
        ScriptStore.Script script = "script".equals(editorTransportType) ? new ScriptStore(this).byId(editorScriptId) : null;
        scriptSettingsButton.setVisibility(script != null && script.hasSettings() ? View.VISIBLE : View.GONE);
    }

    // Opens a JS transport's settings page (the core builds it from what the
    // script declares, or the script brings its own) prefilled from the
    // carrier's saved values plus its profile field; onSave gets the field's
    // new value and the rest of the settings.
    private void openScriptSettings(String scriptId, String value, JSONObject saved,
            java.util.function.BiConsumer<String, JSONObject> onSave) {
        ScriptStore store = new ScriptStore(this);
        ScriptStore.Script script = store.byId(scriptId);
        if (script == null) return;
        new Thread(() -> {
            String message = null;
            JSONObject answer = null;
            try {
                byte[][] pkg = store.packageBytes(script);
                if (pkg == null) throw new IllegalStateException(getString(R.string.script_file_missing));
                JSONObject values = Profile.copyOf(saved);
                if (!script.primaryKey().isEmpty()) values.put(script.primaryKey(), value);
                answer = new JSONObject(Mobile.scriptSettings(pkg[0], pkg[1], script.pubkey, values.toString(), "ru"));
                if (!answer.optBoolean("ok")) message = scriptSettingsProblem(answer);
            } catch (Exception e) {
                message = getString(R.string.script_settings_failed, e.getMessage());
            }
            String problem = message;
            JSONObject page = answer;
            runOnUiThread(() -> {
                if (problem != null) {
                    Toast.makeText(this, problem, Toast.LENGTH_LONG).show();
                    return;
                }
                pendingSettingsKeys = new java.util.HashSet<>();
                org.json.JSONArray params = page.optJSONArray("params");
                for (int i = 0; params != null && i < params.length(); i++) {
                    pendingSettingsKeys.add(params.optJSONObject(i).optString("key"));
                }
                pendingSettingsPrimary = script.primaryKey();
                pendingSettingsSave = onSave;
                startActivityForResult(new Intent(this, CaptchaActivity.Settings.class)
                        .putExtra(CaptchaActivity.EXTRA_SETTINGS_HTML, page.optString("html"))
                        .putExtra(CaptchaActivity.EXTRA_TITLE, getString(R.string.script_settings_title, script.name)), SCRIPT_SETTINGS_REQUEST);
            });
        }, "script-settings").start();
    }

    // What the settings page submitted: only what the script declares (a
    // custom page may send more), the profile field's key split back out.
    private void onScriptSettings(String json) {
        java.util.function.BiConsumer<String, JSONObject> save = pendingSettingsSave;
        pendingSettingsSave = null;
        if (json == null || save == null) return;
        JSONObject values;
        try {
            values = ScriptStore.flatten(this, json);
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        String primary = null;
        JSONObject rest = new JSONObject();
        for (java.util.Iterator<String> keys = values.keys(); keys.hasNext(); ) {
            String key = keys.next();
            if (!pendingSettingsKeys.isEmpty() && !pendingSettingsKeys.contains(key)) continue;
            if (key.equals(pendingSettingsPrimary)) primary = values.optString(key);
            else {
                try {
                    rest.put(key, values.optString(key));
                } catch (org.json.JSONException ignored) {
                    // A non-empty string key always fits.
                }
            }
        }
        save.accept(primary, rest);
        Toast.makeText(this, getString(R.string.script_settings_moved), Toast.LENGTH_LONG).show();
    }

    // Why the core could not open a script's settings (upstream's ScriptSettingsMessages).
    private String scriptSettingsProblem(JSONObject page) {
        switch (page.optString("code")) {
            case "no_key": return getString(R.string.script_settings_no_key);
            case "bad_signature": return getString(R.string.script_settings_bad_signature);
            case "no_settings": return getString(R.string.script_settings_none);
            case "needs_newer_app": return getString(R.string.script_settings_needs_app);
            default: return getString(R.string.script_settings_failed, page.optString("error", getString(R.string.unknown_error_lower)));
        }
    }

    private void applySessionVisibility() {
        if (sessionFieldsContainer != null) {
            sessionFieldsContainer.setVisibility(editorSession ? View.VISIBLE : View.GONE);
        }
        if (codecSection != null) codecSection.setVisibility(editorSession || editorStream ? View.GONE : View.VISIBLE);
        if (encryptionSection != null) encryptionSection.setVisibility(editorStream ? View.GONE : View.VISIBLE);
        if (streamHintView != null) streamHintView.setVisibility(editorStream ? View.VISIBLE : View.GONE);
        setFloatingLabel(urlInput, editorValueLabel());
        // Session cannot run without the key; classic mode can.
        setFloatingLabel(encryptionInput, editorSession
                ? getString(R.string.e2e_key) : getString(R.string.e2e_key_optional));
        if (encryptionHintView != null) {
            encryptionHintView.setText(editorSession
                    ? getString(R.string.e2e_key_session_hint)
                    : getString(R.string.e2e_key_optional_hint));
        }
    }

    private View buildSessionFields() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        TextView sessionLabel = label("SESSION");
        LinearLayout.LayoutParams sessionLabelParams = matchWrap();
        sessionLabelParams.topMargin = dp(18);
        sessionLabelParams.bottomMargin = dp(6);
        box.addView(sessionLabel, sessionLabelParams);
        TextView hintView = text(
                getString(R.string.session_hint),
                11, secondary, false);
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.leftMargin = dp(4);
        hintParams.rightMargin = dp(4);
        box.addView(hintView, hintParams);

        priorityInput = settingInput(getString(R.string.main_priority),
                Integer.toString(editorPriority), InputType.TYPE_CLASS_NUMBER);
        boxedInput(box, priorityInput, dp(16));

        TextView extrasLabel = label(getString(R.string.extra_transports));
        LinearLayout.LayoutParams extrasLabelParams = matchWrap();
        extrasLabelParams.topMargin = dp(18);
        box.addView(extrasLabel, extrasLabelParams);

        extrasList = new LinearLayout(this);
        extrasList.setOrientation(LinearLayout.VERTICAL);
        box.addView(extrasList, matchWrap());
        refreshExtras();

        Button add = new Button(this);
        add.setText(getString(R.string.add_transport));
        add.setAllCaps(false);
        add.setTextColor(accent);
        add.setTextSize(13);
        add.setStateListAnimator(null);
        add.setBackground(ripple(Color.TRANSPARENT, 9));
        add.setOnClickListener(v -> {
            tap(v);
            editorExtras.add(new Profile.Transport());
            refreshExtras();
        });
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(-1, dp(44));
        addParams.topMargin = dp(4);
        box.addView(add, addParams);
        return box;
    }

    private void refreshExtras() {
        if (extrasList == null) return;
        extrasList.removeAllViews();
        for (Profile.Transport t : editorExtras) {
            LinearLayout.LayoutParams rowParams = matchWrap();
            rowParams.topMargin = dp(8);
            extrasList.addView(buildExtraTransportRow(t), rowParams);
        }
    }

    private View buildExtraTransportRow(Profile.Transport t) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(surface, border, 1, 20));
        card.setPadding(dp(14), dp(4), dp(14), dp(14));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text(carrierLabel(t.type, t.scriptId), 15, text, true), new LinearLayout.LayoutParams(0, -2, 1f));
        Button remove = new Button(this);
        remove.setText(getString(R.string.delete));
        remove.setAllCaps(false);
        remove.setTextColor(darkMode ? Color.rgb(242, 139, 130) : Color.rgb(217, 48, 37));
        remove.setTextSize(13);
        remove.setStateListAnimator(null);
        remove.setBackground(ripple(Color.TRANSPARENT, 9));
        remove.setOnClickListener(v -> {
            tap(v);
            editorExtras.remove(t);
            refreshExtras();
        });
        header.addView(remove, new LinearLayout.LayoutParams(-2, dp(44)));
        card.addView(header, matchWrap());

        List<ScriptStore.Script> scripts = new ScriptStore(this).usable();
        boolean js = !scripts.isEmpty() || "script".equals(t.type);
        String[] types = js ? new String[]{"direct", "yandex", "vyandex", "boards", "mailru", "cupsonline", "oneme", "script"}
                : new String[]{"direct", "yandex", "vyandex", "boards", "mailru", "cupsonline", "oneme"};
        String[] typeLabels = js ? new String[]{"Direct", "Yandex", "Volga", "Board", "Mail.ru", "Cups", "MAX", "JS"}
                : new String[]{"Direct", "Yandex", "Volga", "Board", "Mail.ru", "Cups", "MAX"};
        card.addView(chips(typeLabels, java.util.Arrays.asList(types).indexOf(t.type), i -> {
            t.type = types[i];
            t.value = "";
            t.uid = "";
            t.scriptId = "";
            t.settings = new JSONObject();
            refreshExtras();
        }), matchWrap());

        if ("script".equals(t.type)) {
            String[] names = new String[scripts.size()];
            for (int i = 0; i < names.length; i++) names[i] = scripts.get(i).name;
            int picked = -1;
            for (int i = 0; i < names.length; i++) if (scripts.get(i).id.equals(t.scriptId)) picked = i;
            if (names.length == 0) {
                card.addView(text(getString(R.string.scripts_none_enabled_advanced), 12, secondary, false));
            } else {
                card.addView(chips(names, picked, i -> {
                    t.scriptId = scripts.get(i).id;
                    t.settings = new JSONObject();
                    refreshExtras();
                }), matchWrap());
            }
        }

        boolean max = "oneme".equals(t.type);
        boolean direct = "direct".equals(t.type);
        boolean script = "script".equals(t.type);
        EditText value = settingInput(script ? scriptValueLabel(t.scriptId) : transportValueLabel(t.type), t.value,
                max ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                        : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        bindText(value, v -> t.value = v);
        boxedInput(card, value, dp(12));
        ScriptStore.Script installed = script ? new ScriptStore(this).byId(t.scriptId) : null;
        if (installed != null && installed.hasSettings()) {
            Button settings = new Button(this);
            settings.setText(getString(R.string.transport_settings));
            settings.setAllCaps(false);
            settings.setTextColor(accent);
            settings.setTextSize(13);
            settings.setStateListAnimator(null);
            settings.setBackground(ripple(Color.TRANSPARENT, 9));
            settings.setOnClickListener(v -> {
                tap(v);
                openScriptSettings(t.scriptId, t.value, t.settings, (newValue, rest) -> {
                    if (newValue != null) t.value = newValue;
                    t.settings = rest;
                    refreshExtras();
                });
            });
            card.addView(settings, new LinearLayout.LayoutParams(-1, dp(44)));
        }
        if (max) {
            EditText uid = settingInput("MAX call user id", t.uid, InputType.TYPE_CLASS_NUMBER);
            bindText(uid, v -> t.uid = v);
            boxedInput(card, uid, dp(12));
        }
        EditText priority = settingInput(getString(R.string.priority), Integer.toString(t.priority),
                InputType.TYPE_CLASS_NUMBER);
        bindText(priority, v -> t.priority = parsePriority(v, t.priority));
        boxedInput(card, priority, dp(12));
        return card;
    }

    // Returns why an extra Session transport can't be saved, or null.
    private String extraTransportProblem(Profile.Transport t) {
        if ("script".equals(t.type) && t.scriptId.isEmpty()) return getString(R.string.script_pick);
        return transportValueProblem(t.type, t.value, true);
    }

    private static final Pattern CUPS_ROOMS_CODE = Pattern.compile("[A-Za-z0-9_-]+");

    // Returns why value can't serve as the transport's main field (document
    // link, board link, room code, node address, token), or null. Cups.online
    // takes the base64 room code the exit prints, or a link carrying it; the
    // exit itself creates the rooms and needs nothing, so an empty code is
    // allowed when saving (allowEmptyCups) and checked again on connect.
    private String transportValueProblem(String type, String value, boolean allowEmptyCups) {
        String label = transportLabel(type);
        switch (type) {
            case "direct": {
                int colon = value.lastIndexOf(':');
                int port = colon > 0 ? parsePriority(value.substring(colon + 1), -1) : -1;
                return colon <= 0 || port < 1 || port > 65535 ? getString(R.string.value_need_host_port, label) : null;
            }
            case "oneme":
                return value.isEmpty() ? getString(R.string.max_need_token) : null;
            case "script":
                return null; // the script validates its own params in the engine
            case "cupsonline":
                if (value.isEmpty()) return allowEmptyCups ? null : getString(R.string.cups_need_code);
                return isValidDocumentUrl(value) || CUPS_ROOMS_CODE.matcher(value).matches()
                        ? null : getString(R.string.cups_bad_code);
            case "boards":
                return isValidDocumentUrl(value) ? null : getString(R.string.value_need_board_link, label);
            default:
                return isValidDocumentUrl(value) ? null : getString(R.string.value_need_doc_link, label);
        }
    }

    // The main field's label for a transport type.
    private String transportValueLabel(String type) {
        switch (type) {
            case "direct": return getString(R.string.direct_address);
            case "oneme": return "MAX Web token";
            case "boards": return getString(R.string.boards_link);
            case "mailru": return getString(R.string.mailru_link);
            case "cupsonline": return getString(R.string.cups_code);
            case "vyandex": return getString(R.string.vyandex_link);
            default: return getString(R.string.yandex_link);
        }
    }

    private static int parsePriority(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void boxedInput(LinearLayout parent, EditText input, int topMargin) {
        FrameLayout field = new FrameLayout(this);
        field.setBackground(rounded(surface, border, 1, 10));
        input.setPadding(dp(16), 0, dp(16), 0);
        field.addView(input, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(FIELD_HEIGHT));
        params.topMargin = Math.max(0, topMargin - dp(FLOAT_ROOM));
        parent.addView(floating(field, input), params);
    }

    private static void bindText(EditText input, Consumer<String> sink) {
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { sink.accept(s.toString().trim()); }
        });
    }

    private View buildTransportTypeSelector() {
        String[] builtIn = {"yandex", "vyandex", "boards", "mailru", "cupsonline", "oneme"};
        String[][] builtInLabels = {
                {"Yandex Docs", getString(R.string.yandex_doc)},
                {"Yandex Docs (Volga)", getString(R.string.experimental)},
                {"Yandex Board", getString(R.string.boards_detail)},
                {"Mail.ru Docs", getString(R.string.mailru_doc)},
                {"Cups.online", getString(R.string.cups_detail)},
                {"MAX (OneMe)", getString(R.string.max_detail)},
        };
        int[] builtInIcons = {R.drawable.ic_yandex, R.drawable.ic_yandex, R.drawable.ic_yandex,
                R.drawable.ic_mailru, R.drawable.ic_code, R.drawable.ic_max};
        List<ScriptStore.Script> scripts = new ScriptStore(this).usable();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        FrameLayout list = new FrameLayout(this);
        // Shows one tab's list, its row checked only when it holds the
        // selection; the other tab is rebuilt when switched to.
        java.util.function.IntConsumer show = tab -> {
            list.removeAllViews();
            if (tab == 0) {
                int current = java.util.Arrays.asList(builtIn).indexOf(editorTransportType);
                list.addView(choiceList(builtInLabels, builtInIcons, current, i -> pickTransport(builtIn[i], "")));
                return;
            }
            String[][] labels = new String[scripts.size()][];
            int[] icons = new int[scripts.size()];
            int current = -1;
            for (int i = 0; i < labels.length; i++) {
                ScriptStore.Script script = scripts.get(i);
                labels[i] = new String[]{script.name, getString(R.string.script_author_version, script.official
                        ? getString(R.string.script_signed_openflux) : getString(R.string.script_third_party), script.version)};
                icons[i] = R.drawable.ic_code;
                if ("script".equals(editorTransportType) && script.id.equals(editorScriptId)) current = i;
            }
            LinearLayout js = new LinearLayout(this);
            js.setOrientation(LinearLayout.VERTICAL);
            js.addView(choiceList(labels, icons, current, i -> pickTransport("script", scripts.get(i).id)));
            TextView hint = text(getString(R.string.scripts_session_only_hint_advanced),
                    11, secondary, false);
            LinearLayout.LayoutParams hintParams = matchWrap();
            hintParams.topMargin = dp(6);
            hintParams.leftMargin = dp(4);
            js.addView(hint, hintParams);
            list.addView(js);
        };
        boolean scriptTab = "script".equals(editorTransportType);
        // The tabs only once there is a JS transport to offer.
        if (!scripts.isEmpty()) {
            LinearLayout.LayoutParams tabsParams = matchWrap();
            tabsParams.bottomMargin = dp(10);
            box.addView(segmented(new String[]{getString(R.string.scripts_builtin), "JavaScript"}, scriptTab ? 1 : 0, show), tabsParams);
        }
        show.accept(scriptTab && !scripts.isEmpty() ? 1 : 0);
        box.addView(list, matchWrap());
        return box;
    }

    private void pickTransport(String type, String scriptId) {
        if (!scriptId.equals(editorScriptId)) editorSettings = new JSONObject();
        editorScriptId = scriptId;
        editorTransportType = type;
        updateScriptSettingsButton();
        if (maxFieldsContainer != null) {
            maxFieldsContainer.setVisibility("oneme".equals(editorTransportType) ? View.VISIBLE : View.GONE);
        }
        setFloatingLabel(urlInput, editorValueLabel());
        if (urlField != null) urlField.setVisibility("oneme".equals(editorTransportType) ? View.GONE : View.VISIBLE);
    }

    // MAX (OneMe) authenticates via a web token + numeric user id instead of
    // a document URL - only shown/required when that transport is selected.
    private View buildMaxFields(Profile existing) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        FrameLayout tokenField = new FrameLayout(this);
        tokenField.setBackground(rounded(surface, border, 1, 10));
        maxTokenInput = settingInput("MAX Web token", existing != null ? existing.maxToken : "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        maxTokenInput.setPadding(dp(16), 0, dp(16), 0);
        tokenField.addView(maxTokenInput, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams tokenParams = new LinearLayout.LayoutParams(-1, dp(FIELD_HEIGHT));
        box.addView(floating(tokenField, maxTokenInput), tokenParams);

        FrameLayout uidField = new FrameLayout(this);
        uidField.setBackground(rounded(surface, border, 1, 10));
        maxUidInput = settingInput("MAX call user id", existing != null ? existing.maxUid : "",
                InputType.TYPE_CLASS_NUMBER);
        maxUidInput.setPadding(dp(16), 0, dp(16), 0);
        uidField.addView(maxUidInput, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams uidParams = new LinearLayout.LayoutParams(-1, dp(FIELD_HEIGHT));
        uidParams.topMargin = dp(8);
        box.addView(floating(uidField, maxUidInput), uidParams);

        return box;
    }

    private View buildCodecSelector() {
        return choiceList(new String[][]{
                {"Batched + zstd", getString(R.string.codec_default)},
                {"Legacy (LZ4)", getString(R.string.codec_legacy_detail)},
        }, new int[]{R.drawable.ic_codec_fast, R.drawable.ic_codec_legacy}, "legacy".equals(editorCodec) ? 1 : 0, i -> editorCodec = i == 1 ? "legacy" : "batched");
    }

    private View buildNetworkSettings() {
        LinearLayout section = page();

        Switch dnsAutoSwitch = settingSwitch(R.drawable.ic_public, getString(R.string.dns_default),
                getString(R.string.dns_default_detail),
                editorDnsAuto);
        View dnsAutoRow = (View) dnsAutoSwitch.getTag();
        section.addView(dnsAutoRow, matchWrap());

        dnsInput = settingInput(getString(R.string.dns_server), editorDnsServer,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        View dnsInputRow = settingRow(R.drawable.ic_public, getString(R.string.dns_custom), dnsInput);
        setInitialVisibility(dnsInputRow, !editorDnsAuto);
        LinearLayout.LayoutParams dnsInputParams = matchWrap();
        dnsInputParams.topMargin = dp(8);
        section.addView(dnsInputRow, dnsInputParams);
        View dnsHint = fieldHint(
                getString(R.string.dns_custom_hint));
        setInitialVisibility(dnsHint, !editorDnsAuto);
        section.addView(dnsHint);

        dnsAutoSwitch.setOnCheckedChangeListener((button, checked) -> {
            tap(button);
            editorDnsAuto = checked;
            setViewVisibleAnimated(dnsInputRow, !checked);
            setViewVisibleAnimated(dnsHint, !checked);
        });

        mtuInput = settingInput("MTU", String.valueOf(editorMtu), InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams mtuParams = matchWrap();
        mtuParams.topMargin = dp(16);
        section.addView(settingRow(R.drawable.ic_settings, getString(R.string.mtu), mtuInput), mtuParams);
        section.addView(fieldHint(
                getString(R.string.mtu_hint)));

        Switch killSwitchSwitch = settingSwitch(R.drawable.ic_lock, "Kill Switch",
                getString(R.string.killswitch_detail),
                editorKillSwitchEnabled);
        killSwitchSwitch.setOnCheckedChangeListener((button, checked) -> {
            tap(button);
            editorKillSwitchEnabled = checked;
        });
        LinearLayout.LayoutParams killSwitchParams = matchWrap();
        killSwitchParams.topMargin = dp(16);
        section.addView((View) killSwitchSwitch.getTag(), killSwitchParams);
        section.addView(fieldHint(
                getString(R.string.killswitch_hint)));

        return section;
    }

    // Commits the Network draft fields to the live settings. Called by the
    // generic "Сохранить настройки" button in buildSettingsPage() - editing
    // these fields and pressing back without it discards the draft.
    private boolean applyNetworkSettings() {
        String selectedDns = editorDnsAuto ? "" : DnsServer.normalizeIpv4(dnsInput.getText().toString());
        if (selectedDns == null) {
            Toast.makeText(this, getString(R.string.dns_need_ipv4), Toast.LENGTH_LONG).show();
            return false;
        }
        dnsServer = selectedDns;
        int newMtu;
        try {
            newMtu = Integer.parseInt(mtuInput.getText().toString().trim());
        } catch (NumberFormatException ignored) {
            newMtu = DEFAULT_MTU;
        }
        mtu = Math.max(576, Math.min(1500, newMtu));
        killSwitchEnabled = editorKillSwitchEnabled;
        persistSettings();
        return true;
    }

    private void applyAppsSettings() {
        appFilterMode = editorAppFilterMode;
        selectedApps.clear();
        selectedApps.addAll(editorSelectedApps);
        persistAppFilter();
    }

    private View buildRoutingSettings() {
        LinearLayout section = page();
        TextView routingHint = text(
                getString(R.string.routing_hint),
                12, secondary, false);
        section.addView(routingHint, matchWrap());

        if (!isProxyMode()) {
            TextView tunnelNote = text(
                    getString(R.string.routing_proxy_only),
                    12, accent, false);
            LinearLayout.LayoutParams tunnelNoteParams = matchWrap();
            tunnelNoteParams.topMargin = dp(8);
            section.addView(tunnelNote, tunnelNoteParams);
        }

        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.VERTICAL);
        for (DomainFilter.Preset preset : DomainFilter.PRESETS) {
            Switch presetSwitch = settingSwitch(presetIcon(preset.id), getString(preset.title), getString(preset.description),
                    editorEnabledDomainPresets.contains(preset.id));
            presetSwitch.setOnCheckedChangeListener((button, checked) -> {
                tap(button);
                if (checked) editorEnabledDomainPresets.add(preset.id);
                else editorEnabledDomainPresets.remove(preset.id);
            });
            presets.addView((View) presetSwitch.getTag(), matchWrap());
        }
        groupTiles(presets);
        LinearLayout.LayoutParams presetsParams = matchWrap();
        presetsParams.topMargin = dp(12);
        section.addView(presets, presetsParams);

        LinearLayout.LayoutParams customLabelParams = matchWrap();
        customLabelParams.topMargin = dp(16);
        section.addView(text(getString(R.string.routing_custom), 13, secondary, false), customLabelParams);

        customDomainsInput = new EditText(this);
        customDomainsInput.setHint("youtube.com\nexample.org");
        customDomainsInput.setHintTextColor(hint);
        customDomainsInput.setText(editorCustomDomains);
        bindText(customDomainsInput, value -> editorCustomDomains = value);
        customDomainsInput.setTextSize(14);
        customDomainsInput.setTextColor(text);
        customDomainsInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        customDomainsInput.setMinLines(3);
        customDomainsInput.setGravity(Gravity.TOP | Gravity.START);
        customDomainsInput.setBackground(rounded(surface, border, 1, 10));
        customDomainsInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams customInputParams = matchWrap();
        customInputParams.topMargin = dp(6);
        section.addView(customDomainsInput, customInputParams);
        section.addView(fieldHint(getString(R.string.routing_custom_hint)));

        return section;
    }

    private static int presetIcon(String id) {
        switch (id) {
            case "ru": return R.drawable.ic_gerb;
            case "youtube": return R.drawable.ic_youtube;
            case "discord": return R.drawable.ic_discord;
            case "ai": return R.drawable.ic_openai;
            default: return R.drawable.ic_public;
        }
    }

    private void applyRoutingSettings() {
        enabledDomainPresets.clear();
        enabledDomainPresets.addAll(editorEnabledDomainPresets);
        customDomains.clear();
        for (String line : editorCustomDomains.split("\n")) {
            String trimmed = line.trim().toLowerCase(java.util.Locale.ROOT);
            if (!trimmed.isEmpty()) customDomains.add(trimmed);
        }
        persistDomainFilter();
    }

    private void persistDomainFilter() {
        domainFilterPrefs.edit()
                .putStringSet(DomainFilter.KEY_ENABLED_PRESETS, new HashSet<>(enabledDomainPresets))
                .putStringSet(DomainFilter.KEY_CUSTOM_DOMAINS, new HashSet<>(customDomains))
                .apply();
    }

    private TextView fieldHint(String value) {
        TextView hint = text(value, 11, secondary, false);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(5);
        params.leftMargin = dp(4);
        params.rightMargin = dp(4);
        hint.setLayoutParams(params);
        return hint;
    }

    // Interface: the look, the language and the log, applied at once like a
    // system settings screen.
    private View buildInterfaceSettings() {
        LinearLayout section = page();
        sectionLabel(section, getString(R.string.theme_label), true);
        String[] modeLabels = new String[Theme.MODES.length];
        for (int i = 0; i < modeLabels.length; i++) modeLabels[i] = Theme.label(this, Theme.MODES[i]);
        section.addView(segmented(modeLabels, java.util.Arrays.asList(Theme.MODES).indexOf(themeMode),
                i -> setLook(Theme.MODES[i], blackBackground)), matchWrap());
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        Switch blackSwitch = settingSwitch(R.drawable.ic_dark_mode, getString(R.string.theme_black),
                getString(R.string.theme_black_detail), blackBackground);
        blackSwitch.setOnCheckedChangeListener((button, checked) -> {
            tap(button);
            setLook(themeMode, checked);
        });
        group.addView((View) blackSwitch.getTag(), matchWrap());
        groupTiles(group);
        LinearLayout.LayoutParams groupParams = matchWrap();
        groupParams.topMargin = dp(10);
        section.addView(group, groupParams);

        sectionLabel(section, getString(R.string.language_label), false);
        String[][] languages = new String[Lang.TAGS.length][];
        for (int i = 0; i < languages.length; i++) {
            String tag = Lang.TAGS[i];
            languages[i] = tag.isEmpty()
                    ? new String[]{Lang.label(this, tag), getString(R.string.language_system_detail)}
                    : new String[]{Lang.label(this, tag)};
        }
        section.addView(choiceList(languages, null, java.util.Arrays.asList(Lang.TAGS).indexOf(Lang.get(this)),
                i -> setLanguage(Lang.TAGS[i])), matchWrap());

        sectionLabel(section, getString(R.string.logs_label), false);
        LinearLayout logGroup = new LinearLayout(this);
        logGroup.setOrientation(LinearLayout.VERTICAL);
        Switch scrollSwitch = settingSwitch(R.drawable.ic_terminal, getString(R.string.log_autoscroll),
                getString(R.string.log_autoscroll_detail), autoScroll);
        scrollSwitch.setOnCheckedChangeListener((button, checked) -> {
            tap(button);
            autoScroll = checked;
            getSharedPreferences(SETTINGS_PREFS_NAME, MODE_PRIVATE).edit().putBoolean("auto_scroll", checked).apply();
        });
        logGroup.addView((View) scrollSwitch.getTag(), matchWrap());
        groupTiles(logGroup);
        section.addView(logGroup, matchWrap());
        return section;
    }

    private String themeSummary() {
        String mode = Theme.label(this, themeMode);
        return blackBackground && !Theme.LIGHT.equals(themeMode) ? getString(R.string.theme_with_black, mode) : mode;
    }

    // Saves the look and, when the screen would look different, rebuilds
    // it in the new colors on the same page.
    private void setLook(String mode, boolean black) {
        themeMode = mode;
        blackBackground = black;
        Theme.save(this, mode, black);
        Theme.Colors next = Theme.colors(this);
        if (next.dark == colors.dark && next.black == colors.black) return;
        captureSettings();
        View oldRoot = root;
        oldRoot.animate().cancel();
        oldRoot.animate().alpha(0.15f).setDuration(110).withEndAction(() -> {
            applyPalette();
            configureSystemBars();
            buildShell();
            showPage(currentPage);
        }).start();
    }

    private void setLanguage(String tag) {
        if (tag.equals(Lang.get(this))) return;
        Lang.set(this, tag);
        // Android 13+ recreates the activity in the new language itself.
        if (Build.VERSION.SDK_INT < 33) recreate();
    }

    // Advanced: the JS transports and what the app is.
    private View buildAdvancedSettings() {
        LinearLayout section = page();
        sectionLabel(section, getString(R.string.settings_scripts_label), true);
        scriptsPanel = new ScriptsPanel(this);
        section.addView(scriptsPanel.view(), matchWrap());
        sectionLabel(section, getString(R.string.settings_about_label), false);
        section.addView(buildAboutSettings(), matchWrap());
        return section;
    }

    private View buildAppsSettings() {
        LinearLayout section = page();

        RecyclerView appListView = new RecyclerView(this);
        appListView.setLayoutManager(new LinearLayoutManager(this));
        appListView.setClipToPadding(false);
        appListView.setPadding(0, 0, 0, dp(8));
        appListView.setVerticalScrollBarEnabled(false);

        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setMoveDuration(280);
        animator.setAddDuration(160);
        animator.setRemoveDuration(160);
        animator.setSupportsChangeAnimations(false);
        appListView.setItemAnimator(animator);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);

        TextView hint = text(
                getString(R.string.apps_hint),
                12, secondary, false);
        header.addView(hint, matchWrap());

        int generation = ++appsSettingsGeneration;
        List<AppEntry> cachedApps = getInstalledAppsCache(showSystemApps);
        boolean loadingNeeded = cachedApps == null;
        View searchSpacer = new View(this);
        AppListAdapter appAdapter = new AppListAdapter(
                loadingNeeded ? Collections.emptyList() : cachedApps, header,
                !AppFilter.MODE_OFF.equals(editorAppFilterMode), searchSpacer);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setBackground(rounded(surface, border, 1, 10));
        EditText searchInput = settingInput(getString(R.string.apps_search), "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchInput.setSingleLine(true);
        searchInput.setContentDescription(getString(R.string.apps_search_description));
        searchInput.setPadding(dp(10), 0, 0, 0);
        LinearLayout.LayoutParams searchIconParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        searchIconParams.leftMargin = dp(14);
        searchRow.addView(icon(R.drawable.ic_search, this.hint), searchIconParams);
        searchInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, -1, 1f));
        ImageButton clearSearch = iconButton(R.drawable.ic_close,
                getString(R.string.apps_search_clear));
        clearSearch.setVisibility(View.INVISIBLE);
        clearSearch.setOnClickListener(v -> {
            tap(v);
            searchInput.setText("");
        });
        searchRow.addView(clearSearch, new LinearLayout.LayoutParams(dp(48), -1));

        TextView emptyResults = text(getString(R.string.apps_nothing_found), 12, secondary, false);
        emptyResults.setVisibility(View.GONE);
        Runnable updateSearchSpacer = () -> {
            boolean searching = !AppFilter.MODE_OFF.equals(editorAppFilterMode)
                    && ((keyboardOpen && searchInput.hasFocus())
                    || !searchInput.getText().toString().trim().isEmpty());
            int height = searching ? Math.max(0, appListView.getHeight()
                    - appListView.getPaddingTop() - appListView.getPaddingBottom()
                    - searchRow.getHeight() - header.getPaddingBottom()) : 0;
            appAdapter.setSearchSpacerHeight(height);
        };
        Runnable updateSearchState = () -> {
            clearSearch.setVisibility(searchInput.length() == 0 ? View.INVISIBLE : View.VISIBLE);
            boolean noResults = !AppFilter.MODE_OFF.equals(editorAppFilterMode)
                    && getInstalledAppsCache(showSystemApps) != null
                    && !searchInput.getText().toString().trim().isEmpty()
                    && appAdapter.getVisibleAppsCount() == 0;
            emptyResults.setVisibility(noResults ? View.VISIBLE : View.GONE);
            updateSearchSpacer.run();
        };
        bindText(searchInput, query -> {
            appAdapter.setSearchQuery(query);
            updateSearchState.run();
        });
        searchInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != android.view.inputmethod.EditorInfo.IME_ACTION_DONE) return false;
            android.view.inputmethod.InputMethodManager keyboard =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
            searchInput.clearFocus();
            return true;
        });
        searchRow.setVisibility(AppFilter.MODE_OFF.equals(editorAppFilterMode) ? View.GONE : View.VISIBLE);

        Runnable alignSearch = () -> {
            if (!keyboardOpen || !searchInput.hasFocus() || !appListView.isAttachedToWindow()
                    || searchRow.getVisibility() != View.VISIBLE || searchRow.getHeight() == 0) return;
            // The search field is nested in the header; measure its current top in list coordinates.
            int distance = header.getTop() + searchRow.getTop()
                    - appListView.getPaddingTop() - dp(6);
            if (Math.abs(distance) <= dp(1)) return;
            appListView.smoothScrollBy(0, distance,
                    new android.view.animation.AccelerateDecelerateInterpolator(), 300);
        };
        Runnable requestSearchAlignment = () -> {
            appListView.removeCallbacks(alignSearch);
            // Run after layout, when the keyboard's insets and header measurements are applied.
            appListView.post(alignSearch);
        };
        searchInput.setOnFocusChangeListener((view, focused) -> {
            updateSearchSpacer.run();
            if (focused) requestSearchAlignment.run();
            else appListView.removeCallbacks(alignSearch);
        });

        appListView.addOnLayoutChangeListener((view, left, top, right, bottom,
                                               oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom - top == oldBottom - oldTop
                    && right - left == oldRight - oldLeft) return;

            // Update after layout, outside RecyclerView's layout calculation.
            appListView.post(() -> {
                if (!appListView.isAttachedToWindow()) return;
                updateSearchSpacer.run();
                // The keyboard opens after the field takes focus: align once it has.
                if (searchInput.hasFocus()) requestSearchAlignment.run();
            });
        });

        String[] filterModes = {
                AppFilter.MODE_OFF,
                AppFilter.MODE_WHITELIST,
                AppFilter.MODE_BLACKLIST
        };

        // Like the search, the system-apps switch only matters with a list to pick from.
        View[] systemAppsHolder = new View[1];
        View filterChoice = choiceList(new String[][]{
                        {getString(R.string.apps_all)},
                        {getString(R.string.apps_selected_only), getString(R.string.apps_whitelist)},
                        {getString(R.string.apps_all_but_selected), getString(R.string.apps_blacklist)},
                }, null,
                Math.max(0,
                        java.util.Arrays.asList(filterModes)
                                .indexOf(editorAppFilterMode)),
                i -> {
                    editorAppFilterMode = filterModes[i];
                    appAdapter.setAppsVisible(
                            !AppFilter.MODE_OFF.equals(editorAppFilterMode));
                    boolean visible = !AppFilter.MODE_OFF.equals(editorAppFilterMode);
                    if (systemAppsHolder[0] != null) setViewVisibleAnimated(systemAppsHolder[0], visible);
                    searchRow.animate().cancel();

                    if (visible) {
                        searchRow.setAlpha(1f);
                        searchRow.setVisibility(View.VISIBLE);
                    } else {
                        RecyclerView.ItemAnimator listAnimator  = appListView.getItemAnimator();
                        long duration = listAnimator  != null ? listAnimator.getRemoveDuration() : 0L;

                        searchRow.animate()
                                .alpha(0f)
                                .setDuration(duration)
                                .withEndAction(() -> {
                                    if (AppFilter.MODE_OFF.equals(editorAppFilterMode)) {
                                        searchRow.setVisibility(View.GONE);
                                    }
                                })
                                .start();
                    }
                    if (!visible) {
                        android.view.inputmethod.InputMethodManager keyboard =
                                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                        if (keyboard != null) keyboard.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
                        searchInput.clearFocus();
                    }
                    updateSearchState.run();
                });

        LinearLayout.LayoutParams modeGroupParams = matchWrap();
        modeGroupParams.topMargin = dp(12);
        header.addView(filterChoice, modeGroupParams);

        Switch systemAppsSwitch = settingSwitch(R.drawable.ic_apps,
                getString(R.string.apps_show_system),
                getString(R.string.apps_show_system_detail), showSystemApps);
        View systemAppsRow = (View) systemAppsSwitch.getTag();
        systemAppsHolder[0] = systemAppsRow;
        systemAppsRow.setVisibility(AppFilter.MODE_OFF.equals(editorAppFilterMode) ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams systemAppsParams = matchWrap();
        systemAppsParams.topMargin = dp(12);
        header.addView(systemAppsRow, systemAppsParams);

        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(48));
        searchParams.topMargin = dp(12);
        header.addView(searchRow, searchParams);
        LinearLayout.LayoutParams emptyParams = matchWrap();
        emptyParams.topMargin = dp(12);
        header.addView(emptyResults, emptyParams);

        TextView loadStatus = text("", 12, secondary, false);
        loadStatus.setVisibility(View.GONE);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(12);
        header.addView(loadStatus, statusParams);

        Runnable[] requestApps = new Runnable[1];
        int[] loadRequest = {0};
        requestApps[0] = () -> {
            int requestId = ++loadRequest[0];
            boolean includeSystem = showSystemApps;
            emptyResults.setVisibility(View.GONE);
            loadStatus.setVisibility(View.GONE);
            loadStatus.setOnClickListener(null);
            loadInstalledAppsAsync(includeSystem, loaded -> {
                // A previous screen must not update the current settings draft.
                if (requestId != loadRequest[0] || generation != appsSettingsGeneration
                        || currentPage != PAGE_SETTINGS
                        || !settingsDetailOpen || settingsSubTab != SETTINGS_TRAFFIC
                        || trafficTab != TRAFFIC_APPS) return;

                if (loaded == null) {
                    loadStatus.setText(getString(R.string.apps_load_failed));
                    loadStatus.setVisibility(View.VISIBLE);
                    loadStatus.setOnClickListener(v -> {
                        tap(v);
                        requestApps[0].run();
                    });
                    return;
                }

                loadStatus.setVisibility(View.GONE);
                appAdapter.setLoadedApps(loaded);
                updateSearchState.run();
            });
        };

        systemAppsSwitch.setOnCheckedChangeListener((button, checked) -> {
            tap(button);
            showSystemApps = checked;
            // This is a display preference; VPN app selections are saved separately.
            appFilterPrefs.edit().putBoolean("show_system_apps", checked).apply();
            requestApps[0].run();
        });
        systemAppsRow.setOnClickListener(v ->
                systemAppsSwitch.setChecked(!systemAppsSwitch.isChecked()));

        appListView.setAdapter(appAdapter);
        section.addView(appListView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        if (loadingNeeded) requestApps[0].run();
        return section;
    }

    // Shows or hides view with a fade while everything below it slides to
    // its new place, instead of the neighbors jumping when it goes GONE.
    private void setViewVisibleAnimated(View view, boolean visible) {
        view.animate().cancel();
        view.setAlpha(1f);
        view.setTranslationY(0f);
        if ((view.getVisibility() == View.VISIBLE) == visible) return;
        ViewGroup sceneRoot = transitionRoot(view);
        if (sceneRoot != null) {
            android.transition.TransitionSet transition = new android.transition.TransitionSet()
                    .setOrdering(android.transition.TransitionSet.ORDERING_TOGETHER)
                    .addTransition(new android.transition.Fade())
                    .addTransition(new android.transition.ChangeBounds());
            transition.setDuration(280);
            transition.setInterpolator(EMPHASIZED);
            // One transition per frame: later calls in the same toggle are
            // captured by the first.
            android.transition.TransitionManager.beginDelayedTransition(sceneRoot, transition);
        }
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    // The scrolling page content that holds view, so the whole page
    // reflows together.
    private ViewGroup transitionRoot(View view) {
        View child = view;
        for (android.view.ViewParent p = view.getParent(); p instanceof ViewGroup; p = p.getParent()) {
            if (p instanceof ScrollView || p == content) return child instanceof ViewGroup ? (ViewGroup) child : null;
            child = (View) p;
        }
        return null;
    }

    // accent laid over surface at the given strength: the tonal fill of a
    // selected choice.
    private int tonal(float strength) {
        return Color.rgb(
                (int) (Color.red(surface) + (Color.red(accent) - Color.red(surface)) * strength),
                (int) (Color.green(surface) + (Color.green(accent) - Color.green(surface)) * strength),
                (int) (Color.blue(surface) + (Color.blue(accent) - Color.blue(surface)) * strength));
    }

    private android.graphics.drawable.Drawable tile(int fill, int strokeColor, float[] radii) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadii(radii);
        shape.setStroke(dp(1), strokeColor);
        return new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(Color.argb(40, Color.red(accent), Color.green(accent), Color.blue(accent))),
                shape, null);
    }

    private float[] radii(int radius) {
        float r = dp(radius);
        return new float[]{r, r, r, r, r, r, r, r};
    }

    // Icon on a tonal circle, as in the Android 15-16 settings lists.
    private View iconBubble(int iconRes) {
        return iconBubble(iconRes, false);
    }

    private View iconBubble(int iconRes, boolean activeProfile) {
        FrameLayout bubble = new FrameLayout(this);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(activeProfile ? (darkMode ? Color.rgb(29, 66, 49) : Color.rgb(222, 245, 229))
                : tonal(0.2f));
        bubble.setBackground(circle);
        int iconSize = iconRes == R.drawable.ic_yandex ? 18 : 22;
        bubble.addView(icon(iconRes, activeProfile
                        ? (darkMode ? Color.rgb(112, 219, 147) : Color.rgb(31, 143, 70)) : accent),
                new FrameLayout.LayoutParams(dp(iconSize), dp(iconSize), Gravity.CENTER));
        return bubble;
    }

    // Stacks the children of list as one group: 2dp gaps, large outer
    // corners, small inner ones, each tile with its own ripple.
    private void groupTiles(LinearLayout list) {
        int n = list.getChildCount();
        for (int i = 0; i < n; i++) {
            float top = dp(i == 0 ? 24 : 6), bottom = dp(i == n - 1 ? 24 : 6);
            View child = list.getChildAt(i);
            child.setBackground(tile(surface, border,
                    new float[]{top, top, top, top, bottom, bottom, bottom, bottom}));
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) child.getLayoutParams();
            params.topMargin = i == 0 ? 0 : dp(2);
            child.setLayoutParams(params);
        }
    }

    // Material 3 switch: a 56x32 pill track; off, an outlined track with a
    // small thumb; on, a filled track with a large thumb carrying a check.
    void styleSwitch(Switch toggle) {
        // Off state leans toward the accent so the light theme is not a flat grey.
        int off = darkMode ? Color.rgb(140, 147, 160) : tonal(0.55f);
        GradientDrawable trackOn = new GradientDrawable();
        trackOn.setCornerRadius(dp(14));
        trackOn.setColor(accent);
        trackOn.setSize(dp(48), dp(28));
        GradientDrawable trackOff = new GradientDrawable();
        trackOff.setCornerRadius(dp(14));
        trackOff.setColor(darkMode ? background : tonal(0.07f));
        trackOff.setStroke(dp(2), off);
        trackOff.setSize(dp(48), dp(28));
        android.graphics.drawable.StateListDrawable track = new android.graphics.drawable.StateListDrawable();
        track.addState(new int[]{android.R.attr.state_checked}, trackOn);
        track.addState(new int[]{}, trackOff);
        track.setEnterFadeDuration(150);
        track.setExitFadeDuration(150);

        // Both thumbs are 24x28 (the track is twice as wide), so the track
        // keeps its shape while the visible circle grows.
        android.graphics.drawable.Drawable thumbOn = switchThumb(dp(10), Color.WHITE, accent);
        android.graphics.drawable.Drawable thumbOff = switchThumb(dp(7), off, 0);
        android.graphics.drawable.StateListDrawable thumb = new android.graphics.drawable.StateListDrawable();
        thumb.addState(new int[]{android.R.attr.state_checked}, thumbOn);
        thumb.addState(new int[]{}, thumbOff);
        thumb.setEnterFadeDuration(150);
        thumb.setExitFadeDuration(150);

        toggle.setTrackDrawable(track);
        toggle.setThumbDrawable(thumb);
        toggle.setTrackTintList(null);
        toggle.setThumbTintList(null);
        toggle.setSwitchMinWidth(dp(48));
        toggle.setShowText(false);
        toggle.setBackground(null);
    }

    // A switch thumb: a circle of the given radius centered in 24x28, with
    // a check drawn in checkColor when that is not 0.
    private android.graphics.drawable.Drawable switchThumb(float radius, int fill, int checkColor) {
        return new android.graphics.drawable.Drawable() {
            private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            private final android.graphics.Path path = new android.graphics.Path();
            @Override public void draw(android.graphics.Canvas canvas) {
                float cx = getBounds().exactCenterX(), cy = getBounds().exactCenterY();
                paint.setStyle(android.graphics.Paint.Style.FILL);
                paint.setColor(fill);
                canvas.drawCircle(cx, cy, radius, paint);
                if (checkColor == 0) return;
                float u = radius / 12f;
                path.reset();
                path.moveTo(cx - 5 * u, cy);
                path.lineTo(cx - 1.5f * u, cy + 3.5f * u);
                path.lineTo(cx + 5.5f * u, cy - 3.5f * u);
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setStrokeWidth(2.2f * u);
                paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
                paint.setColor(checkColor);
                canvas.drawPath(path, paint);
            }
            @Override public int getIntrinsicWidth() { return dp(24); }
            @Override public int getIntrinsicHeight() { return dp(28); }
            @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
            @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
    }

    private ImageView choiceCheck() {
        ImageView check = icon(R.drawable.ic_check, Color.WHITE);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(accent);
        check.setBackground(dot);
        check.setPadding(dp(3), dp(3), dp(3), dp(3));
        return check;
    }

    // Single choice as stacked tiles, Android 15-16 style: the group has
    // large outer corners and small inner ones, the picked tile gets a tonal
    // fill and a check. options[i] is {title} or {title, subtitle}.
    private View choiceList(String[][] options, int[] icons, int selected, java.util.function.IntConsumer onPick) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int n = options.length;
        LinearLayout[] rows = new LinearLayout[n];
        TextView[] titles = new TextView[n];
        ImageView[] checks = new ImageView[n];
        int[] current = {selected};
        Runnable paint = () -> {
            for (int i = 0; i < n; i++) {
                boolean on = i == current[0];
                float top = dp(i == 0 ? 20 : 6), bottom = dp(i == n - 1 ? 20 : 6);
                rows[i].setBackground(tile(on ? tonal(0.18f) : surface, on ? tonal(0.45f) : border,
                        new float[]{top, top, top, top, bottom, bottom, bottom, bottom}));
                titles[i].setTextColor(on ? accent : text);
                ImageView check = checks[i];
                check.animate().cancel();
                if (on && check.getVisibility() != View.VISIBLE) {
                    check.setVisibility(View.VISIBLE);
                    check.setScaleX(0f);
                    check.setScaleY(0f);
                    check.animate().scaleX(1f).scaleY(1f).setDuration(260)
                            .setInterpolator(new OvershootInterpolator(1.6f)).start();
                } else if (!on) {
                    check.setVisibility(View.INVISIBLE);
                }
            }
        };
        for (int i = 0; i < n; i++) {
            int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(60));
            row.setPadding(dp(18), dp(12), dp(16), dp(12));
            if (icons != null && icons[i] != 0) {
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
                iconParams.rightMargin = dp(14);
                row.addView(iconBubble(icons[i]), iconParams);
                row.setPadding(dp(14), dp(12), dp(16), dp(12));
            }
            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            titles[i] = text(options[i][0], 15, text, true);
            copy.addView(titles[i]);
            if (options[i].length > 1) copy.addView(text(options[i][1], 12, secondary, false));
            row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
            checks[i] = choiceCheck();
            checks[i].setVisibility(i == selected ? View.VISIBLE : View.INVISIBLE);
            row.addView(checks[i], new LinearLayout.LayoutParams(dp(24), dp(24)));
            row.setOnClickListener(v -> {
                if (current[0] == index) return;
                tap(v);
                current[0] = index;
                paint.run();
                onPick.accept(index);
            });
            rows[i] = row;
            LinearLayout.LayoutParams params = matchWrap();
            if (i > 0) params.topMargin = dp(2);
            list.addView(row, params);
        }
        paint.run();
        return list;
    }

    // Material 3 segmented button: one pill split into equal segments, the
    // picked one tonal with a leading check.
    private View segmented(String[] labels, int selected, java.util.function.IntConsumer onPick) {
        LinearLayout bar = new LinearLayout(this);
        int n = labels.length;
        LinearLayout[] segments = new LinearLayout[n];
        TextView[] titles = new TextView[n];
        ImageView[] checks = new ImageView[n];
        int[] current = {selected};
        Runnable paint = () -> {
            for (int i = 0; i < n; i++) {
                boolean on = i == current[0];
                float left = i == 0 ? dp(24) : 0, right = i == n - 1 ? dp(24) : 0;
                segments[i].setBackground(tile(on ? tonal(0.18f) : surface, on ? tonal(0.45f) : border,
                        new float[]{left, left, right, right, right, right, left, left}));
                titles[i].setTextColor(on ? accent : text);
                checks[i].setVisibility(on ? View.VISIBLE : View.GONE);
            }
        };
        for (int i = 0; i < n; i++) {
            int index = i;
            LinearLayout segment = new LinearLayout(this);
            segment.setGravity(Gravity.CENTER);
            checks[i] = icon(R.drawable.ic_check, accent);
            LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dp(18), dp(18));
            checkParams.rightMargin = dp(6);
            segment.addView(checks[i], checkParams);
            titles[i] = text(labels[i], 14, text, true);
            segment.addView(titles[i]);
            segment.setOnClickListener(v -> {
                if (current[0] == index) return;
                tap(v);
                current[0] = index;
                android.transition.TransitionManager.beginDelayedTransition(bar);
                paint.run();
                onPick.accept(index);
            });
            segments[i] = segment;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
            if (i > 0) params.leftMargin = -dp(1);
            bar.addView(segment, params);
        }
        paint.run();
        return bar;
    }

    // A horizontal row of filter chips, one picked.
    private View chips(String[] labels, int selected, java.util.function.IntConsumer onPick) {
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        scroll.addView(row);
        for (int i = 0; i < labels.length; i++) {
            int index = i;
            boolean on = i == selected;
            LinearLayout chip = new LinearLayout(this);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(on ? 10 : 14), 0, dp(14), 0);
            float r = dp(10);
            chip.setBackground(tile(on ? tonal(0.18f) : surface, on ? tonal(0.45f) : border,
                    new float[]{r, r, r, r, r, r, r, r}));
            if (on) {
                LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dp(16), dp(16));
                checkParams.rightMargin = dp(6);
                chip.addView(icon(R.drawable.ic_check, accent), checkParams);
            }
            chip.addView(text(labels[i], 13, on ? accent : text, on));
            chip.setOnClickListener(v -> {
                if (index == selected) return;
                tap(v);
                onPick.accept(index);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(34));
            if (i > 0) params.leftMargin = dp(8);
            row.addView(chip, params);
        }
        return scroll;
    }

    private Button primaryButton(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setStateListAnimator(null);
        button.setBackground(buttonBackground(Color.rgb(79, 124, 255), Color.rgb(59, 93, 191)));
        button.setOnClickListener(v -> {
            bounce(v);
            action.run();
        });
        return button;
    }

    private List<AppEntry> getInstalledAppsCache(boolean includeSystem) {
        return includeSystem ? allInstalledAppsCache : installedAppsCache;
    }

    private void loadInstalledAppsAsync(boolean includeSystem, Consumer<List<AppEntry>> onLoaded) {
        // Cache, callbacks and adapter updates are accessed only on the UI thread.
        List<AppEntry> cached = getInstalledAppsCache(includeSystem);
        if (cached != null) {
            onLoaded.accept(cached);
            return;
        }
        List<Consumer<List<AppEntry>>> pending = includeSystem
                ? allInstalledAppsCallbacks : installedAppsCallbacks;
        pending.add(onLoaded);
        if (includeSystem ? allInstalledAppsLoading : installedAppsLoading) return;
        if (includeSystem) allInstalledAppsLoading = true;
        else installedAppsLoading = true;

        new Thread(() -> {
            List<AppEntry> result;
            try {
                // Package queries, labels and icons stay off the UI thread.
                result = loadInstalledApps(includeSystem);
            } catch (RuntimeException error) {
                android.util.Log.w("OpenFlux", "Failed to load installed apps", error);
                result = null;
            }
            List<AppEntry> loaded = result;
            handler.post(() -> {
                if (includeSystem) {
                    allInstalledAppsLoading = false;
                    if (loaded != null) allInstalledAppsCache = loaded;
                } else {
                    installedAppsLoading = false;
                    if (loaded != null) installedAppsCache = loaded;
                }
                List<Consumer<List<AppEntry>>> callbacks = new ArrayList<>(pending);
                pending.clear();
                if (isFinishing() || isDestroyed()) return;
                for (Consumer<List<AppEntry>> callback : callbacks) callback.accept(loaded);
            });
        }, includeSystem ? "load-all-installed-apps" : "load-launcher-apps").start();
    }

    private List<AppEntry> loadInstalledApps(boolean includeSystem) {
        PackageManager packageManager = getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = packageManager.queryIntentActivities(launcherIntent, 0);
        LinkedHashMap<String, AppEntry> byPackage = new LinkedHashMap<>();
        for (ResolveInfo info : resolved) {
            String packageName = info.activityInfo.packageName;
            if (packageName.equals(getPackageName()) || byPackage.containsKey(packageName)) continue;
            String label = info.loadLabel(packageManager).toString();
            Drawable icon = info.loadIcon(packageManager);
            byPackage.put(packageName, new AppEntry(packageName, label, icon));
        }
        if (includeSystem) {
            // Include packages without launcher activities, such as system services.
            // Existing launcher names and icons remain consistent between both lists.
            for (ApplicationInfo info : packageManager.getInstalledApplications(0)) {
                String packageName = info.packageName;
                if (packageName.equals(getPackageName()) || byPackage.containsKey(packageName)) continue;
                String label = info.loadLabel(packageManager).toString();
                Drawable icon = info.loadIcon(packageManager);
                byPackage.put(packageName, new AppEntry(packageName, label, icon));
            }
        }
        return new ArrayList<>(byPackage.values());
    }

    private void persistAppFilter() {
        appFilterPrefs.edit()
                .putString(AppFilter.KEY_MODE, appFilterMode)
                .putStringSet(AppFilter.KEY_PACKAGES, new HashSet<>(selectedApps))
                .apply();
    }

    // An app in the picker: a selection tile like choiceList's rows, with a
    // round mark (an empty ring until picked, since several can be).
    private View buildAppRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(60));
        row.setPadding(dp(14), dp(10), dp(16), dp(10));
        ImageView icon = new ImageView(this);
        row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView labelView = text("", 15, text, true);
        labels.addView(labelView, new LinearLayout.LayoutParams(-1, -2));
        TextView packageView = text("", 12, secondary, false);
        labels.addView(packageView, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.leftMargin = dp(14);
        labelParams.rightMargin = dp(12);
        row.addView(labels, labelParams);
        ImageView mark = new ImageView(this);
        mark.setPadding(dp(3), dp(3), dp(3), dp(3));
        row.addView(mark, new LinearLayout.LayoutParams(dp(24), dp(24)));
        return row;
    }

    // Paints an app row for its place in the list (the group's ends are
    // rounder, as in choiceList) and whether it is picked.
    private void paintAppRow(View row, AppRowHolder holder, int index, int count, boolean on) {
        float top = dp(index == 0 ? 20 : 6), bottom = dp(index == count - 1 ? 20 : 6);
        row.setBackground(tile(on ? tonal(0.18f) : surface, on ? tonal(0.45f) : border,
                new float[]{top, top, top, top, bottom, bottom, bottom, bottom}));
        holder.label.setTextColor(on ? accent : text);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        if (on) dot.setColor(accent);
        else dot.setStroke(dp(2), hint);
        holder.mark.setBackground(dot);
        holder.mark.setImageResource(on ? R.drawable.ic_check : 0);
        holder.mark.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) row.getLayoutParams();
        int gap = index == 0 ? dp(12) : dp(2);
        if (params != null && params.topMargin != gap) {
            params.topMargin = gap;
            row.setLayoutParams(params);
        }
    }

    private static final class AppEntry {
        final String packageName;
        final String label;
        final Drawable icon;

        AppEntry(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    private static final class AppRowHolder {
        final ImageView icon;
        final TextView label;
        final TextView packageName;
        final ImageView mark;

        AppRowHolder(View row) {
            LinearLayout layout = (LinearLayout) row;
            icon = (ImageView) layout.getChildAt(0);
            LinearLayout labels = (LinearLayout) layout.getChildAt(1);
            label = (TextView) labels.getChildAt(0);
            packageName = (TextView) labels.getChildAt(1);
            mark = (ImageView) layout.getChildAt(2);
        }
    }

    private static final class AppPickerViewHolder extends RecyclerView.ViewHolder {
        final AppRowHolder appRow;

        AppPickerViewHolder(View view, boolean isAppRow) {
            super(view);
            appRow = isAppRow ? new AppRowHolder(view) : null;
        }
    }

    private final class AppListAdapter extends RecyclerView.Adapter<AppPickerViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_APP = 1;
        private static final int TYPE_SPACER = 2;
        private static final String PAINT = "paint";

        private final List<AppEntry> apps;
        private final List<AppEntry> visibleApps = new ArrayList<>();
        private String searchQuery = "";
        private final LinearLayout header;
        private final View searchSpacer;
        private int searchSpacerHeight;
        private final LinkedHashMap<String, Long> itemIds = new LinkedHashMap<>();
        private boolean appsVisible;

        AppListAdapter(List<AppEntry> apps, LinearLayout header, boolean appsVisible, View searchSpacer) {
            // Keep the installed-app cache independent of this screen's ordering.
            this.apps = new ArrayList<>(apps);
            this.header = header;
            this.searchSpacer = searchSpacer;
            this.appsVisible = appsVisible;
            header.setPadding(0, 0, 0, dp(14));

            assignItemIds();
            setHasStableIds(true);
            sortApps();
            rebuildVisibleApps();
        }

        void setSearchSpacerHeight(int height) {
            height = Math.max(0, height);
            if (searchSpacerHeight == height) return;
            searchSpacerHeight = height;
            // Keep enough scroll range for a short result list without adding fake applications.
            ViewGroup.LayoutParams params = searchSpacer.getLayoutParams();
            if (params != null) {
                params.height = height;
                searchSpacer.setLayoutParams(params);
            }
        }

        private void assignItemIds() {
            // Keep package IDs stable even if the backing list is replaced.
            for (AppEntry entry : apps) {
                if (!itemIds.containsKey(entry.packageName)) {
                    itemIds.put(entry.packageName, (long) itemIds.size() + 1);
                }
            }
        }

        void setLoadedApps(List<AppEntry> loaded) {
            List<AppEntry> previous = new ArrayList<>(visibleApps);
            apps.clear();
            apps.addAll(loaded);
            assignItemIds();
            sortApps();
            rebuildVisibleApps();
            if (appsVisible) dispatchAppDiff(previous);
        }

        void setSearchQuery(String query) {
            String normalized = query.trim().toLowerCase(java.util.Locale.ROOT);
            if (searchQuery.equals(normalized)) return;
            List<AppEntry> previous = new ArrayList<>(visibleApps);
            searchQuery = normalized;
            rebuildVisibleApps();
            if (appsVisible) dispatchAppDiff(previous);
        }

        int getVisibleAppsCount() {
            return visibleApps.size();
        }

        private void rebuildVisibleApps() {
            // Filtering changes only the displayed list, never the selected package set.
            visibleApps.clear();
            for (AppEntry entry : apps) {
                if (searchQuery.isEmpty()
                        || entry.label.toLowerCase(java.util.Locale.ROOT).contains(searchQuery)
                        || entry.packageName.toLowerCase(java.util.Locale.ROOT).contains(searchQuery)) {
                    visibleApps.add(entry);
                }
            }
        }

        private void dispatchAppDiff(List<AppEntry> previous) {
            List<AppEntry> current = new ArrayList<>(visibleApps);
            // Keep the header and spacer identities stable while application results change.
            DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return previous.size() + 2; }
                @Override public int getNewListSize() { return current.size() + 2; }

                @Override public boolean areItemsTheSame(int oldPosition, int newPosition) {
                    if (oldPosition == 0 || newPosition == 0) return oldPosition == newPosition;
                    boolean oldSpacer = oldPosition == previous.size() + 1;
                    boolean newSpacer = newPosition == current.size() + 1;
                    if (oldSpacer || newSpacer) return oldSpacer && newSpacer;
                    return previous.get(oldPosition - 1).packageName
                            .equals(current.get(newPosition - 1).packageName);
                }

                @Override public boolean areContentsTheSame(int oldPosition, int newPosition) {
                    if (oldPosition == 0 || newPosition == 0) return oldPosition == newPosition;
                    boolean oldSpacer = oldPosition == previous.size() + 1;
                    boolean newSpacer = newPosition == current.size() + 1;
                    if (oldSpacer || newSpacer) return oldSpacer && newSpacer;
                    AppEntry oldEntry = previous.get(oldPosition - 1);
                    AppEntry newEntry = current.get(newPosition - 1);
                    return oldEntry.label.equals(newEntry.label) && oldEntry.icon == newEntry.icon;
                }
            }).dispatchUpdatesTo(this);
        }

        private void sortApps() {
            java.text.Collator collator = java.text.Collator.getInstance();
            collator.setStrength(java.text.Collator.SECONDARY);

            apps.sort((a, b) -> {
                boolean aSelected = editorSelectedApps.contains(a.packageName);
                boolean bSelected = editorSelectedApps.contains(b.packageName);
                if (aSelected != bSelected) return aSelected ? -1 : 1;

                int byLabel = collator.compare(a.label, b.label);
                return byLabel != 0
                        ? byLabel
                        : a.packageName.compareTo(b.packageName);
            });
        }

        void setAppsVisible(boolean visible) {
            if (appsVisible == visible) return;
            appsVisible = visible;
            header.setPadding(0, 0, 0, dp(14));

            // The header always remains at position zero.
            int count = visibleApps.size() + 1;
            if (visible) notifyItemRangeInserted(1, count);
            else notifyItemRangeRemoved(1, count);
        }

        @Override public int getItemCount() {
            return 1 + (appsVisible ? visibleApps.size() + 1 : 0);
        }

        @Override public int getItemViewType(int position) {
            if (position == 0) return TYPE_HEADER;
            return position == visibleApps.size() + 1 ? TYPE_SPACER : TYPE_APP;
        }

        @Override public long getItemId(int position) {
            if (position == 0) return 0L;
            if (position == visibleApps.size() + 1) return Long.MAX_VALUE;
            return itemIds.get(visibleApps.get(position - 1).packageName);
        }

        @Override public AppPickerViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = viewType == TYPE_HEADER ? header
                    : viewType == TYPE_SPACER ? searchSpacer : buildAppRow();
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    viewType == TYPE_SPACER ? searchSpacerHeight : ViewGroup.LayoutParams.WRAP_CONTENT));
            return new AppPickerViewHolder(view, viewType == TYPE_APP);
        }

        @Override public void onBindViewHolder(AppPickerViewHolder viewHolder, int position) {
            if (viewHolder.appRow == null) return;

            AppEntry entry = visibleApps.get(position - 1);
            AppRowHolder holder = viewHolder.appRow;
            holder.icon.setImageDrawable(entry.icon);
            holder.label.setText(entry.label);
            holder.packageName.setText(entry.packageName);
            paintAppRow(viewHolder.itemView, holder, position - 1, visibleApps.size(),
                    editorSelectedApps.contains(entry.packageName));
            viewHolder.itemView.setOnClickListener(v -> {
                if (viewHolder.getBindingAdapterPosition() == RecyclerView.NO_POSITION) return;
                tap(v);
                updateSelection(entry, !editorSelectedApps.contains(entry.packageName));
            });
        }

        // A pick changes a row's look and, once it moves, which rows end the
        // group: repaint the rows without rebinding them.
        @Override public void onBindViewHolder(AppPickerViewHolder viewHolder, int position, List<Object> payloads) {
            if (!payloads.contains(PAINT) || viewHolder.appRow == null) {
                super.onBindViewHolder(viewHolder, position, payloads);
                return;
            }
            AppEntry entry = visibleApps.get(position - 1);
            paintAppRow(viewHolder.itemView, viewHolder.appRow, position - 1, visibleApps.size(),
                    editorSelectedApps.contains(entry.packageName));
        }

        private void updateSelection(AppEntry entry, boolean checked) {
            int oldPosition = findAppPosition(entry.packageName);
            if (oldPosition < 0) return;

            boolean changed = checked
                    ? editorSelectedApps.add(entry.packageName)
                    : editorSelectedApps.remove(entry.packageName);
            if (!changed) return;

            sortApps();
            rebuildVisibleApps();
            int newPosition = findAppPosition(entry.packageName);
            if (appsVisible && oldPosition != newPosition) {
                // Only this package moved; RecyclerView animates affected rows.
                // Offset by one because the header is also an adapter item.
                notifyItemMoved(oldPosition + 1, newPosition + 1);
            }
            if (appsVisible) notifyItemRangeChanged(1, visibleApps.size(), PAINT);
        }

        private int findAppPosition(String packageName) {
            // Bound rows may still reference entries from the previous cached list.
            for (int i = 0; i < visibleApps.size(); i++) {
                if (visibleApps.get(i).packageName.equals(packageName)) return i;
            }
            return -1;
        }

        @Override public void onViewRecycled(AppPickerViewHolder viewHolder) {
            if (viewHolder.appRow != null) viewHolder.itemView.setOnClickListener(null);
            super.onViewRecycled(viewHolder);
        }
    }

    private View buildUrlField(String initialValue) {
        FrameLayout field = new FrameLayout(this);
        field.setBackground(rounded(surface, border, 1, 10));
        urlInput = settingInput(editorValueLabel(), initialValue,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setTransformationMethod(urlVisible ? null : PasswordTransformationMethod.getInstance());
        urlInput.setPadding(dp(16), 0, dp(56), 0);
        field.addView(urlInput, new FrameLayout.LayoutParams(-1, -1));
        visibilityButton = iconButton(urlVisible ? R.drawable.ic_visibility_off : R.drawable.ic_visibility,
                urlVisible ? getString(R.string.link_hide) : getString(R.string.link_show));
        visibilityButton.setOnClickListener(v -> {
            tap(v);
            toggleUrlVisibility();
        });
        FrameLayout.LayoutParams eye = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END | Gravity.CENTER_VERTICAL);
        eye.rightMargin = dp(4);
        field.addView(visibilityButton, eye);
        return floating(field, urlInput);
    }

    private View buildEncryptionField(String initialValue) {
        FrameLayout field = new FrameLayout(this);
        field.setBackground(rounded(surface, border, 1, 10));
        encryptionInput = settingInput(getString(R.string.e2e_key_optional), initialValue,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        encryptionInput.setTransformationMethod(encryptionVisible ? null : PasswordTransformationMethod.getInstance());
        encryptionInput.setPadding(dp(16), 0, dp(56), 0);
        field.addView(encryptionInput, new FrameLayout.LayoutParams(-1, -1));
        encryptionVisibilityButton = iconButton(
                encryptionVisible ? R.drawable.ic_visibility_off : R.drawable.ic_visibility,
                encryptionVisible ? getString(R.string.key_hide) : getString(R.string.key_show));
        encryptionVisibilityButton.setOnClickListener(v -> {
            tap(v);
            toggleEncryptionVisibility();
        });
        FrameLayout.LayoutParams eye = new FrameLayout.LayoutParams(
                dp(48), dp(48), Gravity.END | Gravity.CENTER_VERTICAL);
        eye.rightMargin = dp(4);
        field.addView(encryptionVisibilityButton, eye);
        return floating(field, encryptionInput);
    }

    private LinearLayout cardRow(int iconRes, String titleValue, String detailValue) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(16), dp(14));
        row.setBackground(tile(surface, border, radii(20)));
        row.addView(iconBubble(iconRes), new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1f);
        copyParams.leftMargin = dp(14);
        copy.addView(text(titleValue, 15, text, true));
        copy.addView(text(detailValue, 12, secondary, false));
        row.addView(copy, copyParams);
        return row;
    }

    private View settingRow(int iconRes, String labelValue, EditText input) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(10), dp(10));
        row.setBackground(tile(surface, border, radii(20)));
        row.addView(iconBubble(iconRes), new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView title = text(labelValue, 14, text, false);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.leftMargin = dp(12);
        row.addView(title, titleParams);
        row.addView(input, new LinearLayout.LayoutParams(dp(120), dp(46)));
        return row;
    }

    private Switch settingSwitch(int iconRes, String titleValue, String detailValue, boolean checked) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setBackground(tile(surface, border, radii(20)));
        row.addView(iconBubble(iconRes), new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1f);
        copyParams.leftMargin = dp(12);
        // Keeps the description off the switch.
        copyParams.rightMargin = dp(16);
        copy.addView(text(titleValue, 14, text, false));
        copy.addView(text(detailValue, 11, secondary, false));
        row.addView(copy, copyParams);
        Switch toggle = new Switch(this);
        styleSwitch(toggle);
        toggle.setChecked(checked);
        toggle.setContentDescription(titleValue);
        row.addView(toggle, new LinearLayout.LayoutParams(-2, dp(42)));
        toggle.setTag(row);
        return toggle;
    }

    // Wraps an outlined field (box holding input) in a Google-style floating
    // label: the label rests inside the box like a hint while the field is
    // empty and slides up onto the top border once it has text or focus.
    // The input's hint becomes the label; setFloatingLabel changes it later.
    private View floating(View box, EditText input) {
        FrameLayout wrapper = new FrameLayout(this);
        // FLOAT_ROOM above the box holds the upper half of the floated label,
        // so it never pokes out under a neighbor.
        FrameLayout.LayoutParams boxParams = new FrameLayout.LayoutParams(-1, -1);
        boxParams.topMargin = dp(FLOAT_ROOM);
        wrapper.addView(box, boxParams);
        TextView label = text(String.valueOf(input.getHint()), 14, hint, false);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(dp(4), 0, dp(4), 0);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        wrapper.addView(label, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
        input.setContentDescription(input.getHint());
        input.setHint(null);
        input.setTag(label);
        // Top half over whatever is behind the field, bottom half over the
        // box: hides the border line behind the floated label.
        android.graphics.drawable.Drawable patch = new android.graphics.drawable.Drawable() {
            private final android.graphics.Paint paint = new android.graphics.Paint();
            @Override public void draw(android.graphics.Canvas canvas) {
                android.graphics.Rect b = getBounds();
                paint.setColor(colorBehind(wrapper));
                canvas.drawRect(b.left, b.top, b.right, b.exactCenterY(), paint);
                paint.setColor(surface);
                canvas.drawRect(b.left, b.exactCenterY(), b.right, b.bottom, paint);
            }
            @Override public void setAlpha(int alpha) { }
            @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
            @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
        };
        boolean[] floated = {false};
        boolean[] sliding = {false};
        Consumer<Boolean> place = animate -> {
            if (wrapper.getHeight() == 0) return;
            boolean up = input.hasFocus() || input.length() > 0;
            label.setTextColor(up && input.hasFocus() ? accent : up ? secondary : hint);
            // A layout pass (the keyboard opening) must not cut a slide short.
            if (sliding[0] && (!animate || up == floated[0])) return;
            // Text start of the input, relative to the wrapper.
            float x = input.getPaddingLeft() - dp(4);
            for (View v = input; v != wrapper && v != null; v = (View) v.getParent()) x += v.getLeft();
            label.setX(x);
            // Resting, the label must stop where the text does (before an eye
            // button); floated, it is scaled down and fits anyway.
            int textRight = (int) (x + dp(4) + input.getWidth() - input.getPaddingLeft() - input.getPaddingRight());
            label.setMaxWidth(Math.max(dp(40), textRight - (int) x + dp(4)));
            label.setPivotX(0);
            label.setPivotY(label.getHeight() / 2f);
            float room = dp(FLOAT_ROOM);
            float y = up ? room - label.getHeight() / 2f
                    : room + (wrapper.getHeight() - room - label.getHeight()) / 2f;
            float scale = up ? 0.8f : 1f;
            if (animate && up != floated[0]) {
                sliding[0] = true;
                // The patch only belongs on the border: drop it before sliding
                // down, add it once the label has landed up there.
                if (!up) label.setBackground(null);
                label.animate().y(y).scaleX(scale).scaleY(scale).setDuration(220)
                        .setInterpolator(EMPHASIZED)
                        .withEndAction(() -> {
                            sliding[0] = false;
                            label.setBackground(up ? patch : null);
                        }).start();
            } else {
                label.setY(y);
                label.setScaleX(scale);
                label.setScaleY(scale);
                label.setBackground(up ? patch : null);
            }
            floated[0] = up;
        };
        wrapper.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> v.post(() -> place.accept(false)));
        input.setOnFocusChangeListener((v, focus) -> place.accept(true));
        bindText(input, v -> place.accept(true));
        return wrapper;
    }

    // The fill of the nearest ancestor that paints one, else the page's.
    private int colorBehind(View view) {
        for (android.view.ViewParent p = view.getParent(); p instanceof View; p = p.getParent()) {
            android.graphics.drawable.Drawable d = ((View) p).getBackground();
            if (d instanceof android.graphics.drawable.ColorDrawable) {
                return ((android.graphics.drawable.ColorDrawable) d).getColor();
            }
            if (d instanceof android.graphics.drawable.GradientDrawable
                    && ((android.graphics.drawable.GradientDrawable) d).getColor() != null) {
                return ((android.graphics.drawable.GradientDrawable) d).getColor().getDefaultColor();
            }
        }
        return background;
    }

    private static final int FLOAT_ROOM = 8;
    // Height of a field wrapped by floating(): the 56dp box plus FLOAT_ROOM.
    private static final int FIELD_HEIGHT = 56 + FLOAT_ROOM;

    private static void setFloatingLabel(EditText input, String value) {
        if (input == null || !(input.getTag() instanceof TextView)) return;
        ((TextView) input.getTag()).setText(value);
        input.setContentDescription(value);
    }

    private EditText settingInput(String fieldHint, String value, int inputType) {
        EditText input = new EditText(this);
        input.setHint(fieldHint);
        input.setHintTextColor(hint);
        input.setText(value);
        input.setSingleLine(true);
        input.setTextSize(14);
        input.setTextColor(text);
        input.setInputType(inputType);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(8), 0, dp(8), 0);
        return input;
    }

    private void toggleUrlVisibility() {
        int position = urlInput.getSelectionStart();
        urlVisible = !urlVisible;
        urlInput.setTransformationMethod(urlVisible ? null : PasswordTransformationMethod.getInstance());
        urlInput.setTypeface(Typeface.DEFAULT);
        visibilityButton.setImageResource(urlVisible ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);
        visibilityButton.setContentDescription(urlVisible ? getString(R.string.link_hide) : getString(R.string.link_show));
        urlInput.setSelection(Math.max(0, Math.min(position, urlInput.length())));
    }

    private void toggleEncryptionVisibility() {
        int position = encryptionInput.getSelectionStart();
        encryptionVisible = !encryptionVisible;
        encryptionInput.setTransformationMethod(
                encryptionVisible ? null : PasswordTransformationMethod.getInstance());
        encryptionInput.setTypeface(Typeface.DEFAULT);
        encryptionVisibilityButton.setImageResource(
                encryptionVisible ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);
        encryptionVisibilityButton.setContentDescription(
                encryptionVisible ? getString(R.string.key_hide) : getString(R.string.key_show));
        encryptionInput.setSelection(Math.max(0, Math.min(position, encryptionInput.length())));
    }

    private void generateEncryptionSecret() {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String generated = Base64.encodeToString(
                random, Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE);
        if (encryptionInput != null) {
            encryptionInput.setText(generated);
            encryptionInput.setSelection(encryptionInput.length());
        }
        Toast.makeText(this, getString(R.string.key_generated), Toast.LENGTH_LONG).show();
    }

    // Called on every navigation (showPage), not just when leaving a settings
    // page with unsaved edits - so it must NOT commit the Network/Mode draft
    // fields (applyNetworkSettings/applyModeSettings do that, only when the
    // save button is pressed). The log buffer is kept separately from the
    // filtered view, so switching pages must not copy the visible text back.
    private void captureSettings() {
        urlInput = null;
        encryptionInput = null;
        profileNameInput = null;
        dnsInput = null;
        mtuInput = null;
        proxyPortInput = null;
        proxyUsernameInput = null;
        proxyPasswordInput = null;
        logList = null;
        logAdapter = null;
        logEmpty = null;
        logJump = null;
    }

    private void persistSettings() {
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
                .commit();
    }

    private boolean isProxyMode() {
        return MODE_PROXY.equals(connectionMode);
    }

    private boolean isExitMode() {
        return MODE_EXIT.equals(connectionMode);
    }

    private String modeLabel(String mode) {
        if (MODE_PROXY.equals(mode)) return getString(R.string.mode_proxy);
        if (MODE_EXIT.equals(mode)) return getString(R.string.mode_exit);
        return getString(R.string.mode_tunnel);
    }

    private boolean isConnectionRunning() {
        if (isExitMode()) return OpenFluxExitService.isRunning();
        return isProxyMode() ? OpenFluxProxyService.isRunning() : OpenFluxTunnelService.isRunning();
    }

    private String connectionStatus() {
        if (isExitMode()) return OpenFluxExitService.getStatus();
        return isProxyMode() ? OpenFluxProxyService.getStatus() : OpenFluxTunnelService.getStatus();
    }

    private String connectionLastError() {
        if (isExitMode()) return OpenFluxExitService.getLastError();
        return isProxyMode() ? OpenFluxProxyService.getLastError() : OpenFluxTunnelService.getLastError();
    }

    private long connectionStartedAt() {
        if (isExitMode()) return OpenFluxExitService.getConnectedAtMillis();
        return isProxyMode() ? OpenFluxProxyService.getConnectedAtMillis() : OpenFluxTunnelService.getConnectedAtMillis();
    }

    private void stopConnection(String mode) {
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

    private void toggleConnection() {
        if (isConnectionRunning()) {
            stopConnection(connectionMode);
            appendLog(getString(R.string.log_stop_requested, modeLabel(connectionMode)));
            return;
        }
        String valueProblem = transportValueProblem(transportType,
                "oneme".equals(transportType) ? (maxToken == null ? "" : maxToken) : documentUrl, isExitMode());
        if (valueProblem != null) {
            Toast.makeText(this, valueProblem, Toast.LENGTH_LONG).show();
            showPage(PAGE_PROFILES);
            return;
        }
        Profile selected = selectedProfile();
        String scriptProblem = selected == null ? null : selected.scriptProblem(this, new ScriptStore(this));
        if (scriptProblem != null) {
            Toast.makeText(this, scriptProblem, Toast.LENGTH_LONG).show();
            return;
        }
        if (encryptionSecret != null && !encryptionSecret.isEmpty() && encryptionSecret.length() < 16) {
            Toast.makeText(this, getString(R.string.profile_short_secret), Toast.LENGTH_LONG).show();
            showPage(PAGE_PROFILES);
            return;
        }
        if (isProxyMode() && proxyLanAccess && proxyAuthEnabled
                && (proxyUsername.isEmpty() || proxyPassword.isEmpty())) {
            Toast.makeText(this, getString(R.string.proxy_need_credentials), Toast.LENGTH_LONG).show();
            openSettingsDetail(SETTINGS_CONNECTION);
            return;
        }
        persistSettings();
        if (isExitMode()) {
            Intent intent = new Intent(this, OpenFluxExitService.class).setAction(OpenFluxExitService.ACTION_START);
            putProfileExtras(intent);
            startForegroundService(intent);
            appendLog(getString(R.string.exit_starting));
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
        if (scriptsPanel != null && scriptsPanel.onActivityResult(requestCode, resultCode, data)) return;
        if (requestCode == SCRIPT_SETTINGS_REQUEST) {
            onScriptSettings(resultCode == RESULT_OK && data != null
                    ? data.getStringExtra(CaptchaActivity.EXTRA_SUBMITTED) : null);
        }
        if (requestCode == TUNNEL_PERMISSION_REQUEST && resultCode == RESULT_OK) startTunnel();
        else if (requestCode == TUNNEL_PERMISSION_REQUEST) appendLog("[ERROR] " + getString(R.string.log_tunnel_permission_denied));
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
        appendLog(getString(R.string.log_starting_tunnel));
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
        appendLog(getString(R.string.log_starting_proxy));
    }

    private void updateStatus() {
        if (tunnelButtonText == null || tunnelButton == null) return;
        String state = connectionStatus();
        // Text sits on the button's own fill color, not the page background,
        // so it stays a fixed white for contrast rather than status-colored.
        tunnelButtonText.setText(Status.label(this, state));

        long connectedAt = connectionStartedAt();
        if (connectedAt == 0L) {
            // GONE, not just empty text: an empty-but-present line still
            // reserves its height, which pushes the icon+status above dead
            // center in the button while there's no time to show yet.
            uptimeView.setVisibility(View.GONE);
            speedView.setVisibility(View.GONE);
        } else {
            uptimeView.setVisibility(View.VISIBLE);
            uptimeView.setText(formatUptime(System.currentTimeMillis() - connectedAt));
            long sentPerSec = isExitMode() ? OpenFluxExitService.getSentPerSec()
                    : isProxyMode() ? OpenFluxProxyService.getSentPerSec() : OpenFluxTunnelService.getSentPerSec();
            long receivedPerSec = isExitMode() ? OpenFluxExitService.getReceivedPerSec()
                    : isProxyMode() ? OpenFluxProxyService.getReceivedPerSec() : OpenFluxTunnelService.getReceivedPerSec();
            speedView.setVisibility(View.VISIBLE);
            Profile p = selectedProfile();
            String carriers = Profile.carriersLabel(this, Mobile.currentTransports(),
                    p != null && p.stream ? p.transportType : "");
            speedView.setText("↑ " + OpenFluxTunnelService.formatSpeed(this, sentPerSec)
                    + "   ↓ " + OpenFluxTunnelService.formatSpeed(this, receivedPerSec)
                    + (carriers.isEmpty() ? "" : "\n" + carriers));
        }

        if (state != null && !state.equals(lastAnnouncedState)) {
            if (Status.CONNECTED.equals(state) && isExitMode() && Status.WAITING.equals(lastAnnouncedState)) {
                vibrateSuccess();
                appendLog("[SUCCESS] " + getString(R.string.log_exit_client_connected));
            } else if (Status.CONNECTED.equals(state)) {
                vibrateSuccess();
                appendLog("[SUCCESS] " + getString(R.string.log_connected, modeLabel(connectionMode)));
            } else if (Status.ERROR.equals(state)) {
                vibrateError();
            }
            lastAnnouncedState = state;
        }

        int tunnelFill;
        int tunnelPressed;
        boolean pulsing;
        // Idle matches the "Активные параметры" card background instead of a
        // fixed dark grey, so it also needs to pick readable content color -
        // that card's own background is near-white in light mode.
        boolean idle;
        if (Status.CONNECTED.equals(state)) {
            tunnelFill = Color.rgb(79, 124, 255);
            tunnelPressed = Color.rgb(59, 93, 191);
            pulsing = true;
            idle = false;
        } else if (Status.ERROR.equals(state)) {
            tunnelFill = Color.rgb(239, 68, 68);
            tunnelPressed = Color.rgb(185, 28, 28);
            pulsing = false;
            idle = false;
        } else if (Status.busy(state)) {
            tunnelFill = Color.rgb(251, 191, 36);
            tunnelPressed = Color.rgb(217, 119, 6);
            pulsing = true;
            idle = false;
        } else {
            tunnelFill = surface;
            tunnelPressed = border;
            pulsing = false;
            idle = true;
        }
        animateTunnelButtonFill(tunnelFill, tunnelPressed);
        setRingPulsing(pulsing);

        int contentColor = idle ? text : Color.WHITE;
        tunnelButtonText.setTextColor(contentColor);
        uptimeView.setTextColor(contentColor);
        speedView.setTextColor(contentColor);
        if (tunnelPowerIcon != null) {
            tunnelPowerIcon.setImageTintList(ColorStateList.valueOf(contentColor));
        }

        refreshExitShareCard();

        String error = connectionLastError();
        if (error != null && !error.isEmpty() && !error.equals(lastShownError)) {
            lastShownError = error;
            appendLog("[ERROR] " + error);
        }
    }

    private void setRingPulsing(boolean pulsing) {
        if (ringPulse == null || ringWave == null) return;
        if (pulsing) {
            if (!ringPulse.isRunning()) ringPulse.start();
        } else if (ringPulse.isRunning()) {
            ringPulse.cancel();
            ringWave.setScaleX(1f);
            ringWave.setScaleY(1f);
            ringWave.setAlpha(0f);
        }
    }

    private String formatUptime(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0
                ? String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds);
    }

    private void animateTunnelButtonFill(int fill, int pressed) {
        if (tunnelButton == null) return;
        if (ringWave != null) ringWave.setBackground(rounded(Color.TRANSPARENT, fill, 2, 70));
        if (lastTunnelButtonFill == fill) return;
        int from = lastTunnelButtonFill == -1 ? fill : lastTunnelButtonFill;
        lastTunnelButtonFill = fill;
        ValueAnimator animator = ValueAnimator.ofArgb(from, fill);
        animator.setDuration(260);
        animator.addUpdateListener(a -> tunnelButton.setBackground(circleBackground((int) a.getAnimatedValue(), pressed)));
        animator.start();
    }

    // Static decorative ring around the big connect button - a thin stroke
    // circle, no fill. A single large corner radius renders as a perfect
    // circle regardless of the view's own size (GradientDrawable clips
    // excess radius), so the same drawable works for both ring sizes.
    private GradientDrawable ringOutline() {
        return rounded(Color.TRANSPARENT, border, 1, 100);
    }

    private RippleDrawable circleBackground(int fill, int pressed) {
        return new RippleDrawable(ColorStateList.valueOf(pressed), rounded(fill, Color.TRANSPARENT, 0, 70),
                rounded(Color.WHITE, Color.TRANSPARENT, 0, 70));
    }

    // getLocalIpAddress finds this device's IPv4 address on whatever network
    // it's currently attached to (Wi-Fi, a hotspot it joined, Ethernet, ...)
    // by scanning network interfaces directly, so it works the same way
    // regardless of connection type and needs no extra permission.
    private String getLocalIpAddress() {
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

    private boolean isValidDocumentUrl(String value) {
        if (value == null || !value.startsWith("https://")) return false;
        try {
            android.net.Uri uri = android.net.Uri.parse(value);
            return uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void appendLog(String value) {
        LogBuffer.add(value);
        refreshLogs();
    }

    // Adds what came since the list was filled; the newest line stays in
    // view when the list showed it and auto-scroll is on.
    private void refreshLogs() {
        if (logAdapter == null) return;
        List<LogBuffer.Entry> fresh = LogBuffer.since(logShownSeq);
        if (fresh.isEmpty()) return;
        logShownSeq = fresh.get(fresh.size() - 1).seq + 1;
        boolean stick = autoScroll && !logList.canScrollVertically(1);
        logAdapter.append(fresh);
        if (stick && logAdapter.getItemCount() > 0) logList.scrollToPosition(logAdapter.getItemCount() - 1);
        updateLogChrome();
    }

    // Fills the list again: another filter or search, a cleared log.
    private void reloadLogs() {
        if (logAdapter == null) return;
        List<LogBuffer.Entry> all = LogBuffer.since(0);
        logShownSeq = all.isEmpty() ? LogBuffer.nextSeq() : all.get(all.size() - 1).seq + 1;
        logAdapter.reset(all);
        if (autoScroll && logAdapter.getItemCount() > 0) logList.scrollToPosition(logAdapter.getItemCount() - 1);
        updateLogChrome();
    }

    private void updateLogChrome() {
        boolean empty = logAdapter.getItemCount() == 0;
        logEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            logEmpty.setText(getString(!logQuery.isEmpty() ? R.string.logs_nothing_found
                    : logFilter == LOG_FILTER_ERRORS ? R.string.logs_no_errors
                    : logFilter == LOG_FILTER_IMPORTANT ? R.string.logs_no_important
                    : R.string.logs_empty));
        }
        // After the list has laid the new lines out.
        logList.post(this::updateLogJump);
    }

    private void updateLogJump() {
        if (logList == null || logJump == null) return;
        logJump.setVisibility(logAdapter.getItemCount() > 0 && logList.canScrollVertically(1)
                ? View.VISIBLE : View.GONE);
    }

    // The lines the screen shows (all of the log while it is closed).
    private List<LogBuffer.Entry> shownLogs() {
        return logAdapter != null ? logAdapter.shown : LogBuffer.since(0);
    }

    private void copyLogs() {
        List<LogBuffer.Entry> shown = shownLogs();
        if (shown.isEmpty()) {
            Toast.makeText(this, getString(R.string.logs_empty), Toast.LENGTH_SHORT).show();
            return;
        }
        // The clipboard goes through one binder call: the newest lines that fit.
        int from = shown.size();
        int size = 0;
        while (from > 0 && size + shown.get(from - 1).text.length() + 12 < 200_000) {
            from--;
            size += shown.get(from).text.length() + 12;
        }
        StringBuilder out = new StringBuilder();
        for (int i = from; i < shown.size(); i++) out.append(LogBuffer.line(shown.get(i))).append('\n');
        getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("ReFlux log", out));
        Toast.makeText(this, from == 0 ? getString(R.string.logs_copied)
                : getString(R.string.logs_copied_last, shown.size() - from), Toast.LENGTH_SHORT).show();
    }

    // Writes the shown lines to a file and hands it to another app (a chat,
    // mail, files) through FileProvider.
    private void shareLogs() {
        List<LogBuffer.Entry> shown = shownLogs();
        if (shown.isEmpty()) {
            Toast.makeText(this, getString(R.string.logs_empty), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            java.io.File dir = new java.io.File(getCacheDir(), "logs");
            java.io.File[] old = dir.listFiles();
            if (old != null) for (java.io.File file : old) file.delete();
            if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException(dir.getPath());
            java.util.Date now = new java.util.Date();
            java.io.File file = new java.io.File(dir, "reflux-log-"
                    + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now) + ".txt");
            try (java.io.Writer out = new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(file), StandardCharsets.UTF_8)) {
                out.write("ReFlux " + appVersion + " · Android " + Build.VERSION.RELEASE + "\n");
                out.write(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(now) + "\n\n");
                for (LogBuffer.Entry e : shown) out.write(LogBuffer.fullLine(e) + "\n");
            }
            Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, file.getName())
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.logs_share)));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.logs_share_failed, String.valueOf(e.getMessage())),
                    Toast.LENGTH_LONG).show();
        }
    }

    // The log screen's lines: the filter (a continuation line goes with its
    // head line) and the search pick them from LogBuffer.
    private final class LogAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        final List<LogBuffer.Entry> shown = new ArrayList<>();
        private boolean headShown = true;

        void reset(List<LogBuffer.Entry> entries) {
            shown.clear();
            headShown = true;
            for (LogBuffer.Entry e : entries) if (accept(e)) shown.add(e);
            notifyDataSetChanged();
        }

        void append(List<LogBuffer.Entry> entries) {
            int start = shown.size();
            for (LogBuffer.Entry e : entries) if (accept(e)) shown.add(e);
            if (shown.size() > start) notifyItemRangeInserted(start, shown.size() - start);
            int extra = shown.size() - LogBuffer.MAX;
            if (extra > 0) {
                shown.subList(0, extra).clear();
                notifyItemRangeRemoved(0, extra);
            }
        }

        private boolean accept(LogBuffer.Entry e) {
            if (!e.continuation) {
                headShown = logFilter == LOG_FILTER_ALL || (logFilter == LOG_FILTER_ERRORS
                        ? isErrorLogLine(e.tag, e.text) : isImportantLogLine(e.tag));
            }
            return headShown && (logQuery.isEmpty() || e.text.toLowerCase(Locale.ROOT).contains(logQuery));
        }

        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView line = new TextView(MainActivity.this);
            line.setTypeface(Typeface.MONOSPACE);
            line.setTextSize(12);
            line.setLineSpacing(dp(1), 1f);
            line.setPadding(dp(14), dp(3), dp(14), dp(3));
            line.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerView.ViewHolder(line) { };
        }

        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            LogBuffer.Entry e = shown.get(position);
            TextView line = (TextView) holder.itemView;
            line.setText(colorizeEntry(e));
            line.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                getSystemService(ClipboardManager.class).setPrimaryClip(
                        ClipData.newPlainText("ReFlux log", LogBuffer.line(e)));
                Toast.makeText(MainActivity.this, getString(R.string.logs_line_copied), Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        @Override public int getItemCount() {
            return shown.size();
        }
    }

    private boolean isErrorLogLine(String tag, String line) {
        if ("[ERROR]".equals(tag) || "[PANIC]".equals(tag)) return true;
        return ("[ANDROID]".equals(tag) || "[NODE]".equals(tag))
                && ANDROID_LOG_ERROR_PATTERN.matcher(line).find();
    }

    private boolean isImportantLogLine(String tag) {
        if (tag == null) return true;
        switch (tag) {
            case "[ANDROID]":
            case "[NODE]":
            case "[ERROR]":
            case "[PANIC]":
            case "[WARN]":
            case "[WARNING]":
            case "[SUCCESS]":
                return true;
            default:
                return false;
        }
    }

    private static final Pattern ANDROID_LOG_ERROR_PATTERN = Pattern.compile(
            "(?i)\\b(?:failed|error)\\b|ошибк|не удалось");

    private int logTagColor(String tag) {
        switch (tag) {
            case "[ERROR]":
            case "[PANIC]":
                return logErrorColor();
            case "[SUCCESS]":
                return logSuccessColor();
            case "[WARN]":
            case "[WARNING]":
                return logWarningColor();
            default:
                return accent;
        }
    }

    private int logErrorColor() {
        return darkMode ? Color.rgb(255, 145, 145) : Color.rgb(180, 38, 45);
    }

    private int logSuccessColor() {
        return darkMode ? Color.rgb(105, 211, 149) : Color.rgb(18, 122, 66);
    }

    private int logWarningColor() {
        return darkMode ? Color.rgb(255, 202, 97) : Color.rgb(151, 86, 0);
    }

    private int logMessageColor(String tag, String message) {
        switch (tag) {
            case "[ERROR]":
            case "[PANIC]":
                return logErrorColor();
            case "[SUCCESS]":
                return logSuccessColor();
            case "[WARN]":
            case "[WARNING]":
                return logWarningColor();
            case "[ANDROID]":
                return ANDROID_LOG_ERROR_PATTERN.matcher(message).find() ? logErrorColor() : logColor;
            case "[YDOCS]":
            case "[VOLGA]":
            case "[MAX]":
            case "[CUPS]":
            case "[M-DOCS]":
            case "[CRYPTO]":
            case "[SESSION]":
            case "[DEBUG]":
                return darkMode ? Color.rgb(165, 174, 193) : Color.rgb(91, 100, 117);
            default:
                return logColor;
        }
    }

    // One line in colors: the time, the tag by its kind, the message, and
    // the search's matches.
    private CharSequence colorizeEntry(LogBuffer.Entry e) {
        SpannableStringBuilder line = new SpannableStringBuilder();
        line.append(LogBuffer.time(e), new ForegroundColorSpan(secondary), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        line.append("  ");
        int start = line.length();
        line.append(e.text);
        if (e.tag != null) {
            int tagEnd = start + e.tag.length();
            line.setSpan(new ForegroundColorSpan(logTagColor(e.tag)), start, tagEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (tagEnd < line.length()) {
                line.setSpan(new ForegroundColorSpan(logMessageColor(e.tag, e.text.substring(e.tag.length()))),
                        tagEnd, line.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } else {
            line.setSpan(new ForegroundColorSpan(logColor), start, line.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        String lower = e.text.toLowerCase(Locale.ROOT);
        if (!logQuery.isEmpty() && lower.length() == e.text.length()) {
            for (int i = lower.indexOf(logQuery); i >= 0; i = lower.indexOf(logQuery, i + logQuery.length())) {
                line.setSpan(new android.text.style.BackgroundColorSpan(tonal(0.35f)),
                        start + i, start + i + logQuery.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return line;
    }

    private LinearLayout page() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        return page;
    }

    private TextView text(CharSequence value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView label(String value) {
        TextView view = text(value, 11, secondary, true);
        view.setLetterSpacing(0.08f);
        return view;
    }

    private ImageView icon(int resource, int color) {
        ImageView view = new ImageView(this);
        view.setImageResource(resource);
        view.setImageTintList(ColorStateList.valueOf(color));
        return view;
    }

    private ImageButton iconButton(int resource, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(resource);
        button.setImageTintList(ColorStateList.valueOf(secondary));
        button.setContentDescription(description);
        button.setPadding(dp(11), dp(11), dp(11), dp(11));
        button.setBackground(ripple(Color.TRANSPARENT, 24));
        return button;
    }

    private GradientDrawable rounded(int fill, int stroke, int strokeWidth, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), stroke);
        return drawable;
    }

    private RippleDrawable ripple(int fill, int radius) {
        // No ripple highlight color - it showed as a flat grey/tinted box
        // over the whole rounded touch target instead of a subtle effect.
        // Feedback on tap comes from tap()/bounce() (haptics + scale) instead.
        return new RippleDrawable(ColorStateList.valueOf(Color.TRANSPARENT),
                rounded(fill, Color.TRANSPARENT, 0, radius), rounded(Color.WHITE, Color.TRANSPARENT, 0, radius));
    }

    private RippleDrawable buttonBackground(int fill, int pressed) {
        return new RippleDrawable(ColorStateList.valueOf(pressed), rounded(fill, Color.TRANSPARENT, 0, 9),
                rounded(Color.WHITE, Color.TRANSPARENT, 0, 9));
    }

    // tap gives a light click haptic for a direct user interaction (button
    // press, toggle, list selection). Respects the system's haptic feedback
    // setting automatically and needs no permission.
    private void tap(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    // bounce gives a tap haptic plus a scale-down/scale-up feedback animation.
    // Repeated or rapid taps on the same view would otherwise stack multiple
    // overlapping ViewPropertyAnimator sequences (cancel() still runs a
    // pending withEndAction on API 23+), so any animation already running for
    // this exact view is fully cancelled and replaced before starting a new
    // one, and the scale is reset synchronously rather than relying on the
    // cancelled animation to leave it in a known state.
    private void bounce(View view) {
        tap(view);
        AnimatorSet running = bounceAnimators.remove(view);
        if (running != null) running.cancel();
        view.setScaleX(1f);
        view.setScaleY(1f);
        ObjectAnimator shrink = ObjectAnimator.ofPropertyValuesHolder(view,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.97f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.97f));
        shrink.setDuration(90);
        shrink.setInterpolator(new DecelerateInterpolator());
        ObjectAnimator grow = ObjectAnimator.ofPropertyValuesHolder(view,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f));
        // No overshoot: growing past 1 pushed views under their neighbors
        // and past the edges of clipping parents.
        grow.setDuration(220);
        grow.setInterpolator(EMPHASIZED);
        AnimatorSet set = new AnimatorSet();
        set.playSequentially(shrink, grow);
        set.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                bounceAnimators.remove(view);
            }
        });
        bounceAnimators.put(view, set);
        set.start();
    }

    // vibrateSuccess/vibrateError are for state changes that aren't a direct
    // touch response (e.g. the tunnel finishing connecting a second later),
    // so they go through the Vibrator instead of View.performHapticFeedback.
    private void vibrateSuccess() {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= 29) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    private void vibrateError() {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 45, 60, 45}, -1));
    }

    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, -1, 1f); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
