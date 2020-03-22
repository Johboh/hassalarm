package com.fjun.hassalarm;

import android.app.AlarmManager;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.google.auto.value.AutoValue;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static com.fjun.hassalarm.Constants.DEFAULT_PORT;
import static com.fjun.hassalarm.Constants.KEY_PREFS_API_KEY;
import static com.fjun.hassalarm.Constants.KEY_PREFS_HOST;
import static com.fjun.hassalarm.Constants.PREFS_NAME;

public class NextAlarmUpdaterJob extends JobService {

    private static final String BEARER_PATTERN = "Bearer %s";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
    private static final SimpleDateFormat DATE_FORMAT_LEGACY = new SimpleDateFormat("yyyy-MM-dd HH:mm:00", Locale.ENGLISH);
    private static final int MAX_EXECUTION_DELAY_MS = 3600 * 1000; // 1h
    static final int JOB_ID = 0;

    private Call<ResponseBody> mCall;

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        Log.d(Constants.LOG_TAG, "Starting job.");
        if (mCall != null) {
            Log.d(Constants.LOG_TAG, "Canceling previously retrofit job.");
            mCall.cancel();
        }

        final long triggerTimestamp;
        try {
            final Request request = createRequest(this);
            mCall = request.call();
            triggerTimestamp = request.triggerTimestamp();
        } catch (IllegalArgumentException e) {
            Log.e(Constants.LOG_TAG, "Failed to create request: " + e.getMessage());
            markAsDone(this, false, 0);
            return false;
        }

        Log.d(Constants.LOG_TAG, "Enqueueing retrofit job.");
        mCall.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                boolean successful = false;
                try {
                    final ResponseBody body = response.body();
                    if (body != null) {
                        Log.d(Constants.LOG_TAG, "Retofit succeeded: " + body.toString());
                        successful = true;
                    } else if (response.errorBody() != null) {
                        Log.e(Constants.LOG_TAG, "Retofit failed: " + response.errorBody().string());
                    } else {
                        Log.e(Constants.LOG_TAG, "Retofit failed with code: " + response.code());
                    }
                } catch (IOException e) {
                    Log.e(Constants.LOG_TAG, "Retofit failed: " + e.getMessage());
                }
                jobFinished(jobParameters, !successful);
                markAsDone(NextAlarmUpdaterJob.this, successful, triggerTimestamp);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(NextAlarmUpdaterJob.class.getName(), "Retofit failed: " + t.getMessage());
                // Fail, reschedule job.
                jobFinished(jobParameters, true);
                markAsDone(NextAlarmUpdaterJob.this, false, 0);
            }
        });

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Log.d(Constants.LOG_TAG, "Stopping job.");
        if (mCall != null) {
            mCall.cancel();
        }
        return true;
    }

    public Request createRequest(Context context) throws IllegalArgumentException {
        final SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String host = sharedPreferences.getString(KEY_PREFS_HOST, "");
        final String apiKeyOrToken = sharedPreferences.getString(KEY_PREFS_API_KEY, "");
        final String entityId = Migration.getEntityId(sharedPreferences);
        final Constants.AccessType accessType = Migration.getAccessType(sharedPreferences);
        final boolean entityIdIsLegacy = Migration.entityIdIsLegacy(sharedPreferences);
        return createRequest(context, host, apiKeyOrToken, entityId, accessType, entityIdIsLegacy);
    }

    /**
     * Create a call that can be executed. Will throw an exception in case of any failure,
     * like missing parameters etc.
     */
    public static Request createRequest(Context context,
                                        String host,
                                        String apiKeyOrToken,
                                        String entityId,
                                        Constants.AccessType accessType,
                                        boolean entityIdIsLegacy) throws IllegalArgumentException {
        final AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        final AlarmManager.AlarmClockInfo alarmClockInfo = alarmManager.getNextAlarmClock();

        // Verify host and API key.
        if (TextUtils.isEmpty(host)) {
            throw new IllegalArgumentException("Host is missing. You need to specify the host to your hass.io instance.");
        }

        // No port number? Add default one.
        if (!host.contains(":")) {
            host = String.format(Locale.US, "%s:%d", host, DEFAULT_PORT);
        }
        // Default to http:// if there is no protocol defined.
        if (!host.startsWith("http://") && !host.startsWith("https://")) {
            host = String.format(Locale.US, "http://%s", host);
        }

        // Support empty API key, if there is no one required.
        final Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(host)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        final HassApi hassApi = retrofit.create(HassApi.class);

        // Get next scheduled alarm, if any.
        final State state;
        final Datetime datetime;
        final long triggerTimestamp;
        if (alarmClockInfo != null) {
            triggerTimestamp = alarmClockInfo.getTriggerTime();
            final Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(triggerTimestamp);
            state = new State(DATE_FORMAT_LEGACY.format(calendar.getTime()));
            datetime = new Datetime(entityId, DATE_FORMAT.format(calendar.getTime()));
        } else {
            triggerTimestamp = 0;
            state = new State("");
            datetime = new Datetime(entityId, "1970-01-01 00:00:00");
        }
        Log.d(Constants.LOG_TAG, "Setting time to " + datetime.datetime);

        // Enqueue call and run on background thread.
        // Check if it is using long lived access tokens
        final Call<ResponseBody> call;
        if (accessType == Constants.AccessType.LONG_LIVED_TOKEN) {
            // Create Authorization Header value
            String bearer = String.format(BEARER_PATTERN, apiKeyOrToken);
            if (entityIdIsLegacy) {
                call = hassApi.updateStateUsingToken(state, entityId, bearer);
            } else {
                call = hassApi.setInputDatetimeUsingToken(datetime, bearer);
            }
        } else {
            if (accessType == Constants.AccessType.WEB_HOOK) {
                call = hassApi.updateStateUsingWebhook(datetime, apiKeyOrToken);
            } else if (entityIdIsLegacy) {
                call = hassApi.updateStateUsingApiKey(state, entityId, apiKeyOrToken);
            } else {
                call = hassApi.setInputDatetimeUsingApiKey(datetime, apiKeyOrToken);
            }
        }
        return Request.create(call, triggerTimestamp);
    }

    /**
     * Schedule a job to update the next alarm once we have some kind of network connection.
     */
    public static void scheduleJob(Context context) {
        Log.d(Constants.LOG_TAG, "Scheduling job");
        final JobInfo jobInfo = new JobInfo.Builder(JOB_ID,
                new ComponentName(context, NextAlarmUpdaterJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .setOverrideDeadline(MAX_EXECUTION_DELAY_MS)
                .build();
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(jobInfo);
    }

    public static void markAsDone(Context context, boolean successful, long triggerTimestamp) {
        final SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        final long timestamp = System.currentTimeMillis();
        final SharedPreferences.Editor editor = sharedPreferences
                .edit()
                .putBoolean(Constants.LAST_PUBLISH_WAS_SUCCESSFUL, successful)
                .putLong(Constants.LAST_PUBLISH_ATTEMPT, timestamp);
        if (successful) {
            editor.putLong(Constants.LAST_SUCCESSFUL_PUBLISH, timestamp);
            editor.putLong(Constants.LAST_PUBLISHED_TRIGGER_TIMESTAMP, triggerTimestamp);
        }
        editor.apply();
    }

    @AutoValue
    static abstract class Request {
        public abstract Call<ResponseBody> call();

        public abstract long triggerTimestamp();

        static Request create(Call<ResponseBody> call, long triggerTimestamp) {
            return new AutoValue_NextAlarmUpdaterJob_Request(call, triggerTimestamp);
        }
    }
}
