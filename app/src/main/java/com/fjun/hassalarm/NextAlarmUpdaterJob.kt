package com.fjun.hassalarm

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.fjun.hassalarm.UnsafeOkHttpClient.unsafeOkHttpClient
import com.fjun.hassalarm.history.AppDatabase.Companion.getDatabase
import com.fjun.hassalarm.history.Publish
import com.fjun.hassalarm.history.PublishDao
import java.io.IOException
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.math.max
import kotlin.math.min

private val DATE_FORMAT_LEGACY = SimpleDateFormat("yyyy-MM-dd HH:mm:00", Locale.ENGLISH)
private const val MAX_EXECUTION_DELAY_MS = 3600L * 1000L // 1h
private const val BACKOFF_MS = 60L * 1000L // 1m
private const val MINIMUM_LATENCY_MS = 5L * 1000L // 5s
private const val JOB_ID = 0

class NextAlarmUpdaterJob : JobService() {
    private var call: Call<ResponseBody>? = null

    private var insertJob: Job? = null

    override fun onStartJob(jobParameters: JobParameters): Boolean {
        Log.d(LOG_TAG, "Starting job.")
        if (call != null) {
            Log.d(LOG_TAG, "Canceling previously retrofit job.")
            call?.cancel()
        }
        val triggerTimestamp: Long
        val creatorPackage: String?
        try {
            val request = createRequest(this)
            triggerTimestamp = request.triggerTimestamp
            creatorPackage = request.creatorPackage
            call = request.call
        } catch (e: IllegalArgumentException) {
            Log.e(LOG_TAG, "Failed to create request: " + e.message)
            jobFinished(jobParameters, true)
            markAsDone(this, false, 0)
            insertPublish(Publish(System.currentTimeMillis(), false, 0L, e.message, null))
            return false
        }

        // No need to publish the same timestamp if we already had an successful publish
        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val lastPublishedTriggerTime =
            sharedPreferences.getLong(LAST_PUBLISHED_TRIGGER_TIMESTAMP, -1)
        if (triggerTimestamp == lastPublishedTriggerTime) {
            Log.d(LOG_TAG, "Trigger timestamp already published. Bail.")
            jobFinished(jobParameters, false)
            return false
        }
        Log.d(LOG_TAG, "Enqueueing retrofit job.")
        call?.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                var successful = false
                var message = ""
                try {
                    val body = response.body()
                    if (body != null) {
                        message = response.raw().toString()
                        Log.d(LOG_TAG, "Retofit succeeded: $message")
                        successful = true
                    } else if (response.errorBody() != null) {
                        message = response.errorBody()?.string().orEmpty()
                        Log.e(LOG_TAG, "Retofit failed: $message")
                    } else {
                        message = response.code().toString()
                        Log.e(LOG_TAG, "Retofit failed with code: $message")
                    }
                } catch (e: IOException) {
                    Log.e(LOG_TAG, "Retofit failed: " + e.message)
                }
                jobFinished(jobParameters, !successful)
                markAsDone(this@NextAlarmUpdaterJob, successful, triggerTimestamp)
                insertPublish(
                    Publish(
                        System.currentTimeMillis(),
                        successful,
                        triggerTimestamp,
                        message,
                        creatorPackage
                    )
                )
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                val message = t.message
                Log.e(NextAlarmUpdaterJob::class.java.name, "Retofit failed: $message")
                // Fail, reschedule job.
                jobFinished(jobParameters, true)
                markAsDone(this@NextAlarmUpdaterJob, false, 0)
                insertPublish(
                    Publish(
                        System.currentTimeMillis(),
                        false,
                        triggerTimestamp,
                        message,
                        creatorPackage
                    )
                )
            }
        })
        return true
    }

    override fun onStopJob(jobParameters: JobParameters): Boolean {
        Log.d(LOG_TAG, "Stopping job.")
        call?.cancel()
        insertJob?.cancel()
        return true
    }

    @Throws(IllegalArgumentException::class)
    fun createRequest(context: Context): UpdateRequest {
        val sharedPreferences =
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val host = sharedPreferences.getString(KEY_PREFS_HOST, "").orEmpty()
        val apiKeyOrToken = sharedPreferences.getString(KEY_PREFS_API_KEY, "").orEmpty()
        val entityId = Migration.getEntityId(sharedPreferences)
        val accessType = Migration.getAccessType(sharedPreferences)
        val entityIdIsLegacy = Migration.entityIdIsLegacy(sharedPreferences)
        val ignoredPackages = sharedPreferences.getStringSet(
            KEY_IGNORED_PACKAGES,
            HashSet()
        ) ?: setOf()
        return createRequest(
            context,
            host,
            apiKeyOrToken,
            entityId,
            accessType,
            entityIdIsLegacy,
            ignoredPackages
        )
    }

    private fun database(): PublishDao = getDatabase(applicationContext).publishDao()


    private fun insertPublish(publish: Publish) {
        insertJob?.cancel()
        insertJob = CoroutineScope(Dispatchers.IO).launch {
            database().insertAll(publish)
        }
    }

    companion object {
        /**
         * Create a call that can be executed. Will throw an exception in case of any failure,
         * like missing parameters etc.
         */
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
        ): UpdateRequest {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            val alarmClockInfo = alarmManager.nextAlarmClock

            // Verify host and API key.
            require(hostInput.isNotEmpty()) { "Host is missing. You need to specify the host to your hass.io instance." }

            var hostToUse = hostInput
            if (!hostToUse.startsWith("http://") && !hostToUse.startsWith("https://")) {
                hostToUse = "http://$hostToUse"
            }
            val uri = URI(hostToUse)
            if (uri.port == -1) {
                hostToUse = "$hostToUse:$DEFAULT_PORT"
            }

            // Support empty API key, if there is no one required.
            val retrofit = Retrofit.Builder()
                .baseUrl(hostToUse)
                .client(unsafeOkHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val hassApi = retrofit.create(HassApi::class.java)

            // Get next scheduled alarm, if any.
            val state: State
            val datetime: Datetime
            val triggerTimestamp: Long
            val creatorPackage: String?
            if (alarmClockInfo != null) {
                // Ignored package?
                val showIntent = alarmClockInfo.showIntent
                val packageName =
                    if (showIntent != null) showIntent.creatorPackage else "<no-pending-intent>"
                if (packageName != null && ignoredPackages.contains(packageName)) {
                    // Ignore!
                    Log.d(
                        LOG_TAG,
                        "Package $packageName is in ignored list. Ignoring alarm for this package."
                    )
                    triggerTimestamp = 0
                    creatorPackage = null
                    state = State("")
                    datetime = Datetime(entityId, 1)
                } else {
                    triggerTimestamp = alarmClockInfo.triggerTime
                    creatorPackage = packageName
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = triggerTimestamp
                    state = State(DATE_FORMAT_LEGACY.format(calendar.time))
                    datetime = Datetime(entityId, triggerTimestamp / 1000)
                }
            } else {
                triggerTimestamp = 0
                creatorPackage = ""
                state = State("")
                datetime = Datetime(entityId, 1)
            }
            Log.d(LOG_TAG, "Setting time to " + datetime.timestamp)

            // Enqueue call and run on background thread.
            // Check if it is using long lived access tokens
            val call: Call<ResponseBody> = if (accessType == AccessType.LONG_LIVED_TOKEN) {
                // Create Authorization Header value
                val bearer = "Bearer $apiKeyOrToken"
                if (entityIdIsLegacy) {
                    hassApi.updateStateUsingToken(state, entityId, bearer)
                } else {
                    hassApi.setInputDatetimeUsingToken(datetime, bearer)
                }
            } else {
                if (accessType == AccessType.WEB_HOOK) {
                    hassApi.updateStateUsingWebhook(datetime, apiKeyOrToken)
                } else if (entityIdIsLegacy) {
                    hassApi.updateStateUsingApiKey(state, entityId, apiKeyOrToken)
                } else {
                    hassApi.setInputDatetimeUsingApiKey(datetime, apiKeyOrToken)
                }
            }
            return UpdateRequest(triggerTimestamp, call, creatorPackage)
        }

        /**
         * Schedule a job to update the next alarm once we have some kind of network connection.
         */
        fun scheduleJob(context: Context) {
            Log.d(LOG_TAG, "Scheduling job")
            val jobInfo = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, NextAlarmUpdaterJob::class.java)
            )
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .setBackoffCriteria(BACKOFF_MS, JobInfo.BACKOFF_POLICY_LINEAR)
                .setMinimumLatency(MINIMUM_LATENCY_MS)
                .setOverrideDeadline(deadline(context))
                .build()
            val jobScheduler = context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.schedule(jobInfo)
        }

        fun markAsDone(context: Context, successful: Boolean, triggerTimestamp: Long) {
            val sharedPreferences =
                context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val timestamp = System.currentTimeMillis()
            sharedPreferences
                .edit {
                    putBoolean(LAST_PUBLISH_WAS_SUCCESSFUL, successful)
                    putLong(LAST_PUBLISH_ATTEMPT, timestamp)
                    if (successful) {
                        putLong(LAST_SUCCESSFUL_PUBLISH, timestamp)
                        putLong(
                            LAST_PUBLISHED_TRIGGER_TIMESTAMP,
                            triggerTimestamp
                        )
                    }
                }
        }

        private fun alarmClockInfo(context: Context): AlarmClockInfo? {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            return alarmManager.nextAlarmClock
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
            val packageName =
                if (pendingIntent != null) {
                    pendingIntent.creatorPackage
                } else {
                    "<no-package>"
                }
            val ignoredPackages =
                sharedPreferences.getStringSet(KEY_IGNORED_PACKAGES, HashSet()) ?: setOf()
            return if (packageName != null && ignoredPackages.contains(packageName)) {
                MAX_EXECUTION_DELAY_MS
            } else {
                val triggerTime = getTriggerTime(context)
                val now = System.currentTimeMillis()
                val halfDiff = max(0, triggerTime - now) / 2
                min(MAX_EXECUTION_DELAY_MS, halfDiff)
            }
        }
    }
}