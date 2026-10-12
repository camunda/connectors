#!/usr/bin/env python3

import json
import pathlib
import unittest

import validate_registry


REGISTRY_PATH = (
    pathlib.Path(__file__).parents[2]
    / "connectors-e2e-test"
    / "connectors-e2e-test-agentic-ai"
    / "src"
    / "test"
    / "resources"
    / "ai-agent-cpt"
    / "registry.json"
)


class ValidateRegistryTest(unittest.TestCase):

    def setUp(self):
        self.registry = json.loads(REGISTRY_PATH.read_text(encoding="utf-8"))

    def validate(self):
        validate_registry.validate(self.registry, self.registry["rows"])

    def test_current_registry_is_valid(self):
        self.validate()

    def test_renders_group_array_as_maven_tag_expression(self):
        matrix = validate_registry.render_ci_matrix(self.registry["rows"])
        row = next(row for row in matrix if row["name"] == "OpenAI native capability CPT")

        self.assertEqual(
            "core-smoke | structured-output | reasoning",
            row["groups"],
        )

    def test_rejects_empty_ci_matrix(self):
        for row in self.registry["rows"]:
            row["ci"] = False

        with self.assertRaisesRegex(ValueError, "at least one ci=true row"):
            self.validate()

    def test_rejects_native_ci_row_without_provider_group(self):
        row = next(row for row in self.registry["rows"] if row["id"] == "openai-native")
        row["providerGroup"] = ""

        with self.assertRaisesRegex(ValueError, "native ci rows require a providerGroup"):
            self.validate()

    def test_rejects_missing_required_ci_capability(self):
        row = next(row for row in self.registry["rows"] if row["id"] == "vertex-native")
        row["groups"].remove("reasoning")

        with self.assertRaisesRegex(ValueError, "reasoning"):
            self.validate()

    def test_rejects_duplicate_group(self):
        row = next(row for row in self.registry["rows"] if row["id"] == "openai-native")
        row["groups"].append("reasoning")

        with self.assertRaisesRegex(ValueError, "unique non-empty strings"):
            self.validate()

    def test_rejects_missing_required_provider_group(self):
        del self.registry["requiredCiCapabilities"]["bedrock"]

        with self.assertRaisesRegex(ValueError, "define exactly these provider groups"):
            self.validate()


if __name__ == "__main__":
    unittest.main()
