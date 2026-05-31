package com.unb.connect;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class UnbAccessibilityService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String scheduledJobId = "";
    private long lastScheduleAt = 0;

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

            if (jobId.equals(scheduledJobId) && now - lastScheduleAt < 12000) return;

            scheduledJobId = jobId;
            lastScheduleAt = now;

            handler.postDelayed(() -> attemptSend(jobId, 1), 2800);

        } catch (Exception ignored) {}
    }

    @Override
    public void onInterrupt() {}

    private void attemptSend(String jobId, int attempt) {
        try {
            SharedPreferences p = getSharedPreferences("unb_connect", MODE_PRIVATE);
            String currentJob = p.getString("pending_job_id", "");
            if (!jobId.equals(currentJob)) return;

            AccessibilityNodeInfo root = getRootInActiveWindow();

            boolean clicked = false;

            if (root != null) {
                AccessibilityNodeInfo send = findSendByName(root);
                if (send != null) clicked = clickNode(send);
            }

            if (!clicked && root != null) {
                AccessibilityNodeInfo input = findMessageInput(root);
                if (input != null) clicked = tapZoneBesideInput(input);
            }

            if (!clicked) {
                clicked = tapGreenSendButtonZone();
            }

            if (clicked) {
                String base = p.getString("pending_server_url", "");
                String token = p.getString("pending_token", "");

                p.edit().remove("pending_job_id").apply();
                markResult(base, token, jobId, "sent", "whatsapp_delayed_force_send_v10_attempt_" + attempt);
                return;
            }

            if (attempt < 4) {
                handler.postDelayed(() -> attemptSend(jobId, attempt + 1), 1800);
            } else {
                String base = p.getString("pending_server_url", "");
                String token = p.getString("pending_token", "");

                p.edit().remove("pending_job_id").apply();
                markResult(base, token, jobId, "failed", "whatsapp_send_button_not_clicked_v10");
            }

        } catch (Exception ignored) {}
    }

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
                r.height() < 300;

        if (looksInput && bottomInput && n.isEnabled()) return n;

        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo x = findMessageInput(n.getChild(i));
            if (x != null) return x;
        }

        return null;
    }

    private boolean tapZoneBesideInput(AccessibilityNodeInfo input) {
        if (Build.VERSION.SDK_INT < 24) return false;

        try {
            Rect r = new Rect();
            input.getBoundsInScreen(r);

            DisplayMetrics dm = getResources().getDisplayMetrics();
            int sw = dm.widthPixels;

            int y = r.top + (r.height() / 2);
            int x;

            if (r.left > sw * 0.12) {
                x = Math.max(45, r.left / 2);
            } else {
                x = Math.min(sw - 45, r.right + ((sw - r.right) / 2));
            }

            int[] dx = new int[] {0, -20, 20, -40, 40};
            int[] dy = new int[] {0, -18, 18, -35, 35};

            boolean ok = false;
            for (int yy : dy) {
                for (int xx : dx) {
                    ok = tap(x + xx, y + yy) || ok;
                    sleep(220);
                }
            }

            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean tapGreenSendButtonZone() {
        if (Build.VERSION.SDK_INT < 24) return false;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int sw = dm.widthPixels;
        int sh = dm.heightPixels;

        int[] xs = new int[] {
                (int)(sw * 0.055),
                (int)(sw * 0.075),
                (int)(sw * 0.095),
                (int)(sw * 0.115),
                (int)(sw * 0.135)
        };

        int[] ys = new int[] {
                (int)(sh * 0.845),
                (int)(sh * 0.870),
                (int)(sh * 0.895),
                (int)(sh * 0.920),
                (int)(sh * 0.945)
        };

        boolean ok = false;

        for (int y : ys) {
            for (int x : xs) {
                ok = tap(x, y) || ok;
                sleep(220);
            }
        }

        return ok;
    }

    private boolean tap(int x, int y) {
        if (Build.VERSION.SDK_INT < 24) return false;

        try {
            Path path = new Path();
            path.moveTo(x, y);

            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 180))
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
