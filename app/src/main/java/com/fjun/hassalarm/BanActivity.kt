package com.fjun.hassalarm

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.fjun.hassalarm.databinding.ActivityBanlistBinding

class BanActivity : AppCompatActivity() {

    private lateinit var adapter: BanAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityBanlistBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupInsets(binding.root)

        val alarmManager = getSystemService(AlarmManager::class.java)
        val alarmClockInfo = alarmManager.nextAlarmClock
        val missingPackageName = getString(R.string.ban_no_alarm_set)
        val pendingIntent = alarmClockInfo?.showIntent
        val pendingPackageName =
            if (pendingIntent != null) {
                pendingIntent.creatorPackage
            } else {
                missingPackageName
            }

        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        adapter = BanAdapter { packageName: String ->
            // REMOVE
            val set = sharedPreferences
                .getStringSet(KEY_IGNORED_PACKAGES, HashSet<String>()) ?: setOf()
            val newSet = set.toMutableSet().apply {
                remove(packageName)
            }
            save(sharedPreferences, newSet)
            setList(sharedPreferences, adapter)
        }

        with(binding) {
            add.isEnabled = pendingIntent != null
            nextPackage.text = pendingPackageName ?: missingPackageName
            list.layoutManager = LinearLayoutManager(this@BanActivity)
            list.adapter = adapter
            add.setOnClickListener {
                // ADD
                val creatorPackage = pendingIntent?.creatorPackage
                if (!creatorPackage.isNullOrEmpty()) {
                    val set = sharedPreferences
                        .getStringSet(KEY_IGNORED_PACKAGES, HashSet<String>()) ?: setOf()
                    val newSet = set.toMutableSet().apply {
                        add(creatorPackage)
                    }
                    save(sharedPreferences, newSet)
                    setList(sharedPreferences, adapter)
                }
            }
        }

        setList(sharedPreferences, adapter)
    }

    private fun setList(sharedPreferences: SharedPreferences, adapter: BanAdapter) {
        adapter.set(
            (sharedPreferences.getStringSet(KEY_IGNORED_PACKAGES, HashSet<String>())
                ?: setOf()).toList()
        )
    }

    private fun save(sharedPreferences: SharedPreferences, set: Set<String>) {
        sharedPreferences.edit().putStringSet(KEY_IGNORED_PACKAGES, set)
            .apply()
    }

    companion object {
        fun createIntent(context: Context?): Intent = Intent(context, BanActivity::class.java)
    }
}