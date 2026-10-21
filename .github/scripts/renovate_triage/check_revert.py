#!/usr/bin/env python3
"""Check that a triage patch only reverts lines the Renovate PR changed.

Usage: check_revert.py <renovate-pr.diff> <fix.patch>

Per file, every line the fix removes must be a line the PR added, and every line
the fix adds must be a line the PR removed. The fix can therefore only swap
Renovate's new lines back to the old ones; it cannot introduce anything that was
not already on the base branch. Exits 1 with a reason on stdout otherwise.
"""
import sys
from collections import Counter


def parse(path):
    """Return {file: (added Counter, removed Counter)} for a unified diff."""
    files = {}
    current = None
    in_hunk = False
    with open(path, encoding="utf-8", errors="surrogateescape") as diff:
        for line in diff.read().splitlines():
            if line.startswith("diff --git "):
                current, in_hunk = None, False
            elif not in_hunk and line.startswith("+++ "):
                target = line[4:]
                current = target[2:] if target.startswith("b/") else target
                files.setdefault(current, (Counter(), Counter()))
            elif line.startswith("@@"):
                in_hunk = current is not None
            elif in_hunk and line.startswith("+"):
                files[current][0][line[1:]] += 1
            elif in_hunk and line.startswith("-"):
                files[current][1][line[1:]] += 1
    return files


def main(pr_diff, fix_patch):
    pr = parse(pr_diff)
    fix = parse(fix_patch)
    if not fix:
        print("The fix is empty.")
        return 1
    for name, (fix_added, fix_removed) in fix.items():
        if name not in pr:
            print(f"The fix touches `{name}`, which this PR did not change.")
            return 1
        pr_added, pr_removed = pr[name]
        if fix_removed - pr_added:
            print(f"The fix removes lines in `{name}` that this PR did not add.")
            return 1
        if fix_added - pr_removed:
            print(f"The fix adds lines to `{name}` that this PR did not remove.")
            return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:3]))
