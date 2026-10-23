#!/bin/bash

# Fetches release archives of the dependencies into deps/, checks their sha256 and unpacks them.
# Nothing is downloaded when an archive is already in deps/ (offline builds, F-Droid prebuild):
# put it there under the same name.
#
#   deps/boost      headers and sources of program_options, json and url (compiled in deps.mk)
#   deps/openssl    sources, built by build_openssl.sh with its own Configure
#   deps/miniupnpc  sources (compiled in deps.mk), headers in export/miniupnpc, generated miniupnpcstrings.h

set -e

BOOST_VERSION=1.84.0
BOOST_SHA256=a5800f405508f5df8114558ca9855d2640a2de8f0445f051fa1c7c3383045724
BOOST_LIBS="program_options json url"

OPENSSL_VERSION=3.5.9
OPENSSL_SHA256=603f5602e2eef00d77fbd429d34dcd5822bb301757a1bc9cdb24c670f1eb859a

MINIUPNPC_VERSION=2.3.3
MINIUPNPC_SHA256=d52a0afa614ad6c088cc9ddff1ae7d29c8c595ac5fdd321170a05f41e634bd1a

DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
DEPS_DIR="$DIR/deps"
mkdir -p "$DEPS_DIR"

# download <archive> <sha256> <url>...: the first URL that answers, the archive must match the checksum
function download {
	local archive="$DEPS_DIR/$1" sha256="$2"
	shift 2
	if [ ! -f "$archive" ]; then
		for url in "$@"; do
			echo "Downloading $url..."
			if curl -fL --retry 3 --connect-timeout 30 -o "$archive.part" "$url"; then
				mv "$archive.part" "$archive"
				break
			fi
		done
		[ -f "$archive" ] || { echo "Cannot download $(basename "$archive")"; exit 1; }
	fi
	echo "$sha256  $archive" | sha256sum -c -
}

# prepared <name> <stamp>: true when deps/<name> is unpacked from the same version
function prepared {
	[ -f "$DEPS_DIR/$1/.version" ] && [ "$(cat "$DEPS_DIR/$1/.version")" == "$2" ]
}

function fetch_boost {
	local name="boost_${BOOST_VERSION//./_}" stamp="$BOOST_VERSION $BOOST_LIBS" dest="$DEPS_DIR/boost"
	if prepared boost "$stamp"; then echo "Boost $BOOST_VERSION is ready"; return; fi
	download "$name.tar.gz" "$BOOST_SHA256" "https://archives.boost.io/release/$BOOST_VERSION/source/$name.tar.gz"
	echo "Unpacking Boost $BOOST_VERSION..."
	rm -rf "$dest"
	mkdir -p "$dest"
	local sources=()
	for lib in $BOOST_LIBS; do
		sources+=("$name/libs/$lib/src")
	done
	tar -xzf "$DEPS_DIR/$name.tar.gz" -C "$dest" --strip-components=1 "$name/boost" "${sources[@]}" "$name/LICENSE_1_0.txt"
	echo "$stamp" > "$dest/.version"
}

function fetch_openssl {
	local name="openssl-$OPENSSL_VERSION" dest="$DEPS_DIR/openssl"
	if prepared openssl "$OPENSSL_VERSION"; then echo "OpenSSL $OPENSSL_VERSION is ready"; return; fi
	download "$name.tar.gz" "$OPENSSL_SHA256" \
		"https://github.com/openssl/openssl/releases/download/$name/$name.tar.gz" \
		"https://www.openssl.org/source/$name.tar.gz"
	echo "Unpacking OpenSSL $OPENSSL_VERSION..."
	rm -rf "$dest"
	mkdir -p "$dest"
	tar -xzf "$DEPS_DIR/$name.tar.gz" -C "$dest" --strip-components=1
	echo "$OPENSSL_VERSION" > "$dest/.version"
}

function fetch_miniupnpc {
	local name="miniupnpc-$MINIUPNPC_VERSION" dest="$DEPS_DIR/miniupnpc"
	if prepared miniupnpc "$MINIUPNPC_VERSION"; then echo "miniupnpc $MINIUPNPC_VERSION is ready"; return; fi
	# The author's site only; free.fr serves no HTTPS, the checksum guards the archive
	download "$name.tar.gz" "$MINIUPNPC_SHA256" \
		"https://miniupnp.tuxfamily.org/files/$name.tar.gz" \
		"http://miniupnp.free.fr/files/$name.tar.gz"
	echo "Unpacking miniupnpc $MINIUPNPC_VERSION..."
	rm -rf "$dest"
	mkdir -p "$dest"
	tar -xzf "$DEPS_DIR/$name.tar.gz" -C "$dest" --strip-components=1
	# i2pd includes <miniupnpc/...>; CMake would generate miniupnpcstrings.h ("Android" as its CMAKE_SYSTEM_NAME)
	mkdir -p "$dest/export/miniupnpc" "$dest/generated"
	cp "$dest"/include/*.h "$dest/export/miniupnpc/"
	sed -e 's/${CMAKE_SYSTEM_NAME}/Android/' -e "s/\${PROJECT_VERSION}/$MINIUPNPC_VERSION/" \
		"$dest/miniupnpcstrings.h.cmake" > "$dest/generated/miniupnpcstrings.h"
	echo "$MINIUPNPC_VERSION" > "$dest/.version"
}

fetch_boost
fetch_openssl
fetch_miniupnpc
