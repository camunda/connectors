#!/bin/bash

# Fails if any non-test Java source links to docs.camunda.io without a pinned <major>.<minor>
# version (unversioned or /next/). The release bumper only rewrites links that already carry a
# version, so an unversioned link is never corrected once introduced. Element templates are
# checked by the element template validator (docs-link-version rule), except agentic-ai ones,
# which the validator skips by default; they stay here until #9835 removes that skip.

cd "$(git rev-parse --show-toplevel)" || exit 1

UNVERSIONED_LINK='https://docs\.camunda\.io/docs/(?![0-9]+\.[0-9]+/)'

violations=$(git grep -nP "$UNVERSIONED_LINK" -- \
  ':(glob)**/*.java' \
  ':(glob)connectors/agentic-ai/**/element-templates/**/*.json' \
  ':(exclude,glob)**/element-templates/versioned/**' \
  ':(exclude,glob)**/src/test/**')
status=$?

# git grep exits 1 when nothing matches; anything above that is a real error
if [ "$status" -gt 1 ]; then
  echo "❌ git grep failed with exit code $status"
  exit "$status"
fi

if [ -n "$violations" ]; then
  echo "❌ Unversioned or /next/ documentation links found. Pin the current released minor,"
  echo "   e.g. https://docs.camunda.io/docs/<major>.<minor>/components/..."
  echo
  echo "$violations"
  exit 1
fi

echo "✅ All documentation links are version-pinned."
