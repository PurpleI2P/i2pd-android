package org.purplei2p.i2pd;

public class I2PD_JNI {
    public static native String getABICompiledWith();

    /**
     * returns error info if failed
     * returns "ok" if daemon initialized and started okay
     */
    public static native String startDaemon();

    //should only be called after startDaemon() success
    public static native void stopDaemon();

    public static native void stopAcceptingTunnels();

    public static native void startAcceptingTunnels();

    public static native void reloadTunnelsConfigs();

    public static native void onNetworkStateChanged(boolean isConnected);

    public static native void setDataDir(String jdataDir);

    public static native int GetTransitTunnelsCount();

    public static native String getWebConsAddr();

    public static native void setLanguage(String jlanguage);

    public static void loadLibraries() {
        //System.loadLibrary("c++_shared");
        System.loadLibrary("i2pd");
    }

    public static native String getDataDir();
}
