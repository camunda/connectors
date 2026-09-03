#!/usr/bin/env python3
"""Discover AlwaysGreen failures in a run and emit dispatch payloads.

The I/O shell around `classify` and `plan`: everything here talks to `gh` or the
filesystem, and every decision is delegated to those two modules so the rules stay
unit-tested.

Usage:
    discover.py --run-id 123 --base-ref main [--out plan.json] [--max-dispatches 2]

Writes a JSON object to --out (default stdout):

    {"dispatches": [...], "suppressed": [...], "noise": [...], "blame": {...}}

Exit status is 0 even when nothing is dispatchable — an empty plan is a normal
outcome, not an error. It is non-zero only when a required lookup fails, so that
"the API was down" cannot be mistaken for "nothing failed".
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import tempfile
from dataclasses import asdict
from datetime import datetime, timezone
from pathlib import Path

import classify
import plan as planning

REPO = os.environ.get("ALWAYSGREEN_REPO", "camunda/connectors")
E2E_REPO = os.environ.get("ALWAYSGREEN_E2E_REPO", "camunda/c8-cross-component-e2e-tests")
FIX_WORKFLOW = os.environ.get("ALWAYSGREEN_FIX_WORKFLOW", "alwaysgreen-fix.yml")
FIX_LABEL = os.environ.get("ALWAYSGREEN_FIX_LABEL", "alwaysgreen-fix")
#: Namespaces dispatch keys. camunda/camunda runs its own AlwaysGreen agent, both open
#: fix PRs into the same e2e repository, and both call their branch `main` — so without
#: this the two pipelines' keys collide and each suppresses the other's dispatch.
SOURCE = REPO.split("/")[-1]
#: Prefix of the per-dispatch-key label the fix workflow stamps on every PR it opens.
KEY_LABEL_PREFIX = planning.KEY_LABEL_PREFIX
#: Repos a fix PR can land in, mirroring the fix workflow's own label list and the App
#: token's scope. camunda-platform-helm is deliberately absent: the agent may read the
#: charts but never push to them, so no fix PR can exist there to find — and because the
#: two lookups below fail closed, a repository the token cannot reach would wedge every
#: dispatch shut rather than merely returning nothing.
FIX_PR_REPOS = [REPO, E2E_REPO]


def _ttl_hours(env_var: str, default: int) -> int:
    """An overridable TTL dial, read defensively.

    Matches the degrade-don't-crash bias of everything else here: an unreadable
    timestamp keeps the lock and a failed lookup suppresses, so a malformed dial must
    not raise at import and take down every entry point in this module. Set to 0 to
    restore the old never-expiring lock.
    """
    try:
        return int(os.environ.get(env_var, "").strip() or default)
    except ValueError:
        print(
            f"::warning::unparseable {env_var}; using the default ({default}h).",
            file=sys.stderr,
        )
        return default


#: How long an open fix PR keeps holding its dispatch key; see
#: planning.PR_LOCK_TTL_HOURS.
PR_LOCK_TTL_HOURS = _ttl_hours(
    "ALWAYSGREEN_PR_LOCK_TTL_HOURS", planning.PR_LOCK_TTL_HOURS
)
#: How long an open PR keeps blocking dispatches that would edit the spec files it
#: touches; see planning.PATH_CLAIM_TTL_HOURS.
PATH_CLAIM_TTL_HOURS = _ttl_hours(
    "ALWAYSGREEN_PATH_CLAIM_TTL_HOURS", planning.PATH_CLAIM_TTL_HOURS
)
#: Which attempt of the run to classify. The watcher gates on one specific attempt's
#: jobs, and a bare run request answers with whatever attempt is newest right now -- so
#: without this, a re-run landing between the gate and here has discovery reading a
#: different attempt than the one that was judged worth classifying, and dispatching on
#: its evidence. Empty means "whatever is latest", which is right for a manual replay.
RUN_ATTEMPT = (os.environ.get("ALWAYSGREEN_RUN_ATTEMPT") or "").strip()


def run_path(run_id: str, suffix: str = "") -> str:
    """API path for the run, pinned to RUN_ATTEMPT when one is set.

    Note what this cannot pin: `gh run download` has no attempt selector, so evidence
    artifacts always come from the latest attempt. That only diverges inside the narrow
    window where a re-run starts mid-triage, and the alternative -- classifying an
    attempt other than the one the gate judged -- is worse than reading artifacts from a
    newer one.
    """
    base = f"repos/{REPO}/actions/runs/{run_id}"
    if RUN_ATTEMPT:
        base = f"{base}/attempts/{RUN_ATTEMPT}"
    return f"{base}{suffix}"


class DiscoveryError(RuntimeError):
    """A required lookup failed, so the run cannot be classified at all."""


def log(message: str) -> None:
    print(message, file=sys.stderr)


def gh_json_ex(args: list[str], default) -> tuple[object, str]:
    """Run a gh command expecting JSON. Returns (value, error_text)."""
    try:
        out = subprocess.run(
            ["gh", *args], capture_output=True, text=True, timeout=120, check=True
        ).stdout.strip()
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired, OSError) as exc:
        stderr = (getattr(exc, "stderr", "") or str(exc)).strip()
        log(f"::warning::gh {' '.join(args[:3])} failed: {stderr[:200]}")
        return default, stderr
    if not out:
        return default, ""
    try:
        return json.loads(out), ""
    except json.JSONDecodeError:
        log(f"::warning::gh {' '.join(args[:3])} returned non-JSON output")
        return default, "invalid json"


def gh_json(args: list[str], default):
    """Run a gh command expecting JSON on stdout. Returns `default` on any failure."""
    value, _ = gh_json_ex(args, default)
    return value


def download_artifacts(run_id: str, repo: str, pattern: str, dest: Path) -> bool:
    dest.mkdir(parents=True, exist_ok=True)
    try:
        subprocess.run(
            [
                "gh", "run", "download", str(run_id),
                "--repo", repo, "--pattern", pattern, "--dir", str(dest),
            ],
            capture_output=True, text=True, timeout=300, check=True,
        )
        return True
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired, OSError):
        return False


def read_json_files(root: Path, filename: str) -> list[dict]:
    out = []
    for path in sorted(root.rglob(filename)):
        try:
            out.append(json.loads(path.read_text()))
        except (json.JSONDecodeError, OSError):
            log(f"::warning::could not parse {path}")
    return out


# ---------------------------------------------------------------------------
# Failing jobs
# ---------------------------------------------------------------------------


def failure_annotations(check_run_url: str | None) -> list[str]:
    if not check_run_url:
        return []
    path = check_run_url.replace("https://api.github.com/", "")
    data = gh_json(["api", f"{path}/annotations"], [])
    if not isinstance(data, list):
        return []
    return [
        a.get("message") or ""
        for a in data
        if isinstance(a, dict) and a.get("annotation_level") == "failure"
    ]


#: Job conclusions that count as a failure worth classifying.
#:
#: This MUST stay identical to the scan in connectors-streak-detector.yml, which decides
#: whether triage runs at all. The two are separate implementations — one bash, one
#: Python — and a conclusion the scan counts but this does not produces the worst
#: outcome available: a triage run that finds no candidate and reports the failing run as
#: having nothing to dispatch.
COUNTABLE_JOB_CONCLUSIONS = ("failure", "cancelled", "timed_out")
#: The subset that counts when the run ITSELF was cancelled. A `cancelled` job inside a
#: run that completed is a job timeout -- how the SaaS stage dies when the downstream run
#: outlives its watcher. A run that was cancelled as a whole is the merge queue or a human
#: dropping it, and its cancelled jobs carry no failure.
COUNTABLE_IN_CANCELLED_RUN = ("failure",)


def countable_conclusions(run_id: str) -> tuple[str, ...]:
    """Which job conclusions count for this run, mirroring the scan's two-level rule.

    An unreadable run conclusion falls back to the full set: over-including makes triage
    report a candidate it might not dispatch, while under-including drops a real failure
    silently, and only one of those is recoverable by looking at the summary.
    """
    meta, _ = gh_json_ex(["api", run_path(run_id)], None)
    conclusion = meta.get("conclusion") if isinstance(meta, dict) else None
    if conclusion == "cancelled":
        return COUNTABLE_IN_CANCELLED_RUN
    return COUNTABLE_JOB_CONCLUSIONS


def failing_jobs(run_id: str) -> list[dict]:
    """The run's failing jobs.

    Raises rather than degrading to an empty list: "the API is down" and "nothing
    failed" would otherwise be indistinguishable, and the second reads as a clean
    triage. The caller turns this into a non-zero exit so the workflow reports a
    failed classification instead of a quiet all-clear.
    """
    # --paginate --slurp, not a bare request: past 100 jobs a single page silently
    # truncates the run, and the classifier would report a later-page failure as absent
    # while the gate that invoked it had counted the whole run. --slurp turns the
    # per-page documents into one array so json.loads sees valid JSON.
    data, err = gh_json_ex(
        ["api", "--paginate", "--slurp", run_path(run_id, "/jobs?per_page=100")],
        None,
    )
    if not isinstance(data, list):
        raise DiscoveryError(f"could not list jobs for run {run_id}: {err.strip()[:200]}")
    jobs = [
        j
        for page in data
        if isinstance(page, dict)
        for j in (page.get("jobs") or [])
        if isinstance(j, dict)
    ]
    countable = countable_conclusions(run_id)
    return [j for j in jobs if j.get("conclusion") in countable]


# ---------------------------------------------------------------------------
# Evidence
# ---------------------------------------------------------------------------


def sm_candidates(run_id: str, base_ref: str, job_name: str, workdir: Path) -> planning.Candidate:
    """Build the SM candidate from playwright-results-json on the AlwaysGreen run."""
    cand = planning.Candidate(
        base_ref=base_ref,
        surface=classify.SURFACE_SM_E2E,
        job_name=job_name,
        source=SOURCE,
        evidence_run_url=f"https://github.com/{REPO}/actions/runs/{run_id}",
        evidence_repo=REPO,
    )
    dest = workdir / "sm"
    if not download_artifacts(run_id, REPO, "playwright-results-json*", dest):
        log("::warning::no playwright-results-json artifact for the SM e2e failure")
        return cand

    for report in read_json_files(dest, "playwright-results.json"):
        suite = classify.suite_from_rootdir(
            ((report.get("config") or {}).get("rootDir")) if isinstance(report, dict) else None
        )
        cand.specs.extend(classify.failing_specs(report, suite=suite))
    return cand


def saas_candidate(run_id: str, base_ref: str, job_name: str, workdir: Path) -> planning.Candidate:
    """Build the SaaS candidate by following the downstream run into the e2e repo.

    The surface is recomputed from the downstream report rather than taken from the
    pipeline's own `downstream_category`, which cannot express `product` or `mixed`.
    """
    cand = planning.Candidate(
        base_ref=base_ref,
        surface=classify.SURFACE_SAAS_INFRA,
        job_name=job_name,
        source=SOURCE,
    )

    cat_dir = workdir / "saas-category"
    downstream_url = ""
    if download_artifacts(run_id, REPO, "alwaysgreen-saas-category", cat_dir):
        for blob in read_json_files(cat_dir, "alwaysgreen-saas-category.json"):
            downstream_url = blob.get("downstream_run_url") or downstream_url

    if not downstream_url:
        log("::warning::no downstream SaaS run URL; treating as saas-infra")
        return cand

    cand.evidence_run_url = downstream_url
    cand.evidence_repo = E2E_REPO
    downstream_id = downstream_url.rstrip("/").rsplit("/", 1)[-1]

    # Fail loudly rather than into saas-infra: an outage here looks exactly like a
    # downstream run that published no reports, and that verdict silently withholds a
    # dispatchable failure.
    arts, err = gh_json_ex(
        ["api", f"repos/{E2E_REPO}/actions/runs/{downstream_id}/artifacts?per_page=100"],
        None,
    )
    if arts is None or not isinstance(arts, dict):
        raise DiscoveryError(
            f"could not list artifacts for downstream run {downstream_id}: {err.strip()[:200]}"
        )
    names = [a.get("name", "") for a in (arts.get("artifacts") or [])]
    has_reports = any(n.startswith("json-report") for n in names)

    counts = classify.SpecCounts()
    if has_reports:
        dest = workdir / "saas"
        if download_artifacts(downstream_id, E2E_REPO, "json-report*", dest):
            for report in read_json_files(dest, "results.json"):
                c = classify.count_specs(report)
                counts = classify.SpecCounts(
                    counts.total + c.total,
                    counts.failed + c.failed,
                    counts.flaky + c.flaky,
                    counts.setup_failed + c.setup_failed,
                )
                suite = classify.suite_from_rootdir(
                    ((report.get("config") or {}).get("rootDir"))
                    if isinstance(report, dict)
                    else None
                )
                cand.specs.extend(classify.failing_specs(report, suite=suite))
        else:
            has_reports = False

    cand.surface = classify.saas_surface_from_counts(counts, has_artifacts=has_reports)
    log(
        f"saas counts total={counts.total} failed={counts.failed} "
        f"flaky={counts.flaky} setup_failed={counts.setup_failed} -> {cand.surface}"
    )
    if cand.surface == classify.SURFACE_SAAS_INFRA:
        # No report at all, or a report with no failing spec: there is nothing to hand an
        # agent, so this surface is reported and routed rather than dispatched.
        cand.specs = []
        return cand

    if cand.surface == classify.SURFACE_SAAS_PROVISIONING:
        # Kept as report evidence, not as a dispatch payload -- this surface is not in
        # DISPATCHABLE_SURFACES, so no agent ever receives it. Every failing spec here IS
        # test-setup.spec.ts, and naming it is the whole value of the report: the reader
        # learns which setup step gave way without opening the run. `serialise` carries
        # these paths into the suppressed entry.
        return cand

    # A mixed report — org provisioning broke *and* a real spec failed — is a
    # saas-smoke-e2e dispatch for the real one, and the setup failures must not travel
    # with it: the agent would be told to fix a spec whose failure is a cluster problem,
    # and mixing the two remits in one PR is how a provisioning fix gets buried. Dropping
    # them here also keeps them out of the coverage block, so no fix PR claims the
    # provisioning failure and a later run can still classify it. Note what this does NOT
    # do: no second candidate is emitted for the discarded specs, so the provisioning
    # half of a mixed report is not reported by THIS run at all. It surfaces on a run
    # where setup is the only thing failing, which is when it classifies as saas-setup.
    dropped = [s for s in cand.specs if classify.is_setup_spec(s.file)]
    if dropped:
        log(
            f"saas: withholding {len(dropped)} provisioning spec(s) from the payload: "
            + ", ".join(sorted({s.file for s in dropped}))
        )
        cand.specs = [s for s in cand.specs if not classify.is_setup_spec(s.file)]
    return cand


# ---------------------------------------------------------------------------
# Dedupe inputs
# ---------------------------------------------------------------------------


def open_fix_prs(repo: str) -> tuple[list[dict], bool]:
    """Open fix PRs in one repository, with the fields dedupe needs. Returns (prs, ok).

    A seam, so `dedupe_inputs` can be unit-tested without a token.
    """
    prs, err = gh_json_ex(
        [
            "pr", "list", "--repo", repo,
            "--search", f"label:{FIX_LABEL} is:open",
            "--limit", "100", "--json", "labels,number,createdAt,body",
        ],
        None,
    )
    if prs is None or not isinstance(prs, list):
        log(f"::warning::open fix PR lookup failed for {repo}: {err.strip()[:200]}")
        return [], False
    return prs, True


def dedupe_inputs() -> tuple[set[str], set[str], set[str], bool]:
    """(covered fingerprints, keys with an open PR, keys decided per spec, ok).

    One lookup behind one `ok`, across every repo a fix can land in — FIX_PR_REPOS, not
    the source repo alone, since a test-side fix lands in the e2e repository. Coverage
    used to come from a second, separate `gh` call whose failure was swallowed into an
    empty set. That was survivable while every open fix PR locked its whole surface,
    because the key layer caught what the empty set missed; once a claiming PR's key
    stops locking, the two must agree or a failed coverage lookup dispatches a duplicate
    fix for a spec that PR already claims.

    Keys are read from the `ag-key:<source>:<base_ref>:<surface>` label the fix workflow
    stamps, not from the PR body: the body's coverage block is written by the agent and
    cannot be relied on to exist.

    A PR past PR_LOCK_TTL_HOURS stops holding its key, so a fix PR left unreviewed
    cannot wedge its surface shut for good.

    `keys_with_coverage` is the subset whose every active holder claims at least one
    fingerprint, so `plan` can decide those keys per spec instead of locking the
    surface. A block that parses to nothing — absent, or present with no `fp=` line —
    claims nothing, and a key any of whose active holders claims nothing stays out of
    the subset: the marker comment alone is not a statement of remit.

    Coverage is collected from every open fix PR, expired or not, and is the one thing
    here with no TTL at all: a fingerprint in a PR's coverage block is that PR's stated
    remit, and it holds for as long as the PR is open. The two locks a PR takes
    implicitly do expire — its dispatch key after PR_LOCK_TTL_HOURS from `createdAt`
    (below), and the spec files it touches after PATH_CLAIM_TTL_HOURS from `updatedAt`
    (`paths_claimed_by_open_prs`).

    As with `inflight_keys`, a failed lookup makes the caller suppress rather than risk
    a duplicate PR.
    """
    covered: set[str] = set()
    keys: set[str] = set()
    uncovered: set[str] = set()
    ok = True
    now = datetime.now(timezone.utc)
    for repo in FIX_PR_REPOS:
        prs, repo_ok = open_fix_prs(repo)
        if not repo_ok:
            ok = False
            continue
        for pr in prs:
            claims = planning.parse_coverage_block(pr.get("body"))
            covered |= claims
            pr_keys: set[str] = set()
            for label in pr.get("labels") or []:
                name = (label.get("name") or "").strip()
                if name.startswith(KEY_LABEL_PREFIX):
                    key = name[len(KEY_LABEL_PREFIX) :].strip()
                    if key:
                        pr_keys.add(key)
            if not pr_keys:
                continue
            if planning.pr_lock_expired(
                pr.get("createdAt") or "", now, PR_LOCK_TTL_HOURS
            ):
                log(
                    f"lock expired after {PR_LOCK_TTL_HOURS}h: {repo}#{pr.get('number')} "
                    f"no longer holds {', '.join(sorted(pr_keys))}"
                )
                continue
            keys |= pr_keys
            if not claims:
                uncovered |= pr_keys
            log(
                f"open fix PR {repo}#{pr.get('number')} holds "
                f"{', '.join(sorted(pr_keys))}, claiming {len(claims)} spec(s)"
            )
    # Per key, not per PR: dispatchability is an intersection over every active holder,
    # so a PR-level line cannot state it. Logged so a suppressed run names its blocker
    # without anyone cross-listing open fix PRs by hand.
    for key in sorted(keys):
        log(
            f"key {key}: "
            + (
                "locked (a holder claims no specs)"
                if key in uncovered
                else "decided per spec (every holder claims some)"
            )
        )
    return covered, keys, keys - uncovered, ok


def inflight_keys() -> tuple[set[str], bool]:
    """Dispatch keys of in-progress fix-agent runs.

    Returns (keys, ok). On failure `ok` is False and the caller suppresses rather
    than dispatching: a duplicate PR is worse than a delay, and the next failing
    run retries in ~30-40 minutes anyway.
    """
    runs, err = gh_json_ex(
        [
            "run", "list", "--repo", REPO, "--workflow", FIX_WORKFLOW,
            "--limit", "50", "--json", "status,name,databaseId",
        ],
        None,
    )
    if runs is None:
        # A workflow with no runs yet — the state on first deployment — is not an
        # outage and must not wedge dispatch shut.
        if any(m in err.lower() for m in ("could not find any workflows", "no workflows", "404")):
            log("fix workflow has no runs yet; treating as nothing in flight")
            return set(), True
        return set(), False
    keys = {
        (r.get("name") or "").split("[", 1)[-1].split("]", 1)[0]
        for r in runs
        if r.get("status") in {"queued", "in_progress"} and "[" in (r.get("name") or "")
    }
    return {k for k in keys if k}, True


def paths_claimed_by_open_prs(paths: set[str]) -> tuple[dict[str, int], bool]:
    """Map each spec path that an open e2e-repo PR already touches to that PR number.

    Deliberately author-agnostic — a `claude/*` branch from another agent, a human
    fix, and a bot PR all count. Filtering by author would reintroduce exactly the
    blindness this layer exists to close.

    A PR idle for longer than PATH_CLAIM_TTL_HOURS stops claiming its files, so a
    forgotten draft cannot hold a surface's spec files shut indefinitely.

    Returns (claims, ok). On any lookup failure `ok` is False and the caller
    suppresses: a missed dispatch retries on the next failing run, a duplicate PR
    editing the same lines does not un-happen.
    """
    claims: dict[str, int] = {}
    if not paths:
        return claims, True

    prs, err = gh_json_ex(
        [
            "pr", "list", "--repo", E2E_REPO, "--state", "open",
            "--limit", "100", "--json", "number,files,updatedAt",
        ],
        None,
    )
    if prs is None:
        log(f"::warning::open PR path scan failed for {E2E_REPO}: {err.strip()[:200]}")
        return claims, False
    if not isinstance(prs, list):
        return claims, False

    if len(prs) >= 100:
        log("::warning::open PR listing hit the page limit; suppressing this run")
        return claims, False

    now = datetime.now(timezone.utc)
    for pr in prs:
        number = pr.get("number")
        # Before the truncation check below, so a stale PR large enough to truncate
        # cannot suppress the run either — it claims nothing, so its file list does
        # not need to be complete.
        if planning.pr_lock_expired(
            pr.get("updatedAt") or "", now, PATH_CLAIM_TTL_HOURS
        ):
            log(
                f"path claim expired after {PATH_CLAIM_TTL_HOURS}h: {E2E_REPO}#{number} "
                "no longer claims the spec files it touches"
            )
            continue
        files = pr.get("files") or []
        # gh caps the per-PR file list. A truncated list under-reports claims, which
        # would read as "nothing claimed" — the one wrong answer this layer must not
        # give, so treat it as a failed lookup.
        if len(files) >= 100:
            log(f"::warning::PR #{number} file list is truncated; suppressing this run")
            return claims, False
        for entry in files:
            name = (entry.get("path") or "").strip()
            if name in paths and name not in claims:
                claims[name] = number
    return claims, True


def product_bug_fingerprints() -> set[str]:
    issues = gh_json(
        [
            "search", "issues", "nightly-product-bug is:issue",
            "--owner", "camunda", "--state", "open",
            "--limit", "200", "--json", "body",
        ],
        [],
    )
    out: set[str] = set()
    for issue in issues if isinstance(issues, list) else []:
        for line in (issue.get("body") or "").splitlines():
            if "nightly-product-bug fp=" in line:
                out.add(line.split("fp=", 1)[1].strip()[:8])
    return out


# ---------------------------------------------------------------------------
# Blame
# ---------------------------------------------------------------------------


def resolve_blame(head_sha: str) -> classify.Blame:
    prs = gh_json(["api", f"repos/{REPO}/commits/{head_sha}/pulls"], [])
    if not isinstance(prs, list):
        prs = []

    def lookup(number: int):
        return gh_json(
            ["api", f"repos/{REPO}/pulls/{number}"], {}
        )

    return classify.resolve_blame(head_sha=head_sha, prs=prs, lookup_pr=lookup)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------


def build_candidates(run_id: str, base_ref: str, workdir: Path):
    candidates: list[planning.Candidate] = []
    noise: list[tuple[str, str]] = []

    for job in failing_jobs(run_id):
        name = job.get("name") or ""
        surface = classify.surface_for_job(name)
        if surface is None:
            continue

        # The job's own conclusion, not a hardcoded "failure". noise_verdict only ever
        # calls a `failure` noise, so a cancelled or timed-out job passed as "failure"
        # is matched against the cancellation marker and discarded as NOISE_CANCELLED --
        # which is precisely the job-timeout case failing_jobs now goes out of its way
        # to keep.
        verdict = classify.noise_verdict(
            conclusion=job.get("conclusion") or "",
            step_count=len(job.get("steps") or []),
            failure_annotations=failure_annotations(job.get("check_run_url")),
        )
        if verdict:
            noise.append((classify.job_leaf_name(name), verdict))
            log(f"noise ({verdict}): {classify.job_leaf_name(name)}")
            continue

        if surface == classify.SURFACE_SM_E2E:
            candidates.append(sm_candidates(run_id, base_ref, name, workdir))
        elif surface == classify.SURFACE_SAAS_E2E:
            candidates.append(saas_candidate(run_id, base_ref, name, workdir))
        else:
            candidates.append(
                planning.Candidate(
                    base_ref=base_ref, surface=surface, job_name=name, job_level=True,
                    source=SOURCE,
                    evidence_run_url=f"https://github.com/{REPO}/actions/runs/{run_id}",
                    evidence_repo=REPO,
                )
            )

    return candidates, noise


def serialise(result: planning.Plan, blame: classify.Blame, run_id: str) -> dict:
    return {
        "run_url": f"https://github.com/{REPO}/actions/runs/{run_id}",
        "blame": asdict(blame),
        "dispatches": [
            {
                "base_ref": c.base_ref,
                "surface": c.surface,
                "dispatch_key": c.key,
                "job_name": classify.job_leaf_name(c.job_name),
                "evidence_run_url": c.evidence_run_url,
                "evidence_repo": c.evidence_repo,
                "job_level": c.job_level,
                "fingerprints": c.fingerprints,
                "test_specs": [
                    {
                        "file": s.file,
                        "test_name": s.test_name,
                        "error": s.error,
                        "project": s.project,
                        "attempts": s.attempts,
                        "statuses": s.statuses,
                        "deterministic": s.deterministic,
                    }
                    for s in c.specs
                ],
            }
            for c in result.dispatches
        ],
        # `specs` matters for the surfaces that are classified but never dispatched --
        # saas-setup above all, whose whole value as a report is naming the setup spec
        # that failed. Without it the entry is a bare verdict and the reader has to go
        # back to the run to learn anything. Empty for a job-level suppression, which has
        # no spec to name.
        "suppressed": [
            {
                "surface": s.candidate.surface,
                "dispatch_key": s.candidate.key,
                "reason": s.reason,
                "detail": s.detail,
                "specs": sorted({sp.file for sp in s.candidate.specs}),
            }
            for s in result.suppressed
        ],
        "noise": [{"job": j, "verdict": v} for j, v in result.noise],
    }


def main() -> int:
    try:
        return _run()
    except DiscoveryError as exc:
        # Surfaced as an error, not a traceback: the workflow reads the step outcome and
        # says "triage failed" rather than "nothing to dispatch".
        log(f"::error::AlwaysGreen discovery failed: {exc}")
        return 1


def _run() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--run-id", required=True)
    ap.add_argument("--base-ref", required=True)
    ap.add_argument("--out", default="-")
    ap.add_argument("--max-dispatches", type=int, default=2)
    args = ap.parse_args()

    # Normalise before anything derives from it: the ref is part of every fingerprint
    # and is validated by the fix workflow.
    base_ref = classify.normalise_base_ref(args.base_ref)
    if base_ref != args.base_ref:
        log(f"normalised base_ref '{args.base_ref}' -> '{base_ref}'")

    with tempfile.TemporaryDirectory(prefix="alwaysgreen-") as tmp:
        workdir = Path(tmp)
        candidates, noise = build_candidates(args.run_id, base_ref, workdir)

        keys, keys_ok = inflight_keys()
        if not keys_ok:
            # Cannot prove nothing is running; suppress every candidate this tick.
            keys = {c.key for c in candidates}
            log("::warning::in-flight lookup failed; suppressing dispatch this run")

        covered, pr_keys, pr_keys_covered, dedupe_ok = dedupe_inputs()
        if not dedupe_ok:
            # Cannot prove what an open PR already covers, so suppress every candidate:
            # the key set and the coverage set must be one snapshot or a partial read
            # licenses a duplicate PR.
            pr_keys = {c.key for c in candidates}
            pr_keys_covered = set()
            log("::warning::open fix PR lookup failed; suppressing dispatch this run")

        spec_paths = {s.file for c in candidates for s in c.specs if s.file}
        claimed, claimed_ok = paths_claimed_by_open_prs(spec_paths)
        if not claimed_ok:
            # Cannot prove the files are free; treat every one as claimed.
            claimed = {path: 0 for path in spec_paths}
            log("::warning::open PR path scan failed; suppressing dispatch this run")

        result = planning.plan_dispatches(
            candidates,
            covered_fingerprints=covered,
            inflight_keys=keys,
            open_pr_keys=pr_keys,
            open_pr_keys_with_coverage=pr_keys_covered,
            product_bug_fingerprints=product_bug_fingerprints(),
            claimed_paths=claimed,
            max_dispatches=args.max_dispatches,
        )
        result.noise = noise

        run = gh_json(["api", f"repos/{REPO}/actions/runs/{args.run_id}"], {})
        blame = resolve_blame(run.get("head_sha") or "")

        payload = serialise(result, blame, args.run_id)

    text = json.dumps(payload, indent=2)
    if args.out == "-":
        print(text)
    else:
        Path(args.out).write_text(text + "\n")
    log(
        f"dispatches={len(payload['dispatches'])} "
        f"suppressed={len(payload['suppressed'])} noise={len(payload['noise'])}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
