#!/bin/bash
set -eu

OPENSSL_CONFIGURE_OPTIONS="{{configure-options}}"

./config $OPENSSL_CONFIGURE_OPTIONS --prefix="{{prefix}}" -fPIC
make depend install
