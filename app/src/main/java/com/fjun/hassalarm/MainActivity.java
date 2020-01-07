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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import static com.fjun.hassalarm.Constants.DEFAULT_ENTITY_ID;
import static com.fjun.hassalarm.Constants.KEY_PREFS_ENTITY_ID;
import static com.fjun.hassalarm.Constants.PREFS_NAME;

public class MainActivity extends AppCompatActivity {

    private TextView mEntityIdTextView;
    private TextView mSuccessfulTextView;
    private TextView mLastPublishAtTextView;
    private TextView mNextAlarmTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));

        mNextAlarmTextView = findViewById(R.id.next_alarm);
        mEntityIdTextView = findViewById(R.id.entity_id);
        mSuccessfulTextView = findViewById(R.id.successful);
        mLastPublishAtTextView = findViewById(R.id.publish_at);

        findViewById(R.id.edit_connection).setOnClickListener(v -> startActivity(EditConnectionActivity.createIntent(this)));

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
            mSuccessfulTextView.setText(R.string.published_successfully);
            mLastPublishAtTextView.setVisibility(View.VISIBLE);
            setLastPublishAt(mLastPublishAtTextView, lastAttempt);
            mEntityIdTextView.setVisibility(View.VISIBLE);
            final String entityId = sharedPreferences.getString(KEY_PREFS_ENTITY_ID, "");
            mEntityIdTextView.setText(getString(R.string.entity_id, TextUtils.isEmpty(entityId) ? DEFAULT_ENTITY_ID : entityId));
        } else {
            if (lastAttempt > 0) {
                mSuccessfulTextView.setText(R.string.failed_to_publish);
            } else {
                mSuccessfulTextView.setText(R.string.failed_no_connection);
            }
            mEntityIdTextView.setVisibility(View.GONE);
            mLastPublishAtTextView.setVisibility(View.GONE);
        }

        showNextAlarm(mNextAlarmTextView);
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
