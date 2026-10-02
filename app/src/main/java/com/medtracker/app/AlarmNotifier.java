package com.medtracker.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;

import androidx.core.app.NotificationCompat;

import java.text.DateFormat;
import java.util.Date;

/**
 * Builds the ringing alarm notification. It keeps sounding until you
 * press "I took it" or "Dismiss", and opens the full-screen alarm
 * when the phone is locked.
 */
public final class AlarmNotifier {

    static final String CHANNEL_ID = "medicine_alarm";
    static final int NOTIFICATION_ID = 42;

    private AlarmNotifier() {}

    static void ensureChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Medicine alarm", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Rings when it is time to take your medicine");

        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        channel.setSound(sound, attributes);
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 700, 400, 700});
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.setBypassDnd(true);
        manager.createNotificationChannel(channel);
    }

    static void show(Context context, long at) {
        ensureChannel(context);

        Intent fullScreen = new Intent(context, AlarmActivity.class)
                .putExtra(AlarmActivity.EXTRA_AT, at)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent fullScreenPi = PendingIntent.getActivity(context, 2, fullScreen,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent tookPi = PendingIntent.getActivity(context, 3, MainActivity.tookIntent(context),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent dismissPi = PendingIntent.getBroadcast(context, 4,
                new Intent(context, DismissReceiver.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(at));

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notify)
                .setColor(0xFFFF6A13)
                .setContentTitle("Take your medicine")
                .setContentText("Reminder for " + time)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreenPi, true)
                .setContentIntent(fullScreenPi)
                .setOngoing(true)
                .setAutoCancel(false)
                .addAction(0, "I took it", tookPi)
                .addAction(0, "Dismiss", dismissPi)
                .build();

        // Repeat the sound until the notification is removed
        notification.flags |= Notification.FLAG_INSISTENT;

        try {
            context.getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification);
        } catch (SecurityException e) {
            // Notifications are not allowed. The setup list in the app asks for this.
        }
    }

    static void cancel(Context context) {
        context.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
    }
}
