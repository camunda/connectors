#!/usr/bin/env bash
# Runs every test_*.sh next to this file; non-zero if any fails.
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")" || exit 1
rc=0
for t in test_*.sh; do
  bash "$t" || rc=1
done
exit $rc
