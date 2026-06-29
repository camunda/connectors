# New Minor Version

Here are the steps we need to do as we release a minor version ("Old Minor Branch") and prepare for a new set of alphas ("Next Alpha").

## After Last Reset of Old Minor Branch

1. Add old minor version to `baseBranches` of `renovate.json`. [Example PR](https://github.com/camunda/connectors/pull/3302/files)

## After Helm Charts Released

These are usually only ready on the day of the release.

1. [Check new Helm directory names here](https://github.com/camunda/camunda-platform-helm/tree/main/charts)
2. Update `helm-git-refs.json`. [Example PR](https://github.com/camunda/connectors/pull/3468/files) - remember to backport to the old minor release branch!
3. [Update DMN in release process](https://modeler.ultrawombat.com/diagrams/32231b91-9382-45be-b2a7-eca1c1f45065--variables-from-release-version)

## Before Next Alpha Code Freeze

1. Run the `connectors` [Create Release Branch workflow](https://github.com/camunda/connectors/actions/workflows/CREATE_RELEASE_BRANCH.yml)
2. Open the PR created by the workflow. Follow the instructions, approve, then merge the PR.

## Bump docs link to new minor version

1. Merge the PR the RELEASE workflow creates for the first new minor release, with the version bump of the documentation links.
