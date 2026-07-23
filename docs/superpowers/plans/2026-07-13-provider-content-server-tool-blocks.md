# ProviderContent — Server-Tool / Code-Execution Block Round-Trip Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax. Implementation subagents run on **Sonnet** (`model: "sonnet"`), never Opus (standing user constraint). Subagents ONLY `git add`+`git commit` — never checkout/reset/revert/rebase/stash/push. Run all mvn/git with `dangerouslyDisableSandbox: true` (sandbox breaks Mockito + blocks network). Read surefire XML, not mvn stdout.

**Goal:** Stop silently dropping Anthropic `server_tool_use` / `*_tool_result` / `container_upload` response blocks (produced by **Skills + code execution**, and web tools) — preserve them losslessly so multi-turn / `pause_turn` continuations and code-execution container state actually work end-to-end.

**Why now (not deferred with web tools):** Skills run *inside* code execution. A skills turn's response contains `server_tool_use` (code_execution) + `code_execution_tool_result` + `container_upload` blocks. Today `AnthropicMessageResponseConverter.toAssistantMessage` silently skips every block that isn't text/tool_use/thinking/redacted_thinking, so on a `pause_turn` continuation we re-send an assistant message missing those blocks → malformed continuation. This is Skills-critical.

**Architecture:** One new opaque neutral content subtype `ProviderContent(provider, blockType, payload, metadata)` carries the raw block as **plain JSON** (a `Map`, never the live SDK object). Response side maps every non-core block to `ProviderContent` in original order; request side deserializes the payload straight back into `BetaContentBlockParam`. Generic — no per-block-type code. Ratified design (2026-07-13): explicit sealed subtype (not `ObjectContent`+metadata, not reshaping into `ToolCall`/`ToolCallResult`); kept in `AssistantMessage.content`, NOT `toolCalls` (so the agent loop never tries to execute server tool calls locally).

**Tech Stack:** Java 21, `@NullMarked`, anthropic-java-core **2.48.0** (beta messages types `com.anthropic.models.beta.messages.*`), `com.anthropic.core.ObjectMappers.jsonMapper()` (the SDK mapper), Jackson, JUnit5, AssertJ.

## Global Constraints
- BC on Camunda 8.9-persisted data is #1 priority. `Content` is a **sealed, persisted** interface — adding `ProviderContent` is purely additive (old conversations never contain it; forward-compatible). Do NOT change existing subtypes' wire shapes.
- Native Anthropic v2 path only. Do NOT touch v1/LangChain4j behavior, `OpenAiChatModel`, or the SPI beyond the compile-forced `Content` switch branches.
- `payload` MUST be plain JSON (`Map<String,Object>`), produced via the **SDK mapper** — never the live SDK block object and never a bare `JsonMissing`/`JsonField` (this is the exact trap behind the no-arg-tool crash `0a072fc176`).
- Every exhaustive `switch (content)` over `Content` becomes a compile error until it handles `ProviderContent`. There are **9 main-source sites** (enumerated in Task 1). This compile-forcing is the safety net — do not suppress it with a catch-all `default` on the sealed switches unless the site already has one.
- anthropic-java 2.48.0 verified facts: input union `BetaContentBlockParam` has `ofServerToolUse`, `ofWebSearchToolResult`, `ofWebFetchToolResult`, `ofCodeExecutionToolResult`, `ofBashCodeExecutionToolResult`, `ofTextEditorCodeExecutionToolResult`, `ofContainerUpload`, `ofMcpToolUse`, `ofFallback`, plus a raw `JsonValue` in its constructor. Output union `BetaContentBlock` has matching `is*/*()` accessors and `_json()`. `AgentInstanceHistoryContent` (camunda-client-java 8.10.0-SNAPSHOT) has ONLY `text(String)`/`object(Object)`/`document(DocumentReferenceResponse)`.

---

## File Structure
- Create: `.../aiagent/model/message/content/ProviderContent.java` — the new sealed subtype.
- Modify: `.../content/Content.java` — add to `permits` + `@JsonSubTypes`.
- Modify (native, meaningful branches):
  - `.../framework/anthropic/AnthropicMessageResponseConverter.java` — map non-core response blocks → `ProviderContent` (Task 2).
  - `.../framework/anthropic/AnthropicContentConverter.java` — `toContentBlockParams` round-trips `ProviderContent` → `BetaContentBlockParam` (Task 1 + Task 3).
- Modify (compile-forced branches, behavior per Task 1):
  - `.../agentinstance/AgentInstanceHistoryMapper.java` → `object(payload)` + mirroring comment.
  - `.../memory/conversation/awsagentcore/mapping/AwsAgentCoreConversationMapper.java` → preserve as opaque (object-equivalent) for storage round-trip.
  - `.../framework/multimodal/CapabilityAwareToolCallResultStrategy.java` → skip/passthrough (ProviderContent is not a tool-result content).
  - `.../aiagent/agent/AgentResponseHandlerImpl.java` → not surfaced to response output; skip.
  - `.../a2a/client/agentic/tool/A2aGatewayToolHandler.java` → skip.
  - `.../framework/langchain4j/ChatMessageConverterImpl.java`, `.../langchain4j/ContentConverterImpl.java`, `.../langchain4j/tool/ToolCallConverterImpl.java` → bridge never produces `ProviderContent`; add a branch that skips (mirror the existing `ReasoningContent` handling in each) — do NOT throw (keeps replay of a mixed store robust).
- Tests: alongside each converter; new e2e in `connectors-e2e-test-agentic-ai` (Task 4).

---

### Task 1: Add `ProviderContent` and wire all exhaustive `Content` switches (build green)

Adding a sealed subtype breaks every exhaustive switch at once, so this task adds the type AND all branches in one coherent, compiling change. Native round-trip branch is real; the rest follow the per-site table in File Structure.

**Files:** Create `ProviderContent.java`; modify `Content.java` + the 9 switch sites.

**Interfaces:**
- Produces: `record ProviderContent(String provider, String blockType, Object payload, @Nullable Map<String,Object> metadata) implements Content` — mirror `ReasoningContent`'s Jackson annotations (`@JsonIgnoreProperties(ignoreUnknown=true)`, `@JsonInclude` on nullable/empty fields). `payload` typed `Object` (a `Map` at runtime) so Jackson persists/reads it as generic JSON. Add a convenience factory `providerContent(String provider, String blockType, Object payload)`.

- [ ] **Step 1: Enumerate & confirm the 9 sites.** Run `rg -l 'case (TextContent|ReasoningContent|ObjectContent|DocumentContent)|instanceof (TextContent|ReasoningContent|ObjectContent|DocumentContent)' connectors/agentic-ai/connector-agentic-ai/src/main/java -g '*.java'`. Expect exactly: `AgentInstanceHistoryMapper`, `A2aGatewayToolHandler`, `CapabilityAwareToolCallResultStrategy`, `AgentResponseHandlerImpl`, `ToolCallConverterImpl`, `AwsAgentCoreConversationMapper`, `ChatMessageConverterImpl`, `AnthropicContentConverter`, `ContentConverterImpl`. If the set differs, STOP and report.
- [ ] **Step 2: Write failing test** `ProviderContentTest` — construct a `ProviderContent("anthropic","code_execution_tool_result", Map.of("type","code_execution_tool_result","tool_use_id","srvtoolu_1","content", Map.of("stdout","hi")), null)`, serialize+deserialize via a plain `ObjectMapper` (matches how conversation memory persists), assert equality incl. nested payload. Also assert it deserializes through the `Content` base type (polymorphic `type` discriminator = `"provider"` — pick the `@JsonSubTypes` name `provider`).
- [ ] **Step 3: Create `ProviderContent.java`** + register in `Content.java` (`permits ... , ProviderContent`; `@JsonSubTypes.Type(value = ProviderContent.class, name = "provider")`). Run Step-2 test → PASS.
- [ ] **Step 4: Add the round-trip branch to `AnthropicContentConverter.toContentBlockParams`** (exhaustive switch, no default): `case ProviderContent pc -> blocks.add(ObjectMappers.jsonMapper().convertValue(pc.payload(), BetaContentBlockParam.class));`. Add `com.anthropic.core.ObjectMappers` import. (Its sibling `toToolResultBlocks` already has a `default` catch-all — `ProviderContent` there is not expected, existing default handles it; leave as-is.)
- [ ] **Step 5: Add `AgentInstanceHistoryMapper.toHistoryContent` branch** → `case ProviderContent providerContent -> objectHistoryContent(providerContent.payload());` with a comment mirroring the `ReasoningContent` one verbatim in spirit: `// Agent instance history has no dedicated provider/server-tool content block yet; surface it as an object block for now (follow-up: engine schema addition + team decision).`
- [ ] **Step 6: Add the remaining 7 branches** per the File Structure table: AWS AgentCore mapper preserves the payload (object/opaque so its store round-trips); multimodal strategy / response handler / A2A / the three L4J bridge converters skip (mirror their existing `ReasoningContent` branch — most are `-> {}` or equivalent no-op/append-nothing). Read each site's `ReasoningContent` branch first and match its idiom.
- [ ] **Step 7: Unit tests** — (a) `AnthropicContentConverterTest`: a `ProviderContent` whose payload is a valid `server_tool_use` JSON map round-trips to a `BetaContentBlockParam` with `isServerToolUse()` true; (b) `AgentInstanceHistoryMapperTest`: a `ProviderContent` maps to an `object` history content carrying the payload.
- [ ] **Step 8: Build the module green** — `mvn test -pl connectors/agentic-ai/connector-agentic-ai` (all existing + new tests). Confirm no site was left with a suppressed/incorrect branch.
- [ ] **Step 9: Commit** — `Add ProviderContent for lossless provider-specific content blocks`.

---

### Task 2: Map non-core Anthropic response blocks → `ProviderContent`

**Files:** Modify `AnthropicMessageResponseConverter.java`; test `AnthropicMessageResponseConverterTest.java`.

**Interfaces:**
- Consumes: `BetaContentBlock` (output union), `ProviderContent` (Task 1).
- The `toAssistantMessage` block loop currently: `if isText -> content.add(TextContent) else if isToolUse -> toolCalls.add else if isThinking/isRedactedThinking -> content.add(ReasoningContent)`. Everything else is dropped.

- [ ] **Step 1: Spike/verify round-trip fidelity** (de-risk): in a test, `ObjectMappers.jsonMapper().readValue(<realistic code_execution BetaMessage JSON>, BetaMessage.class)`; for each non-core block, `Map raw = ObjectMappers.jsonMapper().convertValue(block, Map.class)`; then `ObjectMappers.jsonMapper().convertValue(raw, BetaContentBlockParam.class)` and assert the discriminator survives (e.g. `isCodeExecutionToolResult()`). If a specific output-only field makes the param deserialize throw, fall back to constructing the param from raw `JsonValue` (`BetaContentBlockParam` has a `JsonValue` constructor / `ofFallback`) — record which blocks need the fallback. Use block JSON for: `server_tool_use`, `code_execution_tool_result`, `container_upload` (skills path), plus `web_search_tool_result` (web path).
- [ ] **Step 2: Write failing test** `mapsServerToolBlocksToProviderContentPreservingOrder`: readValue a `BetaMessage` whose content is `[text "working", server_tool_use(code_execution), code_execution_tool_result, text "done"]`; assert `assistantMessage.content()` is `[TextContent, ProviderContent(blockType=server_tool_use), ProviderContent(blockType=code_execution_tool_result), TextContent]` in that order, and `toolCalls()` is empty (server tool use is NOT a client tool call).
- [ ] **Step 3: Implement** — add a final catch-all to the block loop: `else { final Map<String,Object> raw = ObjectMappers.jsonMapper().convertValue(block, new TypeReference<Map<String,Object>>(){}); content.add(new ProviderContent("anthropic", String.valueOf(raw.get("type")), raw, null)); }`. Keep it AFTER the isText/isToolUse/isThinking/isRedactedThinking checks so client tool_use still routes to `toolCalls` and thinking still routes to `ReasoningContent`. Remove any interim "log skipped block" line (now handled).
- [ ] **Step 4: Run Step-2 test → PASS.** Also add a test that a plain client `tool_use` still lands in `toolCalls` (not `ProviderContent`) — guards the ordering of the if/else chain.
- [ ] **Step 5: Commit** — `Preserve Anthropic server-tool and code-execution response blocks as ProviderContent`.

---

### Task 3: Round-trip continuation (`ProviderContent` survives a full turn cycle)

**Files:** Test only (unit/integration at converter level) — `AnthropicMessageRequestConverterTest` / a new focused test; no production change if Tasks 1-2 are correct. If a gap surfaces (e.g. ordering of content vs `toolCalls` on the request side), fix in `AnthropicMessageRequestConverter.applyMessages`.

- [ ] **Step 1: Failing test** `roundTripsProviderContentBackToServerToolBlockParams`: build an `AssistantMessage` whose `content` includes the `ProviderContent` blocks produced in Task 2 (reuse that fixture), run it through the request path (`toMessageCreateParams` with that assistant message in the snapshot), and assert the emitted assistant message param contains `server_tool_use` + `code_execution_tool_result` content blocks, in order, with the ids preserved.
- [ ] **Step 2: Verify order** relative to any client `tool_use` in `toolCalls`. Document the known limitation if interleaving a client tool_use *between* server blocks is not preservable (server blocks stay grouped by content order; client tool_use appended per current behavior). Add a comment; only restructure if a skills/code-exec scenario actually needs interleaving (it typically does not).
- [ ] **Step 3: PASS + Commit** — `Verify ProviderContent server-tool blocks round-trip into Anthropic requests`.

---

### Task 4: End-to-end skills/code-execution turn through the real accumulator

**Files:** Extend `NativeAnthropicMessagesSseChatModelStubs` (SSE stub) to emit a `server_tool_use` + `code_execution_tool_result` sequence for a turn; new e2e test in `.../wiremock/anthropic/` (mirror `NativeAnthropicSkillsAndToolsWireFormatTest`). This closes the coverage gap that let the JsonMissing crash + this drop ship (native path had zero server-block e2e).

- [ ] **Step 1:** Add a `TurnStub` variant (or extend the existing tool-call stub) that frames `content_block_start`/delta/stop for a `server_tool_use` and a `code_execution_tool_result` block, serialized with `ObjectMappers.jsonMapper()` (as the stub already does), driven through the real `BetaMessageAccumulator`.
- [ ] **Step 2:** e2e test: a skills-configured native-Anthropic agent whose stubbed turn returns text + server_tool_use + code_execution_tool_result + text, then a satisfied follow-up. Assert the process completes AND (if the flow issues a second model call) the second request's assistant history contains the round-tripped `server_tool_use`/`code_execution_tool_result` blocks. Reuse `NativeAnthropicSkillsAndToolsWireFormatTest`'s Anthropic wiring.
- [ ] **Step 3:** Run the e2e (engine-backed, slow; refresh stale `~/.m2` snapshots with `mvn install -pl connector-sdk/core,connectors/agentic-ai/connector-agentic-ai -am -DskipTests` if phantom failures). Also cross-build `connectors-e2e-test-agentic-ai` (standing constraint).
- [ ] **Step 4: Commit** — `Add native-Anthropic e2e coverage for code-execution server-tool blocks`.

---

## Post-implementation
- Whole-block `crit` review (Mathias reviews) covering the C7 block + these commits.
- Follow-up (separate, engine-side): a first-class `AgentInstanceHistoryContent` type so `ProviderContent` isn't degraded to `object(...)` — same gap class as `systemPrompt`-on-`UpdateAgentInstance`. File when picking up web-tools/dynamic-filtering.
- The web-tools/dynamic-filtering work (allowed_callers, newer web tool versions) is still separate and later; this plan's response/round-trip mechanism already covers web `*_tool_result` blocks generically once those tools are enabled.

## Provider generality (OpenAI etc.)
`ProviderContent` is `provider`-tagged and opaque precisely so it generalizes: a future **native OpenAI** path (Responses API hosted tools — `web_search_call`, `file_search_call`, `code_interpreter_call`, reasoning items) reuses the SAME neutral type as `ProviderContent("openai", <itemType>, payload, ...)` with ZERO neutral-model changes. What does NOT transfer: the per-provider CONVERTER logic (each provider owns its response→ProviderContent mapping and ProviderContent→request round-trip). Continuation semantics: **DECIDED (2026-07-13, Mathias) — we will NOT use OpenAI server-side state (`previous_response_id`) for now.** So the future native OpenAI path replays items statelessly, meaning the `ProviderContent` round-trip is genuinely exercised for OpenAI too (store AND re-emit), same as Anthropic — not merely stored. OpenAI hosted-tool items also tend to BUNDLE call+result into one item (`code_interpreter_call` carries `outputs`, `file_search_call` carries `results`) vs Anthropic's two separate blocks — fewer `ProviderContent` entries, same opaque-in-order carrier. Hosted tools are a Responses-API concept only (Chat Completions has just client `function` calls → `ToolCall`). Also, encrypted reasoning items that MUST round-trip may belong on the typed `ReasoningContent.providerPayload` rather than `ProviderContent`, per provider. NOTE: there is currently NO native OpenAI path (OpenAI runs through the L4J bridge, which exposes no hosted tools) — this is forward-looking, not in scope now.

## Self-review notes
- Type consistency: `ProviderContent(String provider, String blockType, Object payload, @Nullable Map metadata)`; `@JsonSubTypes` name `"provider"`; SDK mapper = `com.anthropic.core.ObjectMappers.jsonMapper()`; round-trip via `convertValue(payload, BetaContentBlockParam.class)`.
- Risk (Task 2 Step 1): output-block JSON may carry fields the input param rejects → fallback to raw `JsonValue`/`ofFallback`. This is the one spike to run before trusting the generic path.
