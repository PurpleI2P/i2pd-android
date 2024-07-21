#!/bin/bash

# https://stackoverflow.com/a/246128
SOURCE="${0}"
while [ -h "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
  DIR="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
DIR="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"

# Checking Android SDK
if [ -z "$ANDROID_HOME" -a "$ANDROID_HOME" == "" ]; then
        echo -e "\033[31mFailed! ANDROID_HOME is empty. Run 'export ANDROID_HOME=[PATH_TO_SDK]'\033[0m"
        exit
fi

# Checking Android NDK
if [ -z "$ANDROID_NDK_HOME" -a "$ANDROID_NDK_HOME" == "" ]; then
	echo -e "\033[31mFailed! ANDROID_NDK_HOME is empty. Run 'export ANDROID_NDK_HOME=[PATH_TO_NDK]'\033[0m"
	exit 1
fi

_NDK_OPTS="-j `nproc` NDK_MODULE_PATH=$DIR"

help()
{
	echo "Syntax: $(basename "$SOURCE") [-m|d|s|h|v]"
	echo "Options:"
	echo "m     Rename binaries as libraries."
	echo "d     Debug build."
	echo "s     Strip binaries."
	echo "h     Print this Help."
	echo "v     Verbose NDK output."
	echo
}

while getopts ":dmsvh" option; do
	case $option in
		d) # debug build
			_NDK_OPTS="$_NDK_OPTS NDK_DEBUG=1"
			;;
		m) # make module
			_MODULE=1
			;;
		s) # strip binaries
			_STRIP=1
			;;
		v) # verbose output
			_NDK_OPTS="$_NDK_OPTS V=1 NDK_LOG=1"
			;;
		h) # display help
			help
			exit;;
		\?) # Invalid option
			echo "Error: Invalid option. Use $(basename "$SOURCE") -h for help"
			exit;;
	esac
done

# Building
echo Building boost...
./build_boost.sh

echo Building openssl...
./build_openssl.sh

echo Building miniupnpc...
./build_miniupnpc.sh

echo Building i2pd...
$ANDROID_NDK_HOME/ndk-build $_NDK_OPTS

pushd $DIR/../libs
for xarch in $(ls .); do
	if [ ! -z "$_STRIP" ]; then
		$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip -s $xarch/i2pd
	fi
	if [ ! -z "$_MODULE" ]; then
		mv $xarch/i2pd $xarch/libi2pd.so
	fi
done
popd
