package org.purplei2p.i2pd.nativelib

/**
 * Raw JNI bindings to libi2pd.so.
 *
 * Methods are registered in JNI_OnLoad (native/jni/jni_bridge.cpp), so the class name
 * and method signatures must stay in sync with kMethods there.
 */
object I2pdNative {
    init {
        System.loadLibrary("i2pd")
    }

    /** i2pd version the library was built from, e.g. "2.61.0". */
    @JvmStatic
    external fun version(): String

    /** ABI the library was compiled for. */
    @JvmStatic
    external fun abi(): String
}
