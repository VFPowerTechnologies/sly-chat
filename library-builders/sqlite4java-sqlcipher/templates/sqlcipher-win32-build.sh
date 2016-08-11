#!/bin/bash
set -eu

PREFIX="{{prefix}}"
INC_DIRS="-I$PREFIX/include "
LIB_DIRS="-L$PREFIX/lib "

export CFLAGS="$INC_DIRS $LIB_DIRS -DSQLITE_TEMP_STORE=3 -DSQLITE_ENABLE_COLUMN_METADATA -DSQLITE_HAS_CODEC -DSQLITE_OMIT_DEPRECATED -fPIC"
export CPPFLAGS="$CFLAGS"

HOST=x86_64-w64-mingw32

./configure --host=$HOST --disable-tcl --disable-shared --prefix="$PREFIX" --enable-tempstore=yes config_TARGET_EXEEXT=".exe"
make install
