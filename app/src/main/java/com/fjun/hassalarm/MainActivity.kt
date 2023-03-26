package com.fjun.hassalarm

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.fjun.hassalarm.HistoryActivity.Companion.createIntent
import com.fjun.hassalarm.databinding.ActivityMainBinding
import com.fjun.hassalarm.databinding.ContentMainBinding
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ContentMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        this.binding = binding.content
        this.binding.editConnection.setOnClickListener {
            startActivity(EditConnectionActivity.createIntent(this))
        }
        updateView()
        NextAlarmUpdaterJob.scheduleJob(this)
    }

    override fun onResume() {
        super.onResume()
        updateView()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_about) {
            startActivity(AboutActivity.createIntent(this))
            return true
        }
        if (item.itemId == R.id.action_history) {
            startActivity(createIntent(this))
        }
        if (item.itemId == R.id.action_banlist) {
            startActivity(BanActivity.createIntent(this))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateView() {
        val sharedPreferences =
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val wasSuccessful =
            sharedPreferences.getBoolean(
                LAST_PUBLISH_WAS_SUCCESSFUL,
                false
            )
        val lastAttempt =
            sharedPreferences.getLong(LAST_PUBLISH_ATTEMPT, 0)
        val lastSuccessfulAttempt =
            sharedPreferences.getLong(
                LAST_SUCCESSFUL_PUBLISH,
                0
            )
        val lastPublishedTriggerTime =
            sharedPreferences.getLong(
                LAST_PUBLISHED_TRIGGER_TIMESTAMP,
                -1
            )
        val triggerTime = triggerTime
        if (wasSuccessful) {
            binding.successful.setText(R.string.published_successfully)
            binding.successfulPublishAt.visibility = View.GONE
            setLastPublishAt(R.string.last_publish_at, binding.publishAt, lastAttempt)
        } else if (triggerTime > 0 && triggerTime == lastPublishedTriggerTime) {
            // If last run was not successful, but if there has been a successful run that published the
            // value of next scheduled alarm, then we are considered successful.
            binding.successful.setText(R.string.published_successfully)
            binding.successfulPublishAt.visibility = View.GONE
            setLastPublishAt(
                R.string.last_successful_publish_at,
                binding.publishAt,
                lastSuccessfulAttempt
            )
        } else {
            if (lastAttempt > 0) {
                binding.successful.setText(R.string.failed_to_publish)
                setLastPublishAt(R.string.last_failed_publish_at, binding.publishAt, lastAttempt)
            } else {
                binding.successful.setText(R.string.failed_no_connection)
                binding.publishAt.visibility = View.GONE
            }
            if (lastSuccessfulAttempt > 0) {
                setLastPublishAt(
                    R.string.last_successful_publish_at,
                    binding.successfulPublishAt,
                    lastSuccessfulAttempt
                )
            } else {
                binding.successfulPublishAt.visibility = View.GONE
            }
        }
        showNextAlarm(
            binding.nextAlarm,
            sharedPreferences.getStringSet(
                KEY_IGNORED_PACKAGES,
                HashSet()
            )
        )
    }

    private fun setLastPublishAt(@StringRes res: Int, textView: TextView, time: Long) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = time
        textView.text = getString(
            res,
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(calendar.time)
        )
        textView.visibility = View.VISIBLE
    }

    private fun showNextAlarm(textView: TextView, ignoredPackages: Set<String>?) {
        val triggerTime = triggerTime

        // Ignored?
        val alarmClockInfo = alarmClockInfo()
        val pendingIntent = alarmClockInfo?.showIntent
        val packageName =
            if (pendingIntent != null) {
                pendingIntent.creatorPackage
            } else {
                "<no-package>"
            }
        if (packageName != null && ignoredPackages?.contains(packageName) == true) {
            textView.setText(R.string.main_next_is_ignored_app)
        } else if (triggerTime <= 0) {
            textView.text = getString(R.string.no_scheduled_alarm)
        } else {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = triggerTime
            textView.text = getString(
                R.string.next_alarm,
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(calendar.time)
            )
        }
    }

    private fun alarmClockInfo(): AlarmClockInfo? {
        val alarmManager = getSystemService(AlarmManager::class.java)
        return alarmManager.nextAlarmClock
    }

    private val triggerTime: Long
        get() {
            val nextAlarm = alarmClockInfo()
            return nextAlarm?.triggerTime ?: -1
        }
}