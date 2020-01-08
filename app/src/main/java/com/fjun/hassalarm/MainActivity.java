package com.fjun.hassalarm;

import android.app.AlarmManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.fjun.hassalarm.databinding.ActivityMainBinding;
import com.fjun.hassalarm.databinding.ContentMainBinding;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import static com.fjun.hassalarm.Constants.DEFAULT_ENTITY_ID;
import static com.fjun.hassalarm.Constants.KEY_PREFS_ENTITY_ID;
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

        if (wasSuccessful) {
            mBinding.successful.setText(R.string.published_successfully);
            mBinding.publishAt.setVisibility(View.VISIBLE);
            setLastPublishAt(mBinding.publishAt, lastAttempt);
            mBinding.entityId.setVisibility(View.VISIBLE);
            final String entityId = sharedPreferences.getString(KEY_PREFS_ENTITY_ID, "");
            mBinding.entityId.setText(getString(R.string.entity_id, TextUtils.isEmpty(entityId) ? DEFAULT_ENTITY_ID : entityId));
        } else {
            if (lastAttempt > 0) {
                mBinding.successful.setText(R.string.failed_to_publish);
            } else {
                mBinding.successful.setText(R.string.failed_no_connection);
            }
            mBinding.entityId.setVisibility(View.GONE);
            mBinding.publishAt.setVisibility(View.GONE);
        }

        showNextAlarm(mBinding.nextAlarm);
    }

    private void setLastPublishAt(TextView textView, Long time) {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        textView.setText(getString(R.string.last_publish_at, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(calendar.getTime())));
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
