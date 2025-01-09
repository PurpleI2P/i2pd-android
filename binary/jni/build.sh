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

_help()
{
	echo "Syntax: $(basename "$SOURCE") [-m|d|s|h|v]"
	echo "Options:"
	echo "b     Build binary."
	echo "d     Debug build."
	echo "s     Strip binaries."
	echo "x     Skip libraries rebuild."
	echo "v     Verbose NDK output."
	echo "h     Print this Help."
	echo
}

_failed()
{
	echo "Compilation failed";
	exit 1;
}

while getopts ":dbsvxh" option; do
	case $option in
		d) # debug build
			_NDK_OPTS="$_NDK_OPTS NDK_DEBUG=1"
			;;
		b) # build binary
			_BINARY=1
			;;
		s) # strip binaries
			_STRIP=1
			;;
		x) # skip libraries rebuild
			_SKIP_LIBS=1
			;;
		v) # verbose output
			_NDK_OPTS="$_NDK_OPTS V=1 NDK_LOG=1"
			;;
		h) # display help
			_help
			exit;;
		\?) # Invalid option
			echo "Error: Invalid option. Use $(basename "$SOURCE") -h for help"
			exit;;
	esac
done

# Building
if [ -z "$_SKIP_LIBS" ]; then
	echo "Building boost..."
	./build_boost.sh
	[ $? -ne 0 ] && _failed

	echo "Building openssl..."
	./build_openssl.sh
	[ $? -ne 0 ] && _failed

	echo "Building miniupnpc..."
	./build_miniupnpc.sh
	[ $? -ne 0 ] && _failed
fi

if [ ! -z "$_BINARY" ]; then
	echo "Building i2pd..."
	$ANDROID_NDK_HOME/ndk-build $_NDK_OPTS
fi

echo "Processing binaries (if requested)..."
pushd $DIR/../libs > /dev/null
for xarch in $(ls .); do
	if [ ! -z "$_STRIP" ]; then
		$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip -s $xarch/i2pd
	fi
	if [ ! -z "$_MODULE" ]; then
		mv $xarch/i2pd $xarch/libi2pd.so
	fi
done
popd > /dev/null

echo "Compilation finished"
