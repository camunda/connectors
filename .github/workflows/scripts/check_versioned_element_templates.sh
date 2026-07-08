#!/bin/bash
# Ensures every newly added versioned element template is an exact copy of its
# non-versioned counterpart on main. Prevents version-bump PRs from silently
# snapshotting a stale state when changes to the non-versioned file were already
# merged to main without a corresponding version bump.
set -euo pipefail

BASE_REF="origin/main"
ERRORS=0
CHECKED=0

git fetch origin main --depth=1 --quiet

VERSIONED_FILES=$(git diff --name-only --diff-filter=A "${BASE_REF}" HEAD | grep '/versioned/.*\.json$' || true)

if [ -z "$VERSIONED_FILES" ]; then
  echo "No new versioned element templates found — check skipped."
  exit 0
fi

while IFS= read -r versioned_file; do
  [ -z "$versioned_file" ] && continue

  CHECKED=$((CHECKED + 1))

  basename=$(basename "$versioned_file" .json)
  base_name=$(echo "$basename" | sed 's/-[0-9]*$//')

  versioned_dir=$(dirname "$versioned_file")
  parent_dir=$(dirname "$versioned_dir")
  non_versioned="${parent_dir}/${base_name}.json"

  if ! git show "${BASE_REF}:${non_versioned}" > /dev/null 2>&1; then
    echo "ℹ️  Skipping ${versioned_file}: no counterpart ${non_versioned} found on main"
    CHECKED=$((CHECKED - 1))
    continue
  fi

  main_content=$(git show "${BASE_REF}:${non_versioned}")
  branch_content=$(cat "$versioned_file")

  if [ "$main_content" = "$branch_content" ]; then
    echo "✅ ${versioned_file} matches ${non_versioned} on main"
  else
    echo "❌ MISMATCH: ${versioned_file} does not match ${non_versioned} on main"
    echo "   The versioned snapshot differs from the current non-versioned template on main."
    echo "   Update ${versioned_file} to exactly match ${non_versioned} on main before merging."
    ERRORS=$((ERRORS + 1))
  fi
done <<< "$VERSIONED_FILES"

if [ "$CHECKED" -eq 0 ]; then
  echo "No new versioned element templates with a counterpart on main found — check skipped."
  exit 0
fi

if [ "$ERRORS" -gt 0 ]; then
  echo ""
  echo "ERROR: $ERRORS versioned element template(s) do not match their non-versioned counterpart on main."
  exit 1
fi

echo ""
echo "All $CHECKED versioned element template(s) match their non-versioned counterpart on main."
