package com.fjun.hassalarm;

import static android.app.job.JobInfo.BACKOFF_POLICY_LINEAR;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import com.fjun.hassalarm.history.AppDatabase;
import com.fjun.hassalarm.history.Publish;
import com.fjun.hassalarm.history.PublishDao;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static com.fjun.hassalarm.Constants.DEFAULT_PORT;
import static com.fjun.hassalarm.Constants.KEY_IGNORED_PACKAGES;
import static com.fjun.hassalarm.Constants.KEY_PREFS_API_KEY;
import static com.fjun.hassalarm.Constants.KEY_PREFS_HOST;
import static com.fjun.hassalarm.Constants.PREFS_NAME;

public class NextAlarmUpdaterJob extends JobService {

    private static final String BEARER_PATTERN = "Bearer %s";
    private static final SimpleDateFormat DATE_FORMAT_LEGACY = new SimpleDateFormat("yyyy-MM-dd HH:mm:00", Locale.ENGLISH);
    private static final int MAX_EXECUTION_DELAY_MS = 3600 * 1000; // 1h
    private static final int BACKOFF_MS = 60 * 1000; // 1m
    private static final int MINIMUM_LATENCY_MS = 5 * 1000; // 5s
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
        final String creatorPackage;
        try {
            final UpdateRequest request = createRequest(this);
            triggerTimestamp = request.triggerTimestamp();
            creatorPackage = request.creatorPackage();
            mCall = request.call();
        } catch (IllegalArgumentException e) {
            Log.e(Constants.LOG_TAG, "Failed to create request: " + e.getMessage());
            jobFinished(jobParameters, true);
            markAsDone(this, false, 0);
            insertPublish(new Publish(System.currentTimeMillis(), false, 0L, e.getMessage(), null));
            return false;
        }

        // No need to publish the same timestamp if we already had an successful publish
        final SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        final long lastPublishedTriggerTime = sharedPreferences.getLong(Constants.LAST_PUBLISHED_TRIGGER_TIMESTAMP, -1);
        if (triggerTimestamp == lastPublishedTriggerTime) {
            Log.d(Constants.LOG_TAG, "Trigger timestamp already published. Bail.");
            jobFinished(jobParameters, false);
            return false;
        }


        Log.d(Constants.LOG_TAG, "Enqueueing retrofit job.");
        mCall.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                boolean successful = false;
                String message = "";
                try {
                    final ResponseBody body = response.body();
                    if (body != null) {
                        message = body.toString();
                        Log.d(Constants.LOG_TAG, "Retofit succeeded: " + message);
                        successful = true;
                    } else if (response.errorBody() != null) {
                        message = response.errorBody().string();
                        Log.e(Constants.LOG_TAG, "Retofit failed: " + message);
                    } else {
                        message =String.valueOf(response.code());
                        Log.e(Constants.LOG_TAG, "Retofit failed with code: " + message);
                    }
                } catch (IOException e) {
                    Log.e(Constants.LOG_TAG, "Retofit failed: " + e.getMessage());
                }
                jobFinished(jobParameters, !successful);
                markAsDone(NextAlarmUpdaterJob.this, successful, triggerTimestamp);
                insertPublish(new Publish(System.currentTimeMillis(), successful, triggerTimestamp, message, creatorPackage));
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                String message = t.getMessage();
                Log.e(NextAlarmUpdaterJob.class.getName(), "Retofit failed: " + message);
                // Fail, reschedule job.
                jobFinished(jobParameters, true);
                markAsDone(NextAlarmUpdaterJob.this, false, 0);
                insertPublish(new Publish(System.currentTimeMillis(), false, triggerTimestamp, message, creatorPackage));
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

    public UpdateRequest createRequest(Context context) throws IllegalArgumentException {
        final SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String host = sharedPreferences.getString(KEY_PREFS_HOST, "");
        final String apiKeyOrToken = sharedPreferences.getString(KEY_PREFS_API_KEY, "");
        final String entityId = Migration.getEntityId(sharedPreferences);
        final Constants.AccessType accessType = Migration.getAccessType(sharedPreferences);
        final boolean entityIdIsLegacy = Migration.entityIdIsLegacy(sharedPreferences);
        final Set<String> ignoredPackages = sharedPreferences.getStringSet(KEY_IGNORED_PACKAGES, new HashSet<>());
        return createRequest(context, host, apiKeyOrToken, entityId, accessType, entityIdIsLegacy, ignoredPackages);
    }

    /**
     * Create a call that can be executed. Will throw an exception in case of any failure,
     * like missing parameters etc.
     */
    public static UpdateRequest createRequest(Context context,
                                        String host,
                                        String apiKeyOrToken,
                                        String entityId,
                                        Constants.AccessType accessType,
                                        boolean entityIdIsLegacy,
                                        Set<String> ignoredPackages) throws IllegalArgumentException {
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
                .client(UnsafeOkHttpClient.getUnsafeOkHttpClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        final HassApi hassApi = retrofit.create(HassApi.class);

        // Get next scheduled alarm, if any.
        final State state;
        final Datetime datetime;
        final long triggerTimestamp;
        final String creatorPackage;
        if (alarmClockInfo != null) {
            // Ignored package?
            final PendingIntent showIntent = alarmClockInfo.getShowIntent();
            String packageName = showIntent != null ? showIntent.getCreatorPackage() : "<no-pending-intent>";
            if (packageName != null && ignoredPackages.contains(packageName)) {
                // Ignore!
                Log.d(Constants.LOG_TAG, "Package " + packageName + " is in ignored list. Ignoring alarm for this package.");
                triggerTimestamp = 0;
                creatorPackage = null;
                state = new State("");
                datetime = new Datetime(entityId, 1);
            } else {
                triggerTimestamp = alarmClockInfo.getTriggerTime();
                creatorPackage = packageName;
                final Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(triggerTimestamp);
                state = new State(DATE_FORMAT_LEGACY.format(calendar.getTime()));
                datetime = new Datetime(entityId, triggerTimestamp / 1000);
            }
        } else {
            triggerTimestamp = 0;
            creatorPackage = "";
            state = new State("");
            datetime = new Datetime(entityId, 1);
        }
        Log.d(Constants.LOG_TAG, "Setting time to " + datetime.timestamp);

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
        return new UpdateRequest(triggerTimestamp, call, creatorPackage);
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
                .setBackoffCriteria(BACKOFF_MS, BACKOFF_POLICY_LINEAR)
                .setMinimumLatency(MINIMUM_LATENCY_MS)
                .setOverrideDeadline(deadline(context))
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

    private PublishDao database() {
        return AppDatabase.getDatabase(getApplicationContext()).publishDao();
    }

    private void insertPublish(Publish publish) {
        AsyncTask.execute(() -> database().insertAll(publish));
    }

    private static AlarmManager.AlarmClockInfo alarmClockInfo(Context context) {
        final AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        return alarmManager.getNextAlarmClock();
    }

    private static long getTriggerTime(Context context) {
        final AlarmManager.AlarmClockInfo nextAlarm = alarmClockInfo(context);
        return nextAlarm == null ? 0 : nextAlarm.getTriggerTime();
    }

    private static int deadline(Context context) {
        final SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        final AlarmManager.AlarmClockInfo alarmClockInfo = alarmClockInfo(context);
        final PendingIntent pendingIntent = alarmClockInfo != null ? alarmClockInfo.getShowIntent() : null;
        final String packageName = pendingIntent != null ? pendingIntent.getCreatorPackage() : "<no-package>";
        final Set<String> ignoredPackages = sharedPreferences.getStringSet(KEY_IGNORED_PACKAGES, new HashSet<>());
        if (packageName != null && ignoredPackages.contains(packageName)) {
            return MAX_EXECUTION_DELAY_MS;
        } else {
            final long triggerTime = getTriggerTime(context);
            final long now = System.currentTimeMillis();
            final long halfDiff = Math.max(0, triggerTime - now) / 2;
            return (int)Math.min(MAX_EXECUTION_DELAY_MS, halfDiff);
        }
    }
}
