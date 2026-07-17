# R1 — Native Anthropic Reasoning (thinking) & Effort Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add first-class reasoning configuration to the native Anthropic path — the two orthogonal axes Anthropic exposes (**thinking** and **effort**) on the request side, matrix-driven fail-fast validation, and a lossless provider-neutral reasoning trace that round-trips back to the API byte-identical.

**Architecture:** Provider-specific *request* config (typed to Anthropic's wire shape, capability-gated), provider-neutral *response* data (`ReasoningContent` carrying the raw block for lossless replay), and — finishing R0 — a provider-agnostic *config-binding* shape (`ModelCapabilitiesProperties` = typed agnostic core + one opaque `provider` bag) whose Anthropic projection types the `reasoning` descriptor. Stacks on R0 (commit `957cf321`).

**Tech Stack:** Java 21 records + enums, Jackson tree merge + `treeToValue`, anthropic-java **2.48.0 beta** messages client, Spring `@ConfigurationProperties` relaxed binding, element-template generator, JUnit 5 + Mockito + AssertJ, WireMock e2e (Camunda Process Test). Module: `connectors/agentic-ai/connector-agentic-ai` (+ separate e2e module `connectors-e2e-test/connectors-e2e-test-agentic-ai`).

**Design spec:** `docs/superpowers/specs/2026-07-14-anthropic-reasoning-effort-design.md` (§2–§9; ratified refinements in §3a/§3b/§3c). Read it for rationale; this plan carries the code.

## Global Constraints

- **BC is the #1 priority.** Do not touch the wire shape of any persisted type. The native path is unreleased (8.10 v2), so `ReasoningContent.providerPayload` semantics and the matrix/binding restructure are BC-free — but the **L4J bridge path is released**: verify the bridge never persists a `providerPayload` shape this changes (it doesn't populate `providerPayload`), and leave all `langchain4j/**` behaviour untouched.
- **SDK facts are pinned (verified against anthropic-java 2.48.0, spec §9):** `BetaThinkingConfigParam.ofEnabled/ofAdaptive/ofDisabled`; `BetaThinkingConfigEnabled.builder().budgetTokens(long)`; `BetaThinkingConfigAdaptive.Display` = `SUMMARIZED`/`OMITTED` + `Display.of(String)`; `BetaOutputConfig.Effort` = `LOW/MEDIUM/HIGH/XHIGH/MAX` + `Effort.of(String)` (open enum, no `minimal`); set via `MessageCreateParams.Builder.thinking(...)` and `.outputConfig(BetaOutputConfig.builder().effort(...).build())`.
- **Provider-agnostic capabilities package:** `framework/capabilities/**` must import nothing from `framework/anthropic/**`. Provider vocabulary (`reasoning`, thinking modes, effort levels) lives only in `framework/anthropic/**`.
- **Neutral `ModelCapabilities` interface stays modalities-only** (R0 invariant). Do not add reasoning/effort methods to it.
- **`reasoning` never in a family `defaults:` block** (spec §3c) — deepMerge can't un-set; declare per-model/glob only. A bundled-matrix test enforces this.
- **Opaque-bag key convention:** kebab-case inside `provider:` (Spring binds Map keys literally); typed descriptor uses kebab `@JsonProperty` (`thinking-modes`, `effort-levels`). Typed shared fields keep relaxed binding + `@JsonNaming(SnakeCase)`.
- **Effort is Anthropic-specific**, NOT shared with OpenAI. `CUSTOM` bypasses validation and is sent verbatim via `Effort.of(customEffort)`.
- `@NullMarked` everywhere; `org.jspecify.annotations.Nullable`; never suppress null errors.
- **v1 templates/behaviour byte-identical.** Only the **v2** (own-LLM-layer) templates change. Regenerate templates via `mvn clean compile -f connectors/agentic-ai/pom.xml`; commit the JSON diff; never hand-edit generated JSON. If a template version bumps or a property is added, follow AGENTS.md "Element templates" (README table) — but adding optional properties to an existing unreleased v2 template needs no README change unless the min-engine version changes.
- Build/verify (outside the sandbox — Mockito + network): `mvn clean install -f connectors/agentic-ai/pom.xml`. E2e (separate module, needs `element-templates-cli` on PATH): `mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -Dtest=<Class>` — build with `-am` after a model/serialization change. Spotless/license enforced by pre-commit hooks.
- **Model-gating source of truth** (spec §1c table) for authoring the matrix reasoning descriptors — do not invent per-model support; use that table.

---

## File Structure

**Capabilities package** (`.../framework/capabilities/`, provider-agnostic):
- `ModelCapabilitiesProperties.java` — **CHANGED**: prune 3 flags; restructure to `(inputModalities, outputModalities, contextWindow, maxOutputTokens, Map<String,Object> provider)`.
- `ModelCapabilitiesOverride.java` — **CHANGED**: drop the 3 pruned flags + `supportsReasoning`; keep modalities + limits; add nothing provider-specific (reasoning override deferred, see Task 1 note).

**Anthropic package** (`.../framework/anthropic/`, provider-internal):
- `AnthropicModelCapabilities.java` — **CHANGED**: `(CoreModelCapabilities core, @Nullable AnthropicReasoningCapabilities reasoning)` + derived `supportsReasoning()`.
- `AnthropicModelCapabilitiesData.java` — **CHANGED**: prune flags; `(inputModalities, outputModalities, contextWindow, maxOutputTokens, @Nullable AnthropicProviderCapabilities provider)`; project `provider.reasoning`.
- `AnthropicProviderCapabilities.java` — **NEW**: `(@Nullable AnthropicReasoningCapabilities reasoning)`.
- `AnthropicReasoningCapabilities.java` — **NEW**: `(@JsonProperty("thinking-modes") List<ThinkingMode> thinkingModes, @JsonProperty("effort-levels") List<AnthropicEffort> effortLevels)`.
- `AnthropicReasoningValidator.java` — **NEW**: fail-fast validation (spec §6).
- `AnthropicMessageRequestConverter.java` — **CHANGED**: apply thinking + effort; call the validator; read caps via concrete type.
- `AnthropicContentConverter.java` — **CHANGED**: unconditional `ReasoningContent` re-emission.
- `AnthropicMessageResponseConverter.java` — **CHANGED**: `ReasoningContent.providerPayload` = raw block Map (Option A).

**Model/config** (`.../aiagent/model/request/chatmodel/`):
- `AnthropicChatModel.java` — **CHANGED**: `AnthropicModelParameters` gains `thinking`/`effort`/`customEffort`; new nested `AnthropicThinking` record + `ThinkingMode`/`ThinkingDisplay`/`AnthropicEffort` enums (co-located here — they are config vocabulary, but the matrix descriptor in the anthropic package reuses `ThinkingMode`/`AnthropicEffort`; see Task 1 note on enum placement).

**Matrix data:** `.../resources/capabilities/model-capabilities.yaml` — **CHANGED**: prune 3 flags from defaults; add `provider.reasoning` per reasoning-capable model.

**Generated:** v2 element-template JSON under `connector-agentic-ai/element-templates/` — regenerated.

**Tests:** `ModelCapabilitiesResolverTest`, `BundledCapabilityMatrixTest`, `CapabilityMatrixOverrideTest`, `AnthropicModelCapabilitiesTest`, `AnthropicMessageRequestConverterTest`, `AnthropicMessageResponseConverterTest`, `AnthropicContentConverterTest`, `AnthropicChatModelTest`, new `AnthropicReasoningValidatorTest`; e2e in the separate module extending the native fixture.

### Enum placement decision
`ThinkingMode` and `AnthropicEffort` are used by **both** the config records (`AnthropicChatModel`) and the matrix descriptor (`framework/anthropic`). To avoid a cycle and keep the capabilities package clean, define them in `framework/anthropic` (e.g. as top-level enums or nested on `AnthropicReasoningCapabilities`) and have `AnthropicChatModel` import them. `AnthropicChatModel` (model package) importing from `framework/anthropic` is acceptable — the model package already imports `framework/capabilities` (`ModelCapabilitiesOverride`). `ThinkingDisplay` is config-only → co-locate with `AnthropicThinking`. Confirm no architectural rule forbids `model → framework/anthropic`; if it does, place the shared enums in a neutral spot both can see. Resolve in Task 1 and state the choice in the report.

---

### Task 1: Capability layer — prune flags, provider-bag alignment, reasoning descriptor

**Files:**
- Modify: `.../capabilities/ModelCapabilitiesProperties.java`, `.../capabilities/ModelCapabilitiesOverride.java`
- Modify: `.../anthropic/AnthropicModelCapabilities.java`, `.../anthropic/AnthropicModelCapabilitiesData.java`
- Create: `.../anthropic/AnthropicProviderCapabilities.java`, `.../anthropic/AnthropicReasoningCapabilities.java`, `.../anthropic/ThinkingMode.java`, `.../anthropic/AnthropicEffort.java`
- Modify: `.../resources/capabilities/model-capabilities.yaml`
- Modify: `.../anthropic/AnthropicMessageRequestConverter.java` (only the `capabilities.core()` read paths already use `.core()`; verify no flag reads remain)
- Test: `ModelCapabilitiesResolverTest`, `BundledCapabilityMatrixTest`, `CapabilityMatrixOverrideTest`, `AnthropicModelCapabilitiesTest`, new `BundledCapabilityMatrixReasoningTest` (or extend `BundledCapabilityMatrixTest`).

**Interfaces produced (relied on by later tasks):**
- `enum ThinkingMode { ENABLED, ADAPTIVE, DISABLED }`
- `enum AnthropicEffort { LOW, MEDIUM, HIGH, XHIGH, MAX, CUSTOM }` (CUSTOM used by config only; the matrix `effort-levels` list never contains CUSTOM)
- `record AnthropicReasoningCapabilities(List<ThinkingMode> thinkingModes, List<AnthropicEffort> effortLevels)`
- `record AnthropicProviderCapabilities(@Nullable AnthropicReasoningCapabilities reasoning)`
- `record AnthropicModelCapabilities(CoreModelCapabilities core, @Nullable AnthropicReasoningCapabilities reasoning) implements ModelCapabilities` with `boolean supportsReasoning()` = `reasoning != null`, delegating modality methods to `core`.

- [ ] **Step 1: Write the failing capability tests**

Rewrite `AnthropicModelCapabilitiesTest` for the new shape: a caps with `reasoning != null` reports `supportsReasoning() == true` and exposes `reasoning().thinkingModes()`/`effortLevels()`; a caps with `reasoning == null` reports `supportsReasoning() == false`; modality methods still delegate to `core`.

Add a bundled-matrix reasoning test (new file `BundledCapabilityMatrixReasoningTest` in the capabilities test package, resolving through `AnthropicModelCapabilitiesData.class`):
```java
@Test
void opus48ResolvesAdaptiveDisabledWithFullEffortLevels() {
  var caps = resolver.resolve("anthropic-messages", "claude-opus-4-8", "direct",
      Optional.empty(), AnthropicModelCapabilitiesData.class);
  assertThat(caps.supportsReasoning()).isTrue();
  assertThat(caps.reasoning().thinkingModes())
      .containsExactly(ThinkingMode.ADAPTIVE, ThinkingMode.DISABLED);
  assertThat(caps.reasoning().effortLevels())
      .containsExactly(AnthropicEffort.LOW, AnthropicEffort.MEDIUM, AnthropicEffort.HIGH,
          AnthropicEffort.XHIGH, AnthropicEffort.MAX);
}

@Test
void sonnet45ResolvesEnabledDisabledWithNoEffort() {
  var caps = resolver.resolve("anthropic-messages", "claude-sonnet-4-5", "direct",
      Optional.empty(), AnthropicModelCapabilitiesData.class);
  assertThat(caps.reasoning().thinkingModes())
      .containsExactly(ThinkingMode.ENABLED, ThinkingMode.DISABLED);
  assertThat(caps.reasoning().effortLevels()).isEmpty();
}

@Test
void unknownModelHasNoReasoningDescriptor() {
  var caps = resolver.resolve("anthropic-messages", "claude-mystery-9", "direct",
      Optional.empty(), AnthropicModelCapabilitiesData.class);
  assertThat(caps.reasoning()).isNull();
  assertThat(caps.supportsReasoning()).isFalse();
}

@Test
void familyDefaultsDeclareNoReasoning() {
  // guards the "reasoning never in defaults" invariant (spec §3c): a family-default reasoning
  // block would leak into every non-reasoning model. Assert the bundled defaults omit it.
  var caps = resolver.resolve("anthropic-messages", "totally-unmatched", "direct",
      Optional.empty(), AnthropicModelCapabilitiesData.class);
  assertThat(caps.reasoning()).isNull();
}
```
Get the `resolver` from the bundled matrix the same way `BundledCapabilityMatrixTest` does (reuse its setup).

- [ ] **Step 2: Run the tests — expect compile failure**

Run: `mvn -q -o test-compile -f connectors/agentic-ai/pom.xml`. Expected: `AnthropicReasoningCapabilities`, `ThinkingMode`, `AnthropicEffort`, the new `AnthropicModelCapabilities`/`...Data` shapes don't exist.

- [ ] **Step 3: Create the enums and descriptor**

`ThinkingMode.java`:
```java
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

public enum ThinkingMode { ENABLED, ADAPTIVE, DISABLED }
```
`AnthropicEffort.java`:
```java
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

/** Anthropic effort levels. CUSTOM is a config-only escape hatch (never appears in the matrix). */
public enum AnthropicEffort { LOW, MEDIUM, HIGH, XHIGH, MAX, CUSTOM }
```
`AnthropicReasoningCapabilities.java`:
```java
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Typed Anthropic reasoning descriptor, materialised from a model's {@code provider.reasoning} matrix
 * block. {@code thinking-modes} lists the thinking mechanisms the model accepts (a manual
 * {@code enabled} budget 400s on adaptive-only models; {@code disabled} 400s on always-on models);
 * {@code effort-levels} lists supported effort values (empty ⇒ effort unsupported).
 */
public record AnthropicReasoningCapabilities(
    @JsonProperty("thinking-modes") List<ThinkingMode> thinkingModes,
    @JsonProperty("effort-levels") List<AnthropicEffort> effortLevels) {

  public AnthropicReasoningCapabilities {
    thinkingModes = thinkingModes == null ? List.of() : List.copyOf(thinkingModes);
    effortLevels = effortLevels == null ? List.of() : List.copyOf(effortLevels);
  }
}
```
`AnthropicProviderCapabilities.java`:
```java
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import org.jspecify.annotations.Nullable;

/** Typed interpretation of the opaque {@code provider} capability bag for the Anthropic family. */
public record AnthropicProviderCapabilities(@Nullable AnthropicReasoningCapabilities reasoning) {}
```

- [ ] **Step 4: Reshape `AnthropicModelCapabilities`**

```java
public record AnthropicModelCapabilities(
    CoreModelCapabilities core, @Nullable AnthropicReasoningCapabilities reasoning)
    implements ModelCapabilities {

  public boolean supportsReasoning() {
    return reasoning != null;
  }

  @Override public List<Modality> userMessageModalities() { return core.userMessageModalities(); }
  @Override public List<Modality> toolResultModalities() { return core.toolResultModalities(); }
  @Override public List<Modality> assistantMessageModalities() { return core.assistantMessageModalities(); }
}
```
(Import `...capabilities.CoreModelCapabilities`, `...capabilities.ModelCapabilities`, `...ModelCapabilities.Modality`, `java.util.List`, `Nullable`.)

- [ ] **Step 5: Reshape `AnthropicModelCapabilitiesData`**

Prune the 3 flags and `supports_reasoning`; add the typed `provider`:
```java
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AnthropicModelCapabilitiesData(
    @Nullable InputModalities inputModalities,
    @Nullable OutputModalities outputModalities,
    @Nullable Integer contextWindow,
    @Nullable Integer maxOutputTokens,
    @Nullable AnthropicProviderCapabilities provider)
    implements ModelCapabilitiesData<AnthropicModelCapabilities> {

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record InputModalities(
      @Nullable List<Modality> userMessage, @Nullable List<Modality> toolResult) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record OutputModalities(@Nullable List<Modality> assistantMessage) {}

  @Override
  public AnthropicModelCapabilities toModelCapabilities() {
    return new AnthropicModelCapabilities(
        new CoreModelCapabilities(
            userMessageModalities(), toolResultModalities(), assistantMessageModalities(),
            contextWindow, maxOutputTokens),
        provider == null ? null : provider.reasoning());
  }

  private List<Modality> userMessageModalities() { /* same null→[TEXT] fallback as before */ }
  private List<Modality> toolResultModalities() { /* … */ }
  private List<Modality> assistantMessageModalities() { /* … */ }
}
```
Keep the three private modality accessors exactly as they are today (null → `List.of(Modality.TEXT)`).

Note the `provider` field default: with `@JsonInclude(ALWAYS)` and Jackson, an absent `provider` deserialises to `null` (record component), which `toModelCapabilities` maps to `reasoning == null`. Confirm `provider.reasoning` kebab keys (`thinking-modes`) resolve via the `@JsonProperty` on `AnthropicReasoningCapabilities`.

- [ ] **Step 6: Restructure `ModelCapabilitiesProperties`** (the Spring binding shape)

```java
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.ALWAYS)
record ModelCapabilitiesProperties(
    @Nullable InputModalities inputModalities,
    @Nullable OutputModalities outputModalities,
    @Nullable Integer contextWindow,
    @Nullable Integer maxOutputTokens,
    @Nullable Map<String, Object> provider) {   // opaque provider-specific bag

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonInclude(JsonInclude.Include.ALWAYS)
  record InputModalities(@Nullable List<Modality> userMessage, @Nullable List<Modality> toolResult) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonInclude(JsonInclude.Include.ALWAYS)
  record OutputModalities(@Nullable List<Modality> assistantMessage) {}
}
```
Import `java.util.Map`. `CapabilityMatrixFactory` still `valueToTree`s this — verify (no change needed): the `provider` map serialises into the tree under `provider` with its literal kebab keys, which the resolver deep-merges and the Anthropic DTO reads. Confirm Spring binds a nested `provider:` YAML block (with a nested `reasoning:` map + lists) into `Map<String,Object>` — it does (recursive map/list binding).

- [ ] **Step 7: Prune `ModelCapabilitiesOverride`**

Drop `supportsReasoning`, `supportsReasoningSignatureRoundtrip`, `supportsPromptCaching`, `supportsParallelToolCalls` from the record components and from `toSparseJsonNode` (remove the four `root.put(...)` blocks and the corresponding params). Keep `userMessageModalities`/`toolResultModalities`/`assistantMessageModalities`/`contextWindow`/`maxOutputTokens`. **Note (deferred):** per-element FEEL override of the `reasoning` descriptor is out of R1 scope — the override still covers modalities + limits, its primary use. Update the class javadoc (it currently references `ModelCapabilitiesData` and lists the removed flags — the R0 review flagged this as stale; fix it here).

Update `ModelCapabilitiesOverrideTest`/`CapabilityMatrixOverrideTest` and any other caller for the reduced constructor arity.

- [ ] **Step 8: Update the matrix YAML**

In `model-capabilities.yaml`, `anthropic-messages`:
1. In `defaults:` remove `supports-prompt-caching` and `supports-parallel-tool-calls` (and `supports-reasoning-signature-roundtrip` if present in defaults). Keep `supports-reasoning: false`? — **No**: `supports-reasoning` is derived now; remove it from defaults and all model entries.
2. Remove `supports-reasoning` / `supports-reasoning-signature-roundtrip` / `supports-prompt-caching` / `supports-parallel-tool-calls` from every model entry.
3. Add a `provider.reasoning` block to each reasoning-capable model per the spec §1c gating table. Concrete values (author exactly these; kebab-case inside `provider`):

| Model entry (pattern) | `thinking-modes` | `effort-levels` |
|---|---|---|
| `claude-fable` (`claude-fable-*`) | `[adaptive]` (always-on ⇒ no `disabled`) | `[low, medium, high, xhigh, max]` |
| `claude-sonnet-5` (`claude-sonnet-5*`) | `[adaptive, disabled]` | `[low, medium, high, xhigh, max]` |
| `claude-opus-4-6-plus` (`claude-opus-4-6*`,`4-7*`,`4-8*`) | `[adaptive, disabled]` | `[low, medium, high, xhigh, max]` |
| `claude-sonnet-4-6-plus` (`claude-sonnet-4-6*`) | `[enabled, adaptive, disabled]` | `[low, medium, high, xhigh, max]` |
| `claude-opus-4-5` (`claude-opus-4-5*`) | `[enabled, disabled]` | `[low, medium, high, max]` |
| `claude-sonnet-4-5` (`claude-sonnet-4-5*`) | `[enabled, disabled]` | `[]` (omit `effort-levels`) |
| `claude-opus-4-1` (`claude-opus-4-1*`) | `[enabled, disabled]` | `[]` (omit) |
| `claude-haiku` (`claude-haiku-*`) | `[enabled, disabled]` | `[]` (omit) |

Example entry:
```yaml
claude-opus-4-6-plus:
  pattern: [claude-opus-4-6*, claude-opus-4-7*, claude-opus-4-8*]
  capabilities:
    context-window: 1000000
    max-output-tokens: 128000
    provider:
      reasoning:
        thinking-modes: [adaptive, disabled]
        effort-levels: [low, medium, high, xhigh, max]
```
Update the YAML header comment that documents the flags (remove the pruned ones; document the `provider.reasoning` shape + the "never in defaults" rule).

- [ ] **Step 9: Update `ModelCapabilitiesResolverTest`**

Its fixtures build `AnthropicModelCapabilitiesData` with the old flat flags. Update to the new constructor `(inputModalities, outputModalities, contextWindow, maxOutputTokens, provider)`. Where a fixture set `supportsReasoning=true` etc., either drop it (no longer a field) or, if the test's intent was reasoning, set `provider = new AnthropicProviderCapabilities(new AnthropicReasoningCapabilities(...))`. Reads of `caps.supportsReasoning()` still work (derived); reads of the 3 pruned flags must be removed. `contextWindow()`/`maxOutputTokens()` stay via `caps.core()`.

- [ ] **Step 10: Build green**

Run: `mvn clean install -f connectors/agentic-ai/pom.xml`. Expected BUILD SUCCESS, all tests pass. This task is behaviour-identical *except* the new reasoning descriptors are now resolvable (asserted in Step 1).

- [ ] **Step 11: Commit**
```bash
git add connectors/agentic-ai/connector-agentic-ai/src connectors/agentic-ai/connector-agentic-ai/src/main/resources/capabilities/model-capabilities.yaml
git commit -m "Add Anthropic reasoning capability descriptor and align config binding via provider bag"
```

---

### Task 2: Config surface — thinking + effort on `AnthropicModelParameters`

**Files:**
- Modify: `.../model/request/chatmodel/AnthropicChatModel.java` (`AnthropicModelParameters` + new nested `AnthropicThinking` + `ThinkingDisplay`).
- Test: `.../model/request/chatmodel/AnthropicChatModelTest.java`.
- Regenerate: v2 element templates.

**Interfaces produced:**
- `AnthropicModelParameters(@Nullable Integer maxTokens, @Nullable Double temperature, @Nullable Double topP, @Nullable Integer topK, @Valid @Nullable AnthropicThinking thinking, @Nullable AnthropicEffort effort, @Nullable String customEffort)`
- `record AnthropicThinking(@NotNull ThinkingMode mode, @Min(1024) @Nullable Integer budgetTokens, @Nullable ThinkingDisplay display)`
- `enum ThinkingDisplay { SUMMARIZED, OMITTED }`

- [ ] **Step 1: Write the failing config test**

In `AnthropicChatModelTest`, add a deserialization/validation test: a JSON/config with `thinking: {mode: ENABLED, budgetTokens: 2048}` + `effort: HIGH` binds to the record; `effort: CUSTOM` + `customEffort: "ultra"` binds; `budgetTokens: 512` fails `@Min(1024)` validation. Follow the existing test's binding/validation harness in that file.

- [ ] **Step 2: Run — expect failure** (`thinking`/`effort`/`customEffort` unknown).

> **CRITICAL — lowercase enum values (carried from Task 1).** Task 1 gave `ThinkingMode`/`AnthropicEffort` lowercase `@JsonProperty` values (`enabled`/`adaptive`/`disabled`, `low`/`medium`/`high`/`xhigh`/`max`/`custom`) so they deserialize the matrix YAML. The connector request config is deserialized by the SAME Jackson enums, so the element-template dropdown **choice values must be the lowercase serialized names**, not the constant names — otherwise a modeler-selected `"ADAPTIVE"` fails to bind. Likewise the `PropertyCondition` `equals` values are lowercase (`"enabled"`, `"adaptive"`, `"custom"`) as written below. Give `ThinkingDisplay` the same lowercase `@JsonProperty` treatment (`summarized`/`omitted`) and use lowercase dropdown choices for it too. When adding `PropertyType.Dropdown` choices, use each enum's `@JsonProperty` value as the choice value; confirm the generated JSON shows lowercase `value` fields.

- [ ] **Step 3: Add the enums + `AnthropicThinking`**

`ThinkingDisplay` (config-only, with lowercase `@JsonProperty` per the note above) co-located with `AnthropicThinking` inside `AnthropicChatModel` (or as a sibling record). Import `ThinkingMode`/`AnthropicEffort` from `framework/anthropic` (see enum-placement decision).
```java
public record AnthropicThinking(
    @Nullable   // optional: blank ⇒ omit `thinking` ⇒ model default (ratified). No @NotNull.
    @TemplateProperty(group = "model", label = "Thinking mode",
        description = "Extended thinking mechanism. ENABLED = manual token budget (older models); "
            + "ADAPTIVE = model-managed (newer models); DISABLED = off. Support varies by model.",
        type = TemplateProperty.PropertyType.Dropdown, optional = true)
    ThinkingMode mode,
    @Min(1024)
    @TemplateProperty(group = "model", label = "Thinking budget tokens",
        tooltip = "Max tokens the model may spend on extended thinking. Required and used only when "
            + "thinking mode is ENABLED (min 1024).",
        type = TemplateProperty.PropertyType.Number, feel = FeelMode.required, optional = true,
        condition = @TemplateProperty.PropertyCondition(
            property = "configuration.anthropic.model.parameters.thinking.mode", equals = "enabled"))
    @Nullable Integer budgetTokens,
    @TemplateProperty(group = "model", label = "Thinking display",
        tooltip = "Adaptive-thinking output display (SUMMARIZED or OMITTED). Applies only to ADAPTIVE.",
        type = TemplateProperty.PropertyType.Dropdown, optional = true,
        condition = @TemplateProperty.PropertyCondition(
            property = "configuration.anthropic.model.parameters.thinking.mode", equals = "adaptive"))
    @Nullable ThinkingDisplay display) {}

public enum ThinkingDisplay {
  @com.fasterxml.jackson.annotation.JsonProperty("summarized") SUMMARIZED,
  @com.fasterxml.jackson.annotation.JsonProperty("omitted") OMITTED
}
```
Verify the `@TemplateProperty.PropertyCondition` `property` path matches the actual v2 binding prefix — inspect the generated template for the existing `webSearchVersion` condition path (`configuration.anthropic.enableWebSearch`) and mirror the nesting for `...model.parameters.thinking.mode`. Adjust the path to whatever the generator actually emits (confirm by regenerating and reading the JSON).

- [ ] **Step 4: Add the three fields to `AnthropicModelParameters`**

Append after `topK`:
```java
@Valid @Nullable AnthropicThinking thinking,
@TemplateProperty(group = "model", label = "Effort",
    tooltip = "General effort dial (affects text, tool calls and thinking). Not supported on all "
        + "models. CUSTOM sends the free-text value below verbatim. Unset ⇒ model default (high).",
    type = TemplateProperty.PropertyType.Dropdown, optional = true)
@Nullable AnthropicEffort effort,
@TemplateProperty(group = "model", label = "Custom effort",
    tooltip = "Free-text effort value sent verbatim when Effort = CUSTOM.",
    type = TemplateProperty.PropertyType.String, feel = FeelMode.optional, optional = true,
    condition = @TemplateProperty.PropertyCondition(
        property = "configuration.anthropic.model.parameters.effort", equals = "custom"))
@Nullable String customEffort
```
(Confirm `PropertyType.Dropdown` renders enum choices; if the generator needs explicit choices, follow the existing dropdown pattern in the codebase — check `TemplateDiscriminatorProperty`/existing dropdowns. If enums don't auto-populate a dropdown, use `PropertyType.String` with a `choices`-equivalent or document the accepted values in the tooltip. Resolve by regenerating and inspecting.)

- [ ] **Step 5: Regenerate templates + verify**

Run: `mvn clean compile -f connectors/agentic-ai/pom.xml`. Read the v2 template JSON diff: the three new properties appear under the model group with correct conditions; **v1 templates are unchanged**; the derived v2 sub-process template regenerated. Confirm no unintended version bump.

- [ ] **Step 6: Build green + commit**
```bash
mvn clean install -f connectors/agentic-ai/pom.xml
git add connectors/agentic-ai/connector-agentic-ai/src connectors/agentic-ai/connector-agentic-ai/element-templates
git commit -m "Add Anthropic thinking and effort request configuration"
```

---

### Task 3: Request-side — validation + mapping to the SDK

**Files:**
- Create: `.../anthropic/AnthropicReasoningValidator.java`
- Modify: `.../anthropic/AnthropicMessageRequestConverter.java`
- Test: new `AnthropicReasoningValidatorTest.java`; extend `AnthropicMessageRequestConverterTest.java`.

**Interfaces:**
- Consumes: `AnthropicModelParameters.thinking/effort/customEffort` (Task 2), `AnthropicModelCapabilities.reasoning` + `supportsReasoning()` (Task 1).
- Produces: validated thinking/effort applied to `MessageCreateParams.Builder`.

- [ ] **Step 1: Write the failing validator tests** (spec §6 rules)

`AnthropicReasoningValidatorTest`: given an `AnthropicReasoningCapabilities` descriptor + config, assert `ConnectorException` (code `ERROR_CODE_FAILED_MODEL_CALL`) with an actionable message for: thinking set + `reasoning == null` (known model) → fail; `mode` ∉ `thinkingModes` → fail; `ENABLED` + null `budgetTokens` → fail; `effort` (non-CUSTOM) + empty `effortLevels` → fail; `effort` ∉ `effortLevels` → fail; `effort == CUSTOM` → pass; unmatched-model (descriptor null + a "matched=false" signal) + thinking set → **pass-through** (no throw). Assert valid configs do not throw.

- [ ] **Step 2: Run — expect failure** (validator doesn't exist).

- [ ] **Step 3: Implement `AnthropicReasoningValidator`**

A small stateless validator (static method or injected bean). Signature:
```java
static void validate(@Nullable AnthropicModelParameters params,
    @Nullable AnthropicReasoningCapabilities reasoning, boolean modelMatched, String modelId)
```
Rules per spec §6. **"thinking set" means `thinking != null && thinking.mode() != null`** — a `thinking` object with a null `mode` (modeler left the dropdown blank) is treated as unset (no thinking param, no validation). **`effort == CUSTOM` fully bypasses ALL effort validation (ratified)** — including rule 1's effort trigger — since it is sent verbatim and the API decides; it contributes to no rule. **Unmatched-model pass-through:** if `!modelMatched`, return without validating (custom/unknown models stay usable). If `modelMatched && reasoning == null && (thinkingModeSet || (effort != null && effort != CUSTOM))` → fail rule 1. Throw `ConnectorException(ERROR_CODE_FAILED_MODEL_CALL, "<actionable message>")`.

**`modelMatched` signal:** the resolver currently doesn't expose whether a model matched. Add a minimal signal: extend the resolver to also report a match indicator (spec §6). Options — pick the least invasive and state it in the report: (a) a new `boolean` method `matches(apiFamily, modelId, backend)` on `ModelCapabilitiesResolver`; (b) resolve into a small `Resolved<T>(T capabilities, boolean matched)` wrapper. **Recommended: (a)** — a separate `matches(...)` query keeps `resolve(...)`'s signature and all existing call sites unchanged, and the factory calls it once. If (a) proves awkward (e.g. duplicates the match logic), fall back to pass-through-both (skip rule 1) and document it, per spec §6.

- [ ] **Step 4: Wire the validator + mapping into `AnthropicMessageRequestConverter`**

In `toMessageCreateParams`, after resolving params and before/at `applyModelParameters`, call the validator (the converter already receives `AnthropicModelCapabilities capabilities` after R0 — pass `capabilities.reasoning()` + the matched signal + `model.anthropic().model().model()`). Extend `applyModelParameters` (or a new `applyReasoning`) to map thinking + effort (spec §5):
```java
if (params.thinking() != null && params.thinking().mode() != null) {  // mode null ⇒ no thinking param (model default)
  var t = params.thinking();
  switch (t.mode()) {
    case ENABLED -> builder.thinking(BetaThinkingConfigParam.ofEnabled(
        BetaThinkingConfigEnabled.builder().budgetTokens(t.budgetTokens().longValue()).build()));
    case ADAPTIVE -> {
      var ab = BetaThinkingConfigAdaptive.builder();
      if (t.display() != null) ab.display(BetaThinkingConfigAdaptive.Display.of(t.display().name().toLowerCase()));
      builder.thinking(BetaThinkingConfigParam.ofAdaptive(ab.build()));
    }
    case DISABLED -> builder.thinking(BetaThinkingConfigParam.ofDisabled(
        BetaThinkingConfigDisabled.builder().build()));
  }
}
if (params.effort() != null) {
  var eff = params.effort() == AnthropicEffort.CUSTOM
      ? BetaOutputConfig.Effort.of(params.customEffort())
      : BetaOutputConfig.Effort.of(params.effort().name().toLowerCase());
  builder.outputConfig(BetaOutputConfig.builder().effort(eff).build());
}
```
Verify the `Display.of(...)` lowercase mapping (`SUMMARIZED`→`"summarized"`) and effort lowercase (`XHIGH`→`"xhigh"`) match the wire values — confirm via a serialization round-trip assertion in the converter test (spec §9 item 3). If the enum's wire form differs, map explicitly.

`max_tokens`/effort interaction: document only (tooltip already covers it, Task 2); no clamp.

- [ ] **Step 5: Extend `AnthropicMessageRequestConverterTest`**

Assert the built `MessageCreateParams` carries the right `thinking` union variant + `output_config.effort` for enabled/adaptive/disabled + each effort level + CUSTOM. Reuse the file's existing `caps(...)` helper (extend it to supply a `reasoning` descriptor so validation passes). Assert a validation failure throws before the SDK is touched.

- [ ] **Step 6: Build green + commit**
```bash
mvn clean install -f connectors/agentic-ai/pom.xml
git commit -am "Validate and map Anthropic thinking and effort into the messages request"
```

---

### Task 4: Response side — `ReasoningContent` Option A + unconditional round-trip

**Files:**
- Modify: `.../anthropic/AnthropicMessageResponseConverter.java`
- Modify: `.../anthropic/AnthropicContentConverter.java`
- Test: `AnthropicMessageResponseConverterTest`, `AnthropicContentConverterTest`.

- [ ] **Step 1: Write the failing round-trip tests**

`AnthropicMessageResponseConverterTest`: a `thinking` block maps to `ReasoningContent(text=thinking, providerPayload=<raw block Map {type,thinking,signature}>, null)`; a `redacted_thinking` block maps to `ReasoningContent(text=null, providerPayload=<raw {type:"redacted_thinking",data}>, null)`.
`AnthropicContentConverterTest`: a `ReasoningContent` with a non-null `providerPayload` (a raw thinking-block Map) re-emits a `BetaContentBlockParam` byte-identical to the source block via the SDK mapper; a `ReasoningContent` with null `providerPayload` emits nothing.

- [ ] **Step 2: Run — expect failure** (current converter stores bare signature string; content converter skips reasoning).

- [ ] **Step 3: Response converter → Option A**

Replace the two `ReasoningContent` constructions (current lines ~100 and ~103) with the raw-block-Map form (mirrors the `ProviderContent` else-branch already in the file):
```java
} else if (block.isThinking()) {
  final Map<String, Object> raw =
      ObjectMappers.jsonMapper().convertValue(block, new TypeReference<Map<String, Object>>() {});
  content.add(new ReasoningContent(block.thinking().orElseThrow().thinking(), raw, null));
} else if (block.isRedactedThinking()) {
  final Map<String, Object> raw =
      ObjectMappers.jsonMapper().convertValue(block, new TypeReference<Map<String, Object>>() {});
  content.add(new ReasoningContent(null, raw, null));
}
```
Update the class javadoc (it currently says "signature/redacted payload preserved in providerPayload" — clarify it is now the full raw block Map, and that reasoning IS now re-emitted).

- [ ] **Step 4: Content converter → unconditional re-emission** (spec §4b)

Replace `case ReasoningContent ignored -> {}` with:
```java
case ReasoningContent rc -> {
  if (rc.providerPayload() != null) {
    blocks.add(
        ObjectMappers.jsonMapper().convertValue(rc.providerPayload(), BetaContentBlockParam.class));
  }
}
```
Update the stale comment (lines ~60–62) to state re-emission is now unconditional (payload-guarded), matching the `ProviderContent` branch below it.

- [ ] **Step 5: Verify the pure-reasoning-turn assumption** (spec §9 item 4)

Add a converter test that an assistant message whose only content is a `ReasoningContent` (plus a tool call) re-emits `thinking` before `tool_use` (ordering per §1d), and a message with only reasoning + no tool call/text still produces a non-empty content array. (Full API-level 400 verification lands in the e2e task.)

- [ ] **Step 6: Build green + commit**
```bash
mvn clean install -f connectors/agentic-ai/pom.xml
git commit -am "Round-trip Anthropic thinking blocks losslessly via ReasoningContent"
```

---

### Task 5: Native e2e (WireMock)

**Files:**
- Test: extend the native Anthropic WireMock harness — `NativeAnthropicMessagesWireFormatFixture` / `NativeAnthropicMessagesSseChatModelStubs` and a new IT (e.g. `NativeAnthropicReasoningEffortIT` under `wiremock/anthropic/`), following `BaseAiAgentJobWorkerTest`/existing native IT patterns.

**Rationale (AGENTS.md e2e rule):** this touches the full request → LLM → response cycle **and** element-template behaviour (new config properties reach the wire), so an e2e is required.

- [ ] **Step 1: Read the existing native e2e** to mirror its stub + fixture + assertion style. Do NOT invent a new harness. Run one existing native IT first to confirm `element-templates-cli` is on PATH and the module builds (`-am`).

- [ ] **Step 2: Add scenarios** (each: configure the v2 element with the new params, stub the SSE response, assert the outbound request body + the resulting conversation):
  - `thinking: {mode: ENABLED, budgetTokens: 2048}` → request carries `thinking:{type:enabled,budget_tokens:2048}`.
  - `thinking: {mode: ADAPTIVE, display: SUMMARIZED}` → `thinking:{type:adaptive,display:summarized}`.
  - `thinking: {mode: DISABLED}` → `thinking:{type:disabled}`.
  - `effort: XHIGH` → `output_config:{effort:"xhigh"}`; `effort: CUSTOM, customEffort: "ultra"` → `output_config:{effort:"ultra"}`.
  - **Round-trip replay:** a first response containing a signed `thinking` block + a `tool_use`; assert the *second* request (after the tool result) replays the thinking block byte-identical, in order before the tool result, and the API stub accepts it (no 400).
  - **Validation failure:** a known non-reasoning model (or `ENABLED` on an adaptive-only model) → the job fails fast with the connector's actionable error, before any HTTP call.

- [ ] **Step 3: Run the e2e**
Run: `mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -am -Dtest=NativeAnthropicReasoningEffortIT`
Expected: all scenarios green.

- [ ] **Step 4: Commit**
```bash
git commit -am "Add native Anthropic reasoning and effort e2e coverage"
```

---

## Self-Review (plan author)

**Spec coverage:** prune + binding alignment (§3a/§3b) → Task 1; matrix descriptor (§3, §1c table) → Task 1 Step 8; config surface (§2) → Task 2; request mapping (§5) + validation (§6) → Task 3; response Option A + unconditional round-trip (§4) → Task 4; e2e (§7 tests) → Task 5. SDK facts (§9) pinned in Global Constraints + verified in Task 3/4 steps.

**Open items deliberately left to implementer judgment (each with a stated default):** (1) enum placement (`framework/anthropic` vs neutral) — Task 1 note, default `framework/anthropic`; (2) `modelMatched` signal shape — Task 3 Step 3, default a `matches(...)` resolver method, fallback pass-through-both; (3) exact `@TemplateProperty` dropdown mechanics + condition path — Task 2, resolved by regenerating + inspecting the JSON. These are genuine "confirm against the generated artifact / existing pattern" points, not placeholders.

**Placeholder scan:** the three modality private accessors in Task 1 Step 5 say "same as today" — that is a deliberate *preserve-verbatim* instruction (the code exists in the current file), not a missing spec. Everything else carries actual code.

**Type consistency:** `AnthropicModelCapabilities(core, reasoning)`, `AnthropicReasoningCapabilities(thinkingModes, effortLevels)`, `AnthropicThinking(mode, budgetTokens, display)`, `AnthropicModelParameters(..., thinking, effort, customEffort)`, and `ThinkingMode`/`AnthropicEffort`/`ThinkingDisplay` names are identical across all tasks and match the spec.
