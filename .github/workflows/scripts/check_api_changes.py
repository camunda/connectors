#!/usr/bin/env python3
"""Checks the accepted API breaks in every api-changes.json (see docs/api-compatibility.md).

Every accepted break needs a justification. Below a directory listed in BREAKING_CHANGES_DOCS, the
justification must also link an existing anchor of that directory's breaking-changes document, so
that the API check and the document cannot disagree.
"""
import json
import re
import subprocess
import sys
from pathlib import Path

BREAKING_CHANGES_DOCS = {
    "connectors/agentic-ai/": "connectors/agentic-ai/docs/breaking-changes.md",
}

LINK = re.compile(r"breaking-changes\.md#([\w-]+)")


def github_anchors(markdown: str) -> set[str]:
    anchors: set[str] = set()
    counts: dict[str, int] = {}
    in_code_block = False
    for line in markdown.splitlines():
        if line.lstrip().startswith("```"):
            in_code_block = not in_code_block
            continue
        if in_code_block:
            continue
        anchors.update(re.findall(r'<a\s+(?:id|name)="([^"]+)"', line))
        heading = re.match(r"#{1,6}\s+(.*?)\s*#*\s*$", line)
        if not heading:
            continue
        text = re.sub(r"!?\[([^\]]*)\]\([^)]*\)", r"\1", heading.group(1))
        text = re.sub(r"<[^>]+>", "", text)
        slug = re.sub(r"[^\w\- ]", "", text.strip().lower()).replace(" ", "-")
        count = counts.get(slug, 0)
        counts[slug] = count + 1
        anchors.add(slug if count == 0 else f"{slug}-{count}")
    return anchors


def accepted_breaks(file: Path) -> list[dict]:
    breaks = []
    for extension in json.loads(file.read_text()):
        if extension.get("extension") == "revapi.differences":
            breaks.extend(extension.get("configuration", {}).get("differences", []))
    return breaks


def check(file: str) -> list[str]:
    doc = next((d for prefix, d in BREAKING_CHANGES_DOCS.items() if file.startswith(prefix)), None)
    anchors = github_anchors(Path(doc).read_text()) if doc else set()
    errors = []
    for difference in accepted_breaks(Path(file)):
        name = f"{file}: {difference.get('code')} {difference.get('old') or difference.get('new') or ''}".rstrip()
        justification = difference.get("justification", "").strip()
        if not justification:
            errors.append(f"{name}: missing justification")
        elif doc:
            links = LINK.findall(justification)
            if not links:
                errors.append(f"{name}: justification must link {doc}#<anchor>")
            for anchor in links:
                if anchor not in anchors:
                    errors.append(f"{name}: anchor #{anchor} does not exist in {doc}")
    return errors


def main() -> int:
    files = subprocess.run(
        ["git", "ls-files", "*api-changes.json"], capture_output=True, text=True, check=True
    ).stdout.split()
    errors = [error for file in files for error in check(file)]
    for error in errors:
        print(f"❌ {error}")
    if errors:
        print("See docs/api-compatibility.md#accepting-a-break")
        return 1
    print(f"✅ All accepted API breaks in {len(files)} file(s) are justified.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
