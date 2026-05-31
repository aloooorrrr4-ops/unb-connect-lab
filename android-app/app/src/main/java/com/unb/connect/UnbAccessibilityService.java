package com.unb.connect;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class UnbAccessibilityService extends AccessibilityService {
    private long lastClickAt = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            if (event == null || event.getPackageName() == null) return;

            String pkg = event.getPackageName().toString();
            if (!pkg.equals("com.whatsapp") && !pkg.equals("com.whatsapp.w4b")) return;

            SharedPreferences p = getSharedPreferences("unb_connect", MODE_PRIVATE);
            String jobId = p.getString("pending_job_id", "");
            if (jobId.length() == 0) return;

            long now = System.currentTimeMillis();
            if (now - lastClickAt < 2500) return;

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;

            boolean clicked = false;

            AccessibilityNodeInfo send = findSendButton(root);
            if (send != null) {
                clicked = clickNode(send);
            }

            if (!clicked) {
                AccessibilityNodeInfo icon = findBottomSendIcon(root);
                if (icon != null) clicked = clickNode(icon);
            }

            if (!clicked) {
                clicked = clickBottomLeftFallback(root);
            }

            if (clicked) {
                lastClickAt = now;

                String base = p.getString("pending_server_url", "");
                String token = p.getString("pending_token", "");

                p.edit().remove("pending_job_id").apply();

                markResult(base, token, jobId, "sent", "whatsapp_send_clicked_by_accessibility_fallback");
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onInterrupt() {}

    private AccessibilityNodeInfo findSendButton(AccessibilityNodeInfo n) {
        if (n == null) return null;

        String id = "";
        try {
            if (n.getViewIdResourceName() != null) {
                id = n.getViewIdResourceName().toLowerCase();
            }
        } catch (Exception ignored) {}

        CharSequence d = n.getContentDescription();
        CharSequence t = n.getText();

        String s = (
                (d == null ? "" : d.toString()) + " " +
                (t == null ? "" : t.toString()) + " " +
                id
        ).toLowerCase();

        boolean looksSend =
                s.contains("send") ||
                s.contains("إرسال") ||
                s.contains("ارسال") ||
                id.contains("send");

        if (looksSend && n.isEnabled()) return n;

        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = findSendButton(n.getChild(i));
            if (r != null) return r;
        }

        return null;
    }

    private AccessibilityNodeInfo findBottomSendIcon(AccessibilityNodeInfo root) {
        Rect screen = new Rect();
        root.getBoundsInScreen(screen);

        return findBottomClickable(root, screen);
    }

    private AccessibilityNodeInfo findBottomClickable(AccessibilityNodeInfo n, Rect screen) {
        if (n == null) return null;

        Rect r = new Rect();
        n.getBoundsInScreen(r);

        int sw = Math.max(1, screen.width());
        int sh = Math.max(1, screen.height());
        int w = r.width();
        int h = r.height();

        boolean nearBottom = r.top > (int)(sh * 0.45);
        boolean goodSize = w >= 35 && w <= 180 && h >= 35 && h <= 180;
        boolean sideButton = r.left < (int)(sw * 0.22) || r.right > (int)(sw * 0.78);

        if (n.isEnabled() && n.isClickable() && nearBottom && goodSize && sideButton) {
            return n;
        }

        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo x = findBottomClickable(n.getChild(i), screen);
            if (x != null) return x;
        }

        return null;
    }

    private boolean clickNode(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo cur = n;

        while (cur != null) {
            if (cur.isClickable() && cur.isEnabled()) {
                return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            cur = cur.getParent();
        }

        return false;
    }

    private boolean clickBottomLeftFallback(AccessibilityNodeInfo root) {
        if (Build.VERSION.SDK_INT < 24) return false;

        try {
            Rect screen = new Rect();
            root.getBoundsInScreen(screen);

            int x = Math.max(45, screen.left + (int)(screen.width() * 0.07));
            int y = screen.top + (int)(screen.height() * 0.55);

            Path path = new Path();
            path.moveTo(x, y);

            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 120))
                    .build();

            dispatchGesture(gesture, null, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void markResult(String base, String token, String jobId, String status, String note) {
        final String b0 = base == null ? "" : base;
        final String t0 = token == null ? "" : token;

        new Thread(() -> {
            try {
                if (b0.length() == 0) return;

                String b = b0;
                if (b.endsWith("/")) b = b.substring(0, b.length() - 1);

                JSONObject body = new JSONObject();
                body.put("job_id", jobId);
                body.put("status", status);
                body.put("note", note);

                HttpURLConnection c = (HttpURLConnection) new URL(b + "/api/queue/result").openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setRequestProperty("X-Device-Token", t0);

                OutputStream os = c.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();

                InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
                if (is != null) is.close();
            } catch (Exception ignored) {}
        }).start();
    }
}
