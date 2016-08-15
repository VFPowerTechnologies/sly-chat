#!/bin/bash
set -eu

OPENSSL_CONFIGURE_OPTIONS="{{configure-options}}"

#using --cross-compile-prefix causes the build system to attempt to call
#`makedepend`, which no longer exists on modern OSX versions
export CC=x86_64-apple-darwin15-clang
export AR=x86_64-apple-darwin15-ar
export RANLIB=x86_64-apple-darwin15-ranlib

./Configure darwin64-x86_64-cc $OPENSSL_CONFIGURE_OPTIONS --prefix="{{prefix}}" -fPIC
make depend install
