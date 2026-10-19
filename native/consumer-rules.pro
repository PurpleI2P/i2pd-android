# Natives are registered by explicit class name in JNI_OnLoad (jni/jni_bridge.cpp)
-keep class org.purplei2p.i2pd.nativelib.I2pdNative {
    native <methods>;
}
