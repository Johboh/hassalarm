package com.fjun.hassalarm

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Context.JOB_SCHEDULER_SERVICE
import android.content.Context.MODE_PRIVATE
import android.util.Log
import java.util.HashSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private const val MAX_EXECUTION_DELAY_MS = 3600L * 1000L // 1h
private const val BACKOFF_MS = 30L * 1000L // 30s
private const val JOB_ID = 0

class NextAlarmUpdaterJob : JobService() {
    private var job: Job? = null

    override fun onStartJob(jobParameters: JobParameters): Boolean {
        Log.d(LOG_TAG, "NextAlarmUpdaterJob started.")
        job?.cancel()
        job = CoroutineScope(Dispatchers.IO).launch {
            val successful = AlarmUpdater.performUpdate(this@NextAlarmUpdaterJob)
            jobFinished(jobParameters, !successful)
        }
        return true
    }

    override fun onStopJob(jobParameters: JobParameters): Boolean {
        Log.d(LOG_TAG, "NextAlarmUpdaterJob stopped.")
        job?.cancel()
        return true
    }

    companion object {
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun createRequest(context: Context): UpdateRequest =
            AlarmUpdater.createRequest(context)

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun createRequest(
            context: Context,
            hostInput: String,
            apiKeyOrToken: String,
            entityId: String?,
            accessType: AccessType?,
            entityIdIsLegacy: Boolean,
            ignoredPackages: Set<String>
        ): UpdateRequest =
            AlarmUpdater.createRequest(
                context,
                hostInput,
                apiKeyOrToken,
                entityId,
                accessType,
                entityIdIsLegacy,
                ignoredPackages
            )

        /**
         * Schedule a job to update the next alarm once we have some kind of network connection.
         */
        fun scheduleJob(context: Context) {
            Log.d(LOG_TAG, "Scheduling job")
            val deadlineMs = deadline(context)
            val jobInfo = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, NextAlarmUpdaterJob::class.java)
            )
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .setBackoffCriteria(BACKOFF_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setOverrideDeadline(deadlineMs)
                .build()
            val jobScheduler = context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.schedule(jobInfo)
        }

        fun markAsDone(context: Context, successful: Boolean, triggerTimestamp: Long) {
            AlarmUpdater.markAsDone(context, successful, triggerTimestamp)
        }

        private fun alarmClockInfo(context: Context): AlarmClockInfo? {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            return alarmManager?.nextAlarmClock
        }

        private fun getTriggerTime(context: Context): Long {
            val nextAlarm = alarmClockInfo(context)
            return nextAlarm?.triggerTime ?: 0
        }

        private fun deadline(context: Context): Long {
            val sharedPreferences =
                context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val alarmClockInfo = alarmClockInfo(context)
            val pendingIntent = alarmClockInfo?.showIntent
            val packageName = pendingIntent?.creatorPackage ?: "<no-package>"
            val ignoredPackages =
                sharedPreferences.getStringSet(KEY_IGNORED_PACKAGES, HashSet()) ?: setOf()
            if (ignoredPackages.contains(packageName)) {
                return MAX_EXECUTION_DELAY_MS
            }
            val triggerTime = getTriggerTime(context)
            val now = System.currentTimeMillis()
            return if (triggerTime > now) {
                val halfDiff = (triggerTime - now) / 2
                // Ensure deadline is at least 30 seconds and at most 1 hour
                min(MAX_EXECUTION_DELAY_MS, max(30_000L, halfDiff))
            } else {
                // If there's no upcoming alarm or alarm cleared, deadline is 30 seconds
                30_000L
            }
        }
    }
}