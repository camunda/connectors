#!/usr/bin/env python3
"""Tests for check_api_changes.py. Standard library only, like the other CI scripts."""

import unittest

from check_api_changes import github_anchors, linked_anchors, links_to


class LinkedAnchorsTest(unittest.TestCase):
    def test_captures_the_complete_fragment(self):
        self.assertEqual(
            linked_anchors("[migration](docs/breaking-changes.md#module-split.extra)"),
            [("docs/breaking-changes.md", "module-split.extra")],
        )

    def test_ignores_sentence_punctuation_after_the_link(self):
        self.assertEqual(
            linked_anchors("Removed, see docs/breaking-changes.md#module-split."),
            [("docs/breaking-changes.md", "module-split")],
        )

    def test_stops_at_markdown_link_delimiter(self):
        self.assertEqual(
            linked_anchors("[a](breaking-changes.md#module-split) and more"),
            [("breaking-changes.md", "module-split")],
        )


class LinksToTest(unittest.TestCase):
    DOC = "connectors/agentic-ai/docs/breaking-changes.md"

    def test_accepts_paths_and_urls_of_the_document(self):
        for path in [
            "breaking-changes.md",
            "./docs/breaking-changes.md",
            "agentic-ai/docs/breaking-changes.md",
            self.DOC,
            "https://github.com/camunda/connectors/blob/main/" + self.DOC,
        ]:
            self.assertTrue(links_to(path, self.DOC), path)

    def test_rejects_a_wrong_file_name(self):
        path = linked_anchors("[m](docs/not-breaking-changes.md#module-split)")[0][0]
        self.assertFalse(links_to(path, self.DOC))

    def test_rejects_a_wrong_directory(self):
        path = linked_anchors("[m](docs/missing/breaking-changes.md#module-split)")[0][0]
        self.assertFalse(links_to(path, self.DOC))


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
