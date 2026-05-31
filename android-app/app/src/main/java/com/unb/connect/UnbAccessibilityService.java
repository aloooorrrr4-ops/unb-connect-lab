package com.unb.connect;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class UnbAccessibilityService extends AccessibilityService {
    Handler handler = new Handler(Looper.getMainLooper());
    boolean working = false;
    String activeJob = "";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        SharedPreferences prefs = getSharedPreferences("unb_connect", MODE_PRIVATE);
        String jobId = prefs.getString("pending_job_id", "");

        if (jobId.length() == 0) return;

        if (working && jobId.equals(activeJob)) return;

        activeJob = jobId;
        working = true;

        handler.postDelayed(() -> attemptSend(jobId, 1), 2800);
    }

    @Override
    public void onInterrupt() {
        working = false;
    }

    void attemptSend(String jobId, int attempt) {
        SharedPreferences prefs = getSharedPreferences("unb_connect", MODE_PRIVATE);
        String current = prefs.getString("pending_job_id", "");

        if (!jobId.equals(current)) {
            working = false;
            return;
        }

        boolean clicked = false;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            clicked = clickSendNode(root);
        }

        if (!clicked) {
            clicked = tapSendZones();
        }

        if (clicked) {
            handler.postDelayed(() -> postResultWithRetry(jobId, "sent", "whatsapp_apk_sent_by_accessibility_v15", 1), 1500);
            return;
        }

        if (attempt < 8) {
            handler.postDelayed(() -> attemptSend(jobId, attempt + 1), 1800);
        } else {
            postResultWithRetry(jobId, "failed", "whatsapp_apk_send_button_not_found_v15", 1);
        }
    }

    boolean clickSendNode(AccessibilityNodeInfo node) {
        if (node == null) return false;

        String text = node.getText() == null ? "" : node.getText().toString().toLowerCase();
        String desc = node.getContentDescription() == null ? "" : node.getContentDescription().toString().toLowerCase();
        String viewId = Build.VERSION.SDK_INT >= 18 && node.getViewIdResourceName() != null
                ? node.getViewIdResourceName().toLowerCase()
                : "";

        boolean looksSend =
                text.contains("send") ||
                desc.contains("send") ||
                text.contains("إرسال") ||
                desc.contains("إرسال") ||
                text.contains("ارسال") ||
                desc.contains("ارسال") ||
                viewId.contains("send");

        if (looksSend) {
            AccessibilityNodeInfo target = node;
            while (target != null) {
                if (target.isClickable()) {
                    return target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                target = target.getParent();
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            if (clickSendNode(node.getChild(i))) return true;
        }

        return false;
    }

    boolean tapSendZones() {
        if (Build.VERSION.SDK_INT < 24) return false;

        int sw = getResources().getDisplayMetrics().widthPixels;
        int sh = getResources().getDisplayMetrics().heightPixels;

        // واتساب العربي غالبًا زر الإرسال أسفل اليسار، والإنجليزي أسفل اليمين
        boolean left = tap(sw * 0.075f, sh * 0.885f);
        boolean right = tap(sw * 0.925f, sh * 0.885f);

        return left || right;
    }

    boolean tap(float x, float y) {
        if (Build.VERSION.SDK_INT < 24) return false;

        try {
            Path path = new Path();
            path.moveTo(x, y);

            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 140))
                    .build();

            return dispatchGesture(gesture, null, null);
        } catch (Exception e) {
            return false;
        }
    }

    void postResultWithRetry(String jobId, String status, String note, int attempt) {
        new Thread(() -> {
            boolean ok = postResult(jobId, status, note);

            if (ok) {
                SharedPreferences prefs = getSharedPreferences("unb_connect", MODE_PRIVATE);
                prefs.edit()
                        .remove("pending_job_id")
                        .remove("pending_server_url")
                        .remove("pending_token")
                        .putString("last_whatsapp_result", status + " / " + note)
                        .apply();

                working = false;
                activeJob = "";
            } else {
                if (attempt < 6) {
                    handler.postDelayed(() -> postResultWithRetry(jobId, status, note, attempt + 1), 2500);
                } else {
                    working = false;
                    activeJob = "";
                }
            }
        }).start();
    }

    boolean postResult(String jobId, String status, String note) {
        try {
            SharedPreferences prefs = getSharedPreferences("unb_connect", MODE_PRIVATE);

            String serverUrl = prefs.getString("pending_server_url",
                    prefs.getString("server_url", "http://127.0.0.1:3108"));
            while (serverUrl.endsWith("/")) serverUrl = serverUrl.substring(0, serverUrl.length() - 1);

            String token = prefs.getString("pending_token",
                    prefs.getString("token", prefs.getString("device_token", "")));

            String deviceId = prefs.getString("device_id", prefs.getString("deviceId", ""));

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

            if (deviceId.length() > 0) {
                c.setRequestProperty("X-UNB-Device", deviceId);
            }

            JSONObject body = new JSONObject();
            body.put("job_id", jobId);
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
            c.disconnect();

            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        }
    }
}
