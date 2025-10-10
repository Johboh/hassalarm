package com.fjun.hassalarm

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class BanViewModel(application: Application) : AndroidViewModel(application) {

    val nextAlarmPackage = MutableStateFlow("")
    val bannedPackages = MutableStateFlow<List<String>>(emptyList())

    private val sharedPreferences: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        updateBannedPackages()
        updateNextAlarmPackage()
    }

    private fun updateBannedPackages() {
        bannedPackages.value =
            (sharedPreferences.getStringSet(KEY_IGNORED_PACKAGES, HashSet<String>())
                ?: setOf()).toList()
    }

    private fun updateNextAlarmPackage() {
        val alarmManager = getApplication<Application>().getSystemService(AlarmManager::class.java)
        val alarmClockInfo = alarmManager.nextAlarmClock
        val pendingIntent = alarmClockInfo?.showIntent
        nextAlarmPackage.value = pendingIntent?.creatorPackage
            ?: getApplication<Application>().getString(R.string.ban_no_alarm_set)
    }

    fun addPackage(packageName: String) {
        if (packageName.isNotEmpty()) {
            val set =
                sharedPreferences.getStringSet(KEY_IGNORED_PACKAGES, HashSet<String>()) ?: setOf()
            val newSet = set.toMutableSet().apply {
                add(packageName)
            }
            save(newSet)
            updateBannedPackages()
        }
    }

    fun removePackage(packageName: String) {
        val set = sharedPreferences.getStringSet(KEY_IGNORED_PACKAGES, HashSet<String>()) ?: setOf()
        val newSet = set.toMutableSet().apply {
            remove(packageName)
        }
        save(newSet)
        updateBannedPackages()
    }

    private fun save(set: Set<String>) {
        sharedPreferences.edit { putStringSet(KEY_IGNORED_PACKAGES, set) }
    }
}