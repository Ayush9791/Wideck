package com.ayush.wideck;

import android.app.Activity;
import android.content.SharedPreferences;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(16,20,27), PANEL = Color.rgb(35,45,58), TEXT = Color.rgb(243,245,248), MUTED = Color.rgb(155,167,183), ACCENT = Color.rgb(113,224,181);
    private EditText hostInput, pinInput;
    private TextView status;
    private String host = "", token = "";
    private JSONArray availableApps = new JSONArray();
    private final ArrayList<JSONObject> chosenApps = new ArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        prefs = getSharedPreferences("wideck", MODE_PRIVATE);
        host = prefs.getString("host", "");
        showConnect();
    }

    private LinearLayout page(String title, String subtitle) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(12));
        root.setBackgroundColor(BG);
        TextView h = new TextView(this);
        h.setText(title); h.setTextColor(TEXT); h.setTextSize(25);
        h.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(h);
        TextView sub = new TextView(this);
        sub.setText(subtitle); sub.setTextColor(MUTED); sub.setTextSize(12);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(10);
        root.addView(sub, p);
        return root;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextColor(TEXT); b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL));
        return b;
    }

    private JSONArray savedApps() {
        try {
            return new JSONArray(prefs.getString("selected_apps", "[]"));
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    private void showConnect() {
        LinearLayout root = page("WiDeck", "Connect to your Windows host first. Both devices must be on the same Wi-Fi.");
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
                            restored.add(candidate); break;
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
        LinearLayout root = page("Choose your deck", "Select up to five Windows apps. The sixth key is reserved for Settings.");
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
                if (selected.size() == 5) {
                    Toast.makeText(this, "Choose no more than five apps", Toast.LENGTH_SHORT).show();
                    return;
                }
                selected.add((JSONObject) cb.getTag());
            }
            if (selected.isEmpty()) {
                Toast.makeText(this, "Select at least one app", Toast.LENGTH_SHORT).show();
                return;
            }
            chosenApps.clear(); chosenApps.addAll(selected);
            JSONArray selection = new JSONArray();
            for (JSONObject item : selected) selection.put(item);
            prefs.edit().putString("selected_apps", selection.toString()).apply();
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

    private void showDeck() {
        LinearLayout root = page("WiDeck", "Connected to " + host + " · Tap an app to launch it");
        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(3); grid.setRowCount(2);
        for (int i = 0; i < 6; i++) {
            final int index = i;
            String label = index < chosenApps.size() ? chosenApps.get(index).optString("name", "App") : (index == 5 ? "Settings" : "＋ Add app");
            Button b = button(label); b.setTextSize(13);
            android.widget.GridLayout.LayoutParams gp = new android.widget.GridLayout.LayoutParams();
            gp.width = 0; gp.height = 0;
            gp.columnSpec = android.widget.GridLayout.spec(index % 3, 1f);
            gp.rowSpec = android.widget.GridLayout.spec(index / 3, 1f);
            gp.setMargins(dp(5), dp(5), dp(5), dp(5));
            grid.addView(b, gp);
            b.setOnClickListener(v -> {
                if (index == 5) showSettings();
                else if (index < chosenApps.size()) launchApp(chosenApps.get(index));
                else showAppPicker();
            });
        }
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
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
        LinearLayout root = page("Settings", "Deck controls and connection settings");
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