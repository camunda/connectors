#!/usr/bin/env python3
"""Render data/statement-2026-09.txt as a one-page PDF with two PDF-only facts.

The statement ID and rewards line exist only in the PDF, so an agent that quotes
them proves the PDF was parsed. Synthetic demo data.
"""
import pathlib
import sys

PDF_ONLY = [
    "",
    "Statement ID: AP-STM-7Q4K-0926",
    "Rewards points earned this period: 1,737 (balance 18,204)",
]


def escape(line):
    return line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")


def render(lines):
    body = "".join("(%s) Tj T*\n" % escape(l) for l in lines)
    content = "BT /F1 8.5 Tf 36 800 Td 12 TL\n" + body + "ET"
    objs = [
        "<< /Type /Catalog /Pages 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
        "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
        "<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>",
        "<< /Length %d >>\nstream\n%s\nendstream" % (len(content), content),
    ]
    out, offsets = "%PDF-1.4\n", []
    for i, obj in enumerate(objs):
        offsets.append(len(out))
        out += "%d 0 obj\n%s\nendobj\n" % (i + 1, obj)
    xref = len(out)
    out += "xref\n0 %d\n0000000000 65535 f \n" % (len(objs) + 1)
    out += "".join("%010d 00000 n \n" % o for o in offsets)
    out += "trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(objs) + 1, xref)
    return out.encode("latin-1")


if __name__ == "__main__":
    data = pathlib.Path(__file__).resolve().parent.parent / "data"
    src = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else data / "statement-2026-09.txt"
    dst = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 else data / "statement-2026-09.pdf"
    lines = src.read_text().splitlines()
    # Insert the PDF-only facts after the header block (before "Account summary").
    at = lines.index("Account summary")
    lines[at:at] = PDF_ONLY[1:] + [""]
    dst.write_bytes(render(lines))
    print(dst)
