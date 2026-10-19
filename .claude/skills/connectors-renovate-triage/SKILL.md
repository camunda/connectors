---
name: connectors-renovate-triage
description: >
  Triage failing Renovate dependency-update PRs in camunda/connectors. Diagnoses why a
  Renovate PR's CI is red — checking the AlwaysGreen quality-assurance-ci triage comment
  first, then GitHub Actions logs — and tells apart a real dependency break, a flaky
  failure, a stricter linter catching a pre-existing issue, and a deliberate Maven Enforcer
  tripwire that fired on purpose. Finds the specific culprit inside grouped "all non-major
  dependencies" PRs, reverts the offending version on the Renovate branch, and updates
  renovate.json's packageRules (with a description, matching the file's convention) so the
  bad update stops recurring. Use whenever the user mentions a Renovate PR that's failing,
  red, stuck, or removed from the merge queue; says "renovate broke something" or "the deps
  PR won't merge"; hands over PR numbers to fix; asks to pin/block/exclude a package in
  renovate.json after a bad bump; or hits an enforcer/requireProperty rule tripping on a
  dependency bump.
---

# Connectors Renovate Triage

## Mental model

Renovate is configured pretty aggressively in this repo: minor/patch bumps on Maven,
Dockerfiles, and GitHub Actions all `automerge: true`, and most packages land in one big
grouped "all non-major dependencies" PR per branch. That means a Renovate PR only ever
needs a human (or this skill) when something stopped it from sailing through — CI went
red and it got pulled out of the merge queue. So the job here is never "review a dependency
bump," it's always "figure out why this one didn't auto-merge, and make sure it (or the
next one just like it) can."

There are four distinct outcomes, and mixing them up is the main way this goes wrong:
1. **The bump is fine, CI is just noisy** → rerun it, don't touch anything else.
2. **The bump actually broke something** → revert the version *and* teach renovate.json
   not to propose that update again, so the fix and the prevention happen together.
3. **The bump is correct, and it caught a real pre-existing problem** — a linter, formatter,
   or validator got stricter and is right to complain — → the fix is usually to fix the
   thing it's complaining about, not revert the tool. See Step 5's fourth case; this one is
   easy to get wrong in the direction of reverting by reflex when fixing forward would take
   just as long and leave the codebase better off.
4. **The build failed on purpose** — an enforcer rule or similar tripwire fired because a
   dependency it deliberately guards moved → answer the question the guard is asking, and
   never revert or pin in response to it. See Step 4b; this is the case where the reflexive
   fix produces a green build that's actively wrong.

## Defaults & scope

- Repo defaults to `camunda/connectors` (https://github.com/camunda/connectors) unless the
  user names a different one — if they do, treat everything below as a pattern to adapt,
  not a literal script (paths, branch names, and the AlwaysGreen bot are specific to this
  repo).
- Active base branches: `main` plus `stable/8.x` branches. Confirm the current live set
  with `git branch -r | grep stable/` rather than assuming — new stable branches get cut
  periodically and old ones age out.
- `renovate.json` lives at the repo root. As far as this file shows, there's a single copy
  and branch-specific behavior comes entirely from `matchBaseBranches` selectors inside it
  (you can see this already in use — major updates are disabled on `stable/*` as a whole,
  and `io.camunda*` majors/minors are separately disabled there too). Before editing,
  double check with `git show <base-branch>:renovate.json` that the target branch doesn't
  have its own diverged copy — don't assume the root-only layout blindly.

## Input modes

- **One or more PR numbers given directly** → diagnose those.
- **No numbers ("check the failing renovate PRs")** → discover them:
  ```bash
  gh pr list --repo camunda/connectors \
    --search "author:app/renovate is:open" \
    --json number,title,url,baseRefName,headRefName,statusCheckRollup,mergeStateStatus \
    --limit 50
  ```
  Keep only the ones that actually need attention: a `statusCheckRollup` entry with
  `FAILURE`/`ERROR`, or a `mergeStateStatus` of `BLOCKED`/`DIRTY` following a merge-queue
  rejection. A Renovate PR just sitting open with everything green is usually waiting on
  its schedule window or on `prConcurrentLimit`, not actually broken — leave those alone.

## Single PR vs. batch

**One PR named explicitly** → run Steps 1–6 below and just do it end to end (push the
fix straight to the branch, update renovate.json alongside it, report what happened after
the fact) — that matches how you said you want this to work, and a single PR doesn't need
a check-in gate.

**Several PRs at once** → do Steps 1–4c (diagnosis only, no changes) for *all* of them
first, then show one triage table before fixing anything:

| PR | Base | Failing check | Verdict | Culprit package | Proposed fix |
|----|------|---------------|---------|------------------|--------------|
| #7858 | stable/8.7 | sm-e2e-helm | Flaky — rerun passed | — | none, just reran |
| #7861 | main | maven-build (module X) | Real | com.foo:bar 3.2→4.0 | revert to 3.2, block major |

This isn't a permission gate (you already have the green light to push and edit config) —
it's so that across several PRs you can see which are duplicates of the same root cause,
which are just noise, and which genuinely need the config changed, before pushing N commits
and editing the shared renovate.json N separate times. Then execute Steps 5–7 for the
confirmed set.

## Step 1 — Check whether AlwaysGreen already triaged it

This repo has a bot (`quality-assurance-ci`, "AlwaysGreen") that comments on failing PRs
with a structured triage: a **Stage**, a **Category**, an **Owner**, an **Evidence** line,
a link to the run, and a link to a debugging cookbook. Check for it before doing your own
diagnosis — it may have already done half the work:

```bash
gh pr view <PR> --repo camunda/connectors --json comments \
  | jq -r '.comments[] | select(.author.login=="quality-assurance-ci") | .body' | tail -1
```

If **Category** is something specific (not "unknown"/"Needs triage"), read the linked
cookbook before re-deriving anything yourself — someone already characterized this failure
mode. If it says "Needs triage" or there's no comment, move on to Step 2.

## Step 2 — See what actually changed

```bash
gh pr diff <PR> --repo camunda/connectors
```

For every changed dependency, note: which file it's in (a `pom.xml` version property, a
`Dockerfile` `FROM` line, a `docker-images.properties` entry, or a `.github/workflows/*.yml`
`uses:` line — see `references/manager-specifics.md` for how each one is structured), the
package identifier, old → new version, and the update type (major/minor/patch/digest —
compare the semver parts, or "digest" if it's a hash-only change with no version bump).

The recurring "all non-major dependencies" PRs bundle everything minor/patch across every
ecosystem into one PR per branch — expect the diff to list anywhere from one to thirty
packages. That's normal, not a sign something's wrong with the PR itself.

## Step 3 — Get the actual failure signal

```bash
gh pr checks <PR> --repo camunda/connectors
```

Find the failing required check. Also look at the PR timeline for a "removed this pull
request from the merge queue due to failed status checks" event — merges here go through
GitHub's merge queue, so that event is the real gate, not just an individual red check mark.
Then pull the run, asking only for the failing step's output (a full Maven multi-module
build log is enormous):

```bash
gh run view <run-id> --repo camunda/connectors --log-failed
```

Read for a concrete error signature: a compile error naming a class/method, a specific
failing test, "could not resolve dependency," a checksum mismatch, a Testcontainers/Docker
pull failure, etc.

## Step 4 — Rule out flakiness before blaming the dependency

This matters more than it might sound: wrongly pinning a perfectly good version because CI
happened to be noisy that run creates permanent config debt and quietly blocks a real
upgrade forever. It's a real pattern here, not a hypothetical — a recent grouped bump of
unrelated libraries (Google Cloud, AWS SDK, Jackson) got pulled from the merge queue over an
"SM E2E / Helm Integration Tests" failure that had nothing to do with any package in the
diff.

Signs it's noise rather than the dependency's fault: the failing check has no plausible
connection to anything in the diff (e.g., an E2E/Helm/infra job failing on a pure Jackson
minor bump), a timeout or connection-refused pattern, or a test module that wasn't touched
by any of the changed dependencies at all.

If it looks flaky, rerun once and wait:
```bash
gh run rerun <run-id> --repo camunda/connectors --failed
```
If it passes, you're done for that PR — no revert, no config change. Note in your report
that it was a rerun, and if it needs to go back into the merge queue and doesn't do so on
its own, mention that to the user.

If it fails again the same way, treat it as real — but sanity check it's plausibly
connected to something in the diff before moving on. A failure that's obviously unrelated
infrastructure trouble isn't renovate's fault at all; say so and point at whatever owner the
AlwaysGreen comment named, rather than reverting a dependency that had nothing to do with it.

## Step 4b — Check whether the failure is a deliberate tripwire

Before hunting for a culprit, check whether the build failed *on purpose*. This repo plants
intentional guards that fire when a specific dependency version changes — most commonly
Maven Enforcer `requireProperty` rules in `parent/pom.xml` that pin a version property to
an exact regex and carry a `regexMessage` explaining what to re-check when it moves.

These exist because of the CVE workflow: when an upstream library ships a vulnerable
transitive dependency and hasn't fixed it yet, the fix is to override that transitive
version directly (a `version.*` property + a `dependencyManagement` entry) — and then plant
an enforcer rule so that the *next* time the upstream library gets bumped, the build stops
and forces someone to ask "can the override go away now?". The failure is the guard working
as designed, not a break.

**Recognizing it**: the failing check is `maven-enforcer-plugin`, and the log contains the
rule's own `regexMessage` — which is written to tell you exactly what to do. Read it. It
will name the override property, the transitive dependency, the version threshold that
would make the override unnecessary, and usually a CVE link.

**This is the single most dangerous case to misdiagnose.** The reflexive fix — revert the
bump, add a renovate.json rule blocking that package — makes CI go green and looks like a
clean success, while actually freezing the library at a vulnerable-adjacent version forever
and permanently disarming the tripwire that was supposed to prompt the cleanup. Never
revert or pin in response to an enforcer guard failure.

**Resolve it by answering the question the guard is asking**: does the new version of the
upstream library now bring in a fixed version of the transitive dependency?

```bash
gh pr checkout <PR> --repo camunda/connectors
# -Dverbose reveals what the transitive WOULD resolve to, including entries
# "omitted for conflict with" the overridden version — that's the number you need
mvn dependency:tree -Dverbose -Dincludes=<groupId>:<artifactId>
```

Cross-check against the upstream POM for the new version if the tree output is ambiguous.
Then take one of two paths:

**Path A — the new version ships the fix.** The override is now redundant. Remove all three
pieces named in the `regexMessage`, which typically means: the `version.*` property, the
`dependencyManagement` entry pinning it, and the enforcer rule itself. Re-run
`mvn dependency:tree -Dincludes=...` *after* removing them to confirm the transitive really
resolves to the fixed version on its own rather than trusting the upstream POM. Because this
removes a security control and changes the repo's CVE posture, confirm with the user before
pushing, and show the evidence (old vs. new resolved version, the CVE, the threshold from
the guard message). Removing a guard is also usually a signal to update the CVE registry
maintained by the `connectors-cve-triage-v2` skill — check that skill for where the registry
lives and what a resolved entry should look like, rather than assuming a path.

**Path B — the new version still ships the vulnerable transitive.** The override has to
stay. Keep the property, keep the `dependencyManagement` entry, and **update the enforcer
rule's regex to the new version** so the guard re-arms for the next bump. This is the step
that's easy to forget, and forgetting it leaves the build red indefinitely — the rule will
keep failing against a version that's now legitimately in the tree. Update the
`regexMessage` too if it names the old version. This path is low-risk (it preserves the
existing security posture and just moves the tripwire forward), so it doesn't need a
check-in — apply it and report it.

Either way: no revert, no renovate.json rule. The bump itself is fine and should land.

Guards don't always take the enforcer form — a `pom.xml` comment explaining why something is
pinned, or a `banned-dependencies` rule, is the same idea. If a failure message reads like
someone wrote it deliberately for a future reader, treat it as this case and follow its
instructions rather than working around it.

## Step 4c — For grouped PRs, find the specific culprit

If the PR only touched one dependency, that's your culprit (once Step 4 has ruled out
flakiness). If it's a grouped bump with several packages:

**Fast path**: match the error text (an exception's package name, a Maven artifact
coordinate, a Docker image name) against the list of changed dependencies from Step 2. This
resolves it most of the time.

**If it's ambiguous**, reproduce and bisect locally rather than pushing a string of trial
commits to burn CI runs:
```bash
gh pr checkout <PR> --repo camunda/connectors
```
Identify the failing module from the log, run just that module's build/tests locally
(`mvn -pl <module> -am test`, or the equivalent for the failing stage), then revert half the
bumped dependencies in the working tree and rerun. Narrow by half each time until one
dependency's reversion makes the failure disappear. This is faster than round-tripping
through GitHub Actions and doesn't touch the remote branch until you've isolated the cause.

## Step 5 — Decide the right fix, not just the fastest one

Once you know the culprit, the fix isn't always "block this update type forever." Pick
based on what actually happened:

- **A specific release is just broken** (bad checksum, yanked version, obviously buggy
  point release) → revert to the last good version. Consider `allowedVersions` excluding
  that one bad release rather than blocking the whole update type — the *next* patch might
  be fine.
- **A real incompatibility from the bump** (breaking API change, behavior this codebase
  isn't ready for) → revert, and add a `packageRule` blocking that update type for that
  package going forward. This is the common case, and the file already has several examples
  of exactly this shape to follow — see `references/renovate-config-patterns.md`.
- **The new version is genuinely the right direction** (security fix, EOL dependency) but
  needs actual code changes → revert to unblock CI *for now*, but don't silently add a
  long-lived block and move on. Say so plainly in the PR comment and suggest a follow-up
  issue for the migration — quietly suppressing a needed upgrade forever is worse than
  leaving it visibly pending.
- **A stricter tool version is correctly flagging a real, pre-existing issue** — a linter,
  formatter, or static-analysis action (hadolint, checkstyle, actionlint, etc.) got pickier
  and is right: the code was already slightly off, the bump just started noticing. Reverting
  the tool is the wrong reflex here almost as often as it's the right one — it usually just
  means permanently freezing that tool below the version that would catch this, so the
  underlying issue never gets flagged again. If the fix looks small and mechanical (a couple
  of lines, low behavioral risk — e.g. switching a Dockerfile `HEALTHCHECK` to JSON form, or
  using a numeric UID instead of a name), that's usually the better fix. This case is
  different from the other three in one important way: it means editing real
  application/infra code, not just a version pin or a renovate.json rule, so even on a
  single PR you'd otherwise handle end-to-end, describe what's flagged and ask whether to
  apply the quick fix or revert/pin the tool instead — don't just pick one silently. If the
  same tool bump shows up across several PRs (e.g. one per branch), one answer covers all of
  them; no need to ask per PR.

## Step 6 — Apply the fix

**Revert the version** — manager-specific mechanics (where the version lives, how to edit
it) are in `references/manager-specifics.md`. The PR diff from Step 2 already shows you the
exact line and the old value; reverting means restoring that line, not re-deriving it from
scratch.

**Update renovate.json** (when Step 5 called for it) — follow the file's existing
convention exactly: a new object in `packageRules` with a `description` explaining why in
plain English, scoped as narrowly as the situation calls for (`matchPackageNames`,
`matchUpdateTypes`, `matchBaseBranches` if it's only broken on one branch). See
`references/renovate-config-patterns.md` for real shapes already in the file to match, plus
a couple of manager-specific quirks worth knowing about before you add a rule.

**Push directly to the Renovate branch:**
```bash
gh pr checkout <PR> --repo camunda/connectors
# edit the files
git add -A
git commit -m "fix(deps): revert <package> to <version> — <one-line reason>"
git push
```

**Comment on the PR** explaining what happened — what failed, what you found, what you
changed, and a link to the failing run. If this PR needs to go back into the merge queue
manually rather than re-entering on its own, say so.

If Step 5 concluded this needs a real migration rather than a permanent block, say that
explicitly in the comment instead of quietly adding a suppression rule — flag it as
follow-up work, don't bury it.

## Step 7 — Report

**Single PR**: a short summary — what broke, what you changed, whether renovate.json was
touched and how, link to the PR.

**Batch**: one consolidated table covering every PR you acted on (same shape as the triage
table from earlier, with a final "Result" column), plus a one-line note for any PR you left
alone and why (flaky-and-passed, or not actually renovate's fault).

## Safety notes

- Check `git log` on the PR branch before pushing — if someone other than
  `renovate[bot]`/`renovate-approve[bot]` has already pushed commits on top, treat that as
  a signal a human is already working the PR and confirm before overwriting anything.
- Don't add a `packageRule` that duplicates one that already exists — skim the existing
  `packageRules` array first (there are already several package-specific blocks; see
  `references/renovate-config-patterns.md`).
- Only touch what's actually broken. Don't widen a fix into unrelated cleanup of the diff.

## Reference files

- `references/manager-specifics.md` — where each manager's version lives and how to revert
  it: Maven properties/BOMs, Dockerfile `FROM` lines, the `docker-images.properties` custom
  regex manager (and its quirk), GitHub Actions `uses:` lines.
- `references/renovate-config-patterns.md` — real `packageRule` shapes already in this
  file to match style against, the base-branch config caveat, and update-type detection.
