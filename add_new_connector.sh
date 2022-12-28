#!/bin/bash

CONNECTORS_DIR='connectors'
TMP_DIR="${CONNECTORS_DIR}/tmp"

CONNECTOR_NAME=${1}
ARTIFACT_NAME="connector-${CONNECTOR_NAME}"

VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)

mvn archetype:generate \
  -DarchetypeGroupId=io.camunda.connector \
  -DarchetypeArtifactId=connector-archetype-internal \
  -DarchetypeVersion=${VERSION} \
  -DinteractiveMode=false \
  -DconnectorName=${CONNECTOR_NAME} \
  -DoutputDirectory=${TMP_DIR}

# Rename directory to follow convention
mv "${TMP_DIR}/${ARTIFACT_NAME}" "${CONNECTORS_DIR}/${CONNECTOR_NAME}"

rm -rf TMP_DIR
