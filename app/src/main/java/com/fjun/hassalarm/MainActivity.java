package com.fjun.hassalarm;

import android.app.AlarmManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.fjun.hassalarm.databinding.ActivityMainBinding;
import com.fjun.hassalarm.databinding.ContentMainBinding;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static com.fjun.hassalarm.Constants.KEY_IGNORED_PACKAGES;
import static com.fjun.hassalarm.Constants.PREFS_NAME;

public class MainActivity extends AppCompatActivity {

    private ContentMainBinding mBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.root);
        setSupportActionBar(binding.toolbar);
        mBinding = binding.content;

        mBinding.editConnection.setOnClickListener(v -> startActivity(EditConnectionActivity.createIntent(this)));

        updateView();

        NextAlarmUpdaterJob.scheduleJob(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateView();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_about) {
            startActivity(AboutActivity.createIntent(this));
            return true;
        }
        if (item.getItemId() == R.id.action_banlist) {
            startActivity(BanActivity.createIntent(this));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateView() {
        final SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        final boolean wasSuccessful = sharedPreferences.getBoolean(Constants.LAST_PUBLISH_WAS_SUCCESSFUL, false);
        final long lastAttempt = sharedPreferences.getLong(Constants.LAST_PUBLISH_ATTEMPT, 0);
        final long lastSuccessfulAttempt = sharedPreferences.getLong(Constants.LAST_SUCCESSFUL_PUBLISH, 0);
        final long lastPublishedTriggerTime = sharedPreferences.getLong(Constants.LAST_PUBLISHED_TRIGGER_TIMESTAMP, -1);
        final long triggerTime = getTriggerTime();

        if (wasSuccessful) {
            mBinding.successful.setText(R.string.published_successfully);
            mBinding.successfulPublishAt.setVisibility(View.GONE);
            setLastPublishAt(R.string.last_publish_at, mBinding.publishAt, lastAttempt);
        } else if (triggerTime > 0 && triggerTime == lastPublishedTriggerTime) {
            // If last run was not successful, but if there has been a successful run that published the
            // value of next scheduled alarm, then we are considered successful.
            mBinding.successful.setText(R.string.published_successfully);
            mBinding.successfulPublishAt.setVisibility(View.GONE);
            setLastPublishAt(R.string.last_successful_publish_at, mBinding.publishAt, lastSuccessfulAttempt);
        } else {
            if (lastAttempt > 0) {
                mBinding.successful.setText(R.string.failed_to_publish);
                setLastPublishAt(R.string.last_failed_publish_at, mBinding.publishAt, lastAttempt);
            } else {
                mBinding.successful.setText(R.string.failed_no_connection);
                mBinding.publishAt.setVisibility(View.GONE);
            }
            if (lastSuccessfulAttempt > 0) {
                setLastPublishAt(R.string.last_successful_publish_at, mBinding.successfulPublishAt, lastSuccessfulAttempt);
            } else {
                mBinding.successfulPublishAt.setVisibility(View.GONE);
            }
        }

        showNextAlarm(mBinding.nextAlarm, sharedPreferences.getStringSet(KEY_IGNORED_PACKAGES, new HashSet<>()));
    }

    private void setLastPublishAt(@StringRes int res, TextView textView, Long time) {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        textView.setText(getString(res, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(calendar.getTime())));
        textView.setVisibility(View.VISIBLE);
    }

    private void showNextAlarm(TextView textView, Set<String> ignoredPackages) {
        final long triggerTime = getTriggerTime();

        // Ignored?
        final AlarmManager.AlarmClockInfo alarmClockInfo = alarmClockInfo();
        if (alarmClockInfo != null && ignoredPackages.contains(alarmClockInfo.getShowIntent().getCreatorPackage())) {
            textView.setText(R.string.main_next_is_ignored_app);
        } else if (triggerTime <= 0) {
            textView.setText(getString(R.string.no_scheduled_alarm));
        } else {
            final Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(triggerTime);
            textView.setText(getString(R.string.next_alarm, new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(calendar.getTime())));
        }
    }

    private AlarmManager.AlarmClockInfo alarmClockInfo() {
        final AlarmManager alarmManager = getSystemService(AlarmManager.class);
        return alarmManager.getNextAlarmClock();
    }

    private long getTriggerTime() {
        final AlarmManager.AlarmClockInfo nextAlarm = alarmClockInfo();
        return nextAlarm == null ? -1 : nextAlarm.getTriggerTime();
    }
}
