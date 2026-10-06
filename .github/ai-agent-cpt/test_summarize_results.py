#!/usr/bin/env python3

import json
import os
import pathlib
import tempfile
import unittest
from unittest import mock

import summarize_results


class SummarizeResultsTest(unittest.TestCase):

    def test_render_matrix_preserves_registry_ids(self):
        with tempfile.TemporaryDirectory() as directory:
            registry_path = pathlib.Path(directory, "registry.json")
            registry_path.write_text(
                json.dumps(
                    {
                        "credentialProfiles": ["openai"],
                        "requiredCiCapabilities": {
                            "openai": ["core-smoke"],
                            "vertex": ["core-smoke"],
                            "bedrock": ["core-smoke"],
                        },
                        "rows": [
                            {
                                "id": f"{provider}-native",
                                "name": f"{provider.title()} native",
                                "providerGroup": provider,
                                "groups": ["core-smoke"],
                                "testClasses": "",
                                "buildBundle": False,
                                "mavenProjects": "agentic-ai",
                                "credentialProfiles": ["openai"],
                                "ci": True,
                            }
                            for provider in ("openai", "vertex", "bedrock")
                        ],
                    }
                ),
                encoding="utf-8",
            )

            matrix = json.loads(summarize_results.render_matrix(registry_path))

            self.assertEqual(
                [row["id"] for row in matrix],
                ["openai-native", "vertex-native", "bedrock-native"],
            )
            self.assertEqual(matrix[0]["groups"], "core-smoke")

    def test_finalize_extracts_provider_and_model_and_sanitizes_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            report_directory = pathlib.Path(directory, "raw")
            report_directory.mkdir()
            report_directory.joinpath("cpt-results-1.jsonl").write_text(
                json.dumps(
                    {
                        "class": "RealProviderCoreE2ETestIT",
                        "method": "toolCallLoopSurfacesPlantedFact",
                        "displayName": "openai-responses-v2/gpt-5.1/preview",
                        "tags": "core-smoke",
                        "status": "failed",
                        "durationSeconds": 1.2345,
                        "failureClass": "AssertionError",
                    }
                )
                + "\n",
                encoding="utf-8",
            )
            output = pathlib.Path(directory, "result.json")
            environment = {
                "CPT_REPORT_DIRECTORY": str(report_directory),
                "CPT_MATRIX_ID": "openai-native",
                "CPT_MATRIX_NAME": "OpenAI native capability CPT",
                "CPT_PROVIDER_GROUP": "openai",
                "CPT_CAPABILITIES": "core-smoke",
                "CPT_MAVEN_OUTCOME": "failure",
            }
            with mock.patch.dict(os.environ, environment, clear=False):
                summarize_results.finalize(output)

            result = json.loads(output.read_text(encoding="utf-8"))
            test = result["tests"][0]
            self.assertEqual(test["provider"], "openai-responses-v2")
            self.assertEqual(test["model"], "gpt-5.1/preview")
            self.assertEqual(test["status"], "failed")
            self.assertEqual(test["reason"], "AssertionError")

    def test_finalize_redacts_failure_without_an_exception_class(self):
        with tempfile.TemporaryDirectory() as directory:
            report_directory = pathlib.Path(directory, "raw")
            report_directory.mkdir()
            report_directory.joinpath("cpt-results-1.jsonl").write_text(
                json.dumps(
                    {
                        "class": "RealProviderCoreE2ETestIT",
                        "method": "toolCall",
                        "displayName": "provider/model",
                        "tags": "core-smoke",
                        "status": "failed",
                        "durationSeconds": 1,
                        "failureClass": "Authorization: Bearer short-secret password hunter2",
                    }
                )
                + "\n",
                encoding="utf-8",
            )
            output = pathlib.Path(directory, "result.json")
            environment = {
                "CPT_REPORT_DIRECTORY": str(report_directory),
                "CPT_MATRIX_ID": "provider",
                "CPT_MATRIX_NAME": "Provider CPT",
                "CPT_MAVEN_OUTCOME": "failure",
            }
            with mock.patch.dict(os.environ, environment, clear=False):
                summarize_results.finalize(output)

            result = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(result["tests"][0]["reason"], "Failure details redacted")
            self.assertNotIn("short-secret", output.read_text(encoding="utf-8"))
            self.assertNotIn("hunter2", output.read_text(encoding="utf-8"))

    def test_finalize_reports_success_without_results_as_diagnostic(self):
        with tempfile.TemporaryDirectory() as directory:
            output = pathlib.Path(directory, "result.json")
            environment = {
                "CPT_REPORT_DIRECTORY": directory,
                "CPT_MATRIX_ID": "openai-native",
                "CPT_MATRIX_NAME": "OpenAI native capability CPT",
                "CPT_MAVEN_OUTCOME": "success",
            }
            with mock.patch.dict(os.environ, environment, clear=False):
                summarize_results.finalize(output)
            result = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(
                result["diagnostics"], ["Maven succeeded but produced no CPT result records"]
            )

    def test_aggregate_renders_failures_and_missing_rows(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            registry = {
                "rows": [
                    {
                        "id": "openai-native",
                        "name": "OpenAI native",
                        "groups": ["core-smoke"],
                        "testClasses": "",
                        "ci": True,
                    },
                    {
                        "id": "vertex-native",
                        "name": "Vertex native",
                        "groups": ["core-smoke"],
                        "testClasses": "",
                        "ci": True,
                    },
                ]
            }
            registry_path = root / "registry.json"
            registry_path.write_text(json.dumps(registry), encoding="utf-8")
            result_directory = root / "results" / "openai"
            result_directory.mkdir(parents=True)
            result_directory.joinpath("result.json").write_text(
                json.dumps(
                    {
                        "schemaVersion": 1,
                        "matrix": {"id": "openai-native"},
                        "mavenOutcome": "failure",
                        "counts": {"passed": 1, "failed": 1, "skipped": 1},
                        "tests": [
                            {
                                "class": "RealProviderCoreE2ETestIT",
                                "scenario": "toolCall",
                                "capability": "core-smoke",
                                "provider": "openai-responses-v2",
                                "model": "gpt|preview",
                                "status": "failed",
                                "durationSeconds": 2,
                                "reason": "expected `answer` | got none",
                            },
                            {
                                "class": "RealProviderCoreE2ETestIT",
                                "scenario": "feedback",
                                "capability": "core-smoke",
                                "provider": "openai-responses-v2",
                                "model": "gpt",
                                "status": "passed",
                                "durationSeconds": 1.25,
                                "reason": "",
                            },
                            {
                                "class": "DocumentToolCallResultsIT",
                                "scenario": "singleDocument",
                                "capability": "document-tool-results",
                                "provider": "",
                                "model": "",
                                "status": "skipped",
                                "durationSeconds": 0,
                                "reason": "",
                            },
                        ],
                        "diagnostics": [],
                    }
                ),
                encoding="utf-8",
            )
            summary = root / "summary.md"
            summarize_results.aggregate(registry_path, root / "results", summary)
            markdown = summary.read_text(encoding="utf-8")
            self.assertIn("❌ Failed", markdown)
            self.assertIn("gpt\\|preview", markdown)
            self.assertIn("Vertex native: result artifact is missing", markdown)
            self.assertIn("1 passed · 1 failed · 1 skipped · 1 reporting gaps", markdown)
            self.assertIn("2.00s", markdown)
            self.assertIn("| OpenAI native | 1 | 1 | 1 | failure |", markdown)
            self.assertLess(markdown.index("### Failed tests"), markdown.index("### Skipped tests"))


if __name__ == "__main__":
    unittest.main()
