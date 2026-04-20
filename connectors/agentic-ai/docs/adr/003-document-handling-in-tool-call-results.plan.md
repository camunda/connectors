# Implementation Plan: Document Handling in Tool Call Results

Reference: [ADR-003](003-document-handling-in-tool-call-results.md)

## Phase 1: New document extraction utility

### 1.1 Create `ToolCallResultDocumentExtractor`

**Package**: `io.camunda.connector.agenticai.aiagent.agent`

**Public API**:

```java
public class ToolCallResultDocumentExtractor {

    /** Groups of documents extracted from tool call results, preserving order. */
    record ToolCallDocuments(String toolCallId, String toolCallName, List<Document> documents) {}

    /** Extracts all Document instances from a list of tool call results, grouped by tool call. */
    List<ToolCallDocuments> extractDocuments(List<ToolCallResult> toolCallResults);

    /** Extracts all Document instances from an arbitrary object tree. */
    List<Document> extractDocuments(Object contentTree);
}
```

**Recursive walker**: Handles `Document` (collect), `Map<String, Object>` (recurse values),
`List<Object>` (recurse elements), everything else (skip). Supports root-level `Document` content.

### 1.2 Create `ToolCallResultDocumentExtractorTest`

- Root-level `Document`
- `Document` in map value
- `Document` in list element
- Deeply nested `Document` (map -> list -> map -> document)
- Mixed content (documents + scalars)
- No documents found -> empty list
- Null content -> empty list
- Grouping: multiple tool calls, some with documents, some without
- Tool calls without documents are excluded from results

---

## Phase 2: Integrate extraction into `AgentMessagesHandlerImpl`

### 2.1 Inject `ToolCallResultDocumentExtractor` as dependency

Update constructor and the configuration/wiring that creates `AgentMessagesHandlerImpl`.

### 2.2 Create document user message for tool call results

In `addUserMessages()`, after `createToolCallResultMessage()` returns non-null:

```java
if (toolCallResultMessage != null) {
    messages.add(toolCallResultMessage);
    var documentMessage = createDocumentMessageForToolResults(toolCallResultMessage.results());
    if (documentMessage != null) messages.add(documentMessage);
    messages.addAll(eventMessages);
}
```

`createDocumentMessageForToolResults()`:
- Call `extractor.extractDocuments(results)`
- If no documents found, return null
- Build a single `UserMessage` with alternating `TextContent` separators
  (`"Tool call '<name>' (<id>) documents:"`) and `DocumentContent` blocks

### 2.3 Extract documents from event messages

In `createEventMessage()`, after building the main content block:

- Call `extractor.extractDocuments(eventContent)`
- Append `DocumentContent.documentContent(doc)` for each extracted document

### 2.4 Update `AgentMessagesHandlerTest`

New test cases:
- Tool call results containing documents -> document `UserMessage` created after `ToolCallResultMessage`, before events
- Multiple tool calls with documents -> single `UserMessage` with text separators per tool call
- Tool call results without documents -> no extra message
- Event message with documents -> `DocumentContent` blocks appended to event `UserMessage`
- Mixed: tool results with documents + event messages -> correct ordering

Update existing test setup to inject the new `ToolCallResultDocumentExtractor` dependency.

---

## Phase 3: Simplify L4J tool result serialization

### 3.1 Modify `ToolCallConverterImpl`

- Remove `ContentConverter` dependency from constructor; keep only `ObjectMapper`
- Replace `contentConverter.convertToString(result)` with inline logic:
  ```java
  private String contentAsString(String toolName, Object result) {
      if (result == null) return null;
      if (result instanceof String s) return s;
      return objectMapper.writeValueAsString(result);
  }
  ```
- The injected `ObjectMapper` is the `@ConnectorsObjectMapper` which has `DocumentSerializer` registered,
  so `Document` instances serialize as document references.

### 3.2 Update `ToolCallConverterTest`

- Update constructor: remove `ContentConverterImpl` parameter
- `supportsResultsContainingCamundaDocuments`: assert document reference format instead of
  `DocumentToContentResponseModel` format (base64/text content blocks)

### 3.3 Simplify `ContentConverterImpl`

- Remove `contentObjectMapper` field (the ObjectMapper copy with `DocumentToContentModule`)
- `convertToString()` uses the injected `objectMapper` directly
- Constructor simplifies (still receives `DocumentToContentConverter` for `convertToContent()`)

### 3.4 Update `ContentConverterTest`

- `supportsObjectContentContainingCamundaDocuments`: assert document reference format

### 3.5 Update `AgenticAiLangchain4JFrameworkConfiguration`

- `langchain4JToolCallConverter` bean: remove `ContentConverter` parameter, pass only `ObjectMapper`

---

## Phase 4: Delete stale infrastructure

### 4.1 Delete classes

- `DocumentToContentSerializer.java`
- `DocumentToContentModule.java`
- `DocumentToContentResponseModel.java`

### 4.2 Delete tests

- `DocumentToContentSerializerTest.java`

### 4.3 Remove unused imports

Clean up any remaining references to deleted classes across the codebase.

---

## Phase 5: Update E2E tests

### 5.1 Update `BaseL4JAiAgentJobWorkerTest` and `BaseL4JAiAgentConnectorTest`

- Change `DownloadFileToolResult` record: replace `DocumentToContentResponseModel document` field
  with the document reference format (or a generic `Object` that matches the serialized reference)
- Remove `DocumentToContentResponseModel` import

### 5.2 Update `L4JAiAgentJobWorkerToolCallingTests` and `L4JAiAgentConnectorToolCallingTests`

Update `supportsDocumentResponsesFromToolCalls`:
- Expected `ToolExecutionResultMessage` text: document reference format instead of `DocumentToContentResponseModel`
- Expected conversation includes a new `UserMessage` after the `ToolExecutionResultMessage` containing:
  - `TextContent` separator with tool call name and ID
  - Document content block (`TextContent` for text types, `ImageContent` for images, `PdfFileContent` for PDF)
- Adjust `assertLastChatRequest` expected conversation list and chat request count if needed

---

## Verification

1. `mvn clean install -pl connectors/agentic-ai` -- unit tests pass
2. Run e2e tests manually (per CLAUDE.md: never run e2e tests automatically)
3. Verify conversation history serialization round-trips correctly (existing tests should cover this)
