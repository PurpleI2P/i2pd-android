#!/bin/bash

set -e

cd "$( dirname "${BASH_SOURCE[0]}" )"

API=23
HOST_OS=`uname -a`

function build_one {
	local stamp="$NDK_REVISION android-$API"
	if [ -f "out/$CPU/lib/libcrypto.a" ] && [ "$(cat "out/$CPU/.stamp" 2>/dev/null)" == "$stamp" ]; then
		echo "OpenSSL for $CPU is up to date"
		return
	fi

	rm -rf out/$CPU
	mkdir -p out/$CPU

	echo "Configuring OpenSSL for $CPU..."
	./Configure \
	--prefix="$PWD/out/$CPU" \
	$TARGET \
	no-shared \
	no-tests \
	no-docs \
	-D__ANDROID_API__=$API \
	-Wno-macro-redefined

	echo "Building OpenSSL for $CPU..."
	make -j $(nproc) > out/build.log

	make install_sw >> out/build.log

	make clean

	echo "$stamp" > "out/$CPU/.stamp"
}

function checkPreRequisites {

	if ! [ -d "openssl" ] || ! [ "$(ls -A openssl)" ]; then
		echo -e "\033[31mFailed! Submodule 'openssl' not found!\033[0m"
		echo -e "\033[31mTry to run: 'git submodule update --init'\033[0m"
		exit 1
	fi

	if [ -z "$ANDROID_NDK_HOME" ]; then
		echo -e "\033[31mFailed! ANDROID_NDK_HOME is empty. Run 'export ANDROID_NDK_HOME=[PATH_TO_NDK]'\033[0m"
		exit 1
	fi
	export ANDROID_NDK_ROOT=$ANDROID_NDK_HOME
	NDK_REVISION=$(grep 'Pkg.Revision' "$ANDROID_NDK_HOME/source.properties" | sed -E 's/^.*= *//')
}

function build {
	for arg in "$@"; do
		case "$arg" in
			x86_64)
				CPU=x86_64
				TARGET=android-x86_64
				build_one
			;;
			arm64)
				CPU=arm64-v8a
				TARGET=android-arm64
				build_one
			;;
			arm)
				CPU=armeabi-v7a
				TARGET=android-arm
				build_one
			;;
			x86)
				CPU=x86
				TARGET=android-x86
				build_one
			;;
			*)
			;;
		esac
	done
}

checkPreRequisites

cd openssl

if [[ "$HOST_OS" == *"_NT-"* ]]; then
  PATH=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/windows-x86_64/bin:$PATH
else
  PATH=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH
fi

if [[ -f 'Makefile' ]]; then
	make clean
fi

if (( $# == 0 )); then
	build x86_64 arm64 arm x86
else
	build $@
fi
