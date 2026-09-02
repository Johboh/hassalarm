package com.fjun.hassalarm

import android.app.AlarmManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val JOB_ID = 1
private const val PERIODIC_INTERVAL_MS = 15L * 60L * 1000L // 15m (minimum interval for periodic JobScheduler)

class SanityCheckJob : JobService() {
    private var job: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(LOG_TAG, "Sanity check job started.")

        val alarmManager = getSystemService(AlarmManager::class.java)
        val nextAlarm = alarmManager?.nextAlarmClock
        val nextAlarmTriggerTime = nextAlarm?.triggerTime ?: 0L

        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val lastPublishedTriggerTime =
            sharedPreferences.getLong(LAST_PUBLISHED_TRIGGER_TIMESTAMP, -1)
        val lastPublishWasSuccessful =
            sharedPreferences.getBoolean(LAST_PUBLISH_WAS_SUCCESSFUL, false)

        val needsUpdate = (nextAlarmTriggerTime != lastPublishedTriggerTime) || !lastPublishWasSuccessful

        if (needsUpdate) {
            Log.d(
                LOG_TAG,
                "Sanity check found discrepancy or previous failure. Performing update."
            )
            job?.cancel()
            job = CoroutineScope(Dispatchers.IO).launch {
                AlarmUpdater.performUpdate(this@SanityCheckJob)
                jobFinished(params, false)
            }
            return true
        } else {
            Log.d(LOG_TAG, "Sanity check passed.")
            jobFinished(params, false)
            return false
        }
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        job?.cancel()
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
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresDeviceIdle(false)
                .setRequiresCharging(false)
                .build()
            jobScheduler.schedule(jobInfo)
        }
    }
}
