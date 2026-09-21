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
    if not isinstance(profiles, dict):
        fail("credentialProfiles must be an object")
    for profile_name, profile in profiles.items():
        if not isinstance(profile, dict):
            fail(f"{profile_name}: credential profile must be an object")
        for field in ("vaultSecrets", "environment"):
            values = profile.get(field)
            if not isinstance(values, list) or not all(
                isinstance(value, str) for value in values
            ):
                fail(f"{profile_name}: {field} must be an array of strings")
    rows = registry.get("rows")
    if not isinstance(rows, list) or not rows:
        fail("rows must be a non-empty array")
    return registry, rows


def validate_groups(expression, row_id):
    if not expression:
        return
    tokens = [token.strip() for token in expression.replace("|", "&").split("&")]
    if not all(token in KNOWN_TAGS for token in tokens):
        fail(f"{row_id}: groups contains an unknown capability tag")


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
