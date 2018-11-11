package com.fjun.hassalarm;

import android.app.AlarmManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import static com.fjun.hassalarm.Constants.KEY_PREFS_API_KEY;
import static com.fjun.hassalarm.Constants.KEY_PREFS_HOST;
import static com.fjun.hassalarm.Constants.PREFS_NAME;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final TextView textView = findViewById(R.id.text);

        // Support saving settings (host, api key)
        final SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        final EditText hostEditText = findViewById(R.id.host);
        final EditText apiKeyEditText = findViewById(R.id.api_key);
        findViewById(R.id.save).setOnClickListener(view -> {
            sharedPreferences.edit()
                .putString(KEY_PREFS_HOST, hostEditText.getText().toString().trim())
                .putString(KEY_PREFS_API_KEY, apiKeyEditText.getText().toString().trim())
                .apply();
            Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
        });

        // Set current saved host and api key.
        hostEditText.setText(sharedPreferences.getString(KEY_PREFS_HOST, ""));
        apiKeyEditText.setText(sharedPreferences.getString(KEY_PREFS_API_KEY, ""));

        showNextAlarm(textView);
        NextAlarmUpdater.scheduleJob(this);
    }

    private void showNextAlarm(TextView textView) {
        final AlarmManager alarmManager = getSystemService(AlarmManager.class);
        final AlarmManager.AlarmClockInfo nextAlarm = alarmManager.getNextAlarmClock();

        if (nextAlarm == null) {
            textView.setText(getString(R.string.no_scheduled_alarm));
        } else {
            final Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(nextAlarm.getTriggerTime());
            textView.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(calendar.getTime()));
        }
    }
}
