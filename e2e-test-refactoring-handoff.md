# Handoff: Refactor agentic-ai e2e tests — replace Mockito LLM mocking with WireMock

## Branch & State
- **Branch:** `claude/practical-einstein-zrqH5` (pushed, clean working tree)
- **Module:** `connectors-e2e-test/connectors-e2e-test-agentic-ai`
- **Latest commits:**
    - `77af591` style: formatter line-wrap on WireMock e2e classes
    - `59e6200` fix: use `ScenarioMappingBuilder` to enable `willSetStateTo`
    - `b9e11f0` test(agentic-ai e2e): introduce WireMock-based agent conversation pilot

## Goal
Eliminate Mockito mocking **of the agent conversation loop** in the agentic-ai e2e
module and drive the loop against **WireMock-stubbed LLM HTTP responses** instead
(real LangChain4j `OpenAiChatModel` over `/v1/chat/completions`). This exercises the
real model transport + request serialization + response parsing end-to-end, including
multi-turn + tool-calling + user-feedback scenarios.

**Scope (confirmed with user):**
- Replace ONLY the `ChatModelFactory`/`CloseableChatModel` mock + `chatRequestCaptor`.
- **KEEP** the `@MockitoSpyBean` verification spies (`AgentInstanceClient`,
  `CamundaDocumentConversationStore`, `CamundaDocumentStore`, `AdHocToolsSchemaResolver`)
  and **KEEP** `InMemoryBedrockAgentCoreClientFactory` (AWS Bedrock AgentCore SDK mock).
  These verify side-effects / mock a storage SDK, not the conversation — out of scope.
- **Provider:** `openaiCompatible` (its `baseUrl` can be redirected to WireMock; the
  default `openai` provider cannot).

## Achievements (this session) ✅
The **pilot is complete and both adjusted tests PASS** (verified by user locally with Docker):
- `L4JAiAgentConnectorFeedbackLoopTests` ✓
- `L4JAiAgentConnectorToolCallingTests` ✓

(In the cloud sandbox these only fail on `ContainerFetch` for `camunda/camunda:SNAPSHOT`
— Docker isn't available there. Compilation and logic are correct; they run green locally
and in CI.)

### New infrastructure (all under `.../aiagent/langchain4j/wiremock/`)
- **`OpenAiChatModelStubs.java`** — stubs `POST /v1/chat/completions` via WireMock
  **Scenarios** (`STARTED → turn-1 → turn-2 …`) for deterministic multi-turn sequencing.
    - `stubConversation(Turn... turns)`
    - `Turn.text(text, promptTokens, completionTokens)` → `finish_reason: "stop"`
    - `Turn.toolCalls(text, promptTokens, completionTokens, ToolCall...)` → `finish_reason: "tool_calls"`
    - `ToolCall.of(id, name, argumentsJson)`
    - `CHAT_COMPLETIONS_PATH = "/v1/chat/completions"` constant
    - **Gotcha fixed:** the mapping var MUST be typed `ScenarioMappingBuilder` (not
      `MappingBuilder`) — `willSetStateTo()` only exists on the scenario subtype.
      WireMock version here is `wiremock-standalone 3.13.2`.
- **`RecordedLlmConversation.java`** — replaces `ArgumentCaptor<ChatRequest>`. Parses the
  real HTTP bodies recorded by WireMock (`findAll(postRequestedFor(...))`, sorted by
  `LoggedRequest.getLoggedDate()`). Exposes `recorded()`, `modelCallCount()`,
  `lastRequest()`, `requests()`; inner `RecordedChatRequest` has `messages()`, `tools()`,
  `toolNames()`, `responseFormat()`, `body()`.

### New base class
- **`outboundconnector/BaseWireMockL4JAiAgentConnectorTest.java`** (extends
  `BaseAiAgentConnectorTest`, `@SlowTest`):
    - Keeps `@MockitoSpyBean AdHocToolsSchemaResolver toolsSchemaResolver`.
    - `@BeforeEach captureWireMockInfo(WireMockRuntimeInfo)` stores `wireMock`.
    - `withOpenAiCompatibleProvider(template)` sets `provider.type=openaiCompatible`,
      `endpoint=wireMock.getHttpBaseUrl()+"/v1"`, dummy `apiKey`, `model=test-model`.
    - Overrides `createProcessInstance(...)` to compose the provider redirect *before* the
      caller's modifier.
    - Shared scenario helpers with stable signatures:
      `testBasicExecutionWithoutFeedbackLoop(...)`,
      `setupBasicTestWithoutFeedbackLoop(...)`,
      `testInteractionWithToolsAndUserFeedbackLoops(...)`.
    - `assertConversationMessages(...)` + inner `ExpectedMessage` record
      (`system/user/assistant/assistantWithToolCalls/toolResult`),
      `assertToolSpecifications(...)`.

### Feedback decoupling
- **`BaseAiAgentTest.java`**: added `ConcurrentLinkedQueue<Map<String,Object>>
  userFeedbackQueue` + `enqueueUserFeedback(Map...)`. The `user_feedback` job worker
  polls the queue per invocation, falling back to the legacy single-shot
  `AtomicReference userFeedbackVariables` when empty. This replaces feedback being set as
  a Mockito side-effect of each `chat()` call.

### Converted pilot tests
- **`L4JAiAgentConnectorFeedbackLoopTests`** — now extends the WireMock base.
    - `executesAgentWithoutUserFeedback` / `basicExecutionWorksWithoutOptionalConfiguration`
      delegate to `testBasicExecutionWithoutFeedbackLoop`.
    - `executesAgentWithUserFeedback` manually stubs 2 turns, asserts 2 model calls,
      4-message conversation, metrics `(2, TokenUsage(21,42), 0)`. Uses emoji haiku text.
- **`L4JAiAgentConnectorToolCallingTests`** — now extends the WireMock base.
    - `executesAgentWithToolCallingAndUserFeedback` → `testInteractionWithToolsAndUserFeedbackLoops`.
    - `supportsDocumentResponsesFromToolCalls` (parameterized, 10 file types) — 3-turn
      scenario with `Download_A_File`, asserts multimodal "extracted documents" user message
      (preamble + `<doc/>` tag + content block; text→`type=text`, pdf→`type=file`,
      image→`type=image_url` data URL).
    - `supportsExternalDocumentReferenceResponsesFromToolCalls` — 2-turn `External_File_Reference`.
- **`ToolCallResultDocumentAssertions.java`**: `EXTRACTED_DOCUMENTS_PREAMBLE` made `public`
  (cross-package access).

## OpenAI wire format reference (for authoring stubs/assertions)
- **Request (connector → model):** `{ messages:[{role,content,tool_calls,tool_call_id}],
  tools:[{type:"function",function:{name,description,parameters}}], response_format }`
- **Response (WireMock → connector):** `{ choices:[{message:{content,tool_calls},
  finish_reason}], usage:{prompt_tokens,completion_tokens} }`
- Tool-call `arguments` is a JSON-**encoded string**, not a nested object.
- Multimodal document content is a `content` array of parts.

## TODOs / Remaining migration (follow-up)
Migrate the rest off the Mockito base, then delete `BaseL4JAiAgentConnectorTest` and
`BaseL4JAiAgentJobWorkerTest`.

1. **Connector flavor (remaining):** `Limits`, `UserPromptDocuments`,
   `ElementTemplateRegression`, `A2aIntegration`, `ProcessMigration` →
   straightforward conversions to `BaseWireMockL4JAiAgentConnectorTest`.
2. **Connector flavor w/ spies:** `AgentInstance`, `MemoryStorage`, `McpIntegration` —
   keep their `@MockitoSpyBean`s; only swap the LLM mock + captor assertions.
   (`McpIntegration` already uses WireMock `mappings/` for the MCP server.)
    - Note: `L4JAiAgentConnectorAgentInstanceTests` was already WireMock-based and confirms
      the OpenAI tool-call JSON works end-to-end.
3. **`ResponseHandlingTests`** still uses the Mockito base and asserts on
   `chatRequestCaptor.getValue().responseFormat()` → migrate using
   `RecordedLlmConversation.lastRequest().responseFormat()`.
4. **Job-worker flavor:** mirror the connector base into a new
   `BaseWireMockL4JAiAgentJobWorkerTest` and convert all job-worker subclasses
   (ToolCalling, ResponseHandling, FeedbackLoop, Events, VariableScope, Limits,
   UserPromptDocuments, ElementTemplateRegression, ProcessMigration, A2aIntegration,
   AgentInstance, MemoryStorage, McpIntegration); then delete `BaseL4JAiAgentJobWorkerTest`.
5. **Cleanup:** remove now-unused `ToolExecutionRequestEqualsPredicate`,
   `ToolExecutionResultMessageEqualsPredicate` once no caller remains. Migrate the
   already-WireMock `L4JAiAgentConnectorHttpTimeoutTests` single-shot path to
   `enqueueUserFeedback` if convenient (currently still works via the `userFeedbackVariables`
   fallback).

## How to run / verify locally
```bash
mvn -pl connectors-e2e-test/connectors-e2e-test-agentic-ai test \
  -Dtest='L4JAiAgentConnectorFeedbackLoopTests,L4JAiAgentConnectorToolCallingTests'