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
* **Provider independence**: The solution must work across all supported providers (Anthropic, OpenAI via Bedrock),
  not just those with native multi-content tool result support.
* **Auditability**: Document handling must be visible in the persisted conversation history.
* **Abstraction layer**: Changes should be made in the generic agent layer (internal message model), not deep in the
  LangChain4J framework adapter, so they are framework-agnostic and auditable.
* **Consistency**: Reuse existing, proven document handling infrastructure where possible.

## Considered Options

### Option 1: Multi-content `ToolExecutionResultMessage` (L4J-native)

Extract documents from tool call results and add them as separate `Content` blocks on the LangChain4J
`ToolExecutionResultMessage`, which supports `List<Content>` since v1.12.

**Rejected** because:

- LangChain4J provider adapters have inconsistent support for multi-content tool results across providers. The Anthropic
  adapter may handle `ImageContent` in tool results, but Bedrock support is unclear.
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

1. After building the `ToolCallResultMessage`, scan each `ToolCallResult.content()` tree for `Document` instances.
2. Build a single `UserMessage` containing `TextContent` separators per tool call (for association) and `DocumentContent`
   blocks for each extracted document.
3. Apply the same extraction to event messages: append `DocumentContent` blocks to the event `UserMessage`.
4. Message ordering: `ToolCallResultMessage` -> document `UserMessage` -> event `UserMessage`(s).

**LangChain4J layer** (`ToolCallConverterImpl`):

1. Serialize `ToolCallResult.content()` using the default connectors `ObjectMapper`, which serializes `Document`
   instances as document references (store ID, document ID, metadata with content type and filename) via the standard
   `DocumentSerializer`.
2. Remove the `ContentConverter` dependency; the custom `DocumentToContentModule` / `DocumentToContentSerializer` /
   `DocumentToContentResponseModel` infrastructure is deleted.

**Conversation history**: The `ToolCallResultMessage` retains `Document` objects in its content tree (serialized as
references when persisted). The follow-up `UserMessage` with `DocumentContent` blocks is a regular message in the
conversation. Both are visible and auditable.

**User message format** (example with two tool calls):

```
TextContent: "Tool call 'generate_report' (call_1) documents:"
DocumentContent: report.pdf
DocumentContent: chart.png
TextContent: "Tool call 'fetch_data' (call_2) documents:"
DocumentContent: data.csv
```

### Future optimization (out of scope)

A follow-up optimization can promote specific document types from the user message back into the
`ToolExecutionResultMessage` for providers that support native multi-content tool results (e.g., images on Anthropic).
This would be a post-processing step in the L4J framework adapter, transparent to the generic layer and conversation
history.

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
  message. The text separators with tool call name and ID mitigate this.
* Slight increase in conversation history size due to the additional message.
