#!/usr/bin/env bash
set -euo pipefail

mkdir -p build/jogl-smoke-classes build/logs

CP="build/libs/pokemon-ds-map-studio.jar"

javac -encoding UTF-8 -cp "$CP" -d build/jogl-smoke-classes \
  scripts/JoglProfileSmoke.java \
  scripts/JoglCanvasSmoke.java \
  scripts/JoglPanelSmoke.java

run_one() {
  local name="$1"
  shift

  echo
  echo "===== $name first-thread ====="
  {
    echo "===== $name first-thread ====="
    java -showversion \
      -XstartOnFirstThread \
      -Djava.awt.headless=false \
      -Dsun.java2d.metal=false \
      -Dapple.awt.UIElement=false \
      -Dapple.laf.useScreenMenuBar=false \
      -cp "build/jogl-smoke-classes:$CP" \
      "$@"
  } 2>&1 | tee "build/logs/${name}-first-thread.log"
}

# Same JVM flags as run_one, but auto-stop after N seconds (GUI smokes otherwise run until the window closes).
run_one_gui_timeout() {
  local name="$1"
  shift
  local secs="${SMOKE_GUI_SECONDS:-15}"

  echo
  echo "===== $name first-thread (auto-stop after ${secs}s) ====="
  set +e
  {
    echo "===== $name first-thread ====="
    java -showversion \
      -XstartOnFirstThread \
      -Djava.awt.headless=false \
      -Dsun.java2d.metal=false \
      -Dapple.awt.UIElement=false \
      -Dapple.laf.useScreenMenuBar=false \
      -cp "build/jogl-smoke-classes:$CP" \
      "$@" &
    local pid=$!
    sleep "$secs"
    kill "$pid" 2>/dev/null || true
    sleep 1
    kill -9 "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
  } 2>&1 | tee "build/logs/${name}-first-thread.log"
  echo "(gui stopped after ${secs}s)" | tee -a "build/logs/${name}-first-thread.log"
  set -e
}

run_one JoglProfileSmoke JoglProfileSmoke
run_one_gui_timeout JoglCanvasSmoke JoglCanvasSmoke
run_one_gui_timeout JoglPanelSmoke JoglPanelSmoke
