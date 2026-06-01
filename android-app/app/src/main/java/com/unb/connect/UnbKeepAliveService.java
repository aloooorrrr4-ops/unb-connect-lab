package com.unb.connect;

// UNB_CONNECT_V43_KEEPALIVE_AUTOWAKE_SERVICE

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;

public class UnbKeepAliveService extends Service {
    static final String CHANNEL_ID = "unb_connect_v43_autowake";
    static final int NOTIFICATION_ID = 4301;
    static final long CHECK_INTERVAL_MS = 15000L;

    Handler handler;
    boolean busy = false;
    PowerManager.WakeLock wakeLock;

    final Runnable tick = new Runnable() {
        @Override public void run() {
            checkQueue();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        startAsForeground();
        schedule(2500L);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        schedule(2500L);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    void startAsForeground() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "UNB Connect Auto Pull",
                    NotificationManager.IMPORTANCE_LOW
                );
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.createNotificationChannel(ch);
            }

            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

            Notification n = b
                .setContentTitle("UNB Connect")
                .setContentText("فحص الطابور كل 15 ثانية")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .build();

            startForeground(NOTIFICATION_ID, n);
        } catch (Exception ignored) {}
    }

    void schedule(long delay) {
        try {
            handler.removeCallbacks(tick);
            handler.postDelayed(tick, delay);
        } catch (Exception ignored) {}
    }

    void checkQueue() {
        if (busy) {
            schedule(CHECK_INTERVAL_MS);
            return;
        }

        busy = true;

        new Thread(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences("unb_connect", MODE_PRIVATE);
                if (!prefs.getBoolean("full_link_enabled", true)) return;
                if (!prefs.getBoolean("m_whatsapp_web", true)) return;

                String base = prefs.getString("server_url", "http://127.0.0.1:3108");
                String token = prefs.getString("token", "");
                if (base == null || base.trim().length() == 0 || token == null || token.trim().length() == 0) return;

                String res = httpGet(base.replaceAll("/+$", "") + "/api/queue/list?token=" + URLEncoder.encode(token, "UTF-8"));
                if (hasPendingWhatsAppWeb(res)) {
                    wakeAndOpenApp();
                }
            } catch (Exception ignored) {
            } finally {
                busy = false;
                schedule(CHECK_INTERVAL_MS);
            }
        }).start();
    }

    boolean hasPendingWhatsAppWeb(String res) {
        try {
            JSONObject j = new JSONObject(res);
            JSONArray items = j.optJSONArray("items");
            if (items == null) return false;

            for (int i = 0; i < items.length(); i++) {
                JSONObject r = items.optJSONObject(i);
                if (r == null) continue;

                String method = r.optString("delivery_method", r.optString("method", ""));
                String status = r.optString("status", "");

                if ("whatsapp_web".equals(method) && "pending".equals(status)) {
                    return true;
                }
            }
        } catch (Exception ignored) {}

        return false;
    }

    void wakeAndOpenApp() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                try {
                    wakeLock = pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                        PowerManager.ACQUIRE_CAUSES_WAKEUP |
                        PowerManager.ON_AFTER_RELEASE,
                        "UNB_CONNECT:AUTOWAKE_SERVICE_V43"
                    );
                    wakeLock.acquire(2 * 60 * 1000L);
                } catch (Exception e) {
                    try {
                        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UNB_CONNECT:AUTOWAKE_SERVICE_V43_PARTIAL");
                        wakeLock.acquire(2 * 60 * 1000L);
                    } catch (Exception ignored) {}
                }
            }

            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            i.putExtra("unb_v43_autowake_whatsapp_web", true);
            startActivity(i);
        } catch (Exception ignored) {}
    }

    String httpGet(String urlText) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(6000);
        c.setRequestMethod("GET");

        BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null) sb.append(line).append('\n');

        br.close();
        c.disconnect();

        return sb.toString();
    }
}
