#!/bin/bash

set -e

BOOST_VERSION=1.78.0
BOOST_LIBS=date_time,filesystem,program_options,system

function build_one {
	echo "Configuring and building..."
	CXXFLAGS="-std=c++14" \
	NCPU=$(nproc) \
	./build-android.sh \
	--boost=$BOOST_VERSION \
	--arch=$CPU \
	--target-version=$API \
	--with-libraries=$BOOST_LIBS \
	--layout=system \
	$ANDROID_NDK_HOME
}

function checkPreRequisites {

	if ! [ -d "boost" ] || ! [ "$(ls -A boost)" ]; then
		echo -e "\033[31mFailed! Submodule 'boost' not found!\033[0m"
		echo -e "\033[31mTry to run: 'git submodule update --init'\033[0m"
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
				TARGET=x86_64
				build_one
			;;
			arm64)
				API=21
				CPU=arm64-v8a
				build_one
			;;
			x86)
				API=16
				CPU=x86
				build_one
			;;
			arm)
				API=16
				CPU=armeabi-v7a
				build_one
			;;
			all)
				API=16
				build_one
			;;
			*)
			;;
		esac
	done
}

checkPreRequisites

cd boost

# disable verbose output
sed -i -E -e 's/d\+2/d\+0/' build-android.sh
sed -i -E -e 's/\"23\.1\"\|\"25\.0\"/\"23\.1\"\|\"23\.2\"\|\"25\.0\"/' build-android.sh

if (( $# == 0 )); then
	build all
else
	build $@
fi
