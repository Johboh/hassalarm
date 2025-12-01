package com.fjun.hassalarm

import android.app.AlarmManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log

private const val JOB_ID = 1
private const val PERIODIC_INTERVAL_MS = 2 * 3600L * 1000L // 2h

class SanityCheckJob : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(LOG_TAG, "Sanity check job started.")

        val alarmManager = getSystemService(AlarmManager::class.java)
        val nextAlarm = alarmManager.nextAlarmClock
        val nextAlarmTriggerTime = nextAlarm?.triggerTime ?: 0

        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val lastPublishedTriggerTime = sharedPreferences.getLong(LAST_PUBLISHED_TRIGGER_TIMESTAMP, -1)

        if (nextAlarmTriggerTime != lastPublishedTriggerTime) {
            Log.d(LOG_TAG, "Sanity check found discrepancy. Scheduling update job.")
            NextAlarmUpdaterJob.scheduleJob(this)
        } else {
            Log.d(LOG_TAG, "Sanity check passed.")
        }

        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }

    companion object {
        fun scheduleJob(context: Context) {
            Log.d(LOG_TAG, "Scheduling sanity check job")
            val jobScheduler = context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
            val jobInfo = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, SanityCheckJob::class.java)
            )
                .setPeriodic(PERIODIC_INTERVAL_MS)
                .setRequiresDeviceIdle(false)
                .setRequiresCharging(false)
                .build()
            jobScheduler.schedule(jobInfo)
        }
    }
}
