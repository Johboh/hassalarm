package com.fjun.hassalarm;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Listen for the global ACTION_NEXT_ALARM_CLOCK_CHANGED,
 * sent by the system when (decent) alarm clock apps schedule a new next alarm.
 * I.e. when the alarm is displayed in the toolbar/lock screen by the OS.
 */
public class NextAlarmBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        if (!AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED.equalsIgnoreCase(intent.getAction())) {
            return;
        }

        NextAlarmUpdater.scheduleJob(context);
    }
}
