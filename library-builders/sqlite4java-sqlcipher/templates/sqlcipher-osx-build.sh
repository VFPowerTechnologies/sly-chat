#!/bin/bash
set -eu

PREFIX="{{prefix}}"
INC_DIRS="-I$PREFIX/include"
LIB_DIRS="-L$PREFIX/lib"

#FIXME
#since we're not using gcc, I can't find a way to tell configure to check for
#<host>-cc, so doing this instead
HOST=x86_64-apple-darwin15
export CC=${HOST}-clang

export CFLAGS="$INC_DIRS $LIB_DIRS -DSQLITE_TEMP_STORE=3 -DSQLITE_ENABLE_COLUMN_METADATA -DSQLITE_HAS_CODEC -DSQLITE_OMIT_DEPRECATED -fPIC"
export CPPFLAGS="$CFLAGS"

./configure --host=$HOST --disable-tcl --disable-shared --prefix="$PREFIX" --enable-tempstore=yes
make install
