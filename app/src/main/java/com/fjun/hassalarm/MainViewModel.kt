package com.fjun.hassalarm

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.HashSet
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _publishStatus = MutableStateFlow("")
    val publishStatus: StateFlow<String> = _publishStatus.asStateFlow()

    private val _lastPublishAt = MutableStateFlow("")
    val lastPublishAt: StateFlow<String> = _lastPublishAt.asStateFlow()

    private val _lastSuccessfulPublishAt = MutableStateFlow("")
    val lastSuccessfulPublishAt: StateFlow<String> = _lastSuccessfulPublishAt.asStateFlow()

    private val _nextAlarm = MutableStateFlow("")
    val nextAlarm: StateFlow<String> = _nextAlarm.asStateFlow()

    init {
        updateView()
    }

    fun updateView() {
        val wasSuccessful = sharedPreferences.getBoolean(LAST_PUBLISH_WAS_SUCCESSFUL, false)
        val lastAttempt = sharedPreferences.getLong(LAST_PUBLISH_ATTEMPT, 0)
        val lastSuccessfulAttempt = sharedPreferences.getLong(LAST_SUCCESSFUL_PUBLISH, 0)
        val lastPublishedTriggerTime = sharedPreferences.getLong(LAST_PUBLISHED_TRIGGER_TIMESTAMP, -1)
        val triggerTime = getTriggerTime()

        if (wasSuccessful) {
            _publishStatus.value = getApplication<Application>().getString(R.string.published_successfully)
            _lastSuccessfulPublishAt.value = ""
            _lastPublishAt.value = getLastPublishAtString(R.string.last_publish_at, lastAttempt)
        } else if (triggerTime > 0 && triggerTime == lastPublishedTriggerTime) {
            _publishStatus.value = getApplication<Application>().getString(R.string.published_successfully)
            _lastSuccessfulPublishAt.value = ""
            _lastPublishAt.value = getLastPublishAtString(R.string.last_successful_publish_at, lastSuccessfulAttempt)
        } else {
            if (lastAttempt > 0) {
                _publishStatus.value = getApplication<Application>().getString(R.string.failed_to_publish)
                _lastPublishAt.value = getLastPublishAtString(R.string.last_failed_publish_at, lastAttempt)
            } else {
                _publishStatus.value = getApplication<Application>().getString(R.string.failed_no_connection)
                _lastPublishAt.value = ""
            }
            if (lastSuccessfulAttempt > 0) {
                _lastSuccessfulPublishAt.value = getLastPublishAtString(R.string.last_successful_publish_at, lastSuccessfulAttempt)
            } else {
                _lastSuccessfulPublishAt.value = ""
            }
        }
        _nextAlarm.value = getNextAlarmString()
    }

    private fun getLastPublishAtString(res: Int, time: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = time
        return getApplication<Application>().getString(
            res,
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(calendar.time)
        )
    }

    private fun getNextAlarmString(): String {
        val triggerTime = getTriggerTime()
        val ignoredPackages = sharedPreferences.getStringSet(KEY_IGNORED_PACKAGES, HashSet())
        val alarmClockInfo = getAlarmClockInfo()
        val pendingIntent = alarmClockInfo?.showIntent
        val packageName = pendingIntent?.creatorPackage ?: "<no-package>"

        return when {
            ignoredPackages?.contains(packageName) == true -> getApplication<Application>().getString(R.string.main_next_is_ignored_app)
            triggerTime <= 0 -> getApplication<Application>().getString(R.string.no_scheduled_alarm)
            else -> {
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = triggerTime
                getApplication<Application>().getString(
                    R.string.next_alarm,
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(calendar.time)
                )
            }
        }
    }

    private fun getAlarmClockInfo(): AlarmManager.AlarmClockInfo? {
        val alarmManager = getApplication<Application>().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.nextAlarmClock
    }

    private fun getTriggerTime(): Long {
        return getAlarmClockInfo()?.triggerTime ?: -1
    }
}
