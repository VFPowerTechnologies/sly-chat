#!/bin/bash
set -eu

PREFIX="{{prefix}}"
INSTALL_ROOT="$(pwd)/prefix"

INC_DIRS="-I$PREFIX/include"
LIB_DIRS="-L$PREFIX/lib"

IOS_TARGET=7.0
HOST=arm-apple-darwin

buildForSDK() {
    local SDK="$1"
    local ARCH_FLAGS="$2"

    export CC="$(xcrun --sdk $SDK --find clang) -isysroot $(xcrun --sdk $SDK --show-sdk-path)"
    export CPP="$(xcrun --sdk $SDK --find cc) -E"

    export CFLAGS="$INC_DIRS $LIB_DIRS -fembed-bitcode -DSQLITE_TEMP_STORE=3 -DSQLITE_ENABLE_COLUMN_METADATA -DSQLITE_HAS_CODEC -DSQLITE_OMIT_DEPRECATED $ARCH_FLAGS -mios-version-min=$IOS_TARGET"

    ./configure --host=$HOST --disable-tcl --disable-editline --disable-readline --disable-shared --prefix="$INSTALL_ROOT/$SDK" --enable-tempstore=yes
    make install

    make clean
}

buildForSDK iphoneos "-arch armv7 -arch armv7s -arch arm64"
buildForSDK iphonesimulator "-arch x86_64 -arch i386"

lipo -create "$INSTALL_ROOT/iphoneos/lib/libsqlcipher.a" "$INSTALL_ROOT/iphonesimulator/lib/libsqlcipher.a" -output "$PREFIX/lib/libsqlcipher.a"
