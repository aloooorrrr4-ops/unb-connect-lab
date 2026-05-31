package com.unb.connect;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.CookieManager;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class WhatsAppWebActivity extends Activity {
    WebView web;
    TextView statusView;
    Handler handler = new Handler(Looper.getMainLooper());
    SharedPreferences prefs;

    String jobId = "";
    String phone = "";
    String message = "";
    String serverUrl = "";
    String token = "";
    String deviceId = "";

    int attempts = 0;
    boolean resultSent = false;

    static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        prefs = getSharedPreferences("unb_connect", MODE_PRIVATE);

        Intent i = getIntent();
        jobId = safe(i.getStringExtra("job_id"));
        phone = cleanPhone(i.getStringExtra("phone"));
        message = safe(i.getStringExtra("message"));
        serverUrl = safe(i.getStringExtra("server_url"));
        token = safe(i.getStringExtra("token"));

        if (serverUrl.length() == 0) {
            serverUrl = prefs.getString("server_url", prefs.getString("base_url", "http://127.0.0.1:3108"));
        }
        if (token.length() == 0) {
            token = prefs.getString("token", prefs.getString("device_token", ""));
        }
        deviceId = prefs.getString("device_id", prefs.getString("deviceId", ""));

        buildUi();
        setupWebView();

        if (phone.length() > 0 && message.length() > 0 && jobId.length() > 0) {
            setStatus("طلب WhatsApp Web جاهز: " + phone);
            loadSendPage();
        } else {
            setStatus("افتح واتساب ويب واربط الجهاز بالـ QR");
            web.loadUrl("https://web.whatsapp.com/");
        }
    }

    String safe(String v) {
        return v == null ? "" : v;
    }

    String cleanPhone(String v) {
        return safe(v).replaceAll("[^0-9]", "");
    }

    void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        statusView = new TextView(this);
        statusView.setTextSize(15);
        statusView.setPadding(16, 14, 16, 14);
        root.addView(statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        Button open = new Button(this);
        open.setText("فتح واتساب ويب / QR");
        open.setOnClickListener(v -> {
            attempts = 0;
            web.loadUrl("https://web.whatsapp.com/");
            setStatus("فتح صفحة الربط...");
        });
        buttons.addView(open, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button send = new Button(this);
        send.setText("إرسال الطلب الحالي");
        send.setOnClickListener(v -> {
            attempts = 0;
            resultSent = false;
            loadSendPage();
        });
        buttons.addView(send, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        root.addView(buttons);

        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);
    }

    void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setUserAgentString(DESKTOP_UA);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            cm.setAcceptThirdPartyCookies(web, true);
        }

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                setStatus("تحميل: " + shortUrl(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                setStatus("تم تحميل الصفحة. إذا ظهر QR اربطه من واتساب.");
                if (jobId.length() > 0 && phone.length() > 0 && message.length() > 0 && !resultSent) {
                    scheduleAttempt(3500);
                }
            }
        });
    }

    String shortUrl(String u) {
        if (u == null) return "";
        return u.length() > 70 ? u.substring(0, 70) + "..." : u;
    }

    void loadSendPage() {
        try {
            if (phone.length() == 0 || message.length() == 0) {
                setStatus("لا يوجد رقم أو رسالة.");
                return;
            }

            String encoded = URLEncoder.encode(message, "UTF-8");
            String url = "https://web.whatsapp.com/send?phone=" + phone + "&text=" + encoded + "&app_absent=0";
            setStatus("فتح محادثة WhatsApp Web للرقم: " + phone);
            web.loadUrl(url);
            scheduleAttempt(4500);
        } catch (Exception e) {
            setStatus("خطأ تجهيز رابط الإرسال: " + e.getMessage());
        }
    }

    void scheduleAttempt(long delayMs) {
        handler.postDelayed(() -> attemptSend(), delayMs);
    }

    void attemptSend() {
        if (resultSent) return;

        attempts++;
        setStatus("محاولة إرسال WhatsApp Web رقم " + attempts + " ...");

        final String js =
                "(function(){\n" +
                "  function vis(e){ if(!e) return false; var r=e.getBoundingClientRect(); return r.width>0 && r.height>0; }\n" +
                "  function btnFrom(e){ return e.closest('button') || e.closest('[role=\"button\"]') || e; }\n" +
                "  var selectors=[\n" +
                "    'span[data-icon=\"send\"]', '[data-icon=\"send\"]',\n" +
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

        web.evaluateJavascript(js, value -> {
            String v = String.valueOf(value);

            if (v.contains("CLICKED_SEND")) {
                resultSent = true;
                setStatus("تم الضغط على زر إرسال WhatsApp Web.");
                markResult("sent", "whatsapp_web_sent_by_webview");
                return;
            }

            if (attempts < 120) {
                setStatus("لم يظهر زر الإرسال بعد. إذا ظهر QR اربطه. النتيجة: " + v);
                scheduleAttempt(2500);
            } else {
                setStatus("فشل: لم يتم العثور على زر إرسال WhatsApp Web.");
                markResult("failed", "whatsapp_web_send_button_not_found");
            }
        });
    }

    void markResult(String status, String note) {
        if (jobId.length() == 0 || serverUrl.length() == 0) {
            setStatus("لا يمكن رفع النتيجة: job/server مفقود.");
            return;
        }

        new Thread(() -> {
            try {
                URL url = new URL(serverUrl + "/api/queue/result");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                if (token.length() > 0) {
                    c.setRequestProperty("Authorization", "Bearer " + token);
                    c.setRequestProperty("X-UNB-Token", token);
                }

                JSONObject body = new JSONObject();
                body.put("queue_id", jobId); body.put("id", jobId);
                body.put("id", jobId);
                body.put("device_id", deviceId);
                body.put("token", token);
                body.put("status", status);
                body.put("result_note", note);
                body.put("note", note);

                OutputStream os = c.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();

                int code = c.getResponseCode();
                runOnUiThread(() -> setStatus("تم رفع نتيجة WhatsApp Web: " + status + " / HTTP " + code));
                c.disconnect();
            } catch (Exception e) {
                runOnUiThread(() -> setStatus("فشل رفع النتيجة: " + e.getMessage()));
            }
        }).start();
    }

    void setStatus(String s) {
        statusView.setText(s);
    }
}
