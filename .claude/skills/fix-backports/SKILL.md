---
name: fix-backports
description: Resolve a backport PR whose cherry-pick conflicted. Use when asked to fix a backport, or given a link to a "Backport failed" comment.
---

# Fix a conflicted backport

`korthout/backport-action` cherry-picks merged PRs onto release branches. When it
conflicts it either comments "Backport failed for `<branch>`" on the **source** PR, or
(newer config) commits the conflicted state and opens a **draft** PR. Either way, turn it
into the change a developer would have written by hand.

Work it out yourself — you know git. What follows is only what you cannot infer from the
repo.

## Which commits to replay

The bot's comment names them. **A source PR gets one comment per target branch** — PR
#8812 carries five, one each for `stable/8.6` through `8.9`. Read the comment for *your*
target branch, not the first one that matches, or you will replay the wrong set.

If no comment names them, stop and say so. Never guess a SHA.

## The reset rule

If the branch already has a conflict-marker commit, drop **only that commit** — never
reset to the target branch.

`backport-action` stops at the *first* conflict, so commits below the marker are already
cleanly applied cherry-picks from the source PR. Resetting to the target silently discards
them and produces an incomplete backport that looks correct. Check what is actually on top
before resetting; a developer may have pushed a follow-up, so the marker commit is not
always `HEAD`.

Never resolve by committing a fix *on top of* the marker commit — markers in `stable/8.x`
history are permanent once merged.

## Which PRs are safe to touch

Only ones authored by `app/team-connectors-int-automation`.

Humans create branches matching the bot's exact naming: PRs #8845–#8849 are `johnBgood`'s,
on `backport-8812-to-stable/8.6` through `8.9`. **The author is the only discriminator** —
the branch name is not. Rewriting a colleague's branch because it looked bot-shaped is the
worst thing this skill can do.

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

Show the developer the diff and what you decided, and stop. Push only once they say so,
and check the push succeeded before marking a draft ready — a rejected push followed by
`gh pr ready` would publish the conflict markers as finished work.

Put the reasoning in the commit message, not a PR comment: keep `cherry-pick -x`'s trailer
and add a line of substance ("`stable/8.9` still has the old `rootMessage(Throwable)`
overload, so the added call site takes the cause directly").

Giving up is a legitimate outcome — say concretely what defeated you. Guessing at a port
you cannot verify is worse.
