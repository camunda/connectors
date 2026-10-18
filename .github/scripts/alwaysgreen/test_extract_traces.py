"""Tests for safe Playwright trace extraction."""

from __future__ import annotations

import stat
from pathlib import Path
from zipfile import ZipFile, ZipInfo

import pytest

import extract_traces


def test_extracts_trace_next_to_archive(tmp_path: Path):
    archive = tmp_path / "attempt-1" / "trace.zip"
    archive.parent.mkdir()
    with ZipFile(archive, "w") as zipped:
        zipped.writestr("trace.trace", "events")
        zipped.writestr("resources/page.html", "<html></html>")

    assert extract_traces.extract_traces(tmp_path) == 1
    assert (archive.parent / "trace-extracted/trace.trace").read_text() == "events"
    assert (
        archive.parent / "trace-extracted/resources/page.html"
    ).read_text() == "<html></html>"


def test_rejects_path_traversal(tmp_path: Path):
    archive = tmp_path / "trace.zip"
    with ZipFile(archive, "w") as zipped:
        zipped.writestr("../outside", "unsafe")

    with pytest.raises(ValueError, match="unsafe trace archive member"):
        extract_traces.extract_traces(tmp_path)
    assert not (tmp_path.parent / "outside").exists()


def test_rejects_symlinks(tmp_path: Path):
    archive = tmp_path / "trace.zip"
    symlink = ZipInfo("link")
    symlink.create_system = 3
    symlink.external_attr = (stat.S_IFLNK | 0o777) << 16
    with ZipFile(archive, "w") as zipped:
        zipped.writestr(symlink, "/etc/passwd")

    with pytest.raises(ValueError, match="unsafe trace archive member"):
        extract_traces.extract_traces(tmp_path)


def test_rejects_oversized_archives(tmp_path: Path, monkeypatch):
    monkeypatch.setattr(extract_traces, "MAX_UNCOMPRESSED_BYTES", 4)
    archive = tmp_path / "trace.zip"
    with ZipFile(archive, "w") as zipped:
        zipped.writestr("trace.trace", "large")

    with pytest.raises(ValueError, match="too large"):
        extract_traces.extract_traces(tmp_path)
