#!/bin/bash

CONNECTORS_DIR='connectors'

CONNECTOR_NAME=${1}
ARTIFACT_NAME="connector-${CONNECTOR_NAME}"

VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)

# Using old (3.1.0) version due to a bug in the newer one (3.2.1)
# https://issues.apache.org/jira/browse/ARCHETYPE-584
mvn org.apache.maven.plugins:maven-archetype-plugin:3.1.0:generate \
  -DarchetypeGroupId=io.camunda.connector \
  -DarchetypeArtifactId=connector-archetype-internal \
  -DarchetypeVersion=${VERSION} \
  -DinteractiveMode=false \
  -DconnectorName=${CONNECTOR_NAME} \
  -DoutputDirectory=${CONNECTORS_DIR}

# Rename directory to follow convention
mv "${CONNECTORS_DIR}/${ARTIFACT_NAME}" "${CONNECTORS_DIR}/${CONNECTOR_NAME}"
sed "s/${ARTIFACT_NAME}/${CONNECTOR_NAME}/" ${CONNECTORS_DIR}/pom.xml \
 | diff -p ${CONNECTORS_DIR}/pom.xml /dev/stdin | patch
