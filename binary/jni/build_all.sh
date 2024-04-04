if [ -z "$ANDROID_NDK_HOME" -a "$ANDROID_NDK_HOME" == "" ]; then
	echo -e "\033[31mFailed! ANDROID_NDK_HOME is empty. Run 'export ANDROID_NDK_HOME=[PATH_TO_NDK]'\033[0m"
	exit 1
fi
./build_boost.sh
./build_miniupnpc.sh
./build_openssl.sh
export NDK_MODULE_PATH=`pwd` && export NDK_PROJECT_PATH=`pwd`/.. && ./ndkbuild-wrapper.sh V=1 NDK_LOG=1 -j`nproc`
echo "$0 completed."
