package com.ayush.wideck;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.graphics.drawable.GradientDrawable;
import androidx.core.content.FileProvider;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.io.File;
import java.io.FileOutputStream;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(16,20,27), PANEL = Color.rgb(35,45,58), TEXT = Color.rgb(243,245,248), MUTED = Color.rgb(155,167,183), ACCENT = Color.rgb(113,224,181);
    private EditText hostInput, pinInput;
    private TextView status;
    private String host = "", token = "";
    private JSONArray availableApps = new JSONArray();
    private final ArrayList<JSONObject> chosenApps = new ArrayList<>();
    private JSONArray macros = new JSONArray();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private String latestVersion = "", latestApkUrl = "", latestReleaseNotes = "";
    private String currentPage = "connect";

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        prefs = getSharedPreferences("wideck", MODE_PRIVATE);
        host = prefs.getString("host", "");
        macros = savedMacros();
        showConnect();
        checkForUpdates();
        scheduleBackgroundUpdateChecks();
    }

    private LinearLayout page(String title, String subtitle) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(18), dp(24), dp(18));
        root.setBackgroundColor(BG);
        TextView h = new TextView(this);
        h.setText(title); h.setTextColor(TEXT); h.setTextSize(28);
        h.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(h);
        TextView sub = new TextView(this);
        sub.setText(subtitle); sub.setTextColor(MUTED); sub.setTextSize(13);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(10);
        root.addView(sub, p);
        return root;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextColor(TEXT); b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(PANEL);
        shape.setCornerRadius(dp(12));
        b.setBackground(shape);
        b.setElevation(dp(2));
        return b;
    }

    private JSONArray savedMacros() {
        try { return new JSONArray(prefs.getString("saved_macros", "[]")); }
        catch (JSONException ignored) { return new JSONArray(); }
    }

    private void persistApps() {
        JSONArray selection = new JSONArray();
        for (JSONObject item : chosenApps) selection.put(item);
        prefs.edit().putString("selected_apps", selection.toString()).apply();
    }

    private void persistMacros() {
        prefs.edit().putString("saved_macros", macros.toString()).apply();
    }

    private JSONArray savedApps() {
        try {
            return new JSONArray(prefs.getString("selected_apps", "[]"));
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    private void showConnect() {
        currentPage = "connect";
        LinearLayout root = page("WiDeck", "Your phone, your laptop, one connected control deck.");
        addUpdateBanner(root);
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        hostInput = new EditText(this);
        hostInput.setSingleLine(true);
        hostInput.setHint("Laptop IP (e.g. 192.168.1.8)");
        hostInput.setText(host); hostInput.setTextColor(TEXT); hostInput.setHintTextColor(MUTED);
        hostInput.setTextSize(14);
        hostInput.setBackgroundTintList(android.content.res.ColorStateList.valueOf(MUTED));
        fields.addView(hostInput, new LinearLayout.LayoutParams(0, dp(48), 1));
        pinInput = new EditText(this);
        pinInput.setSingleLine(true); pinInput.setHint("Pairing PIN"); pinInput.setInputType(2);
        pinInput.setTextColor(TEXT); pinInput.setHintTextColor(MUTED);
        pinInput.setBackgroundTintList(android.content.res.ColorStateList.valueOf(MUTED));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(130), dp(48));
        pp.leftMargin = dp(10);
        fields.addView(pinInput, pp);
        root.addView(fields);
        Button connect = button("Connect and pair");
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(48));
        cp.topMargin = dp(10);
        root.addView(connect, cp);
        connect.setOnClickListener(v -> pair());
        status = new TextView(this);
        status.setText("Start WiDeck on Windows and enter the IP and PIN shown there.");
        status.setTextColor(MUTED); status.setTextSize(12); status.setPadding(0, dp(8), 0, 0);
        root.addView(status);
        setContentView(root);
    }

    private void addUpdateBanner(LinearLayout root) {
        if (latestApkUrl.isEmpty()) return;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(10), dp(10), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(24, 57, 51));
        bg.setCornerRadius(dp(14));
        card.setBackground(bg);
        TextView message = new TextView(this);
        message.setText("WiDeck " + latestVersion + " is ready");
        message.setTextColor(ACCENT);
        message.setTextSize(13);
        message.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(message, new LinearLayout.LayoutParams(0, -2, 1));
        Button update = button("Update");
        update.setTextSize(12);
        card.addView(update, new LinearLayout.LayoutParams(dp(96), dp(40)));
        update.setOnClickListener(v -> downloadAndInstallUpdate());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        root.addView(card, 1, params);
    }

    private void scheduleBackgroundUpdateChecks() {
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
            UpdateCheckWorker.class, 12, TimeUnit.HOURS
        ).setConstraints(constraints).build();
        WorkManager.getInstance(getApplicationContext()).enqueueUniquePeriodicWork(
            "wideck-update-check", ExistingPeriodicWorkPolicy.KEEP, request
        );
    }

    private void checkForUpdates() {
        worker.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL("https://api.github.com/repos/Ayush9791/Wideck/releases/latest").openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "WiDeck-Android");
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) return;
                InputStream stream = connection.getInputStream();
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int count;
                while ((count = stream.read(chunk)) != -1) buffer.write(chunk, 0, count);
                JSONObject release = new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
                String tag = release.optString("tag_name", "").replaceFirst("^[vV]", "");
                String current = BuildConfig.VERSION_NAME;
                if (tag.isEmpty() || compareVersions(tag, current) <= 0) return;
                JSONArray assets = release.optJSONArray("assets");
                if (assets == null) return;
                String apkUrl = "";
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset == null) continue;
                    String name = asset.optString("name", "");
                    if (name.startsWith("WiDeck-Android-") && name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", "");
                        if (!apkUrl.isEmpty()) break;
                    }
                }
                if (apkUrl.isEmpty()) return;
                String notes = release.optString("body", "");
                final String foundVersion = tag;
                final String foundUrl = apkUrl;
                final String foundNotes = notes;
                runOnUiThread(() -> {
                    latestVersion = foundVersion;
                    latestApkUrl = foundUrl;
                    latestReleaseNotes = foundNotes;
                    if ("connect".equals(currentPage)) showConnect();
                    else if ("deck".equals(currentPage)) showDeck();
                    else Toast.makeText(this, "WiDeck " + latestVersion + " is available. Open Settings to update.", Toast.LENGTH_LONG).show();
                });
            } catch (Exception ignored) {
                // Update checks are best-effort and must not block normal app use.
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private int compareVersions(String first, String second) {
        try {
            String[] a = first.split("\\.");
            String[] b = second.split("\\.");
            for (int i = 0; i < Math.max(a.length, b.length); i++) {
                int av = i < a.length ? Integer.parseInt(a[i].replaceAll("[^0-9].*$", "")) : 0;
                int bv = i < b.length ? Integer.parseInt(b[i].replaceAll("[^0-9].*$", "")) : 0;
                if (av != bv) return Integer.compare(av, bv);
            }
        } catch (Exception ignored) { }
        return first.compareTo(second);
    }

    private void downloadAndInstallUpdate() {
        if (latestApkUrl.isEmpty()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            new android.app.AlertDialog.Builder(this)
                .setTitle("Allow app updates")
                .setMessage("Android needs permission to install updates downloaded by WiDeck. Enable “Allow from this source”, then return and tap Update again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                }).show();
            return;
        }
        Toast.makeText(this, "Downloading WiDeck " + latestVersion + "…", Toast.LENGTH_SHORT).show();
        final String downloadUrl = latestApkUrl;
        worker.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("User-Agent", "WiDeck-Android");
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) throw new Exception("Download failed (" + code + ")");
                File directory = new File(getCacheDir(), "updates");
                if (!directory.exists() && !directory.mkdirs()) throw new Exception("Cannot create update directory");
                File apk = new File(directory, "WiDeck-update.apk");
                try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(apk)) {
                    byte[] bytes = new byte[8192];
                    int length;
                    while ((length = in.read(bytes)) != -1) out.write(bytes, 0, length);
                }
                if (apk.length() < 1024) throw new Exception("Downloaded APK is too small");
                runOnUiThread(() -> {
                    try {
                        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
                        Intent install = new Intent(Intent.ACTION_VIEW);
                        install.setDataAndType(uri, "application/vnd.android.package-archive");
                        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(install);
                    } catch (Exception e) {
                        Toast.makeText(this, "Could not open Android installer. Download the APK from GitHub Releases.", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Update download failed. Check your connection and retry.", Toast.LENGTH_LONG).show());
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private String baseUrl() {
        String h = host.trim();
        if (h.startsWith("http://")) return h.replaceAll("/$", "");
        if (h.matches(".*:\\d+$")) return "http://" + h;
        return "http://" + h + ":47821";
    }

    private JSONObject request(String method, String path, JSONObject body, boolean auth) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(baseUrl() + path).openConnection();
        c.setRequestMethod(method); c.setConnectTimeout(4000); c.setReadTimeout(4000);
        c.setRequestProperty("Accept", "application/json");
        if (auth && !token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        InputStream stream = code < 400 ? c.getInputStream() : c.getErrorStream();
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096]; int count;
        while ((count = stream.read(chunk)) != -1) buffer.write(chunk, 0, count);
        String result = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        c.disconnect();
        if (code >= 400) throw new Exception("Request failed (" + code + ")");
        return new JSONObject(result);
    }

    private void pair() {
        String enteredHost = hostInput.getText().toString().trim();
        String pin = pinInput.getText().toString().trim();
        if (enteredHost.isEmpty() || pin.isEmpty()) {
            status.setText("Enter the laptop IP and pairing PIN.");
            return;
        }
        host = enteredHost;
        status.setText("Pairing with Windows…");
        worker.execute(() -> {
            try {
                JSONObject b = new JSONObject(); b.put("pin", pin);
                JSONObject r = request("POST", "/pair", b, false);
                token = r.getString("token");
                prefs.edit().putString("host", host).apply();
                availableApps = request("GET", "/apps", null, true).getJSONArray("apps");
                JSONArray saved = savedApps();
                ArrayList<JSONObject> restored = new ArrayList<>();
                for (int j = 0; j < saved.length(); j++) {
                    String savedId = saved.optJSONObject(j) == null ? "" : saved.optJSONObject(j).optString("id", "");
                    for (int k = 0; k < availableApps.length(); k++) {
                        JSONObject candidate = availableApps.optJSONObject(k);
                        if (candidate != null && candidate.optString("id").equals(savedId)) {
                            JSONObject restoredItem = new JSONObject(candidate.toString());
                            JSONObject savedItem = saved.optJSONObject(j);
                            if (savedItem != null) {
                                if (savedItem.has("custom_label")) restoredItem.put("custom_label", savedItem.optString("custom_label"));
                                if (savedItem.has("tile_color")) restoredItem.put("tile_color", savedItem.optString("tile_color"));
                            }
                            restored.add(restoredItem); break;
                        }
                    }
                }
                runOnUiThread(() -> {
                    if (!restored.isEmpty()) { chosenApps.clear(); chosenApps.addAll(restored); showDeck(); }
                    else showAppPicker();
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Could not connect. Check the IP, PIN, Wi-Fi and Windows Firewall."));
            }
        });
    }

    private void showAppPicker() {
        currentPage = "picker";
        LinearLayout root = page("Build your deck", "Choose up to five apps. Your sixth key opens Settings.");
        addUpdateBanner(root);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ArrayList<CheckBox> checks = new ArrayList<>();
        JSONArray saved = savedApps();
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search installed apps (e.g. Spotify)");
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setTextSize(14);
        search.setPadding(dp(12), 0, dp(12), 0);
        search.setBackgroundTintList(android.content.res.ColorStateList.valueOf(MUTED));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(44));
        searchParams.bottomMargin = dp(6);
        root.addView(search, searchParams);
        for (int i = 0; i < availableApps.length(); i++) {
            JSONObject app = availableApps.optJSONObject(i);
            if (app == null) continue;
            CheckBox cb = new CheckBox(this);
            cb.setText(app.optString("name", "Windows app"));
            for (int j = 0; j < saved.length(); j++) {
                JSONObject old = saved.optJSONObject(j);
                if (old != null && old.optString("id").equals(app.optString("id"))) cb.setChecked(true);
            }
            cb.setTextColor(TEXT);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
            cb.setTag(app); cb.setPadding(dp(6), 0, 0, 0);
            checks.add(cb);
            list.addView(cb, new LinearLayout.LayoutParams(-1, dp(34)));
        }
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim().toLowerCase(Locale.ROOT);
                for (CheckBox check : checks) {
                    boolean matches = check.getText().toString().toLowerCase(Locale.ROOT).contains(query);
                    check.setVisibility(matches ? View.VISIBLE : View.GONE);
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        Button save = button("Save deck");
        bottom.addView(save, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button refresh = button("Refresh apps");
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, dp(44), 1);
        rp.leftMargin = dp(8); bottom.addView(refresh, rp); root.addView(bottom);
        save.setOnClickListener(v -> {
            ArrayList<JSONObject> selected = new ArrayList<>();
            for (CheckBox cb : checks) if (cb.isChecked()) {
                if (selected.size() >= 5 - macros.length()) {
                    Toast.makeText(this, "The deck has five programmable slots. Remove a macro or app first.", Toast.LENGTH_SHORT).show();
                    return;
                }
                selected.add((JSONObject) cb.getTag());
            }
            if (selected.isEmpty()) {
                Toast.makeText(this, "Select at least one app", Toast.LENGTH_SHORT).show();
                return;
            }
            chosenApps.clear(); chosenApps.addAll(selected);
            persistApps();
            showDeck();
        });
        refresh.setOnClickListener(v -> loadAppsAgain());
        setContentView(root);
    }

    private void loadAppsAgain() {
        worker.execute(() -> {
            try {
                availableApps = request("GET", "/apps", null, true).getJSONArray("apps");
                runOnUiThread(this::showAppPicker);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Could not refresh app list", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private int tileColor(JSONObject item) {
        String value = item.optString("tile_color", "default");
        if ("mint".equals(value)) return Color.rgb(38, 104, 83);
        if ("blue".equals(value)) return Color.rgb(38, 72, 125);
        if ("purple".equals(value)) return Color.rgb(91, 59, 128);
        if ("orange".equals(value)) return Color.rgb(133, 76, 32);
        return PANEL;
    }

    private void showDeck() {
        currentPage = "deck";
        LinearLayout root = page("WiDeck", "Connected to " + host + " · Tap to run · Hold a tile to customize");
        addUpdateBanner(root);
        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(3); grid.setRowCount(2);
        int total = chosenApps.size() + macros.length();
        for (int i = 0; i < 6; i++) {
            final int index = i;
            Button b;
            if (index == 5) {
                b = button("Settings");
            } else if (index < chosenApps.size()) {
                JSONObject item = chosenApps.get(index);
                b = button(item.optString("custom_label", item.optString("name", "App")));
                b.setBackground(makeTileBackground(tileColor(item)));
                b.setOnLongClickListener(v -> { customizeApp(index); return true; });
            } else if (index < total) {
                JSONObject macro = macros.optJSONObject(index - chosenApps.size());
                String macroName = macro == null ? "Macro" : macro.optString("name", "Macro");
                b = button("⚡ " + macroName);
                b.setBackground(makeTileBackground(macro == null ? PANEL : tileColor(macro)));
                final int macroIndex = index - chosenApps.size();
                b.setOnLongClickListener(v -> { manageMacro(macroIndex); return true; });
            } else {
                b = button(index < 5 ? "＋ Add app / macro" : "Settings");
            }
            b.setTextSize(13);
            android.widget.GridLayout.LayoutParams gp = new android.widget.GridLayout.LayoutParams();
            gp.width = 0; gp.height = 0;
            gp.columnSpec = android.widget.GridLayout.spec(index % 3, 1f);
            gp.rowSpec = android.widget.GridLayout.spec(index / 3, 1f);
            gp.setMargins(dp(5), dp(5), dp(5), dp(5));
            grid.addView(b, gp);
            b.setOnClickListener(v -> {
                if (index == 5) showSettings();
                else if (index < chosenApps.size()) launchApp(chosenApps.get(index));
                else if (index < total) runMacro(macros.optJSONObject(index - chosenApps.size()));
                else showAddChoice();
            });
        }
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private GradientDrawable makeTileBackground(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(dp(12));
        return shape;
    }

    private void showAddChoice() {
        String[] options = {"Add Windows app", "Create macro button"};
        new AlertDialog.Builder(this).setTitle("Customize your deck")
            .setItems(options, (dialog, which) -> {
                if (which == 0) showAppPicker();
                else createMacro();
            }).show();
    }

    private void customizeApp(int index) {
        if (index < 0 || index >= chosenApps.size()) return;
        JSONObject item = chosenApps.get(index);
        String[] options = {"Rename tile", "Change tile colour", "Move earlier", "Move later", "Remove from deck"};
        new AlertDialog.Builder(this).setTitle(item.optString("custom_label", item.optString("name", "App")))
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    EditText name = new EditText(this);
                    name.setSingleLine(true);
                    name.setText(item.optString("custom_label", item.optString("name", "App")));
                    new AlertDialog.Builder(this).setTitle("Tile label").setView(name)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Save", (d, w) -> {
                            String value = name.getText().toString().trim();
                            try {
                                if (value.isEmpty() || value.equals(item.optString("name"))) item.remove("custom_label");
                                else item.put("custom_label", value);
                                persistApps(); showDeck();
                            } catch (JSONException ignored) { }
                        }).show();
                } else if (which == 1) {
                    String[] colors = {"Default", "Mint", "Blue", "Purple", "Orange"};
                    String[] keys = {"default", "mint", "blue", "purple", "orange"};
                    new AlertDialog.Builder(this).setTitle("Tile colour").setItems(colors, (d, choice) -> {
                        try { if ("default".equals(keys[choice])) item.remove("tile_color"); else item.put("tile_color", keys[choice]); persistApps(); showDeck(); }
                        catch (JSONException ignored) { }
                    }).show();
                } else if (which == 2 || which == 3) {
                    int target = which == 2 ? index - 1 : index + 1;
                    if (target >= 0 && target < chosenApps.size()) {
                        chosenApps.remove(index); chosenApps.add(target, item); persistApps(); showDeck();
                    }
                } else if (which == 4) {
                    chosenApps.remove(index); persistApps(); showDeck();
                }
            }).show();
    }

    private void createMacro() {
        if (chosenApps.size() + macros.length() >= 5) {
            Toast.makeText(this, "Free one deck slot before adding a macro.", Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(4), dp(20), 0);
        EditText name = new EditText(this); name.setSingleLine(true); name.setHint("Macro name (e.g. Work setup)");
        form.addView(name);
        EditText website = new EditText(this); website.setSingleLine(true); website.setHint("Optional website URL (https://...)");
        form.addView(website);
        TextView hint = new TextView(this);
        hint.setText("Select apps to launch in order. The website opens after the apps.");
        hint.setTextColor(MUTED); hint.setPadding(0, dp(8), 0, dp(4)); form.addView(hint);
        ArrayList<CheckBox> checks = new ArrayList<>();
        for (JSONObject app : chosenApps) {
            CheckBox check = new CheckBox(this);
            check.setText(app.optString("custom_label", app.optString("name", "App")));
            check.setTextColor(TEXT); check.setTag(app); form.addView(check); checks.add(check);
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(form);
        new AlertDialog.Builder(this).setTitle("Create macro").setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save macro", (dialog, which) -> {
                String macroName = name.getText().toString().trim();
                String url = website.getText().toString().trim();
                JSONArray appIds = new JSONArray();
                for (CheckBox check : checks) if (check.isChecked()) appIds.put(((JSONObject) check.getTag()).optString("id"));
                if (macroName.isEmpty()) { Toast.makeText(this, "Enter a macro name.", Toast.LENGTH_SHORT).show(); return; }
                if (appIds.length() == 0 && url.isEmpty()) { Toast.makeText(this, "Select an app or enter a website.", Toast.LENGTH_SHORT).show(); return; }
                if (!url.isEmpty() && !(url.startsWith("https://") || url.startsWith("http://"))) {
                    Toast.makeText(this, "Website must start with http:// or https://.", Toast.LENGTH_LONG).show(); return;
                }
                try {
                    JSONObject macro = new JSONObject();
                    macro.put("id", Long.toString(System.currentTimeMillis()));
                    macro.put("name", macroName); macro.put("app_ids", appIds); macro.put("url", url);
                    JSONArray updated = new JSONArray();
                    for (int i = 0; i < macros.length(); i++) updated.put(macros.getJSONObject(i));
                    updated.put(macro); macros = updated; persistMacros(); showDeck();
                } catch (JSONException ignored) { }
            }).show();
    }

    private void manageMacro(int index) {
        JSONObject macro = macros.optJSONObject(index);
        if (macro == null) return;
        String[] options = {"Rename macro", "Change tile colour", "Move earlier", "Move later", "Delete macro"};
        new AlertDialog.Builder(this).setTitle(macro.optString("name", "Macro")).setItems(options, (dialog, which) -> {
            if (which == 0) {
                EditText name = new EditText(this); name.setSingleLine(true); name.setText(macro.optString("name", ""));
                new AlertDialog.Builder(this).setTitle("Macro name").setView(name).setNegativeButton("Cancel", null)
                    .setPositiveButton("Save", (d, w) -> { try { String n = name.getText().toString().trim(); if (!n.isEmpty()) macro.put("name", n); persistMacros(); showDeck(); } catch (JSONException ignored) { } }).show();
            } else if (which == 1) {
                String[] colors = {"Default", "Mint", "Blue", "Purple", "Orange"};
                String[] keys = {"default", "mint", "blue", "purple", "orange"};
                new AlertDialog.Builder(this).setTitle("Tile colour").setItems(colors, (d, c) -> {
                    try { if ("default".equals(keys[c])) macro.remove("tile_color"); else macro.put("tile_color", keys[c]); persistMacros(); showDeck(); }
                    catch (JSONException ignored) { }
                }).show();
            } else if (which == 2 || which == 3) {
                int target = which == 2 ? index - 1 : index + 1;
                if (target >= 0 && target < macros.length()) {
                    ArrayList<JSONObject> ordered = new ArrayList<>();
                    for (int i = 0; i < macros.length(); i++) ordered.add(macros.optJSONObject(i));
                    ordered.remove(index);
                    ordered.add(target, macro);
                    JSONArray updated = new JSONArray();
                    for (JSONObject entry : ordered) updated.put(entry);
                    macros = updated; persistMacros(); showDeck();
                }
            } else if (which == 4) {
                JSONArray updated = new JSONArray();
                for (int i = 0; i < macros.length(); i++) if (i != index) updated.put(macros.optJSONObject(i));
                macros = updated; persistMacros(); showDeck();
            }
        }).show();
    }

    private void runMacro(JSONObject macro) {
        if (macro == null) return;
        worker.execute(() -> {
            try {
                JSONArray steps = new JSONArray();
                JSONArray ids = macro.optJSONArray("app_ids");
                if (ids != null) for (int i = 0; i < ids.length(); i++) {
                    JSONObject step = new JSONObject(); step.put("type", "launch"); step.put("app_id", ids.optString(i)); steps.put(step);
                }
                String url = macro.optString("url", "").trim();
                if (!url.isEmpty()) { JSONObject step = new JSONObject(); step.put("type", "url"); step.put("url", url); steps.put(step); }
                JSONObject body = new JSONObject(); body.put("steps", steps);
                request("POST", "/macro", body, true);
                runOnUiThread(() -> Toast.makeText(this, "Running " + macro.optString("name", "macro"), Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Macro failed. Reconnect and try again.", Toast.LENGTH_LONG).show());
            }
        });
    }

    private void launchApp(JSONObject selected) {
        String id = selected.optString("id", "");
        worker.execute(() -> {
            try {
                JSONObject body = new JSONObject(); body.put("app_id", id);
                request("POST", "/launch", body, true);
                runOnUiThread(() -> Toast.makeText(this, "Launching " + selected.optString("name"), Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Launch failed. Reconnect and try again.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showSettings() {
        currentPage = "settings";
        LinearLayout root = page("Settings", "Manage your deck and connection.");
        addUpdateBanner(root);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button up = button("Volume +"), down = button("Volume −"), mute = button("Mute");
        row.addView(up, new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams d = new LinearLayout.LayoutParams(0, dp(52), 1);
        d.leftMargin = dp(6); row.addView(down, d);
        LinearLayout.LayoutParams m = new LinearLayout.LayoutParams(0, dp(52), 1);
        m.leftMargin = dp(6); row.addView(mute, m);
        root.addView(row);
        up.setOnClickListener(v -> sendAction("volume_up"));
        down.setOnClickListener(v -> sendAction("volume_down"));
        mute.setOnClickListener(v -> sendAction("mute"));
        Button addMacro = button("Create macro button");
        LinearLayout.LayoutParams amp = new LinearLayout.LayoutParams(-1, dp(46));
        amp.topMargin = dp(10); root.addView(addMacro, amp);
        addMacro.setOnClickListener(v -> createMacro());
        Button choose = button("Change deck apps");
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(46));
        p.topMargin = dp(10); root.addView(choose, p);
        choose.setOnClickListener(v -> showAppPicker());
        Button reconnect = button("Disconnect / reconnect");
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(-1, dp(46));
        p2.topMargin = dp(8); root.addView(reconnect, p2);
        reconnect.setOnClickListener(v -> { token = ""; showConnect(); });
        Button back = button("Back to deck");
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(-1, dp(46));
        p3.topMargin = dp(8); root.addView(back, p3);
        back.setOnClickListener(v -> showDeck());
        setContentView(root);
    }

    private void sendAction(String action) {
        worker.execute(() -> {
            try {
                JSONObject body = new JSONObject(); body.put("action", action);
                request("POST", "/action", body, true);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Command failed. Reconnect to Windows.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }
}