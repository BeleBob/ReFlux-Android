package io.openflux.app;

// What the home screen says about mail.ru for the selected profile. The
// sign-in is no longer a loud notification (CaptchaActivity asks quietly,
// and not at all while other carriers connect): the home screen tells.
final class MailruNotice {
    private MailruNotice() { }

    static final int NONE = 0;
    // Not signed in, and other carriers connect the profile without mail.ru.
    static final int OTHERS_CARRY = 1;
    // Not signed in, and only mail.ru connects the profile.
    static final int ONLY_MAILRU = 2;

    static int of(Profile p) {
        if (p == null || !p.usesMailru() || MailruLogin.signedIn()) return NONE;
        return p.canLeaveMailru() ? OTHERS_CARRY : ONLY_MAILRU;
    }
}
