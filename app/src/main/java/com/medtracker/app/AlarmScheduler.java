package com.medtracker.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * Hands the reminder time to Android's alarm system, so it rings
 * even when the app is closed or the screen is off.
 * The time is also saved, so it can be set again after a restart.
 */
public final class AlarmScheduler {

    private static final String PREFS = "medtracker";
    private static final String KEY_AT = "alarm_at";

    private AlarmScheduler() {}

    /** Set (or move) the alarm to the given time in milliseconds. */
    public static void schedule(Context context, long at) {
        prefs(context).edit().putLong(KEY_AT, at).apply();

        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        PendingIntent fire = firePendingIntent(context);

        if (Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()) {
            // An "alarm clock" alarm: exact, wakes the phone, shows the alarm icon
            Intent open = new Intent(context, MainActivity.class);
            PendingIntent show = PendingIntent.getActivity(context, 1, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), fire);
        } else {
            // Exact alarms not allowed: Android may deliver this a few minutes late
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire);
        }
    }

    /** Remove the alarm. */
    public static void cancel(Context context) {
        prefs(context).edit().remove(KEY_AT).apply();
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        alarmManager.cancel(firePendingIntent(context));
    }

    /** The saved alarm time, or 0 when there is none. */
    public static long savedTime(Context context) {
        return prefs(context).getLong(KEY_AT, 0);
    }

    /** Called when the alarm has rung: forget the saved time. */
    public static void markFired(Context context) {
        prefs(context).edit().remove(KEY_AT).apply();
    }

    /** After a restart or app update: set the alarm again, or ring now if it was missed. */
    public static void restore(Context context) {
        long at = savedTime(context);
        if (at == 0) return;
        if (at > System.currentTimeMillis()) {
            schedule(context, at);
        } else {
            markFired(context);
            AlarmNotifier.show(context, at);
        }
    }

    private static PendingIntent firePendingIntent(Context context) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
