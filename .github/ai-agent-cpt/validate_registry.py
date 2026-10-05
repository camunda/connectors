#!/usr/bin/env python3
"""Validate and render the AI Agent CPT execution registry."""

import json
import pathlib
import sys


KNOWN_PROVIDER_GROUPS = {"", "openai", "vertex", "bedrock"}
KNOWN_TAGS = {
    "core-smoke",
    "structured-output",
    "reasoning",
    "prompt-caching",
    "multimodal-documents",
    "document-tool-results",
}


def fail(message):
    raise ValueError(message)


def load_registry(path):
    try:
        with pathlib.Path(path).open(encoding="utf-8") as registry_file:
            registry = json.load(registry_file)
    except json.JSONDecodeError as error:
        fail(f"registry is not valid JSON: {error}")
    if not isinstance(registry, dict):
        fail("registry must be an object")
    profiles = registry.get("credentialProfiles")
    if (
        not isinstance(profiles, list)
        or not profiles
        or not all(isinstance(profile, str) and profile for profile in profiles)
        or len(profiles) != len(set(profiles))
    ):
        fail("credentialProfiles must be a non-empty array of unique strings")
    rows = registry.get("rows")
    if not isinstance(rows, list) or not rows:
        fail("rows must be a non-empty array")
    required_ci_capabilities = registry.get("requiredCiCapabilities")
    if not isinstance(required_ci_capabilities, dict) or not required_ci_capabilities:
        fail("requiredCiCapabilities must be a non-empty object")
    return registry, rows


def parse_groups(expression):
    if not expression:
        return set()
    if "&" in expression:
        fail("groups supports only OR expressions separated by |")
    return {token.strip() for token in expression.split("|")}


def validate_groups(expression, row_id):
    tokens = parse_groups(expression)
    if not all(token in KNOWN_TAGS for token in tokens):
        fail(f"{row_id}: groups contains an unknown capability tag")


def validate_ci_contract(registry, rows):
    ci_rows = [row for row in rows if row["ci"]]
    if not ci_rows:
        fail("registry must contain at least one ci=true row")

    required_ci_capabilities = registry["requiredCiCapabilities"]
    required_provider_groups = KNOWN_PROVIDER_GROUPS - {""}
    configured_provider_groups = set(required_ci_capabilities)
    if configured_provider_groups != required_provider_groups:
        fail(
            "requiredCiCapabilities must define exactly these provider groups: "
            f"{sorted(required_provider_groups)}"
        )
    for provider_group, required_tags in required_ci_capabilities.items():
        if (
            not isinstance(required_tags, list)
            or not required_tags
            or len(required_tags) != len(set(required_tags))
            or not all(tag in KNOWN_TAGS for tag in required_tags)
        ):
            fail(
                f"requiredCiCapabilities.{provider_group} must contain unique known capability tags"
            )
        selected_tags = set()
        for row in ci_rows:
            if row["providerGroup"] == provider_group:
                selected_tags.update(parse_groups(row["groups"]))
        missing_tags = set(required_tags) - selected_tags
        if missing_tags:
            fail(
                f"{provider_group}: ci rows do not select required capability tag(s): "
                f"{sorted(missing_tags)}"
            )


def validate(registry, rows):
    profiles = set(registry["credentialProfiles"])
    ids = set()
    for row in rows:
        if not isinstance(row, dict):
            fail(f"registry row must be an object: {row!r}")
        required_fields = {
            "id": str,
            "name": str,
            "providerGroup": str,
            "groups": str,
            "testClasses": str,
            "buildBundle": bool,
            "mavenProjects": str,
            "credentialProfiles": list,
            "ci": bool,
        }
        for field, expected_type in required_fields.items():
            if field not in row or not isinstance(row[field], expected_type):
                fail(f"registry row has invalid {field}: {row.get(field)!r}")
        row_id = row.get("id")
        if not row_id or row_id in ids:
            fail(f"duplicate or missing row id: {row_id!r}")
        ids.add(row_id)
        if row.get("providerGroup") not in KNOWN_PROVIDER_GROUPS:
            fail(f"{row_id}: unknown providerGroup")
        validate_groups(row.get("groups", ""), row_id)
        row_profiles = row.get("credentialProfiles")
        if not isinstance(row_profiles, list) or not row_profiles:
            fail(f"{row_id}: credentialProfiles must be non-empty")
        if not all(isinstance(profile, str) for profile in row_profiles):
            fail(f"{row_id}: credentialProfiles must contain only strings")
        unknown_profiles = set(row_profiles) - profiles
        if unknown_profiles:
            fail(f"{row_id}: unknown credential profile(s): {sorted(unknown_profiles)}")
        if row.get("buildBundle"):
            if not row.get("testClasses") or row.get("groups"):
                fail(f"{row_id}: bundle rows require testClasses and no groups")
        elif not row.get("groups"):
            fail(f"{row_id}: native rows require a capability group expression")
        elif row.get("testClasses"):
            fail(f"{row_id}: native rows must not select Java classes")
        elif row.get("ci") and not row.get("providerGroup"):
            fail(f"{row_id}: native ci rows require a providerGroup")
    validate_ci_contract(registry, rows)


def render_ci_matrix(rows):
    return [
        {
            "name": row["name"],
            "provider-group": row["providerGroup"],
            "groups": row["groups"],
            "test-classes": row["testClasses"],
            "build-bundle": row["buildBundle"],
            "maven-projects": row["mavenProjects"],
            "credential-profiles": row["credentialProfiles"],
        }
        for row in rows
        if row.get("ci")
    ]


if __name__ == "__main__":
    registry, rows = load_registry(sys.argv[1])
    validate(registry, rows)
    if len(sys.argv) > 2 and sys.argv[2] == "--matrix":
        print(json.dumps(render_ci_matrix(rows), separators=(",", ":")))
    else:
        print(f"Validated {len(rows)} AI Agent CPT registry rows.")
