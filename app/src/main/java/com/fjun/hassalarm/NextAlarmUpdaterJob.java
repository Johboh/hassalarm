package com.fjun.hassalarm;

import android.app.AlarmManager;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static com.fjun.hassalarm.Constants.DEFAULT_ENTITY_ID;
import static com.fjun.hassalarm.Constants.DEFAULT_PORT;
import static com.fjun.hassalarm.Constants.KEY_PREFS_API_KEY;
import static com.fjun.hassalarm.Constants.KEY_PREFS_ENTITY_ID;
import static com.fjun.hassalarm.Constants.KEY_PREFS_HOST;
import static com.fjun.hassalarm.Constants.KEY_PREFS_IS_TOKEN;
import static com.fjun.hassalarm.Constants.PREFS_NAME;

public class NextAlarmUpdaterJob extends JobService {

    private static final String BEARER_PATTERN = "Bearer %s";
    static final int JOB_ID = 0;

    private Call<ResponseBody> mCall;

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        final AlarmManager alarmManager = getSystemService(AlarmManager.class);
        final AlarmManager.AlarmClockInfo alarmClockInfo = alarmManager.getNextAlarmClock();

        // Get next scheduled alarm, if any.
        final String time;
        if (alarmClockInfo != null) {
            final long timestamp = alarmClockInfo.getTriggerTime();
            final Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(timestamp);
            time = new SimpleDateFormat("yyyy-MM-dd HH:mm:00", Locale.US).format(calendar.getTime());
        } else {
            time = "";
        }

        // Read host and API key.
        final SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String host = sharedPreferences.getString(KEY_PREFS_HOST, "");
        if (TextUtils.isEmpty(host)) {
            return false;
        }

        // No port number? Add default one.
        if (!host.contains(":")) {
            host = String.format(Locale.getDefault(), "%s:%d", host, DEFAULT_PORT);
        }
        // Default to http:// if there is no protocol defined.
        if (!host.startsWith("http://") && !host.startsWith("https://")) {
            host = String.format(Locale.getDefault(), "http://%s", host);
        }

        // Support empty API key, if there is no one required.
        final String apiKeyOrToken = sharedPreferences.getString(KEY_PREFS_API_KEY, "");
        String entityId = sharedPreferences.getString(KEY_PREFS_ENTITY_ID, DEFAULT_ENTITY_ID);
        final boolean isToken = sharedPreferences.getBoolean(KEY_PREFS_IS_TOKEN, false);
        final Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(host)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        final HassApi hassApi = retrofit.create(HassApi.class);
        Log.d(NextAlarmUpdater.class.getName(), "Setting time to " + time);

        if (mCall != null) {
            mCall.cancel();
        }

        // Default to default entity id, if none is set.
        if (TextUtils.isEmpty(entityId)) {
            entityId = DEFAULT_ENTITY_ID;
        }

        // Enqueue call and run on background thread.
        // Check if it is using long lived access tokens
        if (isToken) {
            // Create Authorization Header value
            String bearer = String.format(BEARER_PATTERN, apiKeyOrToken);
            mCall = hassApi.updateStateUsingToken(new State(time), entityId, bearer);
        } else {
            mCall = hassApi.updateStateUsingApiKey(new State(time), entityId, apiKeyOrToken);
        }
        mCall.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                Log.d(NextAlarmUpdater.class.getName(), "Retofit succeeded: " + response.body().toString());
                jobFinished(jobParameters, false);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(NextAlarmUpdater.class.getName(), "Retofit failed: " + t.getMessage());
                // Fail, reschedule job.
                jobFinished(jobParameters, true);
            }
        });

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        if (mCall != null) {
            mCall.cancel();
        }
        return true;
    }
}
