#!/bin/bash -xue

RELEASE_VERSION=${1}
NEXT_VERSION=${2}-SNAPSHOT
RELEASE_MESSAGE="ci: release version ${RELEASE_VERSION}"
NEXT_MESSAGE="ci: set next version ${NEXT_VERSION}"

mvn --batch-mode versions:set -DnewVersion=${RELEASE_VERSION} -DgenerateBackupPoms=false
mvn --batch-mode package
git commit -a -m "${RELEASE_MESSAGE}"
git tag -a ${RELEASE_VERSION} -m "${RELEASE_MESSAGE}"
STAGE=prod ./deploy 
mvn --batch-mode versions:set -DnewVersion=${NEXT_VERSION} -DgenerateBackupPoms=false
mvn --batch-mode verify
git commit -a -m "${NEXT_MESSAGE}"
git push --follow-tags
