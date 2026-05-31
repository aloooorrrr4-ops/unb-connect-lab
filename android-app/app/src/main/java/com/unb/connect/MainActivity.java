package com.unb.connect;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class MainActivity extends Activity {
    EditText serverUrl, tokenBox;
    TextView logView;
    SharedPreferences prefs;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("unb_connect", MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 48, 32, 32);
        root.setGravity(Gravity.RIGHT);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("UNB Connect APK");
        title.setTextSize(26);
        title.setGravity(Gravity.RIGHT);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("نسخة مؤقتة: التحكم من Termux + إرسال واتساب عبر Accessibility.");
        desc.setTextSize(17);
        desc.setGravity(Gravity.RIGHT);
        root.addView(desc);

        serverUrl = new EditText(this);
        serverUrl.setHint("Server URL");
        serverUrl.setText(prefs.getString("server_url", "http://127.0.0.1:3108"));
        root.addView(serverUrl);

        tokenBox = new EditText(this);
        tokenBox.setHint("Device Token");
        tokenBox.setText(prefs.getString("device_token", ""));
        root.addView(tokenBox);

        Button save = button("حفظ");
        save.setOnClickListener(v -> savePrefs());
        root.addView(save);

        Button pair = button("ربط الجهاز مع Termux");
        pair.setOnClickListener(v -> pairDevice());
        root.addView(pair);

        Button acc = button("فتح إعدادات Accessibility");
        acc.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(acc);

        Button poll = button("سحب وتنفيذ طلب واحد");
        poll.setOnClickListener(v -> pollOnce());
        root.addView(poll);

        Button clear = button("تنظيف طلب واتساب المعلق");
        clear.setOnClickListener(v -> {
            prefs.edit()
                    .remove("pending_job_id")
                    .remove("pending_server_url")
                    .remove("pending_token")
                    .apply();
            log("تم تنظيف الطلب المعلق");
        });
        root.addView(clear);

        logView = new TextView(this);
        logView.setTextSize(14);
        logView.setGravity(Gravity.LEFT);
        logView.setText("جاهز...\n");
        root.addView(logView);

        setContentView(scroll);
    }

    Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    void savePrefs() {
        prefs.edit()
                .putString("server_url", serverUrl.getText().toString().trim())
                .putString("device_token", tokenBox.getText().toString().trim())
                .apply();
        log("تم الحفظ");
    }

    String base() {
        String b = serverUrl.getText().toString().trim();
        if (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }

    String token() {
        return tokenBox.getText().toString().trim();
    }

    void log(String s) {
        runOnUiThread(() -> logView.append("\n" + s));
    }

    void pairDevice() {
        savePrefs();
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("device_name", "UNB Connect Android");
                body.put("institution_id", 1);
                body.put("institution_name", "مؤسسة التجربة");

                JSONObject res = postJson(base() + "/api/device/pair", body, null);
                String t = res.getJSONObject("device").getString("device_token");

                prefs.edit().putString("device_token", t).apply();
                runOnUiThread(() -> tokenBox.setText(t));

                log("PAIR_OK");
                log(t);
            } catch (Exception e) {
                log("PAIR_FAILED: " + e.getMessage());
            }
        }).start();
    }

    void pollOnce() {
        savePrefs();
        new Thread(() -> {
            try {
                if (token().length() == 0) {
                    log("اضغط ربط الجهاز أولاً");
                    return;
                }

                JSONObject q = getJson(base() + "/api/queue/poll", token());
                JSONArray jobs = q.optJSONArray("jobs");
                int count = jobs == null ? 0 : jobs.length();
                log("POLL_COUNT=" + count);

                if (count <= 0) return;

                JSONObject job = jobs.getJSONObject(0);
                String id = job.getString("id");
                String method = job.getString("method");
                String phone = job.optString("phone", "");
                String message = job.optString("message", "");

                log("JOB=" + id);
                log("METHOD=" + method);

                if ("whatsapp_apk".equals(method)) {
                    prefs.edit()
                            .putString("pending_job_id", id)
                            .putString("pending_server_url", base())
                            .putString("pending_token", token())
                            .apply();

                    openWhatsApp(phone, message);
                    log("WAIT_ACCESSIBILITY_SEND");
                } else {
                    markResult(id, "failed", "unsupported_now_" + method);
                }
            } catch (Exception e) {
                log("POLL_FAILED: " + e.getMessage());
            }
        }).start();
    }

    void openWhatsApp(String phone, String message) throws Exception {
        String clean = phone.replaceAll("[^0-9]", "");
        String text = URLEncoder.encode(message, "UTF-8").replace("+", "%20");
        Uri uri = Uri.parse("https://wa.me/" + clean + "?text=" + text);
        Intent in = new Intent(Intent.ACTION_VIEW, uri);
        in.setPackage("com.whatsapp");
        startActivity(in);
        log("WHATSAPP_OPENED=" + clean);
    }

    void markResult(String jobId, String status, String note) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("job_id", jobId);
                body.put("status", status);
                body.put("note", note);
                postJson(base() + "/api/queue/result", body, token());
                log("RESULT=" + status);
            } catch (Exception e) {
                log("RESULT_FAILED: " + e.getMessage());
            }
        }).start();
    }

    JSONObject getJson(String u, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        if (token != null) c.setRequestProperty("X-Device-Token", token);
        return new JSONObject(read(c));
    }

    JSONObject postJson(String u, JSONObject body, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (token != null) c.setRequestProperty("X-Device-Token", token);

        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();

        return new JSONObject(read(c));
    }

    String read(HttpURLConnection c) throws Exception {
        InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }
}
