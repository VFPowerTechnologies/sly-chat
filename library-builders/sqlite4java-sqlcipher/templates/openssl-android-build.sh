#!/bin/bash
#no -eu since setenv-android fails otherwise

#CAST causes some text relocations to be generated so we need to disable it
OPENSSL_CONFIGURE_OPTIONS="no-ssl2 no-ssl3 no-cast no-comp no-dso no-hw no-engine no-shared"

export ANDROID_SDK_ROOT={{sdk-root}}
export ANDROID_NDK_ROOT={{ndk-root}}

. setenv-android.sh
./config $OPENSSL_CONFIGURE_OPTIONS --prefix="{{prefix}}" -fPIC
make depend
make install
