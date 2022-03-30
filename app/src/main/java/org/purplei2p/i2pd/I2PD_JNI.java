package org.purplei2p.i2pd;

public class I2PD_JNI {
    public static native String getABICompiledWith();

    public static void loadLibraries() {
        System.loadLibrary("i2pd");
    }

    /**
     * returns error info if failed
     * returns "ok" if daemon initialized and started okay
     */
    public static native String startDaemon();
    public static native void stopDaemon();

    public static native void startAcceptingTunnels();
    public static native void stopAcceptingTunnels();
    public static native void reloadTunnelsConfigs();

    public static native void setDataDir(String jdataDir);
    public static native void setLanguage(String jlanguage);

    public static native int getTransitTunnelsCount();
    public static native String getWebConsAddr();
    public static native String getDataDir();

    public static native boolean getHTTPProxyState();
    public static native boolean getSOCKSProxyState();
    public static native boolean getBOBState();
    public static native boolean getSAMState();
    public static native boolean getI2CPState();

    public static native void onNetworkStateChanged(boolean isConnected);
}
