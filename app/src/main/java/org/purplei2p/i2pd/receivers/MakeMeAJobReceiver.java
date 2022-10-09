package org.purplei2p.i2pd.receivers;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.purplei2p.i2pd.DaemonWrapper;
import org.purplei2p.i2pd.I2PDActivity;
import org.purplei2p.i2pd.MyJobService;

public class MakeMeAJobReceiver extends BroadcastReceiver {
    private static final String TAG = "i2pdMMJR";
    public static final String MAKEMEAJOB_ACTION = "org.purplei2p.i2pd.MAKEMEAJOB_ACTION";
    private static final int JOB_ID = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "MakeMeAJobReceiver.onReceive entered: actionString=='" + intent.getAction() + "'");
        if(!MAKEMEAJOB_ACTION.equals(intent.getAction())){
            Log.d(TAG, "MakeMeAJobReceiver exiting, got unknown action");
            return;
        }
        scheduleJob(context);
        Log.d(TAG, "MakeMeAJobReceiver: MyJobService job scheduled");
    }

    private static void scheduleJob(Context context) {
        if(!I2PDActivity.isJobServiceApiAvailable()) {
            Log.d(TAG, "MakeMeAJobReceiver JobService api not available, need OS API LEVEL >= 21'");
            return;
        }
        JobScheduler jobScheduler = (JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        ComponentName serviceComponent = new ComponentName(context, MyJobService.class);
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, serviceComponent);

        // a workaround for "You're trying to build a job
        // with no constraints, this is not allowed." exception
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        builder.setPersisted(true);

        // ping it every minute, otherwise on my physical device API >= 21 it stops after 10 minutes
        //builder.setPeriodic(60*1000);

        jobScheduler.schedule(builder.build());
    }
}

