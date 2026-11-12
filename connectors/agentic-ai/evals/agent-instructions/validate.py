#!/usr/bin/env python3
"""Check pilot artifacts and fixture applicability, not model or Java correctness."""

import hashlib
import json
import subprocess
import tempfile
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[4]
SUITE = ROOT / "connectors/agentic-ai/evals/agent-instructions"
INSTRUMENTED_REPEAT = ROOT / "connectors/agentic-ai/evals/agent-instructions-workspace/instrumented-repeat"
STATUSES = ("pass", "fail", "not_run")
DIMENSIONS = ("task_outcome", "instruction_interpretation")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def load(path):
    return json.loads(path.read_text())


def run(command, cwd):
    return subprocess.run(
        command, cwd=cwd, check=True, capture_output=True, text=True
    ).stdout


def summary_for(results, assertions):
    summary = {}
    for dimension in DIMENSIONS:
        counts = Counter(
            result["status"]
            for result in results
            if assertions[result["id"]]["dimension"] == dimension
        )
        summary[dimension] = {status: counts[status] for status in STATUSES}
        summary[dimension]["total"] = sum(counts.values())
    statuses = {result["status"] for result in results}
    summary["overall"] = (
        "fail" if "fail" in statuses
        else "incomplete" if "not_run" in statuses
        else "pass"
    )
    return summary


def validate_matched_iteration(suite, case, assertions):
    setup = load(INSTRUMENTED_REPEAT / "setup.json")
    grading = load(INSTRUMENTED_REPEAT / "grading.json")["controls"]
    benchmark = load(INSTRUMENTED_REPEAT / "benchmark.json")
    verification = load(INSTRUMENTED_REPEAT / "verification.json")
    feedback = load(INSTRUMENTED_REPEAT / "feedback.json")
    arms = ("without_instructions", "with_instructions")

    require(
        setup["name"] == benchmark["name"] == "instrumented-repeat",
        "Instrumented repeat name differs",
    )
    require(setup["source_revision"] == suite["source_revision"], "Wrong matched baseline")
    require(setup["shared_user_task"] == case["prompt"], "The paired prompts differ from the eval")
    require(
        setup["configured_agent"]["model"] == benchmark["configured_model"]
        and setup["configured_agent"]["reasoning_effort"] == benchmark["reasoning_effort"],
        "Model configuration differs between setup and benchmark",
    )
    require(
        {item["id"] for item in grading} == set(arms)
        == {item["id"] for item in benchmark["control_summary"]},
        "Matched iteration arm IDs differ",
    )
    require(
        benchmark["model_runs"] == 2 and benchmark["instruction_uplift"] is None,
        "A single pair must not claim general instruction uplift",
    )
    require(
        feedback["human_review"]["status"] == "pending",
        "Matched iteration has not received human review",
    )

    for arm in arms:
        grade = next(item for item in grading if item["id"] == arm)
        results = grade["assertion_results"]
        require(
            len(results) == len(assertions)
            and {result["id"] for result in results} == set(assertions),
            f"Incomplete matched grading: {arm}",
        )
        for result in results:
            require(result["status"] in STATUSES and result["evidence"], "Invalid matched grade")
        summary = summary_for(results, assertions)
        require(summary == grade["summary"], f"Wrong matched counts: {arm}")
        report = next(item for item in benchmark["control_summary"] if item["id"] == arm)
        for dimension in DIMENSIONS:
            require(
                report[dimension]
                == {status: summary[dimension][status] for status in STATUSES},
                f"Wrong matched benchmark counts: {arm}",
            )
        require(report["overall"] == summary["overall"], "Wrong matched overall result")

        outputs = INSTRUMENTED_REPEAT / arm
        for path in ("change.patch", "explanation.md", "commands.txt"):
            require((outputs / path).is_file(), f"Missing matched output: {arm}/{path}")
        result = verification["results"][arm]
        focused = result["focused_candidate"]
        seeded = result["focused_seeded_baseline_with_candidate_tests"]
        combined = result["combined_candidate"]
        require(
            focused["exit_code"] == 0
            and focused["failures"] == focused["errors"] == focused["skipped"] == 0,
            f"Matched candidate focused tests are not green: {arm}",
        )
        require(
            seeded["exit_code"] != 0
            and seeded["failures"] + seeded["errors"] > 0
            and (seeded.get("new_regression_failure") or seeded.get("new_regression_failures")),
            f"Seeded regression was not demonstrated: {arm}",
        )
        require(
            combined["exit_code"] == 0
            and combined["failures"] == combined["errors"] == combined["skipped"] == 0,
            f"Related converter tests are not green: {arm}",
        )
        print(f"{arm}: {summary}")

        source_paths = [suite["reference_files"][0], suite["reference_files"][-1]]
        originals = {
            path: run(["git", "show", f"{suite['source_revision']}:{path}"], ROOT)
            for path in source_paths
        }
        with tempfile.TemporaryDirectory(prefix="instruction-eval-matched-") as directory:
            temporary = Path(directory)
            for path, content in originals.items():
                target = temporary / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
            seed = ROOT / case["files"][0]
            patch = outputs / "change.patch"
            run(["git", "apply", "--check", str(seed)], temporary)
            run(["git", "apply", str(seed)], temporary)
            seeded_source = (temporary / source_paths[0]).read_bytes()
            require(
                hashlib.sha256(seeded_source).hexdigest()
                == setup["seeded_converter_sha256_both_arms"],
                f"Unexpected matched seed for {arm}",
            )
            run(["git", "apply", "--check", str(patch)], temporary)
            run(["git", "apply", str(patch)], temporary)
        print(f"Matched patch applicability: {arm}: pass")


def main():
    for path in sorted(SUITE.parent.rglob("*.json")):
        load(path)
    suite = load(SUITE / "evals.json")
    require(len(suite["evals"]) == 1, "The pilot must contain exactly one case")
    case = suite["evals"][0]
    require(case["id"] == 1 and case["prompt"] and case["expected_output"], "Incomplete case")
    for path in (
        suite["instruction_files"] + suite["instruction_aliases"]
        + suite["reference_files"] + case["files"]
    ):
        require((ROOT / path).is_file(), f"Missing input: {path}")
    assertions = {item["id"]: item for item in case["assertions"]}
    require(len(assertions) == len(case["assertions"]), "Duplicate assertion IDs")
    require(
        {item["dimension"] for item in assertions.values()} == set(DIMENSIONS),
        "Both scoring dimensions are required",
    )
    for item in assertions.values():
        require(item["text"] and item["evidence"], f"Incomplete assertion: {item['id']}")

    validate_matched_iteration(suite, case, assertions)
    print("JSON, references, grading arithmetic and patch checks: pass")
    print("Instrumented repeat red/green outcomes are recorded and independently verified; uplift remains descriptive")


if __name__ == "__main__":
    main()
