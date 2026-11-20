#!/usr/bin/env bash
# shellcheck source=testlib.sh
source "$(dirname "$0")/testlib.sh"
# shellcheck source=../lib.sh
source "$SCRIPT_DIR/lib.sh"

assert_eq 123 "$(pr_number 123)" "bare number"
assert_eq 123 "$(pr_number '#123')" "hash number"
assert_eq 123 "$(pr_number https://github.com/camunda/connectors/pull/123)" "connectors URL"

(pr_number https://github.com/camunda/camunda/pull/1 2>/dev/null)
assert_eq 2 "$?" "other repo URL exits 2"
(pr_number abc 2>/dev/null)
assert_eq 2 "$?" "garbage exits 2"

finish
