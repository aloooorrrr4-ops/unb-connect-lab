package com.unb.connect;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.CookieManager;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class WhatsAppWebActivity extends Activity {
    WebView web;
    LinearLayout webHolder;
    TextView statusView;
    TextView logView;
    Handler handler = new Handler(Looper.getMainLooper());
    SharedPreferences prefs;

    String jobId = "";
    String phone = "";
    String message = "";
    String serverUrl = "";
    String token = "";
    String deviceId = "";

    JSONArray queue = new JSONArray();
    int queueIndex = 0;
    int attempts = 0;
    boolean resultSent = false;
    boolean queueRunning = false;
    boolean webVisible = false;

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

        web.loadUrl("https://web.whatsapp.com/");
        setWebVisible(false, "WhatsApp Web محمّل تحت ومخفي. أظهره فقط للـ QR أو عند السحب.");

        if (phone.length() > 0 && message.length() > 0 && jobId.length() > 0) {
            setWebVisible(true, "تنفيذ طلب مباشر...");
            loadSendPage();
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
        root.setLayoutDirection(LinearLayout.LAYOUT_DIRECTION_RTL);

        statusView = new TextView(this);
        statusView.setTextSize(15);
        statusView.setTypeface(Typeface.DEFAULT_BOLD);
        statusView.setPadding(16, 14, 16, 14);
        statusView.setGravity(Gravity.RIGHT);
        statusView.setBackgroundColor(Color.rgb(15, 23, 42));
        statusView.setTextColor(Color.WHITE);
        root.addView(statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout buttons1 = new LinearLayout(this);
        buttons1.setOrientation(LinearLayout.HORIZONTAL);

        Button qr = btn("إظهار QR / WhatsApp Web");
        qr.setOnClickListener(v -> {
            queueRunning = false;
            setWebVisible(true, "إظهار WhatsApp Web للربط أو المراجعة.");
            web.loadUrl("https://web.whatsapp.com/");
        });
        buttons1.addView(qr, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button hide = btn("إخفاء شاشة واتساب");
        hide.setOnClickListener(v -> setWebVisible(false, "تم إخفاء WhatsApp Web. سيبقى جاهزًا تحت."));
        buttons1.addView(hide, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        root.addView(buttons1);

        LinearLayout buttons2 = new LinearLayout(this);
        buttons2.setOrientation(LinearLayout.HORIZONTAL);

        Button pull = btn("سحب وتنفيذ طابور WhatsApp Web");
        pull.setOnClickListener(v -> {
            setWebVisible(true, "بدء سحب وتنفيذ طابور WhatsApp Web...");
            pullQueueAndRun();
        });
        buttons2.addView(pull, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button stop = btn("إيقاف");
        stop.setOnClickListener(v -> {
            queueRunning = false;
            setStatus("تم إيقاف تنفيذ طابور WhatsApp Web.");
        });
        buttons2.addView(stop, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        root.addView(buttons2);

        logView = new TextView(this);
        logView.setTextSize(12);
        logView.setTextColor(Color.rgb(31, 41, 55));
        logView.setPadding(14, 8, 14, 8);
        logView.setGravity(Gravity.RIGHT);
        logView.setText("جاهز. شاشة واتساب مخفية تحت.");
        root.addView(logView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        webHolder = new LinearLayout(this);
        webHolder.setOrientation(LinearLayout.VERTICAL);

        web = new WebView(this);
        webHolder.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        root.addView(webHolder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(2)
        ));

        setContentView(root);
    }

    Button btn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        return b;
    }

    void setWebVisible(boolean visible, String msg) {
        runOnUiThread(() -> {
            webVisible = visible;
            LinearLayout.LayoutParams lp;
            if (visible) {
                lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
                webHolder.setAlpha(1.0f);
            } else {
                lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2));
                webHolder.setAlpha(0.02f);
            }
            webHolder.setLayoutParams(lp);
            setStatus(msg);
        });
    }

    void setupWebView() {
        WebSettings s = web.getSettings();
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
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            cm.setAcceptThirdPartyCookies(web, true);
        }

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                setLog("تحميل: " + shortUrl(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                setLog("WhatsApp Web محمّل. إذا كان QR مربوطًا اضغط سحب وتنفيذ.");
                if (jobId.length() > 0 && phone.length() > 0 && message.length() > 0 && !resultSent) {
                    scheduleFastAttempts();
                }
            }
        });
    }

    void pullQueueAndRun() {
        queueRunning = true;
        setLog("جاري سحب قائمة whatsapp_web من الطابور...");
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("token", token);
                body.put("limit", 20);
                JSONArray methods = new JSONArray();
                methods.put("whatsapp_web");
                body.put("methods", methods);

                String res = post("/api/queue/pull", body);
                JSONObject j = new JSONObject(res);
                JSONArray jobs = j.optJSONArray("jobs");

                if (jobs == null || jobs.length() == 0) {
                    setLogUi("لا توجد طلبات WhatsApp Web pending.\n" + res);
                    return;
                }

                queue = jobs;
                queueIndex = 0;

                setLogUi("تم سحب " + jobs.length() + " طلب. سيتم التنفيذ سريعًا داخل نفس WebView.");
                runOnUiThread(() -> runNextJob());

            } catch (Exception e) {
                setLogUi("فشل سحب طابور WhatsApp Web: " + e.getMessage());
            }
        }).start();
    }

    void runNextJob() {
        if (!queueRunning) return;

        try {
            if (queueIndex >= queue.length()) {
                setStatus("انتهى تنفيذ طابور WhatsApp Web.");
                setLog("اكتمل التنفيذ. سيتم إخفاء شاشة واتساب بعد ثانيتين.");
                queueRunning = false;
                handler.postDelayed(() -> setWebVisible(false, "WhatsApp Web مخفي وجاهز تحت."), 2000);
                return;
            }

            JSONObject job = queue.getJSONObject(queueIndex);
            queueIndex++;

            jobId = job.optString("id", job.optString("queue_id", ""));
            phone = cleanPhone(job.optString("phone", ""));
            message = job.optString("body", job.optString("message", ""));

            attempts = 0;
            resultSent = false;

            setStatus("تنفيذ WhatsApp Web: " + queueIndex + "/" + queue.length() + " - " + phone);
            loadSendPage();

        } catch (Exception e) {
            setLog("خطأ تجهيز الطلب التالي: " + e.getMessage());
            handler.postDelayed(() -> runNextJob(), 700);
        }
    }

    String shortUrl(String u) {
        if (u == null) return "";
        return u.length() > 70 ? u.substring(0, 70) + "...";
    }

    void loadSendPage() {
        try {
            if (phone.length() == 0 || message.length() == 0) {
                setStatus("لا يوجد رقم أو رسالة.");
                return;
            }

            String encoded = URLEncoder.encode(message, "UTF-8");
            String url = "https://web.whatsapp.com/send?phone=" + phone + "&text=" + encoded + "&app_absent=0";
            setLog("فتح المحادثة داخل نفس WebView...");
            web.loadUrl(url);
            scheduleFastAttempts();

        } catch (Exception e) {
            setStatus("خطأ تجهيز رابط الإرسال: " + e.getMessage());
        }
    }

    void scheduleFastAttempts() {
        scheduleAttempt(700);
        scheduleAttempt(1400);
        scheduleAttempt(2300);
        scheduleAttempt(3500);
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

        web.evaluateJavascript(js, value -> {
            String v = String.valueOf(value);
            setLog("WA_WEB_FAST_ATTEMPT_" + attempts + ": " + v);

            if (v.contains("CLICKED_SEND")) {
                resultSent = true;
                setStatus("تم إرسال WhatsApp Web.");
                markResult("sent", "whatsapp_web_sent_by_hidden_fast_runner_v19");

                if (queueRunning) {
                    handler.postDelayed(() -> runNextJob(), 900);
                }
                return;
            }

            if (attempts < 12) {
                scheduleAttempt(900);
            } else {
                resultSent = true;
                setStatus("فشل العثور على زر إرسال WhatsApp Web.");
                markResult("failed", "whatsapp_web_send_button_not_found_hidden_fast_runner_v19");

                if (queueRunning) {
                    handler.postDelayed(() -> runNextJob(), 900);
                }
            }
        });
    }

    void markResult(String status, String note) {
        new Thread(() -> {
            try {
                if (jobId.length() == 0) return;

                JSONObject body = new JSONObject();
                body.put("token", token);
                body.put("queue_id", jobId);
                body.put("id", jobId);
                body.put("status", status);
                body.put("error", note);

                String res = post("/api/queue/ack", body);
                setLogUi("ACK WhatsApp Web: " + status + " / " + jobId + "\n" + res);

            } catch (Exception e) {
                setLogUi("فشل ACK WhatsApp Web: " + e.getMessage());
            }
        }).start();
    }

    String post(String path, JSONObject body) throws Exception {
        String base = serverUrl == null || serverUrl.length() == 0 ? "http://127.0.0.1:3108" : serverUrl;
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);

        URL url = new URL(base + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("X-Web-Token", token);

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

    void setStatus(String s) {
        runOnUiThread(() -> statusView.setText(s));
    }

    void setLog(String s) {
        if (logView != null) logView.setText(s);
    }

    void setLogUi(String s) {
        runOnUiThread(() -> setLog(s));
    }

    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
