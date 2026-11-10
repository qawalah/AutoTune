#!/usr/bin/env bash
# Rebuilds the Dolby Digital 5.1 test clips the TV plays over HDMI ARC: one per speaker, the system test per layout
# and each speaker's level noise. Run from the repository root after changing ChannelClip, PassClip, LevelNoise, Sweep
# or Marker. Needs ffmpeg.
set -euo pipefail
raw="$(mktemp -d)"
out="app/src/main/assets/theatre"
mkdir -p "$out"
./gradlew -q :dsp:theatreClips -Pout="$raw"
for f in "$raw"/*.raw; do
  name="$(basename "$f" .raw)"
  # dialnorm -31: no dialogue-level attenuation in the receiver. 448 kb/s keeps a sweep clean to 20 kHz; the level
  # noise (500 Hz to 2 kHz, or the sub's 40 to 100 Hz) needs far less.
  rate=448k
  case "$name" in pass-*) rate=384k ;; noise-*) rate=192k ;; esac
  ffmpeg -loglevel error -y -f s16le -ar 48000 -ac 6 -channel_layout 5.1 -i "$f" \
    -c:a ac3 -b:a "$rate" -dialnorm -31 -f ac3 "$out/$name.ac3"
  echo "$out/$name.ac3"
done
# decode the system tests back and check every sweep is still found where it should be
for f in "$out"/pass-*.ac3; do
  ffmpeg -loglevel error -y -i "$f" -f s16le -ac 6 -ar 48000 "$raw/$(basename "$f" .ac3).decoded.raw"
done
./gradlew -q :dsp:theatreCheck -Pout="$raw"
rm -rf "$raw"
