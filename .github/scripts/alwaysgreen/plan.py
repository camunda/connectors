"""Turn classified AlwaysGreen failures into dispatch decisions.

Pure functions, like `classify`. `discover.py` fetches the inputs; everything that
decides *whether* and *what* to dispatch lives here so it can be tested without a
token.

Two levels of identity are used, for different jobs:

* A per-spec fingerprint identifies one failing test. It goes in the fix PR's
  coverage block and is what suppresses a re-dispatch once a PR covers it.
* A dispatch key, `<source>:<base_ref>:<surface>`, identifies one agent's remit. At most one
  agent per key may be in flight. This is the rule that actually holds the line when
  the same cause fails many consecutive runs: on 2026-07-23 seventeen runs failed on
  one root cause roughly 30-40 minutes apart, while an agent takes 15-60 minutes, so
  a PR-existence check alone loses the race repeatedly.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone

import classify

#: Reasons a candidate was not dispatched, surfaced in the job summary.
SUPPRESSED_NOT_DISPATCHABLE = "surface-not-in-increment"
SUPPRESSED_IN_FLIGHT = "agent-already-running"
SUPPRESSED_PR_COVERED = "open-pr-covers-all-specs"
SUPPRESSED_PR_OPEN = "open-fix-pr-for-surface"
SUPPRESSED_PRODUCT_BUG = "tracked-by-open-product-bug"
SUPPRESSED_NO_EVIDENCE = "no-failing-specs-extracted"
SUPPRESSED_CAP = "per-run-cap-reached"
SUPPRESSED_PATH_CLAIMED = "spec-path-claimed-by-open-pr"
#: Every spec was dropped, but by more than one of the sources above.
SUPPRESSED_ALL_ACCOUNTED = "all-specs-already-accounted-for"

#: GitHub rejects a label longer than this, and the dispatch key is stamped on fix
#: PRs as `ag-key:<key>`. Asserted over the supported matrix in test_plan.py so a new
#: source repo or base ref cannot silently push a label past the limit — the failure
#: mode is a label that is never created, which disables dedupe without any error.
KEY_LABEL_PREFIX = "ag-key:"
MAX_LABEL_LENGTH = 50


#: How long an open fix PR's key label keeps holding its dispatch key.
#:
#: The lock is there to win the race described above, but nothing released it: the
#: label stops matching `is:open` only when a human merges or closes the PR, so the
#: agent's own output locked the agent out of its own surface until someone acted on
#: it. camunda-platform-helm#6927 held one key from 2026-08-20 and every affected
#: triage for the six days after reported `open-fix-pr-for-surface` and dispatched
#: nothing.
#:
#: A surface carries many independent causes, so this window is how long an unrelated
#: failure waits. Two hours is roughly three failing runs at the 20-40 minute pipeline
#: cadence: long enough that the same cause is not re-dispatched while an agent's PR is
#: still being read, short enough that a different cause waits hours rather than days.
#: `inflight_keys` still allows one agent per key, so the floor on duplicate work is an
#: agent's runtime rather than this TTL.
PR_LOCK_TTL_HOURS = 2


#: How long an open PR's touched spec files keep blocking a dispatch that would edit
#: them.
#:
#: The claim exists so two agents do not rewrite the same spec at the same time, and it
#: is author-agnostic for that reason — but nothing released it either, and a claim is
#: coarser than the key lock: it holds every failure in every file the PR touches, for
#: whatever the PR happens to be. A draft opened on 2026-08-26 touching ten spec files
#: still held them on 2026-09-03, and run 33727363856 reported
#: `spec-path-claimed-by-open-pr` and dispatched nothing.
#:
#: Measured from last activity, not from creation, and a day rather than the key lock's
#: two hours: a PR being written, reviewed or rebased touches its own timestamp well
#: inside a day, so an active one never loses its files, while one nobody has touched
#: since yesterday is not work in progress that a second agent could collide with.
PATH_CLAIM_TTL_HOURS = 24


def pr_lock_expired(
    created_at: str | None, now: datetime, ttl_hours: int = PR_LOCK_TTL_HOURS
) -> bool:
    """Whether an open PR is too old to keep holding a lock.

    Used for both locks a PR can hold: its dispatch key, from `createdAt`, and the spec
    files it touches, from `updatedAt`. Only the timestamp and the TTL differ.

    A missing or unparseable timestamp keeps the lock, and `ttl_hours <= 0` disables
    expiry altogether: the bias matches the `ok` flags in discover's key lookups,
    where an unproven state suppresses rather than risks a duplicate PR.

    Either timestamp is read as UTC when it carries no offset, so a naive `now` does
    not raise against GitHub's offset-aware `createdAt`.
    """
    if ttl_hours <= 0:
        return False
    text = (created_at or "").strip()
    if not text:
        return False
    try:
        created = datetime.fromisoformat(text.replace("Z", "+00:00"))
    except ValueError:
        return False
    if created.tzinfo is None:
        created = created.replace(tzinfo=timezone.utc)
    if now.tzinfo is None:
        now = now.replace(tzinfo=timezone.utc)
    return now - created > timedelta(hours=ttl_hours)


def dispatch_key(base_ref: str, surface: str, source: str = "") -> str:
    """Identify one failing surface on one branch of one source pipeline.

    `source` is the source repository's name (`connectors`, `camunda`). Several
    pipelines open fix PRs into the same e2e repository and share branch names, so
    without it `main:saas-smoke-e2e` from connectors and from the monorepo are the
    same string and each would suppress the other's dispatch.

    Fingerprints deliberately stay un-namespaced: they identify a failing *test*,
    which is the same test whichever pipeline observed it, so one fix PR should
    cover it for all of them.
    """
    return f"{source}:{base_ref}:{surface}" if source else f"{base_ref}:{surface}"


@dataclass
class Candidate:
    """One surface that failed on one base ref, with the evidence for it."""

    base_ref: str
    surface: str
    job_name: str
    #: Source repository name the failing run belongs to, e.g. `connectors`.
    source: str = ""
    specs: list[classify.FailingSpec] = field(default_factory=list)
    #: Run whose artifacts hold the evidence. For SaaS this is the downstream run
    #: in the e2e repository, not the AlwaysGreen run.
    evidence_run_url: str = ""
    evidence_repo: str = ""
    #: Set when the surface produced no per-spec detail (job-level failure).
    job_level: bool = False

    @property
    def key(self) -> str:
        return dispatch_key(self.base_ref, self.surface, self.source)

    @property
    def spec_fingerprints(self) -> list[str]:
        return [
            classify.spec_fingerprint(self.base_ref, self.surface, s.file, s.test_name)
            for s in self.specs
        ]

    @property
    def fingerprints(self) -> list[str]:
        """Every fingerprint this candidate would claim in a PR coverage block."""
        if self.job_level or not self.specs:
            return [classify.job_fingerprint(self.base_ref, self.surface, self.job_name)]
        return self.spec_fingerprints

    @property
    def deterministic_specs(self) -> list[classify.FailingSpec]:
        return [s for s in self.specs if s.deterministic]


@dataclass
class Suppression:
    candidate: Candidate
    reason: str
    detail: str = ""


@dataclass
class Plan:
    dispatches: list[Candidate] = field(default_factory=list)
    suppressed: list[Suppression] = field(default_factory=list)
    #: Failing jobs dropped by the noise prefilter, for the summary only.
    noise: list[tuple[str, str]] = field(default_factory=list)


def merge_candidates_by_key(candidates: list[Candidate]) -> list[Candidate]:
    """Collapse candidates that share a dispatch key into one.

    `build_candidates` produces one candidate per failing job, but the dispatch key is
    per surface — so a run with two failing jobs on the same surface yields two
    candidates carrying the same key, and every dedupe layer downstream is keyed on
    exactly that. None of them looks at the plan being built, so both would be
    dispatched: two agents, same remit, same files, inside one pass.

    Run 33179631020 is the shape — the SM `smoke` and `full` legs both failed, and both
    candidates read the same Playwright report, so the merge also removes the duplicate
    spec evidence that would otherwise be handed to the agent twice.

    First-seen order is preserved so the plan stays stable across runs. The surviving
    candidate keeps the first job's name and evidence pointers; a merged candidate is
    job-level only if every part was, because one part carrying specs means there is
    per-spec evidence to dispatch on.
    """
    merged: dict[str, Candidate] = {}
    for cand in candidates:
        first = merged.get(cand.key)
        if first is None:
            merged[cand.key] = cand
        else:
            first.specs.extend(cand.specs)
            first.job_level = first.job_level and cand.job_level

    # Deduplicate every accumulated list, not just the parts added above. Two sources of
    # repeats: a candidate can arrive already carrying them, because `sm_candidates`
    # accumulates specs from every downloaded report and two reports can cover the same
    # spec; and a single later candidate can repeat one internally. A duplicate is not
    # cosmetic -- it hands the agent the same spec twice and repeats its fingerprint in
    # the coverage block.
    for cand in merged.values():
        seen: set[tuple[str, str]] = set()
        unique = []
        for spec in cand.specs:
            ident = (spec.file, spec.test_name)
            if ident in seen:
                continue
            seen.add(ident)
            unique.append(spec)
        cand.specs = unique
    return list(merged.values())


def plan_dispatches(
    candidates: list[Candidate],
    *,
    covered_fingerprints: set[str],
    inflight_keys: set[str],
    open_pr_keys: set[str],
    product_bug_fingerprints: set[str],
    open_pr_keys_with_coverage: set[str] | None = None,
    claimed_paths: dict[str, int] | None = None,
    max_dispatches: int = 2,
    dispatchable_surfaces: frozenset[str] = classify.DISPATCHABLE_SURFACES,
) -> Plan:
    """Decide which candidates to hand to the fix agent.

    Checks are ordered cheapest-and-most-decisive first so the summary reports the
    most useful reason when several apply.

    `open_pr_keys_with_coverage` are the keys of `open_pr_keys` whose every holding PR
    claims at least one fingerprint in its coverage block. Those PRs state which specs
    they claim, so the coarse per-surface lock is skipped for them and the per-spec
    accounting below decides. A PR whose block claims nothing — absent, or present but
    empty — is not one of them and keeps its surface locked.
    """
    plan = Plan()
    pr_keys_with_coverage = open_pr_keys_with_coverage or set()

    for cand in merge_candidates_by_key(candidates):
        if cand.surface not in dispatchable_surfaces:
            plan.suppressed.append(
                Suppression(cand, SUPPRESSED_NOT_DISPATCHABLE, cand.surface)
            )
            continue

        if cand.key in inflight_keys:
            plan.suppressed.append(Suppression(cand, SUPPRESSED_IN_FLIGHT, cand.key))
            continue

        # An open fix PR that claims no specs stops a second agent on its surface: the
        # coverage block is written by the agent, so one that is missing or empty
        # states nothing about its remit and the whole surface has to be assumed.
        # A PR that claims at least one spec is authoritative per spec through
        # `covered_fingerprints`, so locking its surface only hides its neighbours —
        # and a surface's failures are usually independent of each other.
        #
        # Two residuals it does not cover. A partially written block reads as
        # authoritative, since the gate only asks "claims anything?"; validating it
        # against `/tmp/fingerprints.json` in alwaysgreen-fix.yml is the fix. And a
        # claim-nothing PR holds its surface only until PR_LOCK_TTL_HOURS, past which
        # `inflight_keys` and the dispatch caps are the only bound.
        if cand.key in open_pr_keys and cand.key not in pr_keys_with_coverage:
            plan.suppressed.append(Suppression(cand, SUPPRESSED_PR_OPEN, cand.key))
            continue

        if not cand.job_level and not cand.specs:
            plan.suppressed.append(Suppression(cand, SUPPRESSED_NO_EVIDENCE))
            continue

        claimed = claimed_paths or {}
        fps = cand.fingerprints

        if fps and all(f in product_bug_fingerprints for f in fps):
            plan.suppressed.append(
                Suppression(cand, SUPPRESSED_PRODUCT_BUG, ",".join(sorted(set(fps))))
            )
            continue

        # Drop specs an open PR, a product bug or an already-claimed spec file accounts
        # for; dispatch only what is left. The path claim is author-agnostic: the other
        # agents editing these files (the e2e repo's own nightly triage, another product
        # pipeline, a human) dedupe on schemes this one cannot see, so the only reliable
        # signal is that the file is already open in a PR. Keyed on exact repo-relative
        # paths, so tests/8.9/x.spec.ts never shadows tests/8.10/x.spec.ts.
        #
        # Per spec, not per candidate. Suppressing the whole candidate on the first hit
        # dropped the fresh specs beside the claimed one, which cancels out the narrowed
        # surface lock above: a candidate carrying both a still-failing claimed spec and
        # a new one is exactly the case that lock was widened to let through.
        if not cand.job_level:
            accounted: list[str] = []
            path_hits: set[tuple[str, int]] = set()
            remaining = []
            for spec, fp in zip(cand.specs, cand.spec_fingerprints):
                if fp in covered_fingerprints:
                    accounted.append(SUPPRESSED_PR_COVERED)
                elif fp in product_bug_fingerprints:
                    accounted.append(SUPPRESSED_PRODUCT_BUG)
                elif spec.file in claimed:
                    accounted.append(SUPPRESSED_PATH_CLAIMED)
                    path_hits.add((spec.file, claimed[spec.file]))
                else:
                    remaining.append(spec)
            if not remaining:
                sources = sorted(set(accounted))
                detail = (
                    ", ".join(f"{path} (#{number})" for path, number in sorted(path_hits))
                    if sources == [SUPPRESSED_PATH_CLAIMED]
                    else ",".join(sources)
                )
                plan.suppressed.append(
                    Suppression(cand, sources[0], detail)
                    if len(sources) == 1
                    else Suppression(cand, SUPPRESSED_ALL_ACCOUNTED, detail)
                )
                continue
            cand.specs = remaining
        elif fps and all(f in covered_fingerprints for f in fps):
            plan.suppressed.append(Suppression(cand, SUPPRESSED_PR_COVERED))
            continue

        if len(plan.dispatches) >= max_dispatches:
            plan.suppressed.append(Suppression(cand, SUPPRESSED_CAP))
            continue

        plan.dispatches.append(cand)

    return plan


# ---------------------------------------------------------------------------
# PR coverage block
# ---------------------------------------------------------------------------

COVERAGE_BEGIN = "<!-- alwaysgreen-fixed"
COVERAGE_END = "-->"


def parse_coverage_block(body: str | None) -> set[str]:
    """Extract fingerprints a fix PR claims to cover.

    Format, one per line inside an HTML comment so it renders invisibly:

        <!-- alwaysgreen-fixed
        fp=1a2b3c4d
        fp=5e6f7a8b
        -->
    """
    text = body or ""
    start = text.find(COVERAGE_BEGIN)
    if start == -1:
        return set()
    end = text.find(COVERAGE_END, start)
    block = text[start : end if end != -1 else len(text)]
    out: set[str] = set()
    for line in block.splitlines():
        line = line.strip()
        if line.startswith("fp="):
            value = line[3:].strip()
            if value:
                out.add(value)
    return out


def render_coverage_block(fingerprints: set[str]) -> str:
    """Render a coverage block, sorted so repeated updates produce a stable diff."""
    lines = [COVERAGE_BEGIN] + [f"fp={fp}" for fp in sorted(fingerprints)] + [COVERAGE_END]
    return "\n".join(lines)


def merge_coverage_block(body: str | None, new_fingerprints: set[str]) -> str:
    """Return the body with the coverage block replaced by the union of fingerprints.

    Existing entries are never dropped: an accumulating PR must keep claiming every
    test it already fixed, or a later triage run would re-dispatch them.
    """
    text = (body or "").rstrip()
    existing = parse_coverage_block(text)
    merged = existing | set(new_fingerprints)

    start = text.find(COVERAGE_BEGIN)
    if start == -1:
        return f"{text}\n\n{render_coverage_block(merged)}\n"

    end = text.find(COVERAGE_END, start)
    tail = text[end + len(COVERAGE_END) :] if end != -1 else ""
    return f"{text[:start]}{render_coverage_block(merged)}{tail}".rstrip() + "\n"
