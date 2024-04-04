package org.purplei2p.i2pd;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/** i2pd process API calls via TCP between the Android Java app and i2pd C++-only process.
 * TODO
 */
public class I2pdApi {
    private static String dataDir;
    private static Process i2pdProcess;
    private static final String TAG = "I2pdApi";

    /**
     * returns error info if failed
     * returns "ok" if daemon initialized and started okay
     */
    public static String startDaemon(Context ctx, String dataDir, String language){
        try {
            I2pdApi.dataDir = dataDir;
            Process p = I2pdApi.i2pdProcess = Runtime.getRuntime().exec(new String[] {
                    ctx.getApplicationInfo().nativeLibraryDir+"/libi2pd.so",
                    "--datadir="+dataDir
            });
            new Thread(() -> {
                try {
                    try (BufferedInputStream bis = new BufferedInputStream(p.getInputStream())) {
                        try (InputStreamReader sr = new InputStreamReader(bis)) {
                            try (BufferedReader r = new BufferedReader(sr)) {
                                while(true){
                                    String s = r.readLine();
                                    if (s == null) break;
                                    Log.i(TAG, s);
                                }
                            }
                        }
                    }
                }catch(Throwable tr){
                    Log.e(TAG, "", tr);
                }
            }, "i2pd-stdout");
            new Thread(() -> {
                try {
                    try (BufferedInputStream bis = new BufferedInputStream(p.getErrorStream())) {
                        try (InputStreamReader sr = new InputStreamReader(bis)) {
                            try (BufferedReader r = new BufferedReader(sr)) {
                                while(true){
                                    String s = r.readLine();
                                    if (s == null) break;
                                    Log.i(TAG, s);
                                }
                            }
                        }
                    }
                }catch(Throwable tr){
                    Log.e(TAG, "", tr);
                }
            }, "i2pd-stderr");
            return "ok";
        } catch (Throwable tr) {
            Log.e(TAG, "", tr);
            return "Error in exec(): " + tr;
        }
    }

    public static void stopDaemon(){
        Process p = i2pdProcess;
        if (p != null) {
            try {
                p.destroy();
            } catch (Throwable tr) {
                Log.e(TAG, "", tr);
            }
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