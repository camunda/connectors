#!/usr/bin/env python3
"""Classify changed repository paths with the curated AI Agent CPT impact map."""

import argparse
import json
import os
import pathlib
import sys


IMPACT_MAP_PATH = pathlib.Path(__file__).with_name("impact-map.json")
EVENT_ACTION_ENV = "AI_AGENT_CPT_EVENT_ACTION"
EVENT_LABEL_ENV = "AI_AGENT_CPT_EVENT_LABEL"
TARGET_LABEL = "ai-agent-model-e2e-test"
STATE_AFFECTED_AUTHORIZED = "affected-authorized"
STATE_AFFECTED_WAITING = "affected-waiting"
STATE_NOT_AFFECTED = "not-affected"
SUPPORTED_EVENT_ACTIONS = {"opened", "reopened", "synchronize", "labeled"}
STATE_SUMMARIES = {
    STATE_AFFECTED_AUTHORIZED: (
        "### AI Agent CPT: affected and authorized",
        "The ai-agent-model-e2e-test label event authorized the current PR head SHA. "
        "Paid provider tests are authorized to run.",
    ),
    STATE_AFFECTED_WAITING: (
        "### AI Agent CPT: affected, waiting for authorization",
        "Apply ai-agent-model-e2e-test to authorize the current PR head SHA. "
        "After a push, remove and reapply the label.",
    ),
    STATE_NOT_AFFECTED: (
        "### AI Agent CPT: not affected",
        "No path in the curated AI Agent CPT impact map changed. "
        "Paid provider tests will not run.",
    ),
}
STATE_ANNOTATIONS = {
    STATE_AFFECTED_AUTHORIZED: (
        "notice",
        "AI Agent CPT changes are affected and authorized for the current head SHA.",
    ),
    STATE_AFFECTED_WAITING: (
        "warning",
        "AI Agent CPT changes are affected and waiting for ai-agent-model-e2e-test "
        "authorization on the current head SHA.",
    ),
    STATE_NOT_AFFECTED: (
        "notice",
        "AI Agent CPT is not affected by this change.",
    ),
}
VALID_OUTPUT_TUPLES = {
    STATE_NOT_AFFECTED: (False, False),
    STATE_AFFECTED_WAITING: (True, False),
    STATE_AFFECTED_AUTHORIZED: (True, True),
}


def load_impact_map(path=IMPACT_MAP_PATH):
    with pathlib.Path(path).open(encoding="utf-8") as impact_map_file:
        impact_map = json.load(impact_map_file)

    for field in ("description", "tradeoffs", "maintenance", "exclusions"):
        if not isinstance(impact_map.get(field), str) or not impact_map[field]:
            raise ValueError(f"impact map {field} must be a non-empty string")

    groups = impact_map.get("groups")
    if not isinstance(groups, list) or not groups:
        raise ValueError("impact map groups must be a non-empty array")

    names = set()
    configured_paths = set()
    for group in groups:
        if not isinstance(group, dict):
            raise ValueError("impact map groups must be objects")
        name = group.get("name")
        description = group.get("description")
        paths = group.get("paths")
        if not isinstance(name, str) or not name or name in names:
            raise ValueError("impact map group names must be unique non-empty strings")
        names.add(name)
        if not isinstance(description, str) or not description:
            raise ValueError(f"{name}: description must be a non-empty string")
        if (
            not isinstance(paths, list)
            or not paths
            or not all(isinstance(pattern, str) and pattern for pattern in paths)
            or len(paths) != len(set(paths))
        ):
            raise ValueError(f"{name}: paths must be unique non-empty strings")
        for pattern in paths:
            if pattern.startswith("/") or ".." in pathlib.PurePosixPath(pattern).parts:
                raise ValueError(f"{name}: paths must be repository-relative: {pattern}")
            if "*" in pattern and (
                not pattern.endswith("/**") or pattern.count("*") != 2
            ):
                raise ValueError(
                    f"{name}: only exact paths and directory/** patterns are supported: {pattern}"
                )
            if pattern in configured_paths:
                raise ValueError(f"impact map paths must be globally unique: {pattern}")
            configured_paths.add(pattern)

    return groups


def path_matches(path, pattern):
    if pattern.endswith("/**"):
        directory = pattern.removesuffix("/**")
        return path.startswith(f"{directory}/")
    return path == pattern


def classify(changed_files, groups):
    if not isinstance(changed_files, list) or not all(
        isinstance(path, str) and path for path in changed_files
    ):
        raise ValueError("changed files must be an array of non-empty strings")

    return [
        group["name"]
        for group in groups
        if any(
            path_matches(path, pattern)
            for path in changed_files
            for pattern in group["paths"]
        )
    ]


def decode_changed_files(data):
    if data and not data.endswith(b"\0"):
        raise ValueError("git diff path input must be NUL terminated")
    return [os.fsdecode(path) for path in data.split(b"\0") if path]


def determine_state(affected, event_action, event_label):
    if event_action not in SUPPORTED_EVENT_ACTIONS:
        raise ValueError(f"unsupported pull_request action: {event_action!r}")
    if not affected:
        return STATE_NOT_AFFECTED
    if event_action == "labeled" and event_label == TARGET_LABEL:
        return STATE_AFFECTED_AUTHORIZED
    return STATE_AFFECTED_WAITING


def render_summary(state):
    try:
        title, detail = STATE_SUMMARIES[state]
    except KeyError as error:
        raise ValueError(f"unknown impact state: {state!r}") from error
    return f"{title}\n\n{detail}\n"


def render_annotation(state):
    try:
        level, message = STATE_ANNOTATIONS[state]
    except KeyError as error:
        raise ValueError(f"unknown impact state: {state!r}") from error
    return f"::{level} title=AI Agent CPT impact::{message}"


def validate_output_tuple(affected, authorized, state):
    try:
        expected = VALID_OUTPUT_TUPLES[state]
    except KeyError as error:
        raise ValueError(f"unknown impact state: {state!r}") from error
    actual = (affected, authorized)
    if actual != expected:
        raise ValueError(
            f"invalid impact outputs for {state}: "
            f"affected={str(affected).lower()}, authorized={str(authorized).lower()}"
        )


def parse_boolean(value, field):
    if value not in {"true", "false"}:
        raise ValueError(f"{field} must be true or false")
    return value == "true"


def classify_main():
    try:
        event_action = os.environ[EVENT_ACTION_ENV]
    except KeyError as error:
        raise ValueError(f"{EVENT_ACTION_ENV} is required") from error

    changed_files = decode_changed_files(sys.stdin.buffer.read())
    matched_groups = classify(changed_files, load_impact_map())
    affected = bool(matched_groups)
    state = determine_state(
        affected, event_action, os.environ.get(EVENT_LABEL_ENV, "")
    )
    authorized = state == STATE_AFFECTED_AUTHORIZED
    print(
        "AI Agent CPT impact: "
        + (", ".join(matched_groups) if affected else "no curated paths matched")
    )
    print(render_annotation(state))

    github_output = os.environ.get("GITHUB_OUTPUT")
    github_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if not github_output or not github_summary:
        raise ValueError("GITHUB_OUTPUT and GITHUB_STEP_SUMMARY are required")

    with pathlib.Path(github_output).open("a", encoding="utf-8") as output:
        output.write(f"affected={str(affected).lower()}\n")
        output.write(f"authorized={str(authorized).lower()}\n")
        output.write(f"state={state}\n")
    with pathlib.Path(github_summary).open("a", encoding="utf-8") as summary:
        summary.write(render_summary(state))


def parse_args():
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command")
    subparsers.add_parser("classify")
    validate_parser = subparsers.add_parser("validate-outputs")
    validate_parser.add_argument("--affected", required=True)
    validate_parser.add_argument("--authorized", required=True)
    validate_parser.add_argument("--state", required=True)
    return parser.parse_args()


def main():
    arguments = parse_args()
    if arguments.command in (None, "classify"):
        classify_main()
        return
    validate_output_tuple(
        parse_boolean(arguments.affected, "affected"),
        parse_boolean(arguments.authorized, "authorized"),
        arguments.state,
    )


if __name__ == "__main__":
    try:
        main()
    except ValueError as error:
        print(f"::error::{error}")
        raise SystemExit(1) from error
