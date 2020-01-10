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
import java.util.Locale;

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
        return super.onOptionsItemSelected(item);
    }

    private void updateView() {
        final SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        final boolean wasSuccessful = sharedPreferences.getBoolean(Constants.LAST_PUBLISH_WAS_SUCCESSFUL, false);
        final Long lastAttempt = sharedPreferences.getLong(Constants.LAST_PUBLISH_ATTEMPT, 0);
        final Long lastSuccessfulAttempt = sharedPreferences.getLong(Constants.LAST_SUCCESSFUL_PUBLISH, 0);

        if (wasSuccessful) {
            mBinding.successful.setText(R.string.published_successfully);
            mBinding.successfulPublishAt.setVisibility(View.GONE);
            setLastPublishAt(R.string.last_publish_at, mBinding.publishAt, lastAttempt);
        } else {
            if (lastAttempt > 0) {
                mBinding.successful.setText(R.string.failed_to_publish);
            } else {
                mBinding.successful.setText(R.string.failed_no_connection);
            }
            setLastPublishAt(R.string.last_failed_publish_at, mBinding.publishAt, lastAttempt);
            mBinding.publishAt.setVisibility(lastAttempt > 0 ? View.VISIBLE : View.GONE);
            if (lastSuccessfulAttempt > 0) {
                mBinding.successfulPublishAt.setVisibility(View.VISIBLE);
                setLastPublishAt(R.string.last_successful_publish_at, mBinding.successfulPublishAt, lastSuccessfulAttempt);
            }
        }

        showNextAlarm(mBinding.nextAlarm);
    }

    private void setLastPublishAt(@StringRes int res, TextView textView, Long time) {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        textView.setText(getString(res, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(calendar.getTime())));
    }

    private void showNextAlarm(TextView textView) {
        final AlarmManager alarmManager = getSystemService(AlarmManager.class);
        final AlarmManager.AlarmClockInfo nextAlarm = alarmManager.getNextAlarmClock();

        if (nextAlarm == null) {
            textView.setText(getString(R.string.no_scheduled_alarm));
        } else {
            final Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(nextAlarm.getTriggerTime());
            textView.setText(getString(R.string.next_alarm, new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(calendar.getTime())));
        }
    }
}
