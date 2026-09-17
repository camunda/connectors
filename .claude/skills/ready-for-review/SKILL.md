---
name: ready-for-review
description: Use when a branch is functionally done and needs final polish before requesting review — comments added during the branch need stripping, the commit history needs squashing to one commit, and the PR needs a short description.
---

# Ready for Review

## Overview

Final pass before requesting review: strip comments added on this branch, squash the branch to a single commit, and write a tight PR description. Rewrites history — run only once the diff is stable, not mid-development.

## Steps

### 1. Remove comments added on this branch

Resolve the actual target branch once, and reuse it (as `$BASE`) for every diff, log, hash, and reset command below — don't hard-code `origin/main`, since a branch may target `stable/*` or another base:

```
BASE_REF=$(gh pr view --json baseRefName -q .baseRefName 2>/dev/null)
```

If that command fails (no PR yet, not authenticated, network error), do not guess `main` — ask the user which branch this targets, and use their answer as `BASE_REF`. Once resolved:

```
BASE="origin/$BASE_REF"
git merge-base "$BASE" HEAD
git diff --name-only $(git merge-base "$BASE" HEAD)...HEAD
```

Exclude non-code files (`.json`, `.md`, `.yml`, generated element templates) unless they contain relevant source comments.

For each changed file, diff it against the merge base and find lines that are both newly **added** (`+`, not context/removed) and comments in that file's language (`//`, `/* */`, `#`, etc.).

Do NOT remove:
- License/copyright headers at the top of a file
- Any comment that predates this branch

Remove qualifying comments (and, if a comment was the only content of a block, remove the whole now-empty block cleanly). Don't touch code logic or surrounding formatting. Print a summary table of what was removed (File | Line(s) | Comment). If nothing qualifies, say so and change nothing.

### 2. Squash all commits into one

Show the user the current commit log first and **stop for confirmation** before rewriting anything — this is a hard-to-reverse operation:

```
git log "$BASE"...HEAD --oneline
```

After confirmation, snapshot the diff for a safety check, then squash:

```
TREE_BEFORE=$(git diff "$BASE"...HEAD | sha256sum)
git reset --soft $(git merge-base "$BASE" HEAD)
git commit -m "<message>"
TREE_AFTER=$(git diff "$BASE"...HEAD | sha256sum)
```

Infer `<message>` from the overall diff/branch name if the user hasn't given one; ask only if genuinely ambiguous. If `$TREE_BEFORE` != `$TREE_AFTER`, stop and report the mismatch — do not proceed — and point to `git reflog` for recovery.

Never force-push as part of this step. Report that a force-push (`git push --force-with-lease`) is needed to update the remote branch, and only run it if the user explicitly says to.

### 3. Write the PR description

Hard cap: **200 words. Target ~100.** Summarize what changed and why — no restating the diff file-by-file, no narrating the work session. A short bullet list is fine if it stays within the cap.

If a PR already exists for this branch (`gh pr view --json number,body`), update it in place. Pass the description through a quoted heredoc and `--body-file` rather than a shell argument, so quotes, backticks, and `$()` in the text can't be interpreted by the shell:
```
gh pr edit <number> --body-file - <<'EOF'
<description>
EOF
```
Otherwise print the draft description for the user to use when opening the PR.
