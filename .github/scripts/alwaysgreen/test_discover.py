"""Unit tests for the dedupe snapshot discover hands to the planner.

`test_plan.py` passes `open_pr_keys_with_coverage` in ready-made, so it asserts what
`plan` does with the answer, never how it is computed. The derivation is where the
"does this whole surface stay locked" decision is actually made — per-PR key grouping,
the TTL skip, and the intersection over holders — so it is tested here against a stubbed
`open_fix_prs`.
"""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from pathlib import Path

import classify
import discover
import plan as planning

NOW = datetime.now(timezone.utc)


def _ago(**kw):
    return (NOW - timedelta(**kw)).isoformat().replace("+00:00", "Z")


def _pr(number, keys, *, claims=(), age_hours=0):
    body = ""
    if claims:
        body = f"Fixes.\n\n{planning.render_coverage_block(set(claims))}\n"
    return {
        "number": number,
        "body": body,
        "createdAt": _ago(hours=age_hours),
        "labels": [{"name": f"{discover.KEY_LABEL_PREFIX}{k}"} for k in keys],
    }


def _stub(monkeypatch, prs, *, ok=True):
    """Serve `prs` from the first fix repo and nothing from the rest.

    Keyed on the repo, not on a call counter: a counter is consumed by the first
    `dedupe_inputs` pass over FIX_PR_REPOS, so a second call in the same test would be
    served empty lists and any assertion about it would pass vacuously.
    """

    def fake(repo):
        return (list(prs), ok) if repo == discover.FIX_PR_REPOS[0] else ([], ok)

    monkeypatch.setattr(discover, "open_fix_prs", fake)


def test_a_claiming_holder_frees_its_key_for_other_specs(monkeypatch):
    _stub(monkeypatch, [_pr(1, ["connectors:main:sm-smoke-e2e"], claims=["aaaaaaaa"])])
    covered, keys, per_spec, ok = discover.dedupe_inputs()
    assert ok is True
    assert covered == {"aaaaaaaa"}
    assert keys == {"connectors:main:sm-smoke-e2e"}
    assert per_spec == {"connectors:main:sm-smoke-e2e"}


def test_a_holder_claiming_nothing_keeps_its_key_locked(monkeypatch):
    _stub(monkeypatch, [_pr(1, ["connectors:main:sm-smoke-e2e"])])
    covered, keys, per_spec, ok = discover.dedupe_inputs()
    assert covered == set()
    assert keys == {"connectors:main:sm-smoke-e2e"}
    assert per_spec == set()


def test_an_empty_coverage_block_claims_nothing(monkeypatch):
    # The marker alone is not a statement of remit, so it must not free the surface.
    pr = _pr(1, ["connectors:main:sm-smoke-e2e"])
    pr["body"] = f"Fixes.\n\n{planning.COVERAGE_BEGIN}\nfp=\n{planning.COVERAGE_END}\n"
    _stub(monkeypatch, [pr])
    _covered, keys, per_spec, _ok = discover.dedupe_inputs()
    assert keys == {"connectors:main:sm-smoke-e2e"}
    assert per_spec == set()


def test_one_non_claiming_holder_locks_a_key_another_holder_claims(monkeypatch):
    # The intersection over holders: this is the case a per-PR view gets wrong.
    _stub(
        monkeypatch,
        [
            _pr(1, ["connectors:main:sm-smoke-e2e"], claims=["aaaaaaaa"]),
            _pr(2, ["connectors:main:sm-smoke-e2e"]),
        ],
    )
    covered, keys, per_spec, _ok = discover.dedupe_inputs()
    assert covered == {"aaaaaaaa"}
    assert keys == {"connectors:main:sm-smoke-e2e"}
    assert per_spec == set()


def test_an_expired_non_claiming_holder_does_not_lock_a_claiming_one(monkeypatch):
    # Only ACTIVE holders decide the key, so an expired PR cannot veto a fresh one.
    _stub(
        monkeypatch,
        [
            _pr(1, ["connectors:main:sm-smoke-e2e"], claims=["aaaaaaaa"]),
            _pr(2, ["connectors:main:sm-smoke-e2e"], age_hours=99),
        ],
    )
    _covered, keys, per_spec, _ok = discover.dedupe_inputs()
    assert keys == {"connectors:main:sm-smoke-e2e"}
    assert per_spec == {"connectors:main:sm-smoke-e2e"}


def test_an_expired_holder_releases_its_key_but_keeps_its_claims(monkeypatch):
    # A coverage-block fingerprint is the PR's stated remit and has no TTL, so the
    # failure it claims to fix stays suppressed per spec even once the key lock is gone.
    # This is about the block's contents, not about the files the PR touches, which
    # expire separately (see the spec-path claim tests below).
    _stub(monkeypatch, [_pr(1, ["connectors:main:sm-smoke-e2e"], claims=["aaaaaaaa"], age_hours=99)])
    covered, keys, per_spec, _ok = discover.dedupe_inputs()
    assert covered == {"aaaaaaaa"}
    assert keys == set()
    assert per_spec == set()


def test_a_pr_carrying_two_key_labels_holds_both(monkeypatch):
    _stub(
        monkeypatch,
        [_pr(1, ["connectors:main:sm-smoke-e2e", "connectors:main:saas-smoke-e2e"], claims=["aaaaaaaa"])],
    )
    _covered, keys, per_spec, _ok = discover.dedupe_inputs()
    assert keys == {"connectors:main:sm-smoke-e2e", "connectors:main:saas-smoke-e2e"}
    assert per_spec == keys


def test_a_pr_with_no_key_label_still_contributes_its_claims(monkeypatch):
    # A fix PR whose key label was never stamped: it locks nothing, but the specs it
    # claims must still suppress a repeat.
    _stub(monkeypatch, [_pr(1, [], claims=["aaaaaaaa"])])
    covered, keys, per_spec, _ok = discover.dedupe_inputs()
    assert covered == {"aaaaaaaa"}
    assert keys == set()
    assert per_spec == set()


def test_a_failed_lookup_reports_not_ok(monkeypatch):
    # Coverage and keys are one snapshot behind one `ok`. A partial read must not let
    # the caller skip the coarse lock while believing nothing is claimed.
    _stub(monkeypatch, [_pr(1, ["connectors:main:sm-smoke-e2e"], claims=["aaaaaaaa"])], ok=False)
    _covered, _keys, _per_spec, ok = discover.dedupe_inputs()
    assert ok is False


SPEC = "tests/8.10/smoke-tests.spec.ts"


def _stub_pr_list(monkeypatch, prs, *, err=""):
    """Serve `prs` as the open-PR listing behind the spec-path claim lookup."""

    def fake(args, default):
        return (None, err) if err else (list(prs), "")

    monkeypatch.setattr(discover, "gh_json_ex", fake)


def _claiming_pr(number, *, idle_hours=0, files=(SPEC,)):
    return {
        "number": number,
        "files": [{"path": f} for f in files],
        "updatedAt": _ago(hours=idle_hours),
    }


def test_a_live_pr_claims_the_spec_files_it_touches(monkeypatch):
    _stub_pr_list(monkeypatch, [_claiming_pr(3153)])
    claims, ok = discover.paths_claimed_by_open_prs({SPEC})
    assert claims == {SPEC: 3153}
    assert ok is True


def test_an_idle_pr_stops_claiming_its_spec_files(monkeypatch):
    # The case that wedged run 33727363856: a draft untouched for eight days still held
    # every spec file it touched, so every failure in those files went undispatched.
    _stub_pr_list(monkeypatch, [_claiming_pr(3153, idle_hours=8 * 24)])
    claims, ok = discover.paths_claimed_by_open_prs({SPEC})
    assert claims == {}
    assert ok is True


def test_an_idle_pr_with_a_truncated_file_list_does_not_suppress(monkeypatch):
    # Truncation is only a problem for a PR whose claims still count. Checking the TTL
    # first keeps a large stale PR from failing the lookup for everyone.
    files = [f"tests/8.10/spec-{i}.spec.ts" for i in range(120)]
    _stub_pr_list(monkeypatch, [_claiming_pr(3153, idle_hours=8 * 24, files=files)])
    claims, ok = discover.paths_claimed_by_open_prs({SPEC})
    assert claims == {}
    assert ok is True


def test_a_live_pr_with_a_truncated_file_list_still_suppresses(monkeypatch):
    files = [f"tests/8.10/spec-{i}.spec.ts" for i in range(120)]
    _stub_pr_list(monkeypatch, [_claiming_pr(3153, files=files)])
    _claims, ok = discover.paths_claimed_by_open_prs({SPEC})
    assert ok is False


def test_a_missing_timestamp_keeps_the_claim(monkeypatch):
    # Same bias as the key lock: an unproven state suppresses rather than risking two
    # agents rewriting one spec.
    pr = _claiming_pr(3153)
    pr["updatedAt"] = ""
    _stub_pr_list(monkeypatch, [pr])
    claims, ok = discover.paths_claimed_by_open_prs({SPEC})
    assert claims == {SPEC: 3153}
    assert ok is True


def test_a_failed_pr_listing_suppresses(monkeypatch):
    _stub_pr_list(monkeypatch, [], err="gh: not found")
    _claims, ok = discover.paths_claimed_by_open_prs({SPEC})
    assert ok is False


# ---------------------------------------------------------------------------
# SaaS sub-classification: which surfaces keep their evidence
# ---------------------------------------------------------------------------


def _saas_report(files):
    """A downstream Playwright report whose listed spec files all failed."""
    return {
        "config": {"rootDir": "/home/runner/work/x/x/tests/8.10"},
        "suites": [
            {
                "suites": [
                    {
                        "specs": [
                            {
                                "file": f,
                                "title": f"t {f}",
                                "ok": False,
                                "tests": [
                                    {
                                        "projectName": "chromium-v2",
                                        "results": [
                                            {"status": "failed", "error": {"message": "boom"}}
                                        ],
                                    }
                                ],
                            }
                            for f in files
                        ]
                    }
                ]
            }
        ],
    }


def _stub_saas(monkeypatch, files):
    """Drive saas_candidate off a synthetic downstream report."""
    monkeypatch.setattr(discover, "download_artifacts", lambda *a, **k: True)
    monkeypatch.setattr(
        discover,
        "read_json_files",
        lambda root, name: (
            [{"downstream_run_url": "https://github.com/o/r/actions/runs/9"}]
            if name.startswith("alwaysgreen-saas-category")
            else [_saas_report(files)]
        ),
    )
    monkeypatch.setattr(
        discover,
        "gh_json_ex",
        lambda args, default: ({"artifacts": [{"name": "json-report-v2"}]}, ""),
    )


def test_a_provisioning_failure_keeps_its_setup_spec_as_evidence(monkeypatch):
    # Report evidence, not a dispatch payload: this surface is reported-only, and the
    # spec is what makes the report worth reading. That it survives into the emitted
    # payload is asserted separately, on the serialised plan.
    _stub_saas(monkeypatch, ["tests/8.10/test-setup.spec.ts"])
    cand = discover.saas_candidate("1", "main", "Trigger SaaS E2E tests", Path("/tmp/x"))
    assert cand.surface == planning.classify.SURFACE_SAAS_PROVISIONING
    assert [s.file for s in cand.specs] == ["tests/8.10/test-setup.spec.ts"]


def test_a_mixed_report_dispatches_the_real_spec_without_the_setup_one(monkeypatch):
    # Two remits must not land in one PR: the provisioning half returns on its own.
    _stub_saas(
        monkeypatch,
        ["tests/8.10/test-setup.spec.ts", "tests/8.10/smoke-tests.spec.ts"],
    )
    cand = discover.saas_candidate("1", "main", "Trigger SaaS E2E tests", Path("/tmp/x"))
    assert cand.surface == planning.classify.SURFACE_SAAS_E2E
    assert [s.file for s in cand.specs] == ["tests/8.10/smoke-tests.spec.ts"]


# ---------------------------------------------------------------------------
# The conclusion set: the scanner's gate and the classifier must agree
# ---------------------------------------------------------------------------


def _stub_run_and_jobs(monkeypatch, run_conclusion, jobs, *, pages=1):
    """Serve a run's metadata and its job list from one stubbed gh caller.

    The job listing comes back as a LIST of page objects, because failing_jobs asks for
    it with `--paginate --slurp`. `pages` splits the jobs across that many pages, which
    is the shape a run over 100 jobs actually produces.
    """

    def fake(args, default):
        path = args[-1]
        if path.endswith("/jobs?per_page=100"):
            per = max(1, -(-len(jobs) // pages))
            return ([{"jobs": jobs[i : i + per]} for i in range(0, len(jobs), per)] or [{"jobs": []}], "")
        return ({"conclusion": run_conclusion}, "")

    monkeypatch.setattr(discover, "gh_json_ex", fake)


def test_a_timed_out_job_in_a_completed_run_is_classified(monkeypatch):
    # The scan counts it, so this must too: a conclusion the gate counts and the
    # classifier drops sends triage looking for a candidate that was filtered out.
    _stub_run_and_jobs(
        monkeypatch,
        "success",
        [{"name": "Trigger SaaS E2E tests", "conclusion": "cancelled"}],
    )
    assert [j["name"] for j in discover.failing_jobs("1")] == ["Trigger SaaS E2E tests"]


def test_a_cancelled_job_in_a_cancelled_run_is_not_classified(monkeypatch):
    # The whole run was dropped by the queue or a human; that is not a test failure.
    _stub_run_and_jobs(
        monkeypatch,
        "cancelled",
        [{"name": "Trigger SaaS E2E tests", "conclusion": "cancelled"}],
    )
    assert discover.failing_jobs("1") == []


def test_a_real_failure_in_a_cancelled_run_is_still_classified(monkeypatch):
    _stub_run_and_jobs(
        monkeypatch,
        "cancelled",
        [
            {"name": "Trigger SaaS E2E tests", "conclusion": "cancelled"},
            {"name": "Playwright e2e full after install", "conclusion": "failure"},
        ],
    )
    assert [j["name"] for j in discover.failing_jobs("1")] == [
        "Playwright e2e full after install"
    ]


def test_an_unreadable_run_conclusion_keeps_the_wider_set(monkeypatch):
    # Over-including shows up in the summary as a candidate that may not dispatch;
    # under-including drops a real failure with nothing to look at.
    def fake(args, default):
        path = args[-1]
        if path.endswith("/jobs?per_page=100"):
            return ([{"jobs": [{"name": "x", "conclusion": "timed_out"}]}], "")
        return (None, "boom")

    monkeypatch.setattr(discover, "gh_json_ex", fake)
    assert [j["name"] for j in discover.failing_jobs("1")] == ["x"]


def test_jobs_beyond_the_first_page_are_still_classified(monkeypatch):
    # A single-page request silently truncates the run, so the classifier would report a
    # later-page failure as absent while the gate that invoked it had counted it.
    jobs = [{"name": f"j{i}", "conclusion": "success"} for i in range(120)]
    jobs[110] = {"name": "late-failure", "conclusion": "failure"}
    _stub_run_and_jobs(monkeypatch, "success", jobs, pages=2)
    assert [j["name"] for j in discover.failing_jobs("1")] == ["late-failure"]


def test_a_suppressed_surface_reports_its_spec_paths():
    # The point of a classified-but-undispatched surface is the report, and a reader who
    # only gets `reason` has to go back to the run to learn anything. Asserted on the
    # serialised payload, because that is what the artifact and the summary consume --
    # the candidate keeping its specs in memory proves nothing about what is emitted.
    spec = classify.FailingSpec(
        file="tests/8.10/test-setup.spec.ts", test_name="setup", error="boom"
    )
    cand = planning.Candidate(
        base_ref="main",
        surface=classify.SURFACE_SAAS_PROVISIONING,
        job_name="Trigger SaaS E2E tests",
        source="connectors",
        specs=[spec],
    )
    result = planning.plan_dispatches(
        [cand],
        covered_fingerprints=set(),
        inflight_keys=set(),
        open_pr_keys=set(),
        product_bug_fingerprints=set(),
    )
    assert result.dispatches == []
    payload = discover.serialise(result, classify.Blame(None, None, None, "none"), "1")
    assert payload["suppressed"][0]["specs"] == ["tests/8.10/test-setup.spec.ts"]


def test_a_job_level_suppression_reports_no_specs():
    cand = planning.Candidate(
        base_ref="main",
        surface=classify.SURFACE_HELM_INSTALL,
        job_name="install for install on gke - agrn",
        source="connectors",
        job_level=True,
    )
    result = planning.plan_dispatches(
        [cand],
        covered_fingerprints=set(),
        inflight_keys=set(),
        open_pr_keys=set(),
        product_bug_fingerprints=set(),
    )
    payload = discover.serialise(result, classify.Blame(None, None, None, "none"), "1")
    assert payload["suppressed"][0]["specs"] == []


# ---------------------------------------------------------------------------
# Classifying the attempt the gate judged, not whatever is newest
# ---------------------------------------------------------------------------


def test_the_run_path_is_pinned_to_the_attempt(monkeypatch):
    monkeypatch.setattr(discover, "RUN_ATTEMPT", "2")
    assert discover.run_path("7") == "repos/camunda/connectors/actions/runs/7/attempts/2"
    assert discover.run_path("7", "/jobs?per_page=100") == (
        "repos/camunda/connectors/actions/runs/7/attempts/2/jobs?per_page=100"
    )


def test_without_an_attempt_the_run_path_is_the_run(monkeypatch):
    # A replay has no attempt to be faithful to: latest is the right answer.
    monkeypatch.setattr(discover, "RUN_ATTEMPT", "")
    assert discover.run_path("7") == "repos/camunda/connectors/actions/runs/7"
    assert discover.run_path("7", "/jobs?per_page=100") == (
        "repos/camunda/connectors/actions/runs/7/jobs?per_page=100"
    )


def test_failing_jobs_asks_for_the_pinned_attempt(monkeypatch):
    # The whole point: the gate judged one attempt's jobs, so discovery must read that
    # attempt's jobs and that attempt's conclusion, not the newest ones.
    monkeypatch.setattr(discover, "RUN_ATTEMPT", "3")
    asked = []

    def fake(args, default):
        asked.append(args[-1])
        if args[-1].endswith("/jobs?per_page=100"):
            return ([{"jobs": [{"name": "x", "conclusion": "failure"}]}], "")
        return ({"conclusion": "success"}, "")

    monkeypatch.setattr(discover, "gh_json_ex", fake)
    assert [j["name"] for j in discover.failing_jobs("7")] == ["x"]
    assert all("/attempts/3" in path for path in asked), asked
