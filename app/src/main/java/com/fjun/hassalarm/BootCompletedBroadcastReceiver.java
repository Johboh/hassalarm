package com.fjun.hassalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Schedule job on boot.
 */
public class BootCompletedBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        if (!Intent.ACTION_BOOT_COMPLETED.equalsIgnoreCase(intent.getAction())) {
            return;
        }

        NextAlarmUpdaterJob.scheduleJob(context);
    }
}
