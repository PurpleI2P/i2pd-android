#!/bin/bash

set -e

CMAKE_VERSION=3.22.1

function build_one {
	mkdir -p build out/$CPU
	cd build

	cmake \
	-G "Unix Makefiles" \
	-DUPNPC_BUILD_SHARED=False \
	-DUPNPC_BUILD_TESTS=False \
	-DUPNPC_BUILD_SAMPLE=False \
	-DANDROID_NATIVE_API_LEVEL=$API \
	-DANDROID_ABI=$CPU \
	-DCMAKE_BUILD_TYPE=Release \
	-DANDROID_NDK=$ANDROID_NDK_HOME \
	-DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
	-DCMAKE_INSTALL_PREFIX=../out/$CPU \
	..

	echo "Building..."
	cmake --build . -- libminiupnpc-static
	make install

	cd ..
	rm -rf build
}

function checkPreRequisites {

	if ! [ -d "miniupnp" ] || ! [ "$(ls -A miniupnp)" ]; then
		echo -e "\033[31mFailed! Submodule 'miniupnp' not found!\033[0m"
		echo -e "\033[31mTry to run: 'git submodule update --init'\033[0m"
		exit
	fi

	if [ -z "$ANDROID_SDK_ROOT" -a "$ANDROID_SDK_ROOT" == "" ]; then
		echo -e "\033[31mFailed! ANDROID_SDK_ROOT is empty. Run 'export ANDROID_SDK_ROOT=[PATH_TO_NDK]'\033[0m"
		exit
	fi

	if [ -z "$ANDROID_NDK_HOME" -a "$ANDROID_NDK_HOME" == "" ]; then
		echo -e "\033[31mFailed! ANDROID_NDK_HOME is empty. Run 'export ANDROID_NDK_HOME=[PATH_TO_NDK]'\033[0m"
		exit
	fi
}

function build {
	for arg in "$@"; do
		case "$arg" in
			x86_64)
				API=21
				CPU=x86_64
				build_one
			;;
			arm64)
				API=21
				CPU=arm64-v8a
				build_one
			;;
			arm)
				API=16
				CPU=armeabi-v7a
				build_one
			;;
			x86)
				API=16
				CPU=x86
				build_one
			;;
			*)
			;;
		esac
	done
}

checkPreRequisites

cd miniupnp/miniupnpc
rm -rf build out

# add cmake from Android SDK to PATH
PATH=$ANDROID_SDK_ROOT/cmake/$CMAKE_VERSION/bin:$PATH

if (( $# == 0 )); then
	build x86_64 arm64 arm x86
else
	build $@
fi
