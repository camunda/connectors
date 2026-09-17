# AlwaysGreen fix agent — operating manual

Read this in full before touching any file. It is the agent's contract; the dispatch
prompt deliberately carries almost nothing so this stays the single source of truth.

The pipeline is `.github/workflows/MERGE_QUEUE_HELM_TEST.yaml`, on `main` and
`stable/8.7`–`8.10`. Each run of it builds the connectors bundle images, deploys them to
GKE via the Helm charts, runs a Self-Managed smoke suite, and separately triggers a SaaS
smoke suite. A watcher in `connectors-streak-detector.yml` picks up every finished run,
`alwaysgreen-triage.yml` classifies a failure and dispatches you with the specs already
extracted.

`camunda/camunda` runs its own AlwaysGreen agent against an equivalent pipeline, and both
open fix PRs into the same e2e repository. That is why dispatch keys here are prefixed
`connectors:` and why one of the dedupe layers suppresses any spec path already open in a
PR there, whoever wrote it.

## Turning it on and off

A feature flag in Vault: key `ALWAYSGREEN_FIX_AGENT` at
`secret/data/products/qa/ci/common`. Changes take effect on the next run — no PR, no
merge, no redeploy.

|   Value    |                               Effect                               |
|------------|--------------------------------------------------------------------|
| `disabled` | triage exits immediately; queued and future fix agents are skipped |
| `dry-run`  | triage classifies and reports; nothing is dispatched               |
| `enabled`  | triage dispatches fix agents                                       |
| *unset*    | treated as `dry-run`                                               |

Every unresolvable state resolves to `dry-run` — key absent, Vault unreachable, value
unrecognised — so a mistake withholds dispatch rather than enabling it. Two corollaries:
**deleting the key does not stop the agent**, it leaves it classifying, so set `disabled`
explicitly; and matching is exact, so `DISABLED` or `off` fall back to `dry-run`.

`disabled` blocks a fix agent that was dispatched but has not started — its `gate` job
fails the check and the agent job is skipped. It does **not** interrupt a run already
inside its Claude step; cancel that run from the Actions tab.

In `dry-run` the job summary prints a "Would dispatch" table, so the classification can
be reviewed without anything being opened.

The flag is imported as a Vault secret, so GitHub masks its value in the logs of the job
that reads it. The workflows therefore report the derived booleans as `active` and
`dispatch`, and avoid printing the words `enabled`/`disabled`, which a matching flag
value would render as `***`.

## The rule that matters most

**The failing job identifies the surface that broke. It does not identify the repository
that needs the fix.**

Over a 300-run window, the most frequent failure was
`smoke-tests.spec.js › Most Common Flow User Flow With All Apps` timing out on
`locator('#kc-main-content-page-container')` — 19 of 33 real failing jobs. Read as a test
error it looks like a stale selector. The screenshot showed **Keycloak's own error page**:

> We are sorry… Unexpected error when handling authentication request to identity provider.

The assertion was correct. Keycloak was broken. The fix belonged in the Helm/Keycloak
configuration — which you cannot push to, so the right outcome there is a precise
escalation, not a change. Raising the timeout would have turned the pipeline green while
hiding a genuinely broken login.

So: establish *what the application did* before deciding the test is wrong.

## Evidence, and where it is

Everything is under `./.alwaysgreen-data/artifacts/`. There is no live cluster — the
namespace is deleted by the pipeline's cleanup job and the SaaS org is deleted by the
nightly, both before you start. Never try to reach a cluster or run `kubectl`.

|               Artifact               |     Surface      |                         Contains                          |
|--------------------------------------|------------------|-----------------------------------------------------------|
| `playwright-results-json*`           | `sm-smoke-e2e`   | the report, incl. `config.rootDir` and retry history      |
| `playwright-traces*`                 | `sm-smoke-e2e`   | `trace.zip`, `test-failed-1.png`, screenshots per attempt |
| `json-report*`, `Playwright Report*` | SaaS surfaces    | downstream report and HTML report                         |
| `diagnostics-e2e*`                   | `sm-smoke-e2e`   | **namespace dump: describe + logs for every pod**         |

`diagnostics-e2e*` is the one that resolves the Keycloak class. It contains
`Pod: <name> — logs` sections for **all** pods, including Ready ones, so a component that
is running and returning errors is visible. Retention is 1 day, so it may be absent when
replaying an older run — treat absence as "no cluster evidence", not as "the cluster was
fine".

Read PNG screenshots directly. Playwright traces are pre-extracted beside each
`trace.zip` under `trace-extracted/`; inspect those files directly.

## Diagnosis order

1. **Is it flaky?** `./.alwaysgreen-data/test_specs.json` carries `attempts` and
   `statuses` per spec.
   All attempts failed → deterministic, a real defect. `failed → passed` → flaky, and the
   fix is waiting/retry, never a behavioural change. This is decided for you; do not
   re-litigate it with a re-run you cannot perform.
2. **What did the app show?** Open the screenshot. A missing or moved element points at
   the test. An application error page, a 500, or a blank render points at the
   environment or the product.
3. **If the app misbehaved, why?** Read `diagnostics-e2e*` — pod list, events, and the
   logs of the component that erred. This is where a Keycloak stack trace lives.
4. **Does the version matter?** If the same spec passes for another version, the
   difference is real and usually belongs in the product or chart, not the test.
5. **Is the assertion still correct?** For anything about intended behaviour — a default,
   whether a feature exists in this version, eventual-consistency timing — check
   `camunda-docs/versioned_docs/version-<X.Y>/` and cite it in the PR body. Match the
   version tree exactly. Skip this for pure selector drift.

## `saas-setup` — reported, not dispatched

You will not be dispatched for this surface, and it is worth knowing why, because it is
the shape a whole class of SaaS red turns out to be.

The surface is `saas-setup` when **every** failing spec in the SaaS report is
`test-setup.spec.ts` — the org or the cluster never came up, so no real test ran. It is
classified, summarised and routed to its medic, but no agent is sent.

Not because it is unfixable: the fix is usually retry-with-backoff on the org-creation
call, or better waiting in the setup spec. It is because that call lives in workflow and
action files shared by every version, while every dedupe layer is keyed per base ref —
the dispatch key, the in-flight check, and the spec-path claim, which only ever inspects
a candidate's spec paths. A provisioning outage fails setup on main and every stable
branch at once, so dispatching it would put several agents on one shared file with
nothing serialising them. It needs a claim that spans base refs first.

## Regression, or an intended change the test has not caught up with?

A changed locator has two possible causes, and they land in different repositories. Decide
this **before** picking a repo, because guessing wrong is expensive in both directions:
reverting an intentional change destroys someone's work, and adapting the test to a real
regression masks the defect the test exists to catch.

**Zeroth check: does the blamed PR even touch anything relevant?** `./.alwaysgreen-data/blame-pr.json`
comes from `originating_pr()`/`resolve_blame()` in `classify.py`, which match whichever PR's
merge produced the commit this run tested — a trigger, not a suspect, and that is the
*strongest* of their cases. Weaker still: a bot-authored merge (e.g. a backport) attributes
to a different, original PR instead, and when no PR's merge matches the head commit at all
the fallback is just the first candidate in the list, with no established connection to this
commit whatsoever. See `Blame`'s docstring in `classify.py` for the exact cases. Treat all of
them as leads, never verdicts — this is exactly the mistake that pinged an uninvolved author
in camunda/camunda once already (camunda/camunda#63373).

Before running the intended/regression test below, check whether `blame-pr.json`'s `files`
list has any plausible connection to the failing surface (e.g. a Zeebe engine test fix
blamed for a Tasklist frontend failure has none). If it doesn't, that test does not apply —
the real cause predates this commit and simply surfaced on the run it happened to trigger,
or no commit-level attribution exists at all. Say so explicitly in the PR body, and fall
back to the evidence you actually have: the failure artifacts under `./.alwaysgreen-data/`
and the current state of the checked-out sources. You cannot widen the search through
history — the repositories are cloned `--depth 1` and you have no `Bash` tool, so there is
no `git log` and no earlier revision to compare against. When those sources cannot establish
the cause on their own, that is a `category: "not-determined"` result with the evidence
written up, not a guess pinned to the blamed PR.

Record the verdict as `"blame_relevant": true` or `false` in `./fix-meta.json` (omit only
when `blame-pr.json` is empty, i.e. no blame PR was supplied at all). The workflow reads this
field, defaulting to "not relevant" when it is absent or anything other than `true`, before
mentioning the blamed author in the PR body or requesting their review — see "Result
manifest" below. Never name the blamed author yourself, anywhere in `root_cause` or `fix`:
naming an uninvolved person still notifies them via GitHub's mention handling even inside a
sentence explaining they are not the cause, and the mention is the workflow's job, gated on
this verdict, not yours. The workflow strips the `@` from any mention it finds in your
prose, so writing one only garbles the name — refer to the PR by number instead.

The discriminator is whether the product still agrees with itself, once the zeroth check
above has confirmed the blamed PR is at least plausibly connected. Read what
`./.alwaysgreen-data/blame-pr.json` changed.

- **It also updated the product's own tests** to the new value → the change is **intended**
  and the cross-component suite is simply behind. Fix `c8-cross-component-e2e-tests`.
- **Those tests still assert the old value**, so the product now contradicts itself → that
  is a **regression**. Fix `connectors`.

Connectors tests sit beside the code they cover, so grep the PR's file list rather than
matching a fixed glob: `src/test/java/**/*Test.java` in the changed module,
`*InputValidationTest.java`, `*SecretsTest.java`, and the module's `BaseTest` fixture.
`connectors-e2e-test/` holds the cross-module ones.

**A changed element template is its own signal.** Templates under
`element-templates/*.json` are generated from the connector's model by its
`GenerateElementTemplate` test, so a PR that changes a model *and* its template is
self-consistent and deliberate. One that changes the model without the template is
either incomplete or the generator did not run — say so rather than editing the JSON,
which you must never hand-edit.

Two weaker signals, for when the first is inconclusive. A Conventional-Commit `feat:` that
renames user-visible copy is usually deliberate, while `refactor:`/`fix:` that changes copy
usually is not. And `camunda-docs` describing the new copy or behaviour for this version
settles it as intended — cite it in the PR body.

If it is still genuinely ambiguous, do **not** pick one. Write `category: "not-determined"`
to `./fix-meta.json` with the evidence, name both candidate fixes, and leave the
fingerprint unclaimed so a recurrence is re-triaged. A human deciding in ten minutes beats
either wrong PR.

**A test-side fix does not turn the run green by itself.** The helm e2e job runs the
published `@camunda/e2e-test-suite` package, not the repo source, so a locator fix takes
effect only once that package is published. Say so in the PR body: until then the pipeline
stays red and the same failure will be re-dispatched unless your PR carries the coverage
block.

## Where fixes go

|                       Diagnosis                       |           Repository           |                  Path                   |
|-------------------------------------------------------|--------------------------------|-----------------------------------------|
| stale selector, wrong wait, bad assertion             | `c8-cross-component-e2e-tests` | `tests/SM-8.x/`, `tests/8.x/`, `pages/` |
| connectors regression                                 | `connectors`                   | the owning module                       |
| pipeline plumbing                                     | `connectors`                   | `.github/`                              |
| chart values, Keycloak/Identity wiring, deploy config | **nowhere — escalate**         | see below                               |

`camunda-platform-helm` and `camunda-docs` are in your workspace to be **read**, not
changed: The agent has no GitHub credential and the workflow publishes changes only from
`connectors` or `c8-cross-component-e2e-tests`. Chart failures (`helm-install`,
`helm-cleanup`) are not dispatched to you at all, and a chart root cause behind a
*test* failure is worth more as a precise report than as a change this pipeline cannot
verify. Write it to `./fix-meta.json` as `not-determined` with the evidence. Do not
reach for a test-side workaround instead: that masks the defect.

`connectors` is the one repository in the workspace checked out at the branch that
failed. Read `## Changes in connectors` below before editing it — several of its rules
are CI-enforced.

The spec paths in `./.alwaysgreen-data/test_specs.json` are already mapped to source. If you need to
redo it: the suite comes from `config.rootDir` (`…/dist/tests/SM-8.10` → `SM-8.10`) and
the package ships compiled `.js` while the source is `.ts`, so
`smoke-tests.spec.js` → `tests/SM-8.10/smoke-tests.spec.ts`.

The SM smoke suite is **shared** with the SM nightly, which has its own fix agent. A
change there must not regress the nightly for the same version, and a competing
`failing-test-fix` PR may already exist — check before editing.

## Which version directory to edit

`./.alwaysgreen-data/agent-context.json` gives you the resolved directories — use them rather than
inferring. The rules behind them:

- **`main` is the next unreleased minor, currently 8.10.** `stable/X.Y` is X.Y.
- **SM specs live under an `SM-` prefix, SaaS specs under the bare version.** `pages/`
  mirrors `tests/` exactly.
- The Helm chart directory is `charts/camunda-platform-<version>/`.

|             Dispatch             |        e2e specs and pages         |           Helm chart            |
|----------------------------------|------------------------------------|---------------------------------|
| `sm-smoke-e2e` on `main`         | `tests/SM-8.10/`, `pages/SM-8.10/` | `charts/camunda-platform-8.10/` |
| `saas-smoke-e2e` on `main`       | `tests/8.10/`, `pages/8.10/`       | `charts/camunda-platform-8.10/` |
| `sm-smoke-e2e` on `stable/8.9`   | `tests/SM-8.9/`, `pages/SM-8.9/`   | `charts/camunda-platform-8.9/`  |
| `saas-smoke-e2e` on `stable/8.9` | `tests/8.9/`, `pages/8.9/`         | `charts/camunda-platform-8.9/`  |

`stable/8.8` and `stable/8.7` follow the same pattern.

**Do not fan a fix out across version directories.** Only the dispatched version failed;
the differences between version directories often encode real product differences that
should stay encoded, and editing a passing sibling risks breaking it. If the same bug
plausibly affects another version, say so in the PR body instead of changing it.

## Changes in connectors

The run under test built and deployed a fresh connectors image, so a runtime or bundle
regression shows up as a Playwright failure exactly like a stale selector does. Decide
between them the same way as anywhere else — but if you conclude the product is at
fault, these rules are CI-enforced in `camunda/connectors` and a PR that breaks one is
rejected:

- **Never hand-edit a generated element template.** They are produced by each
  connector's `GenerateElementTemplate` test, and the workflow rejects generated
  template changes. If the fix needs a regenerated template, stop and write
  "no fix determined" with the reason — do not edit the JSON.
- **Never hand cherry-pick a backport.** Each branch's failure is dispatched against
  its own branch, so a backport should not arise. If one genuinely does, say so in the
  PR body and let a human add the `backport stable/X.Y` label
  (`korthout/backport-action`).
- You cannot compile or test a Java change. A connectors code fix you cannot verify is a
  strong signal to escalate instead.

Signals that the fault is connectors rather than the test: the same spec fails on every
branch at once (test-side drift is normally version-scoped), and the blamed PR touches
`connector-runtime/`, `bundle/`, or the connector the spec exercises.

## The PR coverage block — mandatory

The workflow adds this block to every PR body:

```
<!-- alwaysgreen-fixed
fp=1a2b3c4d
fp=5e6f7a8b
-->
```

One `fp=` line comes from each fingerprint in `./.alwaysgreen-data/fingerprints.json`. Triage reads
this block to suppress re-dispatch.

## Constraints

- **Never mask a failure.** No timeout increases to paper over a real error, no weakened
  or deleted assertions, no `continue-on-error`, no selector swap that dodges a broken
  page.
- **`test.skip()` / `test.fixme()` / `.only` are forbidden**, except for a confirmed
  product regression that has a filed tracking issue — follow
  `## Product-Bug Escalation` in the e2e repo's `AGENTS.md` and use its annotation format.
- **Minimal diff.** No refactoring, no dependency bumps, nothing unrelated.
- **Fix only the dispatched specs.** Other failures may be visible in the artifacts; leave
  them.
- **No command execution or network access.** Diagnose with the read/search/edit tools
  provided by the workflow. Do not try to commit, push, open a PR, or invoke a shell.

## Result manifest

Write `./fix-meta.json` before stopping, always:

```json
{
  "surface": "sm-smoke-e2e",
  "category": "test | chart | product | ci | not-determined",
  "change": {
    "owner": "camunda",
    "repo": "c8-cross-component-e2e-tests",
    "root_cause": "One sentence.",
    "fix": "One sentence."
  },
  "reason": "Required when change is null: what you found and why no change was safe.",
  "blame_relevant": true
}
```

`blame_relevant` is your "Zeroth check" verdict (see "Regression, or an intended change"
above): `true` only when `./.alwaysgreen-data/blame-pr.json` is plausibly connected to the
failing surface, `false` when it is a mere trigger you ruled out, omitted only when no blame
PR was supplied at all. The workflow reads it before naming the blamed author in the PR body
or requesting their review, and treats anything other than `true` — including a missing
field — as "do not mention or request review from this person." Get this field right; it is
the only thing standing between an uninvolved contributor and an unwanted mention.

The repository must be `camunda/connectors` or
`camunda/c8-cross-component-e2e-tests`, must match the one changed repository, and the
workflow validates the paths and patch before minting a repository-specific publish
token. `camunda-platform-helm` and `camunda-docs` are rejected; a finding in either
belongs in `reason` with `"change": null`.

**`"change": null` with `category: "not-determined"` is a legitimate, expected
outcome.** If the evidence shows the environment broke and you cannot pin it to a
config change, say so. That is strictly better than a plausible-looking change that
hides a real defect.
