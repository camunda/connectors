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

wget -i - << EOF
https://github.com/camunda/connector-aws-lambda/releases/download/${AWS_LAMBDA_VERSION}/aws-lambda-connector.json
https://github.com/camunda/connector-google-drive/releases/download/${GOOGLE_DRIVE_VERSION}/google-drive-connector.json
https://github.com/camunda/connector-http-json/releases/download/${HTTP_JSON_VERSION}/http-json-connector.json
https://github.com/camunda/connector-sendgrid/releases/download/${SENDGRID_VERSION}/sendgrid-connector.json
https://github.com/camunda/connector-slack/releases/download/${SLACK_VERSION}/slack-connector.json
https://github.com/camunda/connector-sqs/releases/download/${SQS_VERSION}/aws-sqs-connector.json
EOF

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
