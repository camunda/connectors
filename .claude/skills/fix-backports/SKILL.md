---
name: fix-backports
description: Resolve a backport PR whose cherry-pick conflicted, or sweep every open backport PR in the repo and fix the ones that are broken. Use when asked to fix a backport, given a link to a "Backport failed" comment, or asked to fix/clear all open backports.
---

# Fix conflicted backports

`korthout/backport-action` cherry-picks merged PRs onto release branches. When it
conflicts it either comments "Backport failed for `<branch>`" on the **source** PR, or
(newer config) commits the conflicted state and opens a **draft** PR. Either way, turn it
into the change a developer would have written by hand — for one PR you're given, or for
every broken one in the repo if asked to sweep.

Work it out yourself — you know git. What follows is only what you cannot infer from the
repo.

## Sweeping all of them

`gh pr list --repo camunda/connectors --state open --limit 500 --json number,headRefName,author,isDraft`
— `--limit` is required; `gh pr list` silently returns only its first 30 results without
it, which would make a sweep miss real candidates with no error. Keep only PRs that are
**still draft**, on a `backport-<n>-to-<target>` branch, authored by the bot (see the
login note below), with actual evidence of a conflict — the resolve-instructions comment
(see "Which commits to replay"), or committed conflict markers on the candidate's own
HEAD. A bot-authored PR with neither is a clean backport; leave it alone.

Once a developer has marked a candidate ready, it is out of scope for an unattended
sweep — draft status is this workflow's review boundary, and a sweep must not add a
commit to something someone already decided was finished. A PR you were pointed at by
name is a separate path and isn't restricted this way.

Resolve each candidate in its own worktree, so one bad candidate can't corrupt another's
checkout, and treat every candidate independently — one failing to build or resolve must
not stop the rest. Do not stop for approval between candidates or before pushing one;
"fix all of them" means every candidate that builds clean gets pushed as you go, not
staged for review. End with one summary table of every PR you touched and what happened
to it. Skip anything a human has already started fixing — see "Which PRs are safe to
touch" below; that check is not optional on a sweep.

## Which commits to replay

The resolve-instructions comment names them — **read it on the draft PR itself**, not
the source PR. Verified against `backport-action`'s source: under this repo's
`draft_commit_conflicts` config, that comment is posted by
`commentResolveConflictsOnDraftPr` to the newly created draft PR's own number, not the
source PR. The README's prose says "the original pull request," which is stale; trust
the draft PR.

The source PR still gets its own comments — one per target branch, for successes as well
as failures ("Backport failed for `stable/8.6`", "Successfully created backport PR for
`stable/8.10`", and so on) — but under this config those are status reports, not where
the SHAs to replay live. If a draft PR somehow has nothing, check the source PR as a
fallback before giving up; don't stop at the first empty result.

If neither has them, stop and say so. Never guess a SHA.

## Never rewrite history — only add commits

Do not `reset`, `rebase`, or force-push. The bot's commit — conflict markers baked into
the file content and all — stays exactly where it is. You only ever add a new commit on
top of it; nothing already on the branch is replaced.

That committed conflict is the *first* source commit that failed to cherry-pick cleanly;
everything below it on the branch is already a correctly-applied earlier commit — leave
those alone too. Look at the source commit's actual diff (`git show <sha>`, from "Which
commits to replay" below) to see what it was trying to do, edit the marked-up files to
that same result adapted for this branch, then `git add` and commit normally. That commit
finishes what the bot's commit started; it does not amend or replace it.

If the source PR has commits after the one that conflicted, cherry-pick them on top now,
in order, each as its own `-x` commit. One of those conflicting too is resolved the same
way — edit, commit, move on — never by touching an earlier commit.

A plain `git push` is enough once you're done. Every commit you add only extends the
branch, so there is nothing to force and nothing that needs a lease.

## Which PRs are safe to touch

Three things must all hold: the bot authored the PR, there is actual evidence of a
conflict — a "Backport failed" comment naming this target, or committed conflict markers —
and no human has pushed a commit to it since. Authorship alone also matches clean
backports that need no help, and a PR someone is already fixing by hand is the one case
this skill must never touch.

Check every commit, not just the PR's author: `gh api --paginate repos/camunda/connectors/pulls/<n>/commits --jq '.[].committer.login'`.
Without `--paginate` this silently checks only the first page (30 commits), so a human
commit past that point would be missed. If any entry isn't the bot, a human already pushed
to this branch — skip the PR entirely.
Do not add a commit on top of someone's in-progress work.

The bot's login differs by API: `gh pr view --json author` reports
`app/team-connectors-int-automation`; the REST API — including the commits endpoint above
— reports `team-connectors-int-automation[bot]`. Accept either; hardcoding one makes every
real candidate look human-touched and skips them all.

Humans create branches matching the bot's exact naming: PRs #8845–#8849 are `johnBgood`'s,
on `backport-8812-to-stable/8.6` through `8.9`. **The branch name proves nothing** —
rewriting a colleague's branch because it looked bot-shaped is the worst thing this skill
can do.

## Building

`./mvnw -pl <modules> -am install -DskipTests -DskipChecks` on the modules the change
touches.

- `stable/8.6` has **no `mvnw`** — use the system `mvn` there. 8.7 and later have it.
- Do not add `-Dquickly`. Other CI jobs pass it, but it is undefined in `pom.xml` and
  `parent/pom.xml` and may silently do nothing.
- A changed root `pom.xml` affects every module — do not treat it as nothing to compile.

Compiling is enough; CI on the PR runs the tests.

## Repo rules

- **Element templates are generated.** Never hand-edit one — rerun the owning connector's
  `GenerateElementTemplate` test.
- **Never change the PR title.** `PULL_REQUEST_NAME_CHECK_ON_PR.yml` and
  `ENFORCE_QA_APPROVAL.yml` key off it.

## Finishing

Push and mark ready as soon as a candidate's build is clean — one PR or a whole sweep,
without waiting for anyone's go-ahead. A clean build only proves the change *compiles*;
it is not a review, and CI on the pushed PR is what actually runs the tests. Never let a
clean build read as "verified correct" in what you report.

Check the push itself succeeded before marking a draft ready — a rejected push followed
by `gh pr ready` would publish the conflict markers as finished work. Because you only
ever added commits, a plain push is a normal fast-forward; if it isn't, stop — something
changed on the branch since you started (see "Which PRs are safe to touch"), and pushing
anyway is exactly the case that check exists to prevent.

Put the reasoning in the commit(s) you add, not a PR comment: keep `cherry-pick -x`'s
trailer where it applies and add a line of substance ("`stable/8.9` still has the old
`rootMessage(Throwable)` overload, so the added call site takes the cause directly").

Giving up on a candidate is a legitimate outcome — say concretely what defeated you and
leave that PR as a draft, untouched beyond your attempt. Guessing at a port you cannot
verify is worse, and on a sweep it is worse for everyone reading the PR afterward, not
just you. On a sweep, end with one summary table: PR, target, what conflicted, what you
decided, and whether it was pushed or left as a draft and why.
