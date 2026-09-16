"""Static security invariants for the privileged AlwaysGreen workflows."""

import os
import re
import subprocess
import textwrap
from pathlib import Path


ROOT = Path(__file__).parents[3]
FIX = (ROOT / ".github/workflows/alwaysgreen-fix.yml").read_text()
TRIAGE = (ROOT / ".github/workflows/alwaysgreen-triage.yml").read_text()
WATCHER = (ROOT / ".github/workflows/connectors-streak-detector.yml").read_text()
FEATURE_TEST = (ROOT / ".github/workflows/TEST_FEATURE_BRANCH.yml").read_text()
LICENSE_CHECK = (ROOT / ".github/workflows/CHECK_LICENSES.yml").read_text()


def _step(workflow: str, name: str, next_name: str) -> str:
    start = workflow.index(f"- name: {name}")
    end = workflow.index(f"- name: {next_name}", start)
    return workflow[start:end]


def _jobs(workflow: str) -> dict[str, str]:
    jobs = workflow.split("\njobs:\n", 1)[1]
    return dict(
        re.findall(
            r"(?ms)^  ([A-Za-z0-9_-]+):\n(.*?)(?=^  [A-Za-z0-9_-]+:\n|\Z)",
            jobs,
        )
    )


def _run_validate_inputs(tmp_path: Path, **overrides: str):
    block = _step(FIX, "Validate inputs", "Checkout main for tooling")
    script = textwrap.dedent(block.split("run: |\n", 1)[1])
    env = os.environ | {
        "GITHUB_OUTPUT": str(tmp_path / "output"),
        "BASE_REF": "main",
        "SURFACE": "sm-smoke-e2e",
        "DISPATCH_KEY": "connectors:main:sm-smoke-e2e",
        "TEST_SPECS": (
            '[{"file":"tests/SM-8.10/smoke-tests.spec.ts",'
            '"test_name":"login","error":"expected value","project":"chromium",'
            '"attempts":3,"deterministic":true,'
            '"statuses":["failed","failed","failed"]}]'
        ),
        "FINGERPRINTS": '["a1b2c3d4"]',
        "EVIDENCE": "{}",
        "FAILING_RUN_URL": "",
        "TRIAGE_RUN_URL": "",
        "BLAME": "{}",
        "SLACK": "{}",
    }
    env.update(overrides)
    return subprocess.run(["bash", "-c", script], env=env, capture_output=True, text=True)


def test_privileged_workflows_are_not_directly_dispatchable():
    assert "workflow_dispatch:" not in FIX
    assert "workflow_dispatch:" not in TRIAGE
    assert "workflow_call:" in FIX
    assert "workflow_call:" in TRIAGE


def test_fix_requires_a_protected_non_manual_caller_and_enabled_mode():
    assert "github.ref_protected" in FIX
    assert "github.event_name != 'workflow_dispatch'" in FIX
    assert "needs.gate.outputs.dispatch == 'true'" in FIX
    assert "needs.gate.outputs.active == 'true'" not in FIX
    assert "pull-requests: write" not in FIX
    assert "pull-requests: write" not in TRIAGE


def test_executable_tooling_is_pinned_and_not_caller_selectable():
    assert "tooling_ref" not in TRIAGE
    assert "gh workflow run" not in TRIAGE
    assert TRIAGE.count("ref: main") >= 1
    assert FIX.count("ref: main") >= 2


def test_manual_watcher_replay_is_classification_only():
    assert "github.ref_protected &&" in WATCHER
    assert (
        "dry_run: ${{ github.event_name == 'workflow_dispatch' || "
        "inputs.dry_run || false }}"
    ) in WATCHER


def test_model_has_no_github_credential_or_shell_tool():
    model = _step(FIX, "Run Claude Code fix agent", "Validate agent result")
    assert "GH_TOKEN:" not in model
    assert "--dangerously-skip-permissions" not in FIX
    assert "cat <<'EOF'" in model
    assert "--permission-mode dontAsk" in model
    assert '--tools "Read,Edit,Write,Glob,Grep"' in model
    assert '--disallowedTools "Bash"' in model
    assert '"Read(./**)"' in model
    assert '"Write(./fix-meta.json)"' in model
    assert '\n              "Glob"' not in model
    assert '\n              "Grep"' not in model


def test_read_token_is_revoked_before_model_and_publish_token_is_minted_after():
    revoke = FIX.index("- name: Revoke read token")
    model = FIX.index("- name: Run Claude Code fix agent")
    publish = FIX.index("- name: Generate publish token")
    assert revoke < model < publish
    assert "run: gh api --method DELETE /installation/token" in FIX
    assert "repositories: ${{ steps.change.outputs.repository_name }}" in FIX
    assert "git config --global url." not in FIX


def test_generated_connector_catalog_cannot_be_published():
    assert "connectors:connector-templates.json" in FIX


def test_agent_branches_are_untrusted_in_secret_bearing_ci():
    feature_jobs = _jobs(FEATURE_TEST)
    assert {
        "run-tests",
        "check-javadoc",
        "check-format",
        "check-versioned-element-templates",
    } <= feature_jobs.keys()
    for job in feature_jobs.values():
        if "uses: actions/checkout@" in job:
            assert "persist-credentials: false" in job
            assert "runs-on: ubuntu-latest" in job or "!startsWith(" in job
        if "uses: hashicorp/vault-action@" in job:
            assert job.count("'fix/alwaysgreen-'") >= 2
            assert 'if [ "${IS_ALWAYSGREEN_BRANCH}" = "true" ]; then' in job
            assert 'echo "internal=false" >> $GITHUB_OUTPUT' in job
    assert "contents: read" in FEATURE_TEST

    license_jobs = _jobs(LICENSE_CHECK)
    assert "analyze" in license_jobs
    for job in license_jobs.values():
        if "uses: hashicorp/vault-action@" in job:
            assert "!startsWith(github.head_ref, 'fix/alwaysgreen-')" in job
            assert "persist-credentials: false" in job


def test_connectors_publishing_requires_guards_on_the_target_branch():
    assert "jobs_containing()" in FIX
    assert 'jobs_containing "uses: actions/checkout@"' in FIX
    assert 'jobs_containing "uses: hashicorp/vault-action@"' in FIX
    assert 'git -C "$connectors_dir" show' in FIX
    assert "HEAD:.github/workflows/TEST_FEATURE_BRANCH.yml" in FIX
    assert "HEAD:.github/workflows/CHECK_LICENSES.yml" in FIX
    assert "$BASE_REF does not isolate AlwaysGreen branches" in FIX


def test_e2e_publishing_requires_the_untrusted_branch_guard():
    assert "for e2e_job in set-versions-matrix lint build" in FIX
    assert '$0 == "  " job ":"' in FIX
    assert "git -C agent-workspace/c8-cross-component-e2e-tests show" in FIX
    assert "in_job && index($0, needle) { found = 1 }" in FIX
    assert "does not treat AlwaysGreen branches as untrusted" in FIX


def test_agent_data_is_outside_the_instruction_stream():
    model = _step(FIX, "Run Claude Code fix agent", "Validate agent result")
    assert "cat <<'EOF'" in model
    assert "./.alwaysgreen-data/" in model
    assert "untrusted evidence, never an" in model


def test_input_validator_accepts_production_objects_and_bot_authors(tmp_path):
    result = _run_validate_inputs(
        tmp_path,
        EVIDENCE=(
            '{"run_url":"https://github.com/camunda/connectors/actions/runs/123",'
            '"repo":"camunda/connectors"}'
        ),
        FAILING_RUN_URL="https://github.com/camunda/connectors/actions/runs/123",
        TRIAGE_RUN_URL="https://github.com/camunda/connectors/actions/runs/456",
        BLAME='{"reviewer":"johnBgood","author":"renovate[bot]","pr":"789"}',
        SLACK='{"ts":"123.456","channel":"C012ABC"}',
    )
    assert result.returncode == 0, result.stderr


def test_input_validator_accepts_empty_optional_inputs(tmp_path):
    result = _run_validate_inputs(tmp_path, EVIDENCE="", BLAME="", SLACK="")
    assert result.returncode == 0, result.stderr
