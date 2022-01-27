#!/bin/bash

set -e


function build_one {
	mkdir -p out/${CPU}
	cd out/${CPU}

        cmake \
        -DANDROID_NATIVE_API_LEVEL=${API} \
        -DANDROID_ABI=${CPU} \
        -DCMAKE_BUILD_TYPE=Release \
        -DANDROID_NDK=${ANDROID_NDK_HOME} \
        -DCMAKE_TOOLCHAIN_FILE=${ANDROID_NDK_HOME}/build/cmake/android.toolchain.cmake \
        ../..

        echo "Building..."
        cmake --build . -- libminiupnpc-static

        cd ../..

        if [[ -n 'out/include' ]]; then
                mkdir -p out/include/miniupnpc
                cp include/* out/include/miniupnpc
        fi
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

checkPreRequisites

cd miniupnp/miniupnpc
rm -rf out

# add cmake from Android SDK to PATH
PATH=$ANDROID_SDK_ROOT/cmake/3.10.2.4988404/bin:$PATH

function build {
	for arg in "$@"; do
		case "${arg}" in
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

if (( $# == 0 )); then
	build x86_64 arm64 arm x86
else
	build $@
fi
