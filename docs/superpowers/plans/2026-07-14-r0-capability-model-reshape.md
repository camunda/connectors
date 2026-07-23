# R0 — Capability Model Reshape Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn `ModelCapabilities` from a flat neutral record into a minimal neutral **interface** implemented by provider-specific capability records, so provider-specific data (reasoning, caching, parallel-tool-calls, token limits) lives where it is consumed — a behaviour-identical refactor that unblocks R1.

**Architecture:** `ModelCapabilities` becomes an interface exposing only the cross-provider contract (the three modality lists) plus the nested `Modality` enum. A shared `CoreModelCapabilities` record implements it and carries the provider-agnostic data (modalities + context/output-token limits); it doubles as the LangChain4j bridge's neutral impl. `AnthropicModelCapabilities` implements the interface by delegating the modality methods to a `CoreModelCapabilities` component and adds Anthropic-consumed flags. The resolver's deep-merge cascade stays 100% provider-agnostic (`JsonNode`); only the final materialisation step is parameterised by a provider capability class via a new `ModelCapabilitiesData<T>` projection interface (Anthropic's projection = `AnthropicModelCapabilitiesData`).

**Tech Stack:** Java 21 records + sealed-free interfaces, Jackson (tree merge + `treeToValue`), JUnit 5 + Mockito + AssertJ. Module: `connectors/agentic-ai/connector-agentic-ai`.

## Global Constraints

- **Behaviour-identical.** No resolved capability value changes, no wire output changes, no persisted/template BC impact. The existing resolver/strategy/converter test suite must stay green (adjusted only for the type-shape move, never for behaviour).
- **Neutral interface stays minimal** — only the three modality methods (`userMessageModalities`, `toolResultModalities`, `assistantMessageModalities`) + the nested `Modality` enum. Every custom provider must implement it, so every method is a tax on all of them. Do NOT add `contextWindow`/`maxOutputTokens`/any flag to the interface.
- **No generic code downcasts `ModelCapabilities`.** No `instanceof AnthropicModelCapabilities` (or cast) in any framework-generic class (`multimodal/**`, `framework/api/**`, `agent/**`). Provider-internal classes (`framework/anthropic/**`) may use the concrete type freely.
- **Resolver stays optional / provider-agnostic.** The `capabilities` package must not import `framework/anthropic/**`. A provider can supply its own `ModelCapabilities` without the resolver.
- **Framework-agnostic-core invariant** (AGENTS.md §24): no vendor SDK imports outside `framework/langchain4j/**` and `framework/anthropic/**`; the domain/`capabilities`/`api` packages stay neutral.
- **Null safety:** all production code is `@NullMarked`; use `org.jspecify.annotations.Nullable`. Never suppress null errors.
- SPI-evolution rule: any method added to the neutral interface in a *later* chunk is a `default` method — not relevant to R0 (no methods added), noted so the reviewer does not ask for it.
- Build/verify command (run outside the sandbox — Mockito needs it): `mvn clean install -f connectors/agentic-ai/pom.xml`. Spotless + license are enforced by pre-commit hooks; if deviating from `mvn install`, run `mvn spotless:apply` and `mvn license:format`.

---

## File Structure

**Capabilities package** (`.../aiagent/framework/capabilities/`, provider-neutral):
- `ModelCapabilities.java` — **CHANGED**: record → interface (3 modality methods + nested `Modality` enum).
- `CoreModelCapabilities.java` — **NEW**: record implementing `ModelCapabilities` (modalities + `contextWindow` + `maxOutputTokens`). Shared component + bridge neutral impl.
- `ModelCapabilitiesData.java` — **REPLACED**: was the Anthropic-shaped sparse record; becomes a generic projection interface `ModelCapabilitiesData<T extends ModelCapabilities> { T toModelCapabilities(); }`.
- `ModelCapabilitiesResolver.java` — **CHANGED**: `resolve(...)` gains a `Class<? extends ModelCapabilitiesData<T>>` param and returns `T`.
- `ModelCapabilitiesResolverImpl.java` — **CHANGED**: generic `materialise`, provider-agnostic conservative base tree built inline; drops the two `CONSERVATIVE_DEFAULTS*` constants.
- `ModelCapabilitiesOverride.java` — **UNCHANGED** (still projects onto the flat sparse tree the Anthropic projection reads; verify it still compiles).

**Anthropic package** (`.../aiagent/framework/anthropic/`, provider-internal):
- `AnthropicModelCapabilities.java` — **NEW**: record `(CoreModelCapabilities core, boolean supportsReasoning, boolean supportsReasoningSignatureRoundtrip, boolean supportsPromptCaching, boolean supportsParallelToolCalls)` implements `ModelCapabilities`, delegating modality methods to `core`.
- `AnthropicModelCapabilitiesData.java` — **NEW**: the old sparse-DTO body (flat nullable fields + nested `InputModalities`/`OutputModalities`), implementing `ModelCapabilitiesData<AnthropicModelCapabilities>`; `toModelCapabilities()` builds the record.
- `AnthropicChatModelApiFactory.java` — **CHANGED**: pass `AnthropicModelCapabilitiesData.class` to `resolve(...)`; hold the concrete `AnthropicModelCapabilities`.
- `AnthropicChatModelApi.java` — **CHANGED**: field/ctor type `AnthropicModelCapabilities`; `capabilities()` widens to `ModelCapabilities`.
- `AnthropicMessageRequestConverter.java` — **CHANGED**: `toMessageCreateParams(...)` param type `AnthropicModelCapabilities`; read `capabilities.core().maxOutputTokens()`.

**LangChain4j bridge** (`.../aiagent/framework/langchain4j/`):
- `Langchain4JChatModelApi.java` — **CHANGED**: `BRIDGE_CAPABILITIES` becomes `new CoreModelCapabilities(...)`.

**Tests** — updated in lockstep (enumerated in the task): `ModelCapabilitiesTest`, `ModelCapabilitiesResolverTest`, `BundledCapabilityMatrixTest`, `CapabilityMatrixOverrideTest` (capabilities pkg); `AnthropicChatModelApiFactoryTest`, `AnthropicChatModelApiTest`, `AnthropicMessageRequestConverterTest` (anthropic pkg); `Langchain4JChatModelApiTest`; `CapabilityAwareToolCallResultStrategyTest`, `ToolResultDocumentWindowingTest` (multimodal pkg); `JobWorkerAgentRequestHandlerTest`, `OutboundConnectorAgentRequestHandlerTest`, `LlmProviderChatModelApiConfigurationRegistryTest` (agent/framework pkg).

**Why one task:** flipping `ModelCapabilities` from a record to an interface breaks every consumer at once — there is no compiling intermediate. The change is therefore a single atomic task whose deliverable is "module compiles + full test suite green + new type unit tests pass." Steps are ordered so a reviewer can follow the reshape; the safety net is the pre-existing behavioural suite staying green.

---

### Task 1: Reshape `ModelCapabilities` to a neutral interface with provider records

**Files:**
- Create: `.../capabilities/CoreModelCapabilities.java`
- Create: `.../anthropic/AnthropicModelCapabilities.java`
- Create: `.../anthropic/AnthropicModelCapabilitiesData.java`
- Modify: `.../capabilities/ModelCapabilities.java`
- Modify: `.../capabilities/ModelCapabilitiesData.java`
- Modify: `.../capabilities/ModelCapabilitiesResolver.java`
- Modify: `.../capabilities/ModelCapabilitiesResolverImpl.java`
- Modify: `.../anthropic/AnthropicChatModelApiFactory.java`
- Modify: `.../anthropic/AnthropicChatModelApi.java`
- Modify: `.../anthropic/AnthropicMessageRequestConverter.java` (`toMessageCreateParams` + `resolveMaxTokens`)
- Modify: `.../langchain4j/Langchain4JChatModelApi.java`
- Test: the 13 test files listed under File Structure.

**Interfaces:**
- Consumes: nothing from prior tasks (first task).
- Produces (relied on by R1):
  - `interface ModelCapabilities { List<Modality> userMessageModalities(); List<Modality> toolResultModalities(); List<Modality> assistantMessageModalities(); enum Modality { TEXT, IMAGE, DOCUMENT, AUDIO, VIDEO } }`
  - `record CoreModelCapabilities(List<Modality> userMessageModalities, List<Modality> toolResultModalities, List<Modality> assistantMessageModalities, @Nullable Integer contextWindow, @Nullable Integer maxOutputTokens) implements ModelCapabilities`
  - `record AnthropicModelCapabilities(CoreModelCapabilities core, boolean supportsReasoning, boolean supportsReasoningSignatureRoundtrip, boolean supportsPromptCaching, boolean supportsParallelToolCalls) implements ModelCapabilities` — R1 will add a `@Nullable AnthropicReasoningCapabilities reasoning` component here.
  - `interface ModelCapabilitiesData<T extends ModelCapabilities> { T toModelCapabilities(); }`
  - `<T extends ModelCapabilities> T ModelCapabilitiesResolver.resolve(String apiFamily, String modelId, @Nullable String backend, Optional<ModelCapabilitiesOverride> override, Class<? extends ModelCapabilitiesData<T>> dataClass)`
  - `AnthropicModelCapabilitiesData implements ModelCapabilitiesData<AnthropicModelCapabilities>` — Jackson-deserialisable from the flat merged snake_case tree.

- [ ] **Step 1: Write the new-type unit tests (red)**

Replace the body of `ModelCapabilitiesTest.java` (it currently constructs the old flat record; that constructor is going away). New file content:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.provider.capabilities;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.provider.capabilities.ModelCapabilities.Modality;

import java.util.List;

import org.junit.jupiter.api.Test;

class ModelCapabilitiesTest {

	@Test
	void modalityEnumValuesAreOrderedTextImageDocumentAudioVideo() {
		assertThat(Modality.values())
			.containsExactly(
				Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT, Modality.AUDIO, Modality.VIDEO);
	}

	@Test
	void coreModelCapabilitiesExposesModalitiesAndLimits() {
		final ModelCapabilities caps =
			new CoreModelCapabilities(
				List.of(Modality.TEXT, Modality.IMAGE),
				List.of(Modality.TEXT),
				List.of(Modality.TEXT),
				128000,
				4096);

		assertThat(caps.userMessageModalities()).containsExactly(Modality.TEXT, Modality.IMAGE);
		assertThat(caps.toolResultModalities()).containsExactly(Modality.TEXT);
		assertThat(caps.assistantMessageModalities()).containsExactly(Modality.TEXT);
		assertThat(((CoreModelCapabilities) caps).contextWindow()).isEqualTo(128000);
		assertThat(((CoreModelCapabilities) caps).maxOutputTokens()).isEqualTo(4096);
	}

	@Test
	void coreModelCapabilitiesAllowsNullLimits() {
		final var caps =
			new CoreModelCapabilities(
				List.of(Modality.TEXT), List.of(Modality.TEXT), List.of(Modality.TEXT), null, null);

		assertThat(caps.contextWindow()).isNull();
		assertThat(caps.maxOutputTokens()).isNull();
	}
}
```

Create `.../anthropic/AnthropicModelCapabilitiesTest.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.provider.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.provider.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.provider.capabilities.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.provider.capabilities.ModelCapabilities.Modality;

import java.util.List;

import org.junit.jupiter.api.Test;

class AnthropicModelCapabilitiesTest {

	private static final CoreModelCapabilities CORE =
		new CoreModelCapabilities(
			List.of(Modality.TEXT, Modality.IMAGE),
			List.of(Modality.TEXT),
			List.of(Modality.TEXT),
			200000,
			8192);

	@Test
	void delegatesModalityMethodsToCore() {
		final ModelCapabilities caps = new AnthropicModelCapabilities(CORE, true, true, false, false);

		assertThat(caps.userMessageModalities()).containsExactly(Modality.TEXT, Modality.IMAGE);
		assertThat(caps.toolResultModalities()).containsExactly(Modality.TEXT);
		assertThat(caps.assistantMessageModalities()).containsExactly(Modality.TEXT);
	}

	@Test
	void exposesAnthropicSpecificFlags() {
		final var caps = new AnthropicModelCapabilities(CORE, true, true, false, false);

		assertThat(caps.supportsReasoning()).isTrue();
		assertThat(caps.supportsReasoningSignatureRoundtrip()).isTrue();
		assertThat(caps.supportsPromptCaching()).isFalse();
		assertThat(caps.supportsParallelToolCalls()).isFalse();
		assertThat(caps.core().contextWindow()).isEqualTo(200000);
		assertThat(caps.core().maxOutputTokens()).isEqualTo(8192);
	}
}
```

- [ ] **Step 2: Run the new tests to confirm they fail to compile / fail**

Run: `mvn -q -o test-compile -f connectors/agentic-ai/pom.xml` (or the targeted test classes).
Expected: compilation failure — `CoreModelCapabilities`, `AnthropicModelCapabilities` do not exist yet.

- [ ] **Step 3: Rewrite `ModelCapabilities.java` as the neutral interface**

Replace the whole file. Keep the copyright header. Keep the `Modality` enum verbatim (including the `@JsonProperty` lowercase annotations and the full javadoc on the enum — the resolver's raw-JSON round-trip test depends on them). Drop `@AgenticAiRecord`, the `builder()` method, `implements ModelCapabilitiesBuilder.With`, and the `AgenticAiRecord` import.

```java
package io.camunda.connector.agenticai.aiagent.provider.capabilities;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Provider-neutral capability contract that framework-generic code depends on. Today the only
 * cross-provider consumer is {@code CapabilityAwareToolCallResultStrategy}, which reads modalities
 * only — so this interface is deliberately minimal (three modality lists + the {@link Modality}
 * vocabulary). Provider-specific capability data lives on the provider's own implementing record
 * (e.g. {@code AnthropicModelCapabilities}), never here: every method added here is a tax on every
 * custom provider that must implement it. New methods added in later chunks must be {@code default}
 * so existing custom implementations keep compiling.
 */
public interface ModelCapabilities {

	List<Modality> userMessageModalities();

	List<Modality> toolResultModalities();

	List<Modality> assistantMessageModalities();

	/* Modality enum: copy the enum declaration and its full javadoc VERBATIM from the current file,
       including the @JsonProperty("text"/"image"/"document"/"audio"/"video") annotations. */
	enum Modality {
		@JsonProperty("text")
		TEXT,
		@JsonProperty("image")
		IMAGE,
		@JsonProperty("document")
		DOCUMENT,
		@JsonProperty("audio")
		AUDIO,
		@JsonProperty("video")
		VIDEO
	}
}
```

- [ ] **Step 4: Create `CoreModelCapabilities.java`**

```java
package io.camunda.connector.agenticai.aiagent.provider.capabilities;

import io.camunda.connector.agenticai.aiagent.provider.capabilities.ModelCapabilities.Modality;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Provider-agnostic capability data shared across provider capability records and usable directly
 * as a neutral {@link ModelCapabilities} (e.g. by the LangChain4j bridge). Carries the three
 * modality lists (the neutral contract) plus the provider-agnostic token-budget figures. The token
 * figures are not part of the neutral {@link ModelCapabilities} contract — they are read only by a
 * provider's own converter via the concrete type — so they live here as extra record accessors,
 * not as interface methods.
 */
public record CoreModelCapabilities(
	List<Modality> userMessageModalities,
	List<Modality> toolResultModalities,
	List<Modality> assistantMessageModalities,
	@Nullable Integer contextWindow,
	@Nullable Integer maxOutputTokens)
	implements ModelCapabilities {
}
```

- [ ] **Step 5: Replace `ModelCapabilitiesData.java` with the generic projection interface**

The old file was a package-private Anthropic-shaped record. Replace its entire body with:

```java
package io.camunda.connector.agenticai.aiagent.provider.capabilities;

/**
 * Projects a fully-merged capability matrix tree (materialised via Jackson {@code treeToValue})
 * onto a concrete, provider-specific {@link ModelCapabilities}. Each provider ships its own sparse
 * DTO implementing this interface (e.g. {@code AnthropicModelCapabilitiesData}); the resolver's
 * deep-merge cascade stays provider-agnostic and only the final materialisation is parameterised by
 * the DTO class.
 */
public interface ModelCapabilitiesData<T extends ModelCapabilities> {
	T toModelCapabilities();
}
```

- [ ] **Step 6: Create `AnthropicModelCapabilitiesData.java`** (the old sparse-DTO body, moved + retargeted)

Port the flat sparse-DTO from the old `ModelCapabilitiesData` record: keep the `@JsonNaming(SnakeCaseStrategy)` + `@JsonInclude(ALWAYS)` annotations, the nullable flat fields, and the nested `InputModalities`/`OutputModalities` records with the same annotations. Retarget `toModelCapabilities()` to build an `AnthropicModelCapabilities` wrapping a `CoreModelCapabilities`. Make it `public` (the resolver test in the capabilities package and the factory reference it).

```java
package io.camunda.connector.agenticai.aiagent.provider.anthropic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.camunda.connector.agenticai.aiagent.provider.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.provider.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.provider.capabilities.ModelCapabilitiesData;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Sparse Anthropic capability block — the shape of one merged {@code anthropic-messages} capability
 * matrix row. Each field is nullable so the resolver's deep-merge can fall through to a lower layer;
 * the fully-merged tree is projected onto {@link AnthropicModelCapabilities} via {@link
 * #toModelCapabilities()}. (Ported verbatim from the former provider-neutral {@code
 * ModelCapabilitiesData} record, now Anthropic-owned so R1 can add a typed {@code reasoning}
 * descriptor with Anthropic-specific keys.)
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AnthropicModelCapabilitiesData(
	@Nullable InputModalities inputModalities,
	@Nullable OutputModalities outputModalities,
	@Nullable Boolean supportsReasoning,
	@Nullable Boolean supportsReasoningSignatureRoundtrip,
	@Nullable Boolean supportsPromptCaching,
	@Nullable Boolean supportsParallelToolCalls,
	@Nullable Integer contextWindow,
	@Nullable Integer maxOutputTokens)
	implements ModelCapabilitiesData<AnthropicModelCapabilities> {

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record InputModalities(
		@Nullable List<Modality> userMessage, @Nullable List<Modality> toolResult) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record OutputModalities(@Nullable List<Modality> assistantMessage) {
	}

	@Override
	public AnthropicModelCapabilities toModelCapabilities() {
		return new AnthropicModelCapabilities(
			new CoreModelCapabilities(
				userMessageModalities(),
				toolResultModalities(),
				assistantMessageModalities(),
				contextWindow,
				maxOutputTokens),
			Boolean.TRUE.equals(supportsReasoning),
			Boolean.TRUE.equals(supportsReasoningSignatureRoundtrip),
			Boolean.TRUE.equals(supportsPromptCaching),
			Boolean.TRUE.equals(supportsParallelToolCalls));
	}

	private List<Modality> userMessageModalities() {
		return inputModalities != null && inputModalities.userMessage() != null
			? inputModalities.userMessage()
			: List.of(Modality.TEXT);
	}

	private List<Modality> toolResultModalities() {
		return inputModalities != null && inputModalities.toolResult() != null
			? inputModalities.toolResult()
			: List.of(Modality.TEXT);
	}

	private List<Modality> assistantMessageModalities() {
		return outputModalities != null && outputModalities.assistantMessage() != null
			? outputModalities.assistantMessage()
			: List.of(Modality.TEXT);
	}
}
```

- [ ] **Step 7: Create `AnthropicModelCapabilities.java`**

```java
package io.camunda.connector.agenticai.aiagent.provider.anthropic;

import io.camunda.connector.agenticai.aiagent.provider.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.provider.capabilities.ModelCapabilities;

import java.util.List;

/**
 * Anthropic-owned {@link ModelCapabilities}: the neutral modality contract (delegated to {@link
 * #core()}) plus the flags only the Anthropic path consumes. R1 will add a typed {@code
 * AnthropicReasoningCapabilities reasoning} component.
 */
public record AnthropicModelCapabilities(
	CoreModelCapabilities core,
	boolean supportsReasoning,
	boolean supportsReasoningSignatureRoundtrip,
	boolean supportsPromptCaching,
	boolean supportsParallelToolCalls)
	implements ModelCapabilities {

	@Override
	public List<Modality> userMessageModalities() {
		return core.userMessageModalities();
	}

	@Override
	public List<Modality> toolResultModalities() {
		return core.toolResultModalities();
	}

	@Override
	public List<Modality> assistantMessageModalities() {
		return core.assistantMessageModalities();
	}
}
```

- [ ] **Step 8: Generify `ModelCapabilitiesResolver.java`**

Change the single method signature (keep the class javadoc verbatim):

```java
<T extends ModelCapabilities> T resolve(
    String apiFamily,
    String modelId,
    @Nullable String backend,
    Optional<ModelCapabilitiesOverride> override,
    Class<? extends ModelCapabilitiesData<T>> dataClass);
```

- [ ] **Step 9: Update `ModelCapabilitiesResolverImpl.java`**

1. Delete both constants: `static final ModelCapabilities CONSERVATIVE_DEFAULTS = ...` and `private static final ModelCapabilitiesData CONSERVATIVE_DEFAULTS_DATA = ...`.
2. Build `conservativeBase` inline (provider-agnostic, byte-identical to the old base: text-only modalities, flags omitted → projected `false`, limits omitted → `null`). In the constructor replace `this.conservativeBase = mapper.valueToTree(CONSERVATIVE_DEFAULTS_DATA);` with a call to a new helper:

```java
private static JsonNode conservativeBaseTree(ObjectMapper mapper) {
  final ObjectNode root = mapper.createObjectNode();
  final ArrayNode text = mapper.createArrayNode().add("text");
  final ObjectNode input = root.putObject("input_modalities");
  input.set("user_message", text.deepCopy());
  input.set("tool_result", text.deepCopy());
  root.putObject("output_modalities").set("assistant_message", text.deepCopy());
  return root;
}
```

   Add `import com.fasterxml.jackson.databind.node.ArrayNode;`. Remove the now-unused `import ...ModelCapabilities.Modality;` and `import java.util.List;` **only if** nothing else in the file uses them (the `matchesGlob`/merge code does not — verify after editing).
3. Change `resolve` to the generic signature and thread `dataClass` into `materialise`:

```java
@Override
public <T extends ModelCapabilities> T resolve(
    String apiFamily,
    String modelId,
    @Nullable String backend,
    Optional<ModelCapabilitiesOverride> override,
    Class<? extends ModelCapabilitiesData<T>> dataClass) {

  JsonNode merged = mergedBaseTree(apiFamily, modelId, backend);
  if (override.isPresent()) {
    merged = deepMerge(merged, override.get().toSparseJsonNode(mapper));
  }
  return materialise(merged, dataClass);
}

private <T extends ModelCapabilities> T materialise(
    JsonNode merged, Class<? extends ModelCapabilitiesData<T>> dataClass) {
  try {
    return mapper.treeToValue(merged, dataClass).toModelCapabilities();
  } catch (JsonProcessingException e) {
    throw new IllegalStateException("Failed to materialise model capabilities", e);
  }
}
```

   Leave `mergedBaseTree`, `deepMerge`, `findBest`, `matchesGlob`, and the logging untouched.

- [ ] **Step 10: Update `AnthropicChatModelApiFactory.java`**

Change the `resolve(...)` call to pass `AnthropicModelCapabilitiesData.class` and hold the concrete type:

```java
final AnthropicModelCapabilities capabilities =
    capabilitiesResolver.resolve(
        API_FAMILY,
        connection.model().model(),
        direct.type(),
        Optional.ofNullable(model.capabilityOverride()),
        AnthropicModelCapabilitiesData.class);
```

Change the import `...capabilities.ModelCapabilities;` to nothing (the concrete type is same-package `AnthropicModelCapabilities`). The `LOG.debug(...)` call and the rest are unchanged.

- [ ] **Step 11: Update `AnthropicChatModelApi.java`**

Change the field and both constructor params from `ModelCapabilities capabilities` to `AnthropicModelCapabilities capabilities` (same package — drop the `...capabilities.ModelCapabilities` import, add nothing). `capabilities()` still declares `ModelCapabilities` as its return type (the interface) and returns the field (widening). Keep the `ModelCapabilities` import **only** for the `capabilities()` return type — i.e. keep `import ...capabilities.ModelCapabilities;`.

- [ ] **Step 12: Update `AnthropicMessageRequestConverter.java`**

`toMessageCreateParams(...)` and `resolveMaxTokens(...)`: change the `ModelCapabilities capabilities` param type to `AnthropicModelCapabilities`, and read the limit via `core()`:

```java
private long resolveMaxTokens(
    @Nullable AnthropicModelParameters params, AnthropicModelCapabilities capabilities) {
  if (params != null && params.maxTokens() != null) {
    return params.maxTokens().longValue();
  }
  if (capabilities.core().maxOutputTokens() != null) {
    return capabilities.core().maxOutputTokens().longValue();
  }
  return DEFAULT_MAX_TOKENS;
}
```

Replace `import ...capabilities.ModelCapabilities;` with nothing (same-package concrete type).

- [ ] **Step 13: Update `Langchain4JChatModelApi.java`**

Replace the `BRIDGE_CAPABILITIES` builder chain with a `CoreModelCapabilities` constructor (same three modality values + null limits). Keep the existing javadoc comment on the constant verbatim.

```java
import io.camunda.connector.agenticai.aiagent.provider.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.provider.capabilities.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.provider.capabilities.ModelCapabilities.Modality;

import java.util.List;

// ...
private static final ModelCapabilities BRIDGE_CAPABILITIES =
	new CoreModelCapabilities(
		List.of(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT),
		List.of(),
		List.of(Modality.TEXT),
		null,
		null);
```

- [ ] **Step 14: Update the resolver test (`ModelCapabilitiesResolverTest.java`)**

This is the largest test change. It uses `ModelCapabilitiesData` (+ nested `InputModalities`/`OutputModalities`) as fixture builders and reads flag/limit getters directly off the resolved value.

1. Change imports: `import ...anthropic.AnthropicModelCapabilities;`, `import ...anthropic.AnthropicModelCapabilitiesData;`, `import ...anthropic.AnthropicModelCapabilitiesData.InputModalities;`, `import ...anthropic.AnthropicModelCapabilitiesData.OutputModalities;`. Remove the old `capabilities.ModelCapabilitiesData.*` imports.
2. Replace every `ModelCapabilitiesData` type reference in `fullDefaults`/`richDefaults`/`node`/the inline overlay fixtures with `AnthropicModelCapabilitiesData` (constructor arg order is identical). `node(AnthropicModelCapabilitiesData yaml)` returns `mapper.valueToTree(yaml)` unchanged.
3. Add a helper so `resolve(...)` calls stay readable and pass the DTO class:

```java
private AnthropicModelCapabilities resolveA(
    ModelCapabilitiesResolver resolver,
    String family,
    String model,
    @Nullable String backend,
    Optional<ModelCapabilitiesOverride> override) {
  return resolver.resolve(family, model, backend, override, AnthropicModelCapabilitiesData.class);
}
```

   Replace every `resolver.resolve(family, model, backend, override)` call with `resolveA(resolver, family, model, backend, override)`. The resolved variables are now `AnthropicModelCapabilities`.
4. Fix field reads that moved onto `core()`: `caps.contextWindow()` → `caps.core().contextWindow()`, `caps.maxOutputTokens()` → `caps.core().maxOutputTokens()`. The modality reads (`userMessageModalities()` etc.) and flag reads (`supportsReasoning()`, `supportsPromptCaching()`, `supportsParallelToolCalls()`, `supportsReasoningSignatureRoundtrip()`) stay as-is (flags are on `AnthropicModelCapabilities`).
5. Replace the two `ModelCapabilitiesResolverImpl.CONSERVATIVE_DEFAULTS` assertions:
   - `unknownApiFamilyFallsThroughToConservativeDefaults`: replace `assertThat(caps).isEqualTo(ModelCapabilitiesResolverImpl.CONSERVATIVE_DEFAULTS);` with an explicit expected value:
     ```java
     assertThat(caps)
         .isEqualTo(
             new AnthropicModelCapabilities(
                 new CoreModelCapabilities(
                     List.of(Modality.TEXT),
                     List.of(Modality.TEXT),
                     List.of(Modality.TEXT),
                     null,
                     null),
                 false,
                 false,
                 false,
                 false));
     ```
     (Add `import ...capabilities.CoreModelCapabilities;`.) The remaining field assertions in that test stay (adjust the two limit reads to `.core()`).
   - `unknownModelInKnownFamilyFallsThroughToFamilyDefaultsNotConservativeDefaults`: replace `assertThat(caps).isNotEqualTo(ModelCapabilitiesResolverImpl.CONSERVATIVE_DEFAULTS);` with `assertThat(caps.core().contextWindow()).isNotNull();` (the family defaults set 200000/8192, so this proves it is not the conservative baseline). Keep the other assertions (adjust limit reads to `.core()`).

- [ ] **Step 15: Update `BundledCapabilityMatrixTest.java` and `CapabilityMatrixOverrideTest.java`**

Both call `...resolve(apiFamily, modelId, null, Optional.empty())` (4-arg). Add `, AnthropicModelCapabilitiesData.class` as the 5th arg, import `...anthropic.AnthropicModelCapabilitiesData`, and change any `contextWindow()`/`maxOutputTokens()` reads on the resolved value to go through `.core()`. Read the two files first and adjust each read site; modality/flag reads are unchanged. (These tests assert against the real bundled YAML — values must not change; only the access path does.)

- [ ] **Step 16: Update the Anthropic converter/api/factory tests**

- `AnthropicChatModelApiTest.java`: `private final ModelCapabilities capabilities = ModelCapabilities.builder().build();` → `private final AnthropicModelCapabilities capabilities = anthropicCaps();` where you add a small helper returning a conservative `AnthropicModelCapabilities` (core text-only, null limits, all flags false). Keep the field visible to the constructor call at line ~64.
- `AnthropicMessageRequestConverterTest.java`: add a test helper `private static AnthropicModelCapabilities caps() { return new AnthropicModelCapabilities(new CoreModelCapabilities(List.of(Modality.TEXT), List.of(Modality.TEXT), List.of(Modality.TEXT), null, null), false, false, false, false); }` and an overload `caps(Integer maxOutputTokens)` that sets the `CoreModelCapabilities` `maxOutputTokens`. Replace every `ModelCapabilities.builder().build()` with `caps()` and both `ModelCapabilities.builder().maxOutputTokens(8192).build()` with `caps(8192)`. Update imports.
- `AnthropicChatModelApiFactoryTest.java`: the mock stub `when(capabilitiesResolver.resolve(eq("anthropic-messages"), eq(MODEL_ID), eq("direct"), any())).thenReturn(capabilities)` gains the 5th matcher `eq(AnthropicModelCapabilitiesData.class)`; `capabilities` becomes an `AnthropicModelCapabilities` (conservative helper); the `verify(...).resolve("anthropic-messages", MODEL_ID, "direct", Optional.empty())` gains `, AnthropicModelCapabilitiesData.class`. `assertThat(api.capabilities()).isEqualTo(capabilities)` still holds (returns the same instance).

- [ ] **Step 17: Update the remaining neutral-consumer tests**

Each of these builds `ModelCapabilities.builder()...` with modality-only fields → replace with a `CoreModelCapabilities(...)` constructor (null limits). Import `CoreModelCapabilities`; keep the `Modality` import.
- `CapabilityAwareToolCallResultStrategyTest.java`: the `caps(...)` helper (line ~36) and `BRIDGE_CAPS` (line ~44) → `new CoreModelCapabilities(userMessage, toolResult, List.of(Modality.TEXT), null, null)` (mind the modality argument order: user-message, tool-result, assistant-message).
- `ToolResultDocumentWindowingTest.java` (line ~44): same treatment.
- `JobWorkerAgentRequestHandlerTest.java` (line ~867), `OutboundConnectorAgentRequestHandlerTest.java` (line ~668): replace the builder chain with `new CoreModelCapabilities(...)` preserving the exact modality lists set today.
- `LlmProviderChatModelApiConfigurationRegistryTest.java` (line ~129): `.thenReturn(ModelCapabilities.builder().build())` → `.thenReturn(new CoreModelCapabilities(List.of(), List.of(), List.of(), null, null))`. Also check line ~124 `when(capabilitiesResolver.resolve(...))`: if it stubs the 4-arg resolve, add the 5th matcher `any()` (or `eq(AnthropicModelCapabilitiesData.class)`) — read the file to see the exact stub and match its arity.
- `Langchain4JChatModelApiTest.java`: if it asserts the bridge capabilities, update the expected value to the `CoreModelCapabilities` equivalent (read the file; adjust only if it references the old builder/shape).

- [ ] **Step 18: Build the module and run the full test suite (green)**

Run (outside the sandbox): `mvn clean install -f connectors/agentic-ai/pom.xml`
Expected: BUILD SUCCESS; all unit tests pass. Pay attention to `ModelCapabilitiesResolverTest`, `BundledCapabilityMatrixTest`, `CapabilityMatrixOverrideTest`, `AnthropicMessageRequestConverterTest`, `CapabilityAwareToolCallResultStrategyTest` — behavioural assertions must pass unchanged (only access paths moved). If `mvn install` triggers spotless/license failures, run `mvn spotless:apply` and `mvn license:format` and rebuild.

- [ ] **Step 19: Verify no generic code downcasts the interface + no vendor leak**

Run: `grep -rn "instanceof AnthropicModelCapabilities\|(AnthropicModelCapabilities)" connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/multimodal connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/api connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/agent`
Expected: no matches (generic code uses the interface only).
Run: `grep -rn "framework.anthropic" connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/capabilities`
Expected: no matches (the capabilities package stays provider-agnostic).

- [ ] **Step 20: Commit**

```bash
git add connectors/agentic-ai/connector-agentic-ai/src
git commit -m "Reshape ModelCapabilities into a neutral interface with provider-specific records"
```

---

## Self-Review (completed by plan author)

**Spec coverage (R0 scope, spec §7 + R0 section):** neutral interface ✓ (Step 3); `CoreModelCapabilities` shared component ✓ (Step 4); `AnthropicModelCapabilities` provider record ✓ (Step 7); parameterised resolver + per-provider materialisation ✓ (Steps 5,6,8,9); LangChain4j bridge neutral impl ✓ (Step 13); rewire strategy + `capabilities()` producers to the interface ✓ (Steps 11,13,17 — strategy already depends only on the interface, verified in Step 19); three custom-provider invariants ✓ (interface minimal — Step 3 + Global Constraints; no downcasts — Step 19; resolver optional/agnostic — Step 19). Behaviour-identical, no wire change ✓ (Step 18 keeps the behavioural suite green).

**Placeholder scan:** none — every step has concrete file content or exact edit instructions. The two "read the file to see the exact stub/shape" notes (Steps 15, 17) are genuine because those two test files were not fully read; the adjustment rule (add the 5th arg / go through `.core()`) is fully specified.

**Type consistency:** `resolve(...)` 5-arg generic signature is identical in the interface (Step 8), impl (Step 9), factory call (Step 10), and every test call (Steps 14–17). `AnthropicModelCapabilities` component order `(core, supportsReasoning, supportsReasoningSignatureRoundtrip, supportsPromptCaching, supportsParallelToolCalls)` is identical in Steps 7, 14, 16 and the AnthropicModelCapabilitiesData projection (Step 6). `CoreModelCapabilities` modality argument order `(userMessage, toolResult, assistantMessage, contextWindow, maxOutputTokens)` is stated wherever it is constructed.
