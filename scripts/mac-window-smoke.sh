#!/usr/bin/env bash
# Minimal Swing smoke test — NO -XstartOnFirstThread (plain AWT/Swing; EDT-only UI).
# Pokemon DS Map Studio uses the same JVM shape as this script (see ./pdsm run).
#
# Usage (from repo root): ./scripts/mac-window-smoke.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/mac-window-smoke-classes"
SRC="$ROOT/scripts/MacWindowSmoke.java"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" "$SRC"

exec java -showversion \
  -Djava.awt.headless=false \
  -Dsun.java2d.metal=false \
  -Dapple.awt.UIElement=false \
  -Xdock:name=MacWindowSmoke \
  -cp "$OUT" MacWindowSmoke "$@"
