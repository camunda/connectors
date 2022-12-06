#!/bin/bash -eu
# Download element templates as JSON for all bundled connectors to be distributed
# as single ZIP file on the Github Release page

set -o pipefail

RELEASE_VERSION=${1:-SNAPSHOT}
eval $(sed 's/ARG/export/gp;d' Dockerfile)

ARTIFACT_DIR=${PWD}
WORKING_DIR="$(mktemp -d)"
trap 'rm -rf -- "${WORKING_DIR}"' EXIT

pushd ${WORKING_DIR}

find ${ARTIFACT_DIR} -name "*.json" | grep "element-templates" |\
while read TEMPLATE_FILE;
do cp $TEMPLATE_FILE ${WORKING_DIR};
done

tag_version() {
  local file=$1
  local version=$(cat $file | jq '. | if type == "array" then .[0] else . end | .version')

  local base_name="${file%%".json"*}"

  if [[ "null" != "$version" ]]; then
    echo "tag_version: Renaming $file -> $base_name-$version.json"

    mv "$file" "$base_name-$version.json"
  else
    echo "tag_version: Keeping $file (unversioned)"
  fi
}

for file in *.json; do
  tag_version $file
done

tar czvf ${ARTIFACT_DIR}/connectors-bundle-templates-${RELEASE_VERSION}.tar.gz *.json
zip ${ARTIFACT_DIR}/connectors-bundle-templates-${RELEASE_VERSION}.zip *.json

popd
