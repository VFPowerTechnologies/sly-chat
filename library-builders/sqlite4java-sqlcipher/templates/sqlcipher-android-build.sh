#!/bin/bash

API_VERSION={{api}}
#arm/mips/x86
ARCH={{arch}}

NDK_HOME="{{ndk-home}}"
SYSROOT="$NDK_HOME/platforms/android-$API_VERSION/arch-$ARCH"

HOST={{host}}

PREFIX="{{prefix}}"
INC_DIRS="-I$PREFIX/include "
LIB_DIRS="-L$PREFIX/lib "

export PATH="$NDK_HOME/toolchains/{{eabi}}/prebuilt/{{host-arch}}/bin/:$PATH"
#extensions+col metadata are required by default for sqlite4java
export CFLAGS="--sysroot=$SYSROOT $INC_DIRS $LIB_DIRS -DSQLITE_TEMP_STORE=3 -DSQLITE_ENABLE_COLUMN_METADATA -DSQLITE_HAS_CODEC -DSQLITE_OMIT_DEPRECATED -Dfdatasync=fsync -fPIC"
#required since we need to specify sysroot
export CPPFLAGS="$CFLAGS"

./configure --host=$HOST --disable-tcl --disable-shared --prefix="$PREFIX" --enable-tempstore=yes
make install
