# AI Agent document flow

Design of the document flow of camunda/experience-pdp#81 in the AI Agent (`connectors/agentic-ai`),
covering both flavors (AI Agent Sub-process and AI Agent Task). Paths below are relative to
`connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/` unless stated otherwise;
line numbers refer to the current sources.

## 1. Scope

1. `fromAi(toolCall.x, "desc", "document")` tool parameters and how they materialize in the tool
   definition sent to the model.
2. The agent remembers every document in its conversation (persisted registry), independent of the
   conversation store and of the context window.
3. The agent passes a known document to a tool. The tool receives the real Camunda document
   reference JSON.
4. The agent returns selected documents in its response.

Out of scope: documents for gateway tools (MCP, A2A), Modeler support, document retention (see
sections 6 and 7).

## 2. Architecture overview

A request (one job of the AI Agent connector) passes through these stages. Documents enter the
conversation at ingestion, are remembered in the registry, are shown to the model as tags, can be
referenced by their id in tool calls, and leave through the response.

1. **Tool definition.** `AdHocToolSchemaGeneratorImpl.generateToolSchema` builds the input schema of the
   `fromAi` parameters and writes a `document` parameter as a string schema with the format `camunda-document-id`;
   `AdHocToolsSchemaResolverImpl.createToolDefinition` stores it in `ToolDefinition.inputSchema`
   (`aiagent/model/tool/ToolDefinition.java`), whose constructor ends the description of every document
   parameter with the hint to pass the document id, so the persisted tool definition carries the text the
   model sees. `DocumentParameterSchema` (`aiagent/model/tool/DocumentParameterSchema.java`) finds the document
   parameters by walking a schema, applies the hint and derives the schema the model providers get (section 3.2).
2. **Ingestion.** `AgentConversationTurnInputComposerImpl.compose` (returns `NextTurn(messages,
   ingestedDocuments)`) collects an `IngestedDocument` (document and `DocumentSource`) for every
   document that enters the turn: user prompt documents (`createUserPromptMessage`), tool result
   documents including gateway `extractDocuments` and nested documents
   (`createDocumentMessageForToolResults`, reuses `ToolCallResultDocumentExtractor`) and event
   documents (`createEventMessage`). The composer creates the ids of the messages first, so each
   source carries the id of the message that introduced the document. The list is ordered prompt and
   tool results first, events last, matching the message order.
3. **Registry.** `BaseAgentRequestHandler.converse` passes the new documents to `registerDocuments`.
   It loads the registry through `DocumentRegistryStorage`, adds the documents of the loaded messages
   that the registry does not know yet (`DocumentRegistryBackfill`, section 4.2), adds the new
   documents and returns the agent context carrying the result. `proceed` rehydrates the `AgentConversation` from
   that context, so every copy-on-write step and `AgentConversation.toAgentContext()` carry the
   registry, and it is persisted by the same job completion as the rest of `agentContext`
   (`AgentContext.documents`, `aiagent/model/AgentContext.java`). The `Deferred`, `NoInput` and
   no-op paths do not register anything because nothing enters the conversation there.
4. **Rendering.** The composer stores one `<doc .../>` tag (`aiagent/model/message/DocumentReferenceXmlTag.java`)
   per document and no document content (section 4.1). `AgentConversation.window` expands the tags
   of the windowed user messages from the registry (`DocumentExpander`), so the providers see each
   tag followed by its `DocumentContent`.
   `aiagent/chatmodel/provider/ModelFacingJson.java` renders a `Document` at any depth of tool result
   or message object content as its tag, for every provider path. `AgentConversation.window`
   also appends the eviction note (`EvictedDocumentsNote.appendTo`) to the windowed snapshot. The tool
   definitions of the snapshot are those of `AgentConfiguration.toolDefinitions()`: the tools of the
   process plus the available local tools, among them `return_document` while `returnsDocuments()`
   (section 9), with every document parameter rewritten for the provider (section 3.2).
5. **Model loop.** `BaseAgentRequestHandler.driveContinuationLoop` windows the conversation, calls the
   model, ingests the assistant message, and then either ends the loop or opens another round:
   `nextContinuationRound` (`AgentConversation`) for provider continuations, `nextLocalToolRound`
   (`AgentConversation`) when the agent answered every tool call of the message itself.
6. **Local tool handling.** `localToolCallHandler.handle` runs the call of every available `LocalTool`
   (`return_document` is one, section 9; the tools are beans collected by `LocalToolRegistry`,
   `autoconfigure/AgenticAiConnectorsAutoConfiguration.java`), answers the calls of process tools whose
   document parameter is not the id of a known document, and splits the batch into local results and the calls that
   remain for the process (section 3.6). The context updates of the local results (the selection of
   `return_document`) are applied to the conversation the handler returns, in both outcomes. Without
   remaining calls the loop opens a synthetic round (`applyTurnStart`, `throwIfLimitsReached`); with remaining calls the local
   results are kept as `AgentContext.pendingLocalToolCallResults` (`withPendingLocalToolCallResults`) and
   the loop ends with the normal tool calling status.
7. **Response.** `AgentResponseHandlerImpl.createResponse`. It leaves out the calls the pending local results
   answer, then `DocumentParameterResolver.resolveAll` replaces document ids by registry references in the
   process variable only. For a response without tool calls the selection is turned into `documents` and
   cleared. `AgentSubProcessRequestHandler.createAgentResponseVariable` copies `documents` into the `agent`
   variable; the task flavor returns the `AgentResponse` itself. `AgentSubProcessRequestHandler` uses
   `AgentConversation.hasInterruptedToolCallResultsInInvocation` so that interrupted results of the first
   round still cancel the remaining tool instances after a local round replaced the current turn.
8. **Next job.** `AgentConversationTurnInputComposerImpl.resolveOrderedToolCallResults` merges the pending
   local results with the arrived process results (section 3.6); `BaseAgentRequestHandler.converse` clears
   the pending field when it applies the turn.

Both flavors share `BaseAgentRequestHandler`, so everything above applies to both.

## 3. Decisions

### 3.1 Principal decisions

| # | Decision | Reason |
|---|----------|--------|
| 1 | The registry is persisted in `agentContext` (typed field `documents`) behind a small storage interface. | Deriving it from nested tool result documents is hard; filling at ingestion reuses the existing deep extraction. One place for all stores (including AWS AgentCore); commits atomically with job completion. |
| 2 | Document id is `DocumentHandle.idFor`. The model sees only `<doc id [fileName] [contentType] [source] [toolName] [toolCallId] />`. | Never expose `storeId`, `contentHash`, other metadata or URLs (presigned URLs carry tokens). |
| 3 | Documents out are a built-in local tool `return_document`, run by the agent in the model loop. | The tool must never become a BPMN element activation. |
| 4 | A document parameter that is not exactly the id of a known document (an unknown id, a `<doc/>` tag, text around an id) yields an error tool result and the model retries. | No incident; the tool does not run. The result is delivered with the next model call: in the same job when the whole batch was answered locally, in the next job together with the results of the real calls of the batch otherwise; the agent instance history shows it right away in both cases. |
| 5 | Tools receive the full Camunda document reference JSON. | Same shape other connectors emit and consume; no agent-internal handle leaves the agent. |

### 3.2 Document parameter representation

A document parameter is a string schema node with the format `camunda-document-id`:
`{"type": "string", "format": "camunda-document-id", "description": "..."}`. It is valid JSON schema, so the
persisted definition, the Ad-Hoc Tools Schema Resolver output and any other consumer need no knowledge of a
custom type. `ToolDefinition.inputSchema`, the schema persisted in `agentContext.toolDefinitions`, carries that
marker, for the tools of the process (`fromAi(toolCall.doc, "desc", "document")`; the shorthand type
`document`, also inside the `items` and `properties` of an authored parameter schema, is written as the
string schema with the format by `DocumentParameterSchema.normalizeAuthoredSchema`) and for local tools
(`LocalToolSchema.document`). The format is the only marker: `DocumentParameterSchema` finds the document
parameters by it, not by the type. `ToolDefinition` has no further field for it:
`AgentToolsResolverImpl.updateChangedToolDefinitions` and `AgentInitializerImpl` compare and store whole
`ToolDefinition` records, so an unchanged tool stays unchanged.

The model passes the id of a known document as the value, exactly the `id` attribute of the `<doc />` tag it
sees. The description of every document parameter ends with the hint `DocumentParameterSchema.HINT` (the hint
alone when the parameter has no description). The hint is applied by the constructor of `ToolDefinition`
(`DocumentParameterSchema.withHint`), the one point every tool definition passes through (the generated
schema of an ad-hoc tool, the definitions of gateway tools, the output of the Ad-Hoc Tools Schema Resolver,
local tools and definitions read back from `agentContext`), at the top level, in `items` and in nested
objects. A description that already contains the hint is left as it is, so the application is idempotent. The
stored `ToolDefinition.inputSchema` therefore holds the text the model sees, and the configuration fingerprint,
which covers the tool definitions offered in an invocation (the process tools and the available local tools,
section 9), covers the hint.

Some model providers reject unknown formats, so the provider gets a derived schema:
`DocumentParameterSchema.toProviderToolDefinitions` copies each definition that has document parameters into a
definition whose document nodes lose the `format`. `AgentConversation.window` applies it to
`AgentConfiguration.toolDefinitions()` when it builds the `ConversationSnapshot`; this is the one place, so no
provider converter (langchain4j, Anthropic, Bedrock, Gemini, OpenAI completions/responses) sees the format.
The copy is deep, built in the order of the stored schema (the order is part of the request bytes), and a
definition without document parameters is passed on as the same instance; nothing persisted or configured is
rewritten. Example for `fromAi(toolCall.docs, "The attachments", "array", {"items": {"type": "document"}})`:

```json
{"type": "object",
 "properties": {"docs": {"type": "array", "description": "The attachments",
   "items": {"type": "string", "format": "camunda-document-id",
             "description": "Pass the id of a known document (the id attribute of its <doc /> tag)."}}},
 "required": ["docs"]}
```

which the provider gets as

```json
{"type": "object",
 "properties": {"docs": {"type": "array", "description": "The attachments",
   "items": {"type": "string",
             "description": "Pass the id of a known document (the id attribute of its <doc /> tag)."}}},
 "required": ["docs"]}
```

Reason: the persisted definition holds exactly what the model sees, so a change of the wording changes the
configuration fingerprint; the rewrite for the provider is a pure function at the provider boundary; the resolver
finds the document parameters from the same schema it was authored in, without a second structure that has to
stay in sync.

The Ad-Hoc Tools Schema Resolver (`adhoctoolsschema/*`) returns the authored schema, so a document parameter
carries the format in its output too. Used as is with another LLM connector, a provider that rejects the
format needs the same rewrite, a known follow-up (section 7).

### 3.3 Nested support

A document parameter is found at the top level, as array `items`, and recursively through object
`properties` and array `items` of a parameter schema (`DocumentParameterSchema.documentParameterPaths`).
The rewrite for the provider and `DocumentParameterResolver` walk the same schema the same way: a
`properties` entry is `arguments.x`, `items` is every element of the array, nested nodes recurse through
objects and arrays. Reason: tools commonly take lists of files or an object with a file field.
The format inside `anyOf`/`oneOf`/`$ref` does not make a document parameter: it is neither rewritten nor
resolved and would reach the provider with the format still set (a known limitation, not worth the
schema-walking complexity now).

### 3.4 Tag format

`<doc id="..." fileName="..." contentType="..." source="..." toolName="..." toolCallId="..." />`, attributes in
that order, XML-escaped, blank or unknown attributes omitted (no placeholders). `fileName` is the
Camunda metadata file name, the external reference `name` or the inline `name`; it is omitted when it
equals the id (a document stored without a name of its own would only repeat the id) and, for an
external document without a name, never taken from the URL. `contentType` comes from the Camunda
metadata or the `contentType` of an inline reference and is omitted for external documents because their
metadata is only available after a download. `toolName` and `toolCallId` are present for documents that
came from a tool result. User prompt and event documents carry `source="user_message"` or `source="event"`
instead. The id of the message that introduced a document (`DocumentSource.messageId`) is never part of
the tag. The tag of a document and the tag of its registry entry
are the same: `DocumentHandle.fileNameFor` and `contentTypeFor` exist for a `Document` and for the
serialized reference map of a registry entry, with the same rules (Camunda `metadata.fileName` and
`metadata.contentType`, external `name`, inline `name` and `contentType`), so rendering from the registry
never downloads an external document just to fill `contentType`.

Reason: the tag is the only identity the model needs (decision 2); the tool origin helps the model
choose between similarly named files; the same text next to a native block and in a tool result keeps
one id per document.

A `Document` inside tool result content (bare or nested in object content) serializes as the tag, not
as the Camunda reference JSON, on every provider path. A bare document is the tag text; a nested one
is the tag string inside the JSON value.

| Provider path | Where | Rendering |
|---------------|-------|-----------|
| langchain4j | `ToolCallConverterImpl.contentElementAsString`, `ContentConverterImpl.convertToString` | tag text / JSON with tag strings |
| Anthropic | `AnthropicContentConverter.toToolResultBlocks`, `toContentBlockParams` (object content) | text block |
| OpenAI completions (and the Mistral dialect) | `OpenAiCompletionsRequestConverter.toolResultMessages`, `OpenAiFileContentChunkStrategy` | text |
| OpenAI responses | `OpenAiContentConverter.toResponsesToolResultOutputItems`, `toResponsesContentParts` | `input_text` |
| Bedrock Converse | `BedrockConverseContentConverter.toToolResultBlocks` (`BedrockConverseDocuments` with the tag mapper) | `json` block with tag strings; a bare document or non-map value as `text` |
| Gemini | `GeminiContentConverter.toFunctionResponseParts`, `toParts` (object content) | `functionResponse` output string |

No provider embeds a document inside the tool result entry (Anthropic `tool_result`, OpenAI
`function_call_output`, Bedrock `toolResult`, Gemini `functionResponse` all carry text or JSON only).
The real bytes travel in the user message that follows the tool results, where the tag text precedes
each native block, so the id in the tool result entry and next to the native block is the same. Two
native block names are not the id: the Bedrock `DocumentBlock.name` is a hash derived from the id
(Bedrock charset rules) and the OpenAI `input_file.filename` is the file name; the preceding tag text
carries the id.

### 3.5 Eviction note mechanism

When the conversation is windowed, the registry of the current `AgentContext` is compared with the
windowed messages. A registry document is visible when its id appears as `DocumentContent` of a user
message, as a `Document` in a tool result content tree (`ContentTreeDocumentWalker`), or as the id of
a `<doc/>` tag in user or tool result text. If at least one document is not visible, a user message
is appended to the snapshot only: the preamble `EvictedDocumentsNote.PREAMBLE` plus the tag of each
such document (`DocumentReferenceXmlTag.from(entry)`: id, file name, content type, source or tool call). It is
never part of `allMessages()`, never persisted and leaves the frozen system prompt alone.

Reason for a trailing note instead of a system prompt addition or a rewritten message: the system
prompt is frozen per agent instance; stored messages must stay byte-stable for replay and prompt
caching; the note is the only thing that changes when the window slides, and as a suffix it keeps
cached prefixes valid. The note follows tool result and document messages that are user role anyway.
It is skipped when the windowed messages end with an assistant message (a continuation round resumes
on that message), so such a round does not repeat the note it already had in the first round of the
invocation.

### 3.6 Batch policy (mixed batches)

A batch is split per call. `LocalToolCallHandler.handle(conversation, toolCalls)` returns a
`LocalToolCallSplit(conversation, localResults, remainingCalls)`; `conversation` is the input conversation
with the context updates of the local results applied:

- **Local calls.** A call of an available local tool (section 9) is run by the agent and gets its result
  immediately: the built-in `return_document` and every other registered tool. A call of a process tool
  whose document parameter is not a known id is answered with an error result listing the known
  documents.
- **Remaining calls.** Every other call goes to the process like any tool call, also a call of a local
  tool that is not available for this agent.

The request handler then proceeds by what is left:

| Remaining calls | What happens | Model calls |
|-----------------|--------------|-------------|
| none | The local results become the input of a synthetic round of the same job (`nextLocalToolRound`); the model is called again. The round counts against `maxModelCalls` (`throwIfLimitsReached`). | one more in the job |
| some | The job completes with only the remaining calls activated (`ToolCallProcessVariable` list and element activations hold only them, status `TOOL_CALLING`). The local results are stored in `AgentContext.pendingLocalToolCallResults` and reported to the agent instance history right away (5.1). | none extra |

The context updates of the local results, for `return_document` the selection in
`AgentContext.returnDocuments`, are applied immediately in both cases.

**Pending local results.** `AgentContext.pendingLocalToolCallResults` is a `List<ToolCallResult>`,
`@JsonInclude(NON_EMPTY)`, a missing field binds to an empty list, no schema version bump. It rides in
`agentContext` like the registry, so it reaches the next job whatever the conversation store is (the stores
only handle the conversation pointer), and it is written by the same job completion that activates the
real calls. Content is whatever the local tool answered (text or object content); `completedAt` is the time of the
local answer.

**Merging in the next job.** `resolveOrderedToolCallResults` looks up each tool call id of the last
assistant message first in the pending local results, then in the arrived process results (gateway
transformed; local results are never transformed). The `ToolCallResultMessage` keeps the order of the calls.

- *Partial arrival.* The composer waits for the real calls only. Missing real calls make the result
  `Deferred`; its `arrivedResults` hold the process results only. The local results were reported
  by the job that answered them and are not written again by incomplete jobs.
- *Event interrupt.* Only real calls without a result are cancelled; a locally answered call keeps its
  result.
- *Consumption.* `BaseAgentRequestHandler.converse` clears the pending field when it applies the composed
  turn (`NextTurn`), so the results are consumed once and the field cannot reach a later turn. Pending
  results whose id is not a tool call id of the last assistant message are ignored.
- *Both flavors.* The sub-process and the task flavor share `BaseAgentRequestHandler`, so both follow the
  rule: the sub-process activates the remaining calls as elements, the task flavor returns only them in
  `toolCalls`.

**Adding a local tool.** Register a `LocalTool` bean (section 9). The call is handled on its own, so the
tool works the same alone, with other local tools or with real calls in one batch. State a local tool
changes goes through the context updates of its result, applied in call order.

Reason: the real calls are valid work the model asked for, and running them without an extra model call
keeps a mixed batch at one round. Per-call splitting needs only the results of the local calls to survive
until the process results are in, which `agentContext` already carries.

### 3.7 Reading the value, error text

A document parameter value is a string that is exactly the id of a document of the registry: the whole field is
compared, nothing is parsed, trimmed, unescaped or extracted. A `<doc/>` tag, an id with surrounding
whitespace or an id inside other text is not a known id and is rejected. A value that reaches the process
is the Camunda document reference of the registry entry; the persisted assistant message keeps the id exactly
as the model sent it.

One sentence per invalid value, then the known documents (at most 50 are listed, the rest is
counted):

```
Parameter 'docs[1]' must be the exact id of a known document (the id attribute of a <doc/> tag in the conversation), got 'x'. Known documents:
<doc id="..." fileName="..." contentType="..." toolName="..." toolCallId="..." />
```

The parameter path is `name`, `parent.name` for nested objects and `name[i]` for array elements. A
non-string value reads "Parameter 'p' must be the exact id of a known document (the id attribute of a
<doc/> tag in the conversation), a string.", an array parameter that is not an array "Parameter 'p' must be an array.". With an empty
registry the list is replaced by "No documents are known.". Results are plain text because no provider
has a tool error flag in the domain model, so the model reads the text. Reason: the list of known tags
lets the model correct itself in one retry without any further tool call. The error result reaches the model with its next request: in the synthetic round when the whole batch was local, in the next job together with the results of the real calls of the batch otherwise (section 3.6).

### 3.8 `return_document` semantics

`ReturnDocumentTool` (`aiagent/localtool/ReturnDocumentTool.java`) is a `LocalTool` with `NAME =
"return_document"`. The model calls it once per document with the arguments `key` (required string, the
stable identifier the process finds the document by, matching `[A-Za-z_][A-Za-z0-9_-]*`), `document` (required,
the id of the document) and `description` (optional string). `document` is a document
parameter (string schema with the format `camunda-document-id`), so the framework resolves the id to the
document of the registry and reports a value that is not a known id with the texts of 3.7 before the tool
runs; the tool receives a `Document` and takes the registry id from `DocumentHandle.idFor`. The tool
is available while the option is on. Its description tells the model to use it only for a document the
user should receive in the answer, not for documents it merely passes to other tools, to call it once per
document, what the key is, that the description is optional and that the same key again replaces the
document returned under it. A valid call answers "Returned the document under the key 'k'." and
selects the document with a context update (put into `AgentContext.returnDocuments`). A key that does not
match the pattern or is missing gives an error answer that states the pattern so the model retries; a
missing `document` gives an error answer listing the known documents. An error answer selects nothing.

- **Accumulate.** Every valid call puts its document into the selection under its key, also across jobs and
  turns of the same run. A key that is already selected is replaced in place and keeps its position.
  The selection lives in `AgentContext.returnDocuments` (`key -> SelectedDocument{document, description?}`,
  insertion ordered, `@JsonInclude(NON_EMPTY)`, a missing field binds to an empty map, no schema version
  bump) because the tool can be called in an earlier job than the final response. The component is a
  `LinkedHashMap` because the record builder copies a `Map` into an immutable map of unspecified order.
- **Consume on the final response.** A response without tool calls returns the selection as `documents`, a map
  `key -> ReturnedDocument{document, description?}` in selection order, where `document` is the registry entry
  `reference` map (selections whose id is not in the registry are skipped), and clears the selection in
  the returned context. A response with tool calls leaves it alone. The sub-process flavor copies the map
  into the `agent` variable, so the process reads `agent.documents.report.document`; all documents are
  `get entries(agent.documents).value.document`.
- **No retract.** There is no tool to remove a key. A model can only replace the document of a key. A
  document is only ever returned if the model named it, and the answer text names the key of that call.
- A selection recorded in a batch that also holds real calls (3.6) is persisted with that job, before the
  real calls ran, because the model receives the "Returned ..." result for it with the results of the batch.
- Option off: no tool in the snapshot, no `documents`; a stale selection (option toggled mid-run) is
  cleared with the final response without being returned.

A valid `return_document` call together with real tool calls costs no extra model call: the real calls run
and the model receives all results in the call order (3.6). Only a batch made of local calls alone starts a
synthetic round, which counts against `maxModelCalls`.

### 3.9 Reserved name

`AgentToolsResolverImpl.loadAdHocToolsSchema` rejects an ad-hoc or gateway tool element named like any
registered local tool (`LocalToolRegistry.toolNames()`, today `return_document`) with a
`ConnectorException` (`AgentErrorCodes.ERROR_CODE_RESERVED_TOOL_NAME`). The check ignores whether the tool
is available for the agent (section 9.6). `loadAdHocToolsSchema` runs on tool discovery and on process
migration (`updateToolDefinitions`), so both paths are covered. Reason: a BPMN element with that name would
collide with the local tool in the tool list and silently lose to it.

### 3.10 Configuration option `includeDocuments`

`ResponseConfiguration.includeDocuments()` (`aiagent/model/request/ResponseConfiguration.java`:20),
implemented by `AgentTaskResponseConfiguration` and `AgentSubProcessResponseConfiguration` (template
property `data.response.includeDocuments`, label "Include documents", group `response`, optional
boolean, off by default). `AgentConfiguration.returnsDocuments()` (:69) is the single switch; the
tool's `isAvailable` reads it. `BaseAgentRequestHandler.converse` puts the definitions of the available local
tools after the process tools in `AgentConfiguration.toolDefinitions()` for the invocation; they are not part
of `AgentContext.toolDefinitions`, so toggling the option is invisible to the migration check
(`updateChangedToolDefinitions`) and no persisted tool list changes. The configuration fingerprint covers the
offered tools, so the toggle shows up as a new `CONFIGURATION` history item (section 5.1). Reason: existing
agents see no new tool, no new response field and no persisted-state change unless the modeler opts in.

### 3.11 Templates and versions (v1 frozen)

`data.response.includeDocuments` appears in the v2 AI Agent Task and Sub-process templates (main and
hybrid). The v1 templates are deprecated and frozen, and the request data classes are shared, so the
generated property is dropped from them by the `omittedProperties` tuples in the connector `pom.xml`
(`templateId=propertyId`), applied by both transform scripts; the v1 JSON is byte-identical to before.
There is no template version bump: v2 is still at its first version and a new optional property that
defaults to off needs no migration; the `element-templates/README.md` rows are unchanged.

### 3.12 Inline documents

The registry entry keeps the complete reference, so for an inline document it contains the full
content (base64 in `content`). Reason: the tool payload of decision 5 must be the real reference.
External documents keep the URL (never rendered to the model, decision 2) and Camunda documents keep
the full reference with metadata. The agent instance history shows the same reference as the arguments
of a call (section 5.1), so the content of an inline document is part of the history item too. The cost
is described in section 4 and listed as a blocker in section 5.

### 3.13 Remaining design choices

- **Where the references are resolved.** Only the `ToolCallProcessVariable` (the element activation
  variable `toolCall` of the sub-process flavor and the task flavor's `toolCalls`) carries the
  references; the persisted assistant message keeps the ids the model sent, and the original arguments
  are never modified. A missing or `null` argument is left alone (optional parameters). Resolution
  runs on the raw tool calls before the gateway transformation, so gateway tool calls stay untouched.
- **Local handler abstraction.** `LocalToolCallHandler.handle(conversation, toolCalls)` returns a
  `LocalToolCallSplit(conversation, localResults, remainingCalls)`; a local tool returns a `LocalToolResult`
  with content and context updates. Reason: the loop stays generic and a future local tool is one more
  bean (3.6, section 9).
- **Registry shape.** `DocumentRegistry` is an immutable, insertion-ordered, id-keyed value
  (`findById`, `entries`, `withDocuments`, `of`, `fromMessages`); the first registration of an id wins
  and re-adding a known id returns the same instance. `DocumentRegistryEntry` holds `id`, `source`
  (a `DocumentSource`: the `type` `TOOL_CALL`, `USER_MESSAGE` or `EVENT`, the `toolName` and `toolCallId` of
  the tool call that returned the document for `TOOL_CALL`, and the nullable `messageId` of the message that
  introduced it) and `reference` (a `Map<String, Object>` with the JSON the standard
  `DocumentSerializer` emits, untouched). The file name and content type of a tag are read from the
  reference (3.4). The reference is a plain map so reading the registry needs
  no `DocumentFactory`; it binds back to a `Document` through the regular document deserializer
  (covered by a test). The entry serializer uses a private `ObjectMapper` with the document serializer
  module and `NON_NULL` inclusion; dates are ISO strings.
- **Loop obligations of a synthetic round.** `throwIfLimitsReached` before the round (retries are bounded
  by `maxModelCalls` and fail with the existing max-model-calls error code); agent instance history
  reporting for the synthetic input; a persist and reload round trip through `TurnReconstructor`
  without `lastIterationKey` drift (see 5 and 5.1 for reporting details). A mixed batch opens no round and
  has none of these obligations beyond the history reporting of its local results (5.1).

## 4. Registry storage

**Where.** `AgentContext.documents` (`List<DocumentRegistryEntry>`, `@JsonInclude(NON_EMPTY)`),
accessed through `DocumentRegistryStorage` (`DocumentRegistry load(AgentContext)`, `AgentContext
store(AgentContext, DocumentRegistry)`). The only implementation, `AgentContextDocumentRegistryStorage`
(bean at `AgenticAiConnectorsAutoConfiguration`), reads and writes `AgentContext.documents()`.

**Store independence.** The registry rides in `agentContext`. The `ConversationSession`
implementations (in-process, Camunda document, AWS AgentCore) only read the context and return the
new `ConversationContext` pointer; `AgentConversation.withStoredConversation` copies the rest of the
context. The store formats are unchanged.

**Entry shape.** `{id, source, reference}`: `source` is `{type, toolName, toolCallId, messageId}` with the
`type` `TOOL_CALL`, `USER_MESSAGE` or `EVENT`; `toolName` and `toolCallId` are only present for `TOOL_CALL`,
`messageId` is the id of the message that introduced the document (the tool call result message for a tool
document, the user message for a user prompt or event document) and is left out when it is not known;
`reference` is the untouched JSON of the document serializer. The file name and content type shown in tags
are read from the reference, not stored. Example of a Camunda document returned by the tool `Search_Tool`:

```json
{"id": "25ece9fa-aeea-423d-98ed-67c1f08b137b",
 "source": {"type": "TOOL_CALL", "toolName": "Search_Tool", "toolCallId": "call_0123456789abcdef",
            "messageId": "0197a000-0000-7000-8000-000000000004"},
 "reference": {"camunda.document.type": "camunda", "storeId": "in-memory",
               "documentId": "25ece9fa-aeea-423d-98ed-67c1f08b137b", "contentHash": "…",
               "metadata": {"contentType": "application/pdf", "fileName": "report.pdf"}}}
```

**Serialized size per entry** (`DocumentRegistryEntryTest#serializedEntrySizes`, source `TOOL_CALL` with
`Search_Tool`, `call_0123456789abcdef` and a message id):

| Entry | Bytes |
|-------|-------|
| Camunda document (metadata: contentType, fileName) | 397 |
| External document (URL of about 50 characters) | 307 |
| Inline document, 1 KB content | 1302 |

A Camunda reference with more metadata (size, expiresAt, process ids) adds roughly 100 to 150 bytes.

**Limit.** Zeebe has no separate per-variable size cap; the bound is the maximum message size of the
broker, `NetworkCfg.DEFAULT_MAX_MESSAGE_SIZE` = 4 MB (`zeebe/broker/.../NetworkCfg.java`), which
applies to the whole job completion command carrying `agentContext` and the other variables. At about
400 bytes per Camunda document the registry alone needs roughly 10,000 documents to reach it, so it
only matters together with the in-process store (messages in the same variable) or inline documents.

**Large inline document risk.** The content of an inline document is held a second time in
`agentContext`, in addition to the tool result in the conversation, and a returned inline document
repeats it again in `agent.documents`. Inline documents are rare (Camunda documents are the norm), but
a large inline image counts fully against the 4 MB limit.

**Backward compatibility and downgrade.** No `CURRENT_SCHEMA_VERSION` bump. A missing `documents`
field binds to an empty list (builder default for collections, same as `toolDefinitions`), also
through the raw-tree path of `AgentContextSchemaMigration`. Conversations stored by Camunda 8.10, which
has no registry and stores documents as content, keep working without a rewrite of their messages
(section 4.2). On downgrade, an older connector runtime ignores `documents`, `returnDocuments` and
`pendingLocalToolCallResults` (`ignoreUnknown`) and drops them on its next write. It also reads a
document that was stored as a tag only (section 4.1) as the tag text without the document content; the
next upgraded run rebuilds the registry of the documents it can still find in the messages, but the
selection of `return_document` and documents known only from evicted or unscanned content are lost.
Mixed batches work from 8.11 on; all changes land on 8.11 only, so there is no compatibility with older
engines or connector runtimes beyond what section 4.2 describes.

**Migration path to the stores.** Implement `DocumentRegistryStorage` against `ConversationSession`
(store the registry next to the messages, keyed by the conversation pointer), then switch the bean.
The handler and the composer do not change; `AgentContext.documents` stays readable (empty for new
conversations) so persisted state from this layout still loads, and the backfill scan covers any
conversation without a stored registry.

### 4.1 Persisted form and provider form

A document is persisted in a conversation once per message that introduces it, as its tag only: the
composer stores a `TextContent` with the `<doc .../>` text and the metadata `{"kind": "document-reference", "documentId": "<id>"}`
for every user prompt, tool call document and event document, and no `DocumentContent` next to it. The
preamble texts (`Documents extracted from tool calls ...`, `Documents extracted from event data ...`) stay
in front of the tags. The reference of the document lives in the registry (`AgentContext.documents`)
only, in addition to the `ToolCallResultMessage` of a tool document, which is stored unchanged. The
metadata (`kind` equal to `document-reference`) is the only marker: `Content` is a sealed hierarchy, so a tag is a normal `TextContent`, and
text a user typed that happens to hold a `<doc/>` tag is never touched.

The providers see another form. `AgentConversation.window` passes the windowed messages through
`DocumentExpander`, which adds after each flagged tag a `DocumentContent` with the document built from
the registry reference map, with the same document deserialization the persisted messages use (the
connectors object mapper with its document factory). The result is the pair every provider converter
already handled: tag text followed by the native block. The expansion

- is skipped for a tag that a `DocumentContent` of the same document already follows (idempotent);
- leaves the tag text alone when the registry has no entry for the id or the reference cannot be read,
  and never fails the request;
- depends on the messages and the registry only, so the request bytes are stable between requests
  (prompt caching);
- leaves the stored conversation alone: `allMessages()` and the stored messages keep the tag only. The
  eviction of the tool call document messages (`MessageWindowFilter`, metadata `toolCallDocuments`) works
  on the stored messages, before the expansion.

The agent instance history reports a user message with the same expansion (`AgentInstanceHistoryMapper`
with the registry of `HistoryDocuments`): the tag text and the document as a `DOCUMENT` part, as it did
when the content was stored. The AWS AgentCore conversion keeps a `TextContent` with metadata in its
content blob (a text without metadata stays a conversational payload), so the marker survives that store.

Reason: a document reference (for an inline document its complete content) would otherwise be held by
the registry, the extracted document message and the tool result. The extracted document message is the
copy that carries nothing the registry does not. The tool result is left as it is because the provider
converters and the tool result history read it.

### 4.2 Conversations in the form of earlier versions

Conversations written by Camunda 8.10 hold the documents of user prompts as bare `DocumentContent`, the
documents of tool calls and events as a tag in an older attribute format (`documentId` and `storeId`, or
`url` and `name` for an external document, `toolName` and `toolCallId` for tool calls) followed by the
`DocumentContent`, and have no registry. They are never rewritten: append-only conversation stores keep
the messages they have, and only `agentContext.documents` changes. Nothing fails on them; the worst case is
a document without a tag.

- **Backfill.** On every job `BaseAgentRequestHandler` extends the registry by the documents of the loaded
  messages that it does not know (`DocumentRegistryBackfill`): the `DocumentContent` of user messages and
  the documents in the content trees of tool call results (`ContentTreeDocumentWalker`). The source is
  derived from the place: a document in a tool call result is `TOOL_CALL` with the tool of that result; a
  document of a user message with the `toolCallDocuments` metadata is `TOOL_CALL` with the tool read from
  the tag in front of it; a document after the event documents preamble is `EVENT`; every other document of
  a user message is `USER_MESSAGE`. A backfilled source never carries a `messageId`: messages of these
  conversations may lack a persisted id, and the id of such a message is generated again on every load, so
  only the ingestion of new documents fills `messageId`. The backfill is idempotent (a known id is never
  replaced or duplicated, a conversation without missing documents leaves the registry unchanged), looks
  at message content only, and adds Camunda and external documents. Inline documents are not added.
- **Window normalization.** `DocumentExpander` also normalizes what it finds in a user message when it
  expands for the model, in the view only: a tag text that is not flagged, names the following
  `DocumentContent` (by `id`, `documentId` or `url`) and is followed by it is replaced by the canonical
  tag of the document (taken from the registry entry, so it carries the `source` or the tool call); a
  `DocumentContent` without a tag in front of it gets the canonical tag inserted before it. These tag
  texts are not flagged. Text that matches no known pattern, such as a tag for another document, passes
  through unchanged.
- **Inline documents** stay as they are in Camunda 8.10: no id, no tag and no registry entry for the
  legacy form. A tag and the content of an inline document that were stored together are not touched.
- `LegacyDocumentConversationTest` runs a golden conversation in this form
  (`src/test/resources/backwardcompatibility/camunda-8.10/agentContext-documents-inprocess.json`: a prompt
  document, a tool call document pair, an inline document pair, an event document pair and an event
  message without an id) through load, backfill, normalization and a further turn, and asserts that the
  stored messages are unchanged.

## 5. Blockers found

Severity reflects what a user of the feature would hit, not the effort to fix.

| # | Finding | Severity | Detail |
|---|---------|----------|--------|
| 1 | Large inline documents | High (rare trigger) | Content is stored in the registry and in the tool result, and again in `agent.documents` when returned. A large inline image can push the job completion past the 4 MB broker message limit; there is no size guard or fallback to a reference-only entry. |
| 2 | Local answers of a mixed batch reach the model late | Low | The agent instance history shows the local results of a batch that also holds real calls as soon as the job that answered them completes (5.1). The model only receives them with the next request, in the next job, when the real calls have completed; a real call that never completes holds that request back. A batch made of local calls alone is answered in the same job. |
| 3 | Gateway tools unsupported | Medium | MCP and A2A tool definitions never declare document parameters; a document id passed to them is not resolved. Documents returned by gateway tools are ingested and passable to ad-hoc tools, but not to gateway tools. |
| 4 | No tool error flag | Low | The domain model has no error flag on tool results, so invalid-id errors are plain text the model must interpret; a weak model may not retry. |
| 5 | Reused tool call ids | Low | A model that reuses a tool call id across rounds reuses the agent instance history item id of the earlier result. Ids are assigned by the providers and unique in practice. |
| 6 | History reporting of local rounds | Low (handled) | See 5.1: local calls and rounds are reported through the existing history items; what is not reported there is the document selection itself and the documents' content (the arguments of a call show the document reference, which holds the content of an inline document). |
| 7 | Backfilled registry completeness | Low | The backfill (section 4.2) adds only Camunda and external documents it finds as content or in tool call result trees, and scans the loaded messages on every job. Inline documents of conversations from Camunda 8.10 are not added and stay without a tag. |
| 8 | Order of tool schemas in the fingerprint | Low | `ToolDefinition.builder()` copies the input schema into an immutable map (`Map.copyOf`) whose iteration order depends on a per-JVM salt and the insertion order, so every Jackson round trip of the persisted tool definitions can reorder the top-level schema entries, on another worker or within the same JVM. `AgentConfiguration.fingerprint()` therefore hashes a canonical form of the tool definitions instead of their `toString`: tools in list order, each `name`, `description` and `inputSchema` with map keys sorted recursively (also maps inside lists), list order kept, numbers compared by value (integral values of any numeric type agree), `null` explicit. The fingerprint thus stays equal across round trips and JVMs, and a job reports a `CONFIGURATION` history item only when model, provider, system prompt, model-call limit or the tools really change. Nothing is sorted on the definitions themselves: the order sent to the model and persisted is as authored. A fingerprint persisted before this canonical form does not match it, so an in-flight agent instance reports one extra `CONFIGURATION` item at upgrade (acceptable, the feature is 8.11-only). |

Additional facts behind these findings: validation looks only at ad-hoc tools whose input schema declares
document parameters (the tool name is the lookup key); a failing resolution in `createResponse`
(unreachable after validation) throws `IllegalStateException`; the assistant turn of a round followed
by a synthetic local round completes with `THINKING` instead of `TOOL_CALLING`, while a mixed batch completes
with `TOOL_CALLING`; metrics count every round as a model call (`jobMetrics` sums the rolled-in turns) and
every tool call the model issued as a tool call of its turn, local or not; persisted messages read `assistant(tool calls) ->
tool results -> assistant`, which `TurnReconstructor` splits into one turn per assistant message, so
`lastIterationKey` equals the reconstructed turn count and the drift warning does not fire; the
windowing filter evicts the tool results together with their assistant message.

### 5.1 History and visibility reporting of local calls

Reporting goes through the same `AgentInstanceClient` calls as for any turn; there is no local-specific
call. What is reported:

**Definitions.** The tool list of a `CONFIGURATION` history item holds the process tools and the available
local tools (`AgentConfiguration.toolDefinitions()`, section 3.10), mapped by `AgentInstanceToolMapper`. A
local tool is an `AgentTool` with name and description and **no element id** (`null`): the engine field is
the "BPMN element ID of the tool" and is nullable. Only available tools are listed. The fingerprint of
the configuration covers the offered tools, so enabling `includeDocuments` on a running agent adds a
`CONFIGURATION` item with the new tool list.

**Calls and results.**

| Case | Calls (in order) | Local result items |
|------|------------------|--------------------|
| Batch with no remaining call (synthetic round) | `applyTurnStart` (turn input), `applyTurnCompletion` with `THINKING` (assistant item with all calls), `applyTurnStart` of the synthetic turn (input = the local results), the next `applyTurnCompletion` with the terminal status | `TOOL_RESULT` items in the synthetic `applyTurnStart`, `loopIteration` = assistant turn + 1 |
| Mixed batch | `applyTurnStart`, `applyTurnCompletion` with `TOOL_CALLING` (assistant item with all calls, local and real), then `applyToolCallResults` with the local results (no status). The next job: `applyTurnStart` with the merged results | `TOOL_RESULT` items of the local results in the `applyToolCallResults` of the job that answered them; the next job's `applyTurnStart` reports them again under the same `historyItemId` and the engine keeps one row. They stay in call order with the process results |
| Mixed batch, partial arrival (`Deferred`) | `applyToolCallResults` with the arrived process results only | none; the local results were reported by the job that answered them |

In a mixed batch `BaseAgentRequestHandler.driveContinuationLoop` calls `applyToolCallResults` with the local
results and the just completed assistant turn right after `applyTurnCompletion`. That call carries no status,
so the one status per round of the engine stays with the completion write. The write fails the job like every
other history write, so a failed report is retried with the job. Each local `TOOL_RESULT` item correlates to its call like any result: `toolCallId` and `toolName` of the
call, the originating arguments (looked up in the previous turn), `historyItemId` = tool call id, local
`completedAt` as `producedAt`. The `elementId` of the call entry is `null` because a local call has no BPMN
element, for the call in the `ASSISTANT` item and for the result (`AgentInstanceHistoryMapper` recognizes the
call by `LocalToolRegistry.toolNames()`, which is safe because those names are reserved, 3.9). The result
text is the content of the `TOOL_RESULT` item, an error result included.

**Document arguments.** A document parameter of a call (a schema node with the format `camunda-document` of the tool's
definition, found as in 3.3; for a tool of the process and for a local tool such as `return_document`,
whose definition is looked up in `LocalToolRegistry` even while the tool is unavailable) is shown with the
registry entry's `reference` map instead of the id the model sent, in the `arguments` of the call in the
`ASSISTANT` item and of the `TOOL_RESULT` item. This is the complete reference JSON, so the history of an
inline document holds its content (section 3.12). The substitution is for reporting only: persisted
messages, the process variable and the text the model reads keep the ids. A value that is not exactly the
id of a registry document (unknown id, a tag, text around an id), a value of an unexpected shape and a parameter that is missing stay as given
(`DocumentParameterResolver.substituteKnownReferences`, which never validates). The mapper needs the tool
definitions and the registry, so `AgentInstanceClient.applyTurnStart`, `applyTurnCompletion` and
`applyToolCallResults` take a `HistoryDocuments(toolDefinitions, documents)`; the request handler builds
it from `AgentConfiguration.toolDefinitions()` and the registry of the conversation (of the agent context
when the composer defers). The early write of a local result and the later write of the merged turn
start, like the assistant item, show identical arguments: the result depends only on the call, the tool
definition and the registry entry of the id, which is immutable once registered, so it does not matter
which write the engine's deduplication keeps.
The `ASSISTANT` item lists every call of the message; the per-turn metrics count them all in `toolCalls`
(a measure of what the model requested, consistent with the listed calls and the status choice
`IDLE`/`TOOL_CALLING`). Unit tests: `AgentSubProcessRequestHandlerTest` and `AgentTaskRequestHandlerTest`
(call order, status and arguments), `CamundaAgentInstanceClientTest` (merged local and process results
as items, local tools in the `CONFIGURATION` item), `AgentInstanceToolMapperTest` and
`AgentInstanceHistoryMapperTest` (no element id for local tools, document references in the arguments of the assistant and the result items), `AgentConversationTurnInputComposerImplTest`
(what `Deferred` carries). End to end: `AgentSubProcessDocumentFlowTests` reads the agent instance back
through the query API (`agentInstanceHistoryReportsTheLocalToolAndItsCalls`).

What cannot be represented:

- The history API has no tool kind and no result status. A local tool is recognizable only by the missing
  element id on its tool entry and on its call entries; a local result and a real one are otherwise alike. An
  error result of a local tool (invalid document id, `LocalToolException`, invalid arguments) is plain
  text content, the same way the model reads it.
- The documents the agent returns are not reported; the selection of `return_document` appears only as the
  text of its result and as the references in its call arguments.
- The agent instance history is written repeatedly for the same result: a local result by the job that
  answers it and again by the next job, a process result that arrived early by every incomplete job and
  again by the batch-complete `applyTurnStart`. This is intended: the engine deduplicates history items by
  `historyItemId`, within one job activation and across jobs (camunda/camunda#58792), and keeps the first
  write, so the history holds each result once. What the first write carries is therefore final: the item
  ids, `loopIteration` (assistant turn + 1), `producedAt` (the result's `completedAt`, persisted with the
  pending local results) and the call correlation are identical in both writes.

## 6. Gaps compared with camunda/experience-pdp#81

- Documents mentioned only in the content of an evicted assistant message are not tracked; only
  registry documents are listed in the eviction note.
- The eviction note lists tags only (id, file name, content type, source or tool call) and does not describe
  the content of a document.
- `<doc/>` ids the model writes in the final text instead of calling `return_document` are not
  interpreted.
- No retract for `return_document`: a key can be replaced but not removed until the final response.
- External documents have no `contentType` in any tag, because it would require a download.
- No document TTL or expiry handling: the registry keeps references for the lifetime of the
  conversation, and a returned or passed reference can point to a document the store has already
  deleted.
- No Modeler support for the fromAi type `document` (no validation, no completion, no linting).
- Gateway tools (MCP, A2A) cannot receive documents.
- Returned references are registry snapshots, including inline documents with their content.
- The agent instance history shows the complete document reference in the arguments of a call, so an
  inline document's content is in the history item.
- The registry lives in `agentContext` for all stores; it does not use the store-specific
  persistence.

## 7. Open points

- **Gateway tools (MCP, A2A).** Their definitions never declare document parameters; the gateway
  handler seam (`transformToolCalls`) is unchanged and runs after the document resolution, which leaves
  their calls untouched. Passing documents into gateway tools needs its own design because the gateway
  wraps the call arguments in an envelope the handler builds.
- **Resolver output for standalone use.** The Ad-Hoc Tools Schema Resolver returns the authored schema
  with the format `camunda-document-id` and the hint in the description (3.2). The AI Agent derives the
  provider schema (no format) at the snapshot, but another LLM connector that uses the resolver output
  directly would send the format; it needs the same rewrite (`DocumentParameterSchema.toProviderToolDefinitions`) or
  a documented contract.
- **Inline documents in the history.** The agent instance history shows the complete reference of a
  document, so inline content is part of a history item (3.12, 5.1). Decide whether the history should
  show a reference without the content.
- **Modeler and linting support** for the fromAi type `document`: the shorthand type is accepted by
  the runtime but not known to the Modeler tooling.
- **Document TTL (Risk 1 of the epic).** Decide whether the agent should notice expired documents
  (drop registry entries, tell the model) or leave expiry to the tool that dereferences the document.
- **Native tool-result representation (#7232).** Providers currently carry only the tag text in the
  tool result entry and the bytes in the following user message; a native representation would change
  where the tag and the content live.
- **Tool result documents stay in the tool result.** The `ToolCallResultMessage` keeps the complete
  reference of a tool result document next to the registry entry. Storing only the tag there as well needs
  the provider converters and the history mapper to read tool result documents from the registry; it is
  not part of the persisted form of section 4.1.
- **Inline documents of Camunda 8.10 conversations** get neither registry entry nor tag (section 4.2);
  a conversation that relies on them can only pass them on by re-ingesting the document.
- **Capability matrix (#7230).** Which providers can take which document types natively decides what
  the model receives next to the tag; the registry and tags are independent of that.

## 8. Test coverage map

### Unit tests (`connector-agentic-ai/src/test/java/io/camunda/connector/agenticai/`)

| Area | Tests |
|------|-------|
| Document parameters in the tool definition | `adhoctoolsschema/schema/AdHocToolSchemaGeneratorTest` (maps the type `document` to the string schema with the format), `AdHocToolsSchemaResolverTest`, `AdHocToolsSchemaFunctionIntegrationTest`, `aiagent/model/tool/DocumentParameterSchemaTest` (paths, provider rewrite, hint, normalization), `AgentConversationTest` (the window rewrites, nothing persisted changes), the converter tests under `aiagent/chatmodel/provider/` (one string schema with hint test per provider path) |
| Registry, entry sizes, handles | `aiagent/model/document/DocumentRegistryTest`, `DocumentRegistryEntryTest` (entry JSON, including `serializedEntrySizes`), `DocumentHandleTest` (file name and content type of a document and of its reference) |
| Ingestion and tags | `aiagent/agent/AgentConversationTurnInputComposerImplTest` (flagged tags, sources and message ids), `ToolCallResultDocumentExtractorTest`, `ContentTreeDocumentWalkerTest`, `aiagent/model/message/DocumentReferenceXmlTagTest` (source attribute, flagged content, attribute parsing) |
| Persisted form and expansion | `aiagent/model/document/DocumentExpanderTest` (expansion, idempotence, dangling and unreadable references, older tags, inline documents), `aiagent/model/AgentConversationTest` (window expansion, stored messages untouched), `aiagent/agentinstance/AgentInstanceHistoryMapperTest`, `AwsAgentCoreConversationMapperTest` (text with metadata) |
| Conversations of Camunda 8.10 | `aiagent/agent/DocumentRegistryBackfillTest`, `aiagent/agent/LegacyDocumentConversationTest` (golden conversation, `backwardcompatibility/camunda-8.10`) |
| Eviction note and loop primitives | `aiagent/model/document/EvictedDocumentsNoteTest`, `aiagent/model/AgentConversationTest` (including `withContextUpdate`) |
| Provider rendering | the converter tests under `aiagent/chatmodel/provider/` (langchain4j, Anthropic, OpenAI completions/responses, Bedrock, Gemini, Mistral) |
| Documents into tools | `aiagent/agent/DocumentParameterResolverTest` (exact id match, rejection of tags, escaped tags and ids inside text, arrays, nested, `substituteKnownReferences`), `LocalToolCallHandlerImplTest` (per-call split, order, document parameters of process tools and of local tools) |
| Documents out | `aiagent/agent/ReturnDocumentToolTest`, `AgentResponseHandlerDocumentsTest`, `AgentToolsResolverTest` (reserved name), `AgentSubProcessRequestHandlerTest`, `AgentTaskRequestHandlerTest` |
| Local tool API | `aiagent/localtool/LocalToolBuilderTest`, `LocalToolRegistryImplTest`, the test-only `SaveNoteTool` (stateful, typed arguments, error results, availability) driven through `aiagent/agent/LocalToolCallHandlerImplTest` (including a mixed batch with a real call) |
| Agent instance history of local tools | `aiagent/agentinstance/AgentInstanceToolMapperTest`, `AgentInstanceHistoryMapperTest` (document references in the arguments), `CamundaAgentInstanceClientTest` (tools of the `CONFIGURATION` item, call and result items without element id, identical arguments in every write of a call) |
| Mixed batches | `AgentSubProcessRequestHandlerTest` / `AgentTaskRequestHandlerTest` (one model call, only real calls activated, pending results, status, clearing), `AgentResponseHandlerDocumentsTest` (local calls not emitted), `AgentConversationTurnInputComposerImplTest` (merge in call order, partial arrival, interrupt, no gateway transform, foreign ids ignored), `AgentConversationTest` (`withPendingLocalToolCallResults`), `AgentContextTest` and `AgentContextSchemaMigrationTest` (persistence round trip, missing field), `CamundaAgentInstanceClientTest` (history items of merged results, the early item equals the one of the merged turn start) |

### End-to-end tests (`connectors-e2e-test/connectors-e2e-test-agentic-ai`, WireMock model)

Test classes: `aiagent/subprocess/AgentSubProcessDocumentFlowTests` (sub-process flavor, `@Import` of
the in-memory AWS AgentCore client) and `aiagent/task/AgentTaskDocumentFlowTests` (AI Agent Task
flavor), under `src/test/java/io/camunda/connector/e2e/agenticai/`. Shared: `aiagent/DocumentFlowFixtures`
(scripted turns, upload endpoint stub, upload assertions), `aiagent/DocumentFlowAssertions` (global
request assertion, tool schema access), `aiagent/wiremock/DocumentIdPlaceholderTransformer`. The v2
templates are used because `includeDocuments` only exists there. Processes
`agentic-ai-ahsp-connectors-documents.bpmn` and `agentic-ai-connectors-documents.bpmn` (in
`src/test/resources`) define the tools `Download_A_File` (HTTP connector, stores the response as a
Camunda document), `Upload_A_File` (HTTP connector, multipart POST to `uploadUrl`, body `file` =
`fromAi(toolCall.document, "...", "document")`) and `Ping`. They are separate from the shared tool
processes so the tool specification assertions of the existing tests stay untouched.

How ids are derived: Camunda document ids are generated by the store, so the scripted model cannot
know them. `OpenAiCompletionsChatModelStubs` registers the `document-id-placeholders` transformer on
every response; a placeholder `@@DOC_ID_n@@` in a scripted tool call (`documentId(n)` in the fixtures)
resolves to the n-th distinct `<doc id="..."/>` id (order of first appearance) of the request body the
response answers, whatever the JSON escaping depth; the tool call arguments carry the placeholder as the
plain id the model passes. The test reads the same id from the recorded tool
result tag, so the model-visible id and the id in `agent.documents` are compared, not assumed.

| Scenario | Test | Proves |
|----------|------|--------|
| (a) schema | `toolDefinitionMarksTheDocumentParameterWithTheDocumentFormatAndTheProviderGetsAStringWithAHint` | The first raw request has `type: string`, no `format` and the hint for the document parameter, and `required`; no tool schema of any request has the type `document` or the format `camunda-document-id` (parsed, in `assertNoDocumentInternalsInLlmRequests`); the persisted `agentContext.toolDefinitions` keep `format: camunda-document-id` and hold the hint in the description; no `return_document` while the option is off. |
| (b) round trip | `passesADownloadedDocumentToAToolAndReturnsIt` | Download (job 1), upload with the id (job 2, real connector, multipart `file` part equals the served bytes), `return_document` (local round), final text; `agent.documents` holds the key `report` with the description and the Camunda reference as `document` (`camunda.document.type`, `storeId`, `documentId` = the id the model saw, `contentHash`); selection consumed in `agentContext`. |
| (c) unknown id | `unknownDocumentIdIsAnsweredInTheLoopAndTheModelRetries` (both flavors) | A batch made of the bad call alone is answered in the loop: error tool result with the known documents, the tool endpoint is hit exactly once (by the retry), process completes without incident, four model calls. |
| (d) eviction | `documentOfAnEvictedMessageStaysPassable` | With `contextWindowSize=3` the third request holds neither the download nor its document message, but ends with the eviction note (preamble + tag with tool origin); the model still passes the id and the tool gets the real bytes. |
| (e) stores | `passesADownloadedDocumentToAToolAndReturnsIt` parameterized | Same round trip with in-process, Camunda document and AWS AgentCore memory (the registry lives in `agentContext`, so it survives the job boundaries in every store). |
| (f) task flavor | `AgentTaskDocumentFlowTests` | Round trip and unknown id retry with the AI Agent Task (`agent.documents` on the `AgentResponse`). |
| (g) mixed batch | `mixedBatchOfReturnDocumentAndARealToolCostsNoExtraModelCall` (both flavors) | `return_document` and `Ping` in one assistant message: three model calls in total (download, mixed, final), `Ping` ran, the model got both results in call order, `agent.documents` holds the document under its key, nothing pending afterwards. |
| (g2) mixed batch, early report | `localResultOfAMixedBatchIsInTheHistoryBeforeTheRealCallCompletesAndStaysUnique` (both flavors) | `return_document` and `Slow_Tool` (a job the test holds) in one assistant message: while `Slow_Tool` runs, the committed history already holds the `return_document` result; after the test completes `Slow_Tool` the history holds the results of `Download_A_File`, `return_document` and `Slow_Tool` once each, in that order. |
| (i) history arguments | `agentInstanceHistoryShowsTheDocumentReferenceInTheToolCallArguments` (both flavors) | After the round trip, the history read through the query API shows the Camunda reference (`camunda.document.type`, `storeId`, `documentId`, `contentHash`) and not the id in the arguments of `Upload_A_File` (document parameter) and of `return_document` (the `document` argument), in the `ASSISTANT` and in the `TOOL_RESULT` items, while the model requests keep the tag. |
| (h) mixed batch with an unknown id | `mixedBatchWithAnUnknownDocumentIdRunsTheValidCallAndAnswersTheInvalidOne` (both flavors) | Upload with an unknown id plus `Ping`: `Ping` runs, the next request holds the error result and the `Ping` result in call order, the corrected upload is the only upload, four model calls. |

Global assertion: `DocumentFlowAssertions.assertNoDocumentInternalsInLlmRequests` checks the raw body
of every recorded model request for `storeId`, `contentHash` and `camunda.document.type`. It runs in
every document flow scenario and in the existing tool result and user prompt document tests of both
flavors (48 tests, run against the tag rendering and passing). No production bug was found by the
document flow tests.

Not covered: the real-provider document tests (`DocumentToolCallResultsIT`) and gateway tools (MCP,
A2A), because the feature does not apply to them.

## 9. Local tool API

A local tool is a tool the agent runs itself: the model sees its definition next to the tools of the
process, the agent answers the call and no BPMN element is activated. `return_document` is the first one;
loading a skill or saving a memory are the next. Authors register a tool, a definition plus a handler,
the way tools are registered with other agent frameworks. Code is in
`aiagent/localtool/` (public API and registry) and `aiagent/agent/LocalToolCallHandlerImpl` (execution).

### 9.1 How other frameworks define a tool

Verified means read from the sources jars in the local Maven repository, at the versions in the table.
LangChain4j `ToolExecutor` and the error handlers are in the `langchain4j` artifact (the module depends on
`langchain4j-core` only, same version), Spring AI is not a dependency of the module. The OpenAI Agents SDK
row is from its public documentation and not verified against sources here.

| | Definition | Executor | Arguments | Context | Result | Errors | Enablement | State |
|---|---|---|---|---|---|---|---|---|
| LangChain4j 1.20.2 (verified, sources) | `ToolSpecification` (name, description, `JsonObjectSchema`, metadata); from `@Tool` methods via `ToolSpecifications` | `ToolExecutor.execute(ToolExecutionRequest, memoryId)`; `executeWithContext` with `InvocationContext` | arguments JSON string; `@Tool` methods are bound by reflection, `@P` describes parameters | memory id, `InvocationContext` | `String`, or `ToolExecutionResult` (`isError`, result text or contents, attributes); `ReturnBehavior` (`TO_LLM`, `IMMEDIATE`) | `ToolExecutionErrorHandler`; default of the sync path hands the execution error to the model as text, argument errors are rethrown | `ToolProvider.provideTools(request)`, `isDynamic()` per request | none in the tool; memory by id |
| Spring AI 2.0.0 (verified, sources) | `ToolDefinition` (name, description, input schema string); `ToolMetadata.returnDirect` | `ToolCallback.call(String toolInput [, ToolContext])`; `FunctionToolCallback.builder(name, BiFunction<I, ToolContext, O>)`; `MethodToolCallback` for `@Tool` | JSON string; `FunctionToolCallback` binds to `inputType` and derives the schema | `ToolContext`, a read-only `Map<String,Object>` | any `O`, converted to a string by `ToolCallResultConverter` | `ToolExecutionExceptionProcessor`; default hands a `RuntimeException` message to the model, other throwables are rethrown | `ToolCallbackProvider`/`ToolCallbackResolver` per request | none in the tool; context map from the caller |
| Anthropic Java SDK 2.66.0 tool runner (verified, sources) | `BetaTool`; `BetaRunnableTool.of(Class<T>, run)` derives it from a class | `run`: typed `T` to `ToolResultBlockParam.Content` | JSON bound to the class, local schema validation | none | `String` or content blocks | the runner catches any `Exception` and answers `is_error` with `Error: <message>`; an unknown or unavailable tool also gives an error result | `availableToolNames` of the runner | none |
| MCP Java SDK 2.0.1 (verified, sources) | `McpSchema.Tool` (name, description, input schema) inside `McpServerFeatures.SyncToolSpecification` | `BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult>` | `CallToolRequest` holds a `Map` | the exchange (session, client capabilities) | `CallToolResult` (content list, `isError`, structured content, meta) | the handler returns `isError` itself | tools added and removed at runtime on the server | none |
| OpenAI Agents SDK (documentation, not verified) | `FunctionTool` (name, description, JSON schema, strict); `@function_tool` derives it from a function signature | `on_invoke_tool(ctx, input_json)` | JSON string bound to the signature | `RunContextWrapper` with a user-defined context object | any value, converted to text | `failure_error_function` turns an exception into model-visible text by default | `is_enabled` flag or callable | the mutable context object |

### 9.2 What we adapt and why

- **Definition and handler are one registered object** (LangChain4j, Spring AI, MCP all pair them). A tool
  is one `LocalTool` bean; the registry collects the beans.
- **Typed arguments, no reflection scan.** The Anthropic runner and Spring's `FunctionToolCallback` bind the
  JSON to a type. `LocalTool.builder(..).handler(Class<A>, handler)` does that with the connector
  `ObjectMapper`. Annotation scanning (`@Tool`, `MethodToolCallback`) and schema derivation from a class are
  not adopted: the module has no schema generator, and LangChain4j's must not be imported outside the
  LangChain4j adapter (invariant I1). The schema is a `Map` or the small `LocalToolSchema` builder.
- **Context as the second handler argument** (Spring `ToolContext`, MCP exchange, OpenAI context).
  `LocalToolContext` is read-only and narrow: configuration, agent properties, documents, agent instance
  key, `ObjectMapper`.
- **A result object with an error flag** (LangChain4j `isError`, MCP `isError`, Anthropic `is_error`).
  `LocalToolResult` has content, the flag, and context updates. The flag stays inside the agent because the
  domain tool result has no error flag (blocker 4); the model reads the message as text.
- **Dynamic enablement** (OpenAI `is_enabled`, LangChain4j dynamic providers, MCP add/remove).
  `LocalTool.isAvailable(AgentConfiguration)` is evaluated per invocation.
- **Declarative document parameters.** None of the frameworks has them; a string schema node with the format
  `camunda-document-id` (3.2) is honored for local tools, so a tool that takes a document never validates ids itself.
- **State through context updates.** The frameworks keep state in the caller (memory id, context map, mutable
  context object). The agent runs one job after the other, possibly on different workers, so state must live
  in the persisted `AgentContext`: a result can carry `UnaryOperator<AgentContext>` updates that the
  framework applies. This has no verified counterpart; it follows from where the agent keeps state.
- **Error semantics differ.** Every framework above hands any (runtime) exception to the model by default.
  A local tool is code of the connector, not a user integration, and it can change agent state, so an
  unexpected exception must not turn into text the model reads: it propagates and fails the job like any
  other connector error. Expected failures are reported with `LocalToolException` or `LocalToolResult.error`.
- **Not adopted:** return-direct behavior (the model loop decides what happens after a result), asynchronous
  executors, tool search.

### 9.3 The API

```java
public interface LocalTool {
  ToolDefinition definition();
  default boolean isAvailable(AgentConfiguration configuration) { return true; }
  LocalToolResult call(LocalToolCall call, LocalToolContext context);

  static Builder builder(String name);
  static Builder builder(ToolDefinition definition);
  // Builder: description(String), inputSchema(Map | LocalToolSchema),
  //          available(Predicate<AgentConfiguration>),
  //          handler(Handler)                      // raw call
  //          handler(Class<A>, TypedHandler<A>)    // arguments bound to A
}

public record LocalToolCall(String id, String name,
    Map<String, Object> arguments,          // as the model sent them (document ids)
    Map<String, Object> resolvedArguments)  // declared document parameters replaced by the references
{ <A> A argumentsAs(Class<A> type, ObjectMapper objectMapper); }

public record LocalToolContext(AgentConfiguration configuration, Map<String, Object> properties,
    DocumentRegistry documents, @Nullable AgentInstanceKey agentInstanceKey, ObjectMapper objectMapper) {}

public record LocalToolResult(Object content, boolean error, List<UnaryOperator<AgentContext>> contextUpdates) {
  static LocalToolResult success(Object content);
  static LocalToolResult error(String message);
  LocalToolResult withContextUpdate(UnaryOperator<AgentContext> update);
}

public class LocalToolException extends RuntimeException {}   // an error the model can act on
public interface LocalToolRegistry { Set<String> toolNames(); List<LocalTool> availableTools(AgentConfiguration);
    Optional<LocalTool> findAvailable(String name, AgentConfiguration); Optional<ToolDefinition> findDefinition(String name);
    List<ToolDefinition> availableToolDefinitions(AgentConfiguration); }
```

`LocalToolSchema.object().string(..).integer(..).number(..).bool(..).stringArray(..).document(..).build()`
creates a flat object schema; `document(..)` is a parameter with the format `camunda-document-id`.

### 9.4 What the framework does

1. **Offering.** `BaseAgentRequestHandler.converse` appends `availableToolDefinitions` to the process tools in
   `AgentConfiguration.toolDefinitions()`. Every provider converter, the window and the agent instance
   `CONFIGURATION` item read that list; the persisted `AgentContext.toolDefinitions` never contain local tools.
2. **Split.** For each call of the last assistant message, in order: a call of an available local tool runs;
   a call of a process tool with an invalid document parameter is answered with an error; every other call
   remains for the process (3.6). A call of a registered but unavailable local tool remains for the process.
3. **Document parameters.** If the definition declares document parameters, `DocumentParameterResolver`
   resolves them against the registry first. An invalid value answers the call with the error text of 3.7 and
   the tool does not run. The tool sees `arguments` (ids) and `resolvedArguments` (references).
4. **Run.** The tool runs with a `LocalToolContext` of the working conversation. The typed handler binds
   `resolvedArguments` to its type; a parameter of type `Document` receives the registry document, and an
   argument that does not fit the type answers the call with "Invalid arguments for tool '<name>': ...".
5. **Result.** The content becomes the `ToolCallResult` (text or object content, a `Document` inside renders
   as its tag, 3.4). The context updates are applied to the working conversation in order, call after call,
   so a later call of the batch sees the state an earlier one left. An error result carries no update.
   The results follow the paths of 3.6 (synthetic round or pending results) and are reported as in 5.1.

### 9.5 Errors

| Situation | Outcome |
|---|---|
| `LocalToolException` thrown, or `LocalToolResult.error(..)` | error result with the message; logged at debug; no context update |
| invalid document parameter | error result of 3.7; the tool does not run |
| arguments do not fit the typed handler | error result "Invalid arguments for tool '<name>': <reason>" |
| any other exception (bug, I/O failure, `ConnectorException`) | propagates; the job fails or becomes an incident like any connector error |

### 9.6 Availability and reserved names

`isAvailable` is evaluated against the configuration of the invocation, both when the definitions are offered
and when calls are split, so they cannot diverge. The task flavor evaluates its input per iteration, so
availability can change between jobs; a call of a tool that is no longer available goes to the process.

Names are reserved for every registered tool, available or not (3.9). Reasoning: availability depends on a
configuration that can change while the agent runs, and a BPMN element that is valid today must not turn into
a collision on the next iteration; the check also has no configuration at hand on process migration. The
cost is that a modeler cannot name an element `return_document` even with the option off. Uniqueness among
local tools is checked when the registry is created (startup).

### 9.7 Add a local tool

1. Pick a name (it becomes reserved) and write the definition: description, input schema, with a
   document parameter (`LocalToolSchema.document`) if the tool takes documents.
2. Write the handler. Typed arguments are the usual case:

   ```java
   record Note(@Nullable String text) {}

   LocalTool saveNote =
       LocalTool.builder("save_note")
           .description("Saves a note for later.")
           .inputSchema(LocalToolSchema.object().string("text", "The text of the note", true))
           .available(configuration -> configuration.memory() != null)
           .handler(
               Note.class,
               (note, context) -> {
                 if (note.text() == null || note.text().isBlank()) {
                   return LocalToolResult.error("The note must not be blank.");
                 }
                 return LocalToolResult.success("Saved.")
                     .withContextUpdate(agentContext -> agentContext.withProperty("lastNote", note.text()));
               });
   ```

3. Register it as a Spring bean of type `LocalTool` (`@Bean`); `LocalToolRegistryImpl` collects every bean.
4. Report failures the model can act on with `LocalToolResult.error` or `LocalToolException`; let bugs
   propagate.
5. Build a schema passed as a `Map` insertion-ordered (`LinkedHashMap`, not `Map.of`): the request sent to the
   model carries the schema in the order of the map, and `Map.of` iterates in a different order in every JVM.
   The configuration fingerprint does not depend on this order (it hashes the schema with sorted keys).
   `LocalToolSchema` does this and `LocalTool.builder` keeps the order of the map it is given; construct a
   definition by hand with the `ToolDefinition` constructor, not `ToolDefinition.builder()` (the record
   builder copies the schema into an immutable map of unspecified order).
6. Change agent state only through `withContextUpdate`, never in a field of the tool. The update receives the
   persisted `AgentContext`; use `AgentContext.properties()` for free-form state.
7. Test it through `LocalToolCallHandlerImpl` with a conversation, as `LocalToolCallHandlerImplTest` does
   with the test-only `SaveNoteTool`. Add an end-to-end test when the tool changes what crosses a job
   boundary.

### 9.8 Limits

- The schema helper is flat; build nested schemas as a `Map`.
- Document parameters of local tools are found the same way and have the same limits as in 3.3 (no `anyOf`/`oneOf`).
- A local tool cannot start another model call or activate a BPMN element.
