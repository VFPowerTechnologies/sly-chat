#!/bin/bash
set -eu

OPENSSL_CONFIGURE_OPTIONS="{{configure-options}}"

#-DOPENSSL_SYS_WIN32_CYGWIN is used to remove a dependency on gdi
#if this isn't set, RAND_screen will call out to gdi to read the contents of the screen
#as this function is unused in openssl itself and in sqlcipher, there's no harm in doing this
./Configure mingw64 $OPENSSL_CONFIGURE_OPTIONS --prefix="{{prefix}}" --cross-compile-prefix=x86_64-w64-mingw32- -DOPENSSL_SYS_WIN32_CYGWIN
make depend install
