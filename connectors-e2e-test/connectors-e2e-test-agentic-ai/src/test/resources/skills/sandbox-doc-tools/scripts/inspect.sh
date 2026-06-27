#!/usr/bin/env bash
# Quick summary of a file imported into the sandbox.
# Usage: bash inspect.sh <path>
set -euo pipefail

path="${1:?usage: inspect.sh <path>}"

if [[ ! -f "$path" ]]; then
  echo "No such file: $path" >&2
  exit 1
fi

echo "== $path =="
echo "size:  $(wc -c < "$path" | tr -d ' ') bytes"
echo "type:  $(file -b "$path" 2>/dev/null || echo unknown)"

# Text-ish files: show line count + a short preview. Binary: stop here.
if file "$path" | grep -qiE 'text|json|xml|csv|ascii'; then
  echo "lines: $(wc -l < "$path" | tr -d ' ')"
  echo "-- preview (first 20 lines) --"
  head -n 20 "$path"
else
  echo "(binary file — no text preview)"
fi
