if [ -z "$ANDROID_NDK_HOME" -a "$ANDROID_NDK_HOME" == "" ]; then
	echo -e "\033[31mFailed! ANDROID_NDK_HOME is empty. Run 'export ANDROID_NDK_HOME=[PATH_TO_NDK]'\033[0m"
	exit 1
fi
echo Building boost...
./build_boost.sh
echo Building miniupnpc...
./build_miniupnpc.sh
echo Building openssl...
./build_openssl.sh
echo Building i2pd...
NDK_MODULE_PATH=`pwd`
cd ..
NDK_PROJECT_PATH=`pwd`
if [ -z "$BUILD_SO" -a "$BUILD_SO" == "" ]; then
export NDK_MODULE_PATH=$NDK_MODULE_PATH && export NDK_PROJECT_PATH=$NDK_PROJECT_PATH && $ANDROID_NDK_HOME/ndk-build V=1 NDK_LOG=1 -j`nproc`
else
export NDK_MODULE_PATH=$NDK_MODULE_PATH && export NDK_PROJECT_PATH=$NDK_PROJECT_PATH && ./ndkbuild-wrapper.sh V=1 NDK_LOG=1 -j`nproc`
fi
echo "$0 completed."