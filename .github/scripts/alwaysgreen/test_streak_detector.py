"""Tests for the streak job in connectors-streak-detector.yml.

Runs the workflow's own step scripts against a stubbed `gh`, so the assertions follow
the YAML rather than a copy of it.
"""

from __future__ import annotations

import json
import os
import subprocess
import textwrap
from pathlib import Path

import yaml

WORKFLOW = Path(__file__).resolve().parents[2] / "workflows/connectors-streak-detector.yml"
REPO = "camunda/connectors"
SHA = "a" * 40
CONNECTORS_MEDIC = "<connectors-medic>"

_WF = yaml.safe_load(WORKFLOW.read_text())
_STEPS = {s.get("id"): s["run"] for s in _WF["jobs"]["detect-streak"]["steps"] if s.get("id")}
_ENV = {k: str(v) for k, v in _WF["env"].items()}

GH_STUB = textwrap.dedent(
    r"""
    #!/usr/bin/env python3
    import json, os, re, subprocess, sys

    fixture = json.load(open(os.environ["GH_FIXTURE"]))
    args = sys.argv[1:]
    if args[:2] == ["run", "list"]:
        workflow = args[args.index("--workflow") + 1]
        key = "saas" if workflow == os.environ["SAAS_RESULT_WORKFLOW_FILE"] else "pipeline"
        print(json.dumps(fixture[key]))
    elif args[0] == "api":
        run_id = next(re.search(r"/runs/(\d+)/jobs", a).group(1) for a in args if "/jobs" in a)
        data = json.dumps({"jobs": fixture["jobs"].get(run_id, [])})
        if "--jq" in args:
            expr = args[args.index("--jq") + 1]
            sys.stdout.write(subprocess.run(["jq", "-r", expr], input=data, text=True,
                                            capture_output=True, check=True).stdout)
        else:
            print(data)
    else:
        sys.exit(f"unexpected gh call: {args}")
    """
).lstrip()


def pipeline_run(run_id: int, conclusion: str) -> dict:
    return {
        "databaseId": run_id,
        "conclusion": conclusion,
        "status": "completed",
        "event": "push",
        "createdAt": f"2026-10-16T10:{run_id % 60:02d}:00Z",
        "url": f"https://github.com/{REPO}/actions/runs/{run_id}",
    }


def saas_result(parent: int, conclusion: str, run_id: int, attempt: int = 1) -> dict:
    return {
        "conclusion": conclusion,
        "displayTitle": f"saas-e2e-result {REPO}:{parent}:{attempt}:{SHA}",
        "createdAt": f"2026-10-16T11:{run_id % 60:02d}:00Z",
        "databaseId": run_id,
        "url": f"https://github.com/{REPO}/actions/runs/{run_id}",
    }


def dispatched_trigger(attempt: int = 1) -> dict:
    return {
        "name": "Trigger SaaS E2E tests",
        "run_attempt": attempt,
        "conclusion": "success",
        "steps": [
            {"name": "Trigger repository_dispatch for SaaS Connectors", "conclusion": "success"}
        ],
    }


def failed_job(name: str) -> dict:
    return {"name": name, "conclusion": "failure"}


def run_step(tmp_path: Path, step: str, fixture: dict, env: dict) -> dict:
    bin_dir = tmp_path / "bin"
    bin_dir.mkdir(exist_ok=True)
    gh = bin_dir / "gh"
    gh.write_text(GH_STUB)
    gh.chmod(0o755)
    fixture_file = tmp_path / "fixture.json"
    fixture_file.write_text(json.dumps(fixture))
    output = tmp_path / f"{step}.out"
    output.write_text("")
    result = subprocess.run(
        ["bash", "-c", _STEPS[step]],
        env={
            **os.environ,
            **_ENV,
            **env,
            "PATH": f"{bin_dir}{os.pathsep}{os.environ['PATH']}",
            "GH_FIXTURE": str(fixture_file),
            "GITHUB_OUTPUT": str(output),
            "GITHUB_REPOSITORY": REPO,
            "GH_TOKEN": "unused",
            "CONNECTORS_MEDIC": CONNECTORS_MEDIC,
        },
        text=True,
        capture_output=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    return dict(line.split("=", 1) for line in output.read_text().splitlines() if "=" in line)


def detect(tmp_path: Path, fixture: dict) -> dict:
    out = run_step(tmp_path, "streak", fixture, {"BASE_REF": "main"})
    if out["is_streak"] == "true":
        medics = run_step(
            tmp_path,
            "medics",
            fixture,
            {"FAIL_RUN_ID": out["fail_run_id"], "FAIL_KIND": out["fail_kind"]},
        )
        out["pings"] = medics["pings"]
    return out


def test_saas_only_failures_point_at_the_failing_callback(tmp_path: Path):
    # Three green pipeline runs whose SaaS callbacks failed; the event that raises the
    # streak can be the green parent, which carries no failed jobs.
    parents = (101, 102, 103)
    out = detect(
        tmp_path,
        {
            "pipeline": [pipeline_run(i, "success") for i in parents],
            "saas": [saas_result(i, "failure", i + 400) for i in parents],
            "jobs": {str(i): [dispatched_trigger()] for i in parents},
        },
    )

    assert out["is_streak"] == "true"
    assert out["streak_count"] == "3"
    assert out["fail_kind"] == "saas"
    assert out["fail_run_id"] == "503"
    assert out["fail_run_url"] == f"https://github.com/{REPO}/actions/runs/503"
    assert out["pings"] == _ENV["MEDIC_TEST_AUTOMATION"]


def test_pipeline_failures_point_at_the_newest_failing_run(tmp_path: Path):
    out = detect(
        tmp_path,
        {
            "pipeline": [pipeline_run(i, "failure") for i in (101, 102, 103)],
            "saas": [],
            "jobs": {"103": [failed_job("Build connectors image")]},
        },
    )

    assert out["fail_kind"] == "pipeline"
    assert out["fail_run_id"] == "103"
    assert out["fail_run_url"] == f"https://github.com/{REPO}/actions/runs/103"
    assert out["pings"] == CONNECTORS_MEDIC


def test_pending_run_is_skipped_when_choosing_the_failing_run(tmp_path: Path):
    out = detect(
        tmp_path,
        {
            "pipeline": [
                pipeline_run(100, "failure"),
                pipeline_run(101, "success"),
                pipeline_run(102, "failure"),
                pipeline_run(103, "success"),
            ],
            "saas": [saas_result(101, "failure", 501)],
            "jobs": {
                "101": [dispatched_trigger()],
                "102": [failed_job("Helm chart setup")],
                "103": [dispatched_trigger()],
            },
        },
    )

    assert out["streak_count"] == "3"
    assert out["fail_kind"] == "pipeline"
    assert out["fail_run_id"] == "102"
    assert out["pings"] == _ENV["MEDIC_DISTRO"]


def test_green_run_after_failures_resolves(tmp_path: Path):
    out = detect(
        tmp_path,
        {
            "pipeline": [
                pipeline_run(101, "failure"),
                pipeline_run(102, "failure"),
                pipeline_run(103, "success"),
            ],
            "saas": [saas_result(103, "success", 503)],
            "jobs": {"103": [dispatched_trigger()]},
        },
    )

    assert out["is_streak"] == "false"
    assert out["latest_success"] == "true"
