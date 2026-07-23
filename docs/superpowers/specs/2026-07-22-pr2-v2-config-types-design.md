# PR2′ — v2 provider config types + job workers + element templates (matrix-free, converter-free)

**Status:** design, ready for implementation
**Date:** 2026-07-22
**Epic:** #7211 own-the-LLM-layer. **Stacks on:** PR1 `#8023` (branch `agentic-ai/issue-8022-chat-model-spi`, tip `0eb5465d75`).
**Branch:** `agentic-ai/v2-config-types` (already created off the PR1 tip).
**Role-model source:** pilot branch `agentic-ai/issue-7211-vertical-pilot` (worktree `…/issue-7211-own-llm-layer`). Reference only — never merged.

---

## 1. Goal

Introduce the **v2 AI Agent** entry points — new job-worker/outbound connector functions, new element
templates, and a new `V2ProviderConfiguration` sealed configuration union (Anthropic + OpenAI + Custom) —
as **config types only**. v2 becomes **selectable and configurable in Modeler**. The two built-in native
providers (Anthropic, OpenAI) are **not runnable** yet: no request converters, and their factories are
deliberately **not registered**, so a v2 job configured for them fails with an explicit
`UnsupportedOperationException` ("not yet implemented"). Native behavior arrives in the stacked
native-provider PRs.

The **Custom** provider (issue #5547) is the exception: it is genuinely runnable in PR2′'s world, because
the *user* implements and registers a `ChatModelApiFactory` and the registry (shipped in PR1) dispatches to
it by `providerType()`. PR2′ adds only the user-facing config member + template entry; the dispatch SPI
already exists.

This PR also performs the deferred **v1 rename** (package `model.request.provider` → `model.request.v1`,
type `ProviderConfiguration` → `V1ProviderConfiguration`) as its first commit, to make room for the
parallel `model.request.v2` world.

Two hard exclusions, both ratified:
- **Matrix-free** — no capability matrix. On the PR1 branch the `aiagent.capabilities` package does not
  even exist, so this is enforced by construction: PR2′ introduces no `ModelCapabilitiesMode` /
  `ModelCapabilitiesOverride` / `capabilityMode` / `capabilityOverride`.
- **Skills/server-tools-free** — no Anthropic Skills, no code-execution/web-search/web-fetch, no OpenAI
  web_search/code_interpreter. Those are deferred to a later chunk (past the native PRs), which also owns
  server-tool version defaults.

## 2. Guiding principle for what to KEEP vs STRIP

Ship config that the **next stacked PRs** (native Anthropic, native OpenAI) will make runnable; strip
config whose behavior lands further out.

- **KEEP** (native PRs consume it immediately): backend discriminator + backends, model id, reasoning
  (Anthropic thinking), effort (both), prompt caching (Anthropic), OpenAI `apiFamily`, max-tokens
  (Anthropic `maxTokens`; OpenAI split `maxCompletionTokens`/`maxOutputTokens`), sampling
  (`temperature`/`topP`/`topK`), endpoints/headers/auth, timeouts.
- **STRIP** (matrix — no near-term consumer, package absent): `capabilityMode`, `capabilityOverride`,
  `V2ProviderConfiguration.capabilityOverride()`, `OpenAiApiFamily.familyKey()` helper.
- **STRIP** (skills/server-tools — behavior PR is further out than the native PRs, owns its own version
  defaults): Anthropic `skills`, `enableCodeExecution`/`codeExecutionVersion`,
  `enableWebSearch`/`webSearchVersion`, `enableWebFetch`/`webFetchVersion`; OpenAI `enableWebSearch`,
  `enableCodeInterpreter`; and the `skills` ("Model Tools") template group.

**Group id rename:** the pilot's template group id `capabilities` (label "Advanced model options") is
renamed to **`model-options`** in PR2′ (the id was a leftover from the matrix work; the label is
unchanged). It holds the token/sampling params only — it is not matrix-related.

## 3. Critical adaptation: pilot SPI naming → PR1-branch SPI naming

The pilot's v2 code was written against a **later** SPI reshape that does **not** exist on the PR1 branch.
Copying verbatim will not compile. Apply these substitutions to every copied v2 file:

| Pilot v2 code (source) | PR1-branch target (this PR) | Why |
|---|---|---|
| `extends ChatModelApiConfiguration` | `extends ChatModelConfiguration` | PR1 tip's marker interface is `ChatModelConfiguration` (`aiagent.chatmodel`); the `…ApiConfiguration` rename is a later pilot commit, out of scope. |
| interface/record accessor `providerType()` | `provider()` | PR1 tip's `ChatModelConfiguration`/v1 union expose `provider()`+`model()`; `providerType()` is the later pilot reshape. The factory-side `providerType()` is unrelated and not touched here. |

Everything else in the v2 config types copies near-verbatim (adjusting only import paths and the two
strips above).

## 4. Starting-state facts (PR1 branch — verified)

- `aiagent/capabilities/` — **absent**. `aiagent/chatmodel/provider/` contains only `langchain4j`
  (no `anthropic`/`openai` native packages; `AnthropicEffort`/`ThinkingMode` do **not** exist).
- `aiagent/model/request/provider/` — the v1 union, 11 files (see §5.1). `model/request/v2/` absent.
- Base SPI: `aiagent/chatmodel/ChatModelConfiguration.java` with `provider()` + `model()`.
- v1 union `ProviderConfiguration extends ChatModelConfiguration`, `@Override provider()`/`model()`,
  `@JsonTypeInfo(Id.NAME, "type")`, 6 subtypes.

Because the native packages and the capabilities package are absent, the two Anthropic enums must be
**defined locally in the `v2` package** (mirroring `OpenAiEffort`, which the pilot already keeps in `v2`).
The stacked native-Anthropic PR will import them from `v2` — the correct direction (config owns the enum,
converter consumes it).

## 5. Work breakdown

### 5.1 Commit 1 — v1 rename (mechanical, behavior-identical)

Rename package `…model.request.provider[.shared]` → `…model.request.v1[.shared]`; rename the union type
`ProviderConfiguration` → `V1ProviderConfiguration` (only the interface file/type name changes; the 6
subtype records and the 2 `shared` helpers keep their simple names, package only).

- **11 files move**: `AnthropicProviderConfiguration`, `AzureOpenAiProviderConfiguration`,
  `BedrockProviderConfiguration`, `GoogleVertexAiProviderConfiguration`,
  `OpenAiCompatibleProviderConfiguration`, `OpenAiProviderConfiguration`, `ProviderConfiguration`(→`V1ProviderConfiguration`),
  `package-info`, `shared/HttpUrl`, `shared/TimeoutConfiguration`, `shared/package-info`.
- **Reference sites**: 25 main files (11 are the moved files) + 11 test files. Update package
  declarations, imports, and every bare `ProviderConfiguration` token (generic bounds, casts,
  `instanceof` patterns, field types in `JobWorkerAgentRequest`/`OutboundConnectorAgentRequest`).
- **Doc prose** (same commit): `docs/adr/009-chat-model-provider-spi.md:64`,
  `docs/reference/ai-agent.md` (lines ~933, 978–979, 1819–1822).
- **Gotchas (verified):**
  - JSON `"type"` discriminator values (`anthropic`/`bedrock`/`azureOpenAi`/`google-vertex-ai`/`openai`/`openaiCompatible`)
    are string constants owned by each subtype — **byte-stable** across the rename. No stored-state or
    element-template break.
  - `ChatModelConfiguration.java:12` has a Javadoc-only `{@code ProviderConfiguration}` — a `mvn compile`
    won't catch it; fix via text pass.
  - `ProviderConfigurationTest.java` (stays in `…model.request`, not moved) has 6 self-referential
    `@MethodSource` FQCN string literals to **its own** class name — do **not** rewrite those (the test
    class is not renamed in this PR).
  - Do **NOT** carry the pilot's later `provider()`→`providerType()` or
    `ChatModelConfiguration`→`ChatModelApiConfiguration` reshape. Pure package/type rename only.
- **Exit:** module compiles, full module test suite green, element templates regenerate byte-identical
  (discriminator strings unchanged). Zero behavior change.

### 5.2 Commit 2 — v2 config types (`model/request/v2/`)

Create the `v2` package by copying the pilot's v2 config types, applying §3 substitutions and the §2
strips. Files:

- `V2ProviderConfiguration.java` — sealed union `extends ChatModelConfiguration permits AnthropicChatModel,
  OpenAiChatModel, CustomProviderConfiguration`; `@JsonTypeInfo(Id.NAME,"type")` + `@JsonSubTypes`
  (`anthropic`/`openai`/`custom`); `@TemplateDiscriminatorProperty(group="provider")`. Methods: `provider()`,
  `model()`, `backend()`. **No `capabilityOverride()`.** (`CustomProviderConfiguration` is added in commit 5,
  §5.5 — the union's `permits` gains it then; commits 2–4 keep the two-member union and commit 5 widens it.)
- `AnthropicChatModel.java` — wraps `AnthropicConnection anthropic`. KEEP: nested `AnthropicBackend`
  sealed union **`direct` + `compatible` only** (`direct`=`AnthropicDirectBackend(apiKey)`,
  `compatible`=`AnthropicCompatibleBackend(endpoint, headers?, queryParameters?, requestParameters?,
  compatibleAuthentication)`). **DROP the `bedrock` member** (`AnthropicBedrockBackend`, its `@AssertFalse`
  SaaS check, and the `ChatModelAwsAuthentication` type) — Anthropic Bedrock is deferred (see §8); it stays
  on the pilot. `enablePromptCaching`
  (group `model`); nested `AnthropicModel(model, parameters)`; `AnthropicModelParameters(maxTokens,
  temperature, topP, topK, effort, thinking)` — `maxTokens`/`temperature`/`topP`/`topK` carry
  `group="model-options"` (renamed from `capabilities`), `effort`/`thinking` carry `group="model"`;
  `AnthropicThinking(mode, budgetTokens, display)` with the locally-nested `ThinkingDisplay` enum. **STRIP:** `capabilityMode`, `capabilityOverride`, and all skills
  fields (`skills`, `enableCodeExecution`, `codeExecutionVersion`, `enableWebSearch`, `webSearchVersion`,
  `enableWebFetch`, `webFetchVersion`).
- `OpenAiChatModel.java` — wraps `OpenAiConnection openai`. KEEP: `apiFamily` (group `provider`), nested
  `OpenAiBackend` sealed union (`direct`=`OpenAiDirectBackend(apiKey, organizationId?, projectId?)`,
  `compatible`=`OpenAiCompatibleBackend(endpoint, headers?, queryParameters?, requestParameters?,
  authentication)`); nested `OpenAiModel(model, parameters)`; `OpenAiModelParameters(maxCompletionTokens,
  maxOutputTokens, temperature, topP, effort)` — `maxCompletionTokens`/`maxOutputTokens`/`temperature`/`topP`
  carry `group="model-options"` (renamed from `capabilities`), `effort` carries `group="model"`. **STRIP:**
  `capabilityMode`, `capabilityOverride`, `enableWebSearch`, `enableCodeInterpreter`. Drop the matrix-framed
  `apiFamilyKey()` accessor.
- `OpenAiApiFamily.java` — enum `COMPLETIONS`/`RESPONSES` (`@JsonProperty` `completions`/`responses`).
  **STRIP** the `familyKey` field + `familyKey()` accessor (matrix-only) → bare 2-value enum.
- `OpenAiEffort.java` — enum `minimal/low/medium/high/xhigh/max` (already no `custom`). Copy as-is.
- **`AnthropicEffort.java`** (NEW, local to `v2`) — enum `low/medium/high/xhigh/max` (`@JsonProperty`
  lowercase). Ported from the pilot's native package, which does not exist here.
- **`ThinkingMode.java`** (NEW, local to `v2`) — enum `enabled/adaptive/disabled`. Same reason.
- `shared/CompatibleAuthentication.java` — sealed `none`/`apiKey`, copy as-is (self-contained).
- `package-info.java` + `shared/package-info.java` — `@NullMarked` markers.
- **Do NOT copy `shared/ChatModelAwsAuthentication.java`** — it is only used by the deferred Anthropic
  Bedrock backend (§8). It stays on the pilot.

**Naming asymmetry (intentional, preserve):** Anthropic's compatible-backend auth field is
`compatibleAuthentication`; OpenAI's is `authentication`. Both are `CompatibleAuthentication`. The template
generator derives nested property ids from the Java field name, and `authentication` on the Anthropic side
would collide with the stable `AnthropicBedrockBackend.authentication` id — so the asymmetry is required.

`timeouts`/`HttpUrl` on both providers reuse `…model.request.v1.shared.*` (after commit 1's rename).

### 5.3 Commit 3 — v2 workers + requests + wiring

- `AiAgentTaskV2Function.java` — copy pilot; `@OutboundConnector(type="io.camunda.agenticai:aiagent:task:2")`,
  `@ElementTemplate(id="io.camunda.connectors.agenticai.ai-agent-task.v2", version=1, engineVersion="^8.10",
  inputDataClass=OutboundConnectorAgentRequestV2.class, …)`. **`propertyGroups` = pilot minus the `skills`
  ("Model Tools") group, with the `capabilities` group id renamed to `model-options`** (label stays
  "Advanced model options" — it holds token/sampling params, not matrix). Same constructor/`execute()`
  shape as v1 `AiAgentFunction`.
- `AiAgentSubProcessV2Function.java` — copy pilot; `JOB_WORKER_TYPE="io.camunda.agenticai:aiagent:subprocess:2"`,
  name "AI Agent Sub-process"; no `@ElementTemplate` (its template is derived by the groovy transform).
- `JobWorkerAgentRequestV2.java` / `OutboundConnectorAgentRequestV2.java` — copy pilot; these are the v1
  request records with only the `provider` field re-typed to `V2ProviderConfiguration`. Reuse the shared
  `…RequestData` types verbatim (do not duplicate).
- Execution contexts (`JobWorkerAgentExecutionContext`, `OutboundConnectorAgentExecutionContext`) —
  **already version-agnostic on the PR1 branch; no changes.**
- `AgenticAiConnectorsAutoConfiguration.java` — add the two v2 `@Bean` methods (`aiAgentTaskV2Function`,
  `aiAgentSubProcessV2Function`) with `@ConditionalOnBean(OutboundConnectorAgentRequestHandler.class)` /
  `@ConditionalOnBean(JobWorkerAgentRequestHandler.class)` and feature toggles
  `camunda.connector.agenticai.aiagent.task-v2.enabled` / `subprocess-v2.enabled` (`matchIfMissing=true`).
  Also register the not-yet-implemented placeholder factory bean (below). **Do NOT add** any
  `chatmodel/provider/{anthropic,openai}` `@Import` (those packages don't exist here yet).
- **Not-runnable behavior — explicit "not implemented" (per user):** do NOT rely on the generic
  registry "no factory" error (`ERROR_CODE_FAILED_MODEL_CALL`), which is semantically wrong for
  "unbuilt". Instead add a placeholder factory:
  - **`NotImplementedNativeChatModelApiFactory`** (new class, package `aiagent/chatmodel/`) implements
    `ChatModelApiFactory`. `supports(cfg)` returns true **only** for the two built-in native v2 types
    (`cfg instanceof AnthropicChatModel || cfg instanceof OpenAiChatModel`). `create(cfg)` throws
    `new UnsupportedOperationException("Native <provider> chat model is not yet implemented")`.
  - Registered as an unconditional `@Bean` in the autoconfiguration. On a v2 Anthropic/OpenAI job,
    `ChatModelApiRegistryImpl.resolve()` finds exactly this one factory and `create()` throws the
    explicit not-implemented error — surfaced as a normal BPMN incident, not a wiring-time crash.
  - **It must NOT match `CustomProviderConfiguration`** — custom is resolved by the user's own factory
    (§5.5). It must NOT match `V1ProviderConfiguration` (v1 is served by the L4J factory).
  - **Native-PR handoff (document in the class Javadoc):** when the native Anthropic PR registers the
    real `AnthropicChatModelApiFactory`, it must drop `AnthropicChatModel` from this placeholder's
    `supports()` (else `resolve()` reports "multiple factories match"); likewise native OpenAI drops
    `OpenAiChatModel`. When both are removed, the placeholder class is deleted.
  - The v1 `Langchain4JChatModelApiFactory.supports()` only matches `V1ProviderConfiguration`, so it can
    never accidentally satisfy a v2 request.

### 5.4 Commit 4 — generate + commit v2 element templates

Regenerate via `mvn clean compile -f connectors/agentic-ai/pom.xml` (element-template-generator-maven-plugin
generates the Task v2 JSON from annotations; the `gmavenplus`+`bin/transform-ai-agent-template.groovy` step
derives the Sub-process v2 JSON — the transform is generic, no change needed). Add the pom.xml `<connector>`
entry + the two groovy `<execution>` blocks (task + sub-process, each with a hybrid variant), mirroring the
pilot's pom wiring. Commit the 4 generated JSONs:
`element-templates/agenticai-ai-agent-task.v2.json`, `…-subprocess.v2.json`, and their `hybrid/` siblings.

Node/`element-templates-cli` is **not** needed for generation (pure Maven); it's only needed to *apply*
templates in the e2e harness, which this PR does not run.

**Expected template shape:** groups `provider → model → "Advanced model options" → systemPrompt →
userPrompt → tools → memory → limits → response` (Sub-process adds `events` after `limits`). No "Model
Tools" group, no "Model capabilities" dropdown, no capability-override field. (Commit 5 adds the "Custom
Implementation" provider option to the `provider` group's discriminator; templates are regenerated there.)

### 5.5 Commit 5 — custom provider configuration (advances #5547)

Mirror the custom-conversation-memory pattern (`MemoryStorageConfiguration.CustomMemoryStorageConfiguration`).
Not in the pilot — net-new, but a well-worn shape. The dispatch SPI (`ChatModelApiFactory<C>` resolving by
`providerType()`) already shipped in PR1; this commit only adds the user-facing config member + template
entry. No example factory, no runtime code.

- **New `CustomProviderConfiguration.java`** (`model/request/v2/`), a third `V2ProviderConfiguration` member:
  ```java
  @TemplateSubType(id = CUSTOM_ID, label = "Custom Implementation (Self-Managed/Hybrid only)")
  record CustomProviderConfiguration(
      String providerType,          // user-chosen discriminator; dispatches to their factory bean
      String model,                 // dedicated field — feeds the agent-instance history/reporting API
      Map<String, Object> parameters // opaque config consumed by the user's factory
  ) implements V2ProviderConfiguration { ... }
  ```
  - `provider()` returns `providerType`; `model()` returns the dedicated `model` field (NOT dug from
    `parameters` — reporting reads `chatModel().model()`, per the user); `backend()` returns `null`.
  - JSON discriminator `type = "custom"` (a new `CUSTOM_ID = "custom"` constant), added to
    `V2ProviderConfiguration`'s `@JsonSubTypes` and `permits` in this commit.
  - `@TemplateProperty` fields: `providerType` (String/FEEL, "Provider type", required — the discriminator
    the user's `ChatModelApiFactory.providerType()` returns), `model` (String/FEEL, "Model"),
    `parameters` (Text/FEEL, "Provider parameters", optional, a `Map` — mirror custom memory's
    `customProperties` shape).
  - **Self-Managed/Hybrid gating:** the "Custom Implementation" entry must be marked Self-Managed/Hybrid
    only, matching the pattern used elsewhere (e.g. AWS default-credentials-chain label + hybrid-template
    handling). Confirm how the custom-memory / default-chain entries express this and reuse the same
    mechanism (label suffix and/or hybrid-only template variant) — do not invent a new one.
- **Widen `V2ProviderConfiguration`**: add `CustomProviderConfiguration` to `permits` and `@JsonSubTypes`
  (`custom`). The `NotImplementedNativeChatModelApiFactory` from commit 3 stays unchanged — it matches only
  `AnthropicChatModel`/`OpenAiChatModel`, so custom is never swallowed by it; an unregistered custom factory
  yields the normal "no factory registered" error, which is the correct signal (user must register theirs).
- **Regenerate templates** (`mvn clean compile …`): the provider discriminator now offers Anthropic /
  OpenAI / Custom; commit the regenerated 4 JSONs.
- **Docs:** a short section in `docs/reference/ai-agent.md` on the custom provider — implement
  `ChatModelApiFactory` (or register the L4J bridge factory under your `providerType`) and register it as a
  Spring bean; the registry dispatches by `providerType()`. No example module (out of scope per #5547).

## 6. Testing

- **Serialization/validation unit tests** for `V2ProviderConfiguration` and all three members:
  discriminator round-trip (`type`=`anthropic`/`openai`/`custom`), backend sub-union round-trip
  (Anthropic direct/compatible; OpenAI direct/compatible), auth redaction in `toString()`, the
  effort/thinking/apiFamily enums, and the custom member
  (`providerType`/`model`/`parameters` round-trip, `provider()`/`model()`/`backend()==null`). Mirror the
  pilot's v2 config tests (stripped of skills/matrix cases).
- **Not-runnable test** (native): assert `NotImplementedNativeChatModelApiFactory.supports()` is true for a
  v2 `AnthropicChatModel`/`OpenAiChatModel` and false for `CustomProviderConfiguration`/`V1ProviderConfiguration`,
  and that `create()` throws `UnsupportedOperationException`. In `AgenticAiConnectorsAutoConfigurationTest`
  assert the v2 function beans exist and that resolving a native v2 config throws
  `UnsupportedOperationException` via `ChatModelApiRegistryImpl.resolve(...).create(...)`.
- **Custom-runnable test:** with a stub `ChatModelApiFactory` bean returning a matching `providerType()`,
  a `CustomProviderConfiguration` resolves to it (proves the user-supplied path works); with no such bean,
  `resolve()` yields the normal "no factory registered" error.
- **Rename regression:** the existing full module suite must stay green after commit 1 with no test-logic
  changes beyond import/type renames.
- **Element-template assertion:** whatever template-diff/verification test the module already runs must
  pass for the new v2 JSONs.
- No real-API e2e. No `RUN_NATIVE_LLM_E2E`.

## 7. Global constraints

- Java 21. Build/test the **module only**: `mvn -pl connectors/agentic-ai/connector-agentic-ai …`. **Never
  `-am`** (rewrites ~1500 proprietary license headers to Apache). Never run repo-wide `license:format`/spotless.
- Proprietary license headers: never touch/reformat.
- Commit messages describe the actual change only — no PR/review/round/pilot/superpowers references.
- Subagents only `git add`/`commit` their own work — never checkout/reset/revert/rebase/stash/push.
- No local paths / secrets / env-loader names in any committed file.
- Run Maven/git unsandboxed (Mockito breaks in the sandbox); read surefire XML for pass/fail, not stdout.
- **Do not push / open the PR without explicit user approval.** The PR gets its own sub-issue under #7211.
- Superpowers docs (this spec, the plan, the handoff) are scaffolding — they must **not** travel into the
  final PR history (mirror PR1: working docs stayed off-branch).

## 8. Out of scope (deferred, do not build)

Capability matrix + multimodal routing (later epic); request converters + native factories (native
Anthropic / native OpenAI PRs, stacked next); **Anthropic Bedrock backend — both the config type
(`AnthropicBedrockBackend` + `ChatModelAwsAuthentication`) AND the runtime — deferred out of PR2′; it stays
on the pilot and returns in a later chunk**; Anthropic Skills + server tools + server-tool versioning;
OpenAI server tools; streaming SPI decision; v1/v2 config merge; renaming `ProviderConfigurationTest`. **Custom provider:** no example factory and no separate LangChain4j-bridge
module (the bridge module is explicitly out of scope on #5547's parent epic) — PR2′ ships only the config
type + template + docs.

## 9. Commit plan (coherent history)

1. `refactor(agentic-ai): rename the v1 provider configuration package and type` — §5.1
2. `feat(agentic-ai): add v2 provider configuration types` — §5.2
3. `feat(agentic-ai): add v2 AI Agent task and sub-process connectors` — §5.3 (incl. the not-implemented
   placeholder factory)
4. `build(agentic-ai): generate v2 AI Agent element templates` — §5.4
5. `feat(agentic-ai): add custom model provider configuration` — §5.5 (advances #5547)

(Exact wording at the implementer's discretion within these constraints; each commit compiles + tests green.)
