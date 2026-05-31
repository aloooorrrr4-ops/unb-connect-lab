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

    // UNB_V42_AUTO_PULL_10_SECONDS_FIELDS
    boolean autoPullStartedV42 = false;
    boolean autoPullBusyV42 = false;
    long lastCallApkAtV42 = 0L;
    final long AUTO_PULL_INTERVAL_V42 = 10000L;
    final long CALL_APK_GUARD_MS_V42 = 90000L;
    Runnable autoPullRunnableV42 = new Runnable() {
        @Override public void run() {
            autoPullTickV42();
        }
    };

    SharedPreferences prefs;

    ScrollView controlsScroll;
    LinearLayout webPanel;
    LinearLayout waWebHolder;

    EditText urlInput;
    EditText tokenInput;
    EditText waSearchInput;

    TextView statusView;
    TextView brandView;
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
    
        refreshOnAppOpenV40();
        refreshBrandFromPrefsV42();
        startAutoPullV42();
}

    View header() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setGravity(Gravity.CENTER);
        h.setPadding(dp(10), dp(10), dp(10), dp(10));
        h.setBackgroundColor(DARK);

        brandView = tv(appHeaderTextV42(), 21, Typeface.BOLD, Color.WHITE);
        brandView.setGravity(Gravity.CENTER);
        h.addView(brandView);

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
        c.addView(title("توكن طرق الإرسال"));

        linkSummaryView = tv("اضغط أي طريقة لنسخ URL + TOKEN + METHOD", 12, Typeface.BOLD, Color.rgb(31, 41, 55));
        linkSummaryView.setGravity(Gravity.CENTER);
        c.addView(linkSummaryView);

        LinearLayout r1 = row();
        r1.addView(btn("☎ Call", BLUE, v -> copyMethodTokenV42("call_apk")), weight());
        r1.addView(btn("✉ SMS", GREEN, v -> copyMethodTokenV42("sms_apk")), weight());
        c.addView(r1);

        LinearLayout r2 = row();
        r2.addView(btn("WA APK", ORANGE, v -> copyMethodTokenV42("whatsapp_apk")), weight());
        r2.addView(btn("WA Web", BLUE, v -> copyMethodTokenV42("whatsapp_web")), weight());
        c.addView(r2);

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


    // UNB_V40_REFRESH_ON_OPEN_BACKGROUND_AUTODRAIN
    boolean waWebAutoDrainV40 = true;

    void startKeepAliveServiceV40() {
        try {
            android.content.Intent i = new android.content.Intent(this, UnbKeepAliveService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                startForegroundService(i);
            } else {
                startService(i);
            }
            logMini("V40 keep-alive service started.");
        } catch (Exception e) {
            logMini("V40 keep-alive service error: " + e.getMessage());
        }
    }

    void refreshWhatsAppWebV40() {
        try {
            showWhatsAppWeb();
            ensureWhatsAppWebLoaded();
            focusWhatsAppWeb();
            if (waWebView != null) {
                waWebView.reload();
            }
            setStatus("تم تحديث WhatsApp Web.");
            logMini("V40 refresh WhatsApp Web.");
        } catch (Exception e) {
            logMini("V40 refresh error: " + e.getMessage());
        }
    }

    void refreshOnAppOpenV40() {
        try {
            startKeepAliveServiceV40();
            handler.postDelayed(() -> {
                try {
                    refreshWhatsAppWebV40();
                } catch (Exception e) {
                    logMini("V40 open refresh delayed error: " + e.getMessage());
                }
            }, 1200);
        } catch (Exception e) {
            logMini("V40 open refresh error: " + e.getMessage());
        }
    }

    void scheduleNextWhatsAppWebPullV40(String reason) {
        if (!waWebAutoDrainV40) return;
        handler.postDelayed(() -> {
            try {
                if (waWebQueueRunning) {
                    logMini("V40 auto-drain ينتظر انتهاء الطلب الحالي: " + reason);
                    handler.postDelayed(() -> scheduleNextWhatsAppWebPullV40("retry_after_running"), 2500);
                    return;
                }
                logMini("V40 auto-drain يسحب الطلب التالي: " + reason);
                pullWhatsAppWebQueueAndRun();
            } catch (Exception e) {
                logMini("V40 auto-drain error: " + e.getMessage());
            }
        }, 3500);
    }

    void pullWhatsAppWebQueueAndRun() {
        // UNB_V40_PULL_GUARD
        if (waWebQueueRunning) {
            logMini("V40: يوجد تنفيذ WhatsApp Web قائم، لن نسحب طلبًا جديدًا الآن.");
            return;
        }
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
                    waWebQueueRunning = false;
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
                logMini("انتهى تنفيذ طابور WhatsApp Web.");
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
            currentWaWebPhone = ""; logMini("V41: فتح مباشر سريع لكل عميل بدون قالب بدون اختصار المحادثة."); openChatBySearchThenSend();

        } catch (Exception e) {
            setStatus("خطأ تجهيز WhatsApp Web: " + e.getMessage());
        }
    }

    void openChatBySearchThenSend() {
        // UNB_WA_WEB_FAST_DIRECT_CUSTOMERS_V41
        if (waWebResultSent) return;

        try {
            waWebOpenAttempts++;
            waWebChatReadyAttempts = 0;

            String phoneClean = waWebPhone == null ? "" : waWebPhone.replaceAll("[^0-9]", "");
            if (phoneClean.length() == 0) {
                waWebResultSent = true;
                markWhatsAppWebResult("failed", "whatsapp_web_fast_direct_empty_phone_v41");
                return;
            }

            String msg = waWebMessage == null ? "" : waWebMessage;
            String encodedMessage = java.net.URLEncoder.encode(msg, "UTF-8").replace("+", "%20");
            String directUrl = "https://web.whatsapp.com/send?phone=" + phoneClean + "&text=" + encodedMessage + "&app_absent=0&unb_direct_ts=" + System.currentTimeMillis();

            setStatus("V41 فتح مباشر للعميل بدون قالب...");
            logMini("V41_FAST_DIRECT_CUSTOMER_URL: " + directUrl);

            showWhatsAppWeb();
            ensureWhatsAppWebLoaded();
            focusWhatsAppWeb();

            if (waWebView != null) {
                try { waWebView.stopLoading(); } catch (Exception ignored) {}
                waWebView.loadUrl(directUrl);
            }

            handler.postDelayed(() -> {
                try { applyWhatsAppWebDesktopViewportV36(); } catch (Exception ignored) {}
            }, 1600);

            handler.postDelayed(() -> {
                try {
                    applyWhatsAppWebDesktopViewportV36();
                    verifyWhatsAppWebChatReadyThenSend();
                } catch (Exception e) {
                    logMini("V41 fast direct verify error: " + e.getMessage());
                    verifyWhatsAppWebChatReadyThenSend();
                }
            }, 3800);

        } catch (Exception e) {
            waWebResultSent = true;
            markWhatsAppWebResult("failed", "whatsapp_web_fast_direct_exception_v41_" + e.getClass().getSimpleName());
        }
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
        "   return 'DESKTOP_VIEWPORT_APPLIED_V41';\n" +
        " }catch(e){ return 'DESKTOP_VIEWPORT_ERROR_V41:' + e.message; }\n" +
        "})()";

    waWebView.evaluateJavascript(js, result -> {
        logMini("WA_WEB_DESKTOP_VIEWPORT_V36: " + result);
    });
}

void verifyWhatsAppWebChatReadyThenSend() {
    // UNB_WA_WEB_VERIFIED_COMPOSE_V41
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
        logMini("WA_WEB_CHAT_READY_V41_" + waWebChatReadyAttempts + ": " + r);

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
        markWhatsAppWebResult("failed", "whatsapp_web_chat_compose_not_ready_fast_direct_customers_v41");
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
                "  return 'TEXT_INSERTED_CONNECTED_V41';\n" +
                "})(" + quoted + ")";

        waWebView.evaluateJavascript(js, value -> {
            logMini("WA_WEB_SEND_V41_" + waWebAttempts + ": " + value);

            handler.postDelayed(() -> {
                waWebView.evaluateJavascript("(function(){return window.UNB_DIRECT_SENT || '';})()", result -> {
                    String r = String.valueOf(result);
                    logMini("WA_WEB_SEND_RESULT_V41: " + r);

                    if (r.contains("YES_BUTTON")) {
                        waWebResultSent = true;
                        setStatus("تم إرسال WhatsApp Web من نفس الصفحة.");
                        markWhatsAppWebResult("sent", "whatsapp_web_sent_fast_direct_customers_v41");

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
                    markWhatsAppWebResult("failed", "whatsapp_web_direct_send_failed_fast_direct_customers_v41");

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
                markWhatsAppWebResult("sent", "whatsapp_web_sent_fast_direct_customers_v41");

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
                markWhatsAppWebResult("failed", "whatsapp_web_send_button_not_found_fast_direct_customers_v41");

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
    
        // UNB_V40_AUTODRAIN_AFTER_RESULT
        scheduleNextWhatsAppWebPullV40("after_result");
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
        if (linkSummaryView != null) {
            linkSummaryView.setText("اضغط أي طريقة لنسخ URL + TOKEN + METHOD");
        }
    }


    // UNB_V42_HEADER_INSTITUTION_AND_COPY_METHODS
    String appHeaderTextV42() {
        String name = prefs.getString("institution_name", "").trim();
        if (name.length() == 0) name = prefs.getString("company_name", "").trim();
        if (name.length() == 0) name = prefs.getString("tenant_name", "").trim();

        if (name.length() > 0) {
            return "تطبيق خاص بمؤسسة " + name;
        }

        return "تطبيق المتابعه للنظام الأقوى للتأجير";
    }

    void refreshBrandFromPrefsV42() {
        runOnUiThread(() -> {
            try {
                if (brandView != null) brandView.setText(appHeaderTextV42());
            } catch (Exception ignored) {}
        });
    }

    void saveInstitutionNameFromResponseV42(String res) {
        try {
            String name = findInstitutionNameInJsonV42(new JSONObject(res));
            if (name != null) name = name.trim();
            if (name != null && name.length() > 0 && name.length() < 120) {
                prefs.edit().putString("institution_name", name).apply();
                refreshBrandFromPrefsV42();
                logMini("تم تحديث اسم المؤسسة: " + name);
            }
        } catch (Exception ignored) {}
    }

    String findInstitutionNameInJsonV42(Object obj) {
        try {
            if (obj instanceof JSONObject) {
                JSONObject j = (JSONObject) obj;

                String[] preferred = new String[]{
                    "institution_name", "institutionName",
                    "company_name", "companyName",
                    "tenant_name", "tenantName",
                    "organization_name", "organizationName"
                };

                for (String k : preferred) {
                    if (j.has(k) && !j.isNull(k)) {
                        String v = String.valueOf(j.opt(k)).trim();
                        if (v.length() > 0 && !v.equals("null")) return v;
                    }
                }

                java.util.Iterator<String> it = j.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    Object v = j.opt(k);
                    if (v instanceof JSONObject || v instanceof JSONArray) {
                        String found = findInstitutionNameInJsonV42(v);
                        if (found != null && found.trim().length() > 0) return found.trim();
                    }
                }
            }

            if (obj instanceof JSONArray) {
                JSONArray a = (JSONArray) obj;
                for (int i = 0; i < a.length(); i++) {
                    String found = findInstitutionNameInJsonV42(a.opt(i));
                    if (found != null && found.trim().length() > 0) return found.trim();
                }
            }
        } catch (Exception ignored) {}

        return "";
    }

    void copyMethodTokenV42(String method) {
        try {
            saveSettings();
            String payload =
                "METHOD=" + method + "\n" +
                "URL=" + baseUrl() + "\n" +
                "TOKEN=" + token();

            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText(method, payload));
            logMini("تم نسخ بيانات " + method);
        } catch (Exception e) {
            logMini("فشل نسخ بيانات الطريقة: " + e.getMessage());
        }
    }

    // UNB_V42_AUTO_PULL_10_SECONDS
    void startAutoPullV42() {
        if (autoPullStartedV42) return;
        autoPullStartedV42 = true;
        logMini("V42 Auto Pull يعمل كل 10 ثواني.");
        scheduleAutoPullV42(2500L);
    }

    void scheduleAutoPullV42(long delayMs) {
        try {
            handler.removeCallbacks(autoPullRunnableV42);
            handler.postDelayed(autoPullRunnableV42, delayMs);
        } catch (Exception ignored) {}
    }

    void autoPullTickV42() {
        try {
            if (!prefs.getBoolean("full_link_enabled", true)) {
                scheduleAutoPullV42(AUTO_PULL_INTERVAL_V42);
                return;
            }

            if (autoPullBusyV42 || waWebQueueRunning) {
                scheduleAutoPullV42(AUTO_PULL_INTERVAL_V42);
                return;
            }

            autoPullBusyV42 = true;
            pollAndExecuteAutoV42(1);
        } catch (Exception e) {
            autoPullBusyV42 = false;
            logMini("V42 Auto Pull error: " + e.getMessage());
            scheduleAutoPullV42(AUTO_PULL_INTERVAL_V42);
        }
    }

    void pollAndExecuteAutoV42(int limit) {
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
                    return;
                }

                logMiniUi("V42 Auto Pull: تم سحب " + jobs.length() + " طلب.");

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
                logMiniUi("فشل V42 Auto Pull: " + e.getMessage());
            } finally {
                autoPullBusyV42 = false;
                scheduleAutoPullV42(AUTO_PULL_INTERVAL_V42);
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
                saveInstitutionNameFromResponseV42(res);
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
                // UNB_V42_CALL_APK_90_SECONDS_GUARD
                long now = System.currentTimeMillis();
                long waitMs = CALL_APK_GUARD_MS_V42 - (now - lastCallApkAtV42);
                if (waitMs > 0) {
                    logMiniUi("فاصل أمان الاتصال: انتظار " + (waitMs / 1000) + " ثانية قبل الاتصال التالي.");
                    sleep(waitMs);
                }
                lastCallApkAtV42 = System.currentTimeMillis();
                makeCall(phone);
                ack(id, "sent", "call_started_by_native_v42_90s_guard");
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
