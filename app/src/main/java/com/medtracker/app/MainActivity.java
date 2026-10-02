package com.medtracker.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebViewAssetLoader;

/**
 * The app's main screen: a WebView showing the HTML app from the assets folder,
 * plus a "MedAndroid" bridge so the page can set real phone alarms.
 */
public class MainActivity extends Activity {

    static final String EXTRA_ACTION = "action";
    private static final String APP_URL = "https://appassets.androidplatform.net/assets/www/index.html";
    private static final int REQUEST_NOTIFICATIONS = 7;

    private WebView webView;
    private boolean pageReady = false;
    private String pendingEvent = null;

    /** Intent that opens the app and logs a dose (used by the alarm's "I took it"). */
    static Intent tookIntent(Context context) {
        return new Intent(context, MainActivity.class)
                .putExtra(EXTRA_ACTION, "took")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Draw behind the status bar and pad the content so nothing hides under it
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF5B2D0D, 0xFF2B1507, 0xFF180B03}));
        WindowInsetsControllerCompat bars = new WindowInsetsControllerCompat(getWindow(), root);
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bar = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bar.left, bar.top, bar.right, bar.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF2B1507);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);   // needed for localStorage (your dose history)
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        // Serves files from app/src/main/assets under a safe https address
        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if ("appassets.androidplatform.net".equals(url.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, url));
                } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                if (pendingEvent != null) {
                    String event = pendingEvent;
                    pendingEvent = null;
                    sendEvent(event);
                }
            }
        });

        webView.addJavascriptInterface(new Bridge(), "MedAndroid");
        AlarmNotifier.ensureChannel(this);
        handleIntent(getIntent());
        webView.loadUrl(APP_URL);
        askForNotificationsOnFirstRun();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        if (pageReady) sendEvent("resume");
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (pageReady) sendEvent("resume");
    }

    private void handleIntent(Intent intent) {
        if (intent != null && "took".equals(intent.getStringExtra(EXTRA_ACTION))) {
            intent.removeExtra(EXTRA_ACTION);
            AlarmNotifier.cancel(this);
            sendEvent("took");
        }
    }

    /** Tell the page something happened, e.g. window.onNativeEvent('took'). */
    private void sendEvent(String name) {
        if (!pageReady) {
            pendingEvent = name;
            return;
        }
        webView.evaluateJavascript(
                "window.onNativeEvent && window.onNativeEvent('" + name + "')", null);
    }

    private void askForNotificationsOnFirstRun() {
        if (Build.VERSION.SDK_INT < 33) return;
        SharedPreferences prefs = getSharedPreferences("medtracker", MODE_PRIVATE);
        if (prefs.getBoolean("asked_notifications", false)) return;
        prefs.edit().putBoolean("asked_notifications", true).apply();
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    // ---------- Status of each permission the alarm needs ----------

    private boolean notificationsAllowed() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return getSystemService(NotificationManager.class).areNotificationsEnabled();
    }

    private boolean exactAlarmsAllowed() {
        return Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    private boolean fullScreenAllowed() {
        return Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager.class).canUseFullScreenIntent();
    }

    private boolean batteryUnrestricted() {
        return getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
    }

    private void openSettings(String action, boolean withPackage) {
        Intent intent = new Intent(action);
        if (withPackage) intent.setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (Exception e) {
            // Some phones lack a specific screen: fall back to this app's info page
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {}
        }
    }

    /** Methods the web page can call as MedAndroid.xxx(). They run on a background thread. */
    private class Bridge {

        @JavascriptInterface
        public void scheduleAlarm(String at) {
            try {
                AlarmScheduler.schedule(getApplicationContext(), Long.parseLong(at));
            } catch (NumberFormatException ignored) {}
        }

        @JavascriptInterface
        public void cancelAlarm() {
            AlarmScheduler.cancel(getApplicationContext());
        }

        @JavascriptInterface
        public String getSetupStatus() {
            return "{\"notifications\":" + notificationsAllowed()
                    + ",\"exactAlarms\":" + exactAlarmsAllowed()
                    + ",\"fullScreen\":" + fullScreenAllowed()
                    + ",\"battery\":" + batteryUnrestricted() + "}";
        }

        @JavascriptInterface
        public void openSetup(String key) {
            runOnUiThread(() -> {
                switch (key) {
                    case "notifications":
                        if (Build.VERSION.SDK_INT >= 33
                                && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
                        } else {
                            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                            try { startActivity(intent); } catch (Exception e) {
                                openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true);
                            }
                        }
                        break;
                    case "exactAlarms":
                        if (Build.VERSION.SDK_INT >= 31) {
                            openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, true);
                        }
                        break;
                    case "fullScreen":
                        if (Build.VERSION.SDK_INT >= 34) {
                            openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, true);
                        }
                        break;
                    case "battery":
                        openSettings(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true);
                        break;
                    default:
                        break;
                }
            });
        }
    }
}
