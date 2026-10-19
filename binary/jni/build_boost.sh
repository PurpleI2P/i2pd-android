#!/bin/bash

# Fetches Boost sources: headers plus sources of compiled libraries used by i2pd.
# These libraries are compiled by ndk-build from the sources (see deps.mk),
# so no b2/Boost-for-Android toolchain configuration is needed.

set -e

BOOST_VERSION=1.84.0
BOOST_SHA256=a5800f405508f5df8114558ca9855d2640a2de8f0445f051fa1c7c3383045724
BOOST_LIBS="program_options json url"

DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
DEPS_DIR="$DIR/deps"
DEST="$DEPS_DIR/boost"
NAME="boost_${BOOST_VERSION//./_}"
TARBALL="$DEPS_DIR/$NAME.tar.gz"
STAMP="$BOOST_VERSION $BOOST_LIBS"

if [ -f "$DEST/.version" ] && [ "$(cat "$DEST/.version")" == "$STAMP" ]; then
	echo "Boost $BOOST_VERSION is already prepared"
	exit 0
fi

mkdir -p "$DEPS_DIR"

if [ ! -f "$TARBALL" ]; then
	echo "Downloading Boost $BOOST_VERSION..."
	curl -fL --retry 3 -o "$TARBALL.part" "https://archives.boost.io/release/$BOOST_VERSION/source/$NAME.tar.gz"
	mv "$TARBALL.part" "$TARBALL"
fi

echo "$BOOST_SHA256  $TARBALL" | sha256sum -c -

echo "Unpacking Boost $BOOST_VERSION..."
rm -rf "$DEST"
mkdir -p "$DEST"
LIB_SOURCES=()
for lib in $BOOST_LIBS; do
	LIB_SOURCES+=("$NAME/libs/$lib/src")
done
tar -xzf "$TARBALL" -C "$DEST" --strip-components=1 \
	"$NAME/boost" \
	"${LIB_SOURCES[@]}" \
	"$NAME/LICENSE_1_0.txt"

echo "$STAMP" > "$DEST/.version"
