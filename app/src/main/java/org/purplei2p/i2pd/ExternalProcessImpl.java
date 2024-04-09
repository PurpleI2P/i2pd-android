package org.purplei2p.i2pd;

import android.util.Log;

public class ExternalProcessImpl implements AbstractProcess {
    public static final String TAG = "ExtProcKill";
    private final int pid;
    public ExternalProcessImpl(int pid) {
        this.pid = pid;
    }
    public void kill(Throwable reasonThrowable) {
        if(reasonThrowable!=null) Log.e(TAG,"reasonThrowable", reasonThrowable);
        else Log.e(TAG,"reasonThrowable==null");
        try {
            Runtime.getRuntime().exec(new String[]{
                    "/system/bin/sh", "-c", "kill " + pid
            });
        }catch(Throwable tr){
            Log.e(TAG,"",tr);
        }
    }
}
