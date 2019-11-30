package com.fjun.hassalarm;

import android.app.AlarmManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import static com.fjun.hassalarm.Constants.KEY_PREFS_API_KEY;
import static com.fjun.hassalarm.Constants.KEY_PREFS_ENTITY_ID;
import static com.fjun.hassalarm.Constants.KEY_PREFS_HOST;
import static com.fjun.hassalarm.Constants.KEY_PREFS_IS_TOKEN;
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
        final EditText entityIdEditText = findViewById(R.id.entity_id);
        final CheckBox isToken = findViewById(R.id.isToken);
        findViewById(R.id.save).setOnClickListener(view -> {
            sharedPreferences.edit()
                .putString(KEY_PREFS_HOST, hostEditText.getText().toString().trim())
                .putString(KEY_PREFS_API_KEY, apiKeyEditText.getText().toString().trim())
                .putString(KEY_PREFS_ENTITY_ID, entityIdEditText.getText().toString().trim())
                .putBoolean(KEY_PREFS_IS_TOKEN, isToken.isChecked())
                .apply();
            Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
            startActivity(TestConnectionActivity.createIntent(this));
        });

        // Set current saved host and api key.
        hostEditText.setText(sharedPreferences.getString(KEY_PREFS_HOST, ""));
        apiKeyEditText.setText(sharedPreferences.getString(KEY_PREFS_API_KEY, ""));
        entityIdEditText.setText(sharedPreferences.getString(KEY_PREFS_ENTITY_ID, ""));
        isToken.setChecked(sharedPreferences.getBoolean(KEY_PREFS_IS_TOKEN,false));

        showNextAlarm(textView);
        NextAlarmUpdaterJob.scheduleJob(this);
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
