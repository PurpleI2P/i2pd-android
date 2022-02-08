#!/bin/bash

set -e

function build_one {
	mkdir -p out/${CPU}
	mkdir output

	echo "Configuring OpenSSL for ${CPU}..."
	./Configure \
	--prefix="$PWD/output" \
	${TARGET} \
	no-shared \
	no-tests \
	-D__ANDROID_API__=${API} \
	-Wno-macro-redefined

	echo "Building OpenSSL for ${CPU}..."
	make -j $(nproc) > output/build.log

	echo "Installing to temporary directory..."
	make install_sw >> output/build.log

	cp output/lib/*.a out/${CPU}

	if [[ -n 'out/include' ]]; then
		mkdir -p out/include
		cp -r output/include/openssl out/include
	fi

	rm -rf output
	make clean
}

function checkPreRequisites {

	if ! [ -d "openssl" ] || ! [ "$(ls -A openssl)" ]; then
		echo -e "\033[31mFailed! Submodule 'openssl' not found!\033[0m"
		echo -e "\033[31mTry to run: 'git submodule update --init'\033[0m"
		exit
	fi

	if [ -z "$ANDROID_NDK_HOME" -a "$ANDROID_NDK_HOME" == "" ]; then
		echo -e "\033[31mFailed! ANDROID_NDK_HOME is empty. Run 'export ANDROID_NDK_HOME=[PATH_TO_NDK]'\033[0m"
		exit
	fi
}

checkPreRequisites

cd openssl
rm -rf out output

if [[ -f 'Makefile' ]]; then
	make clean
fi


PATH=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH

function build {
	for arg in "$@"; do
		case "${arg}" in
			x86_64)
				API=21
				CPU=x86_64
				TARGET=android-x86_64
				build_one
			;;
			arm64)
				API=21
				CPU=arm64-v8a
				TARGET=android-arm64
				build_one
			;;
			arm)
				API=16
				CPU=armeabi-v7a
				TARGET=android-arm
				build_one
			;;
			x86)
				API=16
				CPU=x86
				TARGET=android-x86
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
