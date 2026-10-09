#!/usr/bin/env python3

import json
import pathlib
import tempfile
import unittest

import classify_impact


REPOSITORY_ROOT = pathlib.Path(__file__).parents[2]
REQUIRED_DIRECT_PATTERNS = {
    "connectors/agentic-ai/**",
    "connectors-e2e-test/connectors-e2e-test-agentic-ai/**",
    ".github/ai-agent-cpt/**",
    ".github/workflows/AI_AGENT_CPT_PR.yml",
    ".github/workflows/package.json",
}


class ClassifyImpactTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.groups = classify_impact.load_impact_map()

    def assert_affected(self, path):
        self.assertTrue(classify_impact.classify([path], self.groups), path)

    def assert_not_affected(self, path):
        self.assertEqual([], classify_impact.classify([path], self.groups), path)

    def test_every_configured_path_exists_and_is_affected(self):
        for group in self.groups:
            for pattern in group["paths"]:
                with self.subTest(group=group["name"], pattern=pattern):
                    if pattern.endswith("/**"):
                        root = REPOSITORY_ROOT / pattern.removesuffix("/**")
                        self.assertTrue(root.is_dir(), f"missing directory root: {pattern}")
                        sample = next(
                            (path for path in root.rglob("*") if path.is_file()), None
                        )
                        self.assertIsNotNone(sample, f"empty directory root: {pattern}")
                        changed_path = sample.relative_to(REPOSITORY_ROOT).as_posix()
                    else:
                        exact_path = REPOSITORY_ROOT / pattern
                        self.assertTrue(
                            exact_path.is_file(), f"missing exact path: {pattern}"
                        )
                        changed_path = pattern

                    self.assertTrue(
                        classify_impact.path_matches(changed_path, pattern),
                        changed_path,
                    )
                    self.assertIn(
                        group["name"],
                        classify_impact.classify([changed_path], self.groups),
                    )

    def test_required_review_paths_are_affected(self):
        for path in [
            "parent/pom.xml",
            "connectors/http/pom.xml",
            "connector-commons/host-ip-validator/pom.xml",
            "connector-commons/host-ip-validator-annotation/pom.xml",
            "connector-runtime/connector-runtime-test/pom.xml",
            "mvnw",
            ".mvn/maven-build-cache-config.xml",
        ]:
            with self.subTest(path=path):
                self.assert_affected(path)

    def test_original_direct_patterns_remain_configured(self):
        direct_groups = [
            group
            for group in self.groups
            if group["name"] == "direct AI Agent CPT inputs"
        ]
        self.assertEqual(1, len(direct_groups))
        self.assertEqual(
            set(),
            REQUIRED_DIRECT_PATTERNS - set(direct_groups[0]["paths"]),
            "original direct impact patterns must not be removed or renamed",
        )

    def test_unrelated_changes_are_not_affected(self):
        for path in [
            "connectors/slack/pom.xml",
            "connectors/openai/pom.xml",
            "connectors-e2e-test/connectors-e2e-test-aws/pom.xml",
            "element-template-generator/openapi-parser/pom.xml",
            ".github/workflows/BUILD_PR_DOCKER_IMAGES.yml",
            "docs/adr/000-example.md",
            "renovate.json",
        ]:
            with self.subTest(path=path):
                self.assert_not_affected(path)

    def test_directory_pattern_does_not_match_a_similar_prefix(self):
        self.assert_not_affected("connectors/agentic-ai-unrelated/pom.xml")

    def test_decodes_git_diff_nul_terminated_paths(self):
        self.assertEqual(
            ["pom.xml", ".github/workflows/AI_AGENT_CPT_PR.yml"],
            classify_impact.decode_changed_files(
                b"pom.xml\0.github/workflows/AI_AGENT_CPT_PR.yml\0"
            ),
        )
        with self.assertRaisesRegex(ValueError, "NUL terminated"):
            classify_impact.decode_changed_files(b"pom.xml")

    def test_authorization_state_truth_table(self):
        cases = [
            (False, "opened", "", classify_impact.STATE_NOT_AFFECTED),
            (
                False,
                "labeled",
                classify_impact.TARGET_LABEL,
                classify_impact.STATE_NOT_AFFECTED,
            ),
            (
                False,
                "labeled",
                "unrelated-label",
                classify_impact.STATE_NOT_AFFECTED,
            ),
            (
                True,
                "labeled",
                classify_impact.TARGET_LABEL,
                classify_impact.STATE_AFFECTED_AUTHORIZED,
            ),
            (True, "opened", "", classify_impact.STATE_AFFECTED_WAITING),
            (True, "reopened", "", classify_impact.STATE_AFFECTED_WAITING),
            (True, "synchronize", "", classify_impact.STATE_AFFECTED_WAITING),
            (
                True,
                "labeled",
                "unrelated-label",
                classify_impact.STATE_AFFECTED_WAITING,
            ),
        ]
        for affected, action, label, expected in cases:
            with self.subTest(affected=affected, action=action, label=label):
                state = classify_impact.determine_state(affected, action, label)
                self.assertEqual(expected, state)
                self.assertIn(expected, classify_impact.STATE_SUMMARIES)
                summary = classify_impact.render_summary(state)
                self.assertTrue(summary.startswith("### "))
                annotation = classify_impact.render_annotation(state)
                expected_level = (
                    "::warning"
                    if state == classify_impact.STATE_AFFECTED_WAITING
                    else "::notice"
                )
                self.assertTrue(annotation.startswith(expected_level))
                self.assertNotIn("::error", annotation)
                if state == classify_impact.STATE_AFFECTED_AUTHORIZED:
                    self.assertIn("are authorized to run", summary)
                    self.assertNotIn("will run", summary)

    def test_annotations_make_each_state_visible_without_errors(self):
        expected = {
            classify_impact.STATE_AFFECTED_AUTHORIZED: (
                "::notice title=AI Agent CPT impact::AI Agent CPT changes are affected "
                "and authorized for the current head SHA."
            ),
            classify_impact.STATE_AFFECTED_WAITING: (
                "::warning title=AI Agent CPT impact::AI Agent CPT changes are affected "
                "and waiting for ai-agent-model-e2e-test authorization on the current "
                "head SHA."
            ),
            classify_impact.STATE_NOT_AFFECTED: (
                "::notice title=AI Agent CPT impact::AI Agent CPT is not affected by "
                "this change."
            ),
        }
        for state, annotation in expected.items():
            with self.subTest(state=state):
                self.assertEqual(annotation, classify_impact.render_annotation(state))
                self.assertNotIn("::error", annotation)

    def test_validate_output_tuple_accepts_only_supported_combinations(self):
        valid = {
            classify_impact.STATE_NOT_AFFECTED: (False, False),
            classify_impact.STATE_AFFECTED_WAITING: (True, False),
            classify_impact.STATE_AFFECTED_AUTHORIZED: (True, True),
        }
        for state, values in valid.items():
            with self.subTest(state=state, values=values):
                classify_impact.validate_output_tuple(*values, state)

        invalid = [
            (True, True, classify_impact.STATE_AFFECTED_WAITING),
            (True, False, classify_impact.STATE_AFFECTED_AUTHORIZED),
            (False, True, classify_impact.STATE_NOT_AFFECTED),
            (False, False, "unknown"),
        ]
        for affected, authorized, state in invalid:
            with self.subTest(
                affected=affected, authorized=authorized, state=state
            ):
                with self.assertRaisesRegex(ValueError, "impact"):
                    classify_impact.validate_output_tuple(
                        affected, authorized, state
                    )

        for value in ("", "TRUE", "0", "yes"):
            with self.subTest(value=value):
                with self.assertRaisesRegex(ValueError, "true or false"):
                    classify_impact.parse_boolean(value, "affected")

    def test_rejects_unsupported_glob_patterns(self):
        impact_map = {
            "description": "test",
            "tradeoffs": "test",
            "maintenance": "test",
            "exclusions": "test",
            "groups": [
                {
                    "name": "invalid",
                    "description": "test",
                    "paths": ["connectors/*/pom.xml"],
                }
            ],
        }
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "impact-map.json"
            path.write_text(json.dumps(impact_map), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "only exact paths"):
                classify_impact.load_impact_map(path)


if __name__ == "__main__":
    unittest.main()
