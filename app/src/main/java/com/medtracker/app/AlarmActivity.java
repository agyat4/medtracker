package com.medtracker.app;

import android.app.Activity;
import android.app.KeyguardManager;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

/** The full-screen alarm shown over the lock screen. */
public class AlarmActivity extends Activity {

    static final String EXTRA_AT = "at";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showOverLockScreen();
        setContentView(R.layout.activity_alarm);

        long at = getIntent().getLongExtra(EXTRA_AT, System.currentTimeMillis());
        String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(at));
        ((TextView) findViewById(R.id.alarmDue)).setText("Due " + time);

        Button took = findViewById(R.id.alarmTook);
        took.setOnClickListener(v -> {
            AlarmNotifier.cancel(this);
            startActivity(MainActivity.tookIntent(this));
            finish();
        });

        Button dismiss = findViewById(R.id.alarmDismiss);
        dismiss.setOnClickListener(v -> {
            AlarmNotifier.cancel(this);
            finish();
        });
    }

    private void showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager keyguard = getSystemService(KeyguardManager.class);
            if (keyguard != null) keyguard.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
