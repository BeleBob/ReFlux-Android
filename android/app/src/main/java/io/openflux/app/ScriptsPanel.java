package io.openflux.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.openflux.bridge.mobile.Mobile;

// Settings → «JS-транспорты»: the experimental-features switch, importing a signed
// script transport (link or file) after the user checks its author, and the
// installed ones with their updates. Every trust decision is the core's
// (InspectTransport, Check/Apply/RollbackScriptUpdate); this screen only asks
// and shows. Ported from upstream's ScriptsScreen.
final class ScriptsPanel {
    static final int PICK_FILE = 4101;
    private static final int MAX_DOWNLOAD = 8 << 20;
    // One job at a time, shared by every panel the settings page builds.
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private final MainActivity host;
    private final Map<String, JSONObject> reports = new HashMap<>();
    private final FlowStyle ui;
    private final ScriptStore store;
    private final LinearLayout page;
    private String pendingKey = "";
    private boolean busy;

    ScriptsPanel(MainActivity host) {
        this.host = host;
        ui = new FlowStyle(host);
        store = new ScriptStore(host);
        page = new LinearLayout(host);
        page.setOrientation(LinearLayout.VERTICAL);
        render();
    }

    // The settings tab's content; MainActivity puts it in its own scroll.
    View view() { return page; }

    private void render() {
        page.removeAllViews();
        boolean on = ScriptStore.experimental(host);

        LinearLayout toggle = card();
        LinearLayout row = new LinearLayout(host);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(caption(host.getString(R.string.scripts_experimental), 15, true), textBeside());
        Switch sw = toggle();
        sw.setChecked(on);
        sw.setOnCheckedChangeListener((b, checked) -> setExperimental(checked));
        row.addView(sw);
        toggle.addView(row);
        toggle.addView(caption(host.getString(R.string.scripts_intro), 12, false));
        page.addView(toggle, spaced());
        if (!on) return;

        LinearLayout actions = new LinearLayout(host);
        actions.addView(button(host.getString(R.string.scripts_import), true, this::showImport), new LinearLayout.LayoutParams(0, dp(46), 1f));
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        checkParams.leftMargin = dp(8);
        actions.addView(button(host.getString(R.string.scripts_check_updates), false, this::checkAll), checkParams);
        page.addView(actions, spaced());

        if (store.list().isEmpty()) {
            page.addView(caption(host.getString(R.string.scripts_empty), 13, false), spaced());
        }
        for (ScriptStore.Script s : store.list()) page.addView(scriptCard(s), spaced());
        page.addView(caption(host.getString(R.string.scripts_warning),
                12, false), spaced());
    }

    private void setExperimental(boolean on) {
        host.getSharedPreferences(MainActivity.SETTINGS_PREFS_NAME, Activity.MODE_PRIVATE).edit()
                .putBoolean(ScriptStore.KEY_EXPERIMENTAL, on).apply();
        if (!on) {
            render();
            return;
        }
        run(host.getString(R.string.scripts_installing_builtin), () -> {
            store.syncBundled();
            Log.i(ScriptStore.TAG, "engine available=" + Mobile.scriptEngineAvailable()
                    + " selftest=" + Mobile.scriptEngineSelfTest(host.getCacheDir().getAbsolutePath()));
            return null;
        });
    }

    private LinearLayout scriptCard(ScriptStore.Script s) {
        LinearLayout card = card();
        LinearLayout top = new LinearLayout(host);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names = new LinearLayout(host);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(caption(s.name + "  · " + (s.official ? "OpenFlux" : host.getString(R.string.scripts_third_party_short)), 15, true));
        names.addView(caption(host.getString(R.string.scripts_version_source,
                s.version.isEmpty() ? "-" : s.version, sourceLabel(s.source)), 12, false));
        top.addView(names, textBeside());
        Switch enabled = toggle();
        enabled.setChecked(s.enabled);
        enabled.setContentDescription(host.getString(R.string.scripts_enabled));
        enabled.setOnCheckedChangeListener((b, checked) -> store.setEnabled(s.id, checked));
        top.addView(enabled);
        card.addView(top);

        if (s.hasSettings()) {
            card.addView(caption(host.getString(R.string.scripts_settings_where, s.settingsPage
                    ? host.getString(R.string.scripts_own_settings)
                    : host.getString(R.string.scripts_params, s.params.length())), 12, false));
        }
        TextView fingerprint = caption(host.getString(R.string.scripts_fingerprint, s.shortFingerprint()), 12, false);
        fingerprint.setOnClickListener(v -> {
            host.getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("fingerprint", s.fingerprint));
            Toast.makeText(host, host.getString(R.string.scripts_fingerprint_copied), Toast.LENGTH_SHORT).show();
        });
        card.addView(fingerprint);

        JSONObject report = reports.get(s.id);
        if (report != null) {
            String status = report.optString("status");
            if ("available".equals(status)) {
                card.addView(button(host.getString(R.string.scripts_update_to, report.optString("latest")), true, () -> confirmUpdate(s, report)), buttonRow());
            } else if (!"current".equals(status) && !report.optString("code").isEmpty()) {
                card.addView(caption(updateFailure(report.optString("code")), 12, false));
            }
        }
        LinearLayout row = new LinearLayout(host);
        if (store.hasPrevious(s)) {
            row.addView(button(host.getString(R.string.scripts_rollback), false, () -> rollback(s)), new LinearLayout.LayoutParams(0, dp(42), 1f));
        }
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        if (row.getChildCount() > 0) deleteParams.leftMargin = dp(8);
        row.addView(button(host.getString(R.string.delete), false, () -> ui.dialog()
                .setTitle(host.getString(R.string.scripts_delete_question, s.name))
                .setMessage(host.getString(R.string.scripts_delete_text))
                .setNegativeButton(host.getString(R.string.cancel), null)
                .setPositiveButton(host.getString(R.string.delete), (d, w) -> {
                    store.delete(s.id);
                    render();
                }).show()), deleteParams);
        card.addView(row, buttonRow());
        return card;
    }

    // ---- import ----

    private void showImport() {
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        EditText url = input(host.getString(R.string.scripts_import_url), InputType.TYPE_TEXT_VARIATION_URI);
        EditText key = input(host.getString(R.string.scripts_import_key), InputType.TYPE_CLASS_TEXT);
        form.addView(url);
        form.addView(key);
        ui.dialog()
                .setTitle(host.getString(R.string.scripts_import_title))
                .setView(form)
                .setNegativeButton(host.getString(R.string.cancel), null)
                .setNeutralButton(host.getString(R.string.scripts_from_file), (d, w) -> {
                    pendingKey = key.getText().toString().trim();
                    Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                    host.startActivityForResult(pick, PICK_FILE);
                })
                .setPositiveButton(host.getString(R.string.scripts_download), (d, w) -> {
                    String link = url.getText().toString().trim();
                    String author = key.getText().toString().trim();
                    if (!link.startsWith("https://") && !link.startsWith("http://")) {
                        Toast.makeText(host, host.getString(R.string.scripts_need_url), Toast.LENGTH_LONG).show();
                        return;
                    }
                    run(host.getString(R.string.scripts_downloading), () -> {
                        byte[] data = download(link);
                        byte[] sig = link.endsWith(".js") ? download(link + ".sig") : new byte[0];
                        return new Candidate(data, sig, author, link.contains("github") ? "github" : "link", link);
                    });
                })
                .show();
    }

    // MainActivity hands every result here first; true when it was this panel's.
    boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_FILE) return false;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return true;
        Uri uri = data.getData();
        String author = pendingKey;
        run(host.getString(R.string.scripts_reading_file), () -> {
            try (InputStream in = host.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException(host.getString(R.string.scripts_file_unopenable));
                return new Candidate(ScriptStore.readAll(in), new byte[0], author, "file", uri.toString());
            }
        });
        return true;
    }

    // A downloaded or picked transport waiting for the user's trust.
    private static final class Candidate {
        final byte[] data, sig;
        final String key, source, origin;

        Candidate(byte[] data, byte[] sig, String key, String source, String origin) {
            this.data = data;
            this.sig = sig;
            this.key = key.isEmpty() ? Mobile.officialScriptKey() : key;
            this.source = source;
            this.origin = origin;
        }
    }

    private void showTrust(Candidate c) {
        JSONObject r = store.inspect(c.data, c.sig, c.key);
        if (r == null || !r.optBoolean("ok")) {
            Toast.makeText(host, host.getString(R.string.scripts_read_failed,
                    r == null ? host.getString(R.string.scripts_core_not_json) : r.optString("error")), Toast.LENGTH_LONG).show();
            return;
        }
        boolean valid = "valid".equals(r.optString("signature"));
        ScriptStore.Script preview = new ScriptStore.Script();
        preview.fingerprint = r.optString("fingerprint");
        String message = host.getString(R.string.scripts_trust_details,
                r.optString("name"), r.optString("version", "-"),
                host.getString(valid ? R.string.scripts_signature_valid : R.string.scripts_signature_invalid),
                host.getString(r.optBoolean("official") ? R.string.scripts_author_official : R.string.scripts_author_unknown),
                preview.shortFingerprint(),
                r.optJSONArray("params") == null ? 0 : r.optJSONArray("params").length())
                + "\n\n" + host.getString(valid ? R.string.scripts_trust_warning : R.string.scripts_trust_blocked);
        android.app.AlertDialog.Builder dialog = ui.dialog()
                .setTitle(host.getString(R.string.scripts_review_title))
                .setMessage(message)
                .setNegativeButton(host.getString(R.string.cancel), null);
        if (valid) {
            dialog.setPositiveButton(host.getString(R.string.scripts_trust_install), (d, w) -> run(host.getString(R.string.scripts_installing), () -> {
                store.install(c.data, c.sig, c.key, c.source, c.origin);
                return host.getString(R.string.scripts_installed);
            }));
        }
        dialog.show();
    }

    private byte[] download(String link) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(link).openConnection();
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(30_000);
        try {
            if (connection.getResponseCode() / 100 != 2) {
                throw new IOException(host.getString(R.string.scripts_server_answered, connection.getResponseCode()));
            }
            if (connection.getContentLengthLong() > MAX_DOWNLOAD) throw new IOException(host.getString(R.string.scripts_file_too_large));
            try (InputStream in = connection.getInputStream()) {
                byte[] data = ScriptStore.readAll(in);
                if (data.length > MAX_DOWNLOAD) throw new IOException(host.getString(R.string.scripts_file_too_large));
                return data;
            }
        } finally {
            connection.disconnect();
        }
    }

    // ---- updates ----

    private void checkAll() {
        run(host.getString(R.string.scripts_checking_updates), () -> {
            int waiting = 0;
            for (ScriptStore.Script s : store.list()) {
                if (!s.enabled || !s.fileName.endsWith(".flux")) continue;
                JSONObject r = s.updatable()
                        ? report(Mobile.checkScriptUpdate(s.installedJson(), "stable"))
                        : new JSONObject().put("status", "error").put("code", "no_source");
                reports.put(s.id, r);
                if ("available".equals(r.optString("status"))) waiting++;
            }
            return waiting == 0 ? host.getString(R.string.scripts_all_fresh)
                    : host.getString(R.string.scripts_updates_available, waiting);
        });
    }

    private void confirmUpdate(ScriptStore.Script s, JSONObject report) {
        boolean wireBreak = report.optBoolean("wireBreak");
        String notes = report.optString("notes");
        ui.dialog()
                .setTitle(s.name + ": " + report.optString("current") + " → " + report.optString("latest"))
                .setMessage((wireBreak ? host.getString(R.string.scripts_wire_break) + "\n\n" : "")
                        + (notes.isEmpty() ? host.getString(R.string.scripts_update_title) : notes))
                .setNegativeButton(host.getString(R.string.cancel), null)
                .setPositiveButton(host.getString(R.string.update), (d, w) -> run(host.getString(R.string.scripts_updating), () -> {
                    JSONObject r = report(Mobile.applyScriptUpdate(s.installedJson(), "stable", store.dir.getPath(), wireBreak));
                    if (!"installed".equals(r.optString("status"))) return updateFailure(r.optString("code"));
                    if (!r.optString("newKey").isEmpty()) store.repin(s.id, r.optString("newKey"));
                    store.refresh(s.id);
                    reports.remove(s.id);
                    return host.getString(R.string.scripts_updated_to, s.name, r.optString("latest"));
                }))
                .show();
    }

    private void rollback(ScriptStore.Script s) {
        run(host.getString(R.string.scripts_rolling_back), () -> {
            JSONObject r = report(Mobile.rollbackScript(s.installedJson(), store.dir.getPath()));
            if (!"installed".equals(r.optString("status"))) return updateFailure(r.optString("code"));
            store.refresh(s.id);
            reports.remove(s.id);
            return host.getString(R.string.scripts_rolled_back, r.optString("latest"));
        });
    }

    private static JSONObject report(String raw) throws JSONException {
        try {
            return new JSONObject(raw);
        } catch (JSONException e) {
            return new JSONObject().put("status", "error").put("code", "bad_answer");
        }
    }

    // Words for the core's script-update codes (upstream's ScriptUpdateMessages).
    private String updateFailure(String code) {
        switch (code) {
            case "no_source": return host.getString(R.string.upd_no_source);
            case "fetch_failed": return host.getString(R.string.upd_fetch_failed);
            case "insecure_url": return host.getString(R.string.upd_not_https);
            case "bad_index": return host.getString(R.string.upd_bad_manifest);
            case "id_mismatch": return host.getString(R.string.upd_other_transport);
            case "no_channel": return host.getString(R.string.upd_no_release);
            case "needs_newer_app": return host.getString(R.string.upd_needs_app);
            case "key_changed": return host.getString(R.string.upd_key_changed);
            case "wire_break": return host.getString(R.string.upd_wire_changed);
            case "bad_hash": return host.getString(R.string.upd_hash_mismatch);
            case "bad_package": return host.getString(R.string.upd_unreadable);
            case "bad_signature": return host.getString(R.string.upd_bad_signature);
            case "version_mismatch": return host.getString(R.string.upd_wrong_version);
            case "not_newer": return host.getString(R.string.upd_not_newer);
            case "install_failed": return host.getString(R.string.upd_write_failed);
            case "no_previous": return host.getString(R.string.upd_no_previous);
            default: return host.getString(R.string.scripts_failed, code);
        }
    }

    private String sourceLabel(String source) {
        switch (source) {
            case "bundled": return host.getString(R.string.scripts_source_bundled);
            case "github": return "GitHub";
            case "link": return host.getString(R.string.scripts_source_link);
            default: return host.getString(R.string.scripts_source_file);
        }
    }

    // ---- plumbing ----

    private interface Job { Object run() throws Exception; }

    // One job at a time on the worker; a Candidate opens the trust dialog, a
    // String is shown as a toast, and the list is redrawn either way.
    private void run(String progress, Job job) {
        if (busy) return;
        busy = true;
        Toast.makeText(host, progress, Toast.LENGTH_SHORT).show();
        WORKER.execute(() -> {
            Object result;
            try {
                result = job.run();
            } catch (Exception e) {
                result = host.getString(R.string.scripts_failed, e.getMessage());
            }
            Object done = result;
            host.runOnUiThread(() -> {
                busy = false;
                if (host.isFinishing()) return;
                render();
                if (done instanceof Candidate) showTrust((Candidate) done);
                else if (done instanceof String) Toast.makeText(host, (String) done, Toast.LENGTH_LONG).show();
            });
        });
    }

    // The app's own switch, as on the other settings tabs.
    private Switch toggle() {
        Switch toggle = new Switch(host);
        host.styleSwitch(toggle);
        return toggle;
    }

    // Text next to a switch: the rest of the row, kept off the switch.
    private LinearLayout.LayoutParams textBeside() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        params.rightMargin = dp(16);
        return params;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(ui.rounded(ui.surface, ui.border, 12));
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        return card;
    }

    private TextView caption(String value, int size, boolean strong) {
        TextView view = new TextView(host);
        view.setText(value);
        view.setTextSize(size);
        if (strong) ui.heading(view);
        else ui.body(view);
        view.setPadding(0, dp(2), 0, dp(2));
        return view;
    }

    private Button button(String label, boolean primary, Runnable action) {
        Button button = new Button(host);
        button.setText(label);
        button.setAllCaps(false);
        if (primary) ui.primary(button);
        else ui.secondary(button);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private EditText input(String hint, int variation) {
        EditText input = new EditText(host);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | variation);
        input.setTextColor(ui.text);
        input.setHintTextColor(ui.hint);
        return input;
    }

    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        return params;
    }

    private LinearLayout.LayoutParams buttonRow() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        return params;
    }

    private int dp(int value) { return ui.dp(value); }
}
