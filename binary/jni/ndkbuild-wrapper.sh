#!/usr/bin/env bash

cd $NDK_PROJECT_PATH/jni
$ANDROID_NDK_HOME/ndk-build $*
mkdir $NDK_PROJECT_PATH/libs
cd $NDK_PROJECT_PATH/libs
for f in $(ls .);
do
    strip $f/i2pd
    mv $f/i2pd $f/libi2pd.so
done
