package io.openflux.app;

import android.content.SharedPreferences;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Where the person's own direct sites are kept (RoutingSettings). The
 * domain presets that used to live here gave way to the routing modes.
 */
final class DomainFilter {
    static final String PREFS_NAME = "openflux_domain_filter";
    static final String KEY_CUSTOM_DOMAINS = "custom_domains";

    private DomainFilter() {
    }

    static Set<String> loadCustomDomains(SharedPreferences prefs) {
        return new LinkedHashSet<>(prefs.getStringSet(KEY_CUSTOM_DOMAINS, Collections.emptySet()));
    }
}
