#!/bin/bash
# The agent's only hands: walk.sh CONFUSION "what I see and why" COMMAND ARGS
#   CONFUSION 0 = clear, 1 = slightly unsure, 2 = confused, 3 = lost
#   COMMAND: shot | tap X Y | long X Y | swipe X1 Y1 X2 Y2 | type TEXT | back | wait SECONDS | open | quit
# Coordinates are pixels of the picture (540 wide, 1170 tall).
D=${WALK_DIR:-/tmp/walk}
CONF=$1; WHY=$2; shift 2
N=$(ls "$D/in" 2>/dev/null | wc -l)
printf '%s\t%s\t%s\t%s\n' "$N" "$CONF" "$*" "$WHY" >> "$D/journal.tsv"
echo "$*" > "$D/in/$(printf %03d $N).cmd"
for i in $(seq 1 600); do
  [ -f "$D/out/$(printf %03d $N).txt" ] && { cat "$D/out/$(printf %03d $N).txt"; exit 0; }
  sleep 0.5
done
echo "timeout: the phone did not answer"; exit 1
