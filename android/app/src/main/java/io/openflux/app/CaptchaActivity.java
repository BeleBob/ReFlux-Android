package io.openflux.app;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.Insets;
import android.os.Build;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.File;
import java.util.Collections;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.openflux.bridge.mobile.Mobile;

// Lets the user pass a Yandex SmartCaptcha (or log in) when the transport
// core can't get through on its own, then hands the resulting cookies back
// to it. Android's side of the core's out-of-band cookie flow - desktop and
// iOS do the same over transport/ipc.
//
// A JS transport's own page (its inline html, or its own server on
// 127.0.0.1, PendingCaptchaOwn) is shown as it is and hands its data over
// through window.openfluxSubmit instead of cookies. With EXTRA_SETTINGS_HTML
// the same page host shows a script's settings page for the profile editor
// and returns what it submitted (EXTRA_SUBMITTED) instead of calling the core.
public class CaptchaActivity extends Activity {
    // The settings-page host: the same page code, its own manifest entry.
    public static final class Settings extends CaptchaActivity { }

    static final String EXTRA_SETTINGS_HTML = "io.openflux.app.SETTINGS_HTML";
    static final String EXTRA_TITLE = "io.openflux.app.SETTINGS_TITLE";
    static final String EXTRA_SUBMITTED = "io.openflux.app.SUBMITTED";
    // A mail.ru sign-in the person starts from the settings (MailruLogin),
    // whether or not the core waits for one.
    static final String EXTRA_MAILRU_SIGN_IN = "io.openflux.app.MAILRU_SIGN_IN";
    // window.openfluxSubmit for a script's page; fires "openflux-ready" once
    // defined. Idempotent: it goes in before the page's scripts where it can
    // and again when the page has loaded.
    private static final String SUBMIT_BRIDGE = "OpenFluxSubmit";
    private static final String BRIDGE_JS = "(function(){if(window.openfluxSubmit)return;window.openfluxSubmit=function(p){try{var j=JSON.stringify(p);window." + SUBMIT_BRIDGE + ".submit(j)}catch(e){}};setTimeout(function(){try{window.dispatchEvent(new Event('openflux-ready'))}catch(e){}},0)})();";
    private static final Pattern LOOPBACK = Pattern.compile(
            "(?i)http://(127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|localhost|\\[::1\\]):(\\d{1,5})(?:[/?#].*)?");

    // Same UA the Go transport uses for the document fetch: the captcha pass
    // may be bound to it, so solving under a different one could be useless.
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:153.0) Gecko/20100101 Firefox/153.0";
    private static final String CHANNEL_ID = "openflux_captcha";
    private static final int NOTIFICATION_ID = 9;

    private static volatile boolean solved;

    private String startUrl;
    // Set when the check belongs to the exit node: the page must load through
    // this proxy (the tunnel) so it is passed from the node's address.
    private String proxy = "";
    private boolean proxyOverridden;
    private volatile String currentUrl;
    private boolean submitted;
    // A script's own page: its inline html, or the origin of its own server.
    private String ownHtml;
    private String ownOrigin;
    private boolean settingsMode;
    // A mail.ru sign-in (MailruLogin): done once the cloud's session cookies
    // are there, not on the first page that is not a check.
    private boolean mailru;
    private boolean signInMode;
    // The page and, over it, the windows it opens (VK ID's).
    private FrameLayout pages;
    // The app's look (Theme): the window and the pages follow it.
    private Theme.Colors colors;

    // Whether the core waits on a page: a real site's (URL) or a script's
    // inline one (HTML only, no URL).
    static boolean pending() {
        return !Mobile.pendingCaptchaURL().isEmpty() || !Mobile.pendingCaptchaHTML().isEmpty();
    }

    static void initCookieStore(Context context) {
        Mobile.setCookieStorePath(new File(context.getFilesDir(), "transport-cookies.json").getPath());
    }

    // Blocks the calling worker while the core waits for a solve, surfacing
    // it as a notification. Returns true once the user submitted cookies,
    // false if nothing was pending, the user cancelled, or the session ended.
    static boolean awaitIfPending(Context context, BooleanSupplier stillCurrent) {
        if (!pending()) return false;
        solved = false;
        // Signed in to mail.ru already: no page needed.
        if (MailruLogin.submitIfSignedIn()) return stillCurrent.getAsBoolean();
        boolean mailru = MailruLogin.pending();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, context.getString(R.string.captcha_channel), NotificationManager.IMPORTANCE_HIGH));
        Intent open = new Intent(context, CaptchaActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent content = PendingIntent.getActivity(
                context, 1, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        boolean login = "login".equals(Mobile.pendingCaptchaReason());
        boolean remote = !Mobile.pendingCaptchaProxy().isEmpty();
        boolean own = Mobile.pendingCaptchaOwn() || !Mobile.pendingCaptchaHTML().isEmpty();
        String title = mailru ? context.getString(R.string.captcha_title_mailru) : own ? context.getString(R.string.captcha_title_own) : remote
                ? (login ? context.getString(R.string.captcha_title_node_login) : context.getString(R.string.captcha_title_node_check))
                : (login ? context.getString(R.string.captcha_title_login) : context.getString(R.string.captcha_title_check));
        manager.notify(NOTIFICATION_ID, new Notification.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(mailru ? context.getString(R.string.captcha_text_mailru)
                        : remote ? context.getString(R.string.captcha_text_node)
                        : context.getString(R.string.captcha_text_own))
                .setSmallIcon(R.drawable.ic_openflux_notification)
                .setAutoCancel(true)
                .setContentIntent(content)
                .build());
        try {
            while (stillCurrent.getAsBoolean() && pending()) {
                Thread.sleep(500);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            manager.cancel(NOTIFICATION_ID);
        }
        return solved && stillCurrent.getAsBoolean();
    }

    // The app's language on Android before 13 (see Lang).
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        // The app's own choice of light or dark, not the system's: the
        // pages see it too (prefers-color-scheme follows the window's theme).
        colors = Theme.colors(this);
        setTheme(colors.dark ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(savedInstanceState);
        String reason = Mobile.pendingCaptchaReason();
        settingsMode = getIntent().hasExtra(EXTRA_SETTINGS_HTML);
        signInMode = getIntent().getBooleanExtra(EXTRA_MAILRU_SIGN_IN, false);
        if (signInMode) {
            startUrl = MailruLogin.SIGN_IN_URL;
            reason = "login";
        } else if (settingsMode) {
            ownHtml = getIntent().getStringExtra(EXTRA_SETTINGS_HTML);
            startUrl = "";
            reason = getIntent().getStringExtra(EXTRA_TITLE);
        } else {
            startUrl = Mobile.pendingCaptchaURL();
            String html = Mobile.pendingCaptchaHTML();
            if (!html.isEmpty()) ownHtml = html;
            else if (Mobile.pendingCaptchaOwn()) ownOrigin = loopbackOrigin(startUrl);
            if (startUrl.isEmpty() && ownHtml == null) {
                finish();
                return;
            }
        }
        boolean own = ownHtml != null || ownOrigin != null;
        boolean login = "login".equals(reason);
        mailru = signInMode || (!settingsMode && MailruLogin.pending());
        // A script's page never goes through the exit's proxy: it is inline
        // or loopback; nor does the person's own mail.ru sign-in.
        proxy = own || signInMode ? "" : Mobile.pendingCaptchaProxy();
        boolean remote = !proxy.isEmpty();

        TextView title = new TextView(this);
        title.setText(mailru ? getString(R.string.captcha_mailru) : own
                ? (reason == null || reason.isEmpty() ? getString(R.string.captcha_setup_transport) : reason)
                : remote
                ? (login ? getString(R.string.captcha_node_login) : getString(R.string.captcha_node_check))
                : (login ? getString(R.string.captcha_login) : getString(R.string.captcha_check)));
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(colors.text);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Button done = barButton(getString(R.string.done), true, this::submit);
        Button cancel = barButton(getString(R.string.cancel), false, this::cancel);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(barBackground());
        bar.setPadding(dp(16), dp(10), dp(12), dp(11));
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        gap.leftMargin = dp(8);
        bar.addView(cancel, gap);
        // A script's page submits itself.
        if (!own) bar.addView(done, gap);

        WebView web = new WebView(this);
        pages = new FrameLayout(this);
        pages.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString(USER_AGENT);
        followTheme(web);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true);
        if (mailru) mailruPage(web);
        if (own) {
            web.addJavascriptInterface(new Submit(), SUBMIT_BRIDGE);
            if (ownOrigin != null && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(web, BRIDGE_JS,
                        Collections.singleton(ownOrigin.substring(0, ownOrigin.length() - 1)));
            }
        }
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return mailru && (openOutside(view, request.getUrl().toString()) || darkVkId(view, request));
            }

            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                currentUrl = url;
            }

            @Override public void onPageFinished(WebView view, String url) {
                currentUrl = url;
                if (own) {
                    if (bridgeAllowedAt(url)) view.evaluateJavascript(BRIDGE_JS, null);
                    return;
                }
                // mail.ru: the login pages are not the end; signed in is.
                if (mailru) {
                    submitWhenSignedIn(view);
                    return;
                }
                // A real browser is often let through without any check (the
                // captcha targets the transport's bot-like client), so any
                // regular page counts as passed. The short delay lets a page
                // that bounces to a check get there before we submit.
                if (!isCheckpoint(url)) {
                    view.postDelayed(() -> {
                        if (!isCheckpoint(currentUrl)) submit();
                    }, 1500);
                }
            }
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colors.surface);
        pages.setBackgroundColor(colors.background);
        web.setBackgroundColor(colors.background);
        if (Build.VERSION.SDK_INT >= 30 && getWindow().getInsetsController() != null) {
            int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            getWindow().getInsetsController().setSystemBarsAppearance(colors.dark ? 0 : light, light);
        }
        root.addView(bar);
        root.addView(pages, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        // Keep the bar and the page clear of the status/navigation bars and
        // the keyboard (edge-to-edge is enforced from Android 15).
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return WindowInsets.CONSUMED;
            });
        } else {
            root.setFitsSystemWindows(true);
        }
        setContentView(root);
        if (ownHtml != null) {
            web.loadDataWithBaseURL(null, inject(ownHtml, BRIDGE_JS), "text/html", "utf-8", null);
            return;
        }
        if (!remote) {
            web.loadUrl(startUrl);
            return;
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            Toast.makeText(this, getString(R.string.captcha_webview_proxy),
                    Toast.LENGTH_LONG).show();
            cancel();
            return;
        }
        ProxyConfig config = new ProxyConfig.Builder().addProxyRule(proxy).build();
        proxyOverridden = true;
        ProxyController.getInstance().setProxyOverride(config, Runnable::run, () -> web.loadUrl(startUrl));
    }

    @Override protected void onDestroy() {
        // The override applies to every WebView in the process; drop it.
        if (proxyOverridden) ProxyController.getInstance().clearProxyOverride(Runnable::run, () -> { });
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        // A window the page opened closes first.
        if (pages != null && pages.getChildCount() > 1) {
            closePopup((WebView) pages.getChildAt(pages.getChildCount() - 1));
            return;
        }
        cancel();
    }

    // The mail.ru sign-in is VK ID's (account.mail.ru sends a phone to
    // id.vk.ru): the phone's own user agent gets its phone page, not the
    // desktop one with a QR code to scan from this very phone; it opens
    // windows (VK ID's) over the page, and hands app links (confirming in
    // the VK app) to the apps. The VK sign-in stays in this window's
    // cookies, so the next mail.ru sign-in is one tap, «Continue as …».
    private void mailruPage(WebView view) {
        WebSettings s = view.getSettings();
        s.setUserAgentString(WebSettings.getDefaultUserAgent(this));
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);
        view.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onCreateWindow(WebView parent, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                WebView popup = new WebView(CaptchaActivity.this);
                WebSettings ps = popup.getSettings();
                ps.setJavaScriptEnabled(true);
                ps.setDomStorageEnabled(true);
                followTheme(popup);
                popup.setBackgroundColor(colors.background);
                CookieManager.getInstance().setAcceptThirdPartyCookies(popup, true);
                mailruPage(popup);
                popup.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        return openOutside(v, request.getUrl().toString()) || darkVkId(v, request);
                    }

                    @Override public void onPageFinished(WebView v, String url) {
                        submitWhenSignedIn(v);
                    }
                });
                pages.addView(popup, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                ((WebView.WebViewTransport) resultMsg.obj).setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }

            @Override public void onCloseWindow(WebView window) {
                closePopup(window);
            }
        });
    }

    private void closePopup(WebView popup) {
        if (pages == null || pages.indexOfChild(popup) <= 0) return;
        pages.removeView(popup);
        popup.destroy();
    }

    // Pages follow the app's look: prefers-color-scheme is the window's
    // theme; a page with no dark style of its own is darkened in dark.
    @SuppressWarnings("deprecation")
    private void followTheme(WebView view) {
        WebSettings s = view.getSettings();
        if (Build.VERSION.SDK_INT >= 33) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, true);
            }
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(s, colors.dark ? WebSettingsCompat.FORCE_DARK_ON : WebSettingsCompat.FORCE_DARK_OFF);
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                WebSettingsCompat.setForceDarkStrategy(s, WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING);
            }
        }
    }

    // mail.ru always asks VK ID for its light page (scheme=light); in the
    // app's dark look the window asks for the dark one. Only a GET can be
    // asked again, and VK ID's sign-in page is one.
    private boolean darkVkId(WebView view, WebResourceRequest request) {
        Uri url = request.getUrl();
        String host = url.getHost() == null ? "" : url.getHost();
        if (!colors.dark || !"GET".equals(request.getMethod())
                || !(host.equals("id.vk.ru") || host.equals("id.vk.com"))
                || !"light".equals(url.getQueryParameter("scheme"))) {
            return false;
        }
        view.loadUrl(url.toString().replace("scheme=light", "scheme=dark"));
        return true;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // The bar over the page: the app's card color, a hairline under it.
    private android.graphics.drawable.Drawable barBackground() {
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(colors.surface);
        GradientDrawable line = new GradientDrawable();
        line.setColor(colors.border);
        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{line, fill});
        layers.setLayerInset(1, 0, 0, 0, Math.max(1, dp(1) / 2));
        return layers;
    }

    // A button as the app draws them: the accent for the main one, an
    // outline for the other; a ripple, a click and a short press-in under
    // the finger that springs back.
    private Button barButton(String label, boolean primary, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(18), 0, dp(18), 0);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(12));
        int ripple;
        if (primary) {
            shape.setColor(colors.accent);
            b.setTextColor(Color.WHITE);
            ripple = Color.argb(70, 255, 255, 255);
        } else {
            shape.setColor(colors.surface);
            shape.setStroke(dp(1), colors.border);
            b.setTextColor(colors.text);
            ripple = Color.argb(40, Color.red(colors.accent), Color.green(colors.accent), Color.blue(colors.accent));
        }
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(dp(12));
        mask.setColor(Color.WHITE);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), shape, mask));
        b.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90)
                            .setInterpolator(new DecelerateInterpolator()).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(260)
                            .setInterpolator(new OvershootInterpolator(2.2f)).start();
                    break;
            }
            return false;
        });
        b.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            action.run();
        });
        return b;
    }

    // Hands the sign-in over once the cloud's session cookies are there.
    private void submitWhenSignedIn(WebView view) {
        view.postDelayed(() -> {
            if (!MailruLogin.cookies().isEmpty()) submit();
        }, 800);
    }

    // A link to an app (intent:, vk: and the like) opens the app, or its
    // web fallback; WebView would only show an error page for it. Web
    // links stay in the window.
    private boolean openOutside(WebView view, String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https:") || lower.startsWith("http:") || lower.startsWith("about:")
                || lower.startsWith("data:") || lower.startsWith("blob:") || lower.startsWith("javascript:")) {
            return false;
        }
        Intent intent;
        try {
            intent = lower.startsWith("intent:") ? Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    : new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        } catch (java.net.URISyntaxException bad) {
            return true;
        }
        // The page names what to open, never which of our components.
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setComponent(null);
        intent.setSelector(null);
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException none) {
            String fallback = intent.getStringExtra("browser_fallback_url");
            if (fallback != null && fallback.startsWith("https://")) view.loadUrl(fallback);
        }
        return true;
    }

    private static boolean isCheckpoint(String url) {
        return url != null && (url.contains("showcaptcha") || url.contains("passport.yandex"));
    }

    private void submit() {
        if (submitted) return;
        if (mailru) {
            submitMailru();
            return;
        }
        CookieManager manager = CookieManager.getInstance();
        manager.flush();
        StringBuilder header = new StringBuilder();
        for (String url : new String[] {startUrl, currentUrl}) {
            String part = url == null ? null : manager.getCookie(url);
            if (part == null || part.isEmpty()) continue;
            if (header.length() > 0) header.append("; ");
            header.append(part);
        }
        String error = Mobile.submitCaptchaCookies(header.toString());
        if (error != null && !error.isEmpty()) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            return;
        }
        submitted = true;
        solved = true;
        finish();
    }

    // The cloud's session cookies, once signed in, go to the core when it
    // waits for a mail.ru sign-in; from the settings, they stay in the
    // browser for when it asks (MailruLogin.submitIfSignedIn).
    private void submitMailru() {
        CookieManager.getInstance().flush();
        String header = MailruLogin.cookies();
        if (header.isEmpty()) {
            Toast.makeText(this, getString(R.string.mailru_login_first), Toast.LENGTH_LONG).show();
            return;
        }
        if (MailruLogin.pending()) {
            String error = Mobile.submitCaptchaCookies(header);
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                return;
            }
            solved = true;
        }
        submitted = true;
        if (signInMode) {
            String account = MailruLogin.account();
            Toast.makeText(this, account.isEmpty() ? getString(R.string.mailru_signed_in_plain)
                    : getString(R.string.mailru_signed_in, account), Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
        }
        finish();
    }

    private void cancel() {
        if (!submitted && !settingsMode && !signInMode) Mobile.cancelCaptcha();
        finish();
    }

    // What a script's page handed to window.openfluxSubmit.
    private void submitData(String json) {
        if (submitted) return;
        if (settingsMode) {
            submitted = true;
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_SUBMITTED, json));
            finish();
            return;
        }
        String error = Mobile.submitCaptchaData(json);
        if (error != null && !error.isEmpty()) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            return;
        }
        submitted = true;
        solved = true;
        finish();
    }

    // The submit channel belongs to the page's own address: once the WebView
    // has followed a link anywhere else, what it sends is dropped.
    private boolean bridgeAllowedAt(String at) {
        if (ownHtml != null) return at == null || at.isEmpty() || at.startsWith("about:") || at.startsWith("data:");
        return ownOrigin != null && at != null && at.toLowerCase(Locale.ROOT).startsWith(ownOrigin);
    }

    private final class Submit {
        @JavascriptInterface public void submit(String json) {
            // Called on a WebView thread; currentUrl is volatile.
            if (!bridgeAllowedAt(currentUrl)) return;
            runOnUiThread(() -> submitData(json));
        }
    }

    // "http://host:port/" of a page on the script's own loopback server, the
    // prefix its address must keep for the bridge; null for anything else.
    static String loopbackOrigin(String url) {
        Matcher m = LOOPBACK.matcher(url == null ? "" : url.trim());
        if (!m.matches()) return null;
        int port = Integer.parseInt(m.group(2));
        if (port < 1 || port > 65535) return null;
        return "http://" + m.group(1).toLowerCase(Locale.ROOT) + ":" + port + "/";
    }

    // Puts a script into a page where it runs before the page's own and the
    // page stays in standards mode: after <head>, else <html>, else the
    // doctype, else in front (the core's devhost.InjectSnippet does the same).
    static String inject(String page, String snippet) {
        String tag = "<script>" + snippet + "</script>";
        // A-Z only, so every index into lower is an index into page.
        char[] chars = page.toCharArray();
        for (int i = 0; i < chars.length; i++) if (chars[i] >= 'A' && chars[i] <= 'Z') chars[i] += 32;
        String lower = new String(chars);
        for (String open : new String[]{"<head", "<html"}) {
            int i = lower.indexOf(open);
            if (i < 0) continue;
            int end = i + open.length();
            // The tag must really be the tag (<header is not <head).
            if (end < lower.length() && " \t\n\r>/".indexOf(lower.charAt(end)) >= 0) {
                int close = lower.indexOf('>', end);
                if (close >= 0) return page.substring(0, close + 1) + tag + page.substring(close + 1);
            }
        }
        if (lower.trim().startsWith("<!doctype")) {
            int close = lower.indexOf('>');
            if (close >= 0) return page.substring(0, close + 1) + tag + page.substring(close + 1);
        }
        return tag + page;
    }
}
