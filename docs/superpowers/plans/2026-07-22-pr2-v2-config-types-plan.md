# PR2′ — v2 config types + workers + templates + custom provider — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:subagent-driven-development to implement this
> plan task-by-task (Sonnet implementers + Sonnet reviewers). Steps use checkbox (`- [ ]`) syntax.

**Goal:** Land the v2 AI Agent entry points (job workers + element templates) and the `V2ProviderConfiguration`
config union (Anthropic + OpenAI + Custom) as config types only — selectable in Modeler, native providers not
runnable (explicit "not implemented"), custom provider runnable via a user-supplied factory. Plus the deferred
v1 package/type rename.

**Architecture:** Copy the pilot's v2 implementation as the role model, back-ported to the PR1-branch SPI naming,
with the capability matrix and skills/server-tools stripped. See the design spec (read it first):
`/Users/mathias.geat/orca/workspaces/connectors/issue-7211-own-llm-layer/docs/superpowers/specs/2026-07-22-pr2-v2-config-types-design.md`

**Tech stack:** Java 21, Spring Boot autoconfiguration, Jackson sealed unions, the element-template-generator
Maven plugin + a gmavenplus groovy transform. Build tool: Maven.

**Role-model source (copy FROM, read-only):** pilot worktree
`/Users/mathias.geat/orca/workspaces/connectors/issue-7211-own-llm-layer` (branch `agentic-ai/issue-7211-vertical-pilot`).
**Target (edit HERE):** worktree `/Users/mathias.geat/orca/workspaces/connectors/agentic-ai-v2-config-types`
(branch `agentic-ai/v2-config-types`, already created off PR1 tip `0eb5465d75`).

## Global Constraints

- **Java 21.** Build/test the module only: `mvn -pl connectors/agentic-ai/connector-agentic-ai <goals>` from the
  target worktree root. Element-template regeneration: `mvn clean compile -f connectors/agentic-ai/pom.xml`.
- **NEVER `-am`** — it runs `license:format` reactor-wide and rewrites ~1500 proprietary license headers to
  Apache. Never run repo-wide `license:format`/spotless. Never touch/reformat license headers.
- **Run Maven/git UNSANDBOXED** (Mockito breaks in the sandbox). Read `target/surefire-reports/*.xml` for
  pass/fail, not mvn stdout.
- **Commit messages describe the actual change only** — no PR/review/round/pilot/superpowers references.
- **Never git checkout/reset/revert/rebase/stash/push.** Only `git add` + `git commit` your own work.
- **No local paths / secrets / env-loader names** in any committed file.
- **Do NOT push or open a PR.** Stop after commit 5 + a green build and report. The user opens the PR (its own
  sub-issue under #7211).
- **Superpowers docs (this plan, the spec, the handoff) live only in the PILOT worktree and MUST NOT be copied
  or committed into the target worktree / PR2′ branch.** The target worktree stays free of `docs/superpowers/`.
- **Two adaptations applied to EVERY copied v2 file** (the pilot uses a later SPI naming that does not exist on
  the PR1 branch — copy-verbatim will not compile):
  1. `extends ChatModelApiConfiguration` → `extends ChatModelConfiguration`
  2. accessor `providerType()` → `provider()`
- **No real-API e2e.** No `RUN_NATIVE_LLM_E2E`.
- **Matrix-free / skills-free:** never introduce `ModelCapabilitiesMode`/`ModelCapabilitiesOverride`/
  `capabilityMode`/`capabilityOverride`, nor any skills/server-tool fields or the "Model Tools" group. (The
  `aiagent.capabilities` package and the `chatmodel/provider/{anthropic,openai}` packages do not exist on this
  branch — do not create them.)

---

### Task 1: Rename the v1 provider configuration package + type

**Files:**
- Move (git mv) all 11 files: `connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/model/request/provider/**`
  → `.../model/request/v1/**`. Rename `provider/ProviderConfiguration.java` → `v1/V1ProviderConfiguration.java`
  (file + type). Keep the 6 subtype records and `shared/{HttpUrl,TimeoutConfiguration,package-info}` simple names.
- Update every reference: 25 main files (11 are the moved files) + 11 test files — package declarations, imports
  (`…model.request.provider[.shared]` → `…model.request.v1[.shared]`), and every bare `ProviderConfiguration`
  token (generic bounds, casts, `instanceof`, field types in `JobWorkerAgentRequest.java` /
  `OutboundConnectorAgentRequest.java`).
- Doc prose: `connectors/agentic-ai/docs/adr/009-chat-model-provider-spi.md:64`,
  `connectors/agentic-ai/docs/reference/ai-agent.md` (~lines 933, 978–979, 1819–1822).

**Interfaces produced:** package `…model.request.v1`, type `V1ProviderConfiguration` (union with the same 6
subtypes, same `provider()`/`model()` methods, same `@JsonTypeInfo(Id.NAME,"type")` and discriminator IDs).

- [ ] **Step 1:** `git mv` the 11 files into `v1/` and rename the interface file to `V1ProviderConfiguration.java`.
- [ ] **Step 2:** In each moved file, change `package …provider[.shared];` → `…v1[.shared];`, and fix the internal
      static imports in the union file (they reference sibling `_ID` constants — same-package, so just the package
      segment changes).
- [ ] **Step 3:** Rename the interface `ProviderConfiguration` → `V1ProviderConfiguration` and update the 6
      `implements` clauses.
- [ ] **Step 4:** Update all external references. Search: `grep -rn "model\.request\.provider\|\bProviderConfiguration\b"
      connectors/agentic-ai` — fix imports and bare tokens. **Exclude** `ProviderConfigurationTest`'s 6
      self-referential `@MethodSource` FQCN string literals (they name the test class itself, which is NOT renamed).
      Fix the Javadoc-only `{@code ProviderConfiguration}` in `ChatModelConfiguration.java:12` (grep, not compiler).
- [ ] **Step 5:** Do NOT change `provider()`→`providerType()` or `ChatModelConfiguration`→`ChatModelApiConfiguration`
      (those are the pilot's later reshape, out of scope). Update the 2 doc-prose files.
- [ ] **Step 6:** Regenerate element templates: `mvn clean compile -f connectors/agentic-ai/pom.xml`. Verify the v1
      template JSON `git diff` is empty (discriminator values are byte-stable — no change expected).
- [ ] **Step 7:** `mvn -pl connectors/agentic-ai/connector-agentic-ai test` — full module suite green (no test-logic
      changes beyond import/type renames). Read surefire XML.
- [ ] **Step 8:** Commit: `refactor(agentic-ai): rename the v1 provider configuration package and type`.

**Verification:** module compiles + full suite green; v1 element-template JSON unchanged; grep for
`model.request.provider` and bare `ProviderConfiguration` (outside the test-class self-refs) returns zero hits.

---

### Task 2: Add the v2 provider configuration types

**Files (create in `connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/model/request/v2/`):**
`V2ProviderConfiguration.java`, `AnthropicChatModel.java`, `OpenAiChatModel.java`, `OpenAiApiFamily.java`,
`OpenAiEffort.java`, `AnthropicEffort.java` (NEW), `ThinkingMode.java` (NEW), `shared/CompatibleAuthentication.java`,
`package-info.java`, `shared/package-info.java`. Tests mirror the pilot's v2 config tests (stripped).
**Do NOT copy `shared/ChatModelAwsAuthentication.java`** — only the deferred Bedrock backend uses it.

**Copy FROM (pilot):** `…/issue-7211-own-llm-layer/connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/model/request/v2/**`

**Interfaces produced:** sealed `V2ProviderConfiguration extends ChatModelConfiguration permits AnthropicChatModel,
OpenAiChatModel` (Custom added in Task 5), methods `provider()`/`model()`/`backend()`. Enums `OpenAiApiFamily`
(COMPLETIONS/RESPONSES), `OpenAiEffort`, `AnthropicEffort`, `ThinkingMode`.

- [ ] **Step 1:** Copy the pilot v2 files. Apply the two Global-Constraint adaptations everywhere.
- [ ] **Step 2:** In `V2ProviderConfiguration.java`: `extends ChatModelConfiguration`; `permits AnthropicChatModel,
      OpenAiChatModel`; methods `provider()`, `model()`, `backend()`. **Remove** the `capabilityOverride()` method
      and its `ModelCapabilitiesOverride` import.
- [ ] **Step 3:** In `AnthropicChatModel.java`: backend union **`direct` + `compatible` only** — **DROP the
      `bedrock` member** (`AnthropicBedrockBackend`, its `@AssertFalse` SaaS check, the `region`/`endpoint`
      fields, and the `ChatModelAwsAuthentication` reference); Anthropic Bedrock is deferred (spec §8), stays on
      the pilot. Keep `enablePromptCaching` (group `model`), `AnthropicModel`, `AnthropicModelParameters`
      (`maxTokens`/`temperature`/`topP`/`topK` → `group="model-options"`; `effort`/`thinking` → `group="model"`),
      `AnthropicThinking` + nested `ThinkingDisplay`. **Strip** `capabilityMode`, `capabilityOverride`, and ALL skills
      fields (`skills`, `enableCodeExecution`, `codeExecutionVersion`, `enableWebSearch`, `webSearchVersion`,
      `enableWebFetch`, `webFetchVersion`) + their imports. Change the `AnthropicEffort`/`ThinkingMode` imports to the
      new local `v2` enums (Step 6).
- [ ] **Step 4:** In `OpenAiChatModel.java`: keep `apiFamily` (group `provider`), backend union (direct/compatible),
      `OpenAiModel`, `OpenAiModelParameters` (`maxCompletionTokens`/`maxOutputTokens`/`temperature`/`topP` →
      `group="model-options"`; `effort` → `group="model"`). **Strip** `capabilityMode`, `capabilityOverride`,
      `enableWebSearch`, `enableCodeInterpreter`, the matrix-framed `apiFamilyKey()` accessor, and their imports.
- [ ] **Step 5:** In `OpenAiApiFamily.java`: strip the `familyKey` field + `familyKey()` accessor → bare enum
      `COMPLETIONS`/`RESPONSES`. Copy `OpenAiEffort.java` as-is (already no `custom`).
- [ ] **Step 6:** Create `AnthropicEffort.java` (`low/medium/high/xhigh/max`, lowercase `@JsonProperty`) and
      `ThinkingMode.java` (`enabled/adaptive/disabled`) as top-level enums in the `v2` package (ported from the
      pilot's `chatmodel/provider/anthropic/`, which does not exist here).
- [ ] **Step 7:** Copy `shared/CompatibleAuthentication.java` + both `package-info.java` as-is (NOT
      `ChatModelAwsAuthentication` — Bedrock deferred). Preserve the intentional naming asymmetry: Anthropic
      compatible auth field = `compatibleAuthentication`, OpenAI = `authentication`. `timeouts`/`HttpUrl`
      reference `…model.request.v1.shared.*`.
- [ ] **Step 8:** Port the pilot's v2 config unit tests (serialization/validation), stripping skills/matrix AND
      Bedrock cases. Cover: discriminator round-trip (`anthropic`/`openai`), backend round-trips (Anthropic
      direct/compatible; OpenAI direct/compatible), `toString()` auth redaction, enum values.
- [ ] **Step 9:** `mvn -pl connectors/agentic-ai/connector-agentic-ai test-compile` then run the new tests. Green.
- [ ] **Step 10:** Commit: `feat(agentic-ai): add v2 provider configuration types`.

**Verification:** the `v2` package compiles with zero imports of `aiagent.capabilities.*` or
`aiagent.chatmodel.provider.*`; new config tests green.

---

### Task 3: Add v2 workers + requests + wiring + not-implemented placeholder

**Files:**
- Create `aiagent/AiAgentTaskV2Function.java`, `aiagent/AiAgentSubProcessV2Function.java`,
  `aiagent/model/request/JobWorkerAgentRequestV2.java`, `aiagent/model/request/OutboundConnectorAgentRequestV2.java`,
  `aiagent/chatmodel/NotImplementedNativeChatModelApiFactory.java` (NEW).
- Modify `autoconfigure/AgenticAiConnectorsAutoConfiguration.java` (+ its test).
- Execution contexts: no change (already version-agnostic).

**Copy FROM (pilot):** the same-named function/request files under `…/issue-7211-own-llm-layer/.../aiagent/`.

**Interfaces consumed:** `V2ProviderConfiguration` (Task 2); the shared `…RequestData` types and execution-context
classes already on the branch. **Produced:** connector types `io.camunda.agenticai:aiagent:task:2` /
`…:subprocess:2`; template id `io.camunda.connectors.agenticai.ai-agent-task.v2`.

- [ ] **Step 1:** Copy `AiAgentTaskV2Function.java`. Its `@ElementTemplate.propertyGroups`: take the pilot's list,
      **remove the `skills` ("Model Tools") group**, and **rename the `capabilities` group id → `model-options`**
      (label stays "Advanced model options"). Bind `inputDataClass = OutboundConnectorAgentRequestV2.class`.
- [ ] **Step 2:** Copy `AiAgentSubProcessV2Function.java` (`JOB_WORKER_TYPE = "io.camunda.agenticai:aiagent:subprocess:2"`,
      name "AI Agent Sub-process"; no `@ElementTemplate`).
- [ ] **Step 3:** Copy `JobWorkerAgentRequestV2.java` / `OutboundConnectorAgentRequestV2.java` — v1 request records
      with `provider` re-typed to `V2ProviderConfiguration`; reuse the shared `…RequestData` types (do not duplicate).
- [ ] **Step 4:** Write `NotImplementedNativeChatModelApiFactory` implementing `ChatModelApiFactory`:
      `supports(cfg)` → `cfg instanceof AnthropicChatModel || cfg instanceof OpenAiChatModel` (NOT custom, NOT v1);
      `create(cfg)` → `throw new UnsupportedOperationException("Native " + <provider label> + " chat model is not
      yet implemented")`. Javadoc: document the native-PR handoff (each native PR drops its type from `supports()`
      when it registers the real factory; delete this class when both are gone). Match the `ChatModelApiFactory`
      SPI signature exactly (check `Langchain4JChatModelApiFactory` for the interface shape).
- [ ] **Step 5:** In `AgenticAiConnectorsAutoConfiguration`: add `@Bean`s `aiAgentTaskV2Function`
      (`@ConditionalOnBean(OutboundConnectorAgentRequestHandler.class)`, toggle
      `camunda.connector.agenticai.aiagent.task-v2.enabled`, `matchIfMissing=true`) and `aiAgentSubProcessV2Function`
      (`@ConditionalOnBean(JobWorkerAgentRequestHandler.class)`, toggle `subprocess-v2.enabled`), plus an
      unconditional `@Bean` for `NotImplementedNativeChatModelApiFactory`. **Do NOT** add any
      `chatmodel/provider/{anthropic,openai}` `@Import`.
- [ ] **Step 6:** Tests: (a) `NotImplementedNativeChatModelApiFactory` supports Anthropic/OpenAI v2, rejects custom/v1,
      `create()` throws `UnsupportedOperationException`; (b) in `AgenticAiConnectorsAutoConfigurationTest`: v2 function
      beans exist; resolving a native v2 config → `resolve(...).create(...)` throws `UnsupportedOperationException`.
- [ ] **Step 7:** `mvn -pl connectors/agentic-ai/connector-agentic-ai test` (run affected classes). Green.
- [ ] **Step 8:** Commit: `feat(agentic-ai): add v2 AI Agent task and sub-process connectors`.

**Verification:** module compiles; v2 beans register; a native v2 config throws `UnsupportedOperationException`
(not `ERROR_CODE_FAILED_MODEL_CALL`); no `chatmodel/provider/{anthropic,openai}` import anywhere.

---

### Task 4: Generate + commit v2 element templates

**Files:** `connector-agentic-ai/pom.xml` (add the generator `<connector>` entry + the two gmavenplus
`<execution>` blocks for the sub-process transform, task + hybrid), and the 4 generated JSONs under
`element-templates/`.

- [ ] **Step 1:** Add the pom.xml wiring mirroring the pilot (`…/issue-7211-own-llm-layer/.../pom.xml`): the
      `<connector>` for `AiAgentTaskV2Function` (`templateId` `io.camunda.connectors.agenticai.ai-agent-task.v2`,
      `templateFileName` `agenticai-ai-agent-task.v2.json`, `generateHybridTemplates=true`) and the two
      `generate-subprocess-v2-template[-hybrid]` gmavenplus executions (`bin/transform-ai-agent-template.groovy` is
      generic — copy it if absent, no logic change).
- [ ] **Step 2:** `mvn clean compile -f connectors/agentic-ai/pom.xml`. Confirm the 4 JSONs are generated:
      `agenticai-ai-agent-task.v2.json`, `agenticai-ai-agent-subprocess.v2.json`, and the two `hybrid/` siblings.
- [ ] **Step 3:** Inspect the generated JSON: group order `provider → model → "Advanced model options" →
      systemPrompt → userPrompt → tools → memory → limits → response` (sub-process adds `events` after `limits`).
      Assert NO "Model Tools" group, NO "Model capabilities" dropdown, NO capability-override field, NO
      `=`-prefixed placeholders.
- [ ] **Step 4:** Run the module's element-template verification test (whatever asserts template correctness). Green.
- [ ] **Step 5:** Commit the 4 JSONs + pom.xml: `build(agentic-ai): generate v2 AI Agent element templates`.

**Verification:** 4 JSONs present + committed; groups as specified; template test green.

---

### Task 5: Add the custom model provider configuration (advances #5547)

**Files:** create `model/request/v2/CustomProviderConfiguration.java`; modify `model/request/v2/V2ProviderConfiguration.java`
(widen union); regenerate the 4 template JSONs; add config test; add a docs section in
`connectors/agentic-ai/docs/reference/ai-agent.md`.

**Pattern reference:** `model/request/MemoryStorageConfiguration.CustomMemoryStorageConfiguration` (type + params
map). Read it before implementing.

- [ ] **Step 1:** Create `CustomProviderConfiguration` record implementing `V2ProviderConfiguration`:
      components `String providerType`, `String model`, `Map<String,Object> parameters`. `provider()` → `providerType`;
      `model()` → `model`; `backend()` → `null`. `@TemplateSubType(id = CUSTOM_ID /* "custom" */, label = "Custom
      Implementation (Self-Managed/Hybrid only)")`. `@TemplateProperty` on `providerType` (String/FEEL, "Provider
      type", required), `model` (String/FEEL, "Model"), `parameters` (Text/FEEL, "Provider parameters", optional,
      Map — mirror custom-memory's `customProperties`).
- [ ] **Step 2:** Widen `V2ProviderConfiguration`: add `CustomProviderConfiguration` to `permits` and a
      `@JsonSubTypes.Type(value = CustomProviderConfiguration.class, name = CUSTOM_ID)`. The placeholder factory
      (Task 3) stays unchanged — it must NOT match custom.
- [ ] **Step 3: Self-Managed/Hybrid gating** — inspect how the custom-memory entry and/or the AWS
      default-credentials-chain option express "Self-Managed/Hybrid only" (label suffix and/or hybrid-only template
      handling) and reuse that exact mechanism. Do NOT invent a new one. If it needs template-generator support that
      isn't present, report back rather than improvising.
- [ ] **Step 4:** Config test: `CustomProviderConfiguration` round-trips (`type="custom"`,
      `providerType`/`model`/`parameters`), `provider()`/`model()`/`backend()==null`. Add a test proving a stub
      `ChatModelApiFactory` bean whose `providerType()` matches resolves a `CustomProviderConfiguration` (user-supplied
      path works), and that with no such bean `resolve()` yields the normal "no factory registered" error.
- [ ] **Step 5:** Regenerate templates (`mvn clean compile -f connectors/agentic-ai/pom.xml`): the provider
      discriminator now offers Anthropic / OpenAI / Custom. Commit the regenerated 4 JSONs.
- [ ] **Step 6:** Docs: short section in `ai-agent.md` — implement `ChatModelApiFactory` (or register the L4J bridge
      factory under your `providerType`) + register as a Spring bean; registry dispatches by `providerType()`. No
      example module (out of scope per #5547).
- [ ] **Step 7:** `mvn -pl connectors/agentic-ai/connector-agentic-ai test` (affected classes). Green.
- [ ] **Step 8:** Commit: `feat(agentic-ai): add custom model provider configuration`.

**Verification:** union has 3 members; custom round-trips; user-factory dispatch works; templates show the Custom
entry gated Self-Managed/Hybrid; placeholder factory still rejects custom.

---

## Final gate

- [ ] Full module suite green: `mvn -pl connectors/agentic-ai/connector-agentic-ai test`.
- [ ] `git log --oneline` shows exactly 5 coherent commits, messages per §9 of the spec, no
      review/pilot/superpowers wording.
- [ ] No `docs/superpowers/` in the target worktree; no license-header churn (`git diff` touches only intended files).
- [ ] Whole-branch review (Sonnet) per subagent-driven-development.
- [ ] **STOP — do not push / open PR.** Report commit range + test summary to the user.

## Self-review notes (planner)

- Spec coverage: every §5 sub-section maps to a task (5.1→T1, 5.2→T2, 5.3→T3, 5.4→T4, 5.5→T5). Testing §6 folded
  into per-task steps + final gate.
- Type consistency: `V2ProviderConfiguration` methods `provider()`/`model()`/`backend()` used consistently;
  the placeholder factory matches only `AnthropicChatModel`/`OpenAiChatModel` across T3/T5.
- Watch item handed to implementer (T5S3): the Self-Managed/Hybrid gating mechanism is verify-then-reuse, not
  invent — escalate if the generator lacks support.
