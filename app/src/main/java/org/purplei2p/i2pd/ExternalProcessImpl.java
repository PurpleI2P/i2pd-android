package org.purplei2p.i2pd;

import android.util.Log;

public class ExternalProcessImpl implements AbstractProcess {
    private final int pid;
    public ExternalProcessImpl(int pid) {
        this.pid = pid;
    }
    public void kill() {
        try {
            Runtime.getRuntime().exec(new String[]{
                    "/system/bin/sh", "-c", "kill " + pid
            });
        }catch(Throwable tr){
            Log.e("ExtProcKill","",tr);
        }
    }
}
