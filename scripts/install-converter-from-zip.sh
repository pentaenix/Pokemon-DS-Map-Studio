#!/usr/bin/env sh
# Copies g3dcvtr.exe (+ Xerces DLL) from a ZIP you downloaded into converter/
# Usage: ./scripts/install-converter-from-zip.sh ~/Downloads/your-archive.zip

set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/converter"
ZIP="${1:-}"

if [ -z "$ZIP" ]; then
    echo "Download G3DCVTR (+ DLL) from ProjectPokemon:" >&2
    echo "  https://projectpokemon.org/home/files/file/3819-g3dcvtr/" >&2
    echo "Then run:" >&2
    echo "  $0 ~/Downloads/TheFileYouDownloaded.zip" >&2
    exit 1
fi
if [ ! -f "$ZIP" ]; then
    echo "File not found: $ZIP" >&2
    exit 1
fi

mkdir -p "$DEST"
TMP="$(mktemp -d)"
cleanup() {
    rm -rf "$TMP"
}
trap cleanup EXIT
unzip -q -o "$ZIP" -d "$TMP"

EXE="$(find "$TMP" -name 'g3dcvtr.exe' -type f ! -path '*/__MACOSX/*' | head -1)"
if [ -z "$EXE" ]; then
    echo "No g3dcvtr.exe found inside the zip." >&2
    exit 1
fi
cp -f "$EXE" "$DEST/"

DLL="$(find "$TMP" \( -iname 'xerces-c_2_5_0.dll' -o -iname 'xerces-c*.dll' \) -type f ! -path '*/__MACOSX/*' | head -1)"
if [ -n "$DLL" ]; then
    cp -f "$DLL" "$DEST/"
else
    echo "Warning: no xerces DLL found in archive; g3dcvtr may need xerces-c_2_5_0.dll beside the exe." >&2
fi

echo "Installed into $DEST :" >&2
ls -la "$DEST" >&2
