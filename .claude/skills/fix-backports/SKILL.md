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

Find open draft PRs whose branch matches the pattern `backport-action` generates.

```bash
gh pr list --repo camunda/connectors --state open --limit 100 \
  --json number,headRefName,baseRefName,isDraft,author \
  --jq '.[] | select(.isDraft) | select(.headRefName | test("^backport-[0-9]+-to-")) | select(.author.login == "app/team-connectors-int-automation") | "\(.number)\t\(.baseRefName)\t\(.headRefName)"'
```

The branch pattern is the primary signal. The author check corroborates it — that App
authors other automation here, so it does not stand alone.

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
if git grep -qE '^(<{7}|>{7}|={7}$)' FETCH_HEAD; then echo "conflicted"; fi
```

Skip hand-made backport branches — anything not matching `backport-<n>-to-<target>`,
such as `backport/8.9-ssl-error-code-and-root-cause-fallback`. Those are a colleague's
manual work and you must not propose rewriting them.

If nothing matches, say so and stop. That is the normal result.

## Phase 2 — resolve, one worktree per candidate

Never switch branches in the developer's checkout. Each candidate gets its own worktree,
so their working tree is untouched and a failed attempt is thrown away with `rm`.

```bash
git worktree add ".claude/worktrees/backport-${PR}" "${HEAD_BRANCH}"
cd ".claude/worktrees/backport-${PR}"
```

Read which commits to replay from `backport-action`'s own comment. As of writing it is
not confirmed whether that comment lands on the draft PR or on the source PR
(`${SOURCE_PR}`, bound in Phase 1) — check both, draft first, and use whichever yields
it:

```bash
gh pr view "${PR}" --repo camunda/connectors --json comments \
  --jq '.comments[] | select(.author.login == "app/team-connectors-int-automation") | .body'
gh pr view "${SOURCE_PR}" --repo camunda/connectors --json comments \
  --jq '.comments[] | select(.author.login == "app/team-connectors-int-automation") | .body'
```

Whichever comment is non-empty names the SHAs in a `git cherry-pick -x` block. Merge
commits are disabled in this repo, so a squash-merged source PR gives one SHA; a
rebase-merged one gives several, in order, and **all** must be replayed. If neither PR
carries the comment, that is a legitimate give-up per Phase 6, not something to guess
around — never fabricate SHAs when the lookup comes up empty.

Then:

```bash
original_sha=$(git rev-parse HEAD)   # the conflict-marker commit — your fallback
git reset --hard HEAD~1              # drop ONLY the conflict-marker commit
git cherry-pick -x <sha> [<sha>...]  # replay, resolving conflicts as they arise
```

**`HEAD~1` is load-bearing.** `backport-action` stops at the *first* conflict, so every
commit below the marker commit is already a cleanly-applied cherry-pick from the source
PR. Resetting to `origin/<target>` instead silently drops them and produces an
incomplete backport that looks correct. Never do it.

**Never commit a fix on top of the marker commit.** Conflict markers in `stable/8.x`
history are permanent once merged.

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

```bash
modules=$(git diff --name-only "origin/${BASE}...HEAD" \
  | while read -r f; do
      d=$(dirname "$f")
      while [ "$d" != "." ] && [ ! -f "$d/pom.xml" ]; do d=$(dirname "$d"); done
      [ "$d" != "." ] && echo "$d"
    done | sort -u | paste -sd,)

if [ -z "$modules" ]; then
  echo "No changed file has an owning Maven module below the repo root — nothing to compile. Say so in the table."
else
  ./mvnw -B -pl "${modules}" -am install -DskipTests -DskipChecks
fi
```

Use `./mvnw`, never `mvn`.

**Do not add `-Dquickly`** — it is undefined in this repo's `pom.xml` and
`parent/pom.xml`; it comes from an external parent and may silently do nothing here.
`-DskipTests -DskipChecks` above is the correct invocation.

This catches the dominant backport failure: a method or signature that does not exist on
the older branch. It does not catch behavioural breakage — CI on the pushed PR does.

If the gate cannot be made to pass, restore the branch and treat this as Phase 6:

```bash
git reset --hard "${original_sha}"
```

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

Push nothing before the developer approves. They may approve per-PR or in a batch.

## Phase 5 — apply, only what was approved

```bash
git push --force-with-lease="refs/heads/${HEAD_BRANCH}:${original_sha}" \
  origin "HEAD:refs/heads/${HEAD_BRANCH}"
gh pr ready "${PR}" --repo camunda/connectors
```

The force is intentional, not incidental: local `HEAD` dropped the conflict-marker commit,
so it is no longer a descendant of the branch's current tip, and that marker commit must
never survive into `stable/8.x` history. The lease is not optional — pinning the expected
remote tip to `${original_sha}` (bound back in Phase 2) is what tells a legitimate rewrite
apart from clobbering a colleague's intervening push; if someone pushed to this branch
while you were resolving, the lease refuses and nothing is overwritten. If it refuses,
stop and re-fetch — never fall back to a bare `--force`.

No PR comment on success.

## Phase 6 — give up honestly

Giving up is a legitimate outcome. Guessing at a port you cannot verify is worse.

Leave the draft exactly as `backport-action` made it — an honest record of the conflict —
and comment with a concrete blocker so whoever picks it up starts ahead of you:

```bash
git reset --hard "${original_sha}"
gh pr comment "${PR}" --repo camunda/connectors --body "..."
```

Say what defeated you specifically — which hunk, which missing API, what you could not
determine — not "could not resolve automatically".

## Phase 7 — clean up

Remove every worktree, whether or not its resolution was applied:

```bash
cd /home/ztefanie/Documents/connectors
git worktree remove --force ".claude/worktrees/backport-${PR}"
git worktree prune
```
