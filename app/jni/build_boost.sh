#!/bin/bash

set -e

BOOST_VERSION=1.74.0
BOOST_VERSION_SUFFIX=1_74
BOOST_LIBS=date_time,filesystem,program_options,system

REMOVE_BOOST_LIBS_SUFFIX_x86_64=-clang-mt-x64-${BOOST_VERSION_SUFFIX}
REMOVE_BOOST_LIBS_SUFFIX_ARM64=-clang-mt-a64-${BOOST_VERSION_SUFFIX}
REMOVE_BOOST_LIBS_SUFFIX_x86=-clang-mt-x32-${BOOST_VERSION_SUFFIX}
REMOVE_BOOST_LIBS_SUFFIX_ARM=-clang-mt-a32-${BOOST_VERSION_SUFFIX}

function build_one {
	echo "Configuring and building..."
	CXXFLAGS="-std=c++14" \
	NCPU=$(nproc) \
	./build-android.sh \
	--boost=${BOOST_VERSION} \
	--arch=${CPU} \
	--target-version=${API} \
	--with-libraries=${BOOST_LIBS} \
	${ANDROID_NDK_HOME}

	case "${CPU}" in
		x86_64)
			mkdir -p out/{x86_64,include}
			cp build/out/x86_64/lib/*.a out/${CPU}
			rename 's/'${REMOVE_BOOST_LIBS_SUFFIX_x86_64}'//' out/x86_64/*.a
			cp -r build/out/x86_64/include/boost-${BOOST_VERSION_SUFFIX}/boost out/include

		;;
		arm64-v8a)
			mkdir -p out/{arm64-v8a,include}
			cp build/out/arm64-v8a/lib/*.a out/${CPU}
			rename 's/'${REMOVE_BOOST_LIBS_SUFFIX_ARM64}'//' out/arm64-v8a/*.a
			cp -r build/out/arm64-v8a/include/boost-${BOOST_VERSION_SUFFIX}/boost out/include
		;;
		x86)
			mkdir -p out/{x86,include}
			cp build/out/x86/lib/*.a out/${CPU}
			rename 's/'${REMOVE_BOOST_LIBS_SUFFIX_x86}'//' out/x86/*.a
			cp -r build/out/x86/include/boost-${BOOST_VERSION_SUFFIX}/boost out/include
		;;
		armeabi-v7a)
			mkdir -p out/{armeabi-v7a,include}
			cp build/out/armeabi-v7a/lib/*.a out/${CPU}
			rename 's/'${REMOVE_BOOST_LIBS_SUFFIX_ARM}'//' out/armeabi-v7a/*.a
			cp -r build/out/armeabi-v7a/include/boost-${BOOST_VERSION_SUFFIX}/boost out/include
		;;
		*)
			mkdir -p out/{x86_64,arm64-v8a,x86,armeabi-v7a,include}

			cp build/out/x86_64/lib/*.a out/x86_64
			rename 's/'${REMOVE_BOOST_LIBS_SUFFIX_x86_64}'//' out/x86_64/*.a

			cp build/out/arm64-v8a/lib/*.a out/arm64-v8a
			rename 's/'${REMOVE_BOOST_LIBS_SUFFIX_ARM64}'//' out/arm64-v8a/*.a

			cp build/out/x86/lib/*.a out/x86
			rename 's/'${REMOVE_BOOST_LIBS_SUFFIX_x86}'//' out/x86/*.a

			cp build/out/armeabi-v7a/lib/*.a out/armeabi-v7a
			rename 's/'${REMOVE_BOOST_LIBS_SUFFIX_ARM}'//' out/armeabi-v7a/*.a

			cp -r build/out/arm64-v8a/include/boost-${BOOST_VERSION_SUFFIX}/boost out/include
		;;
	esac
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

checkPreRequisites

cd boost
rm -rf out

function build {
	for arg in "$@"; do
		case "${arg}" in
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
				CPU=x86_64,arm64-v8a,x86,armeabi-v7a
				build_one
			;;
			*)
			;;
		esac
	done
}

if (( $# == 0 )); then
	build all
else
	build $@
fi
