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

The bot's comment names them. **A source PR gets one comment per target branch**, for
successes as well as failures — PR #8812 carries five: "Backport failed for" `stable/8.6`,
`8.7`, `8.8` and `8.9`, plus a "Successfully created backport PR for `stable/8.10`". Match
on the branch named in the comment, never on position, or you will replay another target's
commits or land on a success comment that names none.

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

Two things must both hold: the bot authored it, **and** there is actual evidence of a
conflict — a "Backport failed" comment naming this target, or committed conflict markers.
Authorship alone also matches clean backports that need no help.

The bot's login differs by API: `gh pr view --json author` reports
`app/team-connectors-int-automation`, the REST API reports
`team-connectors-int-automation[bot]`. Accept either; don't hardcode one and silently
match nothing.

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

Show the developer the diff and what you decided, and stop. Push only once they say so,
and check the push succeeded before marking a draft ready — a rejected push followed by
`gh pr ready` would publish the conflict markers as finished work.

Put the reasoning in the commit message, not a PR comment: keep `cherry-pick -x`'s trailer
and add a line of substance ("`stable/8.9` still has the old `rootMessage(Throwable)`
overload, so the added call site takes the cause directly").

Giving up is a legitimate outcome — say concretely what defeated you. Guessing at a port
you cannot verify is worse.
