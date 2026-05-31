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
            if (now - lastClickAt < 4000) return;
            lastClickAt = now;

            AccessibilityNodeInfo root = getRootInActiveWindow();

            boolean attempted = false;

            if (root != null) {
                AccessibilityNodeInfo send = findSendByName(root);
                if (send != null) {
                    attempted = clickNode(send);
                    sleep(600);
                }
            }

            if (!attempted && root != null) {
                attempted = clickBottomLeftClickable(root);
                sleep(600);
            }

            if (!attempted && root != null) {
                AccessibilityNodeInfo input = findMessageInput(root);
                if (input != null) {
                    attempted = tapWideZoneBesideInput(input);
                    sleep(600);
                }
            }

            if (!attempted) {
                attempted = tapVeryWideBottomLeftZone();
                sleep(600);
            }

            String base = p.getString("pending_server_url", "");
            String token = p.getString("pending_token", "");

            p.edit().remove("pending_job_id").apply();

            if (attempted) {
                markResult(base, token, jobId, "sent", "whatsapp_force_send_zone_v9");
            } else {
                markResult(base, token, jobId, "failed", "whatsapp_force_send_zone_no_click_v9");
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

        String s = ((d == null ? "" : d.toString()) + " " +
                (t == null ? "" : t.toString()) + " " + id).toLowerCase();

        boolean isSend =
                s.contains("send") ||
                s.contains("إرسال") ||
                s.contains("ارسال") ||
                id.contains("send");

        if (isSend && n.isEnabled()) return n;

        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = findSendByName(n.getChild(i));
            if (r != null) return r;
        }

        return null;
    }

    private boolean clickBottomLeftClickable(AccessibilityNodeInfo n) {
        if (n == null) return false;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int sw = dm.widthPixels;
        int sh = dm.heightPixels;

        Rect r = new Rect();
        n.getBoundsInScreen(r);

        int w = r.width();
        int h = r.height();

        boolean bottom = r.top > (int)(sh * 0.60);
        boolean left = r.left < (int)(sw * 0.24);
        boolean goodSize = w >= 35 && w <= 240 && h >= 35 && h <= 240;

        if (n.isEnabled() && n.isClickable() && bottom && left && goodSize) {
            if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        }

        for (int i = 0; i < n.getChildCount(); i++) {
            if (clickBottomLeftClickable(n.getChild(i))) return true;
        }

        return false;
    }

    private AccessibilityNodeInfo findMessageInput(AccessibilityNodeInfo n) {
        if (n == null) return null;

        Rect r = new Rect();
        n.getBoundsInScreen(r);

        String cls = "";
        String id = "";
        try {
            if (n.getClassName() != null) cls = n.getClassName().toString().toLowerCase();
            if (n.getViewIdResourceName() != null) id = n.getViewIdResourceName().toLowerCase();
        } catch (Exception ignored) {}

        boolean looksInput =
                cls.contains("edittext") ||
                id.contains("entry") ||
                id.contains("input") ||
                id.contains("message");

        boolean bottomInput =
                r.top > 500 &&
                r.width() > 250 &&
                r.height() > 35 &&
                r.height() < 280;

        if (looksInput && bottomInput && n.isEnabled()) return n;

        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo x = findMessageInput(n.getChild(i));
            if (x != null) return x;
        }

        return null;
    }

    private boolean tapWideZoneBesideInput(AccessibilityNodeInfo input) {
        if (Build.VERSION.SDK_INT < 24) return false;

        try {
            Rect r = new Rect();
            input.getBoundsInScreen(r);

            DisplayMetrics dm = getResources().getDisplayMetrics();
            int sw = dm.widthPixels;

            int centerY = r.top + (r.height() / 2);
            int centerX;

            if (r.left > sw * 0.12) {
                centerX = Math.max(45, r.left / 2);
            } else {
                centerX = Math.min(sw - 45, r.right + ((sw - r.right) / 2));
            }

            int[] dx = new int[] {0, -20, 20, -40, 40, -60, 60};
            int[] dy = new int[] {0, -20, 20, -40, 40};

            boolean attempted = false;

            for (int yOff : dy) {
                for (int xOff : dx) {
                    attempted = tap(centerX + xOff, centerY + yOff) || attempted;
                    sleep(160);
                }
            }

            return attempted;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean tapVeryWideBottomLeftZone() {
        if (Build.VERSION.SDK_INT < 24) return false;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int sw = dm.widthPixels;
        int sh = dm.heightPixels;

        int[] xs = new int[] {
                Math.max(35, (int)(sw * 0.045)),
                Math.max(45, (int)(sw * 0.065)),
                Math.max(55, (int)(sw * 0.085)),
                Math.max(70, (int)(sw * 0.110)),
                Math.max(90, (int)(sw * 0.140))
        };

        int[] ys = new int[] {
                (int)(sh * 0.780),
                (int)(sh * 0.820),
                (int)(sh * 0.860),
                (int)(sh * 0.890),
                (int)(sh * 0.920),
                (int)(sh * 0.950)
        };

        boolean attempted = false;

        for (int y : ys) {
            for (int x : xs) {
                attempted = tap(x, y) || attempted;
                sleep(160);
            }
        }

        return attempted;
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

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (Exception ignored) {}
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
