#!/bin/bash
# Starts the pretend phone (real AutoTune screens on the JVM, pretend car). Waits until it is ready.
# Usage: tools/walk/start.sh [dir]   (default /tmp/walk)
D=${1:-/tmp/walk}
rm -rf "$D"; mkdir -p "$D"
cd "$(dirname "$0")/../.."
nohup ./gradlew :app:testDebugUnitTest --tests 'dev.autotune.walk.WalkDriver' -Dwalk.dir="$D" ${ROBO_DIR:+-Drobolectric.dependency.dir="$ROBO_DIR"} -i > "$D/gradle.log" 2>&1 &
for i in $(seq 1 300); do [ -f "$D/ready" ] && { echo "ready"; exit 0; }; sleep 2; done
echo "not ready"; tail -30 "$D/gradle.log"; exit 1
