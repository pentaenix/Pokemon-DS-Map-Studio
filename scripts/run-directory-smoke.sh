#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p build/directory-smoke-classes build/logs
javac -encoding UTF-8 -d build/directory-smoke-classes scripts/DirectoryAccessSmoke.java
java -cp build/directory-smoke-classes DirectoryAccessSmoke "${1:-$HOME/Downloads}" \
  2>&1 | tee build/logs/DirectoryAccessSmoke.log
