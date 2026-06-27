---
name: sandbox-doc-tools
description: |
  Use this skill whenever a document is present in the conversation (a user-prompt
  attachment, or a file returned by another tool) and the user wants it inspected,
  analyzed, or transformed. It brings the in-context document into the sandbox
  filesystem, runs ordinary shell tools on it, and can hand a derived file back as a
  new Camunda document. Good for: "what's in this file?", word/line counts, CSV
  column summaries, extracting text, converting formats, or producing a cleaned-up
  copy.

  Do not use it for documents you do not actually have a `<doc id="…"/>` handle for —
  you can only import documents that already appear in the conversation.
---

# Sandbox Document Tools

You operate documents inside a persistent sandbox workspace. Every document already in
the conversation is labelled with a self-closing marker that carries a stable handle:

```
<doc id="…" fileName="…" contentType="…" />
```

The `id` is the only thing you need to bring that document into the workspace — you do
**not** invent paths, URLs, or document ids.

## Workflow

1. **Find the handle.** Locate the `<doc id="…"/>` marker for the document the user is
   referring to (in the user prompt or in an earlier tool result). Use its `id`.
2. **Import it.** Call `sandbox_import_document` with that `id`. The file lands in the
   working directory under its original file name (or pass an explicit `path`). The
   result tells you the final path, size, and content type.
3. **Inspect / process with `sandbox_bash`.** Use normal shell tools against the
   imported path. Helpful starting points:
   - `file <path>` — confirm the real type.
   - text/CSV: `wc -l <path>`, `head -n 20 <path>`, `cut -d, -f1 <path> | sort | uniq -c`.
   - PDF: `pdftotext <path> - | head` if `pdftotext` is available; otherwise report
     that it is binary and summarize size/type instead of guessing content.
   - A bundled helper is available: `bash scripts/inspect.sh <path>` prints a quick
     summary (paths are relative to the skill root).
4. **Optionally produce a result.** Write a derived file with `sandbox_fs_write` or
   `sandbox_bash`, then call `sandbox_export_document` on it to return it to the user as
   a new Camunda document. Mention that it will appear as an attachment in your reply.
5. **Report.** Summarize what you found (and what you produced) in plain language. Do
   not paste large binary blobs back into the conversation.

## Available scripts

- **`scripts/inspect.sh`** — prints a quick summary of a file (size, type, line count,
  and a short text preview). Usage: `bash scripts/inspect.sh <path>`. Paths are relative
  to the skill root.

## Rules

- Only import documents you have a real `<doc id="…"/>` handle for. If the user asks
  about a document that isn't in the conversation, say so — never fabricate an id.
- Keep large content in the sandbox; surface summaries, not raw bytes.
- Prefer the structured tools (`sandbox_fs_read`/`sandbox_fs_write`) for small text and
  `sandbox_bash` for everything else.
