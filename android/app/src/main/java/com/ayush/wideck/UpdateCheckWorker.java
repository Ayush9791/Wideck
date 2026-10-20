package com.ayush.wideck;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class UpdateCheckWorker extends Worker {
    private static final String CHANNEL_ID = "wideck_updates";
    private static final int NOTIFICATION_ID = 4103;

    public UpdateCheckWorker(Context context, WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(
                "https://api.github.com/repos/Ayush9791/Wideck/releases/latest"
            ).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(7000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "WiDeck-Android");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return Result.retry();

            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] bytes = new byte[4096];
                int length;
                while ((length = input.read(bytes)) != -1) output.write(bytes, 0, length);
                JSONObject release = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
                String version = release.optString("tag_name", "").replaceFirst("^[vV]", "");
                if (version.isEmpty() || compareVersions(version, BuildConfig.VERSION_NAME) <= 0) {
                    return Result.success();
                }

                JSONArray assets = release.optJSONArray("assets");
                boolean hasApk = false;
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.optJSONObject(i);
                        if (asset == null) continue;
                        String name = asset.optString("name", "");
                        if (name.startsWith("WiDeck-Android-") && name.endsWith(".apk")
                                && !asset.optString("browser_download_url", "").isEmpty()) {
                            hasApk = true;
                            break;
                        }
                    }
                }
                if (!hasApk) return Result.success();

                Context context = getApplicationContext();
                android.content.SharedPreferences prefs =
                    context.getSharedPreferences("wideck", Context.MODE_PRIVATE);
                if (version.equals(prefs.getString("last_notified_update", ""))) {
                    return Result.success();
                }

                createNotificationChannel(context);
                Intent open = new Intent(context, MainActivity.class);
                open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                PendingIntent pendingIntent = PendingIntent.getActivity(
                    context, 4103, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("WiDeck update available")
                    .setContentText("Version " + version + " is ready. Tap to open WiDeck and update.")
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        "WiDeck " + version + " is available. Open WiDeck and tap Update to download and install it."
                    ))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .addAction(android.R.drawable.ic_menu_view, "Open to update", pendingIntent);

                try {
                    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification.build());
                    prefs.edit().putString("last_notified_update", version).apply();
                } catch (SecurityException ignored) {
                    // Notification permission may be disabled by the user.
                }
                return Result.success();
            }
        } catch (Exception ignored) {
            return Result.retry();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "WiDeck updates", NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications when a new WiDeck release is available");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
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
}
