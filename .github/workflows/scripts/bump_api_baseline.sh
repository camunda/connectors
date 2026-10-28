#!/usr/bin/env bash
# Sets the API compatibility baseline (api.baseline.version in parent/pom.xml) to the given released
# version. Removes the accepted breaks that the released version contains, i.e. those in the
# api-changes.json of its tag; breaks accepted after the release (e.g. on main after the release
# branch was cut) are kept. Requires the release tag to be fetched. See docs/api-compatibility.md.
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
  released=$(git show "refs/tags/${version}:${file}" 2>/dev/null \
    | jq '[.[] | select(.extension == "revapi.differences") | .configuration.differences[]]' \
    || echo '[]')
  jq --argjson released "$released" \
    '(.[] | select(.extension == "revapi.differences") | .configuration.differences)
       |= map(select(. as $d | $released | any(. == $d) | not))' "$file" > "$file.tmp"
  mv "$file.tmp" "$file"
  echo "Removed the accepted breaks released in ${version} from $file"
done
