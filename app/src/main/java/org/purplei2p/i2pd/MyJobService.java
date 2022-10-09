package org.purplei2p.i2pd;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MyJobService extends JobService {
    private static final String TAG = "i2pdJobSrvc";

    private DaemonWrapper.StateUpdateListener listener;

    public static DaemonWrapper getI2PDDaemon() {
        DaemonWrapper daemon = I2PDActivity.getDaemon();
        if (daemon==null) throw new NullPointerException("i2pdDaemon==null");
        return daemon;
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        Log.d(TAG,"onStartJob entered");
        listener = (oldValue, newValue) -> {
            if(!newValue.needsToBeAlive()) {
            /*synchronized (MyJobService.this) {
                MyJobService.this.notifyAll(); //wakeup
            }*/

                removeDaemonStateChangeListener();
                jobFinished(params, false);
            }
        };

        getI2PDDaemon().addStateChangeListener(listener);

        /*
            "So, the system may kill the process at any time to reclaim memory, and in doing so,
            it terminates the spawned thread running in the process. The solution to this problem is
            typically to schedule a JobService from the BroadcastReceiver, so the system knows
            that there is still active work being done in the process."

            Source for the quote: https://developer.android.com/guide/components/activities/process-lifecycle
        */
        /*
        while(I2PDActivity.getDaemon().getState().needsToBeAlive()){
            synchronized (MyJobService.this) {
                try {
                    MyJobService.this.wait(); // occurs in main GUI thread when not commented out
                } catch (InterruptedException e) {
                    Log.e(TAG,"onStartJob interrupted");
                }
            }
        }
        */
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG,"onStopJob entered");
        removeDaemonStateChangeListener();
        return true;
    }

    private void removeDaemonStateChangeListener() {
        getI2PDDaemon().removeStateChangeListener(listener);
    }
}