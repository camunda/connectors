#!/usr/bin/env python3
"""Tests for the streak job in connectors-streak-detector.yml.

Runs the workflow's own step scripts against a stubbed `gh`, so the assertions follow
the YAML rather than a copy of it. Standard library only, like the other CI scripts.
"""

import json
import os
import pathlib
import re
import subprocess
import tempfile
import textwrap
import unittest

WORKFLOW = pathlib.Path(__file__).resolve().parents[1] / "connectors-streak-detector.yml"
REPO = "camunda/connectors"
SHA = "a" * 40
CONNECTORS_MEDIC = "<connectors-medic>"


def workflow_env(text: str) -> dict:
    block = re.search(r"^env:\n((?:[ #].*\n|\n)*)", text, re.M).group(1)
    return {
        m.group(1): m.group(2).strip("'\"")
        for m in re.finditer(r"^  ([A-Z_]+): (.+)$", block, re.M)
    }


def step_script(text: str, step_id: str) -> str:
    """The `run: |` block of the step with the given id."""
    lines = text.splitlines()
    start = lines.index(f"        id: {step_id}")
    run = next(
        i for i in range(start, len(lines)) if lines[i].strip() == "run: |"
    )
    assert not any(l.lstrip().startswith("- name:") for l in lines[start:run]), step_id
    indent = len(lines[run]) - len(lines[run].lstrip())
    body = []
    for line in lines[run + 1:]:
        if line.strip() and len(line) - len(line.lstrip()) <= indent:
            break
        body.append(line)
    return textwrap.dedent("\n".join(body))


_TEXT = WORKFLOW.read_text()
ENV = workflow_env(_TEXT)

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
        "conclusion": "success" if conclusion == "success" else "failure",
        "displayTitle": f"saas-e2e-result {REPO}:{parent}:{attempt}:{SHA}",
        "createdAt": f"2026-10-16T11:{run_id % 60:02d}:00Z",
        "databaseId": run_id,
        "url": f"https://github.com/{REPO}/actions/runs/{run_id}",
    }


def result_job(verdict: str) -> dict:
    """The result run's job: `success`/`failure` for an accepted result, `rejected` when
    the callback failed validation."""
    accepted = verdict != "rejected"
    return {
        "name": "SaaS E2E result",
        "conclusion": "success" if verdict == "success" else "failure",
        "steps": [
            {"name": "Validate the callback payload",
             "conclusion": "success" if accepted else "failure"},
            {"name": "Fail the job when the SaaS E2E run did not pass",
             "conclusion": "failure" if verdict == "failure" else "skipped"},
        ],
    }


def saas_jobs(*results: tuple) -> dict:
    """Job fixtures for `(run_id, verdict)` result runs."""
    return {str(run_id): [result_job(verdict)] for run_id, verdict in results}


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


class StreakDetectorTest(unittest.TestCase):

    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.tmp = pathlib.Path(tmp.name)
        gh = self.tmp / "gh"
        gh.write_text(GH_STUB)
        gh.chmod(0o755)

    def run_step(self, step_id: str, fixture: dict, env: dict) -> dict:
        fixture_file = self.tmp / "fixture.json"
        fixture_file.write_text(json.dumps(fixture))
        output = self.tmp / f"{step_id}.out"
        output.write_text("")
        result = subprocess.run(
            ["bash", "-c", step_script(_TEXT, step_id)],
            env={
                **os.environ,
                **ENV,
                **env,
                "PATH": f"{self.tmp}{os.pathsep}{os.environ['PATH']}",
                "GH_FIXTURE": str(fixture_file),
                "GITHUB_OUTPUT": str(output),
                "GITHUB_REPOSITORY": REPO,
                "GH_TOKEN": "unused",
                "CONNECTORS_MEDIC": CONNECTORS_MEDIC,
            },
            text=True,
            capture_output=True,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return dict(l.split("=", 1) for l in output.read_text().splitlines() if "=" in l)

    def detect(self, fixture: dict) -> dict:
        out = self.run_step("streak", fixture, {"BASE_REF": "main"})
        if out["is_streak"] == "true":
            medics = self.run_step(
                "medics",
                fixture,
                {"FAIL_RUN_ID": out["fail_run_id"], "FAIL_KIND": out["fail_kind"]},
            )
            out["pings"] = medics["pings"]
        return out

    def test_saas_only_failures_point_at_the_failing_callback(self):
        # Three green pipeline runs whose SaaS callbacks failed; the event that raises
        # the streak can be the green parent, which carries no failed jobs.
        parents = (101, 102, 103)
        out = self.detect({
            "pipeline": [pipeline_run(i, "success") for i in parents],
            "saas": [saas_result(i, "failure", i + 400) for i in parents],
            "jobs": {
                **{str(i): [dispatched_trigger()] for i in parents},
                **saas_jobs(*((i + 400, "failure") for i in parents)),
            },
        })

        self.assertEqual(out["is_streak"], "true")
        self.assertEqual(out["streak_count"], "3")
        self.assertEqual(out["fail_kind"], "saas")
        self.assertEqual(out["fail_run_id"], "503")
        self.assertEqual(out["fail_run_url"], f"https://github.com/{REPO}/actions/runs/503")
        self.assertEqual(out["pings"], ENV["MEDIC_TEST_AUTOMATION"])

    def test_pipeline_failures_point_at_the_newest_failing_run(self):
        out = self.detect({
            "pipeline": [pipeline_run(i, "failure") for i in (101, 102, 103)],
            "saas": [],
            "jobs": {"103": [failed_job("Build connectors image")]},
        })

        self.assertEqual(out["fail_kind"], "pipeline")
        self.assertEqual(out["fail_run_id"], "103")
        self.assertEqual(out["fail_run_url"], f"https://github.com/{REPO}/actions/runs/103")
        self.assertEqual(out["pings"], CONNECTORS_MEDIC)

    def test_pending_run_is_skipped_when_choosing_the_failing_run(self):
        out = self.detect({
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
                **saas_jobs((501, "failure")),
            },
        })

        self.assertEqual(out["streak_count"], "3")
        self.assertEqual(out["fail_kind"], "pipeline")
        self.assertEqual(out["fail_run_id"], "102")
        self.assertEqual(out["pings"], ENV["MEDIC_DISTRO"])

    def test_green_run_after_failures_resolves(self):
        out = self.detect({
            "pipeline": [
                pipeline_run(101, "failure"),
                pipeline_run(102, "failure"),
                pipeline_run(103, "success"),
            ],
            "saas": [saas_result(103, "success", 503)],
            "jobs": {"103": [dispatched_trigger()], **saas_jobs((503, "success"))},
        })

        self.assertEqual(out["is_streak"], "false")
        self.assertEqual(out["latest_success"], "true")
        self.assertEqual(out["green_run_url"], f"https://github.com/{REPO}/actions/runs/103")

    def test_late_failure_callback_links_the_newer_green_run(self):
        # The scan is fired by run 101's failing callback, after 102 has already gone green.
        out = self.detect({
            "pipeline": [pipeline_run(101, "success"), pipeline_run(102, "success")],
            "saas": [saas_result(102, "success", 502), saas_result(101, "failure", 503)],
            "jobs": {
                "101": [dispatched_trigger()],
                "102": [dispatched_trigger()],
                **saas_jobs((502, "success"), (503, "failure")),
            },
        })

        self.assertEqual(out["latest_success"], "true")
        self.assertEqual(out["green_run_url"], f"https://github.com/{REPO}/actions/runs/102")

    def test_rejected_callbacks_are_not_saas_failures(self):
        # Every callback failed validation, so no SaaS result is established: the runs
        # stay pending rather than counting towards a streak.
        parents = (101, 102, 103)
        out = self.detect({
            "pipeline": [pipeline_run(i, "success") for i in parents],
            "saas": [saas_result(i, "failure", i + 400) for i in parents],
            "jobs": {
                **{str(i): [dispatched_trigger()] for i in parents},
                **saas_jobs(*((i + 400, "rejected") for i in parents)),
            },
        })

        self.assertEqual(out["streak_count"], "0")
        self.assertEqual(out["is_streak"], "false")
        self.assertEqual(out["latest_success"], "false")

    def test_rejected_callback_does_not_replace_an_accepted_result(self):
        out = self.detect({
            "pipeline": [pipeline_run(101, "failure"), pipeline_run(102, "success")],
            "saas": [saas_result(102, "success", 502), saas_result(102, "failure", 503)],
            "jobs": {
                "102": [dispatched_trigger()],
                **saas_jobs((502, "success"), (503, "rejected")),
            },
        })

        self.assertEqual(out["latest_success"], "true")
        self.assertEqual(out["green_run_url"], f"https://github.com/{REPO}/actions/runs/102")


if __name__ == "__main__":
    unittest.main()
