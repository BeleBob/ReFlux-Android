package io.openflux.app;

// ReFlux's links start with reflux://, the core's with openflux://; the
// rest is the same. The ReFlux server gives reflux:// links, the core reads
// openflux:// ones, and links the app makes are shown as reflux://.
final class Links {
    static final String CORE = "openflux://";
    static final String REFLUX = "reflux://";

    private Links() {
    }

    // For the core: a reflux:// link as openflux://, any other as it is.
    static String toCore(String link) {
        link = link.trim();
        return link.startsWith(REFLUX) ? CORE + link.substring(REFLUX.length()) : link;
    }

    // For people: the core's openflux:// link as reflux://.
    static String toShown(String link) {
        return link.startsWith(CORE) ? REFLUX + link.substring(CORE.length()) : link;
    }
}
