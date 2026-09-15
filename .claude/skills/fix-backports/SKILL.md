---
name: fix-backports
description: Find open backport PRs that backport-action left as drafts with committed conflict markers, resolve each in its own worktree, verify it compiles, and push only what the developer approves. Use when the user asks to fix, sweep, or clear conflicted backports.
---

# Fix conflicted backports

`korthout/backport-action` cherry-picks merged PRs onto release branches. When a
cherry-pick conflicts it commits the conflicted state — markers and all — and opens a
**draft** PR. This skill turns those drafts into the change a developer would have
produced resolving the conflict by hand.

You propose; you do not decide. Nothing reaches GitHub until the developer approves it.

## Phase 1 — discover

Find open draft PRs that `backport-action` itself opened.

```bash
gh pr list --repo camunda/connectors --state open --limit 100 \
  --json number,headRefName,baseRefName,isDraft,author \
  --jq '.[] | select(.isDraft) | select(.headRefName | test("^backport-[0-9]+-to-")) | select(.author.login == "app/team-connectors-int-automation") | "\(.number)\t\(.baseRefName)\t\(.headRefName)"'
```

**The author check is the load-bearing filter. Never relax it, never drop it, and never
substitute the branch pattern for it.** Humans in this repo do create branches that match
the bot pattern exactly: PRs #8845, #8847, #8848 and #8849 are hand-made backports
authored by `johnBgood` on `backport-8812-to-stable/8.6`, `-to-stable/8.7`,
`-to-stable/8.8` and `-to-stable/8.9`. Those are indistinguishable from a bot draft by
branch name alone. `author.login == "app/team-connectors-int-automation"` is the only
thing standing between this skill and force-pushing a colleague's branch.

The branch-name test is the secondary, cheap filter: it trims obviously-unrelated drafts
before the author check decides. It also excludes differently-shaped manual backport
branches such as `backport/8.9-ssl-error-code-and-root-cause-fallback` — but treat that
as a convenience, not as protection.

Each row gives the three values every later phase uses. Bind them per candidate:

```bash
PR=<number>            # column 1
BASE=<baseRefName>     # column 2 — the release branch being backported to
HEAD_BRANCH=<headRefName>  # column 3 — backport-<n>-to-<target>
SOURCE_PR=$(echo "${HEAD_BRANCH}" | sed -E 's/^backport-([0-9]+)-to-.*/\1/')
[[ "$SOURCE_PR" =~ ^[0-9]+$ ]] || { echo "cannot derive source PR from ${HEAD_BRANCH} — report this candidate as unprocessable, do not proceed with an empty value"; }
```

Then confirm each candidate genuinely has conflict markers, rather than being a draft
for some other reason:

```bash
git fetch --quiet origin "${HEAD_BRANCH}"
if git grep -qE '^(<{7}|>{7}|={7}$)' "origin/${HEAD_BRANCH}"; then echo "conflicted"; fi
```

Grep the named remote-tracking ref, not `FETCH_HEAD`: `FETCH_HEAD` is repo-global and the
next candidate's fetch overwrites it, so a sweep of several PRs would grep the wrong tree.
`git fetch origin <branch>` updates `origin/<branch>`, which is stable per candidate.

If nothing matches, say so and stop. That is the normal result.

## Phase 2 — resolve, one worktree per candidate

Never switch branches in the developer's checkout. Each candidate gets its own worktree,
so their working tree is untouched and a failed attempt is thrown away with `rm`.

Run this from the developer's checkout root:

```bash
REPO_ROOT=$(git rev-parse --show-toplevel)
state_file="$(git rev-parse --path-format=absolute --git-common-dir)/fix-backports-state-${PR}"
git worktree add ".claude/worktrees/backport-${PR}" "${HEAD_BRANCH}"
cd ".claude/worktrees/backport-${PR}"
```

Read which commits to replay from `backport-action`'s own comment. Its documentation
states that "instructions are provided on the original pull request on how to resolve the
conflict and continue the cherry-pick" — so the comment is on the **source PR**
(`${SOURCE_PR}`, bound in Phase 1). Check that first; keep the draft backport PR as a
fallback in case the action's behaviour differs here:

```bash
gh pr view "${SOURCE_PR}" --repo camunda/connectors --json comments \
  --jq '.comments[] | select(.author.login == "app/team-connectors-int-automation") | .body'
gh pr view "${PR}" --repo camunda/connectors --json comments \
  --jq '.comments[] | select(.author.login == "app/team-connectors-int-automation") | .body'
```

Whichever comment is non-empty names the SHAs in a `git cherry-pick -x` block. Merge
commits are disabled in this repo, so a squash-merged source PR gives one SHA; a
rebase-merged one gives several, in order, and **all** must be replayed. If neither PR
carries the comment, that is a legitimate give-up per Phase 6, not something to guess
around — never fabricate SHAs when the lookup comes up empty.

Then:

```bash
git fetch --quiet origin main         # the SHAs to replay live on main, not on this branch
original_sha=$(git rev-parse HEAD)    # the conflict-marker commit — your fallback
git reset --hard HEAD~1               # drop ONLY the conflict-marker commit
git cherry-pick -x <sha> [<sha>...]   # replay, resolving conflicts as they arise
```

Fetch `main` first. The source PR was merged there, so a stale checkout fails the
cherry-pick with `bad revision` even though the SHAs are correct.

**`HEAD~1` is load-bearing.** `backport-action` stops at the *first* conflict, so every
commit below the marker commit is already a cleanly-applied cherry-pick from the source
PR. Resetting to `origin/<target>` instead silently drops them and produces an
incomplete backport that looks correct. Never do it.

**Never commit a fix on top of the marker commit.** Conflict markers in `stable/8.x`
history are permanent once merged.

### Persist the bindings — Phase 4 stops, and the stop ends your shell

Phase 4 mandates stopping for developer approval. That approval arrives later, in a new
shell, so every variable bound above is gone by the time Phases 5-7 run. Both degenerate
cases are silent-to-dangerous: an empty `original_sha` turns the push lease into
`--force-with-lease="refs/heads/b:"`, which is rejected as `stale info` so the approved
push never lands; and an unquoted `git reset --hard $original_sha` degenerates into a
bare `git reset --hard`, which restores nothing and leaves the branch sitting at the
post-`HEAD~1` state while Phase 6 reports that you gave up.

So write the bindings to a file the moment they exist:

```bash
cat >"${state_file}" <<EOF
PR=${PR}
BASE=${BASE}
HEAD_BRANCH=${HEAD_BRANCH}
SOURCE_PR=${SOURCE_PR}
REPO_ROOT=${REPO_ROOT}
WORKTREE=${REPO_ROOT}/.claude/worktrees/backport-${PR}
original_sha=${original_sha}
EOF
```

The path resolves identically from the developer's checkout and from inside any worktree,
because `--git-common-dir` always points at the main `.git`. The PR number is the one
value you still have after the stop — it is in the approval table the developer just
answered — and the file supplies the rest.

**If you are resuming after the approval stop, re-establish the bindings before you touch
anything.** Every one of Phases 5, 6 and 7 begins with:

```bash
source "$(git rev-parse --path-format=absolute --git-common-dir)/fix-backports-state-${PR}"
if [ -z "${original_sha}" ] || [ -z "${HEAD_BRANCH}" ]; then
  echo "state for PR ${PR} did not load — stop and report; do not push and do not reset"
  exit 1
fi
```

Never re-derive `original_sha` by guessing, and never run a push or a reset with it unset.

### Latitude

The same a developer has locally — no restricted file list. If the target branch lacks a
method the change calls, or the code around it moved, adapt the change; that is what a
backport *is*. Three limits:

- **Element templates are generated.** Never hand-edit one — regenerate it by running
  the owning connector's `GenerateElementTemplate` test.
- **Never change the PR title.** `PULL_REQUEST_NAME_CHECK_ON_PR.yml` and
  `ENFORCE_QA_APPROVAL.yml` key off it.
- Touch no other branch or PR.

## Phase 3 — compile gate

Derive the affected Maven modules from the changed paths, then build them. Compile and
install only — no test run.

The diff is against `origin/${BASE}`, so fetch that ref first. Without the fetch a missing
or stale `origin/${BASE}` makes the diff *error*, which would otherwise look exactly like
a change that owns no module — and the candidate would reach the approval table with the
compile gate silently never run.

```bash
if ! git fetch --quiet origin "${BASE}"; then
  echo "cannot fetch origin/${BASE} — the compile gate could not run. Report this candidate as unprocessable; do not claim it compiled."
elif ! changed=$(git diff --name-only "origin/${BASE}...HEAD"); then
  echo "git diff against origin/${BASE} failed — the compile gate could not run. Report the error; this is not 'nothing to compile'."
else
  modules=$(printf '%s\n' "${changed}" \
    | while read -r f; do
        [ -n "$f" ] || continue
        d=$(dirname "$f")
        while [ "$d" != "." ] && [ ! -f "$d/pom.xml" ]; do d=$(dirname "$d"); done
        [ "$d" != "." ] && echo "$d"
      done | sort -u | paste -sd,)

  if [ -z "$modules" ]; then
    echo "The diff succeeded and no changed file has an owning Maven module below the repo root — genuinely nothing to compile. Say so in the table."
  else
    ./mvnw -B -pl "${modules}" -am install -DskipTests -DskipChecks
  fi
fi
```

Report a failed fetch or a failed diff as a blocker you are handing back, not as a result
you resolved: the gate did not run, so nothing is known about whether the port compiles.

Use `./mvnw`, never `mvn`.

**Do not add `-Dquickly`** — it is undefined in this repo's `pom.xml` and
`parent/pom.xml`; it comes from an external parent and may silently do nothing here.
`-DskipTests -DskipChecks` above is the correct invocation.

This catches the dominant backport failure: a method or signature that does not exist on
the older branch. It does not catch behavioural breakage — CI on the pushed PR does.

If the gate cannot be made to pass, restore the branch and carry the candidate into the
table as a give-up:

```bash
git reset --hard "${original_sha}"
```

Restore the branch here, but **post nothing yet**. The give-up comment is public and
belongs to Phase 6, after the developer has seen the table.

## Phase 4 — write the commit message, then stop

The developer chose not to have a PR comment on success, so the commit message is the
only record of your judgment. Keep the `cherry-pick -x` trailer that identifies the
source commit and add a short note of real substance.

Not "resolved a conflict". Something like:

> `stable/8.9` still has the old `rootMessage(Throwable)` overload, so the added call
> site takes the cause directly.

Then **stop**. Report all candidates in one table and wait:

| PR | Target | What conflicted | Decision | Gate | Proposed |
|----|--------|-----------------|----------|------|----------|

Candidates you could not resolve appear here too, with `Decision` = give up and the
concrete blocker in `What conflicted`. The developer sees every failure in this table
*before* anything is written to GitHub.

Push nothing and comment nothing before the developer approves. They may approve per-PR
or in a batch.

## Phase 5 — apply, only what was approved

Re-source the state first (see Phase 2) — this shell is not the one that resolved the
conflict:

```bash
source "$(git rev-parse --path-format=absolute --git-common-dir)/fix-backports-state-${PR}"
if [ -z "${original_sha}" ] || [ -z "${HEAD_BRANCH}" ]; then
  echo "state for PR ${PR} did not load — stop and report; do not push"
  exit 1
fi
cd "${WORKTREE}" || exit 1
git push --force-with-lease="refs/heads/${HEAD_BRANCH}:${original_sha}" \
  origin "HEAD:refs/heads/${HEAD_BRANCH}"
gh pr ready "${PR}" --repo camunda/connectors
```

The force is intentional, not incidental: local `HEAD` dropped the conflict-marker commit,
so it is no longer a descendant of the branch's current tip, and that marker commit must
never survive into `stable/8.x` history. The lease is not optional — pinning the expected
remote tip to `${original_sha}` (persisted in Phase 2) is what tells a legitimate rewrite
apart from clobbering a colleague's intervening push; if someone pushed to this branch
while you were resolving, the lease refuses and nothing is overwritten. If it refuses,
stop and re-fetch — never fall back to a bare `--force`.

The `cd` above must halt on failure rather than fall through: if the worktree was removed
or moved between approval and this apply step, a failed `cd` would leave the push running
against the repo root, pushing the developer's *current* HEAD to `${HEAD_BRANCH}` — and the
lease would not catch it, because the remote tip still matches `${original_sha}`. Do not
remove this guard.

No PR comment on success.

## Phase 6 — give up honestly

Giving up is a legitimate outcome. Guessing at a port you cannot verify is worse.

This phase runs **after** the Phase 4 table, alongside Phase 5, as part of the approved
outcome. The comment is public and permanent, so a five-candidate sweep must not scatter
five give-up comments across GitHub before the developer has seen a single row.

Leave the draft exactly as `backport-action` made it — an honest record of the conflict —
and comment with a concrete blocker so whoever picks it up starts ahead of you:

```bash
source "$(git rev-parse --path-format=absolute --git-common-dir)/fix-backports-state-${PR}"
if [ -z "${original_sha}" ] || [ -z "${HEAD_BRANCH}" ]; then
  echo "state for PR ${PR} did not load — stop and report; do not reset"
  exit 1
fi
cd "${WORKTREE}" || exit 1
git reset --hard "${original_sha}"
gh pr comment "${PR}" --repo camunda/connectors --body "..."
```

Say what defeated you specifically — which hunk, which missing API, what you could not
determine — not "could not resolve automatically".

Guard this `cd` the same way: a failed `cd` here would run `git reset --hard` against the
main checkout instead of the worktree, discarding whatever the developer has checked out
there. Halt instead of continuing.

## Phase 7 — clean up

Remove every worktree, whether or not its resolution was applied, and delete the local
branch `git worktree add` created for it. `git worktree remove` does not delete that
branch, so without this they pile up in the developer's checkout.

Leave the worktree before removing it, and return to the repo root rather than any
hardcoded path — this skill is tracked and runs on other people's machines. Note that
inside a worktree `--show-toplevel` is the worktree itself, so use the persisted
`REPO_ROOT`:

```bash
source "$(git rev-parse --path-format=absolute --git-common-dir)/fix-backports-state-${PR}"
if [ -z "${REPO_ROOT}" ] || [ -z "${HEAD_BRANCH}" ]; then
  echo "state for PR ${PR} did not load — clean up by hand; do not guess at paths or branch names"
  exit 1
fi
cd "${REPO_ROOT}"
git worktree remove --force ".claude/worktrees/backport-${PR}"
git branch -D "${HEAD_BRANCH}"
git worktree prune
rm -f "$(git rev-parse --path-format=absolute --git-common-dir)/fix-backports-state-${PR}"
```

If the `cd` or the removal fails, say so — a left-behind worktree makes the next sweep's
`git worktree add` fail on an existing path.
