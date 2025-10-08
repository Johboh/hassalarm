package com.fjun.hassalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.fjun.hassalarm.databinding.ActivityBanlistBinding
import java.util.regex.Pattern

class BanActivity : AppCompatActivity() {

    private lateinit var adapter: BanAdapter
    private lateinit var sharedPreferences: SharedPreferences

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

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        adapter = BanAdapter { packageName: String ->
            // REMOVE
            val set = sharedPreferences
                .getStringSet(KEY_IGNORED_PACKAGES, HashSet<String>()) ?: setOf()
            val newSet = set.toMutableSet().apply {
                remove(packageName)
            }
            save(newSet)
            setList()
        }

        with(binding) {
            add.isEnabled = pendingIntent != null
            nextPackage.text = pendingPackageName ?: missingPackageName
            list.layoutManager = LinearLayoutManager(this@BanActivity)
            list.adapter = adapter
            add.setOnClickListener {
                addNextAlarmPackage(pendingIntent)
            }
            addManual.isEnabled = false
            manualPackageEditText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    addManual.isEnabled = isValidPackageName(s.toString())
                }

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
            addManual.setOnClickListener {
                val manualPackage = manualPackageEditText.text.toString()
                addPackage(manualPackage)
                manualPackageEditText.text = null
            }
        }

        setList()
    }

    private fun isValidPackageName(packageName: String) =
        Pattern.matches("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+$", packageName)

    private fun addNextAlarmPackage(pendingIntent: PendingIntent?) {
        val creatorPackage = pendingIntent?.creatorPackage
        if (!creatorPackage.isNullOrEmpty()) {
            addPackage(creatorPackage)
        }
    }

    private fun addPackage(packageName: String) {
        if (packageName.isNotEmpty()) {
            val set = sharedPreferences
                .getStringSet(KEY_IGNORED_PACKAGES, HashSet<String>()) ?: setOf()
            val newSet = set.toMutableSet().apply {
                add(packageName)
            }
            save(newSet)
            setList()
        }
    }

    private fun setList() {
        adapter.set(
            (sharedPreferences.getStringSet(KEY_IGNORED_PACKAGES, HashSet<String>())
                ?: setOf()).toList()
        )
    }

    private fun save(set: Set<String>) {
        sharedPreferences.edit().putStringSet(KEY_IGNORED_PACKAGES, set)
            .apply()
    }

    companion object {
        fun createIntent(context: Context?): Intent = Intent(context, BanActivity::class.java)
    }
}