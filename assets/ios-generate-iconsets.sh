#!/bin/bash
OUTPUT_DIR="$(dirname $(pwd))/ios/xcode/SlyChat/Assets.xcassets"
RELEASE_OUTPUT_DIR="$OUTPUT_DIR/AppIcon"
DEBUG_OUTPUT_DIR="$OUTPUT_DIR/AppIcon.debug"

rm -fr "$RELEASE_OUTPUT_DIR.appiconset"
rm -fr "$DEBUG_OUTPUT_DIR.appiconset"

python ios-generate-appiconset.py icon_512x512.png "$RELEASE_OUTPUT_DIR"
python ios-generate-appiconset.py debug_icon_512x512.png "$DEBUG_OUTPUT_DIR"
