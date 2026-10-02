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
carries synthetic tool results needs a sibling primitive. Not a blocker; the primitive is added in
step 4. Obligations of every extra round: `throwIfLimitsReached` before the round (bounds retries at
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
