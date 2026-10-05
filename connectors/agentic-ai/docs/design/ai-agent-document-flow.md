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
referenced by id in tool calls, and leave through the response.

1. **Tool definition.** `AdHocToolSchemaGeneratorImpl.generateToolSchema` (:54) and
   `resolveDocumentParameters` (:59) share one pass over the `fromAi` parameters; `mapDocumentTypes`
   (:130) rewrites every `document` node into a string schema with a hint. `AdHocToolsSchemaResolverImpl.createToolDefinition`
   (:60, document parameters at :65) copies the JSON pointers of those nodes into
   `ToolDefinition.documentParameters` (`aiagent/model/tool/ToolDefinition.java`:31).
2. **Ingestion.** `AgentConversationTurnInputComposerImpl.compose` (returns `NextTurn(messages,
   ingestedDocuments)` at :147) collects an `IngestedDocument` (document, `toolCallId`, `toolName`)
   for every document that enters the turn: user prompt documents (`createUserPromptMessage`, :150),
   tool result documents including gateway `extractDocuments` and nested documents
   (`createDocumentMessageForToolResults`, :229, reuses `ToolCallResultDocumentExtractor`) and event
   documents (`createEventMessage`, :247). The list is ordered prompt and tool results first, events
   last, matching the message order.
3. **Registry.** `BaseAgentRequestHandler.converse` (:119) passes the new documents to
   `registerDocuments` (:170). It loads the registry through `DocumentRegistryStorage`, seeds it from
   the loaded messages when it is empty and a conversation exists, adds the new documents and returns
   the agent context carrying the result. `proceed` (:200) rehydrates the `AgentConversation` from
   that context, so every copy-on-write step and `AgentConversation.toAgentContext()` (:361) carry the
   registry, and it is persisted by the same job completion as the rest of `agentContext`
   (`AgentContext.documents`, `aiagent/model/AgentContext.java`:37). The `Deferred`, `NoInput` and
   no-op paths do not register anything because nothing enters the conversation there.
4. **Rendering.** The composer puts a `<doc .../>` tag (`aiagent/model/message/DocumentReferenceXmlTag.java`)
   before each `DocumentContent` (`createDocumentPairs` :293, `createUserPromptMessage` :150).
   `aiagent/chatmodel/provider/ModelFacingJson.java` renders a `Document` at any depth of tool result
   or message object content as its tag, for every provider path. `AgentConversation.window` (:344)
   appends the eviction note (`EvictedDocumentsNote.appendTo`, :346) to the windowed snapshot and, when
   `AgentConfiguration.returnsDocuments()`, the `return_documents` tool definition (:348).
5. **Model loop.** `BaseAgentRequestHandler.driveContinuationLoop` windows the conversation, calls the
   model, ingests the assistant message, and then either ends the loop or opens another round:
   `nextContinuationRound` (`AgentConversation`) for provider continuations, `nextLocalToolRound`
   (`AgentConversation`) when the agent answered every tool call of the message itself.
6. **Local tool handling.** `localToolCallHandler.handle` asks every `ToolCallInspector`
   (`DocumentParameterToolCallInspector`, `ReturnDocumentsToolCallInspector`; beans at
   `autoconfigure/AgenticAiConnectorsAutoConfiguration.java`) for a verdict per call and splits the
   batch into local results and the calls that remain for the process (section 3.6). The selection of
   `return_documents` is merged with `withReturnDocumentIds` in both outcomes. Without remaining calls the
   loop opens a synthetic round (`applyTurnStart`, `throwIfLimitsReached`); with remaining calls the local
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
| 2 | Document id is `DocumentHandle.idFor`. The model sees only `<doc id fileName contentType [toolName] [toolCallId] />`. | Never expose `storeId`, `contentHash`, other metadata or URLs (presigned URLs carry tokens). |
| 3 | Documents out are a built-in tool `return_documents`, handled in the model loop. | The tool must never become a BPMN element activation. |
| 4 | An unknown document id yields an error tool result and the model retries. | No incident; the tool does not run. The result is delivered with the next model call: in the same job when the whole batch was answered locally, in the next job together with the results of the real calls of the batch otherwise. |
| 5 | Tools receive the full Camunda document reference JSON. | Same shape other connectors emit and consume; no agent-internal handle leaves the agent. |

### 3.2 Document parameter representation

`ToolDefinition.documentParameters` is a `List<String>` of JSON pointers into `inputSchema`
(`/properties/doc`, `/properties/docs/items`, `/properties/req/properties/file`; `~` and `/` in
property names are escaped as `~0` and `~1`). It is `@JsonInclude(NON_EMPTY)` and normalized to an
empty list, so a tool without document parameters serializes and compares equal to a definition
persisted before the field existed. The field lives outside the schema, so no provider converter
(langchain4j, Anthropic, Bedrock, Gemini, OpenAI completions/responses) sees it; they only read
`name`, `description` and `inputSchema`. `AgentToolsResolverImpl.updateChangedToolDefinitions` (:116)
and `AgentInitializerImpl` need no change because they compare and store whole `ToolDefinition`
records. No configuration fingerprint covers tool definitions.

Reason: pointers keep the schema sent to the provider free of non-standard keywords, let the resolver
walk arguments without re-deriving the schema, and keep the persisted tool definitions backward
compatible.

The model sees a string. A top-level `document` parameter becomes `{"type": "string", "description":
"<desc> Pass the id of a <doc/> tag of a known document."}`. Example for
`fromAi(toolCall.docs, "The attachments", "array", {"items": {"type": "document"}})`:

```json
{"type": "object",
 "properties": {"docs": {"type": "array", "description": "The attachments",
   "items": {"type": "string",
             "description": "Pass the id of a <doc/> tag of a known document."}}},
 "required": ["docs"]}
```

with `documentParameters = ["/properties/docs/items"]`.

### 3.3 Nested support

Parameter type `document` is mapped at the top level, as array `items`, and recursively through object
`properties` and array `items` of a parameter schema. `DocumentParameterResolver` follows the same
pointers: `/properties/x` is `arguments.x`, `/items` every element of the array, nested pointers
recurse through objects and arrays. Reason: tools commonly take lists of files or an object with a
file field. `document` inside `anyOf`/`oneOf`/`$ref` is not mapped and reaches the provider
converters as an unknown type (a known limitation, not worth the schema-walking complexity now).

### 3.4 Tag format

`<doc id="..." fileName="..." contentType="..." toolName="..." toolCallId="..." />`, attributes in
that order, XML-escaped, blank or unknown attributes omitted (no placeholders). `fileName` is the
Camunda metadata file name, the external reference `name` or the inline `name`; for an external
document without a name it is the last path segment of the URL without query and fragment
(percent-decoded, only when not blank), never the URL. `contentType` comes from the Camunda metadata
or the inline document and is omitted for external documents because their metadata is only available
after a download. `toolName` and `toolCallId` are present for documents that came from a tool result.
User prompt and event documents carry a tag without tool attributes, in the same pair shape as
extracted tool result documents. Registry entries use the same `DocumentHandle` functions
(`idFor`, `fileNameFor`, `contentTypeFor`), so reading the registry never downloads an external
document just to fill `contentType`.

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
such document (`DocumentReferenceXmlTag.from(entry)`: id, file name, content type, tool origin). It is
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
`LocalToolCallSplit(localResults, remainingCalls, returnDocumentIds)`:

- **Local calls.** A call answered by a `ToolCallInspector` (`Answer`) gets its result immediately: the
  built-in `return_documents`, and any call whose document parameter is not a known id (an error result
  listing the known documents). The first `Answer` wins when several inspectors answer.
- **Remaining calls.** Every other call passes and goes to the process like any tool call.

The request handler then proceeds by what is left:

| Remaining calls | What happens | Model calls |
|-----------------|--------------|-------------|
| none | The local results become the input of a synthetic round of the same job (`nextLocalToolRound`); the model is called again. The round counts against `maxModelCalls` (`throwIfLimitsReached`). | one more in the job |
| some | The job completes with only the remaining calls activated (`ToolCallProcessVariable` list and element activations hold only them, status `TOOL_CALLING`). The local results are stored in `AgentContext.pendingLocalToolCallResults`. | none extra |

The selection of `return_documents` is recorded in `AgentContext.returnDocumentIds` immediately in both
cases.

**Pending local results.** `AgentContext.pendingLocalToolCallResults` is a `List<ToolCallResult>`,
`@JsonInclude(NON_EMPTY)`, a missing field binds to an empty list, no schema version bump. It rides in
`agentContext` like the registry, so it reaches the next job whatever the conversation store is (the stores
only handle the conversation pointer), and it is written by the same job completion that activates the
real calls. Content is whatever the inspector answered (text today); `completedAt` is the time of the local
answer.

**Merging in the next job.** `resolveOrderedToolCallResults` looks up each tool call id of the last
assistant message first in the pending local results, then in the arrived process results (gateway
transformed; local results are never transformed). The `ToolCallResultMessage` keeps the order of the calls.

- *Partial arrival.* The composer waits for the real calls only. Missing real calls make the result
  `Deferred`; its `arrivedResults` hold the process results only, so local results are not re-reported
  by incomplete jobs.
- *Event interrupt.* Only real calls without a result are cancelled; a locally answered call keeps its
  result.
- *Consumption.* `BaseAgentRequestHandler.converse` clears the pending field when it applies the composed
  turn (`NextTurn`), so the results are consumed once and the field cannot reach a later turn. Pending
  results whose id is not a tool call id of the last assistant message are ignored.
- *Both flavors.* The sub-process and the task flavor share `BaseAgentRequestHandler`, so both follow the
  rule: the sub-process activates the remaining calls as elements, the task flavor returns only them in
  `toolCalls`.

**Adding a local tool.** Implement `ToolCallInspector`, return `Answer(content, returnDocumentIds)` for
the tool's calls and `Pass` for every other call, and register the bean. The verdict concerns the single
call, so the tool works the same alone, with other local tools or with real calls in one batch. State a
local tool changes is carried by the verdict, as `returnDocumentIds` is today (`LocalToolCallSplit` carries
the aggregate); a new kind of state needs its own field there and in the handler.

Reason: the real calls are valid work the model asked for, and running them without an extra model call
keeps a mixed batch at one round. Per-call splitting needs only the results of the local calls to survive
until the process results are in, which `agentContext` already carries.

### 3.7 Error text

One sentence per invalid value, then the known documents (at most 50 are listed, the rest is
counted):

```
Unknown document id 'x' for parameter 'docs[1]'. Known documents:
<doc id="..." fileName="..." contentType="..." toolName="..." toolCallId="..." />
```

The parameter path is `name`, `parent.name` for nested objects and `name[i]` for array elements. A
blank or non-string value reads "Parameter 'p' must be the id of a known document (a non-blank
string).", an array parameter that is not an array "Parameter 'p' must be an array.". With an empty
registry the list is replaced by "No documents are known.". Results are plain text because no provider
has a tool error flag in the domain model, so the model reads the text. Reason: the list of known tags
lets the model correct itself in one retry without any further tool call. The error result reaches the model with its next request: in the synthetic round when the whole batch was local, in the next job together with the results of the real calls of the batch otherwise (section 3.6).

### 3.8 `return_documents` semantics

`ReturnDocumentsTool` (`aiagent/model/document/ReturnDocumentsTool.java`) has `NAME =
"return_documents"` and one required parameter `ids` (array of strings, the ids of `<doc/>` tags). It
declares `/properties/ids/items` as document parameter, so the inspector reuses
`DocumentParameterResolver` and its error texts. `ReturnDocumentsToolCallInspector` acts only while the
option is on. A valid call answers "Selected N document(s) to return: id1, id2" and carries the ids;
missing, empty or non-array `ids` and unknown ids give an error answer listing the known documents and
select nothing.

- **Accumulate.** Every valid call adds its ids to the selection (ordered union, duplicates dropped),
  also across jobs and turns of the same run. The selection lives in `AgentContext.returnDocumentIds`
  (`List<String>`, `@JsonInclude(NON_EMPTY)`, a missing field binds to an empty list, no schema version
  bump) because the tool can be called in an earlier job than the final response.
- **Consume on the final response.** A response without tool calls returns the selection as `documents`
  (registry entry `reference` maps, selection order, ids not in the registry skipped) and clears it in
  the returned context. A response with tool calls leaves it alone.
- **No retract.** There is no tool to remove an id. Chosen over "each call replaces the selection"
  because models call the tool more than once and a replacing call silently drops earlier picks. The
  cost is that an unwanted pick stays in the response until the run ends. A document is only ever
  returned if the model named it, and the answer text lists what that call selected.
- A selection recorded in a batch that also holds real calls (3.6) is persisted with that job, before the
  real calls ran, because the model receives the "Selected ..." result for it with the results of the batch.
- Option off: no tool in the snapshot, no `documents`; a stale selection (option toggled mid-run) is
  cleared with the final response without being returned.

A valid `return_documents` call together with real tool calls costs no extra model call: the real calls run
and the model receives all results in the call order (3.6). Only a batch made of local calls alone starts a
synthetic round, which counts against `maxModelCalls`.

### 3.9 Reserved name

`AgentToolsResolverImpl.loadAdHocToolsSchema` (:44) rejects an ad-hoc or gateway tool element named
`return_documents` with a `ConnectorException` (`AgentErrorCodes.ERROR_CODE_RESERVED_TOOL_NAME`, raised
at :60). `loadAdHocToolsSchema` runs on tool discovery and on process migration
(`updateToolDefinitions`), so both paths are covered. Reason: a BPMN element with that name would
collide with the built-in tool in the snapshot and silently lose to it.

### 3.10 Configuration option `includeDocuments`

`ResponseConfiguration.includeDocuments()` (`aiagent/model/request/ResponseConfiguration.java`:20),
implemented by `AgentTaskResponseConfiguration` and `AgentSubProcessResponseConfiguration` (template
property `data.response.includeDocuments`, label "Include documents", group `response`, optional
boolean, off by default). `AgentConfiguration.returnsDocuments()` (:69) is the single switch; the
inspector reads it through `LocalToolCallContext` (:35). `window` appends the tool definition to the
snapshot only; it is neither part of `AgentConfiguration.toolDefinitions()` nor of
`AgentContext.toolDefinitions`, so toggling the option is invisible to the migration check
(`updateChangedToolDefinitions`) and to the configuration fingerprint. Reason: existing agents see no
new tool, no new response field and no persisted-state change unless the modeler opts in.

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
the full reference with metadata. The cost is described in section 4 and listed as a blocker in
section 5.

### 3.13 Remaining design choices

- **Where the references are resolved.** Only the `ToolCallProcessVariable` (the element activation
  variable `toolCall` of the sub-process flavor and the task flavor's `toolCalls`) carries the
  references; the persisted assistant message keeps the ids the model sent, and the original arguments
  are never modified. A missing or `null` argument is left alone (optional parameters). Resolution
  runs on the raw tool calls before the gateway transformation, so gateway tool calls stay untouched.
- **Local handler abstraction.** `LocalToolCallHandler.handle(conversation, toolCalls)` returns a
  `LocalToolCallSplit(localResults, remainingCalls, returnDocumentIds)`; inspectors return `Pass` or
  `Answer(content, returnDocumentIds)`. Reason: the loop stays generic and a future local tool is one more
  inspector (3.6).
- **Registry shape.** `DocumentRegistry` is an immutable, insertion-ordered, id-keyed value
  (`findById`, `entries`, `withDocuments`, `of`, `fromMessages`); the first registration of an id wins
  and re-adding a known id returns the same instance. `DocumentRegistryEntry` holds `id`, `reference`
  (a `Map<String, Object>` with the JSON the standard `DocumentSerializer` emits), `fileName`,
  `contentType`, `toolName`, `toolCallId`. The reference is a plain map so reading the registry needs
  no `DocumentFactory`; it binds back to a `Document` through the regular document deserializer
  (covered by a test). The entry serializer uses a private `ObjectMapper` with the document serializer
  module and `NON_NULL` inclusion; dates are ISO strings.
- **Loop obligations of a synthetic round.** `throwIfLimitsReached` before the round (retries are bounded
  by `maxModelCalls` and fail with the existing max-model-calls error code); agent instance history
  reporting for the synthetic input; a persist and reload round trip through `TurnReconstructor`
  without `lastIterationKey` drift (see 5 and 5.1 for reporting details). A mixed batch opens no round and
  has none of these obligations beyond the history reporting of the next job.

## 4. Registry storage

**Where.** `AgentContext.documents` (`List<DocumentRegistryEntry>`, `@JsonInclude(NON_EMPTY)`),
accessed through `DocumentRegistryStorage` (`DocumentRegistry load(AgentContext)`, `AgentContext
store(AgentContext, DocumentRegistry)`). The only implementation, `AgentContextDocumentRegistryStorage`
(bean at `AgenticAiConnectorsAutoConfiguration`:302), reads and writes `AgentContext.documents()`.

**Store independence.** The registry rides in `agentContext`. The `ConversationSession`
implementations (in-process, Camunda document, AWS AgentCore) only read the context and return the
new `ConversationContext` pointer; `AgentConversation.withStoredConversation` copies the rest of the
context. The store formats are unchanged.

**Serialized size per entry** (`DocumentRegistryEntryTest#serializedEntrySizes`, tool origin
`Search_Tool` / `call_0123456789abcdef`):

| Entry | Bytes |
|-------|-------|
| Camunda document (metadata: contentType, fileName) | 372 |
| External document (URL of about 50 characters) | 226 |
| Inline document, 1 KB content | 1221 |

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
through the raw-tree path of `AgentContextSchemaMigration`. A context with a conversation and an empty
registry is seeded from the stored messages: `DocumentContent` of user messages (this includes the
synthetic tool call document messages, so gateway documents are covered) and the content trees of tool
call results (origin retained). The seed is best effort; a conversation that legitimately has no
documents is rescanned on every turn (the messages are loaded anyway). On downgrade, an older
connector runtime ignores `documents`, `returnDocumentIds` and `pendingLocalToolCallResults`
(`ignoreUnknown`) and drops them on its next write; the next upgraded run reseeds the registry from the messages, but the selection of
`return_documents` and documents known only from evicted or unscanned content are lost. A mixed batch in flight at the moment of the downgrade is not completed by the older runtime, which waits for results of the calls the agent answered itself.

**Migration path to the stores.** Implement `DocumentRegistryStorage` against `ConversationSession`
(store the registry next to the messages, keyed by the conversation pointer), then switch the bean.
The handler and the composer do not change; `AgentContext.documents` stays readable (empty for new
conversations) so persisted state from this layout still loads, and the seeding scan covers any
conversation without a stored registry.

## 5. Blockers found

Severity reflects what a user of the feature would hit, not the effort to fix.

| # | Finding | Severity | Detail |
|---|---------|----------|--------|
| 1 | Large inline documents | High (rare trigger) | Content is stored in the registry and in the tool result, and again in `agent.documents` when returned. A large inline image can push the job completion past the 4 MB broker message limit; there is no size guard or fallback to a reference-only entry. |
| 2 | Local answers of a mixed batch are visible late | Low | The local results of a batch that also holds real calls reach the model and the agent instance history only with the next job, when the real calls have completed; a real call that never completes holds them back. A batch made of local calls alone is answered in the same job. |
| 3 | Gateway tools unsupported | Medium | MCP and A2A tool definitions never carry `documentParameters`; a document id passed to them is not resolved. Documents returned by gateway tools are ingested and passable to ad-hoc tools, but not to gateway tools. |
| 4 | No tool error flag | Low | The domain model has no error flag on tool results, so invalid-id errors are plain text the model must interpret; a weak model may not retry. |
| 5 | Reused tool call ids | Low | A model that reuses a tool call id across rounds reuses the agent instance history item id of the earlier result. Ids are assigned by the providers and unique in practice. |
| 6 | History reporting of local rounds | Low (handled) | See 5.1: local calls and rounds are reported through the existing history items; what is not reported there is the document selection itself and the documents' content. |
| 7 | Seeded registry completeness | Low | The seed scan is best effort and rescans conversations without documents every turn. |
| 8 | File name from a URL path segment | Low | For an external document without a name the last URL path segment becomes the file name and can carry an owner-chosen identifier (for example a token). Only a reference `name` or Camunda metadata is fully controlled by the connector user. |

Additional facts behind these findings: validation looks only at ad-hoc tools whose definition has
`documentParameters` (the tool name is the lookup key); a failing resolution in `createResponse`
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

| Case | Calls (in order) | Local result items |
|------|------------------|--------------------|
| Batch with no remaining call (synthetic round) | `applyTurnStart` (turn input), `applyTurnCompletion` with `THINKING` (assistant item with all calls), `applyTurnStart` of the synthetic turn (input = the local results), the next `applyTurnCompletion` with the terminal status | `TOOL_RESULT` items in the synthetic `applyTurnStart`, `loopIteration` = assistant turn + 1 |
| Mixed batch | `applyTurnStart`, `applyTurnCompletion` with `TOOL_CALLING` (assistant item with all calls, local and real). The next job: `applyTurnStart` with the merged results | `TOOL_RESULT` items in the `applyTurnStart` of the next job, exactly once, in call order with the process results |
| Mixed batch, partial arrival (`Deferred`) | `applyToolCallResults` with the arrived process results only | none; reported with the complete batch |

Each local `TOOL_RESULT` item correlates to its call like any result: `toolCallId` and `toolName` of the
call, the originating arguments (looked up in the previous turn), `historyItemId` = tool call id, local
`completedAt` as `producedAt`. `elementId` equals the tool name because a local call has no BPMN element.
The `ASSISTANT` item lists every call of the message; the per-turn metrics count them all in `toolCalls`
(a measure of what the model requested, consistent with the listed calls and the status choice
`IDLE`/`TOOL_CALLING`). Unit tests: `AgentSubProcessRequestHandlerTest` and `AgentTaskRequestHandlerTest`
(call order, status and arguments), `CamundaAgentInstanceClientTest` (merged local and process results
as items), `AgentConversationTurnInputComposerImplTest` (what `Deferred` carries).

What cannot be represented:

- The history has no marker that a result came from the agent and not from a tool element; a consumer sees
  `elementId` = tool name with no matching element. A local result and a real one are otherwise alike.
- A local answer is not visible before the next job (mixed batch), see blocker 2.
- The documents the agent returns are not reported; the selection of `return_documents` appears only as the
  text of its result.
- The rows written by `Deferred` streaming duplicate with the batch-complete write until the history
  dedups by `historyItemId`; local results avoid that by not being streamed.

## 6. Gaps compared with camunda/experience-pdp#81

- Documents mentioned only in the content of an evicted assistant message are not tracked; only
  registry documents are listed in the eviction note.
- The eviction note lists tags only (id, file name, content type, tool origin) and does not describe
  the content of a document.
- `<doc/>` ids the model writes in the final text instead of calling `return_documents` are not
  interpreted.
- No retract for `return_documents`: a selection can only grow until the final response.
- External documents have no `contentType` in any tag, because it would require a download.
- No document TTL or expiry handling: the registry keeps references for the lifetime of the
  conversation, and a returned or passed reference can point to a document the store has already
  deleted.
- No Modeler support for the fromAi type `document` (no validation, no completion, no linting).
- Gateway tools (MCP, A2A) cannot receive documents.
- Returned references are registry snapshots, including inline documents with their content.
- The registry lives in `agentContext` for all stores; it does not use the store-specific
  persistence.

## 7. Open points

- **Gateway tools (MCP, A2A).** Their definitions never carry `documentParameters`; the gateway
  handler seam (`transformToolCalls`) is unchanged and runs after the document resolution, which leaves
  their calls untouched. Passing documents into gateway tools needs its own design because the gateway
  wraps the call arguments in an envelope the handler builds.
- **Modeler and linting support** for the fromAi type `document`: the parameter type is accepted by
  the runtime but not known to the Modeler tooling.
- **Document TTL (Risk 1 of the epic).** Decide whether the agent should notice expired documents
  (drop registry entries, tell the model) or leave expiry to the tool that dereferences the document.
- **Native tool-result representation (#7232).** Providers currently carry only the tag text in the
  tool result entry and the bytes in the following user message; a native representation would change
  where the tag and the content live.
- **Capability matrix (#7230).** Which providers can take which document types natively decides what
  the model receives next to the tag; the registry and tags are independent of that.

## 8. Test coverage map

### Unit tests (`connector-agentic-ai/src/test/java/io/camunda/connector/agenticai/`)

| Area | Tests |
|------|-------|
| Document parameters in the tool definition | `adhoctoolsschema/schema/AdHocToolSchemaGeneratorTest`, `AdHocToolsSchemaResolverTest`, `AdHocToolsSchemaFunctionIntegrationTest` |
| Registry, entry sizes, handles | `aiagent/model/document/DocumentRegistryTest`, `DocumentRegistryEntryTest` (including `serializedEntrySizes`), `DocumentHandleTest` |
| Ingestion and tags | `aiagent/agent/AgentConversationTurnInputComposerImplTest`, `ToolCallResultDocumentExtractorTest`, `ContentTreeDocumentWalkerTest`, `aiagent/model/message/DocumentReferenceXmlTagTest` |
| Eviction note and loop primitives | `aiagent/model/document/EvictedDocumentsNoteTest`, `aiagent/model/AgentConversationTest` |
| Provider rendering | the converter tests under `aiagent/chatmodel/provider/` (langchain4j, Anthropic, OpenAI completions/responses, Bedrock, Gemini, Mistral) |
| Documents into tools | `aiagent/agent/DocumentParameterResolverTest`, `DocumentParameterToolCallInspectorTest`, `LocalToolCallHandlerImplTest` (per-call split, order, first answer wins) |
| Documents out | `aiagent/agent/ReturnDocumentsToolCallInspectorTest`, `AgentResponseHandlerDocumentsTest`, `AgentToolsResolverTest` (reserved name), `AgentSubProcessRequestHandlerTest`, `AgentTaskRequestHandlerTest` |
| Mixed batches | `AgentSubProcessRequestHandlerTest` / `AgentTaskRequestHandlerTest` (one model call, only real calls activated, pending results, status, clearing), `AgentResponseHandlerDocumentsTest` (local calls not emitted), `AgentConversationTurnInputComposerImplTest` (merge in call order, partial arrival, interrupt, no gateway transform, foreign ids ignored), `AgentConversationTest` (`withPendingLocalToolCallResults`), `AgentContextTest` and `AgentContextSchemaMigrationTest` (persistence round trip, missing field), `CamundaAgentInstanceClientTest` (history items of merged results) |

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
response answers, whatever the JSON escaping depth. The test reads the same id from the recorded tool
result tag, so the model-visible id and the id in `agent.documents` are compared, not assumed.

| Scenario | Test | Proves |
|----------|------|--------|
| (a) schema | `toolDefinitionDeclaresTheDocumentParameterAsString` | The first raw request has `type: string` plus the hint for the document parameter, `required`, and no `documentParameters` anywhere; no `return_documents` while the option is off. |
| (b) round trip | `passesADownloadedDocumentToAToolAndReturnsIt` | Download (job 1), upload with the id (job 2, real connector, multipart `file` part equals the served bytes), `return_documents` (local round), final text; `agent.documents` holds the Camunda reference (`camunda.document.type`, `storeId`, `documentId` = the id the model saw, `contentHash`); selection consumed in `agentContext`. |
| (c) unknown id | `unknownDocumentIdIsAnsweredInTheLoopAndTheModelRetries` (both flavors) | A batch made of the bad call alone is answered in the loop: error tool result with the known documents, the tool endpoint is hit exactly once (by the retry), process completes without incident, four model calls. |
| (d) eviction | `documentOfAnEvictedMessageStaysPassable` | With `contextWindowSize=3` the third request holds neither the download nor its document message, but ends with the eviction note (preamble + tag with tool origin); the model still passes the id and the tool gets the real bytes. |
| (e) stores | `passesADownloadedDocumentToAToolAndReturnsIt` parameterized | Same round trip with in-process, Camunda document and AWS AgentCore memory (the registry lives in `agentContext`, so it survives the job boundaries in every store). |
| (f) task flavor | `AgentTaskDocumentFlowTests` | Round trip and unknown id retry with the AI Agent Task (`agent.documents` on the `AgentResponse`). |
| (g) mixed batch | `mixedBatchOfReturnDocumentsAndARealToolCostsNoExtraModelCall` (both flavors) | `return_documents` and `Ping` in one assistant message: three model calls in total (download, mixed, final), `Ping` ran, the model got both results in call order, `agent.documents` holds the document, nothing pending afterwards. |
| (h) mixed batch with an unknown id | `mixedBatchWithAnUnknownDocumentIdRunsTheValidCallAndAnswersTheInvalidOne` (both flavors) | Upload with an unknown id plus `Ping`: `Ping` runs, the next request holds the error result and the `Ping` result in call order, the corrected upload is the only upload, four model calls. |

Global assertion: `DocumentFlowAssertions.assertNoDocumentInternalsInLlmRequests` checks the raw body
of every recorded model request for `storeId`, `contentHash` and `camunda.document.type`. It runs in
every document flow scenario and in the existing tool result and user prompt document tests of both
flavors (48 tests, run against the tag rendering and passing). No production bug was found by the
document flow tests.

Not covered: the real-provider document tests (`DocumentToolCallResultsIT`) and gateway tools (MCP,
A2A), because the feature does not apply to them.
