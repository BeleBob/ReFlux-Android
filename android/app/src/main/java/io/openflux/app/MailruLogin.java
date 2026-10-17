package io.openflux.app;

import android.content.Context;
import android.webkit.CookieManager;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import io.openflux.bridge.mobile.Mobile;

// The person's mail.ru account. Since 15 October 2026 mail.ru opens a
// document for a signed-in account only, so a mail.ru channel needs one;
// each person uses their own. The sign-in lives in the WebView's cookies on
// this phone: CaptchaActivity signs in when the core asks (or from the
// settings), and once signed in, every later request of the core gets the
// cookies at once, without a page.
final class MailruLogin {
    private MailruLogin() { }

    private static final String CLOUD = "https://cloud.mail.ru/";
    // The core's own login page (transport/mailru loginPageURL) starts so.
    private static final String LOGIN_PAGE = "https://account.mail.ru/login";

    // Where a sign-in from the settings starts: mail.ru's login, then the cloud.
    static final String SIGN_IN_URL = LOGIN_PAGE + "?page="
            + URLEncoder.encode("https://cloud.mail.ru/home/", StandardCharsets.UTF_8);

    // The cookies the browser sends to cloud.mail.ru while signed in, or ""
    // without a sign-in: mail.ru's API needs Mpop (the login) and sdcs (the
    // cloud's session).
    static String cookies() {
        String header = CookieManager.getInstance().getCookie(CLOUD);
        if (header == null || !has(header, "Mpop") || !has(header, "sdcs")) return "";
        return header;
    }

    static boolean signedIn() {
        return !cookies().isEmpty();
    }

    // The address the sign-in names (the Mpop cookie: "time:hash:email:"), or "".
    static String account() {
        for (String part : cookies().split(";")) {
            String p = part.trim();
            if (!p.startsWith("Mpop=")) continue;
            String[] f = p.substring(5).split(":");
            if (f.length > 2 && f[2].contains("@")) return f[2];
        }
        return "";
    }

    private static boolean has(String header, String name) {
        for (String part : header.split(";")) {
            String p = part.trim();
            if (p.startsWith(name + "=") && p.length() > name.length() + 1) return true;
        }
        return false;
    }

    // Whether the core waits for a mail.ru sign-in on this phone.
    static boolean pending() {
        return "login".equals(Mobile.pendingCaptchaReason())
                && Mobile.pendingCaptchaURL().startsWith(LOGIN_PAGE)
                && Mobile.pendingCaptchaProxy().isEmpty();
    }

    private static volatile String lastAuto = "";

    // Hands the sign-in to a core that asks for one without showing a page,
    // when the person is already signed in. Once per set of cookies: if
    // mail.ru refuses them, the page shows, and loading it renews the
    // cloud's session.
    static boolean submitIfSignedIn() {
        if (!pending()) return false;
        String header = cookies();
        if (header.isEmpty() || header.equals(lastAuto)) return false;
        lastAuto = header;
        return Mobile.submitCaptchaCookies(header).isEmpty();
    }

    // Signs out on this phone: forgets the browser's mail.ru cookies and the
    // core's saved copies. A running channel keeps its sign-in until it
    // stops; the next start asks again. The session itself stays valid at
    // mail.ru, as when a browser's cookies are cleared.
    static void signOut(Context context, boolean channelRunning) {
        CookieManager manager = CookieManager.getInstance();
        String[] hosts = {CLOUD, "https://account.mail.ru/", "https://auth.mail.ru/", "https://mail.ru/"};
        for (String url : hosts) {
            String header = manager.getCookie(url);
            if (header == null) continue;
            for (String part : header.split(";")) {
                String name = part.trim();
                int eq = name.indexOf('=');
                if (eq <= 0) continue;
                name = name.substring(0, eq);
                manager.setCookie(url, name + "=; Max-Age=0; Path=/");
                manager.setCookie(url, name + "=; Max-Age=0; Path=/; Domain=.mail.ru");
            }
        }
        manager.flush();
        lastAuto = "";
        // The store a running channel uses; with none running, the file
        // (reopening it under a running channel would let its next save
        // write the old copies back).
        Mobile.forgetCookies("mailru");
        if (!channelRunning) {
            CaptchaActivity.initCookieStore(context);
            Mobile.forgetCookies("mailru");
        }
    }
}
