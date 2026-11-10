#!/usr/bin/env bash
# Rebuilds the Dolby Digital 5.1 test clips the TV plays over HDMI ARC, one per speaker.
# Run from the repository root after changing ChannelClip, Sweep or Marker. Needs ffmpeg.
set -euo pipefail
raw="$(mktemp -d)"
out="app/src/main/assets/theatre"
mkdir -p "$out"
./gradlew -q :dsp:theatreClips -Pout="$raw"
for f in "$raw"/*.raw; do
  code="$(basename "$f" .raw)"
  # dialnorm -31: no dialogue-level attenuation in the receiver; 448 kb/s keeps the sweep clean to 20 kHz
  ffmpeg -loglevel error -y -f s16le -ar 48000 -ac 6 -channel_layout 5.1 -i "$f" \
    -c:a ac3 -b:a 448k -dialnorm -31 -f ac3 "$out/$code.ac3"
  echo "$out/$code.ac3"
done
rm -rf "$raw"
