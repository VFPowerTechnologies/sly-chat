#!/bin/bash

export CONFIG_OPTIONS="{{configure-options}}"
./build-libssl.sh --branch=1.0.2 --archs="armv7 armv7s arm64" --tvos-sdk=''
mv lib include bin "{{prefix}}/"
