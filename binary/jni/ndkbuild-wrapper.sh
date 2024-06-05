#!/usr/bin/env bash

cd $NDK_PROJECT_PATH/jni
$ANDROID_NDK_HOME/ndk-build $*

# if it doesn't exist, then create
if [ ! -d $NDK_PROJECT_PATH/libs ]; then mkdir $NDK_PROJECT_PATH/libs; fi

cd $NDK_PROJECT_PATH/libs
for f in $(ls .);
do
  if [ -z "$I2PD_DEBUG" -a "$I2PD_DEBUG" == "" ]; then
    strip $f/i2pd
  fi
  mv $f/i2pd $f/libi2pd.so
done
