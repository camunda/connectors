#!/usr/bin/env python3
"""Safely extract Playwright trace archives for the file-only fix agent."""

from __future__ import annotations

import shutil
import stat
import sys
from pathlib import Path, PurePosixPath
from zipfile import ZipFile


MAX_ARCHIVES = 50
MAX_MEMBERS = 10_000
MAX_UNCOMPRESSED_BYTES = 512 * 1024 * 1024


def extract_traces(root: Path) -> int:
    root = root.resolve()
    archives = sorted(root.rglob("trace.zip"))
    if len(archives) > MAX_ARCHIVES:
        raise ValueError(f"refusing to extract {len(archives)} trace archives")

    member_count = 0
    uncompressed_bytes = 0
    for archive in archives:
        destination = archive.with_name("trace-extracted")
        destination.mkdir(exist_ok=True)
        destination = destination.resolve()

        with ZipFile(archive) as zipped:
            for member in zipped.infolist():
                member_count += 1
                uncompressed_bytes += member.file_size
                if member_count > MAX_MEMBERS:
                    raise ValueError("trace archives contain too many files")
                if uncompressed_bytes > MAX_UNCOMPRESSED_BYTES:
                    raise ValueError("trace archives are too large when extracted")

                relative = PurePosixPath(member.filename)
                mode = member.external_attr >> 16
                file_type = stat.S_IFMT(mode)
                if (
                    not relative.parts
                    or relative.is_absolute()
                    or ".." in relative.parts
                    or "\\" in member.filename
                    or stat.S_ISLNK(mode)
                    or file_type not in {0, stat.S_IFREG, stat.S_IFDIR}
                ):
                    raise ValueError(f"unsafe trace archive member: {member.filename}")

                target = destination.joinpath(*relative.parts).resolve()
                if not target.is_relative_to(destination):
                    raise ValueError(f"trace member escapes extraction root: {member.filename}")
                if member.is_dir():
                    target.mkdir(parents=True, exist_ok=True)
                    continue

                target.parent.mkdir(parents=True, exist_ok=True)
                with zipped.open(member) as source, target.open("wb") as output:
                    shutil.copyfileobj(source, output)

    return len(archives)


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit(f"usage: {Path(sys.argv[0]).name} ARTIFACT_ROOT")
    count = extract_traces(Path(sys.argv[1]))
    print(f"extracted {count} Playwright trace archive(s)")


if __name__ == "__main__":
    main()
