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
