package com.medtracker.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** "Dismiss" button on the notification: stop ringing. */
public class DismissReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AlarmNotifier.cancel(context);
    }
}
