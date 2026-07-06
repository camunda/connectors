# ADR-0004: Storage Connector Flexibility — Two Paths for Document Handling

## Status
Proposed

## Context

The Camunda connectors project needs to support two fundamentally different ways in which documents flow through a process, and treat them consistently across every connector that consumes or produces a document:

- **Path 1 — Document Store (opaque pass-through).** The file is a blob routed between systems; the process never inspects its contents. Example: webhook receives a PDF → upload to S3.
- **Path 2 — Inline (data the process works with).** The file's content *is* process data. Users either construct it from process variables and write it to storage (the *write* side of Path 2), or read it from storage and use the values downstream in FEEL expressions (the *read* side). Path 2 does not involve the Document Store at all. It is useful for small text/JSON/CSV files.

The status quo made both paths awkward:

- **Path 2 write side had no first-class type.** Uploading `error.json` built from process variables required creating a Document Store entry and then referencing it — unnecessary modeling for a file that never leaves the process boundary except as an upload target.
- **Path 2 read side was per-connector guesswork.** Download-capable connectors each shipped a different, ad-hoc toggle:
  - **AWS S3** — a `Create document` (`asFile`) boolean; without it the connector inspected the response `Content-Type` header and either base64-encoded, parsed as JSON, or decoded as UTF-8. A JSON file served with the wrong content-type came back as unusable base64.
  - **Google Cloud Storage / Azure Blob Storage** — a `Return document as reference` boolean; the alternative was raw text, which corrupted binary payloads.
  - **Box / Google Drive** — no toggle at all; always uploaded to the Document Store.
  - **HTTP REST / GraphQL** — a `storeResponse` boolean; alternative was the raw body.
  - **CSV** — a `createDocument` boolean; alternative was the raw content object.
- **Document *input* fields were raw FEEL only.** Every connector that accepted a document exposed the same raw property, with no discoverable UX to differentiate "I already have a Camunda Document" from "I want to inline a JSON" from "I want to pull from an external URL".
- **Bug [#7049](https://github.com/camunda/connectors/issues/7049).** Downloads above Zeebe's ~4MB variable limit without the "create document" toggle silently got stuck instead of failing loudly.

The originating request was [#5590](https://github.com/camunda/connectors/issues/5590) — a customer wanting to create a JSON error report directly from process variables and upload it to S3 without the Document Store intermediary. That was rescoped into a three-step epic (parent: `camunda/product-hub#3224`):

- **Step 1** — Add an inline document type ([#7056](https://github.com/camunda/connectors/issues/7056), CLOSED).
- **Step 2** — Unified document *source* dropdown across all consumer connectors ([#7057](https://github.com/camunda/connectors/issues/7057), CLOSED).
- **Step 3** — Unified download *return format* across producer connectors ([#7058](https://github.com/camunda/connectors/issues/7058), OPEN — this PR + two follow-ups). Also fixes #7049.

## Decision

The two-paths mental model is the organising principle. Every connector that consumes or produces a document exposes a **consistent user experience** that lets users pick which path a given document takes, while the runtime — not the connector — owns the mechanics of that choice.

Two governing principles apply across all three steps:

1. **Users must not have to opt into path-selection per connector.** Whether a document is inline or a store reference, whether a download comes back as a reference or as decoded text, is declared once in the connector's annotations and materialises as a UX element automatically.
2. **The runtime owns conversion logic; the connector owns its response schema.** These are treated as separate concerns and the rest of the design flows from that separation.

### Why the runtime owns conversion

Turning bytes into a `Document`, a decoded `String`, or a parsed JSON tree is the same problem no matter which connector produced the bytes. Every connector previously reimplemented parts of it — S3 had a `Content-Type` sniff, GCS/Azure hard-coded UTF-8 text, Box/Drive hard-coded Document Store upload — and each implementation missed something. Concretely:

- Bug #7049 (process stuck on downloads above the Zeebe variable size limit) was a per-connector oversight: only S3's `asFile=true` branch checked the size, none of the others did.
- MIME inference and encoding selection have to be consistent for users to reason about them.
- Every new format the platform adds (a fourth choice, a new encoding) would otherwise require touching every download-capable connector.

Centralising the conversion in `DocumentReturnProcessor` gives one place for the size check, one place for the encoding rules, one place to log conversion decisions, and one place to add future formats. Connectors shrink to "produce a `RawPayload`, pack the result via `wrap`" and stop carrying platform-level concerns.

### Why the response schema stays connector-specific (and inconsistent)

The obvious symmetric move would be to also unify the *shape* every download returns — a common envelope like `{payload, metadata}` regardless of which connector produced it. This was explicitly *rejected*.

Downstream FEEL expressions in user processes are baked around each connector's existing response schema: `s3Result.element.content`, `gcsResult.content`, `boxResult.document`, `csvResult.data`, and so on. Changing the shape would break every one of those expressions in every existing process — a migration burden with no benefit users would ever see. Additionally, each schema carries connector-specific fields that a common envelope would either lose (S3's `bucket`+`key`, Box's `item` metadata) or force every consumer to relearn.

The trade-off: response shapes stay inconsistent between connectors — `content` vs `element.content` vs `document` vs `data` — as the price of not breaking users. The `wrap` lambda in `DocumentReturn<T>` is the mechanism that lets us do this: connectors still assemble their own response shape after the runtime hands back a converted value.

### Backward compatibility as a hard constraint

None of these three steps may break an existing element template, process, or downstream FEEL expression. This is a *hard* constraint that shaped every part of the design:

- **Existing templates keep working with the new runtime.** Old boolean toggles (`asFile`, `asDocument`, `storeResponse`, `createDocument`) remain as deprecated record components; old templates bind the boolean and hit the pre-existing legacy code path.
- **New behavior is opt-in per template version.** Only newly-generated template versions (Step 3 in this PR: S3 v5, Box v5, GDrive v8, GCS v4, Azure v5) drive the new path. A `useDocumentReturnFlow` guard picks between paths at runtime.
- **Response shapes stay unchanged** (per the previous section) — an existing FEEL expression that reads `s3Result.element.content` today keeps reading the same value regardless of whether the connector took the legacy or new path.
- **Consumer-side additions are additive** — the source dropdown (Step 2) adds Inline/External options next to the existing Camunda Document option; nothing about how existing Camunda Document references bind changes.

Three concrete deliverables realise these principles:

### Step 1 — Inline documents (delivered in #7056)

- Introduce a third document reference type, `inline`, alongside the existing `camunda` (Document Store) and `external` (URL). JSON shape:
  ```json
  {
    "camunda.document.type": "inline",
    "content": {"name": "Jane", "age": 30},
    "name": "report.json",
    "contentType": "application/json"
  }
  ```
- `content` is polymorphic — `String` → UTF-8 bytes; `Map`/`List`/`Number`/`Boolean` → Jackson serialization. `name` and `contentType` are optional; MIME is inferred from the file extension when `contentType` is absent; `application/octet-stream` and a random UUID are the fallbacks.
- No base64 encoding surface — FEEL's built-in `base64` function is the recommended path for binary use cases.
- Size is bounded by Zeebe's ~4MB variable limit. Documentation guides users to switch to the Document Store path for larger files.

### Step 2 — Unified document *source* dropdown (delivered in #7057)

- New `@TemplateDocumentProperty` annotation replaces raw `@TemplateProperty` on `Document` and `List<Document>` fields. It carries the same properties as `@TemplateProperty` plus a `FieldVisibility` enum (`REQUIRED` / `OPTIONAL` / `HIDDEN`) for `fileName` and `contentType`, both defaulting to `OPTIONAL`.
- The generator emits a dropdown with three options — **Camunda Document** (reference field, existing behavior), **Inline Content** (content + optional filename + optional content-type), **External Document** (URL + optional filename). Each option reveals only the relevant sub-fields.
- `List<Document>` fields get a *Single* / *Multiple* toggle. *Single* uses the same source dropdown; *Multiple* switches to a FEEL expression field for constructing an array.
- Field-configurable per connector: CSV, AWS Textract, and Embeddings Vector DB set `fileName`/`contentType` visibility to `HIDDEN` because they don't need them; others accept both as `OPTIONAL`. If `fileName` is required but empty, the runtime generates a UUID with no extension and uses `application/octet-stream`.
- **Consumer scope (Step 2):** S3, GCS, Azure Blob, Google Drive, Box, AWS Textract, Slack (list), Microsoft Teams (list), SendGrid (list), Email (list), AWS Bedrock (list), Embeddings Vector DB (list). CSV migrates `data` from `Object` to `Document`. IDP Extraction stays as-is; Agent AI needs a follow-up with the team.

### Step 3 — Unified download *return format* (this PR + follow-ups, #7058)

- Three-value user choice, exposed as a dropdown: **`DOCUMENT`** (upload to the document store, return a reference), **`TEXT`** (decode bytes as a string), **`JSON`** (parse as JSON). `TEXT` gets an optional encoding sub-field (default UTF-8).
- **SDK protocol.** The connector returns a `DocumentReturn<T>` carrying:
  - a `RawPayload(InputStream stream, String contentType, String fileName)` — the raw bytes plus enough metadata for the `DOCUMENT` branch to build a `DocumentCreationRequest`;
  - a `wrap: BiFunction<Object, DocumentReturnChoice, T>` — a connector-supplied lambda that packs the runtime-converted value (a `Document`, `String`, or parsed JSON tree) into the connector's own response shape (`S3.DownloadResponse`, `BoxResult.Download*`, etc.). This is the mechanism that lets response schemas stay connector-specific per the backward-compatibility constraint above.
- **Runtime conversion.** `DocumentReturnProcessor.process(DocumentReturn<?>, DocumentReturnFormat)` reads the user's choice and encoding, streams the `RawPayload` through a try-with-resources so no buffering is required for `DOCUMENT`, enforces the ~3 MiB inline-payload guard for `TEXT`/`JSON` (fix for #7049), and invokes `wrap` with the converted value. This runs before result-expression evaluation, so downstream expressions see the converted value in the shape the connector defines. A safety-net catch in `SpringConnectorJobHandler` closes the payload stream if `process()` throws before it acquires ownership.
- **Format read at the runtime boundary.** The chosen format lives in the job's input variables under `documentReturnFormat.choice` / `.encoding`. `JobHandlerContext.readDocumentReturnFormat()` extracts it directly from the raw job JSON so connectors don't have to thread it through their `execute()` signatures.
- **Generator annotation.** `@DocumentReturnFormat` on a record component or field in the connector's request POJO auto-generates the dropdown, its condition guard (visibility gated on the download-operation discriminator), and the encoding sub-field. It lives at whichever level makes Jackson bind correctly — on the download sub-type for Box and Drive, on the root request POJO for S3, GCS and Azure (they use `@NestedProperties(addNestedPath = false)`, so the discriminator sub-type isn't a Jackson path segment).
- **Producer scope (Step 3):** S3, GCS, Azure Blob, Box, Google Drive (this PR); HTTP REST/GraphQL (follow-up PR #7554); CSV (follow-up PR, `TEXT` + `DOCUMENT` only — no `JSON` since it doesn't make sense there).

### Alternatives considered for Step 3

Two ways to hand bytes from connector to runtime were considered:

- **(a) Pass a function that produces the content.** The connector returns a `Supplier<InputStream>` (or similar) and the runtime invokes it lazily. Advantage: no external resource is acquired until the runtime confirms it needs one; robust across the exception window between connector return and runtime consumption.
- **(b) Pass the input stream directly.** The connector returns an already-open stream; the runtime consumes it inside try-with-resources.

**Chosen: (b).** All storage connectors either already hand back an `InputStream` from their SDK (S3, GCS) or can be adapted cheaply (Azure, Drive, HTTP). Box needed a wrapper because its SDK only exposes a byte array — a small in-memory buffer, no worse than what it did before. Option (a)'s lazy semantics only mattered for a narrow window of exceptions between connector return and runtime consumption; a runtime safety-net catch closes that window without an SDK-wide API change.

## Consequences

### Positive

- **Consistent UX for document handling.** A single mental model (two paths) and a single UX (source dropdown for consumers, return-format dropdown for producers) apply across every document-touching connector.
- **Runtime-owned conversion.** Text decoding, JSON parsing, Document Store upload, and size-limit enforcement live once — in the SDK and runtime — instead of being scattered per connector. #7049 is fixed as a direct consequence.
- **Connector-owned response schemas.** Each connector still owns its result shape through `wrap`, so no downstream FEEL expressions break.
- **Streaming preserved end-to-end.** `RawPayload` carries an `InputStream` through try-with-resources; `DOCUMENT` uploads for S3, GCS, and the newly-streaming Google Drive path never buffer the payload.
- **Path 2 write side becomes a first-class type.** Users compose an inline document from process variables and hand it to any consumer connector uniformly.
- **All three steps ship as opt-in per template version.** Existing user flows keep working; new templates opt into the new behavior.
- **Extensibility hooks in place.** `@TemplateDocumentProperty` and `@DocumentReturnFormat` are the annotations any future document-touching connector uses; no per-connector plumbing.

### Negative

- **Two code paths live side-by-side.** Every download-capable connector has a `useDocumentReturnFlow` branch and a `newDownloadPath` / `legacyDownloadPath` pair until the deprecated boolean fields are removed.
- **Stream ownership is invisible to static analysis.** The `RawPayload` `InputStream` transfers from connector to runtime via a return value. The contract is honoured by `DocumentReturnProcessor`'s try-with-resources and by `SpringConnectorJobHandler`'s safety-net catch, but CodeQL flags the construction of `GcsStorageClosingStream` (and will flag anything similar) as a potential resource leak (`java/input-resource-leak`). It's a false positive given the runtime contract, but the noise will reappear for every new connector following this pattern.
- **Convention-based coupling between the annotation and the SDK contract.** A connector author who returns a `DocumentReturn` without declaring `@DocumentReturnFormat` in the element template will fail at conversion time with a `ConnectorException`. The wiring is discoverable by convention, not by the type system.
- **Choosing (b) over (a) means eager stream acquisition.** The connector opens the stream before the runtime knows whether it needs it. The safety-net catch is what makes (b) safe in practice; (a) would have avoided that entirely.
- **Zeebe variable size limit constrains Path 2 write side.** Inline documents above ~4MB cannot round-trip through process variables. Users of large files must use the Document Store path — documented, not enforced structurally.
- **Inbound inline handling is unresolved.** Inbound connectors (Webhook, Email IMAP/POP3, Microsoft Email) currently only produce Document Store references. A follow-up is needed to consider inline production and conversion between inline and store representations; large attachments on inline-only inbounds would today be silently dropped (they can't be surfaced via process incidents pre-instance-creation).
