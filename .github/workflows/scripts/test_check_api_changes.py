#!/usr/bin/env python3
"""Tests for check_api_changes.py. Standard library only, like the other CI scripts."""

import unittest

from check_api_changes import github_anchors, linked_anchors


class LinkedAnchorsTest(unittest.TestCase):
    def test_captures_the_complete_fragment(self):
        self.assertEqual(
            linked_anchors("[migration](docs/breaking-changes.md#module-split.extra)"),
            ["module-split.extra"],
        )

    def test_ignores_sentence_punctuation_after_the_link(self):
        self.assertEqual(
            linked_anchors("Removed, see docs/breaking-changes.md#module-split."),
            ["module-split"],
        )

    def test_stops_at_markdown_link_delimiter(self):
        self.assertEqual(
            linked_anchors("[a](breaking-changes.md#module-split) and more"), ["module-split"]
        )


class GithubAnchorsTest(unittest.TestCase):
    def test_slugifies_headings_like_github(self):
        anchors = github_anchors("## Camunda 8.10 to 8.11\n#### `ConversationStore`\n")
        self.assertEqual(anchors, {"camunda-810-to-811", "conversationstore"})

    def test_numbers_duplicate_headings(self):
        self.assertEqual(github_anchors("## Moved\n## Moved\n"), {"moved", "moved-1"})

    def test_ignores_headings_in_code_blocks(self):
        self.assertEqual(github_anchors("```\n# comment\n```\n"), set())


if __name__ == "__main__":
    unittest.main()
