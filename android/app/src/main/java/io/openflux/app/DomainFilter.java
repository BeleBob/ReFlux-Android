package io.openflux.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Domain/service split-routing presets: which domains bypass the tunnel
 * entirely (dialed directly from the device) instead of going through it.
 * Mirrors AppFilter's shape - constants + persistence here, actual routing
 * decisions applied by the connection services / mobile bridge.
 *
 * Preset domain lists live in assets/domain_presets/ - see SOURCE.md there
 * for where they came from.
 */
final class DomainFilter {
    static final String PREFS_NAME = "openflux_domain_filter";
    static final String KEY_ENABLED_PRESETS = "enabled_presets";
    static final String KEY_CUSTOM_DOMAINS = "custom_domains";

    static final class Preset {
        final String id;
        // String resources: the screen shows them in the app's language.
        final int title;
        final int description;
        final String assetFile;

        Preset(String id, int title, int description, String assetFile) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.assetFile = assetFile;
        }
    }

    static final Preset[] PRESETS = {
            new Preset("ru", R.string.preset_ru_title, R.string.preset_ru_desc, "ru.txt"),
            new Preset("youtube", R.string.preset_youtube_title, R.string.preset_youtube_desc, "youtube.txt"),
            new Preset("discord", R.string.preset_discord_title, R.string.preset_discord_desc, "discord.txt"),
            new Preset("ai", R.string.preset_ai_title, R.string.preset_ai_desc, "ai.txt"),
    };

    private DomainFilter() {
    }

    static Preset findPreset(String id) {
        for (Preset preset : PRESETS) {
            if (preset.id.equals(id)) return preset;
        }
        return null;
    }

    /**
     * Loads and merges the domain lists of every enabled preset plus the
     * user's custom domains, normalized to lowercase. Called from the
     * settings draft (enabledPresetIds may be a live or in-progress edit),
     * not tied to SharedPreferences directly, so it works for both.
     */
    static Set<String> resolveDomains(Context context, Set<String> enabledPresetIds, Set<String> customDomains) {
        Set<String> domains = new LinkedHashSet<>();
        for (String id : enabledPresetIds) {
            Preset preset = findPreset(id);
            if (preset == null) continue;
            domains.addAll(loadAssetLines(context, "domain_presets/" + preset.assetFile));
        }
        for (String custom : customDomains) {
            String trimmed = custom.trim().toLowerCase(java.util.Locale.ROOT);
            if (!trimmed.isEmpty()) domains.add(trimmed);
        }
        return domains;
    }

    private static Set<String> loadAssetLines(Context context, String path) {
        Set<String> lines = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim().toLowerCase(java.util.Locale.ROOT);
                if (!trimmed.isEmpty()) lines.add(trimmed);
            }
        } catch (IOException ignored) {
            // Missing/unreadable preset file: just contributes no domains.
        }
        return lines;
    }

    /** True if host equals domain, or is a subdomain of it. */
    static boolean matches(String host, Set<String> domains) {
        if (host == null || host.isEmpty()) return false;
        String normalized = host.toLowerCase(java.util.Locale.ROOT);
        for (String domain : domains) {
            if (normalized.equals(domain) || normalized.endsWith("." + domain)) return true;
        }
        return false;
    }

    static Set<String> loadEnabledPresets(SharedPreferences prefs) {
        return new LinkedHashSet<>(prefs.getStringSet(KEY_ENABLED_PRESETS, Collections.emptySet()));
    }

    static Set<String> loadCustomDomains(SharedPreferences prefs) {
        return new LinkedHashSet<>(prefs.getStringSet(KEY_CUSTOM_DOMAINS, Collections.emptySet()));
    }
}
