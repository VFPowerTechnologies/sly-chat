#!/bin/bash
set -eu

PREFIX="{{prefix}}"
INC_DIRS="-I$PREFIX/include"
LIB_DIRS="-L$PREFIX/lib"

IOS_TARGET=7.0
ARCH_FLAGS="-arch armv7 -arch armv7s -arch arm64"
HOST=arm-apple-darwin

export CC="$(xcrun --sdk iphoneos --find clang) -isysroot $(xcrun --sdk iphoneos --show-sdk-path)"
export CPP="$(xcrun --sdk iphoneos --find cc) -E"

export CFLAGS="$INC_DIRS $LIB_DIRS -fembed-bitcode -DSQLITE_TEMP_STORE=3 -DSQLITE_ENABLE_COLUMN_METADATA -DSQLITE_HAS_CODEC -DSQLITE_OMIT_DEPRECATED $ARCH_FLAGS -mios-version-min=$IOS_TARGET"

./configure --host=$HOST --disable-tcl --disable-editline --disable-readline --disable-shared --prefix="$PREFIX" --enable-tempstore=yes
make install
