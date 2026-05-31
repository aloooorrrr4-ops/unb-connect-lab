package com.unb.connect;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 48, 32, 32);
        root.setGravity(Gravity.RIGHT);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("UNB Connect APK");
        title.setTextSize(26);
        title.setGravity(Gravity.RIGHT);
        root.addView(title);

        TextView msg = new TextView(this);
        msg.setText("تم بناء التطبيق من GitHub بنجاح.\nالمرحلة التالية: ربطه بطابور Termux ثم Accessibility.");
        msg.setTextSize(18);
        msg.setGravity(Gravity.RIGHT);
        root.addView(msg);

        setContentView(scroll);
    }
}
