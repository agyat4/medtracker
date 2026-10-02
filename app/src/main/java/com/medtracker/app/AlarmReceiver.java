package com.medtracker.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Android calls this at the alarm time. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        long at = AlarmScheduler.savedTime(context);
        if (at == 0) at = System.currentTimeMillis();
        AlarmScheduler.markFired(context);
        AlarmNotifier.show(context, at);
    }
}
