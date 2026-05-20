#!/usr/bin/env bash
# Swing JFileChooser smoke test (needs display). Uses project DirectoryFriendlyExtensionFilter.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
OUT="$ROOT/build/tmp/filechooser-smoke-classes"
mkdir -p "$OUT"
./gradlew compileJava -q
javac -encoding UTF-8 -cp "$ROOT/build/classes/java/main" -d "$OUT" "$ROOT/scripts/FileChooserSmoke.java"
exec java -cp "$OUT:$ROOT/build/classes/java/main" FileChooserSmoke "$@"
