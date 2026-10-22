#!/bin/bash

set -e

cd "$( dirname "${BASH_SOURCE[0]}" )"

API=23
# Repository root, for the reproducible paths below
ROOT=$(cd ../.. && pwd)

function build_one {
	# "reproducible": built with the source and NDK paths mapped, see below
	local stamp="$NDK_REVISION android-$API reproducible-paths"
	if [ -f "out/$CPU/lib/libminiupnpc.a" ] && [ "$(cat "out/$CPU/.stamp" 2>/dev/null)" == "$stamp" ]; then
		echo "miniupnpc for $CPU is up to date"
		return
	fi

	rm -rf build out/$CPU
	mkdir -p build out/$CPU
	cd build

	# Reproducible builds: the NDK toolchain compiles with -g, and the absolute paths in the debug info
	# change the build ID of libi2pd.so even after stripping. The toolchain ignores CFLAGS from the
	# environment, CMAKE_C_FLAGS is added to its own flags.
	cmake \
	-G "Unix Makefiles" \
	-DUPNPC_BUILD_SHARED=False \
	-DUPNPC_BUILD_TESTS=False \
	-DUPNPC_BUILD_SAMPLE=False \
	-DANDROID_PLATFORM=android-$API \
	-DANDROID_ABI=$CPU \
	-DCMAKE_BUILD_TYPE=Release \
	-DANDROID_NDK=$ANDROID_NDK_HOME \
	-DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
	-DCMAKE_INSTALL_PREFIX=../out/$CPU \
	-DCMAKE_C_FLAGS="-ffile-prefix-map=$ROOT=. -ffile-prefix-map=$ANDROID_NDK_HOME=/ndk -fdebug-compilation-dir=." \
	..

	echo "Building..."
	cmake --build . -- libminiupnpc-static
	make install

	cd ..
	rm -rf build

	echo "$stamp" > "out/$CPU/.stamp"
}

function checkPreRequisites {

	if ! [ -d "miniupnp" ] || ! [ "$(ls -A miniupnp)" ]; then
		echo -e "\033[31mFailed! Submodule 'miniupnp' not found!\033[0m"
		echo -e "\033[31mTry to run: 'git submodule update --init'\033[0m"
		exit 1
	fi

	if [ -z "$ANDROID_NDK_HOME" ]; then
		echo -e "\033[31mFailed! ANDROID_NDK_HOME is empty. Run 'export ANDROID_NDK_HOME=[PATH_TO_NDK]'\033[0m"
		exit 1
	fi
	NDK_REVISION=$(grep 'Pkg.Revision' "$ANDROID_NDK_HOME/source.properties" | sed -E 's/^.*= *//')

	# prefer cmake from PATH, fall back to the newest one from Android SDK
	if ! command -v cmake > /dev/null && [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME/cmake" ]; then
		PATH=$(ls -d "$ANDROID_HOME"/cmake/*/bin | sort -V | tail -1):$PATH
	fi
	if ! command -v cmake > /dev/null; then
		echo -e "\033[31mFailed! cmake not found. Install it or add 'cmake;<version>' package to Android SDK\033[0m"
		exit 1
	fi
}

function build {
	for arg in "$@"; do
		case "$arg" in
			x86_64)
				CPU=x86_64
				build_one
			;;
			arm64)
				CPU=arm64-v8a
				build_one
			;;
			arm)
				CPU=armeabi-v7a
				build_one
			;;
			x86)
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
rm -rf build

if (( $# == 0 )); then
	build x86_64 arm64 arm x86
else
	build $@
fi
