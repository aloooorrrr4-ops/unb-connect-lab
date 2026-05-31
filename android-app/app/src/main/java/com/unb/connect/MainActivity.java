package com.unb.connect;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

public class MainActivity extends Activity {
    SharedPreferences prefs;

    EditText urlInput;
    EditText tokenInput;
    TextView statusView;

    CheckBox fullLinkBox;
    CheckBox smsBox;
    CheckBox waApkBox;
    CheckBox callBox;
    CheckBox waWebBox;

    final int DARK = Color.rgb(15, 23, 42);
    final int CARD_BORDER = Color.rgb(216, 222, 233);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("unb_connect", MODE_PRIVATE);
        ensureDevice();

        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.SEND_SMS
            }, 20);
        }

        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs != null) buildUi();
    }

    void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(10), dp(10), dp(10), dp(10));
        page.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(page);

        page.addView(header());

        if (!isAccessibilityEnabled()) {
            page.addView(permissionGate());
            page.addView(footer());
            setContentView(scroll);
            return;
        }

        page.addView(systemLinkCard());
        page.addView(actionsCard());
        page.addView(methodsTableCard());
        page.addView(whatsappWebSessionCard());
        page.addView(logCard());
        page.addView(footer());

        setContentView(scroll);
    }

    View header() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setGravity(Gravity.CENTER);
        h.setPadding(dp(12), dp(18), dp(12), dp(18));
        h.setBackgroundColor(DARK);

        TextView brand = tv("هيدرا", 30, Typeface.BOLD, Color.WHITE);
        brand.setGravity(Gravity.CENTER);
        h.addView(brand);

        TextView sub = tv("النظام العالمي هيدرا قوة | UNB Connect APK", 14, Typeface.NORMAL, Color.rgb(209, 213, 219));
        sub.setGravity(Gravity.CENTER);
        h.addView(sub);

        TextView mode = tv("الوضع الحالي: التحكم من Termux", 13, Typeface.BOLD, Color.rgb(187, 247, 208));
        mode.setGravity(Gravity.CENTER);
        h.addView(mode);

        return h;
    }

    View permissionGate() {
        LinearLayout c = card();
        c.setBackgroundColor(Color.rgb(255, 247, 237));

        TextView title = tv("مطلوب تفعيل إذن الوصول", 21, Typeface.BOLD, Color.rgb(17, 24, 39));
        title.setGravity(Gravity.RIGHT);
        c.addView(title);

        TextView note = tv("بدون Accessibility لا تظهر إعدادات الربط ولا يعمل واتساب APK.", 16, Typeface.NORMAL, Color.rgb(55, 65, 81));
        note.setGravity(Gravity.RIGHT);
        c.addView(note);

        TextView badge = badge("غير مفعل", false);
        c.addView(badge);

        c.addView(btn("فتح إعدادات Accessibility", Color.rgb(22, 163, 74), v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }));

        c.addView(btn("تحديث الحالة", Color.rgb(31, 41, 55), v -> buildUi()));

        c.addView(btn("إغلاق", Color.rgb(220, 38, 38), v -> finish()));

        return c;
    }

    View systemLinkCard() {
        LinearLayout c = card();

        TextView title = tv("ربط النظام كامل", 20, Typeface.BOLD, Color.rgb(17, 24, 39));
        c.addView(title);

        TextView note = tv("حاليًا الربط من Termux. لاحقًا نستبدله برابط النظام الحقيقي.", 15, Typeface.NORMAL, Color.rgb(55, 65, 81));
        c.addView(note);

        fullLinkBox = new CheckBox(this);
        fullLinkBox.setText("تفعيل الربط الكامل مع Termux");
        fullLinkBox.setTextSize(16);
        fullLinkBox.setTypeface(Typeface.DEFAULT_BOLD);
        fullLinkBox.setChecked(prefs.getBoolean("full_link_enabled", false));
        fullLinkBox.setOnCheckedChangeListener((b, on) -> {
            prefs.edit().putBoolean("full_link_enabled", on).apply();
            applyFullLinkState(on);
        });
        c.addView(fullLinkBox);

        c.addView(label("رابط Termux الحالي"));
        urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setText(prefs.getString("server_url", "http://127.0.0.1:3108"));
        urlInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        c.addView(urlInput);

        c.addView(label("Device Token"));
        tokenInput = new EditText(this);
        tokenInput.setSingleLine(true);
        tokenInput.setText(prefs.getString("token", ""));
        tokenInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        c.addView(tokenInput);

        c.addView(btn("حفظ الرابط والتوكن", Color.rgb(31, 41, 55), v -> saveSettings()));

        c.addView(btn("ربط الجهاز مع Termux", Color.rgb(22, 163, 74), v -> {
            saveSettings();
            pairDevice();
        }));

        return c;
    }

    View actionsCard() {
        LinearLayout c = card();

        TextView title = tv("أوامر التشغيل", 20, Typeface.BOLD, Color.rgb(17, 24, 39));
        c.addView(title);

        c.addView(btn("سحب وتنفيذ طلب واحد", Color.rgb(37, 99, 235), v -> {
            saveSettings();
            pollOne();
        }));

        c.addView(btn("تنظيف طلب واتساب المعلق", Color.rgb(107, 114, 128), v -> {
            prefs.edit()
                    .remove("pending_job_id")
                    .remove("pending_server_url")
                    .remove("pending_token")
                    .apply();
            log("تم تنظيف طلب واتساب المعلق");
        }));

        c.addView(btn("نسخ حالة الربط", Color.rgb(161, 98, 7), v -> copyText(buildTableText())));

        return c;
    }

    View methodsTableCard() {
        LinearLayout c = card();

        TextView title = tv("جدول الربط الأفقي - مثل Excel", 20, Typeface.BOLD, Color.rgb(17, 24, 39));
        c.addView(title);

        TextView note = tv("كل صف يمثل طريقة إرسال مستقلة. الرابط والتوكن هنا خاصان بربط التطبيق مع Termux فقط.", 14, Typeface.NORMAL, Color.rgb(55, 65, 81));
        c.addView(note);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        TableLayout table = new TableLayout(this);
        table.setStretchAllColumns(false);

        TableRow head = new TableRow(this);
        head.addView(th("القسم"));
        head.addView(th("مفعل"));
        head.addView(th("رابط النظام/Termux"));
        head.addView(th("توكن النظام"));
        head.addView(th("حالة النظام"));
        head.addView(th("حالة التطبيق"));
        head.addView(th("إجراء"));
        table.addView(head);

        smsBox = row(table, "SMS APK", "sms_apk", "جاهز", "لا يحتاج QR", "اختبار SMS");
        waApkBox = row(table, "WhatsApp APK", "whatsapp_apk", "جاهز", "Accessibility مفعل", "اختبار واتساب");
        callBox = row(table, "Call APK", "call_apk", "جاهز", "إذن الاتصال", "اختبار اتصال");
        waWebBox = row(table, "WhatsApp Web", "whatsapp_web", "جاهز من Termux", "جلسة واتساب تحتاج QR", "فتح QR");

        hsv.addView(table);
        c.addView(hsv);

        c.addView(btn("نسخ الجدول كامل", Color.rgb(31, 41, 55), v -> copyText(buildTableText())));

        applyFullLinkState(prefs.getBoolean("full_link_enabled", false));

        return c;
    }

    CheckBox row(TableLayout table, String name, String method, String systemStatus, String appStatus, String actionText) {
        TableRow r = new TableRow(this);

        r.addView(td(name, true));

        CheckBox cb = new CheckBox(this);
        cb.setChecked(prefs.getBoolean(method + "_enabled", true));
        cb.setOnCheckedChangeListener((b, on) -> prefs.edit().putBoolean(method + "_enabled", on).apply());
        r.addView(cb);

        r.addView(td(base() + "/api/" + method, false));
        r.addView(td(token().length() == 0 ? "بدون توكن حاليًا" : token(), false));
        r.addView(td(systemStatus, false));
        r.addView(td(appStatus, false));

        Button act = new Button(this);
        act.setText(actionText);
        act.setAllCaps(false);
        act.setOnClickListener(v -> {
            if ("whatsapp_web".equals(method)) {
                openWhatsAppWebQr();
            } else {
                createTestJob(method);
            }
        });
        r.addView(act);

        table.addView(r);
        return cb;
    }

    View whatsappWebSessionCard() {
        LinearLayout c = card();

        TextView title = tv("جلسة WhatsApp Web داخل التطبيق", 20, Typeface.BOLD, Color.rgb(17, 24, 39));
        c.addView(title);

        TextView note = tv("هذا القسم لا يحتاج رابط ولا توكن. الرابط والتوكن للنظام فقط. هنا نربط التطبيق نفسه مع واتساب ويب عبر QR.", 15, Typeface.NORMAL, Color.rgb(55, 65, 81));
        c.addView(note);

        c.addView(badge("واتساب ويب: يحتاج QR أو جلسة محفوظة", false));

        c.addView(btn("فتح WhatsApp Web / إظهار QR", Color.rgb(22, 163, 74), v -> openWhatsAppWebQr()));

        c.addView(btn("سحب طلب WhatsApp Web من Termux", Color.rgb(37, 99, 235), v -> {
            saveSettings();
            createTestJob("whatsapp_web");
        }));

        return c;
    }

    View logCard() {
        LinearLayout c = card();
        TextView title = tv("الحالة", 18, Typeface.BOLD, Color.rgb(17, 24, 39));
        c.addView(title);

        statusView = tv("جاهز", 14, Typeface.NORMAL, Color.rgb(55, 65, 81));
        statusView.setGravity(Gravity.RIGHT);
        c.addView(statusView);
        return c;
    }

    View footer() {
        TextView f = tv("HYDRA POWER · UNB CONNECT · TERMUX MODE", 13, Typeface.BOLD, Color.rgb(107, 114, 128));
        f.setGravity(Gravity.CENTER);
        f.setPadding(0, dp(14), 0, dp(14));
        return f;
    }

    void applyFullLinkState(boolean on) {
        if (smsBox != null) {
            smsBox.setChecked(on || prefs.getBoolean("sms_apk_enabled", true));
            waApkBox.setChecked(on || prefs.getBoolean("whatsapp_apk_enabled", true));
            callBox.setChecked(on || prefs.getBoolean("call_apk_enabled", true));
            waWebBox.setChecked(on || prefs.getBoolean("whatsapp_web_enabled", true));

            smsBox.setEnabled(!on);
            waApkBox.setEnabled(!on);
            callBox.setEnabled(!on);
            waWebBox.setEnabled(!on);
        }

        if (on) {
            prefs.edit()
                    .putBoolean("sms_apk_enabled", true)
                    .putBoolean("whatsapp_apk_enabled", true)
                    .putBoolean("call_apk_enabled", true)
                    .putBoolean("whatsapp_web_enabled", true)
                    .apply();
            log("الربط الكامل مع Termux مفعل");
        }
    }

    void saveSettings() {
        String u = urlInput == null ? "http://127.0.0.1:3108" : urlInput.getText().toString().trim();
        String t = tokenInput == null ? "" : tokenInput.getText().toString().trim();

        if (u.length() == 0) u = "http://127.0.0.1:3108";
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);

        prefs.edit()
                .putString("server_url", u)
                .putString("base_url", u)
                .putString("token", t)
                .putString("device_token", t)
                .apply();

        log("تم حفظ رابط Termux والتوكن");
    }

    String base() {
        String u = prefs.getString("server_url", "http://127.0.0.1:3108");
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    String token() {
        return prefs.getString("token", prefs.getString("device_token", ""));
    }

    String deviceId() {
        return prefs.getString("device_id", "");
    }

    void ensureDevice() {
        String id = prefs.getString("device_id", "");
        if (id.length() == 0) {
            id = "HYDRA_" + System.currentTimeMillis();
            prefs.edit()
                    .putString("device_id", id)
                    .putString("deviceId", id)
                    .apply();
        }
    }

    boolean isMethodEnabled(String method) {
        if (prefs.getBoolean("full_link_enabled", false)) return true;
        return prefs.getBoolean(method + "_enabled", true);
    }

    void pairDevice() {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("device_id", deviceId());
                body.put("name", "HYDRA UNB Connect APK");
                body.put("source", "termux");

                String res = httpPost(base() + "/api/device/pair", body.toString());
                runOnUiThread(() -> log("ربط Termux: " + res));
            } catch (Exception e) {
                runOnUiThread(() -> log("فشل الربط: " + e.getMessage()));
            }
        }).start();
    }

    void pollOne() {
        new Thread(() -> {
            try {
                JSONObject job = null;

                try {
                    String res = httpGet(base() + "/api/queue/poll?device_id=" + enc(deviceId()));
                    job = parseJob(res);
                } catch (Exception ignored) {}

                if (job == null) {
                    JSONObject body = new JSONObject();
                    body.put("device_id", deviceId());
                    String res = httpPost(base() + "/api/queue/poll", body.toString());
                    job = parseJob(res);
                }

                if (job == null) {
                    runOnUiThread(() -> log("لا يوجد طلب في الطابور"));
                    return;
                }

                JSONObject finalJob = job;
                runOnUiThread(() -> processJob(finalJob));

            } catch (Exception e) {
                runOnUiThread(() -> log("فشل السحب: " + e.getMessage()));
            }
        }).start();
    }

    JSONObject parseJob(String res) throws Exception {
        JSONObject o = new JSONObject(res);

        if (o.has("job") && !o.isNull("job")) {
            Object j = o.get("job");
            if (j instanceof JSONObject) return (JSONObject) j;
        }

        if (o.has("jobs")) {
            JSONArray arr = o.optJSONArray("jobs");
            if (arr != null && arr.length() > 0) return arr.getJSONObject(0);
        }

        if (o.has("id") && o.has("method")) return o;

        return null;
    }

    void processJob(JSONObject job) {
        try {
            String id = job.optString("id", job.optString("job_id", ""));
            String method = job.optString("method", "");
            String phone = job.optString("phone", "");
            String message = job.optString("message", "");

            if (id.length() == 0 || method.length() == 0) {
                log("طلب غير صالح");
                return;
            }

            if (!isMethodEnabled(method)) {
                markResult(id, "failed", "method_disabled_in_hydra_ui");
                return;
            }

            log("تنفيذ: " + method + " / " + phone);

            if ("whatsapp_apk".equals(method)) {
                prefs.edit()
                        .putString("pending_job_id", id)
                        .putString("pending_server_url", base())
                        .putString("pending_token", token())
                        .apply();

                openWhatsApp(phone, message);
                log("تم فتح WhatsApp APK وينتظر Accessibility");
                return;
            }

            if ("sms_apk".equals(method)) {
                sendSms(phone, message);
                markResult(id, "sent", "sms_sent_by_android_app");
                return;
            }

            if ("call_apk".equals(method)) {
                openCall(phone);
                markResult(id, "sent", "call_opened_by_android_app");
                return;
            }

            if ("whatsapp_web".equals(method)) {
                Intent webIntent = new Intent(this, WhatsAppWebActivity.class);
                webIntent.putExtra("job_id", id);
                webIntent.putExtra("phone", phone);
                webIntent.putExtra("message", message);
                webIntent.putExtra("server_url", base());
                webIntent.putExtra("token", token());
                startActivity(webIntent);
                log("فتح WhatsApp Web داخل التطبيق");
                return;
            }

            markResult(id, "failed", "unsupported_method_" + method);

        } catch (Exception e) {
            log("خطأ تنفيذ الطلب: " + e.getMessage());
        }
    }

    void createTestJob(String method) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("method", method);
                body.put("phone", "967782971812");
                body.put("message", "اختبار " + method + " من هيدرا " + System.currentTimeMillis());
                body.put("institution_id", 1);

                String res = httpPost(base() + "/api/test/send", body.toString());
                runOnUiThread(() -> {
                    log("تم إنشاء طلب اختبار: " + method);
                    pollOne();
                });
            } catch (Exception e) {
                runOnUiThread(() -> log("فشل إنشاء اختبار: " + e.getMessage()));
            }
        }).start();
    }

    void openWhatsAppWebQr() {
        Intent webIntent = new Intent(this, WhatsAppWebActivity.class);
        startActivity(webIntent);
        log("فتح WhatsApp Web / QR");
    }

    void openWhatsApp(String phone, String message) {
        try {
            String clean = phone.replaceAll("[^0-9]", "");
            String url = "https://api.whatsapp.com/send?phone=" + clean + "&text=" + enc(message);
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.setPackage("com.whatsapp");
            startActivity(i);
        } catch (Exception e) {
            try {
                String clean = phone.replaceAll("[^0-9]", "");
                String url = "https://api.whatsapp.com/send?phone=" + clean + "&text=" + enc(message);
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ex) {
                log("فشل فتح واتساب: " + ex.getMessage());
            }
        }
    }

    void sendSms(String phone, String message) {
        try {
            if (Build.VERSION.SDK_INT >= 23 &&
                    checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.SEND_SMS}, 21);
                throw new RuntimeException("SMS_PERMISSION_REQUIRED");
            }

            String clean = phone.replaceAll("[^0-9+]", "");
            SmsManager sms = SmsManager.getDefault();
            ArrayList<String> parts = sms.divideMessage(message);
            sms.sendMultipartTextMessage(clean, null, parts, null, null);
            log("تم إرسال SMS: " + clean);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void openCall(String phone) {
        String clean = phone.replaceAll("[^0-9+]", "");
        try {
            Intent call = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + clean));
            if (Build.VERSION.SDK_INT < 23 ||
                    checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                startActivity(call);
                log("تم فتح الاتصال: " + clean);
                return;
            }
        } catch (Exception e) {
            log("CALL_FAILED: " + e.getMessage());
        }

        Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + clean));
        startActivity(dial);
    }

    void markResult(String jobId, String status, String note) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("job_id", jobId);
                body.put("id", jobId);
                body.put("device_id", deviceId());
                body.put("token", token());
                body.put("status", status);
                body.put("result_note", note);
                body.put("note", note);

                String res = httpPost(base() + "/api/queue/result", body.toString());
                runOnUiThread(() -> log("نتيجة الطلب: " + status + " / " + note));
            } catch (Exception e) {
                runOnUiThread(() -> log("فشل رفع النتيجة: " + e.getMessage()));
            }
        }).start();
    }

    String httpGet(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        addHeaders(c);
        return read(c);
    }

    String httpPost(String u, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        addHeaders(c);

        OutputStream os = c.getOutputStream();
        os.write(body.getBytes("UTF-8"));
        os.close();

        return read(c);
    }

    void addHeaders(HttpURLConnection c) {
        String t = token();
        if (t.length() > 0) {
            c.setRequestProperty("Authorization", "Bearer " + t);
            c.setRequestProperty("X-UNB-Token", t);
        }
        c.setRequestProperty("X-UNB-Device", deviceId());
    }

    String read(HttpURLConnection c) throws Exception {
        BufferedReader br;
        if (c.getResponseCode() >= 400 && c.getErrorStream() != null) {
            br = new BufferedReader(new InputStreamReader(c.getErrorStream()));
        } else {
            br = new BufferedReader(new InputStreamReader(c.getInputStream()));
        }

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        c.disconnect();
        return sb.toString();
    }

    boolean isAccessibilityEnabled() {
        try {
            String enabled = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            if (enabled == null) return false;

            String expected1 = getPackageName() + "/" + UnbAccessibilityService.class.getName();
            String expected2 = getPackageName() + "/." + "UnbAccessibilityService";

            String e = enabled.toLowerCase();
            return e.contains(expected1.toLowerCase()) || e.contains(expected2.toLowerCase());
        } catch (Exception e) {
            return false;
        }
    }

    String buildTableText() {
        String url = base();
        String t = token().length() == 0 ? "NO_TOKEN" : token();

        return "القسم\tمفعل\tرابط Termux\tتوكن\tحالة النظام\tحالة التطبيق\n" +
                "SMS APK\t" + isMethodEnabled("sms_apk") + "\t" + url + "/api/sms_apk\t" + t + "\tجاهز\tلا يحتاج QR\n" +
                "WhatsApp APK\t" + isMethodEnabled("whatsapp_apk") + "\t" + url + "/api/whatsapp_apk\t" + t + "\tجاهز\tAccessibility\n" +
                "Call APK\t" + isMethodEnabled("call_apk") + "\t" + url + "/api/call_apk\t" + t + "\tجاهز\tإذن الاتصال\n" +
                "WhatsApp Web\t" + isMethodEnabled("whatsapp_web") + "\t" + url + "/api/whatsapp_web\t" + t + "\tجاهز من Termux\tجلسة QR داخل التطبيق";
    }

    void copyText(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("hydra_connect", text));
        log("تم النسخ");
    }

    TextView label(String s) {
        TextView v = tv(s, 14, Typeface.BOLD, Color.rgb(55, 65, 81));
        v.setPadding(0, dp(8), 0, dp(3));
        return v;
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, dp(10), 0, dp(10));
        c.setLayoutParams(lp);
        c.setBackgroundColor(Color.WHITE);
        return c;
    }

    TextView tv(String s, int size, int style, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTypeface(Typeface.DEFAULT, style);
        v.setTextColor(color);
        v.setPadding(dp(4), dp(4), dp(4), dp(4));
        return v;
    }

    TextView th(String s) {
        TextView v = td(s, true);
        v.setBackgroundColor(Color.rgb(241, 245, 249));
        return v;
    }

    TextView td(String s, boolean bold) {
        TextView v = tv(s, 14, bold ? Typeface.BOLD : Typeface.NORMAL, Color.rgb(17, 24, 39));
        v.setPadding(dp(10), dp(10), dp(10), dp(10));
        v.setSingleLine(false);
        return v;
    }

    TextView badge(String s, boolean ok) {
        TextView v = tv(s, 14, Typeface.BOLD, ok ? Color.rgb(22, 101, 52) : Color.rgb(153, 27, 27));
        v.setText(ok ? "✅ " + s : "❌ " + s);
        return v;
    }

    Button btn(String s, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(color);
        b.setOnClickListener(l);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);

        return b;
    }

    void log(String s) {
        if (statusView != null) statusView.setText(s);
    }

    String enc(String s) throws Exception {
        return URLEncoder.encode(s == null ? "" : s, "UTF-8");
    }

    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
