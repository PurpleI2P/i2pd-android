package org.purplei2p.i2pd;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

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
    public static String startDaemon(final Context ctx, final String dataDir, String language, final DaemonWrapper daemonWrapper){
        try {
            i2pdProcess = null;
            I2pdApi.dataDir = dataDir;
            File pidFile = new File(dataDir, "i2pd.pid");
            final Process p = Runtime.getRuntime().exec(new String[]{
                    ctx.getApplicationInfo().nativeLibraryDir + "/libi2pd.so",
                    "--datadir=" + dataDir,
                    "--pidfile=" + pidFile.getAbsolutePath()
            });
            i2pdProcess = (Throwable tr) -> {
                try {
                    if (p.isAlive()) {
                        if (tr != null)
                            Log.e(TAG, "destroying the subprocess \"i2pd\", reason: " + tr, tr);
                        else
                            Log.e(TAG, "destroying the subprocess \"i2pd\", reason: null");
                        p.destroy();
                    }else{
                        if (tr != null)
                            Log.e(TAG, "skipping destroy of a dead subprocess \"i2pd\", reason: " + tr, tr);
                        else
                            Log.e(TAG, "skipping destroy of a dead subprocess \"i2pd\", reason: null");
                    }
                } catch (Throwable tr2) {
                    Log.e(TAG, "", tr2);
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
            }, "i2pd-stdout").start();
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
                try {
                    p.waitFor();
                } catch (Throwable tr) {
                    Log.e(TAG, "", tr);
                }
                final int errorLevel = p.exitValue();
                Log.i(TAG, "i2pd process exit code: " + errorLevel);
                final Throwable trReason = new Throwable("subprocess \"i2pd\" exited with exit code " + errorLevel);
                try {
                    stopDaemon(trReason);
                    Log.i(TAG, "stopDaemon completed");
                } catch (Throwable tr) {
                    Log.e(TAG, "Called stopDaemon, got exception", tr);
                }
                new Thread(() -> {
                    try {
                        daemonWrapper.stopDaemon(trReason);
                        Log.i(TAG, "daemonWrapper.stopDaemon completed");
                    } catch (Throwable tr) {
                        Log.e(TAG, "Called daemonWrapper.stopDaemon, got exception", tr);
                    }
                }, "stop the daemonWrapper thread").start();
            }, "i2pd-stderr").start();
            new Thread(() -> {
                try {
                    try (BufferedOutputStream bos = new BufferedOutputStream(p.getOutputStream())) {
                        try (OutputStreamWriter sr = new OutputStreamWriter(bos)) {
                            try (BufferedWriter r = new BufferedWriter(sr)) {
                                while (true) {
                                    synchronized (Thread.currentThread()) {
                                        Thread.currentThread().wait(100);
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable tr) {
                    Log.e(TAG, "", tr);
                }
            }, "i2pd-stdin").start();
            return "ok";
        } catch (Throwable tr) {
            Log.e(TAG, "", tr);
            return "Error in exec(): " + tr;
        }
    }

    public static void stopDaemon(Throwable tr){
        AbstractProcess p = i2pdProcess;
        if (p != null) {
            p.kill(tr);
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
