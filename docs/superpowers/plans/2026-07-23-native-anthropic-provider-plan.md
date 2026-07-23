# Native Anthropic Provider Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring a native Anthropic Messages chat-model provider live on the v2 own-LLM-layer path, at feature parity with today's langchain4j (L4J) Anthropic path plus reasoning/effort and prompt caching — no server tools, Skills, or capability matrix.

**Architecture:** The v2 `ProviderConfiguration` sealed union gains an `AnthropicChatModelConfiguration` member with two backends (`anthropic-api`, `compatible`). A native `ChatModelApi` implementation (Anthropic Java SDK, not L4J) is registered via a factory so the existing dispatch resolves it. Request/response converters translate between the provider-neutral domain model and the Anthropic SDK types. Capability infrastructure is absent (deferred); multimodal is handled exactly as today (native user-message blocks + the existing unconditional `<doc/>` tool-result extraction).

**Tech Stack:** Java 21, Spring Boot autoconfiguration, Anthropic Java SDK (beta Messages client, OkHttp transport), Jackson (sealed-union polymorphic config), JUnit 5 + Mockito + AssertJ, WireMock (deterministic wire-format e2e), element-template generator (`process-classes` phase).

**Design doc:** `docs/superpowers/specs/2026-07-23-native-anthropic-provider-design.md` — read it first for scope rationale and deferred-item boundaries.

**This is a PORT plan.** The source of truth for each ported class is the named file on the pilot branch (`agentic-ai/issue-7211-vertical-pilot`), reachable in this repo at the given path. Each task names the port-source file, the target file, and the exact adaptations (what to strip/rename). Do NOT cherry-pick — rebuild by hand, because the target branch's package layout and config structure have diverged from the reference. Reproduce ported logic faithfully; only the adaptations listed here change behaviour.

## Global Constraints

- **Stacks on** `agentic-ai/v2-config-types` (NOT directly on the SPI PR). Work in a fresh worktree/branch off that branch's tip. Stop before any push.
- **Backends this PR:** `anthropic-api` (direct) + `compatible` only. Do NOT port Bedrock / AWS-auth / Azure / Vertex backends. Cloud backends are deferred.
- **Compatible auth:** `None` + `ApiKey` only. Do NOT add OAuth 2.0 (deferred).
- **No capability subsystem.** Drop every `*Capabilities*` / `*CapabilitiesData` / `AnthropicReasoningValidator`'s matrix-coupled parts / `AnthropicModelCapabilities*` / `AnthropicProviderCapabilities` / `AnthropicReasoningCapabilities`. The `ChatModelApi` implementation supplies NO `ModelCapabilities`.
- **No server tools, no Skills.** Drop `AnthropicSkillReference` and all request-side server-tool/Skills mapping.
- **Reasoning/effort:** ships WITHOUT matrix-driven validation. Preserve only matrix-independent structural checks (if any). Invalid combos fail loud at the Anthropic API and surface via the existing error mapping.
- **SDK client: prefer STABLE, avoid beta unless necessary.** The pilot used the Anthropic **beta** Messages client/types (`BetaMessage`, `BetaContentBlock`, `BetaStopReason`, `BetaUsage`, `BetaToolUseBlock`, …) **solely for Skills / server-tools** — both omitted here. Determine (Task 0 §6) whether the shipped surface (reasoning/thinking, effort, prompt caching, tool calling, multimodal) is fully supported on the **stable** Messages client. If yes, use stable types throughout and drop beta-only handling. Use beta ONLY for a shipped feature that genuinely requires it; if one does, note which and keep the whole path on beta (do not mix clients).
- **Config structure authority = the bak branch's refined config**, NOT the pilot worktree's `AnthropicChatModel.java`. Source: `git show agentic-ai/v2-config-types-bak-with-providers:connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/model/request/v2/AnthropicChatModelConfiguration.java` (+ `AnthropicEffort.java`, `ThinkingMode.java`, `shared/CompatibleAuthentication.java`). It already has the refined shape: model params (`maxTokens`/`temperature`/`topP`/`topK`) in the **`model-options`** group; **direct + compatible backends only (no Bedrock)**; no skills/capabilities groups; thinking in the `model` group. Take THIS structure. Adapt it to the target branch's union (bare `ProviderConfiguration` — Task 0 §4), the current rename scheme, our decided backend discriminators (**`anthropic-api`** + **`compatible`**), and provider label **"Anthropic"**. Do NOT reproduce the pilot worktree's older `capabilities`-group layout or its Bedrock/skills members.
- **`pause_turn`:** conditional on the SDK-client choice — keep the `pause_turn` → `ChatModelResult.Continuation` mapping only if the path stays on beta types. If on the stable client and its `StopReason` has no `pause_turn`, drop that branch (it only fires with the deferred server tools, so it cannot occur here).
- **Factory MUST be registered** in autoconfiguration (the v2-config-types PR left factories unregistered).
- **Rename scheme:** bare `ProviderConfiguration` in the versioned package (no `V2` type prefix); `Ai` prefix → `Agent`; `V1`/`V2` suffix only on connector `Function` + `Request` classes. Match whatever the target branch already uses; verify in Task 0. Connector type-ID strings are the external contract — never change them.
- **Build:** NEVER use `-am` (rewrites proprietary license headers reactor-wide). Regenerate templates with `mvn clean process-classes` (NOT `compile`). After model/serialization changes, cross-compile and run the separate `connectors-e2e-test-agentic-ai` module (install-to-m2 + `-pl`, no `-am`).
- **Real-API IT:** compile only. Do NOT run any real-API e2e (no `RUN_NATIVE_LLM_E2E`, no real keys) — the driver requests per-run authorization from the user.
- **Subagents:** only `git add`/`commit` your own work. Never `checkout`/`reset`/`revert`/`rebase`/`stash`/`push`. Commit messages describe the change only — no plan/review/round/pilot/superpowers wording.

---

## File Structure

**Port source → target** (all under `connectors/agentic-ai/connector-agentic-ai/src/…/io/camunda/connector/agenticai/aiagent/`). Target names follow the rename scheme confirmed in Task 0.

| Port source (pilot) | Target | Action |
|---|---|---|
| **bak branch** `AnthropicChatModelConfiguration.java` (refined structure — see Global Constraints) | `model/request/v2/AnthropicChatModelConfiguration.java` | REBUILD from the bak branch's refined config: model params in the **`model-options`** group, `anthropic-api`+`compatible` backends (already no Bedrock there), `api` enum, reasoning/effort/thinking + caching. Adapt union name (bare `ProviderConfiguration`), discriminators, label "Anthropic". NOT the pilot worktree's `AnthropicChatModel.java`. |
| `model/request/v2/shared/CompatibleAuthentication.java` | same (v2/shared) | PORT as-is (already `None`+`ApiKey` only) |
| `model/request/v2/shared/ChatModelAwsAuthentication.java` | — | DROP (Bedrock deferred) |
| `chatmodel/provider/anthropic/AnthropicClientFactory.java` | same | PORT |
| `chatmodel/provider/anthropic/AnthropicOkHttpClientFactory.java` | same | PORT; ADAPT: direct + compatible only, drop Bedrock branch |
| `chatmodel/provider/anthropic/AnthropicContentConverter.java` | same | PORT (multimodal user input, reasoning round-trip, ProviderContent passthrough) |
| `chatmodel/provider/anthropic/AnthropicMessageRequestConverter.java` | same | PORT; ADAPT: drop server-tool/Skills request mapping; keep reasoning/effort/caching mapping |
| `chatmodel/provider/anthropic/AnthropicMessageResponseConverter.java` | same | PORT; keep pause_turn→Continuation, ReasoningContent, ProviderContent passthrough, error mappings |
| `chatmodel/provider/anthropic/AnthropicMessageStreamAssembler.java` | same | PORT (native streamable consumption → `BetaMessage`) |
| `chatmodel/provider/anthropic/AnthropicEffort.java`, `ThinkingMode.java` | same | PORT (reasoning config enums) |
| `chatmodel/provider/anthropic/AnthropicChatModelApi.java` | same | PORT; ADAPT: remove `capabilities()` + capability field/wiring |
| `chatmodel/provider/anthropic/AnthropicChatModelApiFactory.java` | same | PORT; ADAPT: build without capability resolver; recognise `AnthropicChatModelConfiguration` |
| `chatmodel/provider/anthropic/configuration/AgenticAiAnthropicProviderConfiguration.java` | same | PORT; ADAPT: register the factory bean; drop capability beans |
| `AnthropicReasoningValidator.java` | (split) | ADAPT: keep only matrix-independent structural checks; drop capability-driven checks. If nothing structural remains, drop the file. |
| `AnthropicModelCapabilities*.java`, `AnthropicProviderCapabilities.java`, `AnthropicReasoningCapabilities.java`, `AnthropicSkillReference.java` | — | DROP (deferred) |

**Test port source → target** (`src/test/…/chatmodel/provider/anthropic/`): port `AnthropicContentConverterTest`, `AnthropicMessageRequestConverterTest`, `AnthropicMessageResponseConverterTest`, `AnthropicMessageStreamAssemblerTest`, `AnthropicChatModelApiTest`, `AnthropicChatModelApiFactoryTest` — trimming any server-tools/Skills/capability cases. DROP `AnthropicModelCapabilitiesTest`, `AnthropicReasoningValidatorTest` (unless structural checks survived, then keep the structural cases only), `AnthropicSkillReferenceTest`.

---

## Task 0: Branch reconnaissance (verification, no production code)

**Files:** none created. Produce a written findings note committed as `docs/superpowers/specs/2026-07-23-native-anthropic-task0-findings.md`.

- [ ] **Step 1: Confirm SPI shape.** On the worktree branch, read `chatmodel/ChatModelApi.java`. Record whether it declares a `capabilities()` method. Expected: it does NOT (capabilities deferred). If it DOES, STOP and escalate — the plan's "supply no capabilities" assumption is void.

- [ ] **Step 2: Confirm `<doc/>` extraction present.** Locate the unconditional tool-call-result document extraction on the branch (the ADR-004 behaviour: strip document from tool result → synthetic `UserMessage`). Record the class + method. Confirm it is NOT gated by a `ModelCapabilities`.

- [ ] **Step 3: Record the v1 Anthropic parameter surface.** Read the v1 Anthropic provider config / template. List every model parameter it exposes (temperature, topP, topK, maxTokens, stopSequences, etc.). This list is the acceptance checklist for Task 4.

- [ ] **Step 4: Record the target rename scheme + current v2 union shape.** Read the current v2 provider-config union on the branch (its bare-`ProviderConfiguration` name, package, discriminator property, existing `permits`/`@JsonSubTypes`, and the `CustomProviderConfiguration` member). Record exact names so later tasks widen the real union, not the pilot's `V2ProviderConfiguration`.

- [ ] **Step 5: SDK client selection spike.** Determine whether the Anthropic Java SDK's **stable** Messages client + types (`MessageCreateParams`, `Message`, `ContentBlock`, `StopReason`, `Usage`) support every shipped feature: reasoning/thinking, effort, prompt caching (`cache_control`), tool calling, and multimodal input. Record, per feature, stable-vs-beta support. Decide the client for the whole path: **stable if all shipped features are supported there**; beta only if a shipped feature genuinely requires it (note which). Also record whether the stable `StopReason` enum contains `pause_turn` (governs the Task 5 pause_turn branch). The pilot's beta usage existed only for Skills/server-tools (omitted).

- [ ] **Step 6: Commit the findings note.**

```bash
git add docs/superpowers/specs/2026-07-23-native-anthropic-task0-findings.md
git commit -m "docs(agentic-ai): record native Anthropic branch reconnaissance findings"
```

---

## Task 1: v2 Anthropic configuration type

**Files:**
- Create: `…/model/request/v2/AnthropicChatModelConfiguration.java`
- Create/port: `…/model/request/v2/shared/CompatibleAuthentication.java` (if not already present on the branch)
- Modify: the v2 `ProviderConfiguration` sealed union (name/path from Task 0) — widen `permits` + `@JsonSubTypes`
- Port: `…/model/request/v2/AnthropicEffort.java` equivalent + `ThinkingMode.java` (reasoning enums; place per the pilot's package)
- Test: `…/model/request/v2/AnthropicChatModelConfigurationTest.java` (new)

**Interfaces:**
- Consumes: the v2 `ProviderConfiguration` sealed interface (Task 0 §4), `ChatModelConfiguration` SPI (`provider()`, `model()`).
- Produces: `AnthropicChatModelConfiguration` implementing the union, discriminator value (e.g. `anthropic`), a backend sub-union (`anthropic-api`, `compatible`), an `api` enum (single value), reasoning/effort/thinking config, prompt-caching toggle.

- [ ] **Step 1: Write the failing serialization test.** Assert a JSON payload with `"type":"anthropic"`, backend `"anthropic-api"` + apiKey, model, reasoning + caching fields deserializes into `AnthropicChatModelConfiguration`, and re-serializes byte-stably; assert a `"compatible"` backend with base URL + `apiKey` auth + custom headers deserializes. Assert `provider()`/`model()` return the expected values.

- [ ] **Step 2: Run it, verify it fails** (`AnthropicChatModelConfiguration` doesn't exist).
Run: `mvn -q -pl connectors/agentic-ai/connector-agentic-ai test -Dtest=AnthropicChatModelConfigurationTest` — expect compile failure.

- [ ] **Step 3: Rebuild the config type from the bak branch's refined `AnthropicChatModelConfiguration`** (see Global Constraints for the `git show` source). Keep its `model-options`-grouped params (`maxTokens`/`temperature`/`topP`/`topK`), the `anthropic-api` (direct) + `compatible` backend sub-union, the `api` enum (single value, hidden when single-valued), reasoning/effort (`ThinkingMode`, effort enum) and prompt-caching config. Adapt: union name (bare `ProviderConfiguration`), discriminators (`anthropic-api`/`compatible`), provider label "Anthropic". Implement `provider()`/`model()`. Widen the union's `permits` + `@JsonSubTypes`. Do NOT copy the pilot worktree's `AnthropicChatModel.java` (older `capabilities` grouping + Bedrock/skills).

- [ ] **Step 4: Run the test, verify it passes.**

- [ ] **Step 5: Commit** (`feat(agentic-ai): add v2 Anthropic provider configuration type`).

---

## Task 2: Anthropic transport (client factories)

**Files:**
- Port: `…/chatmodel/provider/anthropic/AnthropicClientFactory.java`, `AnthropicOkHttpClientFactory.java`
- Test: port `AnthropicOkHttpClientFactory`-covering test if present; else new `AnthropicOkHttpClientFactoryTest`

**Interfaces:**
- Consumes: `AnthropicChatModelConfiguration` backend types (Task 1).
- Produces: a built Anthropic SDK client per backend (direct: `x-api-key`; compatible: base URL + apiKey/none sentinel + static header/query/body maps).

- [ ] **Step 1: Write the failing test** — assert the direct backend sets the API key; assert the compatible backend sets base URL + `Authorization`/headers appropriately, and uses the no-auth sentinel when auth type is `None`.
- [ ] **Step 2: Run, verify fail.**
- [ ] **Step 3: Port both factories**, dropping the Bedrock branch. Compatible backend handles only `CompatibleNoAuthentication` / `CompatibleApiKeyAuthentication`.
- [ ] **Step 4: Run, verify pass.**
- [ ] **Step 5: Commit** (`feat(agentic-ai): add native Anthropic transport client factories`).

---

## Task 3: Content converter

**Files:**
- Port: `…/chatmodel/provider/anthropic/AnthropicContentConverter.java`
- Test: port `AnthropicContentConverterTest.java` (trim any server-tool/Skills cases)

**Interfaces:**
- Consumes: domain `Content` types (`TextContent`, `DocumentContent`, `ReasoningContent`, `ProviderContent`).
- Produces: Anthropic content blocks (and inverse), including native multimodal user-message blocks and byte-identical reasoning/provider block round-trip.

- [ ] **Step 1: Write/port failing tests** — text, native image/PDF user-message blocks, reasoning-content round-trip, ProviderContent passthrough.
- [ ] **Step 2: Run, verify fail.**
- [ ] **Step 3: Port the converter** faithfully.
- [ ] **Step 4: Run, verify pass.**
- [ ] **Step 5: Commit** (`feat(agentic-ai): add native Anthropic content converter`).

---

## Task 4: Request converter (incl. reasoning/effort/caching, param parity)

**Files:**
- Port: `…/chatmodel/provider/anthropic/AnthropicMessageRequestConverter.java`
- Port (adapted): `AnthropicReasoningValidator.java` — structural checks only, or omit
- Test: port `AnthropicMessageRequestConverterTest.java` (trim server-tools/Skills cases)

**Interfaces:**
- Consumes: domain message list, `AnthropicChatModelConfiguration`, Task 3 content converter.
- Produces: Anthropic `MessageCreateParams` — full parameter mapping, thinking/effort params, `cache_control` when caching enabled.

- [ ] **Step 1: Write/port failing tests** — every v1 parameter from Task 0 §3 is mapped; thinking mode → `thinking` param; effort → output config; caching → top-level `cache_control`; system prompt + tool definitions mapped. Add a test asserting the full Task-0 parameter set appears in the built params (acceptance criterion §6.1).
- [ ] **Step 2: Run, verify fail.**
- [ ] **Step 3: Port the converter**, dropping server-tool/Skills request mapping. If `AnthropicReasoningValidator` has matrix-independent structural checks, port those and call them; otherwise omit the validator entirely (invalid combos fail at the API).
- [ ] **Step 4: Run, verify pass.**
- [ ] **Step 5: Commit** (`feat(agentic-ai): add native Anthropic request converter with reasoning and caching`).

---

## Task 5: Response converter + stream assembler

**Files:**
- Port: `…/chatmodel/provider/anthropic/AnthropicMessageResponseConverter.java`, `AnthropicMessageStreamAssembler.java`
- Test: port `AnthropicMessageResponseConverterTest.java`, `AnthropicMessageStreamAssemblerTest.java`

**Interfaces:**
- Consumes: Anthropic streaming events / `BetaMessage`.
- Produces: `ChatModelResult.Completed | Continuation`, domain `AssistantMessage` (text, tool calls, `ReasoningContent`, `ProviderContent`), `AgentMetrics` (incl. cache + reasoning token usage), stop-reason mapping.

- [ ] **Step 1: Write/port failing tests** — text+tool-use mapping; `refusal` → `CONTENT_FILTERED`; `max_tokens` truncation → the RESPONSE_TRUNCATED error; unknown stop reason → `UnknownStopReason`; cache/reasoning token metrics; stream assembler builds the expected message. Include the `pause_turn` → `Continuation` case (and `mapStopReason` → null) ONLY if Task 0 §5 kept the beta client / the stable `StopReason` has `pause_turn`; otherwise omit it (cannot occur without the deferred server tools). Use the stable message type in assertions unless Task 0 §5 selected beta.
- [ ] **Step 2: Run, verify fail.**
- [ ] **Step 3: Port both**, keeping the pause_turn Continuation logic and the ProviderContent passthrough branch (harmless without server tools; preserves robustness).
- [ ] **Step 4: Run, verify pass.**
- [ ] **Step 5: Commit** (`feat(agentic-ai): add native Anthropic response converter and stream assembler`).

---

## Task 6: ChatModelApi + factory

**Files:**
- Port: `…/chatmodel/provider/anthropic/AnthropicChatModelApi.java`, `AnthropicChatModelApiFactory.java`
- Test: port `AnthropicChatModelApiTest.java`, `AnthropicChatModelApiFactoryTest.java`

**Interfaces:**
- Consumes: Task 2 transport, Task 4 request converter, Task 5 response converter, `AnthropicChatModelConfiguration`.
- Produces: `ChatModelApi` implementation (NO `capabilities()`), a factory that recognises `AnthropicChatModelConfiguration` and builds the api.

- [ ] **Step 1: Write/port failing tests** — factory `supports(...)` the Anthropic v2 config (direct + compatible); `create(...)` builds an api; `AnthropicChatModelApi.call(...)` threads request→SDK→response; `close()` releases the client.
- [ ] **Step 2: Run, verify fail.**
- [ ] **Step 3: Port both**, removing the `capabilities()` method and capability-resolver wiring.
- [ ] **Step 4: Run, verify pass.**
- [ ] **Step 5: Commit** (`feat(agentic-ai): add native Anthropic ChatModelApi and factory`).

---

## Task 7: Autoconfiguration registration

**Files:**
- Port/modify: `…/chatmodel/provider/anthropic/configuration/AgenticAiAnthropicProviderConfiguration.java`
- Modify: the agentic-ai autoconfiguration to import it
- Test: extend the autoconfiguration test — assert the Anthropic factory bean is registered and that resolving an `AnthropicChatModelConfiguration` yields a working factory (NOT the no-factory-registered error)

**Interfaces:**
- Consumes: Task 6 factory.
- Produces: a registered `ChatModelApiFactory` bean handling the Anthropic v2 config.

- [ ] **Step 1: Write the failing autoconfig test** — context loads with the Anthropic provider config bean present; resolving the Anthropic v2 config does not throw the no-factory error.
- [ ] **Step 2: Run, verify fail.**
- [ ] **Step 3: Port the provider `@Configuration`**, register the factory bean, drop capability beans, wire it into autoconfiguration.
- [ ] **Step 4: Run, verify pass.**
- [ ] **Step 5: Commit** (`feat(agentic-ai): register native Anthropic provider factory`).

---

## Task 8: Element templates

**Files:**
- Regenerate: the v2 element templates (generated artifacts)
- Test: the template-assertion test(s) covering provider/backend dropdown entries

- [ ] **Step 1: Write/extend the failing assertion** — the generated v2 template exposes the Anthropic provider with `anthropic-api` + `compatible` backends and their fields; provider label "Anthropic"; `api` field hidden while single-valued.
- [ ] **Step 2: Regenerate templates.**
Run: `mvn -q -pl connectors/agentic-ai/connector-agentic-ai clean process-classes`
- [ ] **Step 3: Run the assertion, verify pass.** Inspect the generated JSON diff for the Anthropic groups.
- [ ] **Step 4: Commit** (`build(agentic-ai): generate v2 element templates with native Anthropic provider`).

---

## Task 9: Deterministic e2e (wire-format + functional)

**Files** (module `connectors-e2e-test/connectors-e2e-test-agentic-ai`):
- Port: `…/wiremock/anthropic/AnthropicMessagesChatModelStubs.java` + the wire-format fixture(s)
- Port (shipped-behavior rows only): reasoning/effort, basic tool-calling, multimodal user-input, prompt-caching e2e classes
- Do NOT port: tool-call-result routing, Skills, code-execution, web_search/web_fetch e2e

- [ ] **Step 1: Port the WireMock stubs + wire-format fixture** for native Anthropic Messages.
- [ ] **Step 2: Port the shipped-behavior e2e tests** (reasoning/effort, tool-calling, multimodal user-input, caching), trimming deferred-feature rows.
- [ ] **Step 3: Cross-build + run the module.** Install the connector to m2, then run the affected e2e classes with `-pl connectors-e2e-test/connectors-e2e-test-agentic-ai` (NO `-am`). Read surefire XML for results.
- [ ] **Step 4: Commit** (`test(agentic-ai): add native Anthropic deterministic e2e coverage`).

---

## Task 10: Real-API smoke IT (compile only)

**Files** (module `connectors-e2e-test-agentic-ai`):
- Port: the cross-provider real-API smoke IT (confirm current name — `NativeProviderAcceptanceIT` / `RealProviderApiSmokeIT`)

- [ ] **Step 1: Port the IT minus OpenAI** — remove OpenAI rows/params (required to compile; OpenAI config types absent). Keep Anthropic rows for shipped behaviour.
- [ ] **Step 2: `@Disabled` the server-tool rows** with a reason referencing the deferred server-tools work.
- [ ] **Step 3: Test-compile only.** Do NOT run (real-API, paid — the driver seeks per-run user authorization separately).
Run: `mvn -q -pl connectors-e2e-test/connectors-e2e-test-agentic-ai test-compile` (after installing the connector to m2; no `-am`).
- [ ] **Step 4: Commit** (`test(agentic-ai): add native Anthropic real-API smoke coverage`).

---

## Self-Review notes

- Spec coverage: config type (T1), transport (T2), converters (T3–T5), api+factory (T6), registration (T7), templates (T8), deterministic e2e (T9), real-API IT (T10), branch verification (T0). Deferred items (server tools, Skills, capability matrix, OAuth, cloud backends, inline tool_result multimodal) are explicitly excluded per Global Constraints.
- The two acceptance criteria (full param parity; `<doc/>` tool-result behaviour unchanged) are enforced in T4 (param checklist from T0) and preserved by NOT introducing capability-gated routing.
- Reasoning-validation decoupling (matrix-independent structural checks only) is fixed in T4 and the Global Constraints.
