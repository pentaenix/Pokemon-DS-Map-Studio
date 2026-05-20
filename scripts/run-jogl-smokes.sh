#!/usr/bin/env bash
set -uo pipefail
# Continue after a smoke crash so all three logs are collected (set +e per run).

mkdir -p build/jogl-smoke-classes build/logs

CP="build/libs/pokemon-ds-map-studio.jar"

echo "Using CP=$CP"

javac -encoding UTF-8 -cp "$CP" -d build/jogl-smoke-classes \
  scripts/JoglProfileSmoke.java \
  scripts/JoglCanvasSmoke.java \
  scripts/JoglPanelSmoke.java

run_one() {
  local name="$1"
  shift

  echo
  echo "===== $name ====="
  set +e
  {
    echo "===== $name ====="
    java -showversion \
      -Djava.awt.headless=false \
      -Dsun.java2d.metal=false \
      -Dapple.awt.UIElement=false \
      -Dapple.laf.useScreenMenuBar=false \
      -cp "build/jogl-smoke-classes:$CP" \
      "$@"
  } 2>&1 | tee "build/logs/${name}.log"
  local ec=${PIPESTATUS[0]}
  echo "(exit $ec)" | tee -a "build/logs/${name}.log"
}

# GUI smokes keep the JVM alive until the window closes; cap runtime so CI/scripts finish.
run_one_gui_timeout() {
  local name="$1"
  shift
  local secs="${SMOKE_GUI_SECONDS:-15}"

  echo
  echo "===== $name (auto-stop after ${secs}s — close window earlier if you prefer) ====="
  set +e
  {
    echo "===== $name ====="
    java -showversion \
      -Djava.awt.headless=false \
      -Dsun.java2d.metal=false \
      -Dapple.awt.UIElement=false \
      -Dapple.laf.useScreenMenuBar=false \
      -cp "build/jogl-smoke-classes:$CP" \
      "$@" &
    local pid=$!
    sleep "$secs"
    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
  } 2>&1 | tee "build/logs/${name}.log"
  echo "(gui stopped after ${secs}s)" | tee -a "build/logs/${name}.log"
}

run_one JoglProfileSmoke JoglProfileSmoke
run_one_gui_timeout JoglCanvasSmoke JoglCanvasSmoke
run_one_gui_timeout JoglPanelSmoke JoglPanelSmoke
