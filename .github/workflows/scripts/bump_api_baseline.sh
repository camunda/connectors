#!/usr/bin/env bash
# Sets the API compatibility baseline (api.baseline.version in parent/pom.xml) to the given released
# version and clears the accepted breaks of every gated module, since they are part of the new baseline.
# See docs/api-compatibility.md.
set -euo pipefail

if [[ $# -ne 1 || ! "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Usage: $0 <released version, e.g. 8.11.0>" >&2
  exit 1
fi
version="$1"
pom="parent/pom.xml"

if ! grep -q '<api.baseline.version>' "$pom"; then
  echo "No API compatibility baseline in $pom, nothing to bump."
  exit 0
fi

perl -pi -e "s#<api.baseline.version>[^<]*</api.baseline.version>#<api.baseline.version>${version}</api.baseline.version>#" "$pom"
echo "Set api.baseline.version to ${version}"

git ls-files -z '*api-changes.json' | while IFS= read -r -d '' file; do
  jq '(.[] | select(.extension == "revapi.differences") | .configuration.differences) = []' "$file" > "$file.tmp"
  mv "$file.tmp" "$file"
  echo "Cleared accepted breaks in $file"
done
