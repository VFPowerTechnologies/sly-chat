#!/bin/bash
set -eu

#CAST causes some text relocations to be generated so we need to disable it
OPENSSL_CONFIGURE_OPTIONS="no-ssl2 no-ssl3 no-cast no-comp no-dso no-hw no-engine no-shared"

./config $OPENSSL_CONFIGURE_OPTIONS --prefix="{{prefix}}" -fPIC
make depend
make install
