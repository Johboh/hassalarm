package com.fjun.hassalarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Listen for the global ACTION_NEXT_ALARM_CLOCK_CHANGED,
 * sent by the system when alarm clock apps schedule a new next alarm,
 * device boot completion, or internal aggressive retry intents.
 */
class NextAlarmBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) {
            return
        }
        val isBootIntent = Intent.ACTION_BOOT_COMPLETED.equals(intent.action, ignoreCase = true)
        val isNextAlarmIntent =
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED.equals(intent.action, ignoreCase = true)
        val isRetryIntent =
            ACTION_RETRY_UPDATE.equals(intent.action, ignoreCase = true)

        if (!isBootIntent && !isNextAlarmIntent && !isRetryIntent) {
            return
        }

        Log.d(
            LOG_TAG,
            "Got intent. Boot: $isBootIntent, next alarm: $isNextAlarmIntent, retry: $isRetryIntent"
        )

        // Ensure sanity check job remains scheduled
        SanityCheckJob.scheduleJob(context)

        // Reset retry counter on brand-new alarm change or reboot
        if (isNextAlarmIntent || isBootIntent) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_RETRY_COUNT, 0).apply()
        }

        // Use goAsync to immediately execute the update in the background with an active wakelock
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmUpdater.performUpdate(context)
            } catch (t: Throwable) {
                Log.e(LOG_TAG, "Failed during background update execution: ${t.message}", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}