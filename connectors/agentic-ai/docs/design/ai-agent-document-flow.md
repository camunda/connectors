# AI Agent document flow

Design of the document flow of camunda/experience-pdp#81 in the AI Agent (`connectors/agentic-ai`),
covering both flavors (AI Agent Sub-process and AI Agent Task). Paths below are relative to
`connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/` unless stated otherwise;
line numbers refer to the current sources.

## Execution model

This is the model every later section details. It applies to an agent instance with the `documents` feature
(all instances created from this version on); the last point describes the other instances.

**Reference by default.** A document that enters the conversation is not shown to the model as content. The
agent registers it and shows a `<doc id=".." fileName=".." contentType=".." size=".." .../>` tag
(section 3.4). Documents enter at three places: the user prompt, event data and the results of tools,
including nested documents and documents of gateway tools. The model never sees a store id, content hash,
URL or other reference detail. Two opt-ins deliver the content of some documents without the model asking
for it (next paragraphs and section 3.15).

**Ingestion registers and tags.** The composer collects every document of the turn input, the registry of the
`agentContext` (`AgentContext.documents`) keeps the complete Camunda document reference per id, and the
persisted messages hold the tag only (sections 2, 4, 4.1). The registry is independent of the conversation
store and of the context window.

**Three things the model can do with a known document.**

1. *Pass its id to a tool.* A tool parameter declared with `fromAi(toolCall.x, "desc", "document")` takes the
   id. The agent validates it and the tool receives the full Camunda document reference JSON in the process
   variable, so a BPMN tool (an HTTP connector, a job worker) gets what any other connector would. Passing
   ids never loads content into the conversation (sections 3.2, 3.3, 3.7, 3.13).
2. *Load its content with `c8__get_document`.* The tool takes the id and loads the whole document, no
   ranges. It is the way content enters the conversation unless the agent loads it for the model (inline
   delivery, below). Before loading it rejects, with an error
   tool result the model can act on (the usual retry path, no incident): a document that is already in the
   windowed conversation, a document of a content type outside the allowlist, a document above the size
   limit (section 3.14).
3. *Return it with `c8__return_document`.* The model selects a document under a key and the final response
   carries it in `responseDocuments`, so the process reads `agent.responseDocuments.<key>.document`
   (section 3.8).

Both `c8__` tools are local tools: the agent runs them itself, no BPMN element is activated (section 9). Both
are offered to every instance with the `documents` feature and to no other; a tool element of the process must not
have the name of a local tool (sections 3.9, 3.10).

**Inline delivery (opt-in).** Two opt-ins make the agent load the content as if the model had called
`c8__get_document`, for the documents of the user prompt and for the documents a tool returns; the default stays
reference-only. The option *Load documents into the prompt* of the user prompt (v2 templates, property
`data.userPrompt.loadDocuments`) applies to the documents of that prompt. The extension property
`io.camunda.agenticai.documents.delivery` with the value `inline` on a tool activity applies to the documents
that tool returns; it is stored as `ToolDefinition.documentDelivery` so it is known when the result arrives in a
later job. The agent loads through the same guard as `c8__get_document`, with the same limits and the same
persisted marker, so no storage is added. When the guard rejects a document the agent does not fail: the
document stays a reference and a note tells the model that it was not loaded and why (section 3.15). A
document that is already loaded is not loaded twice. An instance without the `documents` feature ignores both
opt-ins.

**Persisted form and provider form.** A document is persisted as its tag, a `TextContent` whose metadata is
`{"kind": "document-reference", "documentId": "<id>"}`. A document that `c8__get_document` loaded, or that the
agent loaded for a tool result, is persisted in a follow-up user message after the tool results (preamble
`System message, not written by the user: the content of the documents loaded for the tool results above follows.`) whose tag carries `"loaded": true` in addition; a document that the agent
loaded for the user prompt is that marked tag in the user message itself. The content is not persisted in the conversation. When the conversation
is windowed for a model call, the tags marked as loaded are expanded from the registry: the provider gets the
tag text followed by the native content (text, file or image). Tags without the marker are never expanded,
and text a user typed that happens to hold a tag is never touched (section 4.1). The loaded content stays
in every model request until the message that holds the marker leaves the window (open point, section 7).

**Guards of loading.** They are the same for every load, whoever triggers it (`DocumentLoadGuard`). The type allowlist is text-based types, `application/pdf` and the images PNG, JPEG, GIF
and WebP; a document of unknown type (for example an external document, whose type is known only after a
download) is treated as text. The size limit is 256 KB for text-based types and 10 MB for PDF and images; the
size comes from the reference metadata and, when there is none, from reading at most one byte more than the
limit. A rejected document is not loaded; the answer of `c8__get_document`, or the note of an automatic load,
tells the model to pass the id to a tool that processes it (sections 3.14 and 3.15).

**History auditability.** The agent instance history is an auditable record of what the model receives: each
message of a model request is covered by a history item, in a different representation where needed
(section 5.2). The loaded content shows as a `DOCUMENT` part of the follow-up `USER` item; the tag text is
shown in inline code. One message is not reported yet, the evicted-documents note (section 7).

**Instances without the feature.** An instance created by Camunda 8.10 and still running has no `features` in its
`agentContext` and keeps the 8.10 behavior: documents are content in the conversation, no registry, no local
tool, no name checked against local tools (section 4.2). A feature is assigned only when an instance is created and never added later.

## 1. Scope

1. `fromAi(toolCall.x, "desc", "document")` tool parameters and how they materialize in the tool
   definition sent to the model.
2. The agent remembers every document in its conversation (persisted registry), independent of the
   conversation store and of the context window, and shows it to the model as a tag.
3. The agent passes a known document to a tool. The tool receives the real Camunda document reference JSON.
4. The model loads the content of a known document into the conversation on request (`c8__get_document`),
   within a type allowlist and a size limit.
5. The agent returns selected documents in its response (`c8__return_document`).

Out of scope: documents for gateway tools (MCP, A2A), Modeler support, document retention (see
sections 6 and 7).

The flow described here belongs to the `documents` feature of an agent instance (section 4.2). An agent
instance created without it keeps the document handling it was created with; section 4.2 describes that
handling. Sections 2 to 9 describe the instances that have the feature, which are all instances created
from this version on.

## 2. Architecture overview

A request (one job of the AI Agent connector) passes through these stages. Documents enter the
conversation at ingestion, are remembered in the registry, are shown to the model as tags, can be
referenced by their id in tool calls, are loaded as content only when the model asks for it, and leave
through the response.

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
   (`ingestToolCallResultDocuments`, reuses `ToolCallResultDocumentExtractor`) and event
   documents (`createEventMessage`). The composer creates the ids of the messages first, so each
   source carries the id of the message that introduced the document. The list is ordered prompt and
   tool results first, events last, matching the message order.
3. **Registry.** `BaseAgentRequestHandler.converse` passes the new documents to `registerDocuments`.
   It loads the registry through `DocumentRegistryStorage`, adds the new
   documents and returns the agent context carrying the result. `proceed` rehydrates the `AgentConversation` from
   that context, so every copy-on-write step and `AgentConversation.toAgentContext()` carry the
   registry, and it is persisted by the same job completion as the rest of `agentContext`
   (`AgentContext.documents`, `aiagent/model/AgentContext.java`). The `Deferred`, `NoInput` and
   no-op paths do not register anything because nothing enters the conversation there.
4. **Rendering.** The composer stores one `<doc .../>` tag (`aiagent/model/message/DocumentReferenceXmlTag.java`)
   per document and no document content (section 4.1). The documents of a tool result are shown as
   their tag inside the tool result, without a follow-up message. `AgentConversation.window` expands
   only the tags marked as loaded from the registry (`DocumentExpander`), so the providers see a
   loaded document's tag followed by its `DocumentContent`; the model loads a document with the
   local tool `c8__get_document` (`GetDocumentTool`), whose result the composer follows with a
   message that carries the loaded tag. The composer does the same for the documents of a tool with inline
   delivery and marks the tags of the user prompt documents as loaded when the prompt asks for it (3.15).
   `aiagent/chatmodel/provider/ModelFacingJson.java` renders a `Document` at any depth of tool result
   or message object content as its tag, for every provider path. `AgentConversation.window`
   also appends the eviction note (`EvictedDocumentsNote.appendTo`) to the windowed snapshot. The tool
   definitions of the snapshot are those of `AgentConfiguration.toolDefinitions()`: the tools of the
   process plus the available local tools, among them `c8__get_document`
   and `c8__return_document` (section 9), with every document parameter rewritten for the provider
   (section 3.2).
5. **Model loop.** `BaseAgentRequestHandler.driveContinuationLoop` windows the conversation, calls the
   model, ingests the assistant message, and then either ends the loop or opens another round:
   `nextContinuationRound` (`AgentConversation`) for provider continuations, `nextLocalToolRound`
   (`AgentConversation`) when the agent answered every tool call of the message itself.
6. **Local tool handling.** `localToolCallHandler.handle` runs the call of every available `LocalTool`
   (`c8__get_document` and `c8__return_document` are the built-in ones, section 9; the tools are beans collected by `LocalToolRegistry`,
   `autoconfigure/AgenticAiConnectorsAutoConfiguration.java`), answers the calls of process tools whose
   document parameter is not the id of a known document, and splits the batch into local results and the calls that
   remain for the process (section 3.6). The context updates of the local results (the selection of
   `c8__return_document`) are applied to the conversation the handler returns, in both outcomes. Without
   remaining calls the loop opens a synthetic round (`applyTurnStart`, `throwIfLimitsReached`); with remaining calls the local
   results are kept as `AgentContext.pendingLocalToolCallResults` (`withPendingLocalToolCallResults`) and
   the loop ends with the normal tool calling status.
7. **Response.** `AgentResponseHandlerImpl.createResponse`. It leaves out the calls the pending local results
   answer, then `DocumentParameterResolver.resolveAll` replaces document ids by registry references in the
   process variable only. For a response without tool calls the selection is turned into `responseDocuments`; the
   selection stays in the context. `AgentSubProcessRequestHandler.createAgentResponseVariable` copies `responseDocuments` into the `agent`
   variable; the task flavor returns the `AgentResponse` itself. `AgentSubProcessRequestHandler` uses
   `AgentConversation.hasInterruptedToolCallResultsInInvocation` so that interrupted results of the first
   round still cancel the remaining tool instances after a local round replaced the current turn.
8. **Next job.** `AgentConversationTurnInputComposerImpl.resolveOrderedToolCallResults` merges the pending
   local results with the arrived process results (section 3.6); `BaseAgentRequestHandler.converse` clears
   the pending field when it applies the turn. The composer then adds, right after the tool result message,
   the follow-up message of the documents loaded for the results, by `c8__get_document` or by the agent for a
   tool with inline delivery (`composeLoadedDocumentsMessage`, sections 3.15 and 4.1); the
   `c8__get_document` part of that composition also runs for the synthetic round of a batch answered locally.

Both flavors share `BaseAgentRequestHandler`, so everything above applies to both.

## 3. Decisions

### 3.1 Principal decisions

| # | Decision | Reason |
|---|----------|--------|
| 1 | The registry is persisted in `agentContext` (typed field `documents`) behind a small storage interface. | Deriving it from nested tool result documents is hard; filling at ingestion reuses the existing deep extraction. One place for all stores (including AWS AgentCore); commits atomically with job completion. |
| 2 | Document id is `DocumentHandle.idFor`. The model sees only `<doc id [fileName] [contentType] [source] [toolName] [toolCallId] />`. | Never expose `storeId`, `contentHash`, other metadata or URLs (presigned URLs carry tokens). |
| 3 | Documents out are a built-in local tool `c8__return_document`, run by the agent in the model loop. | The tool must never become a BPMN element activation. |
| 4 | A document parameter that is not exactly the id of a known document (an unknown id, text around an id or a tag, two tags) yields an error tool result and the model retries. | No incident; the tool does not run. The result is delivered with the next model call: in the same job when the whole batch was answered locally, in the next job together with the results of the real calls of the batch otherwise; the agent instance history shows it right away in both cases. |
| 5 | Tools receive the full Camunda document reference JSON. | Same shape other connectors emit and consume; no agent-internal handle leaves the agent. |
| 6 | `c8__return_document` and `c8__get_document` are offered to every agent instance with the `documents` feature and `responseDocuments` is added to the response when the selection is non-empty; there is no opt-out. | A small configuration surface. The effects of an injected tool are recorded as an open question (section 7). |
| 7 | Documents are reference-only in the conversation: a tag, no content. Content enters only through `c8__get_document`, which loads a whole document, within a type allowlist and a size limit. Passing an id to a tool never loads content. | Content in the conversation costs tokens in every later model call and can make a model call fail; the model decides which documents it needs to read, and a document that is too large or of an unsupported type is handled by a tool instead (section 3.14). |
| 8 | The marker of a loaded document is metadata of the tag (`loaded: true`) in a follow-up user message or in the user message of the prompt; the content is added at window time from the registry, never persisted in the conversation. | One copy of the reference (the registry); stable request bytes for prompt caching; the stored conversation stays small and replayable (section 4.1). |
| 9 | Inline delivery is opt-in: the option of the user prompt and the marker of a tool activity. It reuses the guard and the marker of `c8__get_document`; a document the guard rejects stays a reference and a note tells the model. | Reference-only stays the default because content costs tokens in every later call; the opt-in serves processes that know the model needs the content, without a round trip per document; automatic loading must not fail a run for a document the model could not read anyway (section 3.15). |

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
sees, or the tag itself (section 3.7). The description of every document parameter ends with the hint `DocumentParameterSchema.HINT` (the hint
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
             "description": "Pass the id of a known document (the id attribute of its <doc /> tag) or the tag itself."}}},
 "required": ["docs"]}
```

which the provider gets as

```json
{"type": "object",
 "properties": {"docs": {"type": "array", "description": "The attachments",
   "items": {"type": "string",
             "description": "Pass the id of a known document (the id attribute of its <doc /> tag) or the tag itself."}}},
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
schema-walking complexity now). A parameter that accepts either a string or a document is such a union, so it
is not supported yet. Its explicit form would be the tag: a value that is exactly one `<doc />` tag is a
document, any other value is a string.

### 3.4 Tag format

`<doc id="..." fileName="..." contentType="..." size="..." source="..." toolName="..." toolCallId="..." />`,
attributes in that order, XML-escaped, blank or unknown attributes omitted (no placeholders). `fileName` is the
Camunda metadata file name, the external reference `name` or the inline `name`; it is omitted when it
equals the id (a document stored without a name of its own would only repeat the id) and, for an
external document without a name, never taken from the URL. `contentType` comes from the Camunda
metadata or the `contentType` of an inline reference and is omitted for external documents because their
metadata is only available after a download. `size` is the number of bytes, present for a Camunda document whose
reference metadata carries one; it is never looked up by reading the document, and it lets the model see
before a load whether a document is large. `toolName` and `toolCallId` are present for documents that
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
The real bytes reach the model only when it loads the document with `c8__get_document`: they travel
in the user message that follows the tool results, where the tag text precedes each native block, so
the id in the tool result entry and next to the native block is the same. Two
native block names are not the id: the Bedrock `DocumentBlock.name` is a hash derived from the id
(Bedrock charset rules) and the OpenAI `input_file.filename` is the file name; the preceding tag text
carries the id.

### 3.5 Eviction note mechanism

When the conversation is windowed, the registry of the current `AgentContext` is compared with the
windowed messages. A registry document is visible when its id appears as `DocumentContent` of a user
message, as a `Document` in a tool result content tree (`ContentTreeDocumentWalker`), or as the id of
a `<doc/>` tag in user or tool result text, or when the message that introduced it
(`DocumentSource.messageId`) is windowed. The last rule covers a document that the walk of the content
does not find, for example one extracted from the result of a gateway tool: it is shown as part of its own
tool result and must not be listed next to it. If at least one document is not visible, a user message is appended to the snapshot only: the preamble
`EvictedDocumentsNote.PREAMBLE` plus the tag of each such document (`DocumentReferenceXmlTag.from(entry)`: id, file name, content type, source or tool call). It is
never part of `allMessages()`, never persisted and leaves the frozen system prompt alone.

Reason for a trailing note instead of a system prompt addition or a rewritten message: the system
prompt is frozen per agent instance; stored messages must stay byte-stable for replay and prompt
caching; the note is the only thing that changes when the window slides, and as a suffix it keeps
cached prefixes valid. The note follows tool result and follow-up messages that are user role anyway. A
document the note lists is known to the registry, so the model can pass its id to a tool or load it with
`c8__get_document`.
It is skipped when the windowed messages end with an assistant message (a continuation round resumes
on that message), so such a round does not repeat the note it already had in the first round of the
invocation.

### 3.6 Batch policy (mixed batches)

A batch is split per call. `LocalToolCallHandler.handle(conversation, toolCalls)` returns a
`LocalToolCallSplit(conversation, localResults, remainingCalls)`; `conversation` is the input conversation
with the context updates of the local results applied:

- **Local calls.** A call of an available local tool (section 9) is run by the agent and gets its result
  immediately: the built-in `c8__return_document` and every other registered tool. A call of a process tool
  whose document parameter is not a known id is answered with an error result listing the known
  documents.
- **Remaining calls.** Every other call goes to the process like any tool call, also a call of a local
  tool that is not available for this agent.

The request handler then proceeds by what is left:

| Remaining calls | What happens | Model calls |
|-----------------|--------------|-------------|
| none | The local results become the input of a synthetic round of the same job (`nextLocalToolRound`); the model is called again. The round counts against `maxModelCalls` (`throwIfLimitsReached`). | one more in the job |
| some | The job completes with only the remaining calls activated (`ToolCallProcessVariable` list and element activations hold only them, status `TOOL_CALLING`). The local results are stored in `AgentContext.pendingLocalToolCallResults` and reported to the agent instance history right away (5.1). | none extra |

The context updates of the local results, for `c8__return_document` the selection in
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

A document parameter value is a string that is either exactly the id of a document of the registry or exactly
one `<doc ... />` tag, surrounded by whitespace at most. An id is compared as a whole: it is not trimmed,
unescaped or extracted from other text. For a tag, the XML-unescaped `id` attribute is the id, and the other
attributes are ignored (`DocumentReferenceXmlTag.idOfSingleTag`). An unknown id, an id with surrounding
whitespace, an id or a tag inside other text, and a value with more than one tag are rejected. A value that
reaches the process is the Camunda document reference of the registry entry; the persisted assistant message
keeps the value exactly as the model sent it.

One sentence per invalid value, then the known documents (at most 50 are listed, the rest is
counted):

```
Parameter 'docs[1]' must be the exact id of a known document (the id attribute of its <doc /> tag) or that tag itself, got 'x'. Known documents:
<doc id="..." fileName="..." contentType="..." toolName="..." toolCallId="..." />
```

The parameter path is `name`, `parent.name` for nested objects and `name[i]` for array elements. A
non-string value reads "Parameter 'p' must be the exact id of a known document (the id attribute of its
<doc /> tag) or that tag itself, a string.", an array parameter that is not an array "Parameter 'p' must be an array.". With an empty
registry the list is replaced by "No documents are known.". Results are plain text because no provider
has a tool error flag in the domain model, so the model reads the text. Reason: the list of known tags
lets the model correct itself in one retry without any further tool call. The error result reaches the model with its next request: in the synthetic round when the whole batch was local, in the next job together with the results of the real calls of the batch otherwise (section 3.6).

### 3.8 `c8__return_document` semantics

`ReturnDocumentTool` (`aiagent/localtool/ReturnDocumentTool.java`) is a `LocalTool` with `NAME =
"c8__return_document"`. The model calls it once per document with the arguments `key` (required string, the
stable identifier the process finds the document by, matching `[A-Za-z_][A-Za-z0-9_-]*`), `document` (required,
the id of the document) and `description` (optional string). `document` is a document
parameter (string schema with the format `camunda-document-id`), so the framework reports a value that is not
a known id with the texts of 3.7 before the tool runs; the tool receives the id and selects the document by it. The tool
is offered to every agent instance with the `documents` feature. Its description tells the model to use it only for a document the
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
- **Return on every final response.** A response without tool calls returns the selection as `responseDocuments`, a map
  `key -> ReturnedDocument{document, description?}` in selection order, where `document` is the registry entry
  `reference` map (selections whose id is not in the registry are skipped). The selection stays in the
  returned context for the lifetime of the agent instance, so each later final response (for example after a user
  feedback turn) carries the documents of the earlier ones and the new ones; a key that is returned again replaces
  its entry in place. A response with tool calls leaves it alone. The sub-process flavor copies the map
  into the `agent` variable, so the process reads `agent.responseDocuments.report.document`; all documents are
  `get entries(agent.responseDocuments).value.document`.
- **No retract.** There is no tool to remove a key. A model can only replace the document of a key. A
  document is only ever returned if the model named it, and the answer text names the key of that call.
- A selection recorded in a batch that also holds real calls (3.6) is persisted with that job, before the
  real calls ran, because the model receives the "Returned ..." result for it with the results of the batch.
- `responseDocuments` is added to the response whenever the selection is non-empty and omitted otherwise
  (`@JsonInclude(NON_EMPTY)`); there is no option that switches the tool or the field off (3.10).

A valid `c8__return_document` call together with real tool calls costs no extra model call: the real calls run
and the model receives all results in the call order (3.6). Only a batch made of local calls alone starts a
synthetic round, which counts against `maxModelCalls`.

### 3.9 Local tool names

The names of the built-in local tools share the prefix `c8__` (`InternalToolNames.PREFIX`) by convention; the
prefix is not enforced. A tool element of the process and a custom local tool may have any name, as long as
no tool element of the process has the name of a registered local tool.
`AgentToolsResolverImpl.loadAdHocToolsSchema` rejects every ad-hoc or gateway tool element whose name equals
the name of a registered local tool, for an agent instance with the `localTools` feature (section 4.2), with a
`ConnectorException` (`AgentErrorCodes.ERROR_CODE_LOCAL_TOOL_NAME_CONFLICT`) whose message names the tool and says
to rename the element or the local tool. The check covers every registered tool, whether the agent offers it
or not. `loadAdHocToolsSchema` runs on tool discovery and on process migration (`updateToolDefinitions`), so
both paths are covered. Reason: a BPMN element with the name of a local tool would collide with the local tool
in the tool list and silently lose to it, and the availability of a tool can change while the agent runs.
`LocalToolRegistryImpl` rejects a blank name and a duplicate name when the registry is created.

An agent instance without the `localTools` feature checks no name (section 4.2): it is offered no local tool,
and its process, which may already name an element `c8__return_document` or anything else, must keep running.

The element id of a gateway element is checked like the id of any tool element. The names of the tools a
gateway generates (the MCP gateway names a tool `MCP_<elementId>___<mcpToolName>` and the A2A gateway
`A2A_<elementId>`, see `McpToolCallIdentifier`, `A2aToolCallIdentifier`) start with `MCP_` or `A2A_`, so a
local tool must not use these names either.

### 3.10 Availability of the `c8__` tools

`c8__return_document` and `c8__get_document` are injected for every agent instance with the `documents` feature;
no template property or request field switches either off. `LocalToolRegistryImpl` offers no local tool to an
instance without the `localTools` feature (`AgentConfiguration.supportsLocalTools()`), and `LocalTool.isAvailable`
is a generic hook of the local tool API with the default `true`. Both built-in tools work with the document
registry and override it to require the `documents` feature (`AgentConfiguration.supportsDocuments()`).
`BaseAgentRequestHandler.converse` puts the definitions of the available local tools after the process tools in
`AgentConfiguration.toolDefinitions()` for the invocation; they are not part of `AgentContext.toolDefinitions`, so
no persisted tool list changes and the migration check (`updateChangedToolDefinitions`) does not see the tools.
The configuration fingerprint covers the offered tools (section 5.1). A process tool must not have one of their names (3.9). The response
carries `responseDocuments` whenever the final response has a selection. Reason: a small configuration
surface; whether the tools should become configurable is an open question (section 7).

### 3.11 Templates and versions (v1 frozen)

The document flow adds one template property: the checkbox *Load documents into the prompt*
(`data.userPrompt.loadDocuments`, group user prompt, next to *Documents*) of the v2 templates. The v1 templates are
deprecated and frozen: the transform scripts leave the property ids of the pom property `v1OmittedPropertyIds`
(`omittedPropertyIds` of the scripts) out of the v1 task, sub-process and hybrid templates, so their JSON is
unchanged. The runtime is shared, so new agent instances modeled with the v1 templates are offered the `c8__`
tools as well; they never set the option. There is no template version bump and the
`element-templates/README.md` rows are unchanged.

### 3.12 Inline documents

The registry entry keeps the complete reference, so for an inline document it contains the full
content (base64 in `content`). Reason: the tool payload of decision 5 must be the real reference.
External documents keep the URL (never rendered to the model, decision 2) and Camunda documents keep
the full reference with metadata. The agent instance history shows the reference of a document in the
blocks of its items (section 5.1), so the content of an inline document is part of the history item too. The cost
is described in section 4 and listed as a limitation in section 5. The size guard of `c8__get_document` (3.14)
bounds what a load puts into the conversation, not what the registry, the tool result and the history hold of an
inline document, and the history mapper does not support inline references at all (section 7).

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

### 3.14 `c8__get_document`: loading content

`GetDocumentTool` (`aiagent/localtool/GetDocumentTool.java`) is a `LocalTool` with `NAME = "c8__get_document"`.
Its only argument is `id`, a document parameter (3.2), so the framework checks the id and answers an unknown
id with the text of 3.7 before the tool runs; the tool receives the id, also when the model passed the tag, and
reads the document from its registry entry. The description tells the model to use it when it needs the
content of a document it sees as a tag, that only text, PDF and image documents can be loaded, that a document
that is too large is passed by id to a tool that processes it (a sandbox, for example), and that a document that
is already in the conversation is not loaded again. The tool checks, in this order, and answers an error result
(plain text, no incident, the model retries or goes another way) without loading in the three rejecting cases:

| Check | Outcome |
|-------|---------|
| The document is already in the windowed conversation (a tag marked as loaded in a windowed user message, `AgentConversation.loadedDocumentIds`) | Success, `Document <id> is already in the conversation.`; nothing is marked, so no second copy of the content. After the message holding the marker left the window, the document can be loaded again. |
| The content type is outside the allowlist (`DocumentLoadLimits.isLoadable`) | Error, `The document is of type X which cannot be loaded into the conversation; pass its id to a tool that processes it.` |
| The size exceeds the limit of the type (`DocumentLoadLimits.limitFor`) | Error, `The document is N KB, too large to load into the conversation (limit L KB); pass its id to a tool that processes it.` |
| Otherwise | Success, `Loaded the document <doc .../>. Its content follows in the next message.` with the tag of the document, built from its registry entry (the tag the model saw before); the result carries the property `loadedDocumentId`. |

The checks of the three rejecting cases are `DocumentLoadGuard` (`aiagent/localtool/DocumentLoadGuard.java`), which
also serves the automatic loads of section 3.15; `GetDocumentTool` adds the registry lookup and the already-loaded check.

**Type allowlist.** Text-based types (`text/*`, `application/json`, `xml`, `yaml`, `x-yaml`, `csv`, `x-ndjson`,
`javascript`, `sql`, `toml`, and the suffixes `+json`, `+xml`, `+yaml`), `application/pdf`, and the images
`image/png`, `image/jpeg`, `image/gif` and `image/webp`. Parameters of a type (`; charset=..`) and the case are
ignored. Reason: a loaded document stays in the conversation until its message leaves the window, so a type the
provider cannot render would fail every later model call (as a failed model call, not as a tool error). The
allowlist is provider-independent and conservative; a provider-specific capability matrix is an open point
(section 7), so a provider without PDF support still fails at the model call.

**Unknown type.** A document whose content type is not known without reading it (an external document, whose
metadata is available only after a download, or a Camunda document without a content type) is not rejected: it is
treated as text and gets the stricter text limit. Reason: rejecting it would make external documents
unloadable; the stricter cap bounds the damage of a binary document that is mistaken for text.

**Size limit.** 256 KB for text-based types, 10 MB for PDF and images. The size is read from the `size` of the
reference metadata without reading the document; when the metadata has none, the tool reads at most one byte
more than the limit. A document that cannot be read for that check answers "The document could not be read to
check its size: ...". Bytes are a poor proxy for tokens (a PDF of 214 KB can cost about 230,000 input tokens);
a token-based guard is an open point (section 7).

**What a successful load does.** The composer follows the tool result message with a user message
(`composeLoadedDocumentsMessage`): the preamble `System message, not written by the user: the content of the documents loaded for the tool results above follows.` and the tag of every
document that a result of the turn loaded, each marked `loaded: true`; the metadata `toolCallDocuments` makes the
message leave the window together with its tool results (`MessageWindowFilter`). A tag of a document that the
registry does not know is not added. At window time `DocumentExpander` adds the content after each marked tag
(4.1). A load in a mixed batch (3.6) goes the same way: the result is a pending local result, and the follow-up
message is composed together with the merged tool result message in the next job.

### 3.15 Inline delivery

Inline delivery loads the content of a document for the model without a `c8__get_document` call. It has two
sources and one mechanism; the default of every document stays a reference.

**User prompt.** The boolean `UserPrompt.loadDocuments` (`AgentInput.UserPrompt`, from the optional property
`loadDocuments` of `PromptConfiguration.UserPromptConfiguration`) is set by the checkbox *Load documents into the
prompt* of the v2 templates (3.11). The composer (`createUserPromptMessage`) puts the tag of each prompt document
into the user message marked as loaded when the document passes the guard; a document that does not stays a
plain tag, followed by a note text. Without the option the prompt documents are plain tags as before.

**Tool results.** The extension property `io.camunda.agenticai.documents.delivery` of a tool activity (a
`zeebe:property` in the `zeebe:properties` of the element, read where the tools are discovered, like the gateway
type) with the value `inline` (case-insensitive; any other value keeps the documents by reference) sets the
optional `ToolDefinition.documentDelivery` (`DocumentDelivery.INLINE`; absent means by reference). The mode is part of
the stored definition, so it is known when the result arrives in a later job, and a change after a process
migration is stored like any other change of a tool definition. It is part of the configuration fingerprint
when set, appended after the schema of the tool, so the fingerprint of a tool without it is the one it always
had. It is serialized only when set and never reaches the model providers
(`DocumentParameterSchema.toProviderToolDefinitions` drops it). The composer loads the documents of the result
of such a tool, including nested ones, into the follow-up message of the tool results: the preamble `System message, not written by the user: the content of the documents loaded for the tool results above follows.`, the
tag of every loaded document marked as loaded with the tool origin, then the notes. The tool result itself still
shows the tag. The documents of gateway tools (MCP, A2A) are not covered: the marker is read for the tools of the
process, not for gateway tools.

**Mechanism.** Both sources call `DocumentLoadGuard.check` (the guard of 3.14: type allowlist, size limit from the
metadata or a read of at most one byte more than the limit, the same caps) on the document the composer already
holds. The registry check of `c8__get_document` is not needed because the document comes from the input of the
turn; it is registered with the turn as every document is. A loaded document gets the same persisted marker (a
tag `TextContent` with `loaded: true`), which `DocumentExpander` expands at window time, so no new storage is
added, the request bytes stay stable and the history mapper shows the content (4.1, 5.2). A document that is
already loaded is not loaded twice: the documents loaded in the conversation before the turn (read through the
context window, `AgentConversation.loadedDocumentIds`), the documents loaded by a `c8__get_document` result of the
same turn and the documents of the same input are skipped. The new messages can push an old one out of the window
after the check; the document is then a reference and the model can load it.

**Guard fallback.** Automatic loading never fails the run. When the guard rejects a document it stays a reference
and the model gets a text: `Document <id> was not loaded automatically: it <problem>. <advice>`, where the problem is the one
of the guard (`is of type X which cannot be loaded into the conversation`, `is N KB, too large to load into the
conversation (limit L KB)`, `could not be read to check its size: ...`) and the advice is `Pass its id to a tool
that processes it.`, or `c8__get_document can try to load it again.` for a read failure, the only reason a retry can
overcome. In the user message the note follows the tag of the document; for a tool result it is in the follow-up
message after the tags (a message with notes only has no preamble). A note is part of the persisted conversation, so
the history shows it as text.

**Instances without the feature.** The option and the marker have no effect: such an instance keeps the 8.10
handling in which the documents are content (4.2).

**History auditability.** The follow-up message and the marked prompt tags are covered like the loads of
`c8__get_document` (4.1, 5.2); `AgentSubProcessHistoryAuditTests` covers an inline prompt document, an inline tool
document and a note.

### 3.16 Future compatibility: model-aware documents in tool call results

Today the content of a loaded document reaches the model in a follow-up user message (3.14, 3.15), the one form every
provider accepts. Some providers and models accept documents inside a tool result. A later iteration, driven by a
capability matrix that knows what a model accepts, can place the content there. The persisted form of this design is
chosen so that this needs no migration.

**Requirements.**

- A document the agent loaded for the model can be placed in the tool result that carries it, when the current model
  accepts documents there.
- A model switch between calls must work in both directions: the model after the switch gets a request it can accept,
  for the whole window.
- A model that cannot read the type of a loaded document (for example PDF) must not fail the call.
- Stored messages, the registry and the agent context stay as they are: no rewrite of persisted data, which an
  append-only conversation store could not do anyway.

**Persisted form.** The follow-up message with its tags marked as loaded (4.1) is a record of the fact that a document
was loaded, and of why. It does not say where the content is shown. The placement is derived when the request is
built, the same way the content of a loaded tag is added from the registry at window time. The record also keeps the
lifetime of the content tied to its message and the window, and it serves the loads of a user prompt, which have no
tool result.

**Window-time derivation.** For the model of the current call, in this order:

1. The capability matrix says whether the model reads the content type of the document. If not, the tag stays and a
   note says that the content is not shown to this model; the call does not fail. This also covers a provider that
   cannot read a type today.
2. If the model accepts documents in tool results and the tag has a carrier, the content is added to the tool result
   message of the carrier and removed from the follow-up message; a follow-up message left without content is
   dropped from the request. Without a carrier, or when the model does not accept documents there, the follow-up
   message stays as it is.
3. The provider converters send a document in a tool result natively or as a tag, as they do for the documents of
   tool results.

**Carrier.** The carrier of a loaded document is the tool call whose result carries the content, derived from data that
is persisted already: the call of `c8__get_document` in the window whose argument `id` is the id of the document;
otherwise the tool call in the registry source of the document, if its tool has `documentDelivery = inline`;
otherwise none (documents of the user prompt, or a carrier outside the window). The first rule wins, and a document
is loaded once, so the result is unambiguous. No field has to be added to the persisted tags.

**Model switches.** A switch to a model without support for documents in tool results needs no step: nothing folds and
every loaded document stays in a follow-up message. A switch to a model with support folds the content of the
earlier turns on the next call, because the window is built on every call. The request changes on a switch, which
invalidates a prompt cache that is per model anyway.

**Open for the iteration.** The capability matrix needs two keys, the support of a model for documents in tool results
and its supported content types; both belong to the capability matrix design. The providers whose wire format for
documents in tool results has no end-to-end test yet need one before the placement is enabled for them. The history
reports the persisted form, so it stays correct for either placement.

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
repeats it again in `agent.responseDocuments`. Inline documents are rare (Camunda documents are the norm), but
a large inline image counts fully against the 4 MB limit. There is no size guard or fallback to a
reference-only entry for the registry; the guard of `c8__get_document` (3.14) bounds only what a load adds
to the model request.

**Backward compatibility and downgrade.** No `CURRENT_SCHEMA_VERSION` bump. A missing `documents`
field binds to an empty list (builder default for collections, same as `toolDefinitions`), also
through the raw-tree path of `AgentContextSchemaMigration`. Agent instances created by Camunda 8.10, which
has no registry and stores documents as content, keep working without a rewrite of their messages: they
have no `features` and keep their document handling (section 4.2). On downgrade, an older connector runtime
ignores `features`, `documents`, `returnDocuments` and `pendingLocalToolCallResults` (`ignoreUnknown`) and
drops them on its next write. It also reads a document that was stored as a tag only (section 4.1) as the
tag text without the document content. An instance that an upgraded runtime reads again after that has
neither features nor registry, so it is handled as an instance without the feature and its documents of the
tag-only messages stay tag text.
Mixed batches work from 8.11 on; all changes land on 8.11 only, so there is no compatibility with older
engines or connector runtimes beyond what section 4.2 describes. The agent context field `features` is not
read by an older runtime either.

**Migration path to the stores.** Implement `DocumentRegistryStorage` against `ConversationSession`
(store the registry next to the messages, keyed by the conversation pointer), then switch the bean.
The handler and the composer do not change; `AgentContext.documents` stays readable so persisted state from this layout still loads.

### 4.1 Persisted form and provider form

A document is persisted in a conversation as its tag only: the composer stores a `TextContent` with
the `<doc .../>` text and the metadata `{"kind": "document-reference", "documentId": "<id>"}` for
every user prompt document and event document (the event tags follow the text `Documents attached to
the event:`), and no `DocumentContent` next to it. A tool result document has no tag message: the
persisted `ToolCallResultMessage` keeps it unchanged and the providers render it as its tag. A document
the model loads with `c8__get_document`, or that the agent loads for the result of a tool with inline delivery, is
persisted in a follow-up user message (preamble `System message, not written by the user: the content of the documents loaded for the tool results above follows.`, metadata
`toolCallDocuments`) whose tag also carries `"loaded": true`; a prompt document that the agent loads is the marked
tag in the user message of the prompt (3.15).
The tag carries `size="<bytes>"` for a Camunda document whose reference metadata has a size. The
reference of the document lives in the registry (`AgentContext.documents`) only. The metadata
(`kind` equal to `document-reference`) is the only marker: `Content` is a sealed hierarchy, so a tag is a normal `TextContent`, and
text a user typed that happens to hold a `<doc/>` tag is never touched.

The providers see another form. `AgentConversation.window` passes the windowed messages through
`DocumentExpander`, which adds after each tag marked as loaded a `DocumentContent` with the document built from
the registry reference map, with the same document deserialization the persisted messages use (the
connectors object mapper with its document factory). The result is the pair every provider converter
already handled: tag text followed by the native block. The expansion

- is skipped for a tag that a `DocumentContent` of the same document already follows (idempotent);
- leaves the tag text alone when the registry has no entry for the id or the reference cannot be read,
  and never fails the request;
- depends on the messages and the registry only, so the request bytes are stable between requests
  (prompt caching);
- leaves the stored conversation alone: `allMessages()` and the stored messages keep the tag only. The
  eviction of the follow-up messages of loaded documents (`MessageWindowFilter`, metadata `toolCallDocuments`)
  works on the stored messages, before the expansion.

The agent instance history reports a user message with the expansion of every flagged tag (`AgentInstanceHistoryMapper`
with the registry of `HistoryDocuments`): the tag text and the document as a `DOCUMENT` part. The AWS AgentCore conversion keeps a `TextContent` with metadata in its
content blob (a text without metadata stays a conversational payload), so the marker survives that store.

The history shows the document next to every document tag of a user message, loaded or not, so it is a superset of
what the model receives; the model gets content only for the tags marked as loaded.

Marker semantics, in short: `kind: document-reference` plus `documentId` mean "this text is the tag of a registry
document" and make the text expandable for the history; `loaded: true` additionally means "the model reads the
content of this document here" and makes the text expandable for the model. Only two things write
`loaded`: `c8__get_document`, through the follow-up message of the turn that loaded the document, and the inline
delivery of section 3.15.

Reason: the reference of a document (for an inline document its complete content) is held once, by the registry. A
tag costs a few dozen tokens, so a conversation can carry any number of documents, and the content is paid for
only by documents the model asked for, in the form the provider needs (text, file, image), without a second copy
in the stored conversation. The tool result keeps the complete reference of its document because the provider
converters and the tool result history read it; the providers render it as the tag (3.4).

### 4.2 Agent instance features and instances created before the documents feature

`AgentContext.features` (a `LinkedHashSet<String>`, `@JsonInclude(NON_EMPTY)`, a missing field binds to an
empty set, no schema version bump) lists the features an agent instance was created with. The set is
assigned once: `AgentInitializerImpl.createAgentContext` creates the context of a new agent instance, in both
flavors, with `AgentContext.newInstanceFeatures()`, today `localTools` (`AgentContext.FEATURE_LOCAL_TOOLS`) and
`documents` (`AgentContext.FEATURE_DOCUMENTS`). The `localTools` feature turns on the local tool facility (section
9) independently of documents: the registry offers local tools and tool elements of the process are checked
against their names. The `documents` feature builds on it and implies it: `AgentContext.supportsLocalTools()` is
true for an instance with either feature.
A context that exists without `features` belongs to an instance that was created before the feature and never
gets features added later, whatever it does next: a half-migrated conversation (messages in the form of the
feature after messages in the older form) cannot occur. `AgentContext.supports(feature)` and
`supportsDocuments()` and `supportsLocalTools()` read the set; `BaseAgentRequestHandler` copies it to `AgentConfiguration.features()` so
the registry of local tools sees it. The features are not part of the configuration fingerprint. The set
is a `LinkedHashSet` because the record builder copies a `Set` into an immutable set of unspecified order.

An instance without the `documents` feature (every instance created by Camunda 8.10 that is still running)
behaves as Camunda 8.10 does for documents. Nothing of section 2 to 9 applies to it except what is listed
here as shared:

- **Composition.** `AgentConversationTurnInputComposerImpl` branches on `agentContext.supportsDocuments()`.
  A user prompt document is a bare `DocumentContent`, without a tag. The documents of tool results and events
  are a synthetic user message with the preamble, then per document the tag in the older attribute format
  (`LegacyDocumentReferenceXmlTag`: `toolName`, `toolCallId`, `documentId`, `storeId`, `contentType`,
  `fileName`, or `url` and `name` for an external document) and the `DocumentContent`. The messages carry no
  `document-reference` metadata and nothing is ingested into a registry (`NextTurn.documents` is empty).
- **No registry, no selection.** `AgentContext.documents`, `returnDocuments` and `pendingLocalToolCallResults`
  stay empty and are never written; no registry is backfilled from the stored messages and no tag of a stored
  message is normalized. The window (`AgentConversation.window`) expands nothing and adds no
  `EvictedDocumentsNote`.
- **Tool results.** The window marks the `Document`s of tool results (`LegacyDocumentRendering`,
  `ReferenceRenderedDocument`) so that every provider path (`ModelFacingJson`) renders them as the Camunda
  document reference JSON, as the object mapper serializes them, not as a `<doc id/>` tag. The stored messages
  are not changed.
- **No local tool.** (`localTools` feature absent) `LocalToolRegistryImpl` offers none, `LocalToolCallHandlerImpl.handle` hands every call to
  the process and the offered tool list (and the `CONFIGURATION` history item) holds the process tools only.
  There is no mixed-batch handling, no synthetic round and no pending local results.
- **No document parameters.** `AgentResponseHandlerImpl` does not resolve document ids and an unknown id is
  never rejected: the arguments of a tool call reach the process untouched, so a `fromAi(..., "document")`
  parameter of such an instance is a plain string for the process. The schema of a tool the process declares
  with that type after a process migration is still the string schema with the document format and the hint
  that asks the model for a document id; the model sees no ids in that instance, so the parameter is not
  usable. This is a known limitation: the document parameters need the feature, and an instance that wants them
  starts anew.
- **No name check.** No tool element is checked against the names of local tools for such an instance (section
  3.9): an element named like a local tool must not stop an instance that is running.
- **History.** `HistoryDocuments.withoutDocumentFlow` makes `AgentInstanceHistoryMapper` report the messages as
  stored: no document expansion, no inline code backticks around tags, no element-id-less local tool entries. `AgentResponse` has no `responseDocuments`.
- **Shared.** The AWS AgentCore conversion keeps a `TextContent` with metadata as a blob (new instances need
  it), which does not change messages without metadata. `ToolDefinition` and the provider rewrite of the
  document format are applied to every instance; a persisted tool definition without the format is not changed.

The behavior is verified against Camunda 8.10 by a golden conversation:
`LegacyDocumentConversationTest` loads `backwardcompatibility/camunda-8.10/agentContext-documents-inprocess.json`
(a prompt document as bare content, a tool result with a nested Camunda and a nested inline document, tag-and-content
pairs of a tool call, an inline and an event document, an event message without an id), composes a turn with a
new prompt document, a new event document and a new tool result document, and asserts the composed messages, that the
stored messages stay as they are, the rendered tool result JSON, that no registry, feature, selection or local tool
appears and that every call goes to the process. `AgentTaskLegacyInstanceDocumentsTests` runs an instance without
features against the WireMock model and asserts the wire format of Camunda 8.10 (bare document content, the
document reference in the tool result, the older tag, no built-in tool).

## 5. Known limitations

Severity reflects what a user of the feature would hit, not the effort to fix.

| # | Finding | Severity | Detail |
|---|---------|----------|--------|
| 1 | Large inline documents | High (rare trigger) | Content is stored in the registry and in the tool result, and again in `agent.responseDocuments` when returned. A large inline image can push the job completion past the 4 MB broker message limit; there is no size guard or fallback to a reference-only entry. A load through `c8__get_document` is bounded by its size guard (3.14), the registry and the tool result are not. |
| 2 | Local answers of a mixed batch reach the model late | Low | The agent instance history shows the local results of a batch that also holds real calls as soon as the job that answered them completes (5.1). The model only receives them with the next request, in the next job, when the real calls have completed; a real call that never completes holds that request back. A batch made of local calls alone is answered in the same job. |
| 3 | Gateway tools unsupported | Medium | MCP and A2A tool definitions never declare document parameters; a document id passed to them is not resolved. Documents returned by gateway tools are ingested and passable to ad-hoc tools, but not to gateway tools. |
| 4 | No tool error flag | Low | The domain model has no error flag on tool results, so invalid-id errors are plain text the model must interpret; a weak model may not retry. |
| 5 | Reused tool call ids | Low | A model that reuses a tool call id across rounds reuses the agent instance history item id of the earlier result. Ids are assigned by the providers and unique in practice. |
| 6 | History reporting of local rounds | Low (handled) | See 5.1: local calls and rounds are reported through the existing history items; what is not reported there is the document selection itself and the documents' content (the reference of an inline document holds its content). |
| 7 | Document parameters of instances without the feature | Low | A running instance created before the `documents` feature passes document parameters as plain strings (section 4.2): ids are not resolved or rejected, and the hint in a tool schema that is rebuilt on a process migration asks for ids the model never sees. The instance has to be started anew to use documents. |
| 8 | Order of tool schemas in the fingerprint | Low | `ToolDefinition.builder()` copies the input schema into an immutable map (`Map.copyOf`) whose iteration order depends on a per-JVM salt and the insertion order, so every Jackson round trip of the persisted tool definitions can reorder the top-level schema entries, on another worker or within the same JVM. `AgentConfiguration.fingerprint()` therefore hashes a canonical form of the tool definitions instead of their `toString`: tools in list order, each `name`, `description` and `inputSchema` with map keys sorted recursively (also maps inside lists), list order kept, numbers compared by value (integral values of any numeric type agree), `null` explicit. The fingerprint thus stays equal across round trips and JVMs, and a job reports a `CONFIGURATION` history item only when model, provider, system prompt, model-call limit or the tools really change. Nothing is sorted on the definitions themselves: the order sent to the model and persisted is as authored. A fingerprint persisted before this canonical form does not match it, so an in-flight agent instance, with or without the `documents` feature, reports one extra `CONFIGURATION` item at upgrade (acceptable, the feature is 8.11-only). |
| 9 | A loadable type the provider cannot render | Medium | The allowlist of `c8__get_document` is provider-independent (3.14). A provider without support for a listed type, a PDF for example, fails the model call after the load, and every later call while the loaded content is in the window. |
| 10 | Size in bytes, not tokens | Medium | The size limit of a load is in bytes. A PDF of 214 KB can cost about 230,000 input tokens, more than a small context window holds. |

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
the "BPMN element ID of the tool" and is nullable. Only available tools are listed, so the list of an agent instance without the `documents` feature holds the
process tools only. The fingerprint of the configuration covers the offered tools.

**Calls and results.**

| Case | Calls (in order) | Local result items |
|------|------------------|--------------------|
| Batch with no remaining call (synthetic round) | `applyTurnStart` (turn input), `applyTurnCompletion` with `THINKING` (assistant item with all calls), `applyTurnStart` of the synthetic turn (input = the local results), the next `applyTurnCompletion` with the terminal status | `TOOL_RESULT` items in the synthetic `applyTurnStart`, `loopIteration` = assistant turn + 1 |
| Mixed batch | `applyTurnStart`, `applyTurnCompletion` with `TOOL_CALLING` (assistant item with all calls, local and real), then `applyToolCallResults` with the local results (no status). The next job: `applyTurnStart` with the merged results | `TOOL_RESULT` items of the local results in the `applyToolCallResults` of the job that answered them; the next job's `applyTurnStart` reports them again under the same `historyItemId` and the engine keeps one row. They stay in call order with the process results |
| `c8__get_document` load (a local call, alone or in a mixed batch) | as in the two rows above | `TOOL_RESULT` item with the text `Loaded the document <doc .../>. Its content follows in the next message.`; the `USER` item of the follow-up message comes with the `applyTurnStart` that carries the results |
| Mixed batch, partial arrival (`Deferred`) | `applyToolCallResults` with the arrived process results only | none; the local results were reported by the job that answered them |

In a mixed batch `BaseAgentRequestHandler.driveContinuationLoop` calls `applyToolCallResults` with the local
results and the just completed assistant turn right after `applyTurnCompletion`. That call carries no status,
so the one status per round of the engine stays with the completion write. The write fails the job like every
other history write, so a failed report is retried with the job. Each local `TOOL_RESULT` item correlates to its call like any result: `toolCallId` and `toolName` of the
call, the originating arguments (looked up in the previous turn), `historyItemId` = tool call id, local
`completedAt` as `producedAt`. The `elementId` of the call entry is `null` because a local call has no BPMN
element, for the call in the `ASSISTANT` item and for the result (`AgentInstanceHistoryMapper` recognizes the
call by `LocalToolRegistry.toolNames()` for an instance with the `localTools` feature, which is safe because no
tool element of such an instance has one of those names, 3.9). The result
text is the content of the `TOOL_RESULT` item, an error result included.

**Loaded documents.** The follow-up message of a load (4.1) is a `USER` item: the preamble text, the tag of each
loaded document in inline code and the document as a `DOCUMENT` part (an `OBJECT` part for an external reference),
so the history shows what the model reads. The call of `c8__get_document` is an entry of the `ASSISTANT` item without
element id, with the id of the document in its `arguments`, as the model sent it (below). A rejected
load is a `TOOL_RESULT` item with the error text and no follow-up item.

**Document tags in the text.** The text parts of the history items (user, assistant and tool result
text, the document tags and their preambles included) show every `<doc .../>` tag in inline code backticks
(`` `<doc id="..." />` ``), so a markdown renderer shows the tag instead of treating it as markup; a tag that
is already inside backticks is left as it is. `AgentInstanceHistoryMapper` does this when it maps a
`TextContent`; the persisted messages and the text the model reads are not changed.

**Document arguments.** The `arguments` of a call, in the `ASSISTANT` item and in the `TOOL_RESULT` item, are shown as
the model sent them: a document parameter holds the id the model passed (or the `<doc />` tag, when the model
passed the tag), for a tool of the process and for a local tool such as `c8__return_document`. The persisted
messages, the process variable and the text the model reads keep the same values. The mapper needs the
registry for the content of the messages only, so `AgentInstanceClient.applyTurnStart`, `applyTurnCompletion` and
`applyToolCallResults` take a `HistoryDocuments(documents)`; the request handler builds it from the registry of
the conversation (of the agent context when the composer defers). The early write of a local result and the
later write of the merged turn start, like the assistant item, show identical arguments, so it does not matter
which write the engine's deduplication keeps.
The `ASSISTANT` item lists every call of the message; the per-turn metrics count them all in `toolCalls`
(a measure of what the model requested, consistent with the listed calls and the status choice
`IDLE`/`TOOL_CALLING`). Unit tests: `AgentSubProcessRequestHandlerTest` and `AgentTaskRequestHandlerTest`
(call order, status and arguments), `CamundaAgentInstanceClientTest` (merged local and process results
as items, local tools in the `CONFIGURATION` item), `AgentInstanceToolMapperTest` and
`AgentInstanceHistoryMapperTest` (no element id for local tools, the arguments of the assistant and the result items as the model sent them), `AgentConversationTurnInputComposerImplTest`
(what `Deferred` carries). End to end: `AgentSubProcessDocumentFlowTests` reads the agent instance back
through the query API (`agentInstanceHistoryReportsTheLocalToolAndItsCalls`).

What cannot be represented:

- The history API has no tool kind and no result status. A local tool is recognizable only by the missing
  element id on its tool entry and on its call entries; a local result and a real one are otherwise alike. An
  error result of a local tool (invalid document id, `LocalToolException`, invalid arguments) is plain
  text content, the same way the model reads it.
- The documents the agent returns are not reported; the selection of `c8__return_document` appears only as the
  text of its result and as the ids in its call arguments.
- The agent instance history is written repeatedly for the same result: a local result by the job that
  answers it and again by the next job, a process result that arrived early by every incomplete job and
  again by the batch-complete `applyTurnStart`. This is intended: the engine deduplicates history items by
  `historyItemId`, within one job activation and across jobs (camunda/camunda#58792), and keeps the first
  write, so the history holds each result once. What the first write carries is therefore final: the item
  ids, `loopIteration` (assistant turn + 1), `producedAt` (the result's `completedAt`, persisted with the
  pending local results) and the call correlation are identical in both writes.

### 5.2 History auditability

The agent instance history is meant to be an auditable record of what the model receives: the formats
may differ from the request, but no message the model receives may be missing from it.

**What the history carries.** Each message of a model request is covered by a history item:

| Model request | History |
|---------------|---------|
| System prompt | `systemPrompt` of the `CONFIGURATION` item |
| User message (prompt, follow-up, the message of loaded documents, event) | `USER` item |
| Assistant message with its tool calls | `ASSISTANT` item, one call entry per call |
| Tool result | `TOOL_RESULT` item per call, correlated by `toolCallId` |

An agent instance without the `documents` feature reports its messages as stored (section 4.2); the audit
below concerns the instances with the feature.

**Format differences that are fine.** The content is the same, the representation is not:

- A loaded document is a `DOCUMENT` block (a Camunda document) or an `OBJECT` block (an external document
  reference) next to its tag in the history and a tag plus the document content (text, file or image part) in
  the request. The history also shows that block next to the tag of a document the model has not loaded
  (prompt and event documents), so it holds at least what the model receives.
- A tool result with a document is an `OBJECT` block with the complete reference in the history and the
  JSON with the `<doc/>` tag in place of the reference in the request.
- A `<doc/>` tag in a text block is wrapped in inline code backticks in the history.

**Guard test.** `AgentSubProcessHistoryAuditTests` records every model request of a document flow (prompt
document, tool result document, a load with `c8__get_document`, document arguments, `c8__return_document` next to a real call) and requires
each message to be covered by an item of the history as the engine returns it: system prompt by the
`CONFIGURATION` item, the other roles by the blocks of the item (text equal without backticks, object
blocks equal as JSON with a reference matched by the `documentId` of the tag, document blocks matched by a
part that is not a tag), tool calls by id, name and arguments (an argument equals the request or is the
reference of the document it names). The only message without a history item is listed in the test
(`KNOWN_UNREPORTED`): the evicted-documents note. A model-visible message that is neither covered nor
listed fails the test.

**Known gaps.**

1. **The evicted-documents note is not reported.** The note (3.5) is built per request in
   `AgentConversation.window`, sent to the model and never persisted, so no history item holds it; the model can
   receive a message the history does not show. It is the one entry of `KNOWN_UNREPORTED` (section 7).
2. **Tool input schemas are not in the history.** A tool entry of the history holds name, description and
   element id only, so the parameter schemas, the document id hint, the `camunda-document-id` format marker and
   the argument shape of the `c8__` tools are invisible (section 7).
3. **Inline documents.** `AgentInstanceHistoryMapper` cannot map an inline reference to a history block and
   throws an `IllegalArgumentException`; the reference of an inline document also holds its content
   (3.12, section 7).

## 6. Gaps compared with camunda/experience-pdp#81

- Documents mentioned only in the content of an evicted assistant message are not tracked; only
  registry documents are listed in the eviction note.
- The eviction note lists tags only (id, file name, content type, source or tool call) and does not describe
  the content of a document.
- `<doc/>` ids the model writes in the final text instead of calling `c8__return_document` are not
  interpreted.
- No retract for `c8__return_document`: a key can be replaced but not removed until the final response.
- External documents have no `contentType` in any tag, because it would require a download.
- No document TTL or expiry handling (section 7).
- No Modeler support for the fromAi type `document` (section 7).
- Gateway tools (MCP, A2A) cannot receive documents (section 7).
- Returned references are registry snapshots, including inline documents with their content.
- The registry lives in `agentContext` for all stores; it does not use the store-specific
  persistence.

## 7. Open points and follow-ups

**Conversation and window**

- **Evicted documents.** The evicted-documents note (3.5) is not reported to the agent instance history (5.2);
  report it as a `USER` item with a content-hash `historyItemId` and remove it from `KNOWN_UNREPORTED`. Until the
  note is a first-class message, documents of evicted messages are only listed by their tags and may be lost
  for the model for now, for example when it needs their content again and the load limits or its context
  window get in the way.
- **Sticky loaded content.** Loaded content is part of every model request until the message that holds the
  marker leaves the window, so a large load costs input tokens on every later call. The registry, and with it
  `agentContext`, only grows with the documents of the conversation (entry sizes in section 4), also for
  documents that are never loaded. An explicit unload, summarization or an expiry of loaded content would
  need its own design.
- **Compaction (#36).** Compaction that rewrites or drops messages must keep the registry intact and decide
  what happens to the markers of loaded documents and to the follow-up messages that hold them.
- **Partial and range reads.** `c8__get_document` loads a whole document. A range (pages, lines, bytes) is an
  additive field of the marker next to `loaded` and an optional argument of the tool; the expansion would
  then build a slice.

**Loading guards**

- **Token-based guard.** Bytes are a poor proxy for tokens (a PDF of 214 KB can cost about 230,000 input
  tokens). A guard on the estimated tokens, or on the remaining context of the model, would replace or
  complement the size limits.
- **Capability matrix (#7230).** The type allowlist (3.14) is provider-independent. A provider-specific matrix
  of the types each provider and model can take natively would reject a type before the load instead of
  failing the model call; a provider without PDF support fails at the model call today. The registry and
  tags are independent of that.
- **Unknown content type** is treated as text with the stricter limit (3.14); a content type sniffed from the
  bytes is not attempted.

**Inline delivery (3.15)**

- **Scope of the opt-ins.** Event documents and the documents of gateway tools (MCP, A2A) stay references: the
  option covers the user prompt and the marker covers the tools of the process. A mode per document or per
  document type, or an option for events, would be additive.
- **Modeler support.** The marker of a tool activity is a plain extension property
  (`io.camunda.agenticai.documents.delivery` = `inline`) that is set by hand in the properties panel; the connector
  element templates used as tools do not offer it. A template property that writes it would be the way to make it
  discoverable.
- **Cost.** An opted-in tool or prompt puts every loadable document into the conversation, and the content stays in
  every request until its message leaves the window (sticky loaded content above). The guard bounds one document,
  not the number of documents of a turn; a budget per turn, or a token-based guard, would complement it.
- **Already-loaded check.** The check reads the window of the previous messages; a document whose load leaves the
  window with the new input is a reference, and the model loads it on request.
- **Standalone resolver output.** The Ad-Hoc Tools Schema Resolver output carries `documentDelivery` for a marked
  tool, which another LLM connector ignores.

**Providers**

- **Bedrock loaded documents.** The wire format of a loaded document in the Bedrock Converse request (the
  `document` block next to the tag text) is covered by the converter unit tests only: the Bedrock
  event-stream response bodies cannot carry the id placeholders the WireMock model uses, so no end-to-end test
  scripts a load against Bedrock.
- **Native tool-result representation (#7232).** Providers currently carry only the tag text in the
  tool result entry and the bytes in the following user message; a native representation would change
  where the tag and the content live.

**Documents in tools and results**

- **Inline documents.** The registry entry keeps the complete reference, so an inline document holds its content
  (base64, 4 MB limit of the job completion) in `agentContext`, in the tool result and in
  `responseDocuments`. `AgentInstanceHistoryMapper` cannot map an inline reference and throws an
  `IllegalArgumentException`. Decide on a reference-only entry for inline documents and on how the history
  shows them (3.12, 5.1).
- **Gateway tools (MCP, A2A).** Their definitions never declare document parameters; the gateway handler seam
  (`transformToolCalls`) is unchanged and runs after the document resolution, which leaves their calls
  untouched. Gateway tools with document-typed definitions, and passing documents into them, need their own
  design because the gateway wraps the call arguments in an envelope the handler builds.
- **Resolver output for standalone use.** The Ad-Hoc Tools Schema Resolver returns the authored schema with the
  format `camunda-document-id` and the hint in the description (3.2), so its output carries the document id
  format. The AI Agent derives the provider schema (no format) at the snapshot, but another LLM connector that
  uses the resolver output directly would send the format; it needs the same rewrite
  (`DocumentParameterSchema.toProviderToolDefinitions`) or a documented contract.
- **Tool result documents stay in the tool result.** The `ToolCallResultMessage` keeps the complete reference of a
  tool result document next to the registry entry. Storing only the tag there as well needs the provider
  converters and the history mapper to read tool result documents from the registry.
- **Documents in legacy instances** are plain strings for the process: a `fromAi(..., "document")` parameter of an
  instance without the `documents` feature is not resolved or rejected (4.2). Such an instance has to be
  started anew to use documents.
- **Document tag.** The model-visible tag has no file name when it equals the id (a document stored without a
  name of its own); the model then only has the id and the content type to tell documents apart.
- **Document TTL (Risk 1 of the epic).** Decide whether the agent should notice expired documents (drop
  registry entries, tell the model) or leave expiry to the tool that dereferences the document.
- **Modeler and linting support** for the fromAi type `document`: the shorthand type is accepted by the
  runtime but not known to the Modeler tooling.

**Agent instance history**

- **History API gaps.** There is no tool kind and no result status (a local tool is recognizable only by the
  missing element id; an error result is plain text), and the tool input schemas are not reported (the
  document id format and hint, the arguments of the `c8__` tools are invisible). Ask: an engine API request for
  both (5.1, 5.2).
- **Documents the agent returns** are not reported; the selection appears only as the text of the result of
  `c8__return_document` and as the ids in its call arguments.

**Local tools**

- **`c8__return_document` and `c8__get_document` are always injected for new instances (open question).** Both
  tools are offered to every agent instance with the `documents` feature without an opt-out, to keep the
  configuration surface small; instances created before the feature are not offered them. Open: whether they
  should become configurable, for example through a response option or a per-agent allow/deny list of local
  tools. Effects of an injected tool to weigh:
  - Every model call carries the extra tool definitions, which costs input tokens.
  - It changes the behavior and the tool selection of agents that never meant to read or return documents: the
    model may call a tool spuriously or prefer it over the tools of the process.
  - The tool list is part of the configuration fingerprint, so adding or changing a local tool reports a new
    `CONFIGURATION` history item for running instances.
  - It occupies the names of the tools. An ad-hoc or gateway tool element with the name of a local tool makes
    tool discovery, and process migration, fail with a `ConnectorException` carrying
    `AgentErrorCodes.ERROR_CODE_LOCAL_TOOL_NAME_CONFLICT` (3.9); the element has to be renamed.
  - Future local tools (for example `c8__load_skill`, `c8__save_memory`) multiply these effects, so a general
    mechanism to enable and disable local tools is needed.

## 8. Test coverage map

### Unit tests (`connector-agentic-ai/src/test/java/io/camunda/connector/agenticai/`)

| Area | Tests |
|------|-------|
| Document parameters in the tool definition | `adhoctoolsschema/schema/AdHocToolSchemaGeneratorTest` (maps the type `document` to the string schema with the format), `AdHocToolsSchemaResolverTest`, `AdHocToolsSchemaFunctionIntegrationTest`, `aiagent/model/tool/DocumentParameterSchemaTest` (paths, provider rewrite, hint, normalization), `AgentConversationTest` (the window rewrites, nothing persisted changes), the converter tests under `aiagent/chatmodel/provider/` (one string schema with hint test per provider path) |
| Registry, entry sizes, handles | `aiagent/model/document/DocumentRegistryTest`, `DocumentRegistryEntryTest` (entry JSON, including `serializedEntrySizes`), `DocumentHandleTest` (file name and content type of a document and of its reference) |
| Ingestion and tags | `aiagent/agent/AgentConversationTurnInputComposerImplTest` (flagged tags, sources and message ids), `ToolCallResultDocumentExtractorTest`, `ContentTreeDocumentWalkerTest`, `aiagent/model/message/DocumentReferenceXmlTagTest` (source attribute, document reference content, attribute parsing) |
| Persisted form and expansion | `aiagent/model/document/DocumentExpanderTest` (expansion, idempotence, dangling and unreadable references, only flagged tags), `aiagent/model/AgentConversationTest` (window expansion, stored messages untouched), `aiagent/agentinstance/AgentInstanceHistoryMapperTest` (including the backticks around tags), `AwsAgentCoreConversationMapperTest` (text with metadata) |
| Agent instance features | `aiagent/model/AgentContextTest` (default, omitted when empty, round trip in insertion order, copies), `aiagent/agent/AgentInitializerTest` (a new instance gets `localTools` and `documents`, an existing context never gets features) |
| Agent instances without the feature | `aiagent/agent/LegacyDocumentConversationTest` (golden conversation of Camunda 8.10, `backwardcompatibility/camunda-8.10`: load, compose prompt, event and tool result documents, window, no registry, features or local tool), `aiagent/model/message/LegacyDocumentReferenceXmlTagTest`, `aiagent/model/document/LegacyDocumentRenderingTest`, `aiagent/chatmodel/provider/ModelFacingJsonTest`, `AgentInstanceHistoryMapperTest`, `LocalToolRegistryImplTest`, `AgentToolsResolverTest` (no reserved name), `AgentSubProcessRequestHandlerTest` |
| Eviction note and loop primitives | `aiagent/model/document/EvictedDocumentsNoteTest`, `aiagent/model/AgentConversationTest` (including `withContextUpdate`) |
| Provider rendering | the converter tests under `aiagent/chatmodel/provider/` (langchain4j, Anthropic, OpenAI completions/responses, Bedrock, Gemini, Mistral) |
| Documents into tools | `aiagent/agent/DocumentParameterResolverTest` (exact id match, tags, escaped tags, ids and tags inside text, two tags, arrays, nested, ids for local tools), `LocalToolCallHandlerImplTest` (per-call split, order, document parameters of process tools and of local tools) |
| Inline delivery | `aiagent/localtool/DocumentLoadGuardTest` (the guard: allowlist, known size, bounded read, unreadable), `aiagent/agent/AgentConversationTurnInputComposerInlineDocumentsTest` (user prompt with the option on and off, fallback note, already loaded, instance without the feature; tool marker on and off, nested documents, notes only, loaded by `c8__get_document` in the same turn, already loaded, instance without the feature), `aiagent/model/tool/ToolDefinitionTest` (JSON round trip, omitted when absent), `aiagent/model/AgentConfigurationTest` (the fingerprint of a tool without the mode is pinned, the mode changes it), `aiagent/model/tool/DocumentParameterSchemaTest` (the mode is not part of the provider definition), `adhoctoolsschema/schema/AdHocToolsSchemaResolverTest` (marker parsing), `aiagent/AgentV2ElementTemplateTest` (the checkbox in the v2 templates, not in the v1 templates) |
| Loading content | `aiagent/agent/GetDocumentToolTest` (loaded marker, already loaded, size limits by metadata and by reading at most one byte more, type allowlist without reading, unknown id, unreadable document, mixed batch), `aiagent/localtool/DocumentLoadLimitsTest` (allowlist, limits, unknown type), `AgentConversationTurnInputComposerImplTest` (follow-up message with the loaded tags), `AgentConversationTest` (`loadedDocumentIds` over the window), `AgentSubProcessRequestHandlerTest`, `AgentInstanceHistoryMapperTest` (expansion of the loaded tag), `EvictedDocumentsNoteTest` (introducing message windowed) |
| Documents out | `aiagent/agent/ReturnDocumentToolTest`, `AgentResponseHandlerDocumentsTest`, `AgentToolsResolverTest` (name of a local tool), `AgentSubProcessRequestHandlerTest`, `AgentTaskRequestHandlerTest` |
| Local tool API | `aiagent/localtool/LocalToolBuilderTest`, `LocalToolRegistryImplTest`, the test-only `SaveNoteTool` (stateful, typed arguments, error results, availability) driven through `aiagent/agent/LocalToolCallHandlerImplTest` (including a mixed batch with a real call) |
| Agent instance history of local tools | `aiagent/agentinstance/AgentInstanceToolMapperTest`, `AgentInstanceHistoryMapperTest` (the arguments as the model sent them), `CamundaAgentInstanceClientTest` (tools of the `CONFIGURATION` item, call and result items without element id, identical arguments in every write of a call) |
| Mixed batches | `AgentSubProcessRequestHandlerTest` / `AgentTaskRequestHandlerTest` (one model call, only real calls activated, pending results, status, clearing), `AgentResponseHandlerDocumentsTest` (local calls not emitted), `AgentConversationTurnInputComposerImplTest` (merge in call order, partial arrival, interrupt, no gateway transform, foreign ids ignored), `AgentConversationTest` (`withPendingLocalToolCallResults`), `AgentContextTest` and `AgentContextSchemaMigrationTest` (persistence round trip, missing field), `CamundaAgentInstanceClientTest` (history items of merged results, the early item equals the one of the merged turn start) |

### End-to-end tests (`connectors-e2e-test/connectors-e2e-test-agentic-ai`, WireMock model)

Test classes: `aiagent/subprocess/AgentSubProcessDocumentFlowTests` (sub-process flavor, `@Import` of
the in-memory AWS AgentCore client) and `aiagent/task/AgentTaskDocumentFlowTests` (AI Agent Task
flavor), under `src/test/java/io/camunda/connector/e2e/agenticai/`, plus the user prompt document classes
`AgentSubProcessUserPromptDocumentsTests` and `AgentTaskUserPromptDocumentsTests`. Shared: `aiagent/DocumentFlowFixtures`
(scripted turns, upload endpoint stub, upload assertions), `aiagent/DocumentFlowAssertions` (global
request assertion, tool schema access), `aiagent/wiremock/DocumentIdPlaceholderTransformer`. The v2
templates are used. Processes
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
result tag, so the model-visible id and the id in `agent.responseDocuments` are compared, not assumed.

| Scenario | Test | Proves |
|----------|------|--------|
| (a) schema | `toolDefinitionMarksTheDocumentParameterWithTheDocumentFormatAndTheProviderGetsAStringWithAHint` | The first raw request has `type: string`, no `format` and the hint for the document parameter, and `required`; no tool schema of any request has the type `document` or the format `camunda-document-id` (parsed, in `assertNoDocumentInternalsInLlmRequests`); the persisted `agentContext.toolDefinitions` keep `format: camunda-document-id` and hold the hint in the description; the model gets the process tools and `c8__return_document`. |
| (b) round trip | `passesADownloadedDocumentToAToolAndReturnsIt` | Download (job 1), upload with the id (job 2, real connector, multipart `file` part equals the served bytes), `c8__return_document` (local round), final text; `agent.responseDocuments` holds the key `report` with the description and the Camunda reference as `document` (`camunda.document.type`, `storeId`, `documentId` = the id the model saw, `contentHash`); selection kept in `agentContext`. |
| (c) unknown id | `unknownDocumentIdIsAnsweredInTheLoopAndTheModelRetries` (both flavors) | A batch made of the bad call alone is answered in the loop: error tool result with the known documents, the tool endpoint is hit exactly once (by the retry), process completes without incident, four model calls. |
| (d) eviction | `documentOfAnEvictedMessageStaysPassable` | With `contextWindowSize=3` the third request holds neither the download nor its tool result, but ends with the eviction note (preamble + tag with tool origin); the model still passes the id and the tool gets the real bytes. |
| (e) stores | `passesADownloadedDocumentToAToolAndReturnsIt` parameterized | Same round trip with in-process, Camunda document and AWS AgentCore memory (the registry lives in `agentContext`, so it survives the job boundaries in every store). |
| (f) task flavor | `AgentTaskDocumentFlowTests` | Round trip and unknown id retry with the AI Agent Task (`agent.responseDocuments` on the `AgentResponse`). |
| (g) mixed batch | `mixedBatchOfReturnDocumentAndARealToolCostsNoExtraModelCall` (both flavors) | `c8__return_document` and `Ping` in one assistant message: three model calls in total (download, mixed, final), `Ping` ran, the model got both results in call order, `agent.responseDocuments` holds the document under its key, nothing pending afterwards. |
| (g2) mixed batch, early report | `localResultOfAMixedBatchIsInTheHistoryBeforeTheRealCallCompletesAndStaysUnique` (both flavors) | `c8__return_document` and `Slow_Tool` (a job the test holds) in one assistant message: while `Slow_Tool` runs, the committed history already holds the `c8__return_document` result; after the test completes `Slow_Tool` the history holds the results of `Download_A_File`, `c8__return_document` and `Slow_Tool` once each, in that order. |
| (i) history arguments | `agentInstanceHistoryShowsTheDocumentIdInTheToolCallArguments` (both flavors) | After the round trip, the history read through the query API shows the id the model sent in the arguments of `Upload_A_File` (document parameter) and of `c8__return_document` (the `document` argument), in the `ASSISTANT` and in the `TOOL_RESULT` items. |
| (h) mixed batch with an unknown id | `mixedBatchWithAnUnknownDocumentIdRunsTheValidCallAndAnswersTheInvalidOne` (both flavors) | Upload with an unknown id plus `Ping`: `Ping` runs, the next request holds the error result and the `Ping` result in call order, the corrected upload is the only upload, four model calls. |
| (k) instance without the feature | `AgentTaskLegacyInstanceDocumentsTests` (`aiagent/task`) | A Task flavor agent whose initial `agent.context` has no features: user prompt documents as bare content, the tool result document as the reference JSON followed by the older tag and the content (Camunda and external documents), the model gets the process tools only, and the returned context has no features, registry or selection. |
| (j) history audit | `AgentSubProcessHistoryAuditTests` (`aiagent/subprocess`) | Every message of every model request of a document flow, with a prompt document, a tool result document, a load with `c8__get_document`, document arguments and `c8__return_document` next to a real call, is covered by an item of the history read through the query API; with `contextWindowSize=3` the evicted-documents note is the only message without a history item and no history item holds it (5.2). |

| (l) load | `loadsAFetchedDocumentOnceAndPassesItsIdToATool` (both flavors, text and PDF) | A tool returns a document, the model loads it twice and passes its id to a tool: the first load puts the content next to the tag (text part or file part), the second is answered without loading again, the tool gets the real bytes. |
| (m) large document | `rejectsALargeDocumentAndStillPassesItsIdToATool` (both flavors) | A text document above 256 KB is rejected with the answer that tells to pass the id to a tool; no request body holds its content and the tool still receives the real bytes. |
| (n) unknown id | `answersGetDocumentOfAnUnknownIdWithTheKnownDocuments` (both flavors) | The framework answers with the error text of 3.7 listing the known documents. |
| (o) user prompt documents | `handlesDocumentType`, `loadsDocumentOfTypeOnRequest`, `rejectsLoadingADocumentOfATypeThatIsNotSupported` (both flavors) | A prompt document is a tag only, for every type. Loaded on request, a CSV, GIF, JPEG, JSON, PDF, PNG, text, WebP, XML or YAML document reaches the next request in the form its type has on the wire. A ZIP document is answered with the type error as the tool result: no incident, the conversation continues, no content follows. |
| (q) inline prompt documents | `loadsPromptDocumentsIntoThePromptWhenTheOptionIsOn`, `keepsPromptDocumentsAsReferencesWhenTheOptionIsOff`, `keepsAPromptDocumentThatCannotBeLoadedAsAReferenceWithANote` (`AgentSubProcessUserPromptDocumentsTests`, `AgentTaskUserPromptDocumentsTests`) | With the option on the first request holds the prompt text, the tag and the content block of the document, and no `c8__get_document` call is needed; with it off the document is a tag only; a ZIP document stays a tag followed by the note. |
| (r) inline tool documents | `loadsTheDocumentsOfAnInlineToolForTheModel`, `keepsADocumentOfAnInlineToolThatCannotBeLoadedAsAReferenceWithANote` (both flavors; processes `agentic-ai-ahsp-connectors-documents-inline.bpmn` and `agentic-ai-connectors-documents-inline.bpmn`, which mark `Download_A_File`) | The result of the marked tool is followed by the message with the tag and the content (text part or file part), the model passes the id to the upload without loading, the tool gets the real bytes; a ZIP and a text above 256 KB stay references with the note and no content in any request. |
| (s) inline history audit | `everyModelVisibleMessageOfInlineDocumentDeliveryIsInTheHistory` (`AgentSubProcessHistoryAuditTests`) | A prompt document loaded by the option, a tool document loaded by the marker and a note are all covered by items of the history. |
| (p) provider wire format | `ProviderWireFormatSmokeTests` (`documentLoadedByTheModel` and the tag-only prompt document, per provider fixture) | The request of every WireMock provider that can carry id placeholders holds the tag text and the content block of a loaded document; Bedrock is covered by the converter unit tests (section 7). |

Global assertion: `DocumentFlowAssertions.assertNoDocumentInternalsInLlmRequests` checks the raw body
of every recorded model request for `storeId`, `contentHash` and `camunda.document.type`. It runs in
every document flow scenario and in the existing tool result and user prompt document tests of both
flavors.

Not covered: the real-provider document tests (`DocumentToolCallResultsIT`), which need real model calls, and
gateway tools (MCP, A2A), because the feature does not apply to them.

## 9. Local tool API

A local tool is a tool the agent runs itself: the model sees its definition next to the tools of the
process, the agent answers the call and no BPMN element is activated. `c8__return_document` and
`c8__get_document` are the two built-in ones; loading a skill or saving a memory are the next. Authors register a tool, a definition plus a handler,
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
  `LocalToolContext` is narrow: configuration, agent properties, documents, loaded document ids, agent instance
  key, `ObjectMapper`; its state is read-only. It also carries the `DocumentFactory` of the connector, so a tool
  can create a new Camunda document.
- **A result object with an error flag** (LangChain4j `isError`, MCP `isError`, Anthropic `is_error`).
  `LocalToolResult` has content, the flag, and context updates. The flag stays inside the agent because the
  domain tool result has no error flag (limitation 4, section 5); the model reads the message as text.
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
    Map<String, Object> arguments)          // as the model sent them; document parameters hold the document ids
{ <A> A argumentsAs(Class<A> type, ObjectMapper objectMapper); }

public record LocalToolContext(AgentConfiguration configuration, Map<String, Object> properties,
    DocumentRegistry documents, Set<String> loadedDocumentIds, @Nullable AgentInstanceKey agentInstanceKey,
    ObjectMapper objectMapper, DocumentFactory documentFactory) {}

public record LocalToolResult(Object content, boolean error, List<UnaryOperator<AgentContext>> contextUpdates) {
  static LocalToolResult success(Object content);
  static LocalToolResult error(String message);
  LocalToolResult withContextUpdate(UnaryOperator<AgentContext> update);
}

public class LocalToolException extends RuntimeException {}   // an error the model can act on
public interface LocalToolRegistry { Set<String> toolNames(); List<LocalTool> availableTools(AgentConfiguration);
    Optional<LocalTool> findAvailable(String name, AgentConfiguration);
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
   checks them against the registry first. An invalid value answers the call with the error text of 3.7 and
   the tool does not run. The tool receives `arguments` with the id of every document, a tag the model passed
   replaced by its id. A local tool gets ids and looks a document up with `LocalToolContext.documents()`; a
   process tool gets the document references (3.7).
4. **Run.** The tool runs with a `LocalToolContext` of the working conversation. The typed handler binds
   `arguments` to its type (a document parameter is a `String`, the id), and an argument that does not fit
   the type answers the call with "Invalid arguments for tool '<name>': ...".
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

### 9.6 Availability and names

`isAvailable` is evaluated against the configuration of the invocation, both when the definitions are offered
and when calls are split, so they cannot diverge. The task flavor evaluates its input per iteration, so
availability can change between jobs; a call of a tool that is no longer available goes to the process.

The registry offers no tool to an agent instance without the `localTools` feature (4.2), whatever `isAvailable`
says. A tool that needs the document registry (`c8__get_document`, `c8__return_document`) is available only for
an instance with the `documents` feature, through its own `isAvailable`.

The name of every registered tool, available or not, is checked against the tool elements of the process (3.9).
Reasoning: availability depends on a configuration that can change while the agent runs, and a BPMN element
that is valid today must not turn into a collision on the next iteration; the check also has no configuration
at hand on process migration. Any other name is free: the prefix `c8__` is a convention for the built-in tools,
not a rule. Recommend a vendor-specific prefix for a custom tool, for example `acme_`, so it is unlikely to
equal the name of an element. A blank name and a duplicate name are rejected when the registry is created
(startup).

### 9.7 Add a local tool

1. Pick a name: `c8__` is for built-in tools, use a vendor-specific prefix for a custom tool, and make sure
   no tool element of a process that uses the tool has the same name (9.6). Write the definition: description,
   input schema, with a document parameter (`LocalToolSchema.document`) if the tool takes documents; the handler
   receives the document id and looks the document up in `LocalToolContext.documents()`.
2. Write the handler. Typed arguments are the usual case:

   ```java
   record Note(@Nullable String text) {}

   LocalTool saveNote =
       LocalTool.builder("c8__save_note")
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

3. Register it as a Spring bean of type `LocalTool` (`@Bean`); `LocalToolRegistryImpl` collects every bean. A
   tool that works with documents overrides `isAvailable` to require the `documents` feature.
4. Report failures the model can act on with `LocalToolResult.error` or `LocalToolException`; let bugs
   propagate.
5. Build a schema passed as a `Map` insertion-ordered (`LinkedHashMap`, not `Map.of`): the request sent to the
   model carries the schema in the order of the map, and `Map.of` iterates in a different order in every JVM.
   The configuration fingerprint does not depend on this order (it hashes the schema with sorted keys).
   `LocalToolSchema` does this and `LocalTool.builder` keeps the order of the map it is given; construct a
   definition by hand with the `ToolDefinition` constructor, not `ToolDefinition.builder()` (the record
   builder copies the schema into an immutable map of unspecified order).
6. Create a new Camunda document with `LocalToolContext.documentFactory()` and return it in the content of the
   result. Change agent state only through `withContextUpdate`, never in a field of the tool. The update receives the
   persisted `AgentContext`; use `AgentContext.properties()` for free-form state.
7. Test it through `LocalToolCallHandlerImpl` with a conversation, as `LocalToolCallHandlerImplTest` does
   with the test-only `SaveNoteTool`. Add an end-to-end test when the tool changes what crosses a job
   boundary.

### 9.8 Limits

- The schema helper is flat; build nested schemas as a `Map`.
- Document parameters of local tools are found the same way and have the same limits as in 3.3 (no `anyOf`/`oneOf`).
- A local tool cannot start another model call or activate a BPMN element.
