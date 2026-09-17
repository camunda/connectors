---
name: fix-backports
description: Resolve a backport PR whose cherry-pick conflicted, or sweep every open backport PR in the repo and fix the ones that are broken. Use when asked to fix a backport, given a link to a "Backport failed" comment, or asked to fix/clear all open backports.
---

# Fix conflicted backports

`korthout/backport-action` cherry-picks merged PRs onto release branches. Under this
repo's `draft_commit_conflicts` config, a conflict becomes a draft PR on a
`backport-<n>-to-<target>` branch with the conflicted state committed.

## Sweeping all of them

`gh pr list --repo camunda/connectors --state open --limit 500 --json number,headRefName,author,isDraft`
— `--limit` is required, or `gh pr list` silently returns only its first 30 results,
missing real candidates with no error.

Keep only PRs that are still draft, on a `backport-<n>-to-<target>` branch, opened by the
backport bot. For each one, in its own worktree (so one bad candidate can't corrupt
another's checkout): resolve the merge conflicts, compile the change, then push and mark
the PR ready once it builds clean.

Treat every candidate independently — one failing to resolve or build must not stop the
rest, and don't wait for approval between candidates or before pushing one. Skip a PR a
human has already pushed a commit to; that's someone's in-progress work, not a broken
backport. End with one summary table: PR, what you did, and whether it was pushed or left
as a draft and why.

## Repo rules

- **Element templates are generated.** Never hand-edit one — regenerate it instead.
- **Never change the PR title.** `PULL_REQUEST_NAME_CHECK_ON_PR.yml` and
  `ENFORCE_QA_APPROVAL.yml` key off it.
