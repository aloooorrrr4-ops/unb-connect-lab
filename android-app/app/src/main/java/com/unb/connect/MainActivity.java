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

    ScrollView controlsScroll;
    LinearLayout webPanel;
    LinearLayout waWebHolder;

    EditText urlInput;
    EditText tokenInput;
    EditText waSearchInput;

    TextView statusView;
    TextView miniLogView;
    TextView linkSummaryView;

    CheckBox fullLinkBox;
    CheckBox callBox;
    CheckBox smsBox;
    CheckBox waApkBox;
    CheckBox waWebBox;

    WebView waWebView;

    JSONArray waWebQueue = new JSONArray();
    int waWebIndex = 0;
    int waWebAttempts = 0;
    int waWebOpenAttempts = 0; int waWebChatReadyAttempts = 0;
    boolean waWebResultSent = false;
    boolean waWebQueueRunning = false;
    boolean waWebLoadedOnce = false;

    String waWebJobId = "";
    String waWebPhone = "";
    String waWebMessage = "";
    String currentWaWebPhone = "";

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

        // UNB_WA_WEB_CONNECTED_SEARCH_SEND_V28
        // WhatsApp Web متصل بالشاشة الرئيسية ويتحدث عند فتح التطبيق.
        // الإرسال لا يستخدم رابط send، بل يبحث داخل نفس الصفحة ثم يرسل.
        currentWaWebPhone = "";
        waWebLoadedOnce = true;
        logMini("WhatsApp Web متصل بالثلث الأخير ويتحدث عند فتح التطبيق.");
        waWebView.loadUrl("https://web.whatsapp.com/");
    }

    void buildUi() {
        // UNB_WA_WEB_SINGLE_SCROLL_BOTTOM_SAFE_V31
        // صفحة واحدة فقط: الرئيسية فوق، و WhatsApp Web في آخر الصفحة.
        controlsScroll = new ScrollView(this);
        controlsScroll.setFillViewport(false);
        controlsScroll.setBackgroundColor(Color.rgb(245, 247, 250));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(8), dp(8), dp(8), dp(18));
        controls.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        controlsScroll.addView(controls, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        controls.addView(header());
        controls.addView(systemLinkCompact());
        controls.addView(methodConnectionCard());
        controls.addView(methodsCompact());
        controls.addView(actionsCompact());
        controls.addView(whatsappToolbar());

        webPanel = new LinearLayout(this);
        webPanel.setOrientation(LinearLayout.VERTICAL);
        webPanel.setBackgroundColor(Color.WHITE);
        webPanel.setPadding(0, 0, 0, 0);

        TextView bottomTitle = tv("WhatsApp Web - آخر الصفحة", 18, Typeface.BOLD, Color.rgb(17, 24, 39));
        bottomTitle.setGravity(Gravity.CENTER);
        bottomTitle.setPadding(dp(8), dp(10), dp(8), dp(6));
        webPanel.addView(bottomTitle);

        waWebHolder = new LinearLayout(this);
        waWebHolder.setOrientation(LinearLayout.VERTICAL);
        waWebHolder.setBackgroundColor(Color.WHITE);

        waWebView = new WebView(this);
        // UNB_WA_WEB_DESKTOP_VIEW_SETTINGS_V36
        waWebView.getSettings().setJavaScriptEnabled(true);
        waWebView.getSettings().setDomStorageEnabled(true);
        waWebView.getSettings().setDatabaseEnabled(true);
        waWebView.getSettings().setUseWideViewPort(true);
        waWebView.getSettings().setLoadWithOverviewMode(true);
        waWebView.getSettings().setSupportZoom(true);
        waWebView.getSettings().setBuiltInZoomControls(true);
        waWebView.getSettings().setDisplayZoomControls(false);
        waWebView.getSettings().setTextZoom(85);
        waWebView.getSettings().setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        waWebView.setInitialScale(90);

        waWebHolder.addView(waWebView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        webPanel.addView(waWebHolder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(680)
        ));

        controls.addView(webPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(controlsScroll);
    }

    View header() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setGravity(Gravity.CENTER);
        h.setPadding(dp(10), dp(10), dp(10), dp(10));
        h.setBackgroundColor(DARK);

        TextView brand = tv("UNB Connect", 21, Typeface.BOLD, Color.WHITE);
        brand.setGravity(Gravity.CENTER);
        h.addView(brand);

        statusView = tv("وضع WhatsApp Web السهل داخل الصفحة", 13, Typeface.BOLD, Color.rgb(187, 247, 208));
        statusView.setGravity(Gravity.CENTER);
        h.addView(statusView);

        return h;
    }

    View systemLinkCompact() {
        LinearLayout c = card();
        c.addView(title("ربط النظام"));

        fullLinkBox = new CheckBox(this);
        fullLinkBox.setText("تفعيل الربط الكامل");
        fullLinkBox.setTextSize(14);
        fullLinkBox.setChecked(prefs.getBoolean("full_link_enabled", true));
        c.addView(fullLinkBox);

        urlInput = input(prefs.getString("server_url", "http://127.0.0.1:3108"));
        tokenInput = input(prefs.getString("token", ""));

        c.addView(label("System URL"));
        c.addView(urlInput);
        c.addView(label("Web Token"));
        c.addView(tokenInput);

        LinearLayout row = row();
        row.addView(btn("حفظ", GRAY, v -> saveSettings()), weight());
        row.addView(btn("Heartbeat", GREEN, v -> heartbeat()), weight());
        c.addView(row);

        return c;
    }

    View methodConnectionCard() {
        LinearLayout c = card();
        c.addView(title("رابط وتوكن كل طريقة"));

        linkSummaryView = tv("", 12, Typeface.NORMAL, Color.rgb(31, 41, 55));
        linkSummaryView.setTextDirection(View.TEXT_DIRECTION_LTR);
        linkSummaryView.setGravity(Gravity.LEFT);
        c.addView(linkSummaryView);

        refreshLinkSummary();
        return c;
    }

    View methodsCompact() {
        LinearLayout c = card();
        c.addView(title("الطرق"));

        callBox = check("Call", prefs.getBoolean("m_call_apk", true));
        smsBox = check("SMS", prefs.getBoolean("m_sms_apk", true));
        waApkBox = check("WA APK", prefs.getBoolean("m_whatsapp_apk", true));
        waWebBox = check("WA Web", prefs.getBoolean("m_whatsapp_web", true));

        LinearLayout r1 = row();
        r1.addView(callBox, weight());
        r1.addView(smsBox, weight());
        c.addView(r1);

        LinearLayout r2 = row();
        r2.addView(waApkBox, weight());
        r2.addView(waWebBox, weight());
        c.addView(r2);

        return c;
    }

    View actionsCompact() {
        LinearLayout c = card();
        c.addView(title("تنفيذ"));

        LinearLayout r1 = row();
        r1.addView(btn("سحب طلب", BLUE, v -> pollAndExecute(1)), weight());
        r1.addView(btn("سحب الكل", ORANGE, v -> pollAndExecute(20)), weight());
        c.addView(r1);

        LinearLayout r2 = row();
        r2.addView(btn("اختبار", GRAY, v -> enqueueTemp()), weight());
        r2.addView(btn("الطابور", GREEN, v -> listQueue()), weight());
        c.addView(r2);

        LinearLayout r3 = row();
        r3.addView(btn("Accessibility", ORANGE, v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))), weight());
        r3.addView(btn("تكبير واتساب", BLUE, v -> focusWhatsAppWeb()), weight());
        c.addView(r3);

        return c;
    }

    View whatsappToolbar() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(4), dp(4), dp(4), dp(4));
        box.setBackgroundColor(Color.rgb(248, 250, 252));

        TextView t = tv("WhatsApp Web", 18, Typeface.BOLD, Color.rgb(17, 24, 39));
        t.setGravity(Gravity.RIGHT);
        box.addView(t);

        miniLogView = tv("الصفحة غير محملة بعد. اضغط QR أو سحب الطابور.", 12, Typeface.NORMAL, Color.rgb(55, 65, 81));
        miniLogView.setGravity(Gravity.RIGHT);
        box.addView(miniLogView);

        waSearchInput = input("");
        waSearchInput.setHint("بحث داخل واتساب أو رقم");
        box.addView(waSearchInput);

        LinearLayout r1 = row();
        r1.addView(btn("QR / تحميل", GREEN, v -> {
            showWhatsAppWeb();
            ensureWhatsAppWebLoaded();
        }), weight());
        r1.addView(btn("سحب WhatsApp Web", BLUE, v -> {
            saveSettings();
            showWhatsAppWeb();
            ensureWhatsAppWebLoaded();
            pullWhatsAppWebQueueAndRun();
        }), weight());
        box.addView(r1);

        LinearLayout r2 = row();
        r2.addView(btn("بحث", ORANGE, v -> openWhatsAppWebSearch()), weight());
        r2.addView(btn("فتح رقم", BLUE, v -> openPhoneFromSearch()), weight());
        r2.addView(btn("رجوع", GRAY, v -> backWhatsAppWeb()), weight());
        box.addView(r2);

        LinearLayout r3 = row();
        r3.addView(btn("تحديث", GREEN, v -> reloadWhatsAppWeb()), weight());
        r3.addView(btn("تكبير", BLUE, v -> focusWhatsAppWeb()), weight());
        r3.addView(btn("إظهار التحكم", GRAY, v -> showControls()), weight());
        box.addView(r3);

        return box;
    }

    void setupEmbeddedWhatsAppWeb() {
        // UNB_WA_WEB_VIEWPORT_FIT_V28
        waWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        waWebView.setHorizontalScrollBarEnabled(true);
        waWebView.setVerticalScrollBarEnabled(true);
        waWebView.setInitialScale(80);

        WebSettings s = waWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setUserAgentString(DESKTOP_UA);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setTextZoom(90);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) {
            cm.setAcceptThirdPartyCookies(waWebView, true);
        }

        waWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                logMini("تحميل: " + shortUrl(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                logMini("WhatsApp Web جاهز داخل الصفحة.");
                applyWhatsAppWebDesktopViewportV36();
                if (false) { /* V34B no blind send on page finish */ }
            }
        });
    }

    void ensureWhatsAppWebLoaded() {
        runOnUiThread(() -> {
            if (waWebView == null) return;
            if (!waWebLoadedOnce) {
                waWebLoadedOnce = true;
                logMini("تحميل WhatsApp Web لأول مرة...");
                waWebView.loadUrl("https://web.whatsapp.com/");
            } else {
                logMini("WhatsApp Web محمّل وجاهز.");
            }
        });
    }

    void showWhatsAppWeb() {
        runOnUiThread(() -> {
            if (waWebHolder == null) return;
            waWebHolder.setAlpha(1.0f);
            setStatus("WhatsApp Web ظاهر.");
        });
    }

    void focusWhatsAppWeb() {
        runOnUiThread(() -> {
            ensureWhatsAppWebLoaded();
            if (controlsScroll != null && webPanel != null) {
                controlsScroll.post(() -> controlsScroll.smoothScrollTo(0, webPanel.getTop()));
            }
            setStatus("WhatsApp Web في آخر الصفحة وجاهز.");
        });
    }

    void showControls() {
        runOnUiThread(() -> {
            if (controlsScroll != null) {
                controlsScroll.post(() -> controlsScroll.smoothScrollTo(0, 0));
            }
            setStatus("تم الرجوع لأعلى الشاشة الرئيسية.");
        });
    }

    void reloadWhatsAppWeb() {
        ensureWhatsAppWebLoaded();
        runOnUiThread(() -> waWebView.reload());
    }

    void backWhatsAppWeb() {
        runOnUiThread(() -> {
            if (waWebView.canGoBack()) waWebView.goBack();
            else logMini("لا يوجد رجوع داخل WhatsApp Web.");
        });
    }

    void openPhoneFromSearch() {
        ensureWhatsAppWebLoaded();

        String raw = waSearchInput == null ? "" : waSearchInput.getText().toString();
        String digits = cleanPhone(raw);

        if (digits.length() < 7) {
            logMini("اكتب رقمًا صحيحًا في خانة البحث.");
            return;
        }

        waWebPhone = digits;
        waWebMessage = "";
        currentWaWebPhone = "";

        String quotedPhone = JSONObject.quote(digits);

        String js =
                "(function(phone){\n" +
                "  function vis(e){ if(!e) return false; var r=e.getBoundingClientRect(); return r.width>0 && r.height>0; }\n" +
                "  function put(el,v){ try{ el.focus(); document.execCommand('selectAll',false,null); document.execCommand('insertText',false,v); el.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:v})); }catch(e){} }\n" +
                "  var searchSelectors=['span[data-icon=\"search\"]','[data-icon=\"search\"]','button[aria-label*=\"Search\"]','div[aria-label*=\"Search\"]','button[aria-label*=\"بحث\"]','div[aria-label*=\"بحث\"]'];\n" +
                "  for(var s of searchSelectors){ var a=[].slice.call(document.querySelectorAll(s)); for(var el of a){ if(vis(el)){ (el.closest('button')||el.closest('[role=\"button\"]')||el).click(); break; } } }\n" +
                "  setTimeout(function(){ var all=[].slice.call(document.querySelectorAll('div[contenteditable=\"true\"], input')).filter(vis); if(all.length){ put(all[0], phone); } },500);\n" +
                "  return 'PHONE_SEARCH_STARTED_SAME_PAGE';\n" +
                "})(" + quotedPhone + ")";

        waWebView.evaluateJavascript(js, value -> logMini("فتح رقم عبر بحث نفس الصفحة: " + value));
    }

    void openWhatsAppWebSearch() {
        ensureWhatsAppWebLoaded();

        String q = waSearchInput == null ? "" : waSearchInput.getText().toString();
        String quoted = JSONObject.quote(q);

        String js =
                "(function(q){\n" +
                " function vis(e){ if(!e) return false; var r=e.getBoundingClientRect(); return r.width>0 && r.height>0; }\n" +
                " function put(el,v){ try{ el.focus(); document.execCommand('selectAll',false,null); document.execCommand('insertText',false,v); el.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:v})); }catch(e){} }\n" +
                " var selectors=['button[aria-label*=\"Search\"]','div[aria-label*=\"Search\"]','span[data-icon=\"search\"]','[data-icon=\"search\"]','button[aria-label*=\"بحث\"]','div[aria-label*=\"بحث\"]'];\n" +
                " for(var s of selectors){ var a=[].slice.call(document.querySelectorAll(s)); for(var el of a){ if(vis(el)){ el.click(); break; } } }\n" +
                " setTimeout(function(){\n" +
                "   var all=[].slice.call(document.querySelectorAll('div[contenteditable=\"true\"], input')).filter(vis);\n" +
                "   if(all.length && q){ put(all[0], q); }\n" +
                " },500);\n" +
                " return 'SEARCH_OPENED';\n" +
                "})(" + quoted + ")";

        waWebView.evaluateJavascript(js, value -> logMini("بحث WhatsApp Web: " + value));
    }

    void pullWhatsAppWebQueueAndRun() {
        waWebQueueRunning = true;
        logMini("جاري سحب قائمة whatsapp_web من الطابور...");

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("token", token());
                body.put("limit", 1);
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

                logMiniUi("تم سحب " + jobs.length() + " طلب WhatsApp Web.");
                runOnUiThread(() -> {
                    focusWhatsAppWeb();
                    runNextWhatsAppWebJob();
                });

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

            showWhatsAppWeb();
            ensureWhatsAppWebLoaded();

            waWebAttempts = 0;
            waWebOpenAttempts = 0;

            // UNB_WA_WEB_CONNECTED_SEARCH_SEND_V28
            // لا نفتح رابط send?phone نهائيًا.
            // إذا نفس المحادثة مفتوحة: إرسال مباشر من صندوق الرسالة.
            // إذا الرقم تغيّر: نستخدم بحث WhatsApp Web داخل نفس الصفحة.
            if (currentWaWebPhone.equals(waWebPhone)) {
                logMini("نفس المحادثة مفتوحة. إرسال مباشر بدون فتح صفحة.");
                fillAndSendCurrentChat();
            } else {
                logMini("الرقم تغيّر. سيتم البحث داخل نفس WhatsApp Web بدون فتح رابط جديد.");
                openChatBySearchThenSend();
            }

        } catch (Exception e) {
            setStatus("خطأ تجهيز WhatsApp Web: " + e.getMessage());
        }
    }

    void openChatBySearchThenSend() {
        // UNB_WA_WEB_DIRECT_TEMPLATE_V38
        if (waWebResultSent) return;

        waWebOpenAttempts++;
        waWebChatReadyAttempts = 0;

        String phoneClean = waWebPhone == null ? "" : waWebPhone.replaceAll("[^0-9]", "");
        if (phoneClean.length() == 0) {
            waWebResultSent = true;
            markWhatsAppWebResult("failed", "whatsapp_web_direct_template_empty_phone_v37");
            return;
        }

        String msg = waWebMessage == null ? "" : waWebMessage;
        String encodedMessage = "";
        try {
            encodedMessage = java.net.URLEncoder.encode(msg, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            encodedMessage = "";
        }

        String directUrl = "https://web.whatsapp.com/send?phone=" + cleanPhone + "&text=" + encodedMessage + "&app_absent=0&unb_direct_ts=" + System.currentTimeMillis();
        if (encodedMessage.length() > 0) {
            directUrl += "&text=" + encodedMessage;
        }

        String safeUrl = directUrl.replace("&", "&amp;").replace("\"", "&quot;");
        String safePhone = phoneClean.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        String safeMsg = msg.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        String jsUrl = directUrl.replace("\\", "\\\\").replace("'", "\\'");

        String html =
            "<!doctype html><html dir='rtl' lang='ar'><head>" +
            "<meta charset='utf-8'>" +
            "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
            "<style>" +
            "body{font-family:sans-serif;background:#f3f4f6;margin:0;padding:18px;color:#111827;}" +
            ".card{background:white;border-radius:18px;padding:18px;box-shadow:0 8px 28px rgba(0,0,0,.10);}" +
            "h2{margin:0 0 14px;font-size:22px;}" +
            "label{display:block;margin:12px 0 6px;font-weight:bold;color:#374151;}" +
            "input,textarea{width:100%;box-sizing:border-box;border:1px solid #d1d5db;border-radius:12px;padding:12px;font-size:16px;direction:ltr;}" +
            "textarea{height:120px;direction:rtl;}" +
            ".btn{display:block;text-align:center;margin-top:16px;padding:14px;border-radius:14px;background:#059669;color:white;text-decoration:none;font-weight:bold;font-size:17px;}" +
            ".mini{font-size:13px;color:#6b7280;margin-top:12px;direction:ltr;word-break:break-all;}" +
            "</style></head><body>" +
            "<div class='card'>" +
            "<h2>قالب إرسال WhatsApp Web مباشر V38</h2>" +
            "<label>الرقم</label>" +
            "<input value='" + safePhone + "' readonly>" +
            "<label>نص الرسالة</label>" +
            "<textarea readonly>" + safeMsg + "</textarea>" +
            "<a class='btn' href='" + safeUrl + "'>فتح الدردشة مباشرة</a>" +
            "<div class='mini'>" + safeUrl + "</div>" +
            "</div>" +
            "<script>" +
            "setTimeout(function(){ location.href='" + jsUrl + "'; }, 1200);" +
            "</script>" +
            "</body></html>";

        setStatus("فتح قالب WhatsApp Web مباشر للرقم " + phoneClean + " ...");
        logMini("WA_WEB_DIRECT_TEMPLATE_V37_" + waWebOpenAttempts + ": " + phoneClean);

        if (waWebView != null) {
            waWebView.loadDataWithBaseURL("https://web.whatsapp.com/", html, "text/html", "UTF-8", null);
        }

        handler.postDelayed(() -> {
            applyWhatsAppWebDesktopViewportV36();
            verifyWhatsAppWebChatReadyThenSend();
        }, 12500);
    }

    

void applyWhatsAppWebDesktopViewportV36() {
    // UNB_WA_WEB_DESKTOP_VIEWPORT_JS_V36
    if (waWebView == null) return;

    final String js = "(function(){\n" +
        " try{\n" +
        "   var m=document.querySelector('meta[name=viewport]');\n" +
        "   if(!m){ m=document.createElement('meta'); m.name='viewport'; document.head.appendChild(m); }\n" +
        "   m.setAttribute('content','width=980, initial-scale=0.85, maximum-scale=2.0, user-scalable=yes');\n" +
        "   document.documentElement.style.minWidth='980px';\n" +
        "   document.body.style.minWidth='980px';\n" +
        "   document.documentElement.style.zoom='1';\n" +
        "   return 'DESKTOP_VIEWPORT_APPLIED_V38';\n" +
        " }catch(e){ return 'DESKTOP_VIEWPORT_ERROR_V36:' + e.message; }\n" +
        "})()";

    waWebView.evaluateJavascript(js, result -> {
        logMini("WA_WEB_DESKTOP_VIEWPORT_V36: " + result);
    });
}

void verifyWhatsAppWebChatReadyThenSend() {
    // UNB_WA_WEB_VERIFIED_COMPOSE_V38
    if (waWebResultSent) return;
    waWebChatReadyAttempts++;
    setStatus("تأكيد فتح محادثة WhatsApp Web قبل الإرسال " + waWebChatReadyAttempts + " ...");

    final String js = "(function(){\n" +
        " function vis(e){ if(!e) return false; var r=e.getBoundingClientRect(); return r.width>0 && r.height>0; }\n" +
        " var boxes=[].slice.call(document.querySelectorAll('footer div[contenteditable=\"true\"], footer [contenteditable=\"true\"][role=\"textbox\"]')).filter(vis);\n" +
        " if(!boxes.length){ return 'NO_FOOTER_COMPOSE'; }\n" +
        " return 'CHAT_COMPOSE_READY boxes=' + boxes.length + ' title=' + document.title;\n" +
        "})()";

    waWebView.evaluateJavascript(js, result -> {
        String r = String.valueOf(result);
        logMini("WA_WEB_CHAT_READY_V38_" + waWebChatReadyAttempts + ": " + r);

        if (r.contains("CHAT_COMPOSE_READY")) {
            currentWaWebPhone = waWebPhone;
            prefs.edit().putString("current_wa_web_phone", currentWaWebPhone).apply();
            handler.postDelayed(() -> fillAndSendCurrentChat(), 500);
            return;
        }

        if (waWebChatReadyAttempts < 10) {
            handler.postDelayed(() -> verifyWhatsAppWebChatReadyThenSend(), 900);
            return;
        }

        waWebResultSent = true;
        setStatus("فشل تأكيد فتح محادثة WhatsApp Web.");
        markWhatsAppWebResult("failed", "whatsapp_web_chat_compose_not_ready_direct_template_v38");
        if (waWebQueueRunning) {
            handler.postDelayed(() -> runNextWhatsAppWebJob(), 900);
        }
    });
}
 void fillAndSendCurrentChat() {
        // UNB_WA_WEB_DESKTOP_VIEW_BEFORE_SEND_V36
        applyWhatsAppWebDesktopViewportV36();
        if (waWebResultSent) return;

        waWebAttempts++;
        setStatus("إرسال من صندوق WhatsApp Web رقم " + waWebAttempts + " ...");

        String quoted = JSONObject.quote(waWebMessage == null ? "" : waWebMessage);

        final String js =
                "(function(msg){\n" +
                "  window.UNB_DIRECT_SENT='';\n" +
                "  function vis(e){ if(!e) return false; var r=e.getBoundingClientRect(); return r.width>0 && r.height>0; }\n" +
                "  function fire(el,t){ try{ el.dispatchEvent(new Event(t,{bubbles:true})); }catch(e){} }\n" +
                "  function key(el,k){ try{ el.dispatchEvent(new KeyboardEvent('keydown',{key:k,code:k,bubbles:true,cancelable:true,keyCode:k==='Enter'?13:0,which:k==='Enter'?13:0})); }catch(e){} }\n" +
                "  function put(el,v){\n" +
                "    try{\n" +
                "      el.focus();\n" +
                "      document.execCommand('selectAll', false, null);\n" +
                "      document.execCommand('delete', false, null);\n" +
                "      document.execCommand('insertText', false, v);\n" +
                "      el.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:v}));\n" +
                "      fire(el,'change'); fire(el,'keyup');\n" +
                "    }catch(e){ try{ el.textContent=v; fire(el,'input'); fire(el,'change'); }catch(x){} }\n" +
                "  }\n" +
                "  function btnFrom(e){ return e.closest('button') || e.closest('[role=\"button\"]') || e; }\n" +
                "  var boxes=[].slice.call(document.querySelectorAll('footer div[contenteditable=\"true\"], footer [contenteditable=\"true\"][role=\"textbox\"]')).filter(vis);\n" +
                "  if(!boxes.length){ window.UNB_DIRECT_SENT='NO_COMPOSE_BOX'; return 'NO_COMPOSE_BOX'; }\n" +
                "  var box=boxes[boxes.length-1];\n" +
                "  put(box,msg);\n" +
                "  setTimeout(function(){\n" +
                "    var selectors=['span[data-icon=\"send\"]','[data-icon=\"send\"]','[data-testid=\"send\"]','[data-icon=\"wds-ic-send-filled\"]','button[aria-label*=\"Send\"]','div[aria-label*=\"Send\"]','button[aria-label*=\"إرسال\"]','div[aria-label*=\"إرسال\"]'];\n" +
                "    for(var s of selectors){\n" +
                "      var arr=[].slice.call(document.querySelectorAll(s));\n" +
                "      for(var el of arr){ var b=btnFrom(el); if(vis(b)){ b.click(); window.UNB_DIRECT_SENT='YES_BUTTON'; return; } }\n" +
                "    }\n" +
                "    key(box,'Enter');\n" +
                "    window.UNB_DIRECT_SENT='NO_SEND_BUTTON';\n" +
                "  },700);\n" +
                "  return 'TEXT_INSERTED_CONNECTED_V38';\n" +
                "})(" + quoted + ")";

        waWebView.evaluateJavascript(js, value -> {
            logMini("WA_WEB_SEND_V38_" + waWebAttempts + ": " + value);

            handler.postDelayed(() -> {
                waWebView.evaluateJavascript("(function(){return window.UNB_DIRECT_SENT || '';})()", result -> {
                    String r = String.valueOf(result);
                    logMini("WA_WEB_SEND_RESULT_V38: " + r);

                    if (r.contains("YES_BUTTON")) {
                        waWebResultSent = true;
                        setStatus("تم إرسال WhatsApp Web من نفس الصفحة.");
                        markWhatsAppWebResult("sent", "whatsapp_web_sent_direct_template_v38");

                        if (waWebQueueRunning) {
                            handler.postDelayed(() -> runNextWhatsAppWebJob(), 800);
                        }
                        return;
                    }

                    if (waWebAttempts < 6) {
                        handler.postDelayed(() -> fillAndSendCurrentChat(), 1000);
                        return;
                    }

                    waWebResultSent = true;
                    setStatus("فشل الإرسال من صندوق WhatsApp Web.");
                    markWhatsAppWebResult("failed", "whatsapp_web_direct_send_failed_direct_template_v38");

                    if (waWebQueueRunning) {
                        handler.postDelayed(() -> runNextWhatsAppWebJob(), 900);
                    }
                });
            }, 1400);
        });
    }

    void scheduleWhatsAppWebFastAttempts() {
        scheduleWhatsAppWebAttempt(900);
        scheduleWhatsAppWebAttempt(1800);
        scheduleWhatsAppWebAttempt(3000);
        scheduleWhatsAppWebAttempt(4500);
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
            logMini("WA_WEB_CURRENT_CHAT_ATTEMPT_" + waWebAttempts + ": " + v);

            if (v.contains("CLICKED_SEND")) {
                waWebResultSent = true;
                setStatus("تم إرسال WhatsApp Web.");
                markWhatsAppWebResult("sent", "whatsapp_web_sent_direct_template_v38");

                if (waWebQueueRunning) {
                    handler.postDelayed(() -> runNextWhatsAppWebJob(), 900);
                }
                return;
            }

            if (waWebAttempts < 12) {
                scheduleWhatsAppWebAttempt(1000);
            } else {
                waWebResultSent = true;
                setStatus("فشل العثور على زر إرسال WhatsApp Web.");
                markWhatsAppWebResult("failed", "whatsapp_web_send_button_not_found_direct_template_v38");

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

        refreshLinkSummary();
        logMini("تم حفظ الرابط والتوكن والطرق.");
    }

    void refreshLinkSummary() {
        String summary =
                "call_apk\nURL=" + baseUrl() + "\nTOKEN=" + shortToken(token()) + "\n\n" +
                "sms_apk\nURL=" + baseUrl() + "\nTOKEN=" + shortToken(token()) + "\n\n" +
                "whatsapp_apk\nURL=" + baseUrl() + "\nTOKEN=" + shortToken(token()) + "\n\n" +
                "whatsapp_web\nURL=" + baseUrl() + "\nTOKEN=" + shortToken(token());

        if (linkSummaryView != null) linkSummaryView.setText(summary);
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
                body.put("message", "اختبار WhatsApp Web من وضع التطبيق");
                body.put("recipient_type", "customer");

                JSONArray methods = new JSONArray();
                methods.put("whatsapp_web");
                body.put("methods", methods);

                String res = post("/api/queue/enqueue", body);
                logMiniUi("تمت إضافة طابور WhatsApp Web:\n" + res);
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
                        focusWhatsAppWeb();
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
                    body.put("error", "whatsapp_apk_auto_ack_fallback_after_open_v24");

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


    String shortToken(String t) {
        if (t == null || t.length() == 0) return "(فارغ)";
        if (t.length() <= 22) return t;
        return t.substring(0, 14) + "..." + t.substring(t.length() - 10);
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
        v.setPadding(dp(4), dp(4), dp(4), dp(4));
        return v;
    }

    TextView title(String s) {
        TextView v = tv(s, 16, Typeface.BOLD, Color.rgb(17, 24, 39));
        v.setGravity(Gravity.RIGHT);
        return v;
    }

    TextView label(String s) {
        TextView v = tv(s, 12, Typeface.BOLD, Color.rgb(55, 65, 81));
        v.setGravity(Gravity.RIGHT);
        return v;
    }

    EditText input(String s) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(s);
        e.setTextDirection(View.TEXT_DIRECTION_LTR);
        e.setPadding(dp(7), dp(6), dp(7), dp(6));
        return e;
    }

    CheckBox check(String s, boolean on) {
        CheckBox b = new CheckBox(this);
        b.setText(s);
        b.setTextSize(13);
        b.setChecked(on);
        return b;
    }

    Button btn(String s, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackgroundColor(color);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        b.setPadding(dp(4), dp(8), dp(4), dp(8));
        return b;
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(8), dp(8), dp(8), dp(8));
        c.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, dp(5), 0, dp(5));
        c.setLayoutParams(lp);
        return c;
    }

    LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return r;
    }

    LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        return lp;
    }

    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
