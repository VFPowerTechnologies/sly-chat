#!/bin/bash

PREFIX="{{prefix}}"
INC_DIRS="-I$PREFIX/include "
LIB_DIRS="-L$PREFIX/lib "

export CFLAGS="$INC_DIRS $LIB_DIRS -DSQLITE_TEMP_STORE=3 -DSQLITE_ENABLE_COLUMN_METADATA -DSQLITE_HAS_CODEC -DSQLITE_OMIT_DEPRECATED -fPIC"
export CPPFLAGS="$CFLAGS"

./configure --disable-tcl --disable-shared --prefix="$PREFIX" --enable-tempstore=yes
make install
