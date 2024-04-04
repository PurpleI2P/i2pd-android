package org.purplei2p.i2pd;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/** i2pd process API calls via TCP between the Android Java app and i2pd C++-only process.
 * TODO
 */
public class I2pdApi {
    private static String dataDir;
    private static AbstractProcess i2pdProcess;
    private static final String TAG = "I2pdApi";

    /**
     * returns error info if failed
     * returns "ok" if daemon initialized and started okay
     */
    public static String startDaemon(Context ctx, String dataDir, String language){
        try {
            i2pdProcess = null;
            final String pidFileName = dataDir + "/i2pd.pid";
            final File pidFile = new File(pidFileName);
            if (pidFile.exists()) {
                try {
                    try (FileInputStream fis = new FileInputStream(pidFile)) {
                        try (BufferedInputStream bis = new BufferedInputStream(fis)) {
                            try (InputStreamReader isr = new InputStreamReader(bis)) {
                                try (BufferedReader br = new BufferedReader(isr)) {
                                    String pidStr = br.readLine();
                                    int pid = Integer.parseInt(pidStr);
                                    Log.i(TAG, "i2pd_pid:"+pid);
                                    Process psProcess = Runtime.getRuntime().exec(new String[]{
                                            "/system/bin/sh",
                                            "-c",
                                            "ps -a|/system/bin/grep "+pid
                                    });
                                    try(InputStream is2 = psProcess.getInputStream()) {
                                        try(BufferedInputStream bis2 = new BufferedInputStream(is2)) {
                                            try(InputStreamReader isr2 = new InputStreamReader(bis2)) {
                                                try (BufferedReader br2 = new BufferedReader(isr2)) {
                                                    String psOutput = br2.readLine()+"\n"+
                                                            br2.readLine();
                                                    Log.i(TAG, "ps.out:"+psOutput);
                                                    if (psOutput.contains(pidStr)) {
                                                        //process is alive
                                                        i2pdProcess = new ExternalProcessImpl(pid);
                                                    } /*else {
                                                        //process is dead, restart
                                                    }*/
                                                }
                                            }
                                        }
                                    }
                                    try(InputStream is3 = psProcess.getErrorStream()) {
                                        try(BufferedInputStream bis3 = new BufferedInputStream(is3)) {
                                            try(InputStreamReader isr3 = new InputStreamReader(bis3)) {
                                                try (BufferedReader br3 = new BufferedReader(isr3)) {
                                                    String psOutput = br3.readLine();
                                                    if (psOutput == null) psOutput = "";
                                                    Log.i(TAG, "ps.err:"+psOutput);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable tr) {
                    Log.e(TAG, "", tr);
                }
            }
            I2pdApi.dataDir = dataDir;
            if(i2pdProcess == null) {
                Process p = Runtime.getRuntime().exec(new String[]{
                        ctx.getApplicationInfo().nativeLibraryDir + "/libi2pd.so",
                        "--datadir=" + dataDir,
                        "--pidfile=" + pidFileName
                });
                i2pdProcess = () -> {
                    try {
                        p.destroy();
                    } catch (Throwable tr) {
                        Log.e(TAG, "", tr);
                    }
                };
                new Thread(() -> {
                    try {
                        try (BufferedInputStream bis = new BufferedInputStream(p.getInputStream())) {
                            try (InputStreamReader sr = new InputStreamReader(bis)) {
                                try (BufferedReader r = new BufferedReader(sr)) {
                                    while (true) {
                                        String s = r.readLine();
                                        if (s == null) break;
                                        Log.i(TAG, s);
                                    }
                                }
                            }
                        }
                    } catch (Throwable tr) {
                        Log.e(TAG, "", tr);
                    }
                }, "i2pd-stdout");
                new Thread(() -> {
                    try {
                        try (BufferedInputStream bis = new BufferedInputStream(p.getErrorStream())) {
                            try (InputStreamReader sr = new InputStreamReader(bis)) {
                                try (BufferedReader r = new BufferedReader(sr)) {
                                    while (true) {
                                        String s = r.readLine();
                                        if (s == null) break;
                                        Log.i(TAG, s);
                                    }
                                }
                            }
                        }
                    } catch (Throwable tr) {
                        Log.e(TAG, "", tr);
                    }
                }, "i2pd-stderr");
            }
            return "ok";
        } catch (Throwable tr) {
            Log.e(TAG, "", tr);
            return "Error in exec(): " + tr;
        }
    }

    public static void stopDaemon(){
        AbstractProcess p = i2pdProcess;
        if (p != null) {
            p.kill();
            i2pdProcess = null;
        }
    }

    public static void startAcceptingTunnels(){}
    public static void stopAcceptingTunnels(){}
    public static void reloadTunnelsConfigs(){}

    public static int getTransitTunnelsCount(){return -1;}
    public static String getWebConsAddr(){return "";}
    public static String getDataDir() {
        return dataDir;
    }

    public static boolean getHTTPProxyState(){return false;}
    public static boolean getSOCKSProxyState(){return false;}
    public static boolean getBOBState(){return false;}
    public static boolean getSAMState(){return false;}
    public static boolean getI2CPState(){return false;}

    public static void onNetworkStateChanged(boolean isConnected){}
}
