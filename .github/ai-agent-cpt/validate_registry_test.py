#!/usr/bin/env python3

import json
import pathlib
import unittest

import validate_registry


REGISTRY_PATH = pathlib.Path(__file__).with_name("registry.json")


class ValidateRegistryTest(unittest.TestCase):

    def setUp(self):
        self.registry = json.loads(REGISTRY_PATH.read_text(encoding="utf-8"))

    def validate(self):
        validate_registry.validate(self.registry, self.registry["rows"])

    def test_current_registry_is_valid(self):
        self.validate()

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
        row["groups"] = row["groups"].replace(" | reasoning", "")

        with self.assertRaisesRegex(ValueError, "reasoning"):
            self.validate()

    def test_rejects_and_group_expression(self):
        row = next(row for row in self.registry["rows"] if row["id"] == "openai-native")
        row["groups"] = "core-smoke & reasoning"

        with self.assertRaisesRegex(ValueError, "only OR expressions"):
            self.validate()

    def test_rejects_missing_required_provider_group(self):
        del self.registry["requiredCiCapabilities"]["bedrock"]

        with self.assertRaisesRegex(ValueError, "define exactly these provider groups"):
            self.validate()


if __name__ == "__main__":
    unittest.main()
