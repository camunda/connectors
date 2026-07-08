#!/bin/bash
# Ensures every newly added versioned element template is an exact copy of its
# non-versioned counterpart on main. Prevents version-bump PRs from silently
# snapshotting a stale state when changes to the non-versioned file were already
# merged to main without a corresponding version bump.
#
# Known edge case: in a merge queue that batches several PRs, the content is
# always compared against the current tip of origin/main. If one queued PR
# modifies foo.json while another (behind it) adds versioned/foo-1.json based on
# that modified content, this check compares against the pre-queue foo.json and
# may report a spurious mismatch. This is accepted as rare; re-running against an
# updated main resolves it.
#
# Requires full git history (fetch-depth: 0) so the merge-base with origin/main
# can be resolved for the three-dot diff below.
set -e

BASE_REF="origin/main"
ERRORS=0
CHECKED=0

# Use a three-dot diff (merge-base..HEAD) so only files this branch actually
# added are reported. A two-dot diff would classify a file that already exists at
# the same path on main (with different content) as Modified and skip it.
#
# git diff is checked explicitly so a real failure (e.g. an unresolvable base
# ref) aborts the script instead of being masked. Only the grep no-match case is
# tolerated with `|| true`, so the check can never be silently skipped by a git
# error.
if ! ADDED_FILES=$(git diff --name-only --diff-filter=A "${BASE_REF}...HEAD"); then
  echo "ERROR: failed to diff against ${BASE_REF}. Ensure it is fetched (fetch-depth: 0)." >&2
  exit 1
fi
VERSIONED_FILES=$(printf '%s\n' "$ADDED_FILES" | grep '/versioned/.*\.json$' || true)

if [ -z "$VERSIONED_FILES" ]; then
  echo "No new versioned element templates found — check skipped."
  exit 0
fi

while IFS= read -r versioned_file; do
  [ -z "$versioned_file" ] && continue

  CHECKED=$((CHECKED + 1))

  file_stem=$(basename "$versioned_file" .json)
  base_name=$(echo "$file_stem" | sed 's/-[0-9]*$//')

  versioned_dir=$(dirname "$versioned_file")
  parent_dir=$(dirname "$versioned_dir")
  non_versioned="${parent_dir}/${base_name}.json"

  if ! git show "${BASE_REF}:${non_versioned}" > /dev/null 2>&1; then
    echo "ℹ️  Skipping ${versioned_file}: no counterpart ${non_versioned} found on main"
    CHECKED=$((CHECKED - 1))
    continue
  fi

  # Byte-for-byte comparison via cmp on the raw git blob stream. Command
  # substitution would strip trailing newlines and buffer the whole file, so it
  # cannot guarantee an exact match.
  if git show "${BASE_REF}:${non_versioned}" | cmp -s - "$versioned_file"; then
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
