package com.fjun.hassalarm;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

class NextAlarmUpdater {
    /**
     * Schedule a job to update the next alarm once we have some kind of network connection.
     */
    static void scheduleJob(Context context) {
        final JobInfo jobInfo = new JobInfo.Builder(NextAlarmUpdaterJob.JOB_ID,
                new ComponentName(context, NextAlarmUpdaterJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(jobInfo);
    }
}
