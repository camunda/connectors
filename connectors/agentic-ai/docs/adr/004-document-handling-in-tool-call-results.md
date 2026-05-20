# Document handling in tool call results

* Deciders: Agentic AI Team
* Date: Apr 10, 2026

## Status

**Implemented** (PR #6999).

## Context and Problem Statement

BPMN tool activities (modeled as ad-hoc subprocess tasks) can return complex data structures containing Camunda
Documents, either at the root level or nested within maps and lists. When these tool call results are passed to the LLM,
the documents need to be represented in a way the model can actually interpret.

The current implementation converts `ToolCallResult.content()` to a single JSON text string for LangChain4J's
`ToolExecutionResultMessage`. Documents encountered during Jackson serialization are converted to a Claude-specific
content block format (`DocumentToContentSerializer`) containing base64-encoded data embedded within the text. This
approach does not work -- models cannot meaningfully interpret large base64 blobs embedded in tool result text. The
natural alternative — passing documents as native multi-modal content on the tool result — is also blocked today by
how providers and LangChain4J interact: providers vary in what they accept as tool result content (e.g. the OpenAI
Responses API and Anthropic accept rich content blocks; the OpenAI Chat Completions API is narrower), and LangChain4J's
provider adapters only partially expose that capability (e.g. `OpenAiChatModel` surfaces images but not other binary
types). See "Option 1" below for the full rationale.

In contrast, documents provided via user messages already work correctly: they are converted to proper LangChain4J
content types (`ImageContent`, `PdfFileContent`, `TextContent`) through `DocumentToContentConverterImpl` and arrive at
the model as native multi-modal content blocks.

## Decision Drivers

* **Model compatibility**: Documents must be provided in a format that LLMs can actually process.
* **Provider independence**: The solution must work across all supported providers, not just those with native
  multi-content tool result support.
* **Auditability**: Document handling must be visible in the persisted conversation history.
* **Abstraction layer**: Changes should be made in the generic agent layer (internal message model), not deep in the
  LangChain4J framework adapter, so they are framework-agnostic and auditable.
* **Consistency**: Reuse existing, proven document handling infrastructure where possible.

## Considered Options

### Option 1: Multi-content `ToolExecutionResultMessage` (L4J-native)

Extract documents from tool call results and add them as separate `Content` blocks on the LangChain4J
`ToolExecutionResultMessage`, which supports `List<Content>` since v1.12.

**Rejected** because:

- Native multi-content tool results are limited by a combination of factors: providers themselves vary in what
  they accept as tool result content (e.g. the OpenAI Responses API and Anthropic accept rich content blocks; the
  OpenAI Chat Completions API is narrower), and LangChain4J's provider adapters only partially expose that
  capability — `OpenAiChatModel`, for instance, surfaces images but not other binary types, and broader coverage
  would require migrating to richer adapters such as `OpenAiResponsesChatModel`.
- Changes would be invisible in the conversation history (only visible at the L4J wire format level).
- Tightly couples the solution to LangChain4J capabilities.

### Option 2: Text-only (describe binary documents, inline text documents)

Serialize all documents as textual descriptions (filename, content type) without providing actual content.

**Rejected** because the model cannot analyze documents it cannot see. This defeats the purpose for use cases that
require visual/content analysis of tool-returned documents.

### Option 3: Extract documents into appended user messages (selected)

Extract all documents from tool call results into a follow-up `UserMessage` with `DocumentContent` blocks. The tool
result text retains a document reference (serialized by the default connectors `DocumentSerializer`) so the model can
correlate the reference with the actual content in the user message.

## Decision

**Option 3**: Extract documents from tool call results into appended user messages.

### Design

**Generic layer** (`AgentMessagesHandlerImpl`):

1. After building the `ToolCallResultMessage`, scan each `ToolCallResult.content()` for `Document` instances using
   `ToolCallResultDocumentExtractor`. The extractor delegates per result to the responsible
   `GatewayToolHandler.extractDocuments(...)` (with `ContentTreeDocumentWalker` as the default fallback for results not
   managed by any gateway handler) — see "Per-handler document extraction" below.
2. Build a single `UserMessage` (metadata: `toolCallDocuments=true`) containing a preamble, per-document XML tags with
   correlation attributes, and `DocumentContent` blocks.
3. Apply the same extraction to event messages: prepend `<document>` XML tags before each `DocumentContent` block in the
   event `UserMessage`.
4. Message ordering: `ToolCallResultMessage` → document `UserMessage` → event `UserMessage`(s).

**LangChain4J layer** (`ToolCallConverterImpl`):

1. Serialize `ToolCallResult.content()` using the default connectors `ObjectMapper`, which serializes `Document`
   instances as document references (store ID, document ID, metadata with content type and filename) via the standard
   `DocumentSerializer`.
2. Remove the `ContentConverter` dependency; the custom `DocumentToContentModule` / `DocumentToContentSerializer` /
   `DocumentToContentResponseModel` infrastructure is deleted.

**Conversation history**: The `ToolCallResultMessage` retains `Document` objects in its content tree (serialized as
references when persisted). The follow-up `UserMessage` with `DocumentContent` blocks is a regular message in the
conversation. Both are visible and auditable.

### Document user message format

A document is rendered as a pair of adjacent content blocks: a self-closing `<doc />` XML tag with correlation
attributes, immediately followed by a `DocumentContent` block carrying the actual bytes. The pairs are grouped under a
short preamble that names the source (tool calls vs event data) and makes the pair structure explicit.

Attribute names mirror the JSON field names emitted by the standard `DocumentSerializer` (e.g. `documentId`, `storeId`,
`url`, `name`), so the model can correlate a reference in the tool result JSON with its content block 1:1 without
inferring partial-id matches. The tag shape is dispatched on the document's reference type:

* `CamundaDocumentReference` → `documentId`, `storeId`, `contentType`, `fileName` — Camunda references read their
  metadata from the document store in-memory on resolve, so surfacing it on the tag is free.
* `ExternalDocumentReference` → `url`, `name` — no `contentType` / `fileName`: `Document.metadata()` on an external doc
  reads HTTP response headers, which would trigger a network fetch just to render a label.
* any other reference (including inline) → `toolName`, `toolCallId` only.

All attribute values are XML-escaped, and blank attributes are omitted. Tag name and preamble are intentionally terse
to minimise token usage.

#### Example: tool call result documents

A `ToolCallResultMessage` followed by a synthetic `UserMessage` (metadata `toolCallDocuments=true`) carrying the extracted
documents:

```
ToolCallResultMessage:
  - { id: "call_1", name: "generate_report", content: { "report": <Document>, "chart": <Document> } }
  - { id: "call_2", name: "fetch_data", content: { "csv": <Document> } }

UserMessage (toolCallDocuments=true):
  TextContent: "Documents extracted from tool calls (<doc /> tag + content pair):"
  TextContent: <doc toolName="generate_report" toolCallId="call_1" documentId="25ece9fa-aeea-423d-98ed-67c1f08b137b" storeId="in-memory" contentType="application/pdf" fileName="report.pdf" />
  DocumentContent: [report.pdf content]
  TextContent: <doc toolName="generate_report" toolCallId="call_1" documentId="f7b3a1d0-1234-5678-9abc-def012345678" storeId="in-memory" contentType="image/png" fileName="chart.png" />
  DocumentContent: [chart.png content]
  TextContent: <doc toolName="fetch_data" toolCallId="call_2" url="https://example.com/data.csv" name="Q3 metrics" />
  DocumentContent: [data.csv content]
```

#### Example: event message with documents

Events from non-interrupting event sub-processes produce a `UserMessage` whose first content block is the event payload
itself; if the payload contains documents, the preamble + `<doc />` pairs are appended in the same message. Event tags
omit `toolName` and `toolCallId` since events are not associated with a specific tool call:

```
UserMessage:
  ObjectContent: { "type": "invoice_arrived", "invoice": <Document> }
  TextContent: "Documents extracted from event data (<doc /> tag + content pair):"
  TextContent: <doc documentId="9a2b1c4d-5e6f-7890-abcd-ef1234567890" storeId="in-memory" contentType="application/pdf" fileName="invoice-Q3-2025.pdf" />
  DocumentContent: [invoice-Q3-2025.pdf content]
```

#### Example: combined — tool call results + event in one iteration

When `WAIT_FOR_TOOL_CALL_RESULTS` is configured, the worker waits for in-flight tool calls to finish before emitting
event messages. A single `addUserMessages` iteration can then produce a `ToolCallResultMessage`, a synthetic
tool-call-documents `UserMessage`, and one or more event `UserMessage`s in that order:

```
ToolCallResultMessage:
  - { id: "call_1", name: "getWeather", content: { "report": <Document> } }
  - { id: "call_2", name: "getDateTime", content: "15:00" }

UserMessage (toolCallDocuments=true):
  TextContent: "Documents extracted from tool calls (<doc /> tag + content pair):"
  TextContent: <doc toolName="getWeather" toolCallId="call_1" documentId="25ece9fa-..." storeId="in-memory" contentType="text/plain" fileName="weather.txt" />
  DocumentContent: [weather.txt content]

UserMessage:
  ObjectContent: { "type": "invoice_arrived", "invoice": <Document> }
  TextContent: "Documents extracted from event data (<doc /> tag + content pair):"
  TextContent: <doc documentId="9a2b1c4d-..." storeId="in-memory" contentType="application/pdf" fileName="invoice.pdf" />
  DocumentContent: [invoice.pdf content]
```

### Message window memory

The synthetic document `UserMessage` (identified by `UserMessage.METADATA_TOOL_CALL_DOCUMENTS`) does not count toward
the `maxMessages` context window limit. When evicting messages, the document `UserMessage` is removed together with its
associated `ToolCallResultMessage` — it is never orphaned.

### Per-handler document extraction

Gateway tool handlers (MCP, A2A) transform `ToolCallResult` objects — renaming tool calls with fully qualified
identifiers and producing typed domain objects (`McpClientCallToolResult`, `A2aSendMessageResult`) as the transformed
`content()`. Each handler exposes a domain-specific `extractDocuments(ToolCallResult)` method on the
`GatewayToolHandler` SPI that walks its own typed structure (sealed-type `switch`) to collect `Document` instances:

* MCP: walks `McpClientCallToolResult.content()` and matches `McpDocumentContent` and
  `McpEmbeddedResourceContent.BlobDocumentResource`.
* A2A: walks `A2aSendMessageResult` (sealed `A2aMessage | A2aTask`), descending into artifacts and recursive task
  history to collect `DocumentContent` instances.

`ToolCallResultDocumentExtractor` routes each result to the handler that manages it (via
`GatewayToolHandlerRegistry.handlerForToolDefinition(toolName)`). When no handler claims the result — typical for plain
BPMN tool calls whose `content()` is a raw `Map`/`List`/`Document` tree from the engine — the extractor falls back to
`ContentTreeDocumentWalker`, which performs the original `instanceof`-based recursion over `Map`, `Collection`,
`Object[]`, and `Document`.

The walker is also public: handler implementations whose typed content embeds raw user-generated subtrees (e.g. opaque
`Map<String, Object>` payloads from a downstream system) can call `ContentTreeDocumentWalker.extractDocumentsFromContent(...)` for those parts.

The default `GatewayToolHandler.extractDocuments` implementation delegates to the walker, so third-party handlers that
return raw content do not need to override anything.

For simple text-only MCP results, the handler still extracts the text string directly (optimization to avoid
unnecessary JSON wrapping); in that case `extractDocuments` returns an empty list since the content is a `String`.

### Future optimization (out of scope)

A follow-up optimization can promote specific document types from the user message back into the
`ToolExecutionResultMessage` for providers that support native multi-content tool results (e.g., images on Anthropic).
This would be a post-processing step in the L4J framework adapter, transparent to the generic layer and conversation
history. The document `UserMessage` can be rebuilt from the `ToolCallResult` content tree (by re-running extraction)
with only the non-promoted documents remaining.

### Future deduplication across messages (out of scope)

The extractor deduplicates documents within a single tool call result (`HashSet<DocumentReference>` keyed by reference
equality), so a document referenced from multiple paths in one result's content tree contributes a single
`<doc />` + `DocumentContent` pair. Two further dedup tiers would be useful but are not implemented:

* **Within the current turn**: if the same document is returned by two different tool calls in the same iteration, it
  is emitted twice (once per call) so the model sees both correlation IDs. We could emit the content block once and
  reuse a stable correlation ID across the duplicate `<doc />` tags.
* **Across message history**: the same document may already be present (as a `DocumentContent`) in an earlier
  message in the conversation. We could omit the duplicate content block and emit only the `<doc />` tag, letting the
  model rely on the earlier content.

Both require tracking document identity across messages and reasoning about the message-window eviction rules.

## Consequences

### Positive

* Documents in tool call results become visible to the LLM across all providers.
* Reuses the existing, proven `DocumentToContentConverterImpl` path (same as user prompt documents).
* Conversation history shows the full picture: tool results with document references + user message with document
  content.
* Removes ~3 classes of stale document-to-base64 serialization infrastructure.
* Text documents (JSON, XML, plain text) rendered as readable text; binary documents (PDF, images) rendered as native
  multi-modal content blocks.

### Negative

* Adds one extra user message to the conversation when tool results contain documents, consuming additional tokens.
* The model must correlate document references in the tool result text with document content in the follow-up user
  message. The XML tags with tool name, call ID, and document short ID mitigate this.
* Slight increase in conversation history size due to the additional message.
