package com.unb.connect;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
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
            if (now - lastClickAt < 3000) return;

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;

            boolean clicked = false;

            AccessibilityNodeInfo send = findSendByName(root);
            if (send != null) clicked = clickNode(send);

            if (!clicked) {
                AccessibilityNodeInfo input = findMessageInput(root);
                if (input != null) clicked = tapSendBesideInput(input);
            }

            if (!clicked) {
                clicked = tapKnownWhatsAppLeftSendPosition();
            }

            if (clicked) {
                lastClickAt = now;

                String base = p.getString("pending_server_url", "");
                String token = p.getString("pending_token", "");

                p.edit().remove("pending_job_id").apply();

                markResult(base, token, jobId, "sent", "whatsapp_send_precise_icon_tap_v7");
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onInterrupt() {}

    private AccessibilityNodeInfo findSendByName(AccessibilityNodeInfo n) {
        if (n == null) return null;

        String id = "";
        try {
            if (n.getViewIdResourceName() != null) id = n.getViewIdResourceName().toLowerCase();
        } catch (Exception ignored) {}

        CharSequence d = n.getContentDescription();
        CharSequence t = n.getText();

        String s = (
                (d == null ? "" : d.toString()) + " " +
                (t == null ? "" : t.toString()) + " " +
                id
        ).toLowerCase();

        boolean isSend =
                s.contains("send") ||
                s.contains("إرسال") ||
                s.contains("ارسال") ||
                id.endsWith(":id/send") ||
                id.contains("send");

        if (isSend && n.isEnabled()) return n;

        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = findSendByName(n.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    private AccessibilityNodeInfo findMessageInput(AccessibilityNodeInfo n) {
        if (n == null) return null;

        Rect r = new Rect();
        n.getBoundsInScreen(r);

        String cls = "";
        String id = "";
        String text = "";
        try {
            if (n.getClassName() != null) cls = n.getClassName().toString().toLowerCase();
            if (n.getViewIdResourceName() != null) id = n.getViewIdResourceName().toLowerCase();
            if (n.getText() != null) text = n.getText().toString();
        } catch (Exception ignored) {}

        boolean looksInput =
                cls.contains("edittext") ||
                id.contains("entry") ||
                id.contains("input") ||
                id.contains("message");

        boolean goodBounds =
                r.width() > 250 &&
                r.height() > 40 &&
                r.height() < 220 &&
                r.top > 300;

        if (looksInput && goodBounds && n.isEnabled()) return n;

        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo x = findMessageInput(n.getChild(i));
            if (x != null) return x;
        }

        return null;
    }

    private boolean tapSendBesideInput(AccessibilityNodeInfo input) {
        if (Build.VERSION.SDK_INT < 24) return false;

        try {
            Rect r = new Rect();
            input.getBoundsInScreen(r);

            DisplayMetrics dm = getResources().getDisplayMetrics();
            int sw = dm.widthPixels;

            int y = r.top + (r.height() / 2);

            int leftX = Math.max(35, r.left / 2);
            int rightX = Math.min(sw - 35, r.right + ((sw - r.right) / 2));

            int x;
            if (r.left > sw * 0.12) {
                x = leftX;
            } else {
                x = rightX;
            }

            return tap(x, y);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean tapKnownWhatsAppLeftSendPosition() {
        if (Build.VERSION.SDK_INT < 24) return false;

        try {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int sw = dm.widthPixels;
            int sh = dm.heightPixels;

            int x = Math.max(55, (int)(sw * 0.075));
            int y1 = (int)(sh * 0.535);
            int y2 = (int)(sh * 0.565);

            boolean a = tap(x, y1);
            if (!a) return tap(x, y2);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean tap(int x, int y) {
        if (Build.VERSION.SDK_INT < 24) return false;

        try {
            Path path = new Path();
            path.moveTo(x, y);

            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 160))
                    .build();

            return dispatchGesture(gesture, null, null);
        } catch (Exception e) {
            return false;
        }
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
