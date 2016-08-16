#!/bin/bash
#taken from http://stackoverflow.com/a/20703594
set -eu

INPUT_ICON=icon_512x512.png
ICONSET_DIR=SlyChat.iconset
OUTPUT_DIR="$(dirname $(pwd))/desktop/src/main/deploy/package/macosx"
OUTPUT_FILENAME="$OUTPUT_DIR/sly-messenger.icns"
VOLUME_OUTPUT_FILENAME="$OUTPUT_DIR/sly-messenger-volume.icns"

if [ ! -d $OUTPUT_DIR ]; then
    mkdir -p "$OUTPUT_DIR"
fi

mkdir $ICONSET_DIR

sips -z 16 16   "$INPUT_ICON" --out "$ICONSET_DIR/icon_16x16.png"
sips -z 32 32   "$INPUT_ICON" --out "$ICONSET_DIR/icon_16x16@2x.png"
sips -z 32 32   "$INPUT_ICON" --out "$ICONSET_DIR/icon_32x32.png"
sips -z 64 64   "$INPUT_ICON" --out "$ICONSET_DIR/icon_32x32@2x.png"
sips -z 128 128 "$INPUT_ICON" --out "$ICONSET_DIR/icon_128x128.png"
sips -z 256 256 "$INPUT_ICON" --out "$ICONSET_DIR/icon_128x128@2x.png"
sips -z 256 256 "$INPUT_ICON" --out "$ICONSET_DIR/icon_256x256.png"
sips -z 512 512 "$INPUT_ICON" --out "$ICONSET_DIR/icon_256x256@2x.png"

cp "$INPUT_ICON" "$ICONSET_DIR/icon_512x512.png"

iconutil -c icns -o "$OUTPUT_FILENAME" "$ICONSET_DIR"
iconutil -c icns -o "$VOLUME_OUTPUT_FILENAME" "$ICONSET_DIR"
rm -R "$ICONSET_DIR"
