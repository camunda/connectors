# Epic: Improve Storage Connector Flexibility (Direct Creation & Content Conversion)

## Mental Model: Two Paths for Document Handling

**Path 1: Document Store (opaque pass-through)** — The document is a blob moving through the process. The user doesn't need to read or manipulate its content — they just route it. Webhook receives a PDF → upload it to S3. Download an image from GCS → send it via email. The process never "looks inside" the file.

**Path 2: Inline (data the process works with)** — The content is actual process data, not uploaded to the document store. The user either:
- **Writes** it: constructs a JSON/CSV/TXT from process variables and uploads it to storage ("Direct Upload")
- **Reads** it: downloads from storage "As JSON" or "As text" and uses the values directly in FEEL expressions downstream

In Path 2, there's no Document Store involved. The data flows as regular process variables — strings, JSON objects — that happen to be written to or read from cloud storage.

This framing also clarifies the download return format options: "Document reference" = Path 1 (put it in the store, pass it around). "As text" / "As JSON" / "As base64" = Path 2 (give me the data, I want to use it in my process).

---

## Step 1: Create Files Directly from Process Variables ("Inline Documents")

**What:** Users can create and upload files (JSON, CSV, TXT, etc.) to cloud storage directly from process data — no Document Store intermediary needed.

**Why:** Today, if a user wants to store an error report as `error.json` in S3, they must first create a Document Store entry, then reference it. This adds unnecessary modeling complexity for simple text-based file creation.

**User experience:** In the connector properties panel, when a Document input is expected, a new "Direct Upload" option appears. The user provides the content (can be a FEEL expression building the data dynamically) and a filename. Content type is inferred from the file extension via Apache Tika.

**Scope:** Content is provided as a text string or JSON object. For binary file creation, users can use FEEL's `base64encode()` / `base64decode()` functions to encode/decode content. This covers both text-based files (JSON, CSV, TXT, XML) and binary files encoded as base64 via FEEL.

**File size considerations:** Inline document content is embedded in process variables, which are subject to Zeebe's variable size limit (~4MB). Documentation should provide clear guidance on when users should use the Document Store path instead (e.g., for large files).

**Implementation:** New `inline` document type at the platform level (alongside existing `camunda` and `external` types). Discriminator value: `"inline"`. Fields: `content` (polymorphic Object — string, JSON object, array, number) and `name` (filename with extension). The `InlineDocument` class implements the `Document` interface — no connector-side changes needed, since all connectors call `document.asByteArray()` and `document.metadata()`.

**No `encoding` field.** Content is always literal. FEEL's `base64encode()` handles binary encoding.

**Details:** [Step 1 spec](document-epic-step-1-inline-document.md)

## Step 2: Unified Document Source Selection Across All Connectors

**What:** Every connector that accepts a document input gets a consistent dropdown: **Camunda Document** / **Direct Upload** / **External Document**.

**Why:** Today, document inputs are a raw text field where users paste a document reference expression. There's no guidance on what types of documents are accepted or how to provide them. The "External Document" type (added recently) isn't discoverable in the UI.

**User experience:** A clear dropdown replaces the raw field. Each option reveals only the relevant input fields:

- *Camunda Document* → reference field (existing behavior)
- *Direct Upload* → content + filename
- *External Document* → URL + optional filename

**Implementation:** The ETG auto-detects `Document` typed fields and generates: a source dropdown, conditional input fields per source type, and a hidden FEEL composer field (the only `zeebe:input` binding) that assembles the final document JSON from the visible field values. Visible fields use `zeebe:property` bindings — stored in BPMN XML but NOT sent to the connector. This keeps the connector input clean.

**Field ID convention:** IDs derived from binding path + source type namespace — e.g., `action.document.inline.content`, `action.document.external.url`. Collision-free and predictable.

**List\<Document\> handling — Single/Multiple toggle:** For connectors accepting multiple documents (e.g., Slack attachments, SendGrid attachments), a toggle switches between:
- *Single document* → shows the same source dropdown with its fields
- *Multiple documents* → switches to a FEEL expression field where users construct an array

**Scope:** All 13 connectors with Document inputs use ETG — regeneration handles everything, no manual template updates needed.

**Details:** [Step 2 spec](document-epic-step-2-etg-source-selection.md)

## Step 3: User-Controlled Download Return Format

**What:** When downloading/retrieving files, users choose how they want the content returned instead of the connector guessing based on the file's content type.

**Why:** The current behavior is fragile and inconsistent:

- S3 guesses format from content-type headers (JSON file with wrong content-type → returns unusable base64)
- GCS always returns raw text (binary files come back as garbled text)
- Azure always returns raw text
- Box and Google Drive always return Document Store references with no alternative
- Users have no way to recover from wrong format decisions

**User experience:** A 4-option dropdown replaces the current boolean toggles (`asFile`, `asDocument`, `storeResponse`):

| Option | Behavior | Process variable type |
|---|---|---|
| **Document reference** | Save to Document Store, return reference | Document reference object |
| **As text** | `new String(bytes, charset)` | JSON string |
| **As JSON** | `objectMapper.readTree(bytes)` — incident if invalid | JSON object (direct FEEL access) |
| **As base64 string** | `Base64.encode(bytes)` | JSON string (base64) |

"As text" has an optional encoding sub-field (default UTF-8).

**Implementation:** New `DocumentResponse` type in connector-sdk/core with a builder API. Conversion logic written once in the SDK — all connectors call the same builder, no duplicated switch logic. The ETG auto-detects `DocumentResponse` typed fields and generates the dropdown + conditional encoding field. The `documentCreator` is passed as a function (`Function<DocumentCreationRequest, Document>`) — only what's needed, no full context injection.

**Scope:** 6 connectors get the return format dropdown:

| Connector | Current toggle | Changes |
|---|---|---|
| AWS S3 | `asFile` (boolean) | Replace with DocumentResponse |
| Google Cloud Storage | `asDocument` (boolean) | Replace with DocumentResponse |
| Azure Blob Storage | `asFile` (boolean) | Replace with DocumentResponse |
| Box | None (always doc store) | Add DocumentResponse to download |
| Google Drive | None (always doc store) | Add DocumentResponse to download |
| HTTP REST / GraphQL | `storeResponse` (boolean) | Replace with DocumentResponse |

**Not included:** CSV connector (always produces text — keeps its own `createDocument` boolean).

**Breaking change:** Old boolean values (`true`/`false`) stop working. New element template version. Migration guide required.

**Details:** [Step 3 spec](document-epic-step-3-return-format.md)

## Step 4: Element Template Quality Verification

**What:** An automated CI check that ensures all connector templates follow the new document handling patterns.

**Why:** With 13+ connectors and growing, manual review doesn't scale. Ensures new connectors automatically get correct document UX.

**Implementation:** A JUnit test that loads all element template JSON files in the repository and validates:
1. Every property bound to a `Document`-typed field has the source dropdown pattern
2. Correct conditional field structure under each dropdown option
3. Download connectors have the return format dropdown instead of boolean toggles

## Connector Inventory

### Connectors that CONSUME documents (upload/send) — source dropdown

| Connector | Field | Type | Gets source dropdown? |
|---|---|---|---|
| AWS S3 | `document` | `Document` | **Yes** — dropdown |
| Google Cloud Storage | `document` | `Document` | **Yes** — dropdown |
| Azure Blob Storage | `document` | `Document` | **Yes** — dropdown |
| Google Drive | `document` | `Document` | **Yes** — dropdown |
| Box | `document` | `Document` | **Yes** — dropdown |
| AWS Textract | `document` | `Document` | **Yes** — dropdown |
| IDP Extraction (4 variants) | `document` | `Document` | **Yes** — dropdown |
| Slack | `documents` | `List<Document>` | Single/Multiple toggle |
| Microsoft Teams | `documents` | `List<Document>` | Single/Multiple toggle |
| SendGrid | `attachments` | `List<Document>` | Single/Multiple toggle |
| Email (Outbound) | `attachments` | `List<Document>` | Single/Multiple toggle |
| AWS Bedrock | `newDocuments` | `List<Document>` | Single/Multiple toggle |
| Embeddings Vector DB | `newDocuments` | `List<Document>` | Single/Multiple toggle |

### Connectors that PRODUCE documents (download/receive) — return format dropdown

| Connector | Direction | Current behavior | Gets return format dropdown? |
|---|---|---|---|
| AWS S3 | Outbound | Content-type based switching (broken) | **Yes** |
| Google Cloud Storage | Outbound | Always returns string | **Yes** |
| Azure Blob Storage | Outbound | Always returns string | **Yes** |
| Box | Outbound | Document Store only | **Yes** (new) |
| Google Drive | Outbound | Document Store only | **Yes** (new) |
| HTTP REST / GraphQL | Outbound | `storeResponse` boolean toggle | **Yes** |
| Webhook | Inbound | Document Store only | **No** — stays as-is |
| Email (IMAP/POP3) | Inbound | Document Store only | **No** — stays as-is |
| Microsoft Email | Inbound | Document Store only | **No** — stays as-is |

### Connectors that handle documents in BOTH directions

| Connector | Consumes (upload/send) | Produces (download/receive) |
|---|---|---|
| AWS S3 | Document input (upload) | Download with return format |
| Google Cloud Storage | Document input (upload) | Download with return format |
| Azure Blob Storage | Document input (upload) | Download with return format |
| Box | Document input (upload) | Download with return format |
| Google Drive | Document input (upload) | Download with return format |

## Delivery

All steps ship together in one release. The inline document type is a platform-level feature (runtime + SDK), so all connectors benefit automatically. The ETG changes auto-generate the correct templates for both source selection and return format.

## Open Questions / Future Considerations

- **`getJson()` / `getText()` in result expressions:** Currently only works in input fields, not in result expressions. Fixing this would be the cleaner long-term solution (FEEL-side conversion instead of Java-side DocumentResponse). Could be a follow-up that eventually simplifies the return format approach. Not addressed in this epic.
- **Webhook → downstream connector flow:** When a JSON file is uploaded via webhook and needs to be used in the next connector, users currently need to use `getJson()` in input fields. The "As JSON" return format doesn't help here — separate UX gap.
- **Migration guide:** The download return format change is breaking. Needs docs coordination.
