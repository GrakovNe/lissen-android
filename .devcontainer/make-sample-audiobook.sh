#!/usr/bin/env bash
# A 30-minute book in three 10-minute files that beep once a second, each file at its own
# pitch, so it is easy to hear exactly when playback stops and where it picks up again.
set -euo pipefail

book_dir="$(cd "$(dirname "$0")" && pwd)/.data/audiobooks/Lissen Test/Beeping Book"
mkdir -p "$book_dir"

part=0
for freq in 440 554 659; do
  part=$((part + 1))
  out="$book_dir/0${part} - Part ${part}.mp3"
  [ -s "$out" ] && continue

  echo "Generating $out"
  ffmpeg -loglevel error -y \
    -f lavfi -i "aevalsrc='0.4*sin(2*PI*${freq}*t)*lt(mod(t\,1)\,0.15)':s=22050:d=600" \
    -ac 1 -c:a libmp3lame -b:a 48k \
    -metadata title="Part ${part}" \
    -metadata album="Beeping Book" \
    -metadata artist="Lissen Test" \
    -metadata track="${part}/3" \
    "$out"
done
