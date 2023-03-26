package com.fjun.hassalarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Listen for the global ACTION_NEXT_ALARM_CLOCK_CHANGED,
 * sent by the system when (decent) alarm clock apps schedule a new next alarm.
 * I.e. when the alarm is displayed in the toolbar/lock screen by the OS.
 */
class NextAlarmBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) {
            return
        }
        val isBootIntent = Intent.ACTION_BOOT_COMPLETED.equals(intent.action, ignoreCase = true)
        val isNextAlarmIntent =
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED.equals(intent.action, ignoreCase = true)
        if (!isBootIntent && !isNextAlarmIntent) {
            return
        }
        Log.d(LOG_TAG, "Got intent. Boot: $isBootIntent, next alarm: $isNextAlarmIntent")
        NextAlarmUpdaterJob.scheduleJob(context)
    }
}