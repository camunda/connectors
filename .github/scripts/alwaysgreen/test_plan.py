"""Unit tests for AlwaysGreen dispatch planning and the PR coverage block."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone

import classify
import plan


def _spec(name="t", file="tests/SM-8.10/smoke-tests.spec.ts", deterministic=True):
    statuses = ["failed"] * 3 if deterministic else ["failed", "passed"]
    return classify.FailingSpec(file=file, test_name=name, statuses=statuses)


def _cand(
    surface=classify.SURFACE_SM_E2E,
    base_ref="main",
    specs=None,
    job_level=False,
    source="",
):
    return plan.Candidate(
        base_ref=base_ref,
        surface=surface,
        job_name="Playwright e2e after install - install on gke - agrn (1 of 1)",
        source=source,
        specs=list(specs if specs is not None else [_spec()]),
        job_level=job_level,
    )


def _plan(cands, **kw):
    kw.setdefault("covered_fingerprints", set())
    kw.setdefault("inflight_keys", set())
    kw.setdefault("open_pr_keys", set())
    kw.setdefault("product_bug_fingerprints", set())
    return plan.plan_dispatches(cands, **kw)


# ---------------------------------------------------------------------------
# Dispatch decisions
# ---------------------------------------------------------------------------


def test_dispatchable_surface_with_specs_is_dispatched():
    result = _plan([_cand()])
    assert len(result.dispatches) == 1
    assert result.suppressed == []


def test_non_dispatchable_surface_is_recorded_not_dispatched():
    result = _plan([_cand(surface=classify.SURFACE_HELM_INSTALL)])
    assert result.dispatches == []
    assert result.suppressed[0].reason == plan.SUPPRESSED_NOT_DISPATCHABLE


def test_in_flight_agent_blocks_the_same_surface():
    # The 2026-07-23 case: consecutive runs, same cause, agent still working.
    cand = _cand()
    result = _plan([cand], inflight_keys={"main:sm-smoke-e2e"})
    assert result.dispatches == []
    assert result.suppressed[0].reason == plan.SUPPRESSED_IN_FLIGHT


def test_in_flight_on_another_branch_does_not_block():
    result = _plan([_cand(base_ref="main")], inflight_keys={"stable/8.9:sm-smoke-e2e"})
    assert len(result.dispatches) == 1


def test_in_flight_on_another_surface_does_not_block():
    result = _plan([_cand()], inflight_keys={"main:saas-smoke-e2e"})
    assert len(result.dispatches) == 1


def test_fully_covered_candidate_is_suppressed():
    cand = _cand()
    result = _plan([cand], covered_fingerprints=set(cand.spec_fingerprints))
    assert result.dispatches == []
    assert result.suppressed[0].reason == plan.SUPPRESSED_PR_COVERED


def test_partially_covered_candidate_dispatches_only_uncovered_specs():
    covered_spec = _spec(name="already fixed")
    fresh_spec = _spec(name="new failure")
    cand = _cand(specs=[covered_spec, fresh_spec])
    covered_fp = classify.spec_fingerprint(
        "main", classify.SURFACE_SM_E2E, covered_spec.file, covered_spec.test_name
    )

    result = _plan([cand], covered_fingerprints={covered_fp})
    assert len(result.dispatches) == 1
    assert [s.test_name for s in result.dispatches[0].specs] == ["new failure"]


def test_product_bug_suppresses_the_candidate():
    cand = _cand()
    result = _plan([cand], product_bug_fingerprints=set(cand.spec_fingerprints))
    assert result.dispatches == []
    assert result.suppressed[0].reason == plan.SUPPRESSED_PRODUCT_BUG


def test_candidate_with_no_specs_is_suppressed_as_no_evidence():
    result = _plan([_cand(specs=[])])
    assert result.dispatches == []
    assert result.suppressed[0].reason == plan.SUPPRESSED_NO_EVIDENCE


def test_job_level_candidate_dispatches_without_specs():
    result = _plan([_cand(specs=[], job_level=True)])
    assert len(result.dispatches) == 1


def test_cap_limits_dispatches_and_records_the_rest():
    cands = [
        _cand(surface=classify.SURFACE_SM_E2E),
        _cand(surface=classify.SURFACE_SAAS_E2E),
    ]
    result = _plan(cands, max_dispatches=1)
    assert len(result.dispatches) == 1
    assert result.suppressed[0].reason == plan.SUPPRESSED_CAP


def test_in_flight_is_checked_before_cap_so_the_reason_is_useful():
    cands = [
        _cand(surface=classify.SURFACE_SM_E2E),
        _cand(surface=classify.SURFACE_SAAS_E2E),
    ]
    result = _plan(cands, inflight_keys={"main:sm-smoke-e2e"}, max_dispatches=1)
    reasons = {s.reason for s in result.suppressed}
    assert plan.SUPPRESSED_IN_FLIGHT in reasons
    assert len(result.dispatches) == 1
    assert result.dispatches[0].surface == classify.SURFACE_SAAS_E2E


# ---------------------------------------------------------------------------
# Fingerprint identity
# ---------------------------------------------------------------------------


def test_dispatch_key_shape():
    assert plan.dispatch_key("main", "sm-smoke-e2e") == "main:sm-smoke-e2e"


def test_job_level_candidate_uses_a_job_fingerprint():
    cand = _cand(specs=[], job_level=True)
    assert cand.fingerprints == [
        classify.job_fingerprint("main", classify.SURFACE_SM_E2E, cand.job_name)
    ]


def test_deterministic_specs_filter():
    cand = _cand(specs=[_spec("a"), _spec("b", deterministic=False)])
    assert [s.test_name for s in cand.deterministic_specs] == ["a"]


# ---------------------------------------------------------------------------
# Coverage block
# ---------------------------------------------------------------------------


def test_parse_coverage_block():
    body = "Some description\n\n<!-- alwaysgreen-fixed\nfp=aaaaaaaa\nfp=bbbbbbbb\n-->\n"
    assert plan.parse_coverage_block(body) == {"aaaaaaaa", "bbbbbbbb"}


def test_parse_returns_empty_when_absent():
    assert plan.parse_coverage_block("no block here") == set()
    assert plan.parse_coverage_block(None) == set()


def test_parse_returns_empty_for_a_block_that_claims_nothing():
    # The marker alone is not a statement of remit: discover reads an empty result as
    # "claims no specs" and keeps the coarse surface lock, same as a missing block.
    assert plan.parse_coverage_block("Body\n\n<!-- alwaysgreen-fixed\n-->\n") == set()
    assert plan.parse_coverage_block("Body\n\n<!-- alwaysgreen-fixed\nfp=\n-->\n") == set()


def test_merge_appends_block_when_missing():
    merged = plan.merge_coverage_block("Body text", {"aaaaaaaa"})
    assert "fp=aaaaaaaa" in merged
    assert merged.startswith("Body text")


def test_merge_is_a_union_and_never_drops_existing():
    body = "Body\n\n<!-- alwaysgreen-fixed\nfp=aaaaaaaa\n-->\n"
    merged = plan.merge_coverage_block(body, {"bbbbbbbb"})
    assert plan.parse_coverage_block(merged) == {"aaaaaaaa", "bbbbbbbb"}


def test_merge_is_idempotent():
    body = plan.merge_coverage_block("Body", {"aaaaaaaa"})
    again = plan.merge_coverage_block(body, {"aaaaaaaa"})
    assert plan.parse_coverage_block(again) == {"aaaaaaaa"}
    assert again.count(plan.COVERAGE_BEGIN) == 1


def test_merge_preserves_text_after_the_block():
    body = "Head\n\n<!-- alwaysgreen-fixed\nfp=aaaaaaaa\n-->\n\nTail text"
    merged = plan.merge_coverage_block(body, {"bbbbbbbb"})
    assert "Head" in merged and "Tail text" in merged
    assert plan.parse_coverage_block(merged) == {"aaaaaaaa", "bbbbbbbb"}


def test_render_is_sorted_for_stable_diffs():
    rendered = plan.render_coverage_block({"cccccccc", "aaaaaaaa", "bbbbbbbb"})
    assert rendered.index("aaaaaaaa") < rendered.index("bbbbbbbb") < rendered.index("cccccccc")


def test_open_fix_pr_blocks_the_same_surface():
    cand = _cand(surface=classify.SURFACE_SM_E2E)
    result = _plan([cand], open_pr_keys={"main:sm-smoke-e2e"})
    assert result.dispatches == []
    assert [s.reason for s in result.suppressed] == [plan.SUPPRESSED_PR_OPEN]


def test_open_fix_pr_on_another_surface_does_not_block():
    cand = _cand(surface=classify.SURFACE_SM_E2E)
    result = _plan([cand], open_pr_keys={"main:saas-smoke-e2e"})
    assert len(result.dispatches) == 1


def test_open_fix_pr_blocks_even_when_the_body_claims_nothing():
    # The coverage block is agent-written, so an empty one must not let a second
    # agent through while the first PR is still open.
    cand = _cand(surface=classify.SURFACE_SM_E2E)
    result = _plan([cand], covered_fingerprints=set(), open_pr_keys={"main:sm-smoke-e2e"})
    assert result.dispatches == []


def test_open_fix_pr_with_a_coverage_block_does_not_block_an_unclaimed_spec():
    # A surface carries independent causes: the PR claims one spec, and its
    # neighbour must still reach an agent rather than wait for a human to merge.
    claimed = _spec(name="already fixed")
    fresh = _spec(name="new failure", file="tests/SM-8.10/other-tests.spec.ts")
    cand = _cand(surface=classify.SURFACE_SM_E2E, specs=[claimed, fresh])
    claimed_fp = classify.spec_fingerprint(
        "main", classify.SURFACE_SM_E2E, claimed.file, claimed.test_name
    )

    result = _plan(
        [cand],
        covered_fingerprints={claimed_fp},
        open_pr_keys={"main:sm-smoke-e2e"},
        open_pr_keys_with_coverage={"main:sm-smoke-e2e"},
    )
    assert len(result.dispatches) == 1
    assert [s.test_name for s in result.dispatches[0].specs] == ["new failure"]


def test_open_fix_pr_with_a_coverage_block_still_suppresses_the_specs_it_claims():
    cand = _cand(surface=classify.SURFACE_SM_E2E)
    result = _plan(
        [cand],
        covered_fingerprints=set(cand.spec_fingerprints),
        open_pr_keys={"main:sm-smoke-e2e"},
        open_pr_keys_with_coverage={"main:sm-smoke-e2e"},
    )
    assert result.dispatches == []
    assert [s.reason for s in result.suppressed] == [plan.SUPPRESSED_PR_COVERED]


def test_a_second_holder_without_a_coverage_block_keeps_the_surface_locked():
    # `keys_with_coverage` is the intersection over holders, so one PR that claims
    # nothing still locks the surface even beside one that claims a spec.
    cand = _cand(surface=classify.SURFACE_SM_E2E)
    result = _plan(
        [cand],
        open_pr_keys={"main:sm-smoke-e2e"},
        open_pr_keys_with_coverage=set(),
    )
    assert result.dispatches == []
    assert [s.reason for s in result.suppressed] == [plan.SUPPRESSED_PR_OPEN]


def test_in_flight_agent_still_blocks_a_coverage_declaring_surface():
    # Narrowing the PR lock must not touch the concurrency rule: one agent per key.
    cand = _cand(surface=classify.SURFACE_SM_E2E)
    result = _plan(
        [cand],
        inflight_keys={"main:sm-smoke-e2e"},
        open_pr_keys={"main:sm-smoke-e2e"},
        open_pr_keys_with_coverage={"main:sm-smoke-e2e"},
    )
    assert result.dispatches == []
    assert [s.reason for s in result.suppressed] == [plan.SUPPRESSED_IN_FLIGHT]


# ---------------------------------------------------------------------------
# PR lock expiry
# ---------------------------------------------------------------------------

NOW = datetime(2026, 8, 26, 12, 0, tzinfo=timezone.utc)


def _ago(**kw):
    return (NOW - timedelta(**kw)).isoformat().replace("+00:00", "Z")


def test_fresh_fix_pr_keeps_holding_its_key():
    assert plan.pr_lock_expired(_ago(minutes=30), NOW, 2) is False


def test_fix_pr_past_the_ttl_releases_its_key():
    # Nothing but a merge or close used to release the label, so an unreviewed fix PR
    # wedged its surface shut indefinitely.
    assert plan.pr_lock_expired("2026-08-20T14:56:44Z", NOW, 2) is True


def test_ttl_is_read_in_hours_not_days():
    assert plan.pr_lock_expired(_ago(hours=6), NOW, 2) is True
    assert plan.pr_lock_expired(_ago(hours=6), NOW, plan.PR_LOCK_TTL_HOURS) is True


def test_ttl_boundary_is_inclusive_of_the_lock():
    assert plan.pr_lock_expired(_ago(hours=2), NOW, 2) is False
    assert plan.pr_lock_expired(_ago(hours=2, minutes=1), NOW, 2) is True


def test_unreadable_timestamp_keeps_the_lock():
    for value in ("", None, "yesterday", "2026-13-45T00:00:00Z"):
        assert plan.pr_lock_expired(value, NOW, 2) is False


def test_naive_created_at_is_read_as_utc():
    assert plan.pr_lock_expired("2026-08-20T14:56:44", NOW, 2) is True


def test_naive_now_does_not_raise_against_an_offset_aware_created_at():
    naive_now = NOW.replace(tzinfo=None)
    assert plan.pr_lock_expired("2026-08-20T14:56:44Z", naive_now, 2) is True
    assert plan.pr_lock_expired(_ago(minutes=30), naive_now, 2) is False


def test_zero_ttl_restores_the_never_expiring_lock():
    assert plan.pr_lock_expired("2026-01-01T00:00:00Z", NOW, 0) is False


# ---------------------------------------------------------------------------
# Source namespacing
# ---------------------------------------------------------------------------


def test_dispatch_key_is_namespaced_by_source():
    assert (
        plan.dispatch_key("main", "sm-smoke-e2e", "connectors")
        == "connectors:main:sm-smoke-e2e"
    )


def test_two_sources_on_the_same_branch_do_not_suppress_each_other():
    # Both pipelines open PRs into the same e2e repository and both call their branch
    # "main", so an un-namespaced key would make either one's agent block the other's.
    connectors = _cand(source="connectors")
    monorepo = _cand(source="camunda")
    assert connectors.key != monorepo.key

    result = _plan([monorepo], inflight_keys={connectors.key})
    assert len(result.dispatches) == 1


def test_key_labels_fit_githubs_length_limit():
    # A label over the limit is rejected silently by `gh label create`, and the missing
    # label disables dedupe instead of failing the run — so this is asserted, not hoped.
    #
    # The matrix is this copy's real key space, not a speculative one: `SOURCE` derives
    # from `REPO`, and alwaysgreen-fix rejects any dispatch key whose source is not
    # `connectors`, so no other value can reach a label. The widest real key today is
    # 46 characters, leaving four spare — enough that a longer surface name would trip
    # this rather than silently break dedupe in production.
    sources = ("connectors",)
    base_refs = ("main", "stable/8.7", "stable/8.8", "stable/8.9")
    for source in sources:
        for base_ref in base_refs:
            for surface in sorted(classify.DISPATCHABLE_SURFACES):
                label = plan.KEY_LABEL_PREFIX + plan.dispatch_key(
                    base_ref, surface, source
                )
                assert len(label) <= plan.MAX_LABEL_LENGTH, label


# ---------------------------------------------------------------------------
# Fifth dedupe layer: spec paths already open in a PR
# ---------------------------------------------------------------------------


def test_open_pr_touching_a_spec_path_suppresses_the_candidate():
    cand = _cand(specs=[_spec(file="tests/8.10/smoke-tests.spec.ts")])
    result = _plan([cand], claimed_paths={"tests/8.10/smoke-tests.spec.ts": 2951})
    assert result.dispatches == []
    assert result.suppressed[0].reason == plan.SUPPRESSED_PATH_CLAIMED
    assert "#2951" in result.suppressed[0].detail


def test_a_claimed_path_does_not_drop_the_fresh_specs_beside_it():
    # The interaction the narrowed surface lock exists for: an open PR still holds the
    # spec file it is fixing, that spec keeps failing until the PR merges, and a new
    # failure lands beside it. Suppressing the whole candidate on the first path hit
    # cancelled the widened lock out.
    claimed = _spec(name="still failing", file="tests/8.10/smoke-tests.spec.ts")
    fresh = _spec(name="new failure", file="tests/8.10/other-tests.spec.ts")
    cand = _cand(specs=[claimed, fresh])

    result = _plan(
        [cand],
        open_pr_keys={cand.key},
        open_pr_keys_with_coverage={cand.key},
        claimed_paths={"tests/8.10/smoke-tests.spec.ts": 2951},
    )
    assert len(result.dispatches) == 1
    assert [s.test_name for s in result.dispatches[0].specs] == ["new failure"]


def test_a_candidate_whose_every_spec_is_path_claimed_is_still_suppressed():
    claimed = _spec(name="still failing", file="tests/8.10/smoke-tests.spec.ts")
    cand = _cand(specs=[claimed])
    result = _plan([cand], claimed_paths={"tests/8.10/smoke-tests.spec.ts": 2951})
    assert result.dispatches == []
    assert result.suppressed[0].reason == plan.SUPPRESSED_PATH_CLAIMED
    assert "#2951" in result.suppressed[0].detail


def test_specs_dropped_by_more_than_one_source_report_all_of_them():
    # Reporting a mixed remainder as one source hid the others, so the summary named
    # the wrong blocker.
    covered = _spec(name="covered", file="tests/8.10/a.spec.ts")
    path = _spec(name="path claimed", file="tests/8.10/b.spec.ts")
    cand = _cand(specs=[covered, path])
    covered_fp = classify.spec_fingerprint(
        "main", classify.SURFACE_SM_E2E, covered.file, covered.test_name
    )

    result = _plan(
        [cand],
        covered_fingerprints={covered_fp},
        claimed_paths={"tests/8.10/b.spec.ts": 2951},
    )
    assert result.dispatches == []
    assert result.suppressed[0].reason == plan.SUPPRESSED_ALL_ACCOUNTED
    assert plan.SUPPRESSED_PR_COVERED in result.suppressed[0].detail
    assert plan.SUPPRESSED_PATH_CLAIMED in result.suppressed[0].detail


def test_a_sibling_version_path_does_not_shadow_the_failing_one():
    # Matching on basenames would make an open PR against 8.9 suppress an 8.10 failure.
    cand = _cand(specs=[_spec(file="tests/8.10/smoke-tests.spec.ts")])
    result = _plan([cand], claimed_paths={"tests/8.9/smoke-tests.spec.ts": 2951})
    assert len(result.dispatches) == 1


def test_job_level_candidates_are_not_path_claimed():
    cand = _cand(specs=[], job_level=True)
    result = _plan([cand], claimed_paths={"tests/8.10/smoke-tests.spec.ts": 1})
    assert len(result.dispatches) == 1


def test_path_claim_is_checked_after_evidence_so_the_reason_is_useful():
    cand = _cand(specs=[])
    result = _plan([cand], claimed_paths={"tests/8.10/smoke-tests.spec.ts": 1})
    assert result.suppressed[0].reason == plan.SUPPRESSED_NO_EVIDENCE
