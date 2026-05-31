package com.unb.connect;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.CookieManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class MainActivity extends Activity {
    Handler handler = new Handler(Looper.getMainLooper());
    Handler ackHandler = new Handler(Looper.getMainLooper());

    SharedPreferences prefs;

    EditText urlInput;
    EditText tokenInput;
    TextView statusView;
    TextView miniLogView;

    CheckBox fullLinkBox;
    CheckBox callBox;
    CheckBox smsBox;
    CheckBox waApkBox;
    CheckBox waWebBox;

    WebView waWebView;
    LinearLayout waWebHolder;

    JSONArray waWebQueue = new JSONArray();
    int waWebIndex = 0;
    int waWebAttempts = 0;
    boolean waWebResultSent = false;
    boolean waWebQueueRunning = false;
    boolean waWebLoadedOnce = false;

    String waWebJobId = "";
    String waWebPhone = "";
    String waWebMessage = "";

    final int DARK = Color.rgb(15, 23, 42);
    final int GREEN = Color.rgb(13, 148, 136);
    final int BLUE = Color.rgb(37, 99, 235);
    final int RED = Color.rgb(185, 28, 28);
    final int GRAY = Color.rgb(55, 65, 81);
    final int ORANGE = Color.rgb(180, 83, 9);

    static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

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
        setupEmbeddedWhatsAppWeb();

        // تحميل WhatsApp Web مرة واحدة عند فتح/تسجيل التطبيق.
        waWebView.loadUrl("https://web.whatsapp.com/");
        waWebLoadedOnce = true;
        logMini("WhatsApp Web يتحمل الآن مرة واحدة داخل الصفحة. بعد QR يبقى جاهز.");
    }

    void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(10), dp(10), dp(10), dp(10));
        page.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(page);

        page.addView(header());
        page.addView(systemLinkCard());
        page.addView(methodsCard());
        page.addView(nativeActionsCard());
        page.addView(whatsappCard());

        // بدل السجل الأسود: WhatsApp Web داخل نفس الصفحة.
        page.addView(embeddedWhatsAppWebCard());

        setContentView(scroll);
    }

    View header() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setGravity(Gravity.CENTER);
        h.setPadding(dp(14), dp(18), dp(14), dp(18));
        h.setBackgroundColor(DARK);

        TextView brand = tv("UNB Connect APK Hydra", 22, Typeface.BOLD, Color.WHITE);
        brand.setGravity(Gravity.CENTER);
        h.addView(brand);

        TextView sub = tv("تنفيذ الطابور من التطبيق: SMS / Call / WhatsApp APK / WhatsApp Web", 13, Typeface.NORMAL, Color.rgb(209, 213, 219));
        sub.setGravity(Gravity.CENTER);
        h.addView(sub);

        statusView = tv("الوضع: Embedded WhatsApp Web Main Page V1", 13, Typeface.BOLD, Color.rgb(187, 247, 208));
        statusView.setGravity(Gravity.CENTER);
        h.addView(statusView);

        return h;
    }

    View systemLinkCard() {
        LinearLayout c = card();

        c.addView(title("1) ربط النظام / طابور الإرسال"));
        c.addView(note("WhatsApp Web نفسه داخل التطبيق. Termux مؤقتًا فقط مصدر الطابور والـ ACK."));

        fullLinkBox = new CheckBox(this);
        fullLinkBox.setText("تفعيل الربط الكامل");
        fullLinkBox.setTextSize(16);
        fullLinkBox.setTypeface(Typeface.DEFAULT_BOLD);
        fullLinkBox.setChecked(prefs.getBoolean("full_link_enabled", true));
        fullLinkBox.setOnCheckedChangeListener((b, on) -> prefs.edit().putBoolean("full_link_enabled", on).apply());
        c.addView(fullLinkBox);

        c.addView(label("System URL"));
        urlInput = input(prefs.getString("server_url", "http://127.0.0.1:3108"));
        c.addView(urlInput);

        c.addView(label("Web Token"));
        tokenInput = input(prefs.getString("token", ""));
        c.addView(tokenInput);

        c.addView(btn("حفظ الرابط والتوكن", GRAY, v -> saveSettings()));
        c.addView(btn("تحميل التوكن من الطابور", BLUE, v -> loadTokenFromServer()));
        c.addView(btn("فحص الاتصال / Heartbeat", GREEN, v -> heartbeat()));

        return c;
    }

    View methodsCard() {
        LinearLayout c = card();
        c.addView(title("2) طرق الجهاز"));

        callBox = check("call_apk - اتصال من الجوال", prefs.getBoolean("m_call_apk", true));
        smsBox = check("sms_apk - SMS من الجوال", prefs.getBoolean("m_sms_apk", true));
        waApkBox = check("whatsapp_apk - واتساب APK", prefs.getBoolean("m_whatsapp_apk", true));
        waWebBox = check("whatsapp_web - واتساب Web داخل نفس الصفحة", prefs.getBoolean("m_whatsapp_web", true));

        c.addView(callBox);
        c.addView(smsBox);
        c.addView(waApkBox);
        c.addView(waWebBox);

        c.addView(btn("حفظ الطرق", GRAY, v -> saveSettings()));
        return c;
    }

    View nativeActionsCard() {
        LinearLayout c = card();
        c.addView(title("3) تنفيذ الطابور"));

        c.addView(btn("سحب وتنفيذ طلب واحد", BLUE, v -> pollAndExecute(1)));
        c.addView(btn("سحب وتنفيذ كل الطلبات المتاحة", ORANGE, v -> pollAndExecute(20)));
        c.addView(btn("إضافة طابور اختبار مؤقت", GRAY, v -> enqueueTemp()));
        c.addView(btn("عرض حالة الطابور", GREEN, v -> listQueue()));
        c.addView(btn("تنظيف طلب واتساب المعلق", RED, v -> clearPending()));
        c.addView(btn("نسخ حالة الربط", ORANGE, v -> copyText(buildStatusText())));

        return c;
    }

    View whatsappCard() {
        LinearLayout c = card();
        c.addView(title("4) WhatsApp"));

        c.addView(note("WhatsApp APK يستخدم Accessibility. WhatsApp Web محمّل تحت داخل نفس الصفحة، لا يفتح Activity ثانية."));

        c.addView(btn("فتح إعدادات Accessibility", ORANGE, v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));

        c.addView(btn("إظهار / تحديث WhatsApp Web QR", GREEN, v -> {
            showWhatsAppWeb();
            waWebView.loadUrl("https://web.whatsapp.com/");
            logMini("إظهار WhatsApp Web للربط أو المراجعة.");
        }));

        c.addView(btn("إخفاء شاشة واتساب Web", GRAY, v -> {
            hideWhatsAppWeb();
            logMini("تم إخفاء شاشة WhatsApp Web، والجلسة باقية داخل التطبيق.");
        }));

        c.addView(btn("سحب وتنفيذ طابور WhatsApp Web", BLUE, v -> {
            saveSettings();
            showWhatsAppWeb();
            pullWhatsAppWebQueueAndRun();
        }));

        return c;
    }

    View embeddedWhatsAppWebCard() {
        LinearLayout c = card();

        c.addView(title("WhatsApp Web داخل التطبيق"));
        c.addView(note("هذا المكان بدل السجل. الصفحة تتحمل مرة واحدة، وكل إرسال يحدث نفس WebView فقط."));

        miniLogView = tv("جاهز...", 12, Typeface.NORMAL, Color.rgb(31, 41, 55));
        miniLogView.setGravity(Gravity.RIGHT);
        c.addView(miniLogView);

        waWebHolder = new LinearLayout(this);
        waWebHolder.setOrientation(LinearLayout.VERTICAL);
        waWebHolder.setBackgroundColor(Color.WHITE);

        waWebView = new WebView(this);
        waWebHolder.addView(waWebView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        c.addView(waWebHolder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(520)
        ));

        return c;
    }

    void setupEmbeddedWhatsAppWeb() {
        WebSettings s = waWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setUserAgentString(DESKTOP_UA);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) {
            cm.setAcceptThirdPartyCookies(waWebView, true);
        }

        waWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                logMini("WhatsApp Web تحميل: " + shortUrl(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                logMini("WhatsApp Web جاهز داخل الصفحة.");
                if (waWebJobId.length() > 0 && waWebPhone.length() > 0 && waWebMessage.length() > 0 && !waWebResultSent) {
                    scheduleWhatsAppWebFastAttempts();
                }
            }
        });
    }

    void showWhatsAppWeb() {
        runOnUiThread(() -> {
            if (waWebHolder == null) return;
            waWebHolder.setAlpha(1.0f);
            waWebHolder.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(520)
            ));
            setStatus("WhatsApp Web ظاهر داخل الصفحة.");
        });
    }

    void hideWhatsAppWeb() {
        runOnUiThread(() -> {
            if (waWebHolder == null) return;
            waWebHolder.setAlpha(0.08f);
            waWebHolder.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(60)
            ));
            setStatus("WhatsApp Web مخفي جزئيًا وجاهز.");
        });
    }

    void pullWhatsAppWebQueueAndRun() {
        waWebQueueRunning = true;
        logMini("جاري سحب قائمة whatsapp_web من الطابور...");

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("token", token());
                body.put("limit", 20);
                JSONArray methods = new JSONArray();
                methods.put("whatsapp_web");
                body.put("methods", methods);

                String res = post("/api/queue/pull", body);
                JSONObject j = new JSONObject(res);
                JSONArray jobs = j.optJSONArray("jobs");

                if (jobs == null || jobs.length() == 0) {
                    logMiniUi("لا توجد طلبات WhatsApp Web pending.\n" + res);
                    return;
                }

                waWebQueue = jobs;
                waWebIndex = 0;

                logMiniUi("تم سحب " + jobs.length() + " طلب WhatsApp Web. التنفيذ داخل نفس الصفحة.");
                runOnUiThread(() -> runNextWhatsAppWebJob());

            } catch (Exception e) {
                logMiniUi("فشل سحب WhatsApp Web: " + e.getMessage());
            }
        }).start();
    }

    void runNextWhatsAppWebJob() {
        if (!waWebQueueRunning) return;

        try {
            if (waWebIndex >= waWebQueue.length()) {
                waWebQueueRunning = false;
                setStatus("انتهى تنفيذ طابور WhatsApp Web.");
                logMini("اكتمل تنفيذ WhatsApp Web.");
                return;
            }

            JSONObject job = waWebQueue.getJSONObject(waWebIndex);
            waWebIndex++;

            waWebJobId = job.optString("id", job.optString("queue_id", ""));
            waWebPhone = cleanPhone(job.optString("phone", ""));
            waWebMessage = job.optString("body", job.optString("message", ""));

            waWebAttempts = 0;
            waWebResultSent = false;

            setStatus("WhatsApp Web: " + waWebIndex + "/" + waWebQueue.length() + " - " + waWebPhone);
            loadWhatsAppWebSendPage();

        } catch (Exception e) {
            logMini("خطأ تجهيز الطلب التالي: " + e.getMessage());
            handler.postDelayed(() -> runNextWhatsAppWebJob(), 900);
        }
    }

    void loadWhatsAppWebSendPage() {
        try {
            if (waWebPhone.length() == 0 || waWebMessage.length() == 0) {
                setStatus("لا يوجد رقم أو رسالة WhatsApp Web.");
                return;
            }

            String encoded = URLEncoder.encode(waWebMessage, "UTF-8");
            String url = "https://web.whatsapp.com/send?phone=" + waWebPhone + "&text=" + encoded + "&app_absent=0";

            logMini("تحديث نفس WhatsApp Web للمحادثة: " + waWebPhone);
            waWebView.loadUrl(url);

            scheduleWhatsAppWebFastAttempts();

        } catch (Exception e) {
            setStatus("خطأ تجهيز رابط WhatsApp Web: " + e.getMessage());
        }
    }

    void scheduleWhatsAppWebFastAttempts() {
        scheduleWhatsAppWebAttempt(700);
        scheduleWhatsAppWebAttempt(1400);
        scheduleWhatsAppWebAttempt(2300);
        scheduleWhatsAppWebAttempt(3500);
    }

    void scheduleWhatsAppWebAttempt(long delayMs) {
        handler.postDelayed(() -> attemptWhatsAppWebSend(), delayMs);
    }

    void attemptWhatsAppWebSend() {
        if (waWebResultSent) return;

        waWebAttempts++;
        setStatus("محاولة إرسال WhatsApp Web رقم " + waWebAttempts + " ...");

        final String js =
                "(function(){\n" +
                "  function vis(e){ if(!e) return false; var r=e.getBoundingClientRect(); return r.width>0 && r.height>0; }\n" +
                "  function btnFrom(e){ return e.closest('button') || e.closest('[role=\"button\"]') || e; }\n" +
                "  var selectors=[\n" +
                "    'span[data-icon=\"send\"]', '[data-icon=\"send\"]',\n" +
                "    '[data-testid=\"send\"]', '[data-icon=\"wds-ic-send-filled\"]',\n" +
                "    'button[aria-label*=\"Send\"]', 'div[aria-label*=\"Send\"]',\n" +
                "    'button[aria-label*=\"إرسال\"]', 'div[aria-label*=\"إرسال\"]'\n" +
                "  ];\n" +
                "  for(var s of selectors){\n" +
                "    var arr=[].slice.call(document.querySelectorAll(s));\n" +
                "    for(var el of arr){ var b=btnFrom(el); if(vis(b)){ b.click(); return 'CLICKED_SEND'; } }\n" +
                "  }\n" +
                "  var boxes=[].slice.call(document.querySelectorAll('div[contenteditable=\"true\"]')).filter(vis);\n" +
                "  return 'NO_SEND_BUTTON boxes=' + boxes.length + ' title=' + document.title;\n" +
                "})()";

        waWebView.evaluateJavascript(js, value -> {
            String v = String.valueOf(value);
            logMini("WA_WEB_MAIN_ATTEMPT_" + waWebAttempts + ": " + v);

            if (v.contains("CLICKED_SEND")) {
                waWebResultSent = true;
                setStatus("تم إرسال WhatsApp Web من نفس الصفحة.");
                markWhatsAppWebResult("sent", "whatsapp_web_sent_by_embedded_main_webview_v22");

                if (waWebQueueRunning) {
                    handler.postDelayed(() -> runNextWhatsAppWebJob(), 900);
                }
                return;
            }

            if (waWebAttempts < 12) {
                scheduleWhatsAppWebAttempt(900);
            } else {
                waWebResultSent = true;
                setStatus("فشل العثور على زر إرسال WhatsApp Web.");
                markWhatsAppWebResult("failed", "whatsapp_web_send_button_not_found_embedded_main_webview_v22");

                if (waWebQueueRunning) {
                    handler.postDelayed(() -> runNextWhatsAppWebJob(), 900);
                }
            }
        });
    }

    void markWhatsAppWebResult(String status, String note) {
        new Thread(() -> {
            try {
                if (waWebJobId.length() == 0) return;

                JSONObject body = new JSONObject();
                body.put("token", token());
                body.put("queue_id", waWebJobId);
                body.put("id", waWebJobId);
                body.put("status", status);
                body.put("error", note);

                String res = post("/api/queue/ack", body);
                logMiniUi("ACK WhatsApp Web: " + status + " / " + waWebJobId + "\n" + res);

            } catch (Exception e) {
                logMiniUi("فشل ACK WhatsApp Web: " + e.getMessage());
            }
        }).start();
    }

    void saveSettings() {
        String u = urlInput == null ? "" : urlInput.getText().toString().trim();
        String t = tokenInput == null ? "" : tokenInput.getText().toString().trim();

        if (u.length() == 0) u = "http://127.0.0.1:3108";

        prefs.edit()
                .putString("server_url", trimSlash(u))
                .putString("base_url", trimSlash(u))
                .putString("token", t)
                .putString("device_token", t)
                .putBoolean("full_link_enabled", fullLinkBox == null || fullLinkBox.isChecked())
                .putBoolean("m_call_apk", callBox == null || callBox.isChecked())
                .putBoolean("m_sms_apk", smsBox == null || smsBox.isChecked())
                .putBoolean("m_whatsapp_apk", waApkBox == null || waApkBox.isChecked())
                .putBoolean("m_whatsapp_web", waWebBox == null || waWebBox.isChecked())
                .apply();

        logMini("تم حفظ الرابط والتوكن والطرق.");
    }

    void loadTokenFromServer() {
        saveSettings();
        new Thread(() -> {
            try {
                JSONObject j = new JSONObject(get("/api/config"));
                String u = j.optString("system_url", baseUrl());
                String t = j.optString("web_token", token());

                prefs.edit()
                        .putString("server_url", trimSlash(u))
                        .putString("base_url", trimSlash(u))
                        .putString("token", t)
                        .putString("device_token", t)
                        .apply();

                runOnUiThread(() -> {
                    urlInput.setText(trimSlash(u));
                    tokenInput.setText(t);
                    logMini("تم تحميل التوكن.");
                });
            } catch (Exception e) {
                logMiniUi("فشل تحميل التوكن: " + e.getMessage());
            }
        }).start();
    }

    void heartbeat() {
        saveSettings();
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("token", token());
                body.put("device_id", deviceId());
                body.put("full_link_enabled", true);
                body.put("methods", selectedMethods());

                String res = post("/api/device/heartbeat", body);
                logMiniUi("Heartbeat OK:\n" + res);
            } catch (Exception e) {
                logMiniUi("Heartbeat FAILED: " + e.getMessage());
            }
        }).start();
    }

    void enqueueTemp() {
        saveSettings();
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("token", token());
                body.put("phone", "+967782971812");
                body.put("message", "اختبار Native من التطبيق");
                body.put("recipient_type", "customer");
                body.put("methods", selectedMethods());

                String res = post("/api/queue/enqueue", body);
                logMiniUi("تمت إضافة طابور اختبار:\n" + res);
            } catch (Exception e) {
                logMiniUi("فشل إضافة الطابور: " + e.getMessage());
            }
        }).start();
    }

    void listQueue() {
        saveSettings();
        new Thread(() -> {
            try {
                String res = get("/api/queue/list?token=" + enc(token()));
                logMiniUi("Queue:\n" + res);
            } catch (Exception e) {
                logMiniUi("فشل عرض الطابور: " + e.getMessage());
            }
        }).start();
    }

    void pollAndExecute(int limit) {
        saveSettings();
        if (!prefs.getBoolean("full_link_enabled", true)) {
            logMini("الربط الكامل غير مفعل.");
            return;
        }

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("token", token());
                body.put("limit", limit);
                body.put("methods", selectedMethods());

                String res = post("/api/queue/pull", body);
                JSONObject j = new JSONObject(res);
                JSONArray jobs = j.optJSONArray("jobs");

                if (jobs == null || jobs.length() == 0) {
                    logMiniUi("لا توجد طلبات pending.\n" + res);
                    return;
                }

                logMiniUi("تم سحب " + jobs.length() + " طلب.");

                JSONArray webJobs = new JSONArray();

                for (int i = 0; i < jobs.length(); i++) {
                    JSONObject job = jobs.getJSONObject(i);
                    String method = job.optString("delivery_method", "");

                    if (method.equals("whatsapp_web")) {
                        webJobs.put(job);
                    } else {
                        executeJob(job);
                        sleep(900);
                    }
                }

                if (webJobs.length() > 0) {
                    waWebQueue = webJobs;
                    waWebIndex = 0;
                    waWebQueueRunning = true;
                    runOnUiThread(() -> {
                        showWhatsAppWeb();
                        runNextWhatsAppWebJob();
                    });
                }

            } catch (Exception e) {
                logMiniUi("فشل السحب/التنفيذ: " + e.getMessage());
            }
        }).start();
    }

    void executeJob(JSONObject job) {
        try {
            String id = job.optString("id", job.optString("queue_id", ""));
            String method = job.optString("delivery_method", "");
            String phone = cleanPhone(job.optString("phone", ""));
            String message = job.optString("body", job.optString("message", ""));

            logMiniUi("تنفيذ: " + method + " / " + phone + " / " + id);

            if (method.equals("sms_apk")) {
                sendSms(phone, message);
                ack(id, "sent", "sms_sent_by_native");
                return;
            }

            if (method.equals("call_apk")) {
                makeCall(phone);
                ack(id, "sent", "call_started_by_native");
                return;
            }

            if (method.equals("whatsapp_apk")) {
                openWhatsAppApk(id, phone, message);
                return;
            }

            ack(id, "failed", "unsupported_method_" + method);
        } catch (Exception e) {
            logMiniUi("خطأ تنفيذ الطلب: " + e.getMessage());
        }
    }

    void ack(String id, String status, String error) {
        try {
            if (id == null || id.length() == 0) return;

            JSONObject body = new JSONObject();
            body.put("token", token());
            body.put("queue_id", id);
            body.put("id", id);
            body.put("status", status);
            body.put("error", error == null ? "" : error);

            String res = post("/api/queue/ack", body);
            logMiniUi("ACK " + id + " => " + status + "\n" + res);
        } catch (Exception e) {
            logMiniUi("فشل ACK: " + e.getMessage());
        }
    }

    void sendSms(String phone, String message) throws Exception {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.SEND_SMS}, 21);
            throw new Exception("SEND_SMS_PERMISSION_REQUIRED");
        }
        SmsManager.getDefault().sendTextMessage(phone, null, message, null, null);
    }

    void makeCall(String phone) throws Exception {
        Intent i;
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone));
        } else {
            i = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phone));
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    void openWhatsAppApk(String jobId, String phone, String message) throws Exception {
        prefs.edit()
                .putString("pending_job_id", jobId)
                .putString("pending_server_url", baseUrl())
                .putString("pending_token", token())
                .apply();

        String url = "https://wa.me/" + phone + "?text=" + enc(message);
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        i.setPackage("com.whatsapp");

        try {
            startActivity(i);
            logMiniUi("فتح WhatsApp APK. Accessibility سيضغط إرسال.");
            scheduleWhatsAppApkAckFallback(jobId);
        } catch (Exception e) {
            Intent b = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            b.setPackage("com.whatsapp.w4b");
            try {
                startActivity(b);
                logMiniUi("فتح WhatsApp Business.");
                scheduleWhatsAppApkAckFallback(jobId);
            } catch (Exception e2) {
                ack(jobId, "failed", "whatsapp_apk_not_found");
            }
        }
    }

    void scheduleWhatsAppApkAckFallback(String jobId) {
        ackHandler.postDelayed(() -> {
            new Thread(() -> {
                try {
                    if (jobId == null || jobId.length() == 0) return;

                    JSONObject body = new JSONObject();
                    body.put("token", token());
                    body.put("queue_id", jobId);
                    body.put("id", jobId);
                    body.put("status", "sent");
                    body.put("error", "whatsapp_apk_auto_ack_fallback_after_open_v22");

                    String res = post("/api/queue/ack", body);
                    prefs.edit()
                            .remove("pending_job_id")
                            .remove("pending_server_url")
                            .remove("pending_token")
                            .apply();

                    logMiniUi("WhatsApp APK Auto ACK fallback OK: " + jobId + "\n" + res);
                } catch (Exception e) {
                    logMiniUi("WhatsApp APK Auto ACK fallback failed: " + e.getMessage());
                }
            }).start();
        }, 14000);
    }

    void clearPending() {
        prefs.edit()
                .remove("pending_job_id")
                .remove("pending_server_url")
                .remove("pending_token")
                .apply();
        logMini("تم تنظيف طلب واتساب المعلق.");
    }

    JSONArray selectedMethods() {
        JSONArray a = new JSONArray();
        try {
            if (callBox == null || callBox.isChecked()) a.put("call_apk");
            if (smsBox == null || smsBox.isChecked()) a.put("sms_apk");
            if (waApkBox == null || waApkBox.isChecked()) a.put("whatsapp_apk");
            if (waWebBox == null || waWebBox.isChecked()) a.put("whatsapp_web");
        } catch (Exception ignored) {}
        return a;
    }

    String get(String path) throws Exception {
        URL url = new URL(baseUrl() + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setRequestMethod("GET");
        c.setRequestProperty("X-Web-Token", token());
        return readResponse(c);
    }

    String post(String path, JSONObject body) throws Exception {
        URL url = new URL(baseUrl() + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("X-Web-Token", token());

        byte[] bytes = body.toString().getBytes("UTF-8");
        OutputStream os = c.getOutputStream();
        os.write(bytes);
        os.close();

        return readResponse(c);
    }

    String readResponse(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        InputStream is = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append("\n");
        br.close();

        String res = sb.toString().trim();
        if (code < 200 || code >= 400) throw new Exception("HTTP_" + code + ": " + res);
        return res;
    }

    String baseUrl() {
        String u = prefs.getString("server_url", "http://127.0.0.1:3108");
        return trimSlash(u);
    }

    String token() {
        return prefs.getString("token", prefs.getString("device_token", ""));
    }

    String deviceId() {
        return prefs.getString("device_id", "hydra_android_" + System.currentTimeMillis());
    }

    void ensureDevice() {
        if (prefs.getString("device_id", "").length() == 0) {
            prefs.edit().putString("device_id", "hydra_android_" + System.currentTimeMillis()).apply();
        }
    }

    String cleanPhone(String v) {
        if (v == null) return "";
        return v.replaceAll("[^0-9]", "");
    }

    String enc(String s) {
        try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
        catch (Exception e) { return ""; }
    }

    String trimSlash(String u) {
        if (u == null || u.length() == 0) return "http://127.0.0.1:3108";
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    String shortUrl(String u) {
        if (u == null) return "";
        return u.length() > 70 ? u.substring(0, 70) + "..." : u;
    }

    String buildStatusText() {
        return "UNB Connect Embedded WhatsApp Web\n" +
                "URL=" + baseUrl() + "\n" +
                "TOKEN=" + token() + "\n" +
                "FULL_LINK=" + prefs.getBoolean("full_link_enabled", true) + "\n" +
                "METHODS=" + selectedMethods().toString() + "\n";
    }

    void copyText(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("UNB Connect", s));
        logMini("تم النسخ.");
    }

    void setStatus(String s) {
        runOnUiThread(() -> {
            if (statusView != null) statusView.setText(s);
        });
    }

    void logMini(String s) {
        if (miniLogView != null) miniLogView.setText(s);
    }

    void logMiniUi(String s) {
        runOnUiThread(() -> logMini(s));
    }

    void sleep(long ms) {
        try { Thread.sleep(ms); } catch (Exception ignored) {}
    }

    TextView tv(String s, int sp, int style, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTypeface(Typeface.DEFAULT, style);
        v.setTextColor(color);
        v.setPadding(dp(4), dp(5), dp(4), dp(5));
        return v;
    }

    TextView title(String s) {
        TextView v = tv(s, 20, Typeface.BOLD, Color.rgb(17, 24, 39));
        v.setGravity(Gravity.RIGHT);
        return v;
    }

    TextView note(String s) {
        TextView v = tv(s, 14, Typeface.NORMAL, Color.rgb(75, 85, 99));
        v.setGravity(Gravity.RIGHT);
        return v;
    }

    TextView label(String s) {
        TextView v = tv(s, 14, Typeface.BOLD, Color.rgb(55, 65, 81));
        v.setGravity(Gravity.RIGHT);
        return v;
    }

    EditText input(String s) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(s);
        e.setTextDirection(View.TEXT_DIRECTION_LTR);
        e.setPadding(dp(8), dp(8), dp(8), dp(8));
        return e;
    }

    CheckBox check(String s, boolean on) {
        CheckBox b = new CheckBox(this);
        b.setText(s);
        b.setTextSize(15);
        b.setChecked(on);
        return b;
    }

    Button btn(String s, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackgroundColor(color);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        b.setPadding(dp(8), dp(10), dp(8), dp(10));
        return b;
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(12), dp(12), dp(12));
        c.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, dp(10), 0, dp(10));
        c.setLayoutParams(lp);
        return c;
    }

    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
