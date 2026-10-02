# AI Agent document flow

Design and build notes for the document flow of camunda/experience-pdp#81 in the AI Agent
(`connectors/agentic-ai`). Status: working build on a local branch, kept current as steps land.

## 1. Scope

1. `fromAi(toolCall.x, "desc", "document")` tool parameters and how they materialize in the tool
   definition.
2. The agent remembers every document in its conversation (persisted registry).
3. The agent passes a known document to a tool. The tool receives the real Camunda document
   reference JSON.
4. The agent returns selected documents in its response.

## 2. Decisions

| # | Decision | Reason |
|---|----------|--------|
| 1 | The registry is persisted in `agentContext` (typed field `documents`) behind a small storage interface. | Deriving it from nested tool result documents is hard; filling at ingestion reuses the existing deep extraction. One place for all stores (including AWS AgentCore); commits atomically with job completion. |
| 2 | Document id is `DocumentHandle.idFor`. The model sees only `<doc id fileName contentType [toolName] [toolCallId] />`. | Never expose `storeId`, `contentHash`, other metadata or URLs (presigned URLs carry tokens). |
| 3 | Documents out are a built-in tool `return_documents`, handled in the model loop. | The tool must never become a BPMN element activation. |
| 4 | An unknown document id yields an error tool result and a retry in the same job. | No incident; the tool does not run. |
| 5 | Tools receive the full Camunda document reference JSON. | Same shape other connectors emit and consume; no agent-internal handle leaves the agent. |

## 3. Model loop (Step 0 finding)

`BaseAgentRequestHandler.driveContinuationLoop` re-calls the model only on `ChatResult.Continuation`;
`AgentConversation.nextContinuationRound()` opens a pending turn with empty input. A turn that
carries synthetic tool results uses the sibling primitive `AgentConversation.nextLocalToolRound`.
Obligations of every extra round: `throwIfLimitsReached` before the round (bounds retries at
`maxModelCalls`), agent instance history reporting for the synthetic input, and a persist/reload
round trip through `TurnReconstructor` without `lastIterationKey` drift.

### Mixed batches (policy)

The composer waits for a result for every tool call id of the last assistant message. Therefore a
batch is answered **either fully locally or fully by BPMN**. If any call in a batch is local
(`return_documents`) or invalid (unknown document id), every call of the batch is answered in the
loop: local calls get their real result, invalid calls get an error result, real calls that are
otherwise valid get "not executed, resend separately". Then the loop calls the model again. No new
persisted state is needed. The AI Agent Task flavor follows the same rule because both share
`BaseAgentRequestHandler`.

## 4. Open points, blockers, gaps

To be filled while building (see steps).

### Step 1 notes: document parameters in the tool definition

Hook points (`connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/`):

- `adhoctoolsschema/schema/AdHocToolSchemaGeneratorImpl.java`: `generateToolSchema` and
  `resolveDocumentParameters` share one pass; `mapDocumentTypes` rewrites `document` nodes.
- `adhoctoolsschema/schema/AdHocToolsSchemaResolverImpl.java` (`createToolDefinition`): copies the
  document parameters from the generator into the `ToolDefinition`.
- `aiagent/model/tool/ToolDefinition.java`: new field `documentParameters`.
- `AgentToolsResolverImpl.updateChangedToolDefinitions` and `AgentInitializerImpl` need no change:
  they compare/store whole `ToolDefinition` records.

Representation: `ToolDefinition.documentParameters` is a `List<String>` of JSON pointers into
`inputSchema` (`/properties/doc`, `/properties/docs/items`, `/properties/req/properties/file`;
`~` and `/` in property names are escaped as `~0` and `~1`). It is `@JsonInclude(NON_EMPTY)` and
normalized to an empty list, so a tool without document parameters serializes and compares equal to
a definition persisted before the field existed. The field lives outside the schema, so no provider
converter (langchain4j, Anthropic, Bedrock, Gemini, OpenAI completions/responses) sees it; they only
read `name`, `description` and `inputSchema`. No configuration fingerprint covers tool definitions;
the only comparison is record equality in `updateChangedToolDefinitions`.

Nested support: parameter type `document` is mapped at the top level, as array `items`, and
recursively through object `properties` and array `items` of a parameter schema. `document` inside
`anyOf`/`oneOf`/`$ref` is not mapped and reaches the provider converters as an unknown type.

Schema example for `fromAi(toolCall.docs, "The attachments", "array", {"items": {"type": "document"}})`:

```json
{"type": "object",
 "properties": {"docs": {"type": "array", "description": "The attachments",
   "items": {"type": "string",
             "description": "Pass the id of a <doc/> tag of a known document."}}},
 "required": ["docs"]}
```

with `documentParameters = ["/properties/docs/items"]`. A top-level `document` parameter becomes
`{"type": "string", "description": "<desc> Pass the id of a <doc/> tag of a known document."}`.

### Step 2 notes: persisted document registry

Hook points (`connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/`):

- `aiagent/agent/AgentConversationTurnInputComposerImpl.java`: `compose` collects an
  `IngestedDocument` (document, `toolCallId`, `toolName`) for every document that enters the turn:
  user prompt documents (`createUserPromptMessage`, :151), tool result documents including gateway
  `extractDocuments` and nested documents (`createDocumentMessageForToolResults`, :227, reuses
  `ToolCallResultDocumentExtractor`) and event documents (`createEventMessage`, :247). They are
  returned as `CompositionResult.NextTurn.documents()`, ordered prompt/tool results first, events
  last (same order as the messages).
- `aiagent/agent/BaseAgentRequestHandler.java`: `converse` (:146) passes the `NextTurn` documents to
  `registerDocuments` (:164), which loads the registry, seeds it from the loaded messages when it is
  empty and a conversation exists, adds the new documents and returns the agent context carrying the
  result. `proceed` rehydrates the `AgentConversation` from that context, so every copy-on-write
  step and `AgentConversation.toAgentContext()` (:266) carry the registry; it is persisted by the
  same job completion as the rest of `agentContext`. Not on the `Deferred`/`NoInput`/no-op paths
  (nothing enters the conversation there).
- `aiagent/model/AgentContext.java`: new field `documents` (`List<DocumentRegistryEntry>`,
  `@JsonInclude(NON_EMPTY)`).
- `autoconfigure/AgenticAiConnectorsAutoConfiguration.java` (:297): `DocumentRegistryStorage` bean,
  injected into both request handlers.

Interface (`aiagent/model/document/`): `DocumentRegistryStorage` with `DocumentRegistry
load(AgentContext)` and `AgentContext store(AgentContext, DocumentRegistry)`. The only
implementation, `AgentContextDocumentRegistryStorage`, reads and writes `AgentContext.documents()`.
`DocumentRegistry` is an immutable, insertion-ordered, id-keyed value: `findById`, `entries`,
`withDocuments` (first registration of an id wins, re-adding a known id returns the same instance),
`of(entries)` and `fromMessages(messages)` (the seeding scan).

Entry shape (`DocumentRegistryEntry`): `id` (`DocumentHandle.idFor`), `reference` (a
`Map<String, Object>` holding the JSON the standard `DocumentSerializer` emits: `camunda.document.type`,
`storeId`, `documentId`, `contentHash`, `metadata` for Camunda documents), `fileName`, `contentType`,
`toolName`, `toolCallId` (the last two only when the document came from a tool result). The
reference is a plain map, so reading the registry needs no `DocumentFactory`; it binds back to a
`Document` through the regular document deserializer (covered by a test). The entry serializer uses
a private `ObjectMapper` with the document serializer module and `NON_NULL` inclusion; dates are
ISO strings.

Backward compatibility: no `CURRENT_SCHEMA_VERSION` bump. A missing `documents` field binds to an
empty list (builder default for collections, same as `toolDefinitions`), also through the raw-tree
path of `AgentContextSchemaMigration`. A context with a conversation and an empty registry is seeded
from the stored messages: `DocumentContent` of user messages (this includes the synthetic tool call
document messages, so gateway documents are covered) and the content trees of tool call results
(origin retained). The seed is best effort; a conversation that legitimately has no documents is
rescanned on every turn (the messages are loaded anyway). An older connector runtime ignores the
field (`ignoreUnknown`) and drops it on its next write.

Store independence: the registry rides in `agentContext`; the `ConversationSession` implementations
(in-process, Camunda document, AWS AgentCore) only read the context and return the new
`ConversationContext` pointer, `AgentConversation.withStoredConversation` copies the rest of the
context. The store formats are unchanged. Unit tests cover the in-process store; the e2e coverage
for the other two stores comes in step 6.

Inline documents: the entry keeps the complete reference, so for an inline document it contains the
full content (base64 in `content`), because the tool payload of step 3 must be the real reference.
Cost: the content is held a second time in `agentContext`, in addition to the tool result in the
conversation. Inline documents are rare (Camunda documents are the norm), but a large inline image
counts fully against the limit below. External documents keep the URL (never rendered to the model,
decision 2), Camunda documents keep the full reference with metadata.

Serialized size per entry (`DocumentRegistryEntryTest#serializedEntrySizes`, entry JSON with tool
origin `Search_Tool` / `call_0123456789abcdef`):

| Entry | Bytes |
|-------|-------|
| Camunda document (metadata: contentType, fileName) | 372 |
| External document (URL of about 50 characters) | 226 |
| Inline document, 1 KB content | 1221 |

A Camunda reference with more metadata (size, expiresAt, process ids) adds roughly 100 to 150 bytes.

Limits: Zeebe has no separate per-variable size cap; the bound is the maximum message size of the
broker, `NetworkCfg.DEFAULT_MAX_MESSAGE_SIZE` = 4 MB (`zeebe/broker/.../NetworkCfg.java`), which
applies to the whole job completion command carrying `agentContext` and the other variables. At
about 400 bytes per Camunda document the registry alone needs roughly 10,000 documents to reach it,
so it only matters together with the in-process store (messages in the same variable) or inline
documents.

Migration path to the stores: implement `DocumentRegistryStorage` against `ConversationSession`
(store the registry next to the messages, keyed by the conversation pointer), then switch the bean.
The handler and the composer do not change; `AgentContext.documents` stays readable (empty for new
conversations) so persisted state from this layout still loads, and the seeding scan covers any
conversation without a stored registry.

### Step 3 notes: rendering documents to the model

Hook points (`connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/`):

- `aiagent/model/message/DocumentReferenceXmlTag.java`: the tag, a record of `id`, `fileName`,
  `contentType`, `toolName`, `toolCallId`; built from a `Document`, or from a registry entry (used
  by the eviction note). `aiagent/model/document/DocumentHandle.java` derives the attributes
  (`idFor`, `fileNameFor`, `contentTypeFor`); `DocumentRegistryEntry.from` uses the same functions.
- `aiagent/agent/AgentConversationTurnInputComposerImpl.java`: `createDocumentPairs` (tool result and
  event documents) and `createUserPromptMessage` (user prompt documents) put the tag text before each
  `DocumentContent`.
- `aiagent/chatmodel/provider/ModelFacingJson.java`: JSON text for the model; a `Document` at any
  depth renders as its tag. Used by every provider path that serializes message or tool result
  object content (see below).
- `aiagent/model/document/EvictedDocumentsNote.java`, called from `AgentConversation.window`: the
  eviction note.

Tag format: `<doc id="..." fileName="..." contentType="..." toolName="..." toolCallId="..." />`,
attributes in that order, XML-escaped, blank or unknown attributes omitted (no placeholders).
`id` is `DocumentHandle.idFor`. `fileName` is the Camunda metadata file name, the external
reference `name` or the inline `name`; for an external document without a name it is the last path
segment of the URL without query and fragment (percent-decoded, only when not blank), never the URL.
`contentType` comes from the Camunda metadata or the inline document; it is omitted for external
documents because their metadata is only available after a download. `toolName` and `toolCallId`
are present for documents that came from a tool result. The tag never carries a store id, content
hash, other metadata or URL. Registry entries use the same functions, so reading the registry no
longer downloads an external document just to fill `contentType`.

User prompt documents carry a tag without tool attributes, in the same pair shape as the extracted
tool result and event documents.

Per provider, a `Document` inside tool result content (a bare document or nested in object content)
now serializes as the tag, not as the Camunda reference JSON. A bare document is the tag text; a
nested one is the tag string inside the JSON value (quotes escaped by JSON):

| Provider path | Where | Rendering |
|---------------|-------|-----------|
| langchain4j | `ToolCallConverterImpl.contentElementAsString`, `ContentConverterImpl.convertToString` | tag text / JSON with tag strings |
| Anthropic | `AnthropicContentConverter.toToolResultBlocks`, `toContentBlockParams` (object content) | text block |
| OpenAI completions (and the Mistral dialect) | `OpenAiCompletionsRequestConverter.toolResultMessages`, `OpenAiFileContentChunkStrategy` | text |
| OpenAI responses | `OpenAiContentConverter.toResponsesToolResultOutputItems`, `toResponsesContentParts` | `input_text` |
| Bedrock Converse | `BedrockConverseContentConverter.toToolResultBlocks` (`BedrockConverseDocuments` with the tag mapper) | `json` block with tag strings; a bare document or non-map value as `text` |
| Gemini | `GeminiContentConverter.toFunctionResponseParts`, `toParts` (object content) | `functionResponse` output string |

Native tool result documents: no provider embeds a document inside the tool result entry (Anthropic
`tool_result`, OpenAI `function_call_output`, Bedrock `toolResult`, Gemini `functionResponse` all
carry text/JSON only). The real bytes travel in the user message that follows the tool results, where
the tag text precedes each native block. So the id the model sees in the tool result entry and next
to the native block is the same. Two native block names are not the id: the Bedrock `DocumentBlock.name`
is a hash derived from the id (Bedrock charset rules) and the OpenAI `input_file.filename` is the file
name; the preceding tag text is what carries the id.

Eviction note: when the conversation is windowed (`AgentConversation.window(size)`), the registry of
the current `AgentContext` is compared with the windowed messages. A registry document is visible
when its id appears as `DocumentContent` of a user message, as a `Document` in a tool result content
tree (`ContentTreeDocumentWalker`), or as the id of a `<doc/>` tag in user or tool result text. If
at least one document is not visible, a user message is appended to the snapshot only: the preamble
`EvictedDocumentsNote.PREAMBLE` plus the tag of each such document (`DocumentReferenceXmlTag.from(entry)`:
id, file name, content type, tool origin). It is never part of `allMessages()`, never persisted and
leaves the frozen system prompt alone.

Why a trailing note and not a system prompt addition or a rewritten message: the system prompt is
frozen per agent instance; stored messages must stay byte-stable for replay and prompt caching; the
note is the only thing that changes when the window slides, and it is a suffix, so cached prefixes
stay valid. Message ordering: the note follows tool result and document messages that are user role
anyway (the existing document message does the same). It is skipped when the windowed messages end
with an assistant message (a continuation round resumes on that message), so such a round does not
repeat the note it already had in the first round of the invocation.

Gaps:

- Documents referenced from the content of an evicted assistant message only are not tracked; only
  registry documents are listed. A registry that was seeded from an old conversation is as complete
  as the seeding scan.
- The note does not describe a document's content; the model can address it by id (steps 4 to 6 add
  passing and returning by id).
- External documents have no `contentType` in any tag, because it would require a download.
- A file name derived from a URL path segment can still carry an identifier chosen by the URL owner
  (for example a token as the last segment); only the `name` of the reference or Camunda metadata
  is fully under the connector user's control.
- The e2e suite was updated to the new shape (`ToolCallResultDocumentAssertions`, the tool calling,
  MCP and user prompt document tests) and compiles; it was not run for this step.

### Step 4 notes: documents into tools

Hook points (`connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/`):

- `aiagent/agent/BaseAgentRequestHandler.java`: `driveContinuationLoop` (:258). After each
  `Completed` result is ingested, `localToolCallHandler.handle` (:282) inspects the tool calls of the
  assistant message. If it answers them, the loop reports the round with status `THINKING`,
  checks `throwIfLimitsReached`, opens the next round with `AgentConversation.nextLocalToolRound`
  (:314) and reports its start with `applyTurnStart` (:319), then calls the model again. Both
  flavors share it.
- `aiagent/model/AgentConversation.java`: `nextLocalToolRound` (:178) closes the ingested turn and
  opens a pending turn (iterationKey + 1) whose input is the given `ToolCallResultMessage`; it
  requires the results to cover exactly the tool calls of the closed turn. `documentRegistry()`
  (:255) is the registry of the working conversation's context; it already contains the documents
  ingested by this invocation's input (`registerDocuments` stores them before `rehydrate`).
  `hasInterruptedToolCallResultsInInvocation` (:263) replaces the current-turn check of
  `AgentSubProcessRequestHandler` (:103): the interrupted results of the first round must still
  cancel the remaining tool instances after a local round replaced the current turn.
- `aiagent/agent/AgentResponseHandlerImpl.java` (`createResponse`, :67): the seam for the process
  variable. `DocumentParameterResolver.resolveAll` runs on the raw tool calls of the assistant
  message, before the gateway transformation, using the tool definitions of the configuration and
  `conversation.documentRegistry()`. Only the `ToolCallProcessVariable` (and so the element
  activation variable `toolCall` of the sub-process flavor and the task flavor's `toolCalls`)
  carries the references; the persisted assistant message keeps the ids the model sent.
- `aiagent/agent/DocumentParameterResolver.java`: walks the arguments along the pointers of
  `ToolDefinition.documentParameters`: `/properties/x` is `arguments.x`, `/items` every element of
  the array, nested pointers recurse through objects and arrays. An id is replaced by the registry
  entry's `reference` map (`camunda.document.type`, `storeId`, `documentId`, `contentHash`,
  `metadata`; the same shape other connectors emit and consume). A missing or `null` argument is
  left alone (optional parameters). The original arguments are never modified.
- `autoconfigure/AgenticAiConnectorsAutoConfiguration.java`: `DocumentParameterToolCallInspector`
  and `LocalToolCallHandler` beans; every `ToolCallInspector` bean joins the handler.

Local handler abstraction (`aiagent/agent/`): `LocalToolCallHandler.handle(conversation, toolCalls)`
returns `Optional<List<ToolCallResult>>`: empty when every call goes to the process, otherwise one
result per call. The implementation, `LocalToolCallHandlerImpl`, asks every `ToolCallInspector`
(`inspect(toolCall, LocalToolCallContext)`, context = tool definitions + document registry) for a
`ToolCallVerdict` per call: `Pass` or `Answer(content)`. The first `Answer` of a call wins.
`DocumentParameterToolCallInspector` is the only inspector; the `return_documents` handler of step 5
is a second one that answers its own calls with the real result.

Batch policy (see "Mixed batches" above): if at least one call of the batch gets an `Answer`, every
call is answered in the loop. Passed calls get `LocalToolCallHandlerImpl.NOT_EXECUTED`
("Not executed because another tool call in the same batch failed. Resend this call separately or
after fixing the failed call."). Tools never run for any call of such a batch.

Error text of an invalid document parameter (one sentence per invalid value, then the known
documents; at most 50 are listed, the rest is counted):

```
Unknown document id 'x' for parameter 'docs[1]'. Known documents:
<doc id="..." fileName="..." contentType="..." toolName="..." toolCallId="..." />
```

The parameter path is `name`, `parent.name` for nested objects and `name[i]` for array elements. A
blank or non-string value reads "Parameter 'p' must be the id of a known document (a non-blank
string).", an array parameter that is not an array "Parameter 'p' must be an array.". With an empty
registry the list is replaced by "No documents are known.". Results are plain text: no provider has
a tool error flag in the domain model, so the model reads the text.

Loop obligations:

- Limit: `throwIfLimitsReached` runs before every local round, so retries are bounded by
  `maxModelCalls` (the invocation fails with the existing max-model-calls error code, like a
  continuation round).
- Agent instance history: the assistant turn of a round followed by a local round is completed with
  `THINKING` (not `TOOL_CALLING`); the local round starts with `applyTurnStart` whose input is the
  synthetic tool result message and whose previous turn is the closing turn, so each result is
  correlated to its tool call and reported as a `TOOL_RESULT` item with `loopIteration` + 1.
  Exactly one status per call, as before. No other `AgentInstanceClient` call changed, so the
  existing `verifyNoMoreInteractions` expectations hold.
- Persistence: the stored messages read `assistant(tool calls) -> tool results -> assistant`, which
  `TurnReconstructor` splits into one turn per assistant message. `lastIterationKey` is stamped from
  the final round and equals the reconstructed turn count, so the drift warning does not fire. The
  windowing filter evicts the tool results together with their assistant message, as for any
  tool call round.
- Metrics: every round counts as a model call (`jobMetrics` sums the rolled-in turns).

Behavior notes: validation looks only at ad-hoc tools whose definition has `documentParameters`;
the tool name is the lookup key. A failing resolution in `createResponse` (not reachable after
validation) throws `IllegalStateException`. A model that reuses a tool call id across rounds
reuses the agent instance history item id of the earlier result; ids are assigned by the providers
and unique in practice.

Open point, gateway tools (MCP, A2A): out of scope. Their definitions never carry
`documentParameters`, the gateway handler seam (`transformToolCalls`) is unchanged and runs after the
document resolution, which leaves their calls untouched. Passing documents into gateway tools needs
its own design (the gateway wraps the call arguments in an envelope the handler builds).

