# C7 extension: Anthropic Skills + built-in tool toggles (strategy A)

**Goal:** Add Anthropic API Skills support and code-execution/web-search/web-fetch tool toggles to the native Anthropic path, by migrating that path from the stable messages client to the **beta** messages client (strategy A). Merged INTO C7 (commits on top of tip `6b2f9b9a18`); reviewed as part of the whole C7 block.

**Beta-parity VERIFIED (2026-07-13, driver, vs anthropic-java-core 2.48.0 jar):** the beta type family has full equivalents — `com.anthropic.models.beta.messages.MessageCreateParams` (builder: `betas`/`addBeta`, `container(BetaContainerParams)`, `outputConfig(BetaOutputConfig)`, `addTool(BetaToolUnion|BetaTool|BetaCodeExecutionTool20250825|BetaWebSearchTool*|BetaWebFetchTool*)`, `system(String)`, `addMessage(BetaMessageParam)`, `maxTokens(long)`, `temperature/topP/topK`), `BetaMessage`, `BetaStopReason.PAUSE_TURN`, `BetaUsage` (inputTokens/outputTokens/cacheCreationInputTokens/cacheReadInputTokens/outputTokensDetails/serverToolUse), `BetaOutputConfig`+`BetaJsonOutputFormat`, `BetaThinkingBlock`/`BetaRedactedThinkingBlock`, `BetaTextBlock`/`BetaToolUseBlock`, `BetaContentBlockParam`/`BetaMessageParam`, `BetaContainerParams`+`BetaSkillParams`, and `BetaRawMessageStreamEvent` + `BetaRawMessageStartEvent`/`BetaRawContentBlockStartEvent`/`BetaRawContentBlockDeltaEvent`/`BetaRawContentBlockStopEvent`/`BetaRawMessageDeltaEvent`/`BetaRawMessageStopEvent` for the SSE stub. Beta accumulator: `com.anthropic.helpers.BetaMessageAccumulator`. Beta call: `client.beta().messages().createStreaming(params) -> StreamResponse<BetaRawMessageStreamEvent>`.

**Global constraints (from the C7 chunk, still binding):**
- BC on 8.9-persisted data #1: additive only; touch no persisted/serialized domain type. Skills/tools config lives on the transient v2 request config, not on persisted AgentContext.
- v1 (L4J bridge) byte-identical; native path engages only for v2 `LlmProviderChatModelApiConfiguration` + anthropic + DIRECT backend.
- Model-call failures -> `ERROR_CODE_FAILED_MODEL_CALL`; request-mapping errors propagate unwrapped (only the vendor call is wrapped). Client built per call, closed in finally without masking the primary exception.
- SPI unchanged: provider built-in tools/skills are framework-local to `framework/anthropic/**`; do NOT add built-in-tool concepts to `ChatModelRequest`/`ConversationSnapshot`/`ToolDefinition`.
- Element templates are generated (`mvn clean compile`) + v2 subprocess via the groovy transform; commit the regenerated JSON.
- @NullMarked module.

**Skill string construct** (`type:skill:version`, split on `:`):
- 1 token `"pptx"` -> (anthropic, pptx, latest)
- 2 tokens `"pptx:v"` -> (anthropic, pptx, v)
- 3 tokens `"custom:my-skill:v"` -> (custom, my-skill, v)
- 2 tokens where token0 is a known type? NO — keep it positional by COUNT: 2 tokens is always (anthropic, token0, token1). `"custom:my-skill"` is 2 tokens -> would wrongly parse as (anthropic, custom, my-skill). RESOLVE: treat token0 as a TYPE only when there are 3 tokens; with 2 tokens, if token0 is exactly `custom` or `anthropic` treat as (type, skill, latest), else (anthropic, token0, token1). Document this rule in the parser + test both `"pptx:special-version"` and `"custom:my-skill"`. (Confirm this disambiguation with the user if it feels ambiguous during impl — it matches the examples they gave.)

Type maps to `BetaSkillParams.Type.ANTHROPIC` / `.CUSTOM`; version "latest" passed through as the literal string `"latest"`.

---

## Task A: Migrate the native Anthropic path to the beta messages client (behavior-identical)

**Files (all under `connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/anthropic/`):**
- `AnthropicContentConverter.java` — `ContentBlockParam*`/`ToolResultBlockParam`/`ImageBlockParam`/`DocumentBlockParam`/`TextBlockParam`/`ToolUseBlockParam` -> `Beta*` equivalents.
- `AnthropicMessageRequestConverter.java` — `MessageCreateParams`/`MessageParam`/`Tool`/`OutputConfig`/`JsonOutputFormat`/`System` -> beta.
- `AnthropicMessageResponseConverter.java` — consumes `Message` -> `BetaMessage`; `ContentBlock`/`TextBlock`/`ToolUseBlock`/`ThinkingBlock`/`RedactedThinkingBlock`/`StopReason`/`Usage` -> `Beta*`.
- `AnthropicMessageStreamAssembler.java` — `RawMessageStreamEvent`/`MessageAccumulator`/`Message` -> `BetaRawMessageStreamEvent`/`BetaMessageAccumulator`/`BetaMessage`.
- `AnthropicChatModelApi.java` — call site `client.messages().createStreaming(...)` -> `client.beta().messages().createStreaming(...)`; `StreamResponse<RawMessageStreamEvent>` -> `<BetaRawMessageStreamEvent>`.
- Their unit tests: update fixtures to build `Beta*` messages (via `ObjectMappers.jsonMapper().readValue` as C7 does).
- e2e: `NativeAnthropicMessagesSseChatModelStubs.java` — SSE stub events -> `BetaRaw*` event types.

**Method:** for EACH `com.anthropic.models.messages.X` currently referenced, find its `com.anthropic.models.beta.messages.BetaX` equivalent and VERIFY every accessor/factory/builder method via `javap -cp <core jar> <FQN>` before using it — do not assume names. No behavior change, no new features. `pause_turn` handling via `BetaStopReason.value()` sentinel (mirror C7's non-beta `.value()` approach; `.known()` throws on unknown).

**Done when:** module builds; all pre-existing anthropic unit tests pass unchanged in intent; `ProviderWireFormatSmokeTests` stays 20/20 (NativeAnthropicMessages still 4/4 on beta SSE events). One commit: "Migrate native Anthropic path to the beta messages client".

## Task B: Skills support

**Files:**
- Config: `.../aiagent/model/request/chatmodel/AnthropicChatModel.java` — add `List<String> skills` to `AnthropicConnection` (FEEL, `@FEEL @TemplateProperty(group=... type=Text, feel=required, optional=true)`; precedent `McpClientToolsFilterConfiguration`). Group: a new tools/skills group.
- New parser `AnthropicSkillReference` (record `(BetaSkillParams.Type type, String skillId, String version)` + `static parse(String)`), framework-local. Unit test the 4 example forms + the 2-token disambiguation.
- `AnthropicMessageRequestConverter` — when skills non-empty: `.container(BetaContainerParams.builder().addSkill(...).build())`, ADD the `code_execution` tool (`BetaCodeExecutionTool20250825`, name `code_execution`) if not already added, and `.addBeta("code-execution-2025-08-25").addBeta("skills-2025-10-02").addBeta("files-api-2025-04-14")`. Cap at 8 skills (fail loud or log+truncate — pick fail-loud with a clear message).
- Unit tests: request converter emits container.skills + code_execution tool + betas when skills set; none when empty.
- Regenerate templates (`mvn clean compile`), commit JSON diff, update element-templates README if a version bumps (it won't — property add only).

**Done when:** unit tests green; templates regenerate cleanly. One commit: "Add Anthropic Skills support via the beta container".

## Task C: Built-in tool toggles

**Files:**
- Config: booleans on `AnthropicConnection` — `enableCodeExecution`, `enableWebSearch`, `enableWebFetch` (`@TemplateProperty(group=..., type=Boolean, optional=true)`, default false). (Confirm exact grouping/labels; a nested `tools` record is acceptable if cleaner.)
- `AnthropicMessageRequestConverter` — add `BetaCodeExecutionTool20250825` / `BetaWebSearchTool*` / `BetaWebFetchTool*` when the matching toggle is true; dedupe code_execution against the skills auto-add; add each tool's required beta header via `.addBeta(...)` (verify the exact beta identifier per tool from the SDK `AnthropicBeta` enum / docs; web search/fetch beta ids not in the skills doc — check `com.anthropic.models.beta.AnthropicBeta` constants).
- Unit tests: each toggle adds its tool + header; skills+codeExec dedupe.
- Regenerate templates, commit JSON.

**Done when:** unit tests green; templates regenerate. One commit: "Add code-execution/web-search/web-fetch tool toggles".

## Task D: e2e smoke

**Files:** `connectors-e2e-test/.../wiremock/anthropic/` + `ProviderWireFormatSmokeTests`.
- Add a scenario (or extend the native fixture) that configures skills + a tool toggle on the v2 template and asserts the recorded request carries `container.skills[*]`, the `code_execution` tool, and the `anthropic-beta` header(s). Response via the beta SSE stub (already beta after Task A).
- Full `ProviderWireFormatSmokeTests` green (all rows).

**Done when:** e2e green. One commit: "Cover Anthropic Skills + tools in the native wire-format e2e".

---

## Sequencing
A (foundation, behavior-identical) -> B (skills, #1) -> C (toggles, #2) -> D (e2e). Verify each task's unit tests before the next; run the full e2e suite ONCE at the end (Task D) per the batch-testing rule. Driver amends review fixes; implementers only `git add`+`git commit`. Do NOT push.
