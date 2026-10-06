#!/usr/bin/env python3
"""Create per-leg AI Agent CPT results and a consolidated job summary."""

import argparse
import json
import os
import pathlib
import re

import validate_registry


SCHEMA_VERSION = 1
MAX_DETAIL_ROWS = 300
ANSI_ESCAPE = re.compile(r"\x1b\[[0-?]*[ -/]*[@-~]")
EXCEPTION_CLASS = re.compile(r"[A-Za-z_$][A-Za-z0-9_.$]*")


def compact(value):
    value = ANSI_ESCAPE.sub("", str(value or ""))
    return " ".join(value.split())


def sanitize_failure(value):
    value = compact(value)
    if not value:
        return ""
    return value if EXCEPTION_CLASS.fullmatch(value) else "Failure details redacted"


def provider_and_model(display_name):
    display_name = compact(display_name)
    if len(display_name) >= 2 and display_name[0] == display_name[-1] == '"':
        display_name = display_name[1:-1]
    if "/" not in display_name:
        return "", ""
    return tuple(display_name.split("/", 1))


def read_json_lines(report_directory):
    tests = []
    diagnostics = []
    for path in sorted(pathlib.Path(report_directory).glob("*.jsonl")):
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            try:
                raw = json.loads(line)
            except json.JSONDecodeError as error:
                diagnostics.append(f"{path.name}:{line_number}: invalid JSON: {error.msg}")
                continue
            provider, model = provider_and_model(raw.get("displayName", ""))
            tests.append(
                {
                    "class": compact(raw.get("class")),
                    "scenario": compact(raw.get("method")),
                    "capability": compact(raw.get("tags")),
                    "provider": provider,
                    "model": model,
                    "status": compact(raw.get("status")),
                    "durationSeconds": round(float(raw.get("durationSeconds", 0)), 3),
                    "reason": sanitize_failure(raw.get("failureClass")),
                }
            )
    return tests, diagnostics


def finalize(output):
    report_directory = os.environ["CPT_REPORT_DIRECTORY"]
    tests, diagnostics = read_json_lines(report_directory)
    maven_outcome = os.environ.get("CPT_MAVEN_OUTCOME", "unknown")
    if not tests:
        if maven_outcome == "success":
            diagnostics.append("Maven succeeded but produced no CPT result records")
        else:
            diagnostics.append(f"Maven outcome was {maven_outcome} before CPT results were recorded")

    counts = {status: 0 for status in ("passed", "failed", "skipped")}
    for test in tests:
        counts[test["status"]] = counts.get(test["status"], 0) + 1

    result = {
        "schemaVersion": SCHEMA_VERSION,
        "matrix": {
            "id": os.environ["CPT_MATRIX_ID"],
            "name": os.environ["CPT_MATRIX_NAME"],
            "providerGroup": os.environ.get("CPT_PROVIDER_GROUP", ""),
            "capabilities": os.environ.get("CPT_CAPABILITIES", ""),
        },
        "mavenOutcome": maven_outcome,
        "counts": counts,
        "tests": tests,
        "diagnostics": diagnostics,
    }
    output = pathlib.Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def markdown(value):
    return compact(value).replace("\\", "\\\\").replace("|", "\\|").replace("`", "\\`")


def status_label(status):
    return {
        "passed": "✅ Passed",
        "failed": "❌ Failed",
        "skipped": "⏭ Skipped",
    }.get(status, f"⚠ {markdown(status)}")


def table(rows):
    lines = [
        "| Matrix / capability | Test / scenario | Provider | Model | Status | Duration | Reason |",
        "|---|---|---|---|---|---:|---|",
    ]
    for row in rows:
        capability = row["capability"] or row["matrixCapabilities"] or "—"
        lines.append(
            "| {matrix} / {capability} | `{test_class}.{scenario}` | `{provider}` | `{model}` | "
            "{status} | {duration:.2f}s | {reason} |".format(
                matrix=markdown(row["matrixName"]),
                capability=markdown(capability),
                test_class=markdown(row["class"]),
                scenario=markdown(row["scenario"]),
                provider=markdown(row["provider"] or "—"),
                model=markdown(row["model"] or "—"),
                status=status_label(row["status"]),
                duration=row["durationSeconds"],
                reason=markdown(row["reason"]) if row["reason"] else "—",
            )
        )
    return lines


def load_results(results_directory):
    results = {}
    diagnostics = []
    for path in sorted(pathlib.Path(results_directory).glob("**/result.json")):
        try:
            result = json.loads(path.read_text(encoding="utf-8"))
            if result.get("schemaVersion") != SCHEMA_VERSION:
                raise ValueError(f"unsupported schema version {result.get('schemaVersion')}")
            matrix_id = result["matrix"]["id"]
            if matrix_id in results:
                raise ValueError(f"duplicate matrix result {matrix_id}")
            results[matrix_id] = result
        except (json.JSONDecodeError, KeyError, TypeError, ValueError) as error:
            diagnostics.append(f"{path}: {error}")
    return results, diagnostics


def render_matrix(registry_path):
    registry, rows = validate_registry.load_registry(registry_path)
    validate_registry.validate(registry, rows)
    matrix = validate_registry.render_ci_matrix(rows)
    ci_rows = [row for row in rows if row.get("ci")]
    for matrix_row, registry_row in zip(matrix, ci_rows, strict=True):
        matrix_row["id"] = registry_row["id"]
    return json.dumps(matrix, separators=(",", ":"))


def aggregate(registry_path, results_directory, output):
    registry = json.loads(pathlib.Path(registry_path).read_text(encoding="utf-8"))
    expected_rows = [row for row in registry["rows"] if row.get("ci")]
    results, aggregate_diagnostics = load_results(results_directory)
    all_tests = []
    matrix_counts = []
    diagnostics = list(aggregate_diagnostics)
    maven_failed = False

    for expected in expected_rows:
        result = results.pop(expected["id"], None)
        if result is None:
            diagnostics.append(f"{expected['name']}: result artifact is missing")
            matrix_counts.append((expected["name"], 0, 0, 0, "Missing"))
            continue
        diagnostics.extend(f"{expected['name']}: {message}" for message in result["diagnostics"])
        maven_failed = maven_failed or result["mavenOutcome"] == "failure"
        counts = result["counts"]
        matrix_counts.append(
            (
                expected["name"],
                counts.get("passed", 0),
                counts.get("failed", 0),
                counts.get("skipped", 0),
                result["mavenOutcome"],
            )
        )
        for test in result["tests"]:
            all_tests.append(
                {
                    **test,
                    "matrixName": expected["name"],
                    "matrixCapabilities": " | ".join(expected["groups"])
                    or expected["testClasses"],
                }
            )
    diagnostics.extend(f"{matrix_id}: unexpected result artifact" for matrix_id in sorted(results))

    failed = sorted(
        (test for test in all_tests if test["status"] == "failed"),
        key=lambda test: (
            test["matrixName"],
            test["class"],
            test["scenario"],
            test["provider"],
            test["model"],
        ),
    )
    skipped = [test for test in all_tests if test["status"] == "skipped"]
    passed = [test for test in all_tests if test["status"] == "passed"]
    lines = ["## AI Agent CPT test summary", ""]
    outcome = (
        "❌ Failed"
        if failed or maven_failed
        else ("⚠ Incomplete" if diagnostics else "✅ Passed")
    )
    lines.append(
        f"**Result:** {outcome} · {len(passed)} passed · {len(failed)} failed · "
        f"{len(skipped)} skipped · {len(diagnostics)} reporting gaps"
    )
    lines.append("")

    if diagnostics:
        lines.extend(["### Reporting and infrastructure diagnostics", ""])
        lines.extend(f"- ⚠ {markdown(message)}" for message in diagnostics)
        lines.append("")
    if failed:
        lines.extend(["### Failed tests", "", *table(failed), ""])
    if skipped:
        lines.extend(["### Skipped tests", "", *table(skipped), ""])

    lines.extend(
        [
            "### Counts by matrix row",
            "",
            "| Matrix row | Passed | Failed | Skipped | Maven outcome |",
            "|---|---:|---:|---:|---|",
        ]
    )
    for name, passed_count, failed_count, skipped_count, maven_outcome in matrix_counts:
        lines.append(
            f"| {markdown(name)} | {passed_count} | {failed_count} | {skipped_count} | "
            f"{markdown(maven_outcome)} |"
        )

    detail_rows = all_tests[:MAX_DETAIL_ROWS]
    if detail_rows:
        lines.extend(["", "<details>", "<summary>All test results</summary>", "", *table(detail_rows)])
        if len(all_tests) > len(detail_rows):
            lines.extend(
                [
                    "",
                    f"_Omitted {len(all_tests) - len(detail_rows)} additional rows to keep the summary concise._",
                ]
            )
        lines.extend(["", "</details>"])

    pathlib.Path(output).write_text("\n".join(lines) + "\n", encoding="utf-8")


def parse_args():
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)
    matrix_parser = subparsers.add_parser("matrix")
    matrix_parser.add_argument("--registry", required=True)
    finalize_parser = subparsers.add_parser("finalize")
    finalize_parser.add_argument("--output", required=True)
    aggregate_parser = subparsers.add_parser("aggregate")
    aggregate_parser.add_argument("--registry", required=True)
    aggregate_parser.add_argument("--results", required=True)
    aggregate_parser.add_argument("--output", required=True)
    return parser.parse_args()


if __name__ == "__main__":
    arguments = parse_args()
    if arguments.command == "matrix":
        print(render_matrix(arguments.registry))
    elif arguments.command == "finalize":
        finalize(arguments.output)
    else:
        aggregate(arguments.registry, arguments.results, arguments.output)
