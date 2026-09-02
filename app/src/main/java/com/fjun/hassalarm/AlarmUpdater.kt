package com.fjun.hassalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import com.fjun.hassalarm.UnsafeOkHttpClient.unsafeOkHttpClient
import com.fjun.hassalarm.history.AppDatabase
import com.fjun.hassalarm.history.Publish
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.HashSet
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Central coordinator for updating the next alarm to Home Assistant.
 * Handles request creation, synchronous execution, aggressive retries,
 * mutex locking, and network monitoring.
 */
object AlarmUpdater {

    private const val RETRY_REQUEST_CODE = 42
    private val updateMutex = Mutex()

    // Retry delays in milliseconds: 15s, 30s, 1m, 2m, 5m
    private val RETRY_DELAYS_MS = longArrayOf(
        15_000L,
        30_000L,
        60_000L,
        120_000L,
        300_000L
    )

    /**
     * Create an update request based on current SharedPreferences and system alarm.
     */
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

    /**
     * Create a call that can be executed. Will throw an exception in case of any failure,
     * like missing parameters etc.
     */
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
        val alarmClockInfo = alarmManager?.nextAlarmClock

        // Normalize + verify host
        var hostToUse = hostInput.trim()

        require(hostToUse.isNotEmpty()) {
            "Host is missing. You need to specify the host to your hass.io instance."
        }

        // Add scheme if missing
        if (!hostToUse.startsWith("http://") && !hostToUse.startsWith("https://")) {
            hostToUse = "http://$hostToUse"
        }

        // Validate URI safely
        val uri = try {
            URI(hostToUse)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid host: $hostToUse, $e")
        }

        require(!uri.host.isNullOrEmpty()) {
            "Invalid host. Please enter a valid hostname or IP address."
        }

        // Add default port if missing
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
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:00", Locale.ENGLISH)
                state = State(dateFormat.format(calendar.time))
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
     * Perform the alarm update synchronously on Dispatchers.IO.
     * Guaranteed to execute sequentially via Mutex to avoid duplicate network calls.
     *
     * @param context Application or component context
     * @param force Force update even if trigger timestamp matches the previous successful publish
     * @return true if update succeeded or was already up to date, false on failure
     */
    suspend fun performUpdate(context: Context, force: Boolean = false): Boolean {
        val appContext = context.applicationContext
        return updateMutex.withLock {
            withContext(Dispatchers.IO) {
                doUpdate(appContext, force)
            }
        }
    }

    private suspend fun doUpdate(context: Context, force: Boolean): Boolean {
        Log.d(LOG_TAG, "Starting alarm update check (force=$force).")
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val request: UpdateRequest = try {
            createRequest(context)
        } catch (e: IllegalArgumentException) {
            val errorMsg = e.message ?: "Configuration error"
            Log.e(LOG_TAG, "Failed to create request: $errorMsg")
            markAsDone(context, false, 0)
            insertPublish(context, Publish(System.currentTimeMillis(), false, 0L, errorMsg, null))
            // Permanent configuration error - do not retry aggressively
            cancelRetryAlarm(context)
            NetworkMonitor.stopMonitoring(context)
            return false
        }

        val triggerTimestamp = request.triggerTimestamp
        val creatorPackage = request.creatorPackage

        val lastPublishedTriggerTime =
            sharedPreferences.getLong(LAST_PUBLISHED_TRIGGER_TIMESTAMP, -1)
        val lastPublishWasSuccessful =
            sharedPreferences.getBoolean(LAST_PUBLISH_WAS_SUCCESSFUL, false)

        if (!force && triggerTimestamp == lastPublishedTriggerTime && lastPublishWasSuccessful) {
            Log.d(LOG_TAG, "Trigger timestamp $triggerTimestamp already published successfully. Bail.")
            cancelRetryAlarm(context)
            NetworkMonitor.stopMonitoring(context)
            return true
        }

        Log.d(LOG_TAG, "Executing retrofit call for triggerTimestamp: $triggerTimestamp")
        val call = request.call

        try {
            val response = call.execute()
            val successful = response.isSuccessful
            val message: String = if (successful) {
                val raw = response.raw().toString()
                Log.d(LOG_TAG, "Retrofit succeeded: $raw")
                raw
            } else {
                val errorBody = try {
                    response.errorBody()?.string()
                } catch (e: IOException) {
                    null
                }
                val failMsg = if (!errorBody.isNullOrBlank()) {
                    errorBody
                } else {
                    "Request failed with code: ${response.code()}"
                }
                Log.e(LOG_TAG, "Retrofit failed with HTTP ${response.code()}: $failMsg")
                failMsg
            }

            markAsDone(context, successful, triggerTimestamp)
            insertPublish(
                context,
                Publish(
                    System.currentTimeMillis(),
                    successful,
                    triggerTimestamp,
                    message,
                    creatorPackage
                )
            )

            if (successful) {
                cancelRetryAlarm(context)
                NetworkMonitor.stopMonitoring(context)
                return true
            } else {
                val isServerError = response.code() in 500..599
                if (isServerError) {
                    // Server error is transient - retry aggressively
                    scheduleRetry(context)
                } else {
                    // 4xx client errors (e.g. 401 unauthorized, 404 not found) are configuration issues
                    cancelRetryAlarm(context)
                    NetworkMonitor.stopMonitoring(context)
                }
                return false
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: e.javaClass.simpleName
            Log.e(LOG_TAG, "Retrofit execution failed: $errorMsg")
            markAsDone(context, false, 0)
            insertPublish(
                context,
                Publish(
                    System.currentTimeMillis(),
                    false,
                    triggerTimestamp,
                    errorMsg,
                    creatorPackage
                )
            )
            // Network/connection failure - retry aggressively
            scheduleRetry(context)
            return false
        }
    }

    private fun scheduleRetry(context: Context) {
        scheduleRetryAlarm(context)
        NextAlarmUpdaterJob.scheduleJob(context)
        NetworkMonitor.startMonitoring(context)
    }

    fun scheduleRetryAlarm(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val retryCount = prefs.getInt(KEY_RETRY_COUNT, 0)

        var delay = if (retryCount < RETRY_DELAYS_MS.size) {
            RETRY_DELAYS_MS[retryCount]
        } else {
            RETRY_DELAYS_MS.last()
        }

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val nextAlarm = alarmManager?.nextAlarmClock
        val triggerTimestamp = nextAlarm?.triggerTime ?: 0L
        val now = System.currentTimeMillis()

        if (triggerTimestamp > now) {
            val remaining = triggerTimestamp - now
            // Ensure retry fires well before the alarm actually triggers
            val maxAllowed = max(10_000L, remaining / 2)
            delay = min(delay, maxAllowed)
        }

        prefs.edit { putInt(KEY_RETRY_COUNT, retryCount + 1) }

        val intent = Intent(context, NextAlarmBroadcastReceiver::class.java).apply {
            action = ACTION_RETRY_UPDATE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            RETRY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = now + delay
        Log.d(LOG_TAG, "Scheduling retry alarm #$retryCount in ${delay / 1000}s (at $triggerAtMillis)")

        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        }
    }

    fun cancelRetryAlarm(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putInt(KEY_RETRY_COUNT, 0) }

        val intent = Intent(context, NextAlarmBroadcastReceiver::class.java).apply {
            action = ACTION_RETRY_UPDATE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            RETRY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            alarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(LOG_TAG, "Cancelled pending retry alarm.")
        }
    }

    fun markAsDone(context: Context, successful: Boolean, triggerTimestamp: Long) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val timestamp = System.currentTimeMillis()
        sharedPreferences.edit {
            putBoolean(LAST_PUBLISH_WAS_SUCCESSFUL, successful)
            putLong(LAST_PUBLISH_ATTEMPT, timestamp)
            if (successful) {
                putLong(LAST_SUCCESSFUL_PUBLISH, timestamp)
                putLong(LAST_PUBLISHED_TRIGGER_TIMESTAMP, triggerTimestamp)
            }
        }
    }

    private suspend fun insertPublish(context: Context, publish: Publish) {
        try {
            AppDatabase.getDatabase(context).publishDao().insertAll(publish)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to insert publish record: ${e.message}")
        }
    }
}
