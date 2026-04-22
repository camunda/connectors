# Document handling in tool call results

* Deciders: Agentic AI Team
* Date: Apr 10, 2026

## Status

**Proposed**.

## Context and Problem Statement

BPMN tool activities (modeled as ad-hoc subprocess tasks) can return complex data structures containing Camunda
Documents, either at the root level or nested within maps and lists. When these tool call results are passed to the LLM,
the documents need to be represented in a way the model can actually interpret.

The current implementation converts `ToolCallResult.content()` to a single JSON text string for LangChain4J's
`ToolExecutionResultMessage`. Documents encountered during Jackson serialization are converted to a Claude-specific
content block format (`DocumentToContentSerializer`) containing base64-encoded data embedded within the text. This
approach does not work -- models cannot meaningfully interpret large base64 blobs embedded in tool result text.

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

- LangChain4J provider adapters have inconsistent support for multi-content tool results across providers.
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

1. After building the `ToolCallResultMessage`, scan each `ToolCallResult.content()` tree for `Document` instances using
   `ToolCallResultDocumentExtractor` (handles `Map`, `Collection`, `Object[]`, and `Document`).
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

The document `UserMessage` contains interleaved `TextContent` tags and `DocumentContent` blocks. Each document is
preceded by a self-closing XML tag with correlation attributes:

```
TextContent: "Documents extracted from tool call results:"
TextContent: <document tool-name="generate_report" tool-call-id="call_1" document-short-id="25ece9fa" filename="report.pdf" />
DocumentContent: [report.pdf content]
TextContent: <document tool-name="generate_report" tool-call-id="call_1" document-short-id="f7b3a1d0" />
DocumentContent: [chart.png content]
TextContent: <document tool-name="fetch_data" tool-call-id="call_2" document-short-id="c44d82e1" filename="data.csv" />
DocumentContent: [data.csv content]
```

The `document-short-id` is the first segment of the document's UUID identifier (e.g. `25ece9fa` from
`25ece9fa-aeea-423d-98ed-67c1f08b137b`). It provides a compact correlation key for the model to match the reference in
the tool result JSON with the actual content. All attribute values are XML-escaped.

For event documents, the same `<document>` tag format is used, but without `tool` and `call-id` attributes since events
are not associated with a specific tool call.

### Message window memory

The synthetic document `UserMessage` (identified by `UserMessage.METADATA_TOOL_CALL_DOCUMENTS`) does not count toward
the `maxMessages` context window limit. When evicting messages, the document `UserMessage` is removed together with its
associated `ToolCallResultMessage` — it is never orphaned.

### Gateway tool handlers must preserve raw content

Gateway tool handlers (MCP, A2A) transform `ToolCallResult` objects — renaming tool calls with fully qualified
identifiers and processing the result content. When tool call results arrive from the process engine, `Document`
instances in the content tree have already been deserialized by the connectors `ObjectMapper` (which recognizes
`camunda.document.type` references). The content is a raw tree of `Map`, `List`, `String`, and `Document` objects.

Gateway handlers **must not** convert this raw content to typed domain objects (e.g., `McpClientCallToolResult`,
`A2aSendMessageResult`) and put the typed object back as `ToolCallResult.content()`. The `ToolCallResultDocumentExtractor`
walks the content tree using `instanceof` checks for `Document`, `Map`, `Collection`, and `Object[]`. Typed records and POJOs are
invisible to it — documents nested inside them would not be extracted.

Instead, handlers should:
1. Convert to typed objects only when needed to extract metadata (e.g., MCP tool name for the fully qualified identifier).
2. Pass the **raw content** through to the output `ToolCallResult`, preserving the original `Map`/`List`/`Document` tree.
3. For simple text-only results, extract the text string directly (optimization to avoid unnecessary JSON wrapping).

### Future optimization (out of scope)

A follow-up optimization can promote specific document types from the user message back into the
`ToolExecutionResultMessage` for providers that support native multi-content tool results (e.g., images on Anthropic).
This would be a post-processing step in the L4J framework adapter, transparent to the generic layer and conversation
history. The document `UserMessage` can be rebuilt from the `ToolCallResult` content tree (by re-running extraction)
with only the non-promoted documents remaining.

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
