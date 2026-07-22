# C6 — Wire-format-first chat-model config + v2 connector types + generation-validated templates

**Epic:** [#7211](https://github.com/camunda/connectors/issues/7211) — AI Agent: own the LLM layer
**Chunk:** C6 (absorbs the type + template surface of the original C10)
**Spec:** `docs/superpowers/specs/2026-07-08-vertical-pilot-own-llm-layer-design.md` (§1 native matrix, §2 config + connector types, §3 routing, §6 capability matrix + override, §8 bridge fallback)
**Repo / module:** module `connectors/agentic-ai/connector-agentic-ai` (+ e2e module `connectors-e2e-test/connectors-e2e-test-agentic-ai`)
**Executor:** `superpowers:subagent-driven-development` — each task is dispatched to a fresh implementer subagent that sees ONLY that task's brief. Every task is therefore self-contained: it repeats the exact code it needs and never says "as in Task N".

---

## Goal

Deliver, without touching the v1 path, the wire-format-first configuration surface and the two v2 connector types the LLM-provider layer will run on:

- **A.** A new config model package `io.camunda.connector.agenticai.aiagent.model.request.chatmodel`: a sealed, Jackson-polymorphic `LlmProviderConfiguration` with `AnthropicChatModel` + `OpenAiChatModel` members, backend-conditional auth modelled as per-member sealed discriminated dropdowns, full `@TemplateProperty`/`@TemplateSubType`/`@TemplateDiscriminatorProperty`/`@FEEL` annotations.
- **B.** Finishing the DEFERRED sparse per-element capability-override path in `ModelCapabilitiesResolverImpl` (C3 left a placeholder pointing here): a public sparse `ModelCapabilitiesOverride` record deep-merged as the highest-precedence overlay.
- **C.** `LlmProviderChatModelApiConfiguration(LlmProviderConfiguration)` + `AiAgentTaskV2`/`AiAgentSubProcessV2` connector classes + v2 request records, mapping to `LlmProviderChatModelApiConfiguration` at the handler boundary.
- **D.** The v2 sub-process groovy transform + COMMITTED, generation-validated v2 element-template JSON, README + docs.

### Out of scope (explicitly deferred to a slim "C10′" after C7–C9)

- The LLM-provider `ChatModelApiFactory` implementations (`framework/anthropic/**`, `framework/openai/**`) and runtime routing / `supports(LlmProviderChatModelApiConfiguration)`.
- The wire-format e2e smoke tests.

These need the provider SDKs delivered in C7 (Anthropic), C8 (OpenAI Completions), C9 (OpenAI Responses). **C6 does NOT register any LLM-provider factory.**

### Accepted caveat (ratified by the user — "iterating locally, no users")

Between C6 and C7 a v2 element template **applies fine in Modeler** but a v2 agent that actually **runs** MUST **fail loud** at model-call resolution, because no factory yet `supports(LlmProviderChatModelApiConfiguration)`. The failure is the existing registry "no factory" path:

`ChatModelApiRegistryImpl.resolve(...)` filters factories by `supports(...)`, `findFirst()`, and `.orElseThrow(() -> new ConnectorException(ERROR_CODE_FAILED_MODEL_CALL, "No chat model registered for configuration: " + configuration))`. `Langchain4JChatModelApiFactory.supports(...)` returns `configuration instanceof ProviderChatModelApiConfiguration` only, so a `LlmProviderChatModelApiConfiguration` matches no factory and this throws a clear `ConnectorException` — NOT an NPE. This plan verifies that path with an explicit unit test and ensures nothing on the v2 path dereferences a null before it (see Task 5 — the agent-instance metadata refactor).

---

## Architecture

Key facts established by exploration (do not re-derive):

- **SPI.** `io.camunda.connector.agenticai.aiagent.provider.api.ChatModelApiConfiguration` is a plain **marker interface** (no Jackson). The only impl today is `record ProviderChatModelApiConfiguration(ProviderConfiguration providerConfiguration) implements ChatModelApiConfiguration` (constructed programmatically, never deserialized). `ChatModelApiFactory.supports/create/getOrder`; `ChatModelApiRegistryImpl` sorts factories by `getOrder()` ascending and throws `ConnectorException(ERROR_CODE_FAILED_MODEL_CALL, ...)` when none supports.
- **Legacy config.** `io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration` is a sealed, Jackson-polymorphic (`@JsonTypeInfo(use=NAME, property="type")`) interface with plain records (NOT `@AgenticAiRecord`), individually-applied `@TemplateProperty`/`@Valid`/`@NotNull`/`@NotBlank`, `toString()` secret redaction, and per-member auth dropdowns (`BedrockProviderConfiguration.AwsAuthentication` sealed with `credentials`/`apiKey`/`defaultCredentialsChain`; `AzureAuthentication` sealed). Shared helpers `provider.shared.TimeoutConfiguration` and `provider.shared.HttpUrl` are neutral — **reuse them**.
- **Handler boundary.** `BaseAgentRequestHandler` (line 171) does `chatModelApiRegistry.resolve(new ProviderChatModelApiConfiguration(executionContext.configuration().provider()))`. `AgentConfiguration.provider()` (type `ProviderConfiguration`) is read ONLY by (a) that line and (b) `CamundaAgentInstanceClient.executeCreate` (`configuration.provider().model()` + `.providerType()`, run during agent init, BEFORE the model call). `AgentConfiguration` is **transient** (not persisted) — changing its shape has no backward-compat impact on stored data.
- **Capabilities.** `ModelCapabilitiesResolver.resolve(String apiFamily, String modelId, @Nullable String backend, Optional<ModelCapabilities> override)`. `ModelCapabilitiesResolverImpl` has a placeholder that returns `override.get()` verbatim (`resolve()` lines ~71–75) and a `deepMerge`/`merge`/`conservativeBase`/`ModelCapabilitiesData` machinery. The resolver is called **only from tests today** (the bridge uses a hardcoded `BRIDGE_CAPABILITIES` and never calls the resolver; LLM-provider factories that will call it arrive in C7+). The bundled `model-capabilities.yaml` families are `anthropic-messages`, `openai-completions`, `openai-responses`.
- **v1 connectors.** `AiAgentFunction` (Task; `@OutboundConnector(type="io.camunda.agenticai:aiagent:1")` + `@ElementTemplate(id="io.camunda.connectors.agenticai.aiagent.v1", version=11, inputDataClass=OutboundConnectorAgentRequest.class)`). `AiAgentJobWorker` (Sub-process; `@OutboundConnector(type="io.camunda.agenticai:aiagent-job-worker:1")`, **no** `@ElementTemplate` — its template is DERIVED from the Task template by `bin/transform-ai-agent-job-worker-template.groovy` in the `gmavenplus-plugin` `process-classes` phase). Element-template JSON is generated by the `element-template-generator-maven-plugin` (`pom.xml` `<connectors>` block), never hand-edited.
- **Handlers.** `OutboundConnectorAgentRequestHandler extends BaseAgentRequestHandler<OutboundConnectorAgentExecutionContext, AiAgentTaskConnectorResponse>`; `JobWorkerAgentRequestHandler extends BaseAgentRequestHandler<JobWorkerAgentExecutionContext, AiAgentSubProcessConnectorResponse>` (uses the concrete `executionContext.response()`). Beans wired in `AgenticAiConnectorsAutoConfiguration`, each behind `@ConditionalOnMissingBean` + a `@ConditionalOnBooleanProperty(..., matchIfMissing = true)` toggle.

**Chosen v2 wiring (one shared handler + one shared context per flavor; v2 connectors are just new entry points):**

The provider→config mapping moves OUT of the handler and INTO the connector entry points. There are NO v2 handler subclasses and NO v2 execution-context subclasses — the existing flavor handler and flavor context serve both v1 and v2.

1. **`AgentConfiguration` carries the generic SPI config + telemetry strings (not the concrete provider).** Its first component changes from `ProviderConfiguration provider` to `ChatModelApiConfiguration chatModelApiConfiguration`, and it gains two plain `String` components `modelName` + `modelProvider` (the SPI marker has no methods, so agent-instance telemetry needs the strings explicitly). `AgentConfiguration` is transient (persisted `AgentContext` has no path to it), so this is backward-compat-safe.
2. **Known layering tradeoff (flagged for #7537).** This introduces a `model → framework.api` package reference (the record lives in `aiagent.model`, the SPI marker in `aiagent.framework.api`). `framework.api` already depends on `model` (via `ChatModelRequest`), so this is mutual. It compiles; there is no ArchUnit enforcement today (layering is the future epic #7537); `framework.api` is the neutral SPI (no vendor SDK). Do NOT try to avoid it — accept and note it.
3. **Handler is a one-liner.** `BaseAgentRequestHandler` (line ~171) becomes `chatModelApiRegistry.resolve(configuration.chatModelApiConfiguration())` — no seam method, no branching, no `new ProviderChatModelApiConfiguration(...)` wrapping. Byte-identical for v1 (same wrapper instance, built at the entry point instead).
4. **Execution contexts become version-agnostic.** Each flavor context's constructor RECEIVES the already-built `ChatModelApiConfiguration` + the two metadata strings + the shared request DATA, instead of holding a version-specific request and calling `request.provider()`. One class per flavor serves both v1 and v2 (the v2 request records reuse the SAME `*RequestData` types verbatim).
5. **Entry points do the mapping.** v1 `AiAgentFunction`/`AiAgentJobWorker` build `new ProviderChatModelApiConfiguration(request.provider())` + `request.provider().model()`/`.providerType()`; v2 `AiAgentTaskV2`/`AiAgentSubProcessV2` build `new LlmProviderChatModelApiConfiguration(request.configuration())` + `configuration.model()`/`.type()`. Both pass these into the same flavor context and call the same flavor handler bean.
6. **Second consumer of the config: the bridge adapter.** `Langchain4JAiFrameworkAdapter` unwraps `((ProviderChatModelApiConfiguration) configuration.chatModelApiConfiguration()).providerConfiguration()` (safe — `Langchain4JChatModelApiFactory.supports(...)` only routes `ProviderChatModelApiConfiguration` there); `CamundaAgentInstanceClient` reads `configuration.modelName()`/`.modelProvider()`.

---

## Tech stack

- Java 21 records + sealed types; Jackson (`@JsonTypeInfo`/`@JsonSubTypes`/`@JsonProperty`) for polymorphism; jakarta bean-validation (`@Valid`/`@NotNull`/`@NotBlank`/`@Min`) with a Hibernate `Validator`.
- Connector SDK element-template annotations: `@TemplateProperty`, `@TemplateSubType`, `@TemplateDiscriminatorProperty`, `io.camunda.connector.api.annotation.FEEL`, `io.camunda.connector.generator.java.annotation.FeelMode`.
- jspecify null-safety (`@NullMarked` per-package `package-info.java`, explicit `@Nullable`).
- Tests: JUnit 5 + AssertJ + Mockito. Maven build (`mvn ... -f connectors/agentic-ai/pom.xml`); template generation via `element-template-generator-maven-plugin` + `gmavenplus-plugin` groovy transform.

---

## Global constraints (verbatim — apply to every task)

- **Backward compatibility on Camunda 8.9-persisted data is the #1 priority.** C6 adds NEW config types and NEW v2 connectors; it MUST NOT change v1 request records, the legacy `ProviderConfiguration`, the v1 templates, or the bridge path. v1 stays byte-identical. (`AgentConfiguration` is transient, not persisted — nullability changes there are BC-safe.)
- The separate e2e module `connectors-e2e-test/connectors-e2e-test-agentic-ai` MUST keep **compiling AND passing** after every task (model/serialization changes silently break it).
- Module must build green: `mvn clean install -f connectors/agentic-ai/pom.xml`. If template properties change, regenerate templates via `mvn clean compile -f connectors/agentic-ai/pom.xml` and COMMIT the JSON diff (never hand-edit generated JSON). Element-template generation uses the maven plugin (NOT the e2e `element-templates-cli`).
- All production code is `@NullMarked` (jspecify) via per-package `package-info.java`; add `@Nullable` explicitly; never suppress null-safety errors. New packages need a `@NullMarked package-info.java`.
- Follow the existing `provider/` records' style: plain records with individually-applied Jackson + validation + `@TemplateProperty` annotations (NOT `@AgenticAiRecord`), and `toString()` redaction for any secret (apiKey/token/keys).
- Secrets in AWS/other auth must be redacted in `toString()`.
- Update module docs when structure changes: `connectors/agentic-ai/AGENTS.md` and/or `docs/reference/ai-agent.md` (esp. §12 framework abstraction, §16 auto-config, §25 extension points) and the element-templates `README.md` per its maintenance rules (two tables, Task + Sub-process). Fold doc updates into the relevant task.

This whole chunk is **one stacked PR** but lands as several coherent commits (one per task). Do not push.

### Execution notes (for the executor, not tasks)

- Run Maven and git with the sandbox disabled (`dangerouslyDisableSandbox: true`) — the build resolves dependencies and writes to `target/`.
- Implementer subagents run the tests and report the observed output; they do not claim success without running the command.
- Before each commit run `mvn spotless:apply license:format -f connectors/agentic-ai/pom.xml` (pre-commit hooks enforce spotless + license headers); the copyright header shown once below must head every new `.java` file.
- Commit messages describe the actual change (no "wip"/"review"). Do not `git push`, `git checkout`, or `git reset`.
- Every new `.java` file starts with this exact header:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
```

---

## Task 1 — Sparse `ModelCapabilitiesOverride` + resolver deep-merge (finish the C3 deferral)

**Goal:** introduce a public sparse `ModelCapabilitiesOverride`, change the resolver's override parameter type to it, and replace the verbatim-short-circuit placeholder with a highest-precedence deep-merge overlay. Self-contained; unit-tested against the resolver.

### Files

- CREATE `connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/capabilities/ModelCapabilitiesOverride.java`
- MODIFY `.../framework/capabilities/ModelCapabilitiesResolver.java` (interface signature)
- MODIFY `.../framework/capabilities/ModelCapabilitiesResolverImpl.java` (`resolve` body)
- MODIFY (test) `.../test/.../framework/capabilities/ModelCapabilitiesResolverTest.java` (replace the verbatim test, add merge tests)

### Interfaces

- **Produces:** `public record ModelCapabilitiesOverride(...)` with `JsonNode toSparseJsonNode(ObjectMapper mapper)`.
- **Produces (changed):** `ModelCapabilities resolve(String apiFamily, String modelId, @Nullable String backend, Optional<ModelCapabilitiesOverride> override)`.
- **Consumes:** existing `ModelCapabilities`, `ModelCapabilities.Modality`, package-private `ModelCapabilitiesData` (snake_case, nested `input_modalities.{user_message,tool_result}`, `output_modalities.assistant_message`), `deepMerge`, `conservativeBase`.

### Field-name mapping (document in the record's javadoc)

The public override uses the **full names** matching `ModelCapabilities` (not the spec's illustrative short names). They project onto the internal `ModelCapabilitiesData` snake/nested shape exactly:

| `ModelCapabilitiesOverride` (public) | `ModelCapabilitiesData` (internal JSON) |
|---|---|
| `userMessageModalities` | `input_modalities.user_message` |
| `toolResultModalities` | `input_modalities.tool_result` |
| `assistantMessageModalities` | `output_modalities.assistant_message` |
| `supportsReasoning` | `supports_reasoning` |
| `supportsReasoningSignatureRoundtrip` | `supports_reasoning_signature_roundtrip` |
| `supportsPromptCaching` | `supports_prompt_caching` |
| `supportsParallelToolCalls` | `supports_parallel_tool_calls` |
| `contextWindow` | `context_window` |
| `maxOutputTokens` | `max_output_tokens` |

`toSparseJsonNode` OMITS any null field (and omits an empty `input_modalities`/`output_modalities` branch entirely), so `deepMerge` overlays only the fields the operator actually set — sparse fields win, untouched fields inherit.

### Steps

1. **Write failing test.** In `ModelCapabilitiesResolverTest.java`, first DELETE the existing test method `overridePresentShortCircuitsResolutionAndReturnsVerbatim()` (its body constructs a `new ModelCapabilities(...)` and asserts `result` `isSameAs(override)` — that behavior is being replaced) and its now-unused imports if any. Then add these three methods (they will not compile yet, because `ModelCapabilitiesOverride` does not exist and `resolve` still takes `Optional<ModelCapabilities>`):

```java
  @Test
  void overrideDeepMergesOnTopOfResolvedBaseWithSparseFieldsWinning() {
    final var resolver = resolverFor(Map.of());

    // Base for a known Anthropic model resolves supportsReasoning=true, toolResult=[text]
    // (from the bundled matrix). The sparse override flips supportsReasoning off and widens
    // toolResult, leaving every other field to inherit from the resolved base.
    final var override =
        new ModelCapabilitiesOverride(
            null,
            List.of(Modality.TEXT, Modality.IMAGE),
            null,
            false,
            null,
            null,
            null,
            null,
            null);

    final var base = resolver.resolve("anthropic-messages", "claude-opus-4-7", null, Optional.empty());
    final var merged =
        resolver.resolve("anthropic-messages", "claude-opus-4-7", null, Optional.of(override));

    // overridden fields win
    assertThat(merged.toolResultModalities()).containsExactly(Modality.TEXT, Modality.IMAGE);
    assertThat(merged.supportsReasoning()).isFalse();
    // untouched fields inherit from the resolved base verbatim
    assertThat(merged.userMessageModalities()).isEqualTo(base.userMessageModalities());
    assertThat(merged.assistantMessageModalities()).isEqualTo(base.assistantMessageModalities());
    assertThat(merged.supportsPromptCaching()).isEqualTo(base.supportsPromptCaching());
    assertThat(merged.contextWindow()).isEqualTo(base.contextWindow());
    assertThat(merged.maxOutputTokens()).isEqualTo(base.maxOutputTokens());
  }

  @Test
  void overrideScalarFieldsWinAndOthersInherit() {
    final var resolver = resolverFor(Map.of());
    final var base = resolver.resolve("anthropic-messages", "claude-opus-4-7", null, Optional.empty());

    final var override =
        new ModelCapabilitiesOverride(null, null, null, null, null, null, null, 4242, 777);
    final var merged =
        resolver.resolve("anthropic-messages", "claude-opus-4-7", null, Optional.of(override));

    assertThat(merged.contextWindow()).isEqualTo(4242);
    assertThat(merged.maxOutputTokens()).isEqualTo(777);
    assertThat(merged.supportsReasoning()).isEqualTo(base.supportsReasoning());
    assertThat(merged.userMessageModalities()).isEqualTo(base.userMessageModalities());
  }

  @Test
  void overrideAppliesOverConservativeDefaultsWhenFamilyUnknown() {
    final var resolver = resolverFor(Map.of());
    final var override =
        new ModelCapabilitiesOverride(
            List.of(Modality.TEXT, Modality.IMAGE), null, null, true, null, null, null, null, null);

    final var merged = resolver.resolve("does-not-exist", "whatever", null, Optional.of(override));

    // conservative base is text-only, everything false; override widens userMessage + reasoning
    assertThat(merged.userMessageModalities()).containsExactly(Modality.TEXT, Modality.IMAGE);
    assertThat(merged.supportsReasoning()).isTrue();
    assertThat(merged.toolResultModalities()).containsExactly(Modality.TEXT);
    assertThat(merged.supportsPromptCaching()).isFalse();
  }
```

   (`resolverFor(...)`, `Modality`, `Optional`, `List`, `assertThat` are already imported/used in this test class. Confirm `import io.camunda.connector.agenticai.aiagent.provider.capabilities.ModelCapabilities.Modality;` exists — it is used by the deleted test — keep it. Add `import io.camunda.connector.agenticai.aiagent.provider.capabilities.ModelCapabilitiesOverride;` only if the class is in a different package; here it is the same package, so no import needed.)

2. **Run-fail (compile error expected):**

```bash
mvn -q -o test -f connectors/agentic-ai/pom.xml -pl connectors/agentic-ai/connector-agentic-ai -Dtest=ModelCapabilitiesResolverTest
```
Expected: compilation failure — `cannot find symbol: class ModelCapabilitiesOverride` and/or `incompatible types: Optional<ModelCapabilitiesOverride> cannot be converted to Optional<ModelCapabilities>`.

3. **Minimal impl — create `ModelCapabilitiesOverride.java`** (full source):

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.provider.capabilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.agenticai.aiagent.provider.capabilities.ModelCapabilities.Modality;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Sparse, operator-supplied capability override — the highest-precedence layer fed to {@link
 * ModelCapabilitiesResolver#resolve}. Every field is {@link Nullable}: a {@code null} field is
 * absent and inherits from the resolved base (bundled YAML + operator {@code application.yml} +
 * matrix), a non-null field wins.
 *
 * <p>Field names mirror {@link ModelCapabilities} (the friendly, full names). {@link
 * #toSparseJsonNode} projects them onto the internal {@link ModelCapabilitiesData} snake/nested
 * shape used by the deep-merge, omitting any null field so only explicitly-set values overlay:
 *
 * <ul>
 *   <li>{@code userMessageModalities} -&gt; {@code input_modalities.user_message}
 *   <li>{@code toolResultModalities} -&gt; {@code input_modalities.tool_result}
 *   <li>{@code assistantMessageModalities} -&gt; {@code output_modalities.assistant_message}
 *   <li>{@code supportsReasoning} -&gt; {@code supports_reasoning} (and the other flags/counters
 *       by the same snake-case rule)
 * </ul>
 *
 * <p>Kept in the capabilities package (not the config package) so the dependency direction stays
 * config -&gt; framework. The connector config surfaces it as a {@code @FEEL} field; the FEEL
 * expression evaluates to a sparse map/context whose keys are these component names.
 */
public record ModelCapabilitiesOverride(
	@Nullable List<Modality> userMessageModalities,
	@Nullable List<Modality> toolResultModalities,
	@Nullable List<Modality> assistantMessageModalities,
	@Nullable Boolean supportsReasoning,
	@Nullable Boolean supportsReasoningSignatureRoundtrip,
	@Nullable Boolean supportsPromptCaching,
	@Nullable Boolean supportsParallelToolCalls,
	@Nullable Integer contextWindow,
	@Nullable Integer maxOutputTokens) {

	/**
	 * Projects this sparse override onto a {@link ModelCapabilitiesData}-shaped {@link JsonNode},
	 * omitting every null field (and omitting an empty {@code input_modalities} / {@code
	 * output_modalities} branch), so it can be deep-merged as the top overlay.
	 */
	public JsonNode toSparseJsonNode(ObjectMapper mapper) {
		final ObjectNode root = mapper.createObjectNode();

		final ObjectNode input = mapper.createObjectNode();
		if (userMessageModalities != null) {
			input.set("user_message", modalitiesArray(mapper, userMessageModalities));
		}
		if (toolResultModalities != null) {
			input.set("tool_result", modalitiesArray(mapper, toolResultModalities));
		}
		if (!input.isEmpty()) {
			root.set("input_modalities", input);
		}

		final ObjectNode output = mapper.createObjectNode();
		if (assistantMessageModalities != null) {
			output.set("assistant_message", modalitiesArray(mapper, assistantMessageModalities));
		}
		if (!output.isEmpty()) {
			root.set("output_modalities", output);
		}

		if (supportsReasoning != null) {
			root.put("supports_reasoning", supportsReasoning);
		}
		if (supportsReasoningSignatureRoundtrip != null) {
			root.put("supports_reasoning_signature_roundtrip", supportsReasoningSignatureRoundtrip);
		}
		if (supportsPromptCaching != null) {
			root.put("supports_prompt_caching", supportsPromptCaching);
		}
		if (supportsParallelToolCalls != null) {
			root.put("supports_parallel_tool_calls", supportsParallelToolCalls);
		}
		if (contextWindow != null) {
			root.put("context_window", contextWindow);
		}
		if (maxOutputTokens != null) {
			root.put("max_output_tokens", maxOutputTokens);
		}

		return root;
	}

	private static ArrayNode modalitiesArray(ObjectMapper mapper, List<Modality> modalities) {
		final ArrayNode array = mapper.createArrayNode();
		for (Modality modality : modalities) {
			// Modality carries lowercase @JsonProperty values ("text"/"image"/...) matching the YAML.
			array.add(mapper.convertValue(modality, String.class));
		}
		return array;
	}
}
```

4. **Minimal impl — change the interface** `ModelCapabilitiesResolver.java`. Replace the `resolve` signature's last parameter type:

```java
  ModelCapabilities resolve(
      String apiFamily,
      String modelId,
      @Nullable String backend,
      Optional<ModelCapabilitiesOverride> override);
```
(`Optional` and `@Nullable` imports already present. `ModelCapabilitiesOverride` is same-package — no import.)

5. **Minimal impl — change `ModelCapabilitiesResolverImpl.resolve`.** Replace the current method body (the version that early-returns `override.get()` and has two `return merge(...)` paths) with a version that (a) computes the base merged tree exactly as today, (b) overlays the override last, (c) materialises once. Full replacement of the `resolve` method:

```java
  @Override
  public ModelCapabilities resolve(
      String apiFamily,
      String modelId,
      @Nullable String backend,
      Optional<ModelCapabilitiesOverride> override) {

    JsonNode merged = mergedBaseTree(apiFamily, modelId, backend);

    if (override.isPresent()) {
      merged = deepMerge(merged, override.get().toSparseJsonNode(mapper));
    }

    return materialise(merged);
  }

  /**
   * Deep-merges {@code conservativeBase -> familyDefaults -> backend-agnostic entry -> backend-
   * specific entry} into a single tree (the pre-override base), logging pattern/default
   * fall-throughs once per (api family, model id).
   */
  private JsonNode mergedBaseTree(String apiFamily, String modelId, @Nullable String backend) {
    final ApiFamily family = matrix.families().get(apiFamily);
    if (family == null) {
      logOnce(
          "missing-family:" + apiFamily,
          "No capability matrix entry for api family '{}'; using conservative defaults",
          apiFamily);
      return conservativeBase;
    }

    final MatchedEntry agnostic = findBest(family.models(), modelId, null);
    final MatchedEntry specific =
        backend == null ? null : findBest(family.models(), modelId, backend);

    if (agnostic == null && specific == null) {
      logOnce(
          "default:" + apiFamily + ":" + modelId,
          "No capability matrix entry for model '{}' under api family '{}'; using family defaults",
          modelId,
          apiFamily);
      return deepMerge(conservativeBase, family.defaults());
    }

    if (agnostic != null && !agnostic.isExact()) {
      logOnce(
          "pattern:" + apiFamily + ":" + modelId,
          "Capability matrix pattern '{}' matched model '{}' (api family '{}')",
          agnostic.pattern(),
          modelId,
          apiFamily);
    }
    if (specific != null && !specific.isExact()) {
      logOnce(
          "pattern:" + apiFamily + ":" + backend + ":" + modelId,
          "Capability matrix pattern '{}' matched model '{}' for backend '{}' (api family '{}')",
          specific.pattern(),
          modelId,
          backend,
          apiFamily);
    }

    JsonNode merged = deepMerge(conservativeBase, family.defaults());
    if (agnostic != null) {
      merged = deepMerge(merged, agnostic.entry().capabilities());
    }
    if (specific != null) {
      merged = deepMerge(merged, specific.entry().capabilities());
    }
    return merged;
  }

  private ModelCapabilities materialise(JsonNode merged) {
    try {
      return mapper.treeToValue(merged, ModelCapabilitiesData.class).toModelCapabilities();
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to materialise model capabilities", e);
    }
  }
```

   Then DELETE the now-unused private `merge(@Nullable JsonNode familyDefaults, @Nullable JsonNode... overlays)` method (its logic is inlined above; `deepMerge`, `findBest`, `MatchedEntry`, `logOnce`, `conservativeBase`, `mapper` all stay). Keep the `CONSERVATIVE_DEFAULTS` / `CONSERVATIVE_DEFAULTS_DATA` constants and all other helpers untouched. Confirm imports still used: `JsonNode`, `JsonProcessingException`, `ObjectMapper`, `ApiFamily`, `ModelEntry`, `Modality` — all remain; remove any import that becomes unused (none expected).

6. **Run-pass:**

```bash
mvn -q -o test -f connectors/agentic-ai/pom.xml -pl connectors/agentic-ai/connector-agentic-ai -Dtest=ModelCapabilitiesResolverTest
```
Expected: `BUILD SUCCESS`, the three new tests plus all pre-existing (`Optional.empty()`) resolver tests green.

7. **Module build (includes the whole module's tests + spotless):**

```bash
mvn -q -o clean install -f connectors/agentic-ai/pom.xml
```
Expected: `BUILD SUCCESS`. (This confirms no other production caller passed an override — verified: the resolver is called with `Optional.empty()` everywhere in production; the bridge does not call it.)

8. **e2e still green (compile + the fastest existing agent smoke):**

```bash
mvn -q -o test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -am -DskipTests=false -Dtest=BaseAiAgentJobWorkerTest
```
Expected: `BUILD SUCCESS` (or, if that base class is abstract, `mvn -q -o test-compile -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -am` compiling green — pick the concrete smoke test that extends it; the point is the module compiles and a representative test passes).

9. **Commit:**

```bash
mvn -q spotless:apply license:format -f connectors/agentic-ai/pom.xml
git add -A && git commit -m "Add sparse ModelCapabilitiesOverride and deep-merge it as highest-precedence layer in ModelCapabilitiesResolver"
```

---

## Task 2 — `chatmodel` package: `LlmProviderConfiguration` sealed interface + Anthropic member + AWS auth

**Goal:** create the new config package, the sealed Jackson-polymorphic interface (permitting Anthropic only for now), a fresh chatmodel-local AWS auth dropdown, and the `AnthropicChatModel` member with its backend dropdown, model+params, timeouts, and FEEL capability override. Prove Jackson round-trip + bean-validation for the Anthropic variant.

**Design decision (justified):** the AWS auth for Anthropic-on-Bedrock is a **fresh chatmodel-local** `ChatModelAwsAuthentication` (NOT the legacy `BedrockProviderConfiguration.AwsAuthentication`). Rationale: keep the wire-format-first hierarchy self-contained and avoid coupling the new config package to the legacy `provider` package (which is destined to be retired provider-by-provider post-pilot). The neutral helpers `TimeoutConfiguration` and `HttpUrl` ARE reused from `provider.shared` (they carry no provider semantics).

### Files

- CREATE `.../model/request/chatmodel/package-info.java`
- CREATE `.../model/request/chatmodel/LlmProviderConfiguration.java`
- CREATE `.../model/request/chatmodel/AnthropicChatModel.java`
- CREATE `.../model/request/chatmodel/shared/package-info.java`
- CREATE `.../model/request/chatmodel/shared/ChatModelAwsAuthentication.java`
- CREATE (test) `.../test/.../model/request/chatmodel/AnthropicChatModelTest.java`

### Interfaces

- **Produces:** `sealed interface LlmProviderConfiguration permits AnthropicChatModel` with `String type()`, `String model()`, `@Nullable String backend()`, `Optional<ModelCapabilitiesOverride> capabilityOverride()`.
- **Produces:** `record AnthropicChatModel(...) implements LlmProviderConfiguration`; nested sealed `AnthropicBackend` (`anthropic-direct`? no — discriminator values `direct`/`bedrock`), `AnthropicModel`, `AnthropicModelParameters`.
- **Produces:** `sealed interface ChatModelAwsAuthentication` (`credentials`/`apiKey`/`defaultCredentialsChain`).
- **Consumes:** `provider.shared.TimeoutConfiguration`, `provider.shared.HttpUrl`, `framework.capabilities.ModelCapabilitiesOverride` (Task 1).

### Steps

1. **Write failing test** — CREATE `AnthropicChatModelTest.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.chatmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicBackend.AnthropicBedrockBackend;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicBackend.AnthropicDirectBackend;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.shared.ChatModelAwsAuthentication.AwsStaticCredentialsAuthentication;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnthropicChatModelTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final Validator validator =
      Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void deserialisesDirectBackendViaTypeDiscriminatorAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "anthropic",
          "backend": { "type": "direct", "apiKey": "sk-ant-123" },
          "model": { "model": "claude-sonnet-4-6", "parameters": { "maxTokens": 1024 } }
        }
        """;

    final LlmProviderConfiguration parsed = mapper.readValue(json, LlmProviderConfiguration.class);

    assertThat(parsed).isInstanceOf(AnthropicChatModel.class);
    assertThat(parsed.type()).isEqualTo("anthropic");
    assertThat(parsed.model()).isEqualTo("claude-sonnet-4-6");
    assertThat(parsed.backend()).isEqualTo("direct");
    assertThat(parsed.capabilityOverride()).isEmpty();

    final AnthropicChatModel anthropic = (AnthropicChatModel) parsed;
    assertThat(anthropic.backend()).isEqualTo("direct");
    assertThat(anthropic.backendConfig()).isInstanceOf(AnthropicDirectBackend.class);

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, LlmProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void deserialisesBedrockBackendWithStaticCredentials() throws Exception {
    final String json =
        """
        {
          "type": "anthropic",
          "backend": {
            "type": "bedrock",
            "region": "eu-west-1",
            "authentication": { "type": "credentials", "accessKey": "AKIA", "secretKey": "shh" }
          },
          "model": { "model": "claude-sonnet-4-6" }
        }
        """;

    final AnthropicChatModel parsed =
        (AnthropicChatModel) mapper.readValue(json, LlmProviderConfiguration.class);

    assertThat(parsed.backend()).isEqualTo("bedrock");
    assertThat(parsed.backendConfig()).isInstanceOf(AnthropicBedrockBackend.class);
    final AnthropicBedrockBackend bedrock = (AnthropicBedrockBackend) parsed.backendConfig();
    assertThat(bedrock.region()).isEqualTo("eu-west-1");
    assertThat(bedrock.authentication()).isInstanceOf(AwsStaticCredentialsAuthentication.class);
    // secrets redacted in toString
    assertThat(bedrock.authentication().toString()).doesNotContain("shh").contains("REDACTED");
  }

  @Test
  void directBackendRejectsBlankApiKey() {
    final var model =
        new AnthropicChatModel(
            new AnthropicDirectBackend(null, "  "),
            new AnthropicModel("claude-sonnet-4-6", new AnthropicModelParameters(1, null, null, null)),
            null,
            null);

    final var violations = validator.validate(model);
    assertThat(violations)
        .anyMatch(v -> v.getPropertyPath().toString().contains("apiKey"));
  }

  @Test
  void validAnthropicModelHasNoViolations() {
    final var model =
        new AnthropicChatModel(
            new AnthropicDirectBackend(null, "sk-ant-123"),
            new AnthropicModel("claude-sonnet-4-6", null),
            null,
            null);

    assertThat(validator.validate(model)).isEmpty();
    assertThat(model.capabilityOverride()).isEqualTo(Optional.empty());
  }
}
```

2. **Run-fail:**

```bash
mvn -q -o test -f connectors/agentic-ai/pom.xml -pl connectors/agentic-ai/connector-agentic-ai -Dtest=AnthropicChatModelTest
```
Expected: compilation failure — `package io.camunda.connector.agenticai.aiagent.model.request.chatmodel does not exist`.

3. **Impl — `chatmodel/package-info.java`:**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
@NullMarked
package io.camunda.connector.agenticai.aiagent.model.request.chatmodel;

import org.jspecify.annotations.NullMarked;
```

4. **Impl — `chatmodel/shared/package-info.java`:**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
@NullMarked
package io.camunda.connector.agenticai.aiagent.model.request.chatmodel.shared;

import org.jspecify.annotations.NullMarked;
```

5. **Impl — `chatmodel/shared/ChatModelAwsAuthentication.java`** (fresh, chatmodel-local; mirrors legacy `AwsAuthentication` shape verbatim, redacting secrets):

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.chatmodel.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

/**
 * AWS authentication strategies for the wire-format-first chat-model config (Anthropic on Bedrock).
 * Deliberately independent of the legacy {@code BedrockProviderConfiguration.AwsAuthentication} to
 * keep the new config package self-contained and decoupled from the retiring {@code provider}
 * package.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = ChatModelAwsAuthentication.AwsStaticCredentialsAuthentication.class,
      name = "credentials"),
  @JsonSubTypes.Type(
      value = ChatModelAwsAuthentication.AwsDefaultCredentialsChainAuthentication.class,
      name = "defaultCredentialsChain"),
  @JsonSubTypes.Type(
      value = ChatModelAwsAuthentication.AwsApiKeyAuthentication.class, name = "apiKey")
})
@TemplateDiscriminatorProperty(
    label = "AWS authentication",
    group = "provider",
    name = "type",
    defaultValue = "credentials",
    description =
        "Specify the AWS authentication strategy. Learn more at the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-bedrock/#authentication\" target=\"_blank\">documentation page</a>")
public sealed interface ChatModelAwsAuthentication {

  @TemplateSubType(id = "credentials", label = "Credentials")
  record AwsStaticCredentialsAuthentication(
      @TemplateProperty(
              group = "provider",
              label = "Access key",
              description =
                  "Provide an IAM access key tailored to a user, equipped with the necessary permissions")
          @NotBlank
          String accessKey,
      @TemplateProperty(
              group = "provider",
              label = "Secret key",
              description = "Provide the secret key for the IAM access key")
          @NotBlank
          String secretKey)
      implements ChatModelAwsAuthentication {

    @Override
    public String toString() {
      return "AwsStaticCredentialsAuthentication{accessKey=[REDACTED], secretKey=[REDACTED]}";
    }
  }

  @TemplateSubType(id = "apiKey", label = "API key")
  record AwsApiKeyAuthentication(
      @TemplateProperty(
              group = "provider",
              label = "API key",
              description =
                  "Provide an API key with permissions to interact with your AWS Bedrock instance")
          @NotBlank
          String apiKey)
      implements ChatModelAwsAuthentication {

    @Override
    public String toString() {
      return "AwsApiKeyAuthentication{apiKey=[REDACTED]}";
    }
  }

  @TemplateSubType(
      id = "defaultCredentialsChain",
      label = "Default credentials chain (Hybrid/Self-Managed only)")
  record AwsDefaultCredentialsChainAuthentication() implements ChatModelAwsAuthentication {}
}
```

6. **Impl — `LlmProviderConfiguration.java`** (permits Anthropic only for now; Task 3 extends both `permits` and `@JsonSubTypes`):

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.chatmodel;

import static io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.ANTHROPIC_ID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.provider.capabilities.ModelCapabilitiesOverride;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Wire-format-first chat-model configuration surfaced by the v2 connectors (the #7224 target
 * shape). Only the {@code anthropic} and {@code openai} members exist in the pilot; other providers
 * are additive later. Polymorphism is by the {@code type} discriminator; the concrete member owns
 * its backend-conditional authentication.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = AnthropicChatModel.class, name = ANTHROPIC_ID)
})
@TemplateDiscriminatorProperty(
	label = "Provider",
	group = "provider",
	name = "type",
	description = "Specify the LLM provider to use.",
	defaultValue = ANTHROPIC_ID)
public sealed interface LlmProviderConfiguration permits AnthropicChatModel {

	/** Discriminator string identifying the provider (e.g. {@code anthropic}, {@code openai}). */
	String type();

	/** The model id / deployment the request targets. */
	String model();

	/** The backend discriminator (e.g. {@code direct}, {@code bedrock}, {@code compatible}). */
	@Nullable
	String backend();

	/** Optional sparse per-element capability override, highest-precedence overlay for the matrix. */
	Optional<ModelCapabilitiesOverride> capabilityOverride();
}
```

7. **Impl — `AnthropicChatModel.java`** (full source — backend dropdown, model+params, timeouts, FEEL override):

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.chatmodel;

import static io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.ANTHROPIC_ID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.provider.capabilities.ModelCapabilitiesOverride;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.shared.ChatModelAwsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.HttpUrl;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

/** Anthropic Messages wire format. Backends: {@code direct} (API key) and {@code bedrock} (AWS). */
@TemplateSubType(id = ANTHROPIC_ID, label = "Anthropic")
public record AnthropicChatModel(
	@Valid @NotNull AnthropicBackend backend,
	@Valid @NotNull AnthropicModel model,
	@Valid @Nullable TimeoutConfiguration timeouts,
	@FEEL
	@Valid
	@TemplateProperty(
		group = "capabilities",
		label = "Model capability overrides",
		description =
			"Optional sparse capability override (FEEL context) deep-merged as the highest-precedence layer over the resolved model capabilities. Use for unknown/custom models.",
		feel = FeelMode.required,
		optional = true)
	@Nullable ModelCapabilitiesOverride capabilityOverride)
	implements LlmProviderConfiguration {

	@TemplateProperty(ignore = true)
	public static final String ANTHROPIC_ID = "anthropic";

	@Override
	public String type() {
		return ANTHROPIC_ID;
	}

	@Override
	public String model() {
		return model.model();
	}

	@Override
	public String backend() {
		return backend.type();
	}

	@Override
	public Optional<ModelCapabilitiesOverride> capabilityOverride() {
		return Optional.ofNullable(capabilityOverride);
	}

	/** Convenience accessor for the backend config record (distinct from the discriminator string). */
	public AnthropicBackend backendConfig() {
		return backend;
	}

	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
	@JsonSubTypes({
		@JsonSubTypes.Type(value = AnthropicBackend.AnthropicDirectBackend.class, name = "direct"),
		@JsonSubTypes.Type(value = AnthropicBackend.AnthropicBedrockBackend.class, name = "bedrock")
	})
	@TemplateDiscriminatorProperty(
		label = "Backend",
		group = "provider",
		name = "type",
		defaultValue = "direct",
		description = "Specify how the Anthropic Messages API is reached.")
	public sealed interface AnthropicBackend {

		/** The backend discriminator string. */
		String type();

		@TemplateSubType(id = "direct", label = "Anthropic (direct)")
		record AnthropicDirectBackend(
			@HttpUrl
			@TemplateProperty(
				group = "provider",
				label = "Custom API endpoint",
				description = "Optional custom API endpoint",
				type = TemplateProperty.PropertyType.String,
				feel = FeelMode.optional,
				optional = true)
			@Nullable String endpoint,
			@NotBlank
			@TemplateProperty(
				group = "provider",
				label = "Anthropic API key",
				type = TemplateProperty.PropertyType.String,
				feel = FeelMode.optional,
				constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
			String apiKey)
			implements AnthropicBackend {

			@Override
			public String type() {
				return "direct";
			}

			@Override
			public String toString() {
				return "AnthropicDirectBackend{endpoint=%s, apiKey=[REDACTED]}".formatted(endpoint);
			}
		}

		@TemplateSubType(id = "bedrock", label = "AWS Bedrock")
		record AnthropicBedrockBackend(
			@NotBlank
			@TemplateProperty(
				group = "provider",
				label = "Region",
				description = "Specify the AWS region (example: <code>eu-west-1</code>)",
				constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
			String region,
			@HttpUrl
			@FEEL
			@TemplateProperty(
				group = "provider",
				label = "Custom API endpoint",
				description =
					"Custom API endpoint for VPC/PrivateLink configurations, AWS GovCloud, or other non-standard deployments.",
				type = TemplateProperty.PropertyType.String,
				feel = FeelMode.optional,
				optional = true)
			@Nullable String endpoint,
			@Valid @NotNull ChatModelAwsAuthentication authentication)
			implements AnthropicBackend {

			@Override
			public String type() {
				return "bedrock";
			}
		}
	}

	public record AnthropicModel(
		@NotBlank
		@TemplateProperty(
			group = "model",
			label = "Model",
			description =
				"Specify the model ID. Details in the <a href=\"https://docs.anthropic.com/en/docs/about-claude/models/all-models\" target=\"_blank\">documentation</a>.",
			type = TemplateProperty.PropertyType.String,
			feel = FeelMode.optional,
			defaultValue = "",
			defaultValueType = TemplateProperty.DefaultValueType.String,
			placeholder = "claude-sonnet-4-6",
			constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
		String model,
		@Valid @Nullable AnthropicModelParameters parameters) {

		public record AnthropicModelParameters(
			@Min(0)
			@TemplateProperty(
				group = "model",
				label = "Maximum tokens",
				tooltip =
					"The maximum number of tokens per request to generate before stopping. <br><br>Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-max-tokens\" target=\"_blank\">documentation</a>.",
				type = TemplateProperty.PropertyType.Number,
				feel = FeelMode.required,
				optional = true)
			@Nullable Integer maxTokens,
			@Min(0)
			@TemplateProperty(
				group = "model",
				label = "Temperature",
				tooltip =
					"Floating point number between 0 and 1. The higher the number, the more randomness will be injected into the response. <br><br>Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-temperature\" target=\"_blank\">documentation</a>.",
				type = TemplateProperty.PropertyType.Number,
				feel = FeelMode.required,
				optional = true)
			@Nullable Double temperature,
			@Min(0)
			@TemplateProperty(
				group = "model",
				label = "top P",
				tooltip =
					"Floating point number between 0 and 1. Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-top-p\" target=\"_blank\">documentation</a>.",
				type = TemplateProperty.PropertyType.Number,
				feel = FeelMode.required,
				optional = true)
			@Nullable Double topP,
			@Min(0)
			@TemplateProperty(
				group = "model",
				label = "top K",
				tooltip =
					"Integer greater than 0. Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://docs.anthropic.com/en/api/messages#body-top-k\" target=\"_blank\">documentation</a>.",
				type = TemplateProperty.PropertyType.Number,
				feel = FeelMode.required,
				optional = true)
			@Nullable Integer topK) {
		}
	}
}
```

8. **Run-pass:**

```bash
mvn -q -o test -f connectors/agentic-ai/pom.xml -pl connectors/agentic-ai/connector-agentic-ai -Dtest=AnthropicChatModelTest
```
Expected: `BUILD SUCCESS`, 4 tests green.

9. **Module build + e2e green:**

```bash
mvn -q -o clean install -f connectors/agentic-ai/pom.xml
mvn -q -o test-compile -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -am
```
Expected: both `BUILD SUCCESS` (no template regeneration yet — no connector references these types).

10. **Commit:**

```bash
mvn -q spotless:apply license:format -f connectors/agentic-ai/pom.xml
git add -A && git commit -m "Add wire-format-first chatmodel package with sealed LlmProviderConfiguration and Anthropic member"
```

---

## Task 3 — `OpenAiChatModel` member: apiFamily + direct/compatible backends + extensible compatible auth dropdown

**Goal:** add the OpenAI member, extend the sealed interface and `@JsonSubTypes`, and cover the OpenAI variants (direct + compatible with `none`/`apiKey` auth) with round-trip + validation tests plus a polymorphic-dispatch assertion.

The apiFamily keys must match the bundled `model-capabilities.yaml` families (confirmed present): `openai-completions`, `openai-responses` (Anthropic's is `anthropic-messages`, but per the ratified decision apiFamily is NOT on the interface and Anthropic has no apiFamily field).

### Files

- MODIFY `.../model/request/chatmodel/LlmProviderConfiguration.java` (extend `permits` + `@JsonSubTypes`)
- CREATE `.../model/request/chatmodel/OpenAiApiFamily.java`
- CREATE `.../model/request/chatmodel/OpenAiChatModel.java`
- CREATE (test) `.../test/.../model/request/chatmodel/OpenAiChatModelTest.java`

### Interfaces

- **Consumes:** `LlmProviderConfiguration` (Task 2), `provider.shared.{TimeoutConfiguration,HttpUrl}`, `ModelCapabilitiesOverride`.
- **Produces:** `enum OpenAiApiFamily { COMPLETIONS, RESPONSES }` with `String familyKey()`; `record OpenAiChatModel(...) implements LlmProviderConfiguration` with `String apiFamilyKey()`; nested sealed `OpenAiBackend` (`direct`/`compatible`) and `CompatibleAuthentication` (`none`/`apiKey`).

### Steps

1. **Write failing test** — CREATE `OpenAiChatModelTest.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.chatmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.CompatibleAuthentication.CompatibleApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.CompatibleAuthentication.CompatibleNoAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.OpenAiBackend.OpenAiCompatibleBackend;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.OpenAiBackend.OpenAiDirectBackend;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.OpenAiModel;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiChatModelTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void deserialisesDirectCompletionsAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "openai",
          "apiFamily": "completions",
          "backend": { "type": "direct", "apiKey": "sk-oai", "organizationId": "org-1" },
          "model": { "model": "gpt-5.4", "parameters": { "temperature": 0.2 } }
        }
        """;

    final LlmProviderConfiguration parsed = mapper.readValue(json, LlmProviderConfiguration.class);

    assertThat(parsed).isInstanceOf(OpenAiChatModel.class);
    final OpenAiChatModel openai = (OpenAiChatModel) parsed;
    assertThat(openai.type()).isEqualTo("openai");
    assertThat(openai.model()).isEqualTo("gpt-5.4");
    assertThat(openai.backend()).isEqualTo("direct");
    assertThat(openai.apiFamily()).isEqualTo(OpenAiApiFamily.COMPLETIONS);
    assertThat(openai.apiFamilyKey()).isEqualTo("openai-completions");
    assertThat(openai.backendConfig()).isInstanceOf(OpenAiDirectBackend.class);

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, LlmProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void responsesFamilyMapsToResponsesKey() throws Exception {
    final String json =
        """
        {
          "type": "openai",
          "apiFamily": "responses",
          "backend": { "type": "direct", "apiKey": "sk-oai" },
          "model": { "model": "gpt-5.4" }
        }
        """;
    final OpenAiChatModel openai =
        (OpenAiChatModel) mapper.readValue(json, LlmProviderConfiguration.class);
    assertThat(openai.apiFamily()).isEqualTo(OpenAiApiFamily.RESPONSES);
    assertThat(openai.apiFamilyKey()).isEqualTo("openai-responses");
  }

  @Test
  void deserialisesCompatibleBackendWithApiKeyAuthAndCustomSurface() throws Exception {
    final String json =
        """
        {
          "type": "openai",
          "apiFamily": "completions",
          "backend": {
            "type": "compatible",
            "endpoint": "https://gateway.example.com/v1",
            "headers": { "X-Tenant": "acme" },
            "queryParameters": { "api-version": "2024-10" },
            "requestParameters": { "seed": 7 },
            "authentication": { "type": "apiKey", "apiKey": "compat-secret" }
          },
          "model": { "model": "custom-model" }
        }
        """;

    final OpenAiChatModel openai =
        (OpenAiChatModel) mapper.readValue(json, LlmProviderConfiguration.class);

    assertThat(openai.backend()).isEqualTo("compatible");
    final OpenAiCompatibleBackend compatible = (OpenAiCompatibleBackend) openai.backendConfig();
    assertThat(compatible.endpoint()).isEqualTo("https://gateway.example.com/v1");
    assertThat(compatible.headers()).containsEntry("X-Tenant", "acme");
    assertThat(compatible.queryParameters()).containsEntry("api-version", "2024-10");
    assertThat(compatible.requestParameters()).containsEntry("seed", 7);
    assertThat(compatible.authentication()).isInstanceOf(CompatibleApiKeyAuthentication.class);
    assertThat(compatible.authentication().toString()).doesNotContain("compat-secret");
  }

  @Test
  void compatibleBackendSupportsNoAuth() throws Exception {
    final String json =
        """
        {
          "type": "openai",
          "apiFamily": "completions",
          "backend": {
            "type": "compatible",
            "endpoint": "https://gateway.example.com/v1",
            "authentication": { "type": "none" }
          },
          "model": { "model": "custom-model" }
        }
        """;
    final OpenAiChatModel openai =
        (OpenAiChatModel) mapper.readValue(json, LlmProviderConfiguration.class);
    final OpenAiCompatibleBackend compatible = (OpenAiCompatibleBackend) openai.backendConfig();
    assertThat(compatible.authentication()).isInstanceOf(CompatibleNoAuthentication.class);
  }

  @Test
  void directBackendRejectsBlankApiKey() {
    final var model =
        new OpenAiChatModel(
            OpenAiApiFamily.COMPLETIONS,
            new OpenAiDirectBackend("   ", null, null),
            new OpenAiModel("gpt-5.4", null),
            null,
            null);
    assertThat(validator.validate(model))
        .anyMatch(v -> v.getPropertyPath().toString().contains("apiKey"));
  }

  @Test
  void validCompatibleModelHasNoViolations() {
    final var model =
        new OpenAiChatModel(
            OpenAiApiFamily.RESPONSES,
            new OpenAiCompatibleBackend(
                "https://gateway.example.com/v1",
                Map.of(),
                Map.of(),
                Map.of(),
                new CompatibleNoAuthentication()),
            new OpenAiModel("custom-model", null),
            null,
            null);
    assertThat(validator.validate(model)).isEmpty();
  }
}
```

2. **Run-fail:**

```bash
mvn -q -o test -f connectors/agentic-ai/pom.xml -pl connectors/agentic-ai/connector-agentic-ai -Dtest=OpenAiChatModelTest
```
Expected: compilation failure — `cannot find symbol: class OpenAiChatModel` / `OpenAiApiFamily`.

3. **Impl — extend `LlmProviderConfiguration.java`.** Add the OpenAI import-free reference and edit two spots. New `@JsonSubTypes` block and `permits` clause (replace the existing single-entry versions from Task 2):

   - Add the static import at the top with the existing one:
```java
import static io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.ANTHROPIC_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.OPENAI_ID;
```
   - Replace the `@JsonSubTypes({...})` annotation body with:
```java
@JsonSubTypes({
  @JsonSubTypes.Type(value = AnthropicChatModel.class, name = ANTHROPIC_ID),
  @JsonSubTypes.Type(value = OpenAiChatModel.class, name = OPENAI_ID)
})
```
   - Replace the `permits` clause:
```java
public sealed interface LlmProviderConfiguration permits AnthropicChatModel, OpenAiChatModel {
```
   (Everything else in the interface — `@TemplateDiscriminatorProperty`, the four accessors — is unchanged.)

4. **Impl — `OpenAiApiFamily.java`:**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.chatmodel;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenAI wire-format selector. Maps to the capability-matrix api-family keys in {@code
 * model-capabilities.yaml} ({@code openai-completions} / {@code openai-responses}).
 */
public enum OpenAiApiFamily {
  @JsonProperty("completions")
  COMPLETIONS("openai-completions"),
  @JsonProperty("responses")
  RESPONSES("openai-responses");

  private final String familyKey;

  OpenAiApiFamily(String familyKey) {
    this.familyKey = familyKey;
  }

  /** The api-family key used to look the model up in the capability matrix. */
  public String familyKey() {
    return familyKey;
  }
}
```

5. **Impl — `OpenAiChatModel.java`** (full source):

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.chatmodel;

import static io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.OPENAI_ID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.provider.capabilities.ModelCapabilitiesOverride;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.HttpUrl;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/** OpenAI wire formats ({@code completions}/{@code responses}); backends {@code direct}/{@code compatible}. */
@TemplateSubType(id = OPENAI_ID, label = "OpenAI")
public record OpenAiChatModel(
	@NotNull
	@TemplateProperty(
		group = "provider",
		label = "API family",
		description = "OpenAI wire format to use.",
		type = TemplateProperty.PropertyType.Dropdown,
		defaultValue = "completions",
		choices = {
			@TemplateProperty.DropdownPropertyChoice(value = "completions", label = "Chat Completions"),
			@TemplateProperty.DropdownPropertyChoice(value = "responses", label = "Responses")
		})
	OpenAiApiFamily apiFamily,
	@Valid @NotNull OpenAiBackend backend,
	@Valid @NotNull OpenAiModel model,
	@Valid @Nullable TimeoutConfiguration timeouts,
	@FEEL
	@Valid
	@TemplateProperty(
		group = "capabilities",
		label = "Model capability overrides",
		description =
			"Optional sparse capability override (FEEL context) deep-merged as the highest-precedence layer over the resolved model capabilities. Use for unknown/custom models.",
		feel = FeelMode.required,
		optional = true)
	@Nullable ModelCapabilitiesOverride capabilityOverride)
	implements LlmProviderConfiguration {

	@TemplateProperty(ignore = true)
	public static final String OPENAI_ID = "openai";

	@Override
	public String type() {
		return OPENAI_ID;
	}

	@Override
	public String model() {
		return model.model();
	}

	@Override
	public String backend() {
		return backend.type();
	}

	@Override
	public Optional<ModelCapabilitiesOverride> capabilityOverride() {
		return Optional.ofNullable(capabilityOverride);
	}

	/** The capability-matrix api-family key ({@code openai-completions} / {@code openai-responses}). */
	public String apiFamilyKey() {
		return apiFamily.familyKey();
	}

	/** Convenience accessor for the backend config record (distinct from the discriminator string). */
	public OpenAiBackend backendConfig() {
		return backend;
	}

	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
	@JsonSubTypes({
		@JsonSubTypes.Type(value = OpenAiBackend.OpenAiDirectBackend.class, name = "direct"),
		@JsonSubTypes.Type(value = OpenAiBackend.OpenAiCompatibleBackend.class, name = "compatible")
	})
	@TemplateDiscriminatorProperty(
		label = "Backend",
		group = "provider",
		name = "type",
		defaultValue = "direct",
		description = "Specify how the OpenAI-compatible API is reached.")
	public sealed interface OpenAiBackend {

		/** The backend discriminator string. */
		String type();

		@TemplateSubType(id = "direct", label = "OpenAI (direct)")
		record OpenAiDirectBackend(
			@NotBlank
			@TemplateProperty(
				group = "provider",
				label = "OpenAI API key",
				type = TemplateProperty.PropertyType.String,
				feel = FeelMode.optional,
				constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
			String apiKey,
			@TemplateProperty(
				group = "provider",
				label = "Organization ID",
				description =
					"For members of multiple organizations. Details in the <a href=\"https://platform.openai.com/docs/api-reference/authentication\" target=\"_blank\">documentation</a>.",
				type = TemplateProperty.PropertyType.String,
				feel = FeelMode.optional,
				optional = true)
			@Nullable String organizationId,
			@TemplateProperty(
				group = "provider",
				label = "Project ID",
				description =
					"For accounts with multiple projects. Details in the <a href=\"https://platform.openai.com/docs/api-reference/authentication\" target=\"_blank\">documentation</a>.",
				type = TemplateProperty.PropertyType.String,
				feel = FeelMode.optional,
				optional = true)
			@Nullable String projectId)
			implements OpenAiBackend {

			@Override
			public String type() {
				return "direct";
			}

			@Override
			public String toString() {
				return "OpenAiDirectBackend{apiKey=[REDACTED], organizationId=%s, projectId=%s}"
					.formatted(organizationId, projectId);
			}
		}

		@TemplateSubType(id = "compatible", label = "OpenAI Compatible")
		record OpenAiCompatibleBackend(
			@NotBlank
			@HttpUrl
			@TemplateProperty(
				group = "provider",
				label = "API endpoint",
				tooltip = "Specify an endpoint to use the connector with an OpenAI compatible API.",
				type = TemplateProperty.PropertyType.String,
				feel = FeelMode.optional,
				constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
			String endpoint,
			@FEEL
			@TemplateProperty(
				group = "provider",
				label = "Headers",
				description = "Map of HTTP headers to add to the request.",
				feel = FeelMode.required,
				optional = true)
			@Nullable Map<String, String> headers,
			@FEEL
			@Valid
			@TemplateProperty(
				group = "provider",
				label = "Query parameters",
				description = "Map of query parameters to add to the request URL.",
				feel = FeelMode.required,
				optional = true)
			@Nullable Map<@NotBlank String, String> queryParameters,
			@FEEL
			@TemplateProperty(
				group = "provider",
				label = "Request parameters",
				description = "Map of additional request (body) parameters to include.",
				feel = FeelMode.required,
				optional = true)
			@Nullable Map<String, Object> requestParameters,
			@Valid @NotNull CompatibleAuthentication authentication)
			implements OpenAiBackend {

			@Override
			public String type() {
				return "compatible";
			}
		}
	}

	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
	@JsonSubTypes({
		@JsonSubTypes.Type(value = CompatibleAuthentication.CompatibleNoAuthentication.class, name = "none"),
		@JsonSubTypes.Type(
			value = CompatibleAuthentication.CompatibleApiKeyAuthentication.class, name = "apiKey")
	})
	@TemplateDiscriminatorProperty(
		label = "Authentication",
		group = "provider",
		name = "type",
		defaultValue = "none",
		description =
			"Authentication for the OpenAI-compatible gateway. Extensible: more schemes can be added later without breaking existing configs.")
	public sealed interface CompatibleAuthentication {

		@TemplateSubType(id = "none", label = "None")
		record CompatibleNoAuthentication() implements CompatibleAuthentication {
		}

		@TemplateSubType(id = "apiKey", label = "API key")
		record CompatibleApiKeyAuthentication(
			@NotBlank
			@TemplateProperty(
				group = "provider",
				label = "API key",
				type = TemplateProperty.PropertyType.String,
				feel = FeelMode.optional,
				constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
			String apiKey)
			implements CompatibleAuthentication {

			@Override
			public String toString() {
				return "CompatibleApiKeyAuthentication{apiKey=[REDACTED]}";
			}
		}
	}

	public record OpenAiModel(
		@NotBlank
		@TemplateProperty(
			group = "model",
			label = "Model",
			description =
				"Specify the model ID. Details in the <a href=\"https://platform.openai.com/docs/models\" target=\"_blank\">documentation</a>.",
			type = TemplateProperty.PropertyType.String,
			feel = FeelMode.optional,
			defaultValue = "gpt-5.4",
			defaultValueType = TemplateProperty.DefaultValueType.String,
			constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
		String model,
		@Valid @Nullable OpenAiModelParameters parameters) {

		public record OpenAiModelParameters(
			@Min(0)
			@TemplateProperty(
				group = "model",
				label = "Maximum completion tokens",
				tooltip =
					"The maximum number of tokens per request to generate before stopping. <br><br>Details in the <a href=\"https://platform.openai.com/docs/api-reference/chat/create#chat-create-max_completion_tokens\" target=\"_blank\">documentation</a>.",
				type = TemplateProperty.PropertyType.Number,
				feel = FeelMode.required,
				optional = true)
			@Nullable Integer maxCompletionTokens,
			@Min(0)
			@TemplateProperty(
				group = "model",
				label = "Temperature",
				tooltip =
					"Floating point number between 0 and 2. The higher the number, the more randomness will be injected into the response. <br><br>Details in the <a href=\"https://platform.openai.com/docs/api-reference/chat/create#chat-create-temperature\" target=\"_blank\">documentation</a>.",
				type = TemplateProperty.PropertyType.Number,
				feel = FeelMode.required,
				optional = true)
			@Nullable Double temperature,
			@Min(0)
			@TemplateProperty(
				group = "model",
				label = "top P",
				tooltip =
					"Recommended for advanced use cases only (you usually only need to use temperature). <br><br>Details in the <a href=\"https://platform.openai.com/docs/api-reference/chat/create#chat-create-top_p\" target=\"_blank\">documentation</a>.",
				type = TemplateProperty.PropertyType.Number,
				feel = FeelMode.required,
				optional = true)
			@Nullable Double topP) {
		}
	}
}
```

6. **Run-pass:**

```bash
mvn -q -o test -f connectors/agentic-ai/pom.xml -pl connectors/agentic-ai/connector-agentic-ai -Dtest=OpenAiChatModelTest,AnthropicChatModelTest
```
Expected: `BUILD SUCCESS`, all tests green (Anthropic still passes with the extended sealed interface).

7. **Module build + e2e compile:**

```bash
mvn -q -o clean install -f connectors/agentic-ai/pom.xml
mvn -q -o test-compile -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -am
```
Expected: `BUILD SUCCESS` both.

8. **Commit:**

```bash
mvn -q spotless:apply license:format -f connectors/agentic-ai/pom.xml
git add -A && git commit -m "Add OpenAiChatModel member with apiFamily, direct/compatible backends and extensible compatible auth"
```

---

## Task 4 — `LlmProviderChatModelApiConfiguration` + registry fail-loud unit test

**Goal:** add the LOCKED single-arg `ChatModelApiConfiguration` implementation and prove the registry fails loud (clear `ConnectorException`, not NPE) when it is resolved with no LLM-provider factory registered.

### Files

- CREATE `.../aiagent/framework/api/LlmProviderChatModelApiConfiguration.java`
- CREATE (test) `.../test/.../aiagent/framework/LlmProviderChatModelApiConfigurationRegistryTest.java`

### Interfaces

- **Produces:** `record LlmProviderChatModelApiConfiguration(LlmProviderConfiguration configuration) implements ChatModelApiConfiguration` — single component, LOCKED. Capability override is reached via `configuration.capabilityOverride()`.
- **Consumes:** `ChatModelApiRegistryImpl`, `Langchain4JChatModelApiFactory` behavior (`supports` returns true only for `ProviderChatModelApiConfiguration`), `AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL`.

### Steps

1. **Write failing test** — CREATE `LlmProviderChatModelApiConfigurationRegistryTest.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes;
import io.camunda.connector.agenticai.aiagent.provider.api.ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.provider.api.ChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.provider.api.LlmProviderChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.provider.langchain4j.Langchain4JAiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.provider.langchain4j.Langchain4JChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicBackend.AnthropicDirectBackend;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicModel;
import io.camunda.connector.api.error.ConnectorException;

import java.util.List;

import org.junit.jupiter.api.Test;

class LlmProviderChatModelApiConfigurationRegistryTest {

	@Test
	void wrapsProviderConfigAndExposesCapabilityOverrideViaConfiguration() {
		final var config =
			new LlmProviderChatModelApiConfiguration(
				new AnthropicChatModel(
					new AnthropicDirectBackend(null, "sk-ant"),
					new AnthropicModel("claude-sonnet-4-6", null),
					null,
					null));

		assertThat(config.configuration()).isInstanceOf(AnthropicChatModel.class);
		assertThat(config.configuration().capabilityOverride()).isEmpty();
	}

	@Test
	void registryFailsLoudWhenNoFactorySupportsLlmProviderConfiguration() {
		// Only the bridge factory is registered; it supports ProviderChatModelApiConfiguration only.
		final ChatModelApiFactory bridge =
			new Langchain4JChatModelApiFactory(mock(Langchain4JAiFrameworkAdapter.class));
		final var registry = new ChatModelApiRegistryImpl(List.of(bridge));

		final ChatModelApiConfiguration llmProviderConfig =
			new LlmProviderChatModelApiConfiguration(
				new AnthropicChatModel(
					new AnthropicDirectBackend(null, "sk-ant"),
					new AnthropicModel("claude-sonnet-4-6", null),
					null,
					null));

		assertThatThrownBy(() -> registry.resolve(llmProviderConfig))
			.isInstanceOf(ConnectorException.class)
			.hasMessageContaining("No chat model registered for configuration")
			.extracting(e -> ((ConnectorException) e).getErrorCode())
			.isEqualTo(AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL);
	}
}
```

2. **Run-fail:**

```bash
mvn -q -o test -f connectors/agentic-ai/pom.xml -pl connectors/agentic-ai/connector-agentic-ai -Dtest=LlmProviderChatModelApiConfigurationRegistryTest
```
Expected: compilation failure — `cannot find symbol: class LlmProviderChatModelApiConfiguration`.

3. **Impl — `LlmProviderChatModelApiConfiguration.java`:**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.provider.api;

import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.LlmProviderConfiguration;

/**
 * {@link ChatModelApiConfiguration} backed by the wire-format-first {@link
 * LlmProviderConfiguration} surfaced by the v2 connectors. LLM-provider factories (Anthropic, OpenAI)
 * will {@code supports(...)} this type at {@code getOrder() < 1000}; until they exist the registry
 * fails loud with {@code ERROR_CODE_FAILED_MODEL_CALL} for it. The per-element capability override
 * is reached via {@link LlmProviderConfiguration#capabilityOverride()} — do not add a second
 * component here.
 */
public record LlmProviderChatModelApiConfiguration(LlmProviderConfiguration configuration)
	implements ChatModelApiConfiguration {
}
```

4. **Run-pass:**

```bash
mvn -q -o test -f connectors/agentic-ai/pom.xml -pl connectors/agentic-ai/connector-agentic-ai -Dtest=LlmProviderChatModelApiConfigurationRegistryTest
```
Expected: `BUILD SUCCESS`, 2 tests green.

5. **Module build + e2e compile:**

```bash
mvn -q -o clean install -f connectors/agentic-ai/pom.xml
mvn -q -o test-compile -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -am
```
Expected: `BUILD SUCCESS` both.

6. **Commit:**

```bash
mvn -q spotless:apply license:format -f connectors/agentic-ai/pom.xml
git add -A && git commit -m "Add LlmProviderChatModelApiConfiguration and verify registry fails loud without a LLM-provider factory"
```

---

## Task 5 — Move provider→config mapping to entry points; version-agnostic contexts (v1 byte-identical)

**Goal:** move the provider→`ChatModelApiConfiguration` mapping OUT of the handler and INTO the connector entry points; make `AgentConfiguration` carry the generic SPI config + `modelName`/`modelProvider` telemetry strings; refactor the two flavor execution contexts to version-agnostic constructors (receiving the built config + strings + shared request DATA). No v2 types yet. v1 behavior stays byte-identical (same `ProviderChatModelApiConfiguration` instance, same bridge output, same telemetry strings).

### Files

- MODIFY `.../aiagent/model/AgentConfiguration.java` (first component → SPI config; add `modelName`/`modelProvider`)
- MODIFY `.../aiagent/model/OutboundConnectorAgentExecutionContext.java` (version-agnostic constructor)
- MODIFY `.../aiagent/model/JobWorkerAgentExecutionContext.java` (version-agnostic constructor)
- MODIFY `.../aiagent/AiAgentFunction.java` (build config at entry point)
- MODIFY `.../aiagent/AiAgentJobWorker.java` (build config at entry point)
- MODIFY `.../aiagent/agent/BaseAgentRequestHandler.java` (one-liner resolve; drop unused import)
- MODIFY `.../aiagent/agentinstance/CamundaAgentInstanceClient.java` (read `modelName`/`modelProvider`)
- MODIFY `.../aiagent/framework/langchain4j/Langchain4JAiFrameworkAdapter.java` (unwrap the SPI config)
- MODIFY (tests) ~13 files that construct `new AgentConfiguration(...)` positionally (see step 11)
- CREATE (test) `.../test/.../aiagent/model/AgentConfigurationMappingTest.java`

### Interfaces (exact signatures)

- **Produces:** `record AgentConfiguration(ChatModelApiConfiguration chatModelApiConfiguration, String modelName, String modelProvider, SystemPromptConfiguration systemPrompt, UserPromptConfiguration userPrompt, @Nullable MemoryConfiguration memory, @Nullable LimitsConfiguration limits, @Nullable EventHandlingConfiguration events, @Nullable ResponseConfiguration response)` — helpers `contextWindowSize()`/`maxModelCalls()` unchanged; auto-generated `chatModelApiConfiguration()`/`modelName()`/`modelProvider()` accessors.
- **Produces:** `OutboundConnectorAgentExecutionContext(JobContext jobContext, OutboundConnectorAgentRequest.OutboundConnectorAgentRequestData data, ChatModelApiConfiguration chatModelApiConfiguration, String modelName, String modelProvider, ProcessDefinitionAdHocToolElementsResolver toolElementsResolver)`.
- **Produces:** `JobWorkerAgentExecutionContext(JobContext jobContext, JobWorkerAgentRequest.JobWorkerAgentRequestData data, AgentContext initialAgentContext, List<ToolCallResult> initialToolCallResults, List<AdHocToolElement> toolElements, ChatModelApiConfiguration chatModelApiConfiguration, String modelName, String modelProvider)`.
- **Consumes:** `ProviderChatModelApiConfiguration(ProviderConfiguration)`; `ProviderConfiguration.model()`/`.providerType()`; the v1 `*RequestData` accessors (`context()`,`systemPrompt()`,`userPrompt()`,`tools()`,`memory()`,`limits()`,`response()` for outbound; `systemPrompt()`,`userPrompt()`,`memory()`,`limits()`,`events()`,`response()` for job worker) and job-worker top-level `agentContext()`/`toolCallResults()`/`toolElements()` (verified).

**Known layering tradeoff (flag, do not avoid):** `AgentConfiguration` (package `aiagent.model`) now references `ChatModelApiConfiguration` (package `aiagent.framework.api`). `framework.api` already references `model` (via `ChatModelRequest`), so this is a mutual package reference. It compiles; there is no ArchUnit enforcement today (future epic #7537); `framework.api` is the neutral SPI (no vendor SDK). Accept it.

### Steps

1. **Write failing test** — CREATE `AgentConfigurationMappingTest.java` pinning the new v1 mapping contract (the assertions drive the whole refactor):

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.aiagent.provider.api.ProviderChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.OutboundConnectorAgentRequest.OutboundConnectorAgentRequestData;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicModel;
import io.camunda.connector.api.outbound.JobContext;
import org.junit.jupiter.api.Test;

class AgentConfigurationMappingTest {

	@Test
	void outboundContextCarriesWrappedProviderConfigAndTelemetryStrings() {
		final var provider =
			new AnthropicProviderConfiguration(
				new AnthropicConnection(
					null,
					new AnthropicAuthentication("sk-ant"),
					null,
					new AnthropicModel("claude-sonnet-4-6", null)));
		final var data = new OutboundConnectorAgentRequestData(null, null, null, null, null, null, null);

		final var ctx =
			new OutboundConnectorAgentExecutionContext(
				mock(JobContext.class),
				data,
				new ProviderChatModelApiConfiguration(provider),
				provider.model(),
				provider.providerType(),
				mock(ProcessDefinitionAdHocToolElementsResolver.class));

		assertThat(ctx.configuration().chatModelApiConfiguration())
			.isEqualTo(new ProviderChatModelApiConfiguration(provider));
		assertThat(ctx.configuration().modelName()).isEqualTo("claude-sonnet-4-6");
		assertThat(ctx.configuration().modelProvider()).isEqualTo("anthropic");
	}
}
```
   (`OutboundConnectorAgentRequestData`'s 7 components are `context`,`systemPrompt`,`userPrompt`,`tools`,`memory`,`limits`,`response` — all nullable for this construction; the context only stores them.)

2. **Run-fail:**

```bash
mvn -q -o test -f connectors/agentic-ai/pom.xml -pl connectors/agentic-ai/connector-agentic-ai -Dtest=AgentConfigurationMappingTest
```
Expected: compilation failure — the new `OutboundConnectorAgentExecutionContext(...)` constructor signature does not exist and `AgentConfiguration` has no `chatModelApiConfiguration()`.

3. **Impl — `AgentConfiguration.java`** (full source):

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.aiagent.provider.api.ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Static per-invocation configuration. Built once from AgentExecutionContext at the start of each
 * handler invocation; does not change mid-conversation. Carries the resolved, provider-neutral
 * {@link ChatModelApiConfiguration} the registry dispatches on, plus {@code modelName}/{@code
 * modelProvider} for agent-instance telemetry (the SPI marker exposes no methods, so the strings
 * are captured explicitly by the connector entry point). Transient — never persisted.
 */
public record AgentConfiguration(
	ChatModelApiConfiguration chatModelApiConfiguration,
	String modelName,
	String modelProvider,
	SystemPromptConfiguration systemPrompt,
	UserPromptConfiguration userPrompt,
	@Nullable MemoryConfiguration memory,
	@Nullable LimitsConfiguration limits,
	@Nullable EventHandlingConfiguration events,
	@Nullable ResponseConfiguration response) {

	public static final int DEFAULT_CONTEXT_WINDOW_SIZE = 20;
	public static final int DEFAULT_MAX_MODEL_CALLS = 10;

	public int contextWindowSize() {
		return Optional.ofNullable(memory)
			.map(MemoryConfiguration::contextWindowSize)
			.orElse(DEFAULT_CONTEXT_WINDOW_SIZE);
	}

	public int maxModelCalls() {
		return Optional.ofNullable(limits)
			.map(LimitsConfiguration::maxModelCalls)
			.orElse(DEFAULT_MAX_MODEL_CALLS);
	}
}
```

4. **Impl — `OutboundConnectorAgentExecutionContext.java`** (full source, version-agnostic):

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.aiagent.provider.api.ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.OutboundConnectorAgentRequest.OutboundConnectorAgentRequestData;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ToolsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.api.outbound.JobContext;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/** Version-agnostic execution context for the AI Agent Task flavor (serves v1 and v2). */
public class OutboundConnectorAgentExecutionContext implements AgentExecutionContext {

	private final JobContext jobContext;
	private final OutboundConnectorAgentRequestData data;
	private final ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;
	private final AgentConfiguration configuration;

	@Nullable
	private List<AdHocToolElement> toolElements;

	public OutboundConnectorAgentExecutionContext(
		JobContext jobContext,
		OutboundConnectorAgentRequestData data,
		ChatModelApiConfiguration chatModelApiConfiguration,
		String modelName,
		String modelProvider,
		ProcessDefinitionAdHocToolElementsResolver toolElementsResolver) {
		this.jobContext = jobContext;
		this.data = data;
		this.toolElementsResolver = toolElementsResolver;
		this.configuration =
			new AgentConfiguration(
				chatModelApiConfiguration,
				modelName,
				modelProvider,
				data.systemPrompt(),
				data.userPrompt(),
				data.memory(),
				data.limits(),
				// the outbound connector flavor does not support event handling
				null,
				data.response());
	}

	@Override
	public JobContext jobContext() {
		return jobContext;
	}

	@Override
	public AgentContext initialAgentContext() {
		return data.context();
	}

	@Override
	public List<ToolCallResult> initialToolCallResults() {
		return Optional.ofNullable(data.tools())
			.map(ToolsConfiguration::toolCallResults)
			.orElseGet(Collections::emptyList);
	}

	@Override
	public List<AdHocToolElement> toolElements() {
		if (toolElements != null) {
			return toolElements;
		}
		return toolElements = resolveToolElements();
	}

	private List<AdHocToolElement> resolveToolElements() {
		final var toolsContainerElementId =
			Optional.ofNullable(data.tools())
				.map(ToolsConfiguration::containerElementId)
				.filter(id -> !id.isBlank())
				.orElse(null);

		if (toolsContainerElementId == null) {
			return Collections.emptyList();
		}

		return toolElementsResolver.resolveToolElements(
			jobContext.getProcessDefinitionKey(), toolsContainerElementId);
	}

	@Override
	public UserPromptConfiguration userPrompt() {
		return data.userPrompt();
	}

	public @Nullable ToolsConfiguration tools() {
		return data.tools();
	}

	@Override
	public AgentConfiguration configuration() {
		return configuration;
	}
}
```

5. **Impl — `JobWorkerAgentExecutionContext.java`** (full source, version-agnostic):

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.aiagent.provider.api.ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerAgentRequest.JobWorkerAgentRequestData;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.api.outbound.JobContext;

import java.util.List;

/** Version-agnostic execution context for the AI Agent Sub-process flavor (serves v1 and v2). */
public class JobWorkerAgentExecutionContext implements AgentExecutionContext {
	private final JobContext jobContext;
	private final JobWorkerAgentRequestData data;
	private final AgentContext initialAgentContext;
	private final List<ToolCallResult> initialToolCallResults;
	private final List<AdHocToolElement> toolElements;
	private final AgentConfiguration configuration;

	public JobWorkerAgentExecutionContext(
		JobContext jobContext,
		JobWorkerAgentRequestData data,
		AgentContext initialAgentContext,
		List<ToolCallResult> initialToolCallResults,
		List<AdHocToolElement> toolElements,
		ChatModelApiConfiguration chatModelApiConfiguration,
		String modelName,
		String modelProvider) {
		this.jobContext = jobContext;
		this.data = data;
		this.initialAgentContext = initialAgentContext;
		this.initialToolCallResults = initialToolCallResults;
		this.toolElements = toolElements;
		this.configuration =
			new AgentConfiguration(
				chatModelApiConfiguration,
				modelName,
				modelProvider,
				data.systemPrompt(),
				data.userPrompt(),
				data.memory(),
				data.limits(),
				data.events(),
				data.response());
	}

	@Override
	public JobContext jobContext() {
		return jobContext;
	}

	@Override
	public AgentContext initialAgentContext() {
		return initialAgentContext;
	}

	@Override
	public List<ToolCallResult> initialToolCallResults() {
		return initialToolCallResults;
	}

	@Override
	public List<AdHocToolElement> toolElements() {
		return toolElements;
	}

	@Override
	public UserPromptConfiguration userPrompt() {
		return data.userPrompt();
	}

	@Override
	public AgentConfiguration configuration() {
		return configuration;
	}

	/**
	 * Job-worker-specific response configuration. Exposes {@code includeAgentContext}, which is not
	 * part of the generic {@link io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration}.
	 */
	public JobWorkerResponseConfiguration response() {
		return data.response();
	}
}
```

6. **Impl — `AiAgentFunction.java`.** Add `import io.camunda.connector.agenticai.aiagent.provider.api.ProviderChatModelApiConfiguration;` and replace the `execute` method body:

```java
  @Override
  public AiAgentTaskConnectorResponse execute(OutboundConnectorContext context) {
    var request = context.bindVariables(OutboundConnectorAgentRequest.class);
    var provider = request.provider();
    var executionContext =
        new OutboundConnectorAgentExecutionContext(
            context.getJobContext(),
            request.data(),
            new ProviderChatModelApiConfiguration(provider),
            provider.model(),
            provider.providerType(),
            toolElementsResolver);
    return agentRequestHandler.handleRequest(executionContext);
  }
```

7. **Impl — `AiAgentJobWorker.java`.** Add `import io.camunda.connector.agenticai.aiagent.provider.api.ProviderChatModelApiConfiguration;` and replace the `execute` method body (constants/fields unchanged):

```java
  @Override
  public AiAgentSubProcessConnectorResponse execute(OutboundConnectorContext context)
      throws Exception {
    var request = context.bindVariables(JobWorkerAgentRequest.class);
    var provider = request.provider();
    var executionContext =
        new JobWorkerAgentExecutionContext(
            context.getJobContext(),
            request.data(),
            request.agentContext(),
            request.toolCallResults(),
            request.toolElements(),
            new ProviderChatModelApiConfiguration(provider),
            provider.model(),
            provider.providerType());
    return agentRequestHandler.handleRequest(executionContext);
  }
```

8. **Impl — `BaseAgentRequestHandler.java`.** DELETE the import `import io.camunda.connector.agenticai.aiagent.provider.api.ProviderChatModelApiConfiguration;` (line ~19; now unused). In `proceed(...)`, replace the resolve block (currently `final var chatModel = chatModelApiRegistry.resolve(new ProviderChatModelApiConfiguration(executionContext.configuration().provider()));`) with:

```java
    final var chatModel =
        chatModelApiRegistry.resolve(agentConfiguration.chatModelApiConfiguration());
```
   (`agentConfiguration` is the local declared earlier in `proceed(...)` as `var agentConfiguration = executionContext.configuration();` — reuse it.) Byte-identical for v1: the same `ProviderChatModelApiConfiguration` instance now arrives via the config built at the entry point.

9. **Impl — `CamundaAgentInstanceClient.java`.** In `executeCreate(...)`, replace the four `configuration.provider().model()`/`.providerType()` reads with the telemetry strings on `AgentConfiguration`:

```java
    final long elementInstanceKey = agentExecutionContext.jobContext().getElementInstanceKey();
    final var configuration = agentExecutionContext.configuration();
    LOGGER.debug(
        "Creating agent instance for element instance {}: model={}, provider={}",
        elementInstanceKey,
        configuration.modelName(),
        configuration.modelProvider());

    var command =
        camundaClient
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(elementInstanceKey)
            .model(configuration.modelName())
            .provider(configuration.modelProvider())
            .systemPrompt(configuration.systemPrompt().prompt());
```
   (Keep the rest of the method — `limits`, `execute()`, logging — unchanged.)

10. **Impl — `Langchain4JAiFrameworkAdapter.java`.** In `executeChatRequest(...)`, unwrap the SPI config. Add `import io.camunda.connector.agenticai.aiagent.provider.api.ProviderChatModelApiConfiguration;` and replace the try-with-resources head (currently `try (final var chatModel = chatModelFactory.createChatModel(configuration.provider())) {`) with:

```java
    final var providerConfiguration =
        ((ProviderChatModelApiConfiguration) configuration.chatModelApiConfiguration())
            .providerConfiguration();
    try (final var chatModel = chatModelFactory.createChatModel(providerConfiguration)) {
```
   Safe cast: `Langchain4JChatModelApiFactory.supports(...)` routes only `ProviderChatModelApiConfiguration` to this adapter, so the bridge only ever runs with that impl. v1 bridge output is byte-identical.

11. **Impl — update all positional `new AgentConfiguration(...)` constructions in tests.** The old first arg was a `ProviderConfiguration`; the new shape is `(ChatModelApiConfiguration, String modelName, String modelProvider, systemPrompt, userPrompt, memory, limits, events, response)`. Mechanical transform for each: wrap the old provider arg as `new ProviderChatModelApiConfiguration(<oldProviderArg>)`, then insert two strings (`<oldProviderArg>.model()`, `<oldProviderArg>.providerType()` — or literal `"model"`/`"anthropic"` where the test used a mock/no real provider), then keep the remaining args. Files to update (verified during exploration):
    - `memory/conversation/document/CamundaDocumentConversationStoreTest`
    - `memory/conversation/ConversationStoreRegistryTest`
    - `memory/conversation/awsagentcore/AwsAgentCoreConversationStoreReasoningRoundTripTest`
    - `memory/conversation/awsagentcore/AwsAgentCoreConversationStoreTest`
    - `framework/langchain4j/Langchain4JAiFrameworkAdapterTest`
    - `systemprompt/SystemPromptComposerImplTest`
    - `agent/JobWorkerAgentRequestHandlerTest` (×3 constructions)
    - `agent/AgentConversationTurnInputComposerImplTest` (×6)
    - `agent/OutboundConnectorAgentRequestHandlerTest` (×3)
    - `agent/AgentResponseHandlerTest`
    - `model/AgentConversationTest`
    - `agentinstance/CamundaAgentInstanceClientTest`
    Example (before → after) for a test that used a real provider `var provider = new AnthropicProviderConfiguration(...);`:
```java
    // before
    new AgentConfiguration(provider, systemPrompt, userPrompt, memory, limits, events, response);
    // after
    new AgentConfiguration(
        new ProviderChatModelApiConfiguration(provider),
        provider.model(),
        provider.providerType(),
        systemPrompt, userPrompt, memory, limits, events, response);
```
    For `Langchain4JAiFrameworkAdapterTest`, additionally the test must set up the config so the adapter's unwrap works (the wrapped provider is what `chatModelFactory.createChatModel(...)` is verified against). For `CamundaAgentInstanceClientTest`, assert against `modelName`/`modelProvider` (the client now reads those). Run the module build to surface any construction missed.

12. **Run-pass (targeted):**

```bash
mvn -q -o test -f connectors/agentic-ai/pom.xml -pl connectors/agentic-ai/connector-agentic-ai -Dtest=AgentConfigurationMappingTest,CamundaAgentInstanceClientTest,Langchain4JAiFrameworkAdapterTest,BaseAgentRequestHandlerTest
```
Expected: `BUILD SUCCESS`.

13. **Full module build + e2e green:**

```bash
mvn -q -o clean install -f connectors/agentic-ai/pom.xml
mvn -q -o test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -am -Dtest=BaseAiAgentJobWorkerTest
```
Expected: `BUILD SUCCESS` both (v1 behavior unchanged; any missed `new AgentConfiguration(...)` surfaces here as a compile error — fix and rerun).

14. **Commit:**

```bash
mvn -q spotless:apply license:format -f connectors/agentic-ai/pom.xml
git add -A && git commit -m "Move provider→ChatModelApiConfiguration mapping to connector entry points; make execution contexts version-agnostic (v1 unchanged)"
```

---

## Task 6 — v2 connectors as new entry points (reuse the shared handler + context)

**Goal:** add the two v2 connectors as NEW ENTRY POINTS ONLY (locked ids/types). Each binds a v2 request record carrying a top-level `LlmProviderConfiguration configuration` + the SAME shared `*RequestData` as v1, builds `new LlmProviderChatModelApiConfiguration(request.configuration())` + telemetry strings from `configuration.model()`/`.type()`, constructs the SAME flavor execution context (reused, not subclassed), and calls the SAME flavor handler bean (reused, no v2 handler). Until C7 a v2 config reaching the registry fails loud with a clear `ConnectorException` (no NPE — telemetry comes from the `LlmProviderConfiguration`, never a null provider).

### Files

- CREATE `.../model/request/OutboundConnectorAgentRequestV2.java`
- CREATE `.../model/request/JobWorkerAgentRequestV2.java`
- CREATE `.../aiagent/AiAgentTaskV2.java`
- CREATE `.../aiagent/AiAgentSubProcessV2.java`
- MODIFY `.../autoconfigure/AgenticAiConnectorsAutoConfiguration.java` (two v2 connector beans wired to the EXISTING flavor handler beans; no new handler beans)
- CREATE (test) `.../test/.../aiagent/AiAgentV2EntryPointTest.java`

### Interfaces (exact signatures)

- **Produces:** `record OutboundConnectorAgentRequestV2(@Valid @NotNull LlmProviderConfiguration configuration, @Valid @NotNull OutboundConnectorAgentRequest.OutboundConnectorAgentRequestData data)`.
- **Produces:** `record JobWorkerAgentRequestV2(List<AdHocToolElement> toolElements, AgentContext agentContext, List<ToolCallResult> toolCallResults, @Valid @NotNull LlmProviderConfiguration configuration, @Valid @NotNull JobWorkerAgentRequest.JobWorkerAgentRequestData data)`.
- **Consumes:** `LlmProviderChatModelApiConfiguration(LlmProviderConfiguration)` (Task 4); `LlmProviderConfiguration.model()`/`.type()`; the version-agnostic `OutboundConnectorAgentExecutionContext`/`JobWorkerAgentExecutionContext` constructors (Task 5); the EXISTING `OutboundConnectorAgentRequestHandler`/`JobWorkerAgentRequestHandler` beans; response types `AiAgentTaskConnectorResponse`/`AiAgentSubProcessConnectorResponse`.

**No v2 handler classes and no v2 execution-context classes.** The v2 connectors reuse the shared flavor handler and the shared flavor context (Task 5 made both version-agnostic).

### Steps

1. **Write failing test** — CREATE `AiAgentV2EntryPointTest.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.aiagent.provider.api.ChatModelApiRegistryImpl;
import io.camunda.connector.agenticai.aiagent.provider.api.LlmProviderChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.provider.langchain4j.Langchain4JAiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.provider.langchain4j.Langchain4JChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.model.OutboundConnectorAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.OutboundConnectorAgentRequest.OutboundConnectorAgentRequestData;
import io.camunda.connector.agenticai.aiagent.model.request.OutboundConnectorAgentRequestV2;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicBackend.AnthropicDirectBackend;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicModel;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.JobContext;

import java.util.List;

import org.junit.jupiter.api.Test;

class AiAgentV2EntryPointTest {

	private AnthropicChatModel anthropicConfig() {
		return new AnthropicChatModel(
			new AnthropicDirectBackend(null, "sk-ant"),
			new AnthropicModel("claude-sonnet-4-6", null),
			null,
			null);
	}

	@Test
	void v2EntryPointMappingWrapsLlmProviderConfigAndTelemetryWithoutNpe() {
		final var config = anthropicConfig();
		final var request =
			new OutboundConnectorAgentRequestV2(
				config, new OutboundConnectorAgentRequestData(null, null, null, null, null, null, null));

		// reproduces exactly what AiAgentTaskV2.execute builds
		final var ctx =
			new OutboundConnectorAgentExecutionContext(
				mock(JobContext.class),
				request.data(),
				new LlmProviderChatModelApiConfiguration(request.configuration()),
				request.configuration().model(),
				request.configuration().type(),
				mock(ProcessDefinitionAdHocToolElementsResolver.class));

		assertThat(ctx.configuration().chatModelApiConfiguration())
			.isEqualTo(new LlmProviderChatModelApiConfiguration(config));
		assertThat(ctx.configuration().modelName()).isEqualTo("claude-sonnet-4-6");
		assertThat(ctx.configuration().modelProvider()).isEqualTo("anthropic");
	}

	@Test
	void v2LlmProviderConfigFailsLoudThroughRegistryUntilProviderFactoryExists() {
		final var registry =
			new ChatModelApiRegistryImpl(
				List.of(new Langchain4JChatModelApiFactory(mock(Langchain4JAiFrameworkAdapter.class))));

		assertThatThrownBy(
			() -> registry.resolve(new LlmProviderChatModelApiConfiguration(anthropicConfig())))
			.isInstanceOf(ConnectorException.class)
			.hasMessageContaining("No chat model registered for configuration");
	}
}
```

2. **Run-fail:**

```bash
mvn -q -o test -f connectors/agentic-ai/pom.xml -pl connectors/agentic-ai/connector-agentic-ai -Dtest=AiAgentV2EntryPointTest
```
Expected: compilation failure — `OutboundConnectorAgentRequestV2` missing.

3. **Impl — `OutboundConnectorAgentRequestV2.java`:**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import io.camunda.connector.agenticai.aiagent.model.request.OutboundConnectorAgentRequest.OutboundConnectorAgentRequestData;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.LlmProviderConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** v2 AI Agent Task request: wire-format-first chat-model config + the shared v1 request data. */
public record OutboundConnectorAgentRequestV2(
    @Valid @NotNull LlmProviderConfiguration configuration,
    @Valid @NotNull OutboundConnectorAgentRequestData data) {}
```

4. **Impl — `JobWorkerAgentRequestV2.java`:**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerAgentRequest.JobWorkerAgentRequestData;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.LlmProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** v2 AI Agent Sub-process request: wire-format-first chat-model config + the shared v1 request data. */
public record JobWorkerAgentRequestV2(
    @JsonProperty("adHocSubProcessElements") List<AdHocToolElement> toolElements,
    @FEEL
        @TemplateProperty(
            label = "Agent context",
            group = "memory",
            id = "agentContext",
            description =
                "Initial agent context from previous interactions. Avoid reusing context variables across agents to prevent issues with stale data or tool access.",
            tooltip =
                "The agent context variable containing all relevant data for the agent to support the feedback loop between "
                    + "user requests, tool calls and LLM responses. Make sure this variable points to the <code>context</code> "
                    + "variable which is returned from the agent response. "
                    + "<a href=\"https://docs.camunda.io/docs/8.9/components/connectors/out-of-the-box-connectors/agentic-ai-aiagent-subprocess/\" target=\"_blank\">See documentation</a> "
                    + "for details.",
            type = TemplateProperty.PropertyType.Text,
            feel = FeelMode.required)
        @Valid
        AgentContext agentContext,
    List<ToolCallResult> toolCallResults,
    @Valid @NotNull LlmProviderConfiguration configuration,
    @Valid @NotNull JobWorkerAgentRequestData data) {}
```

5. **Impl — `AiAgentTaskV2.java`** (Task v2 connector; carries the `@ElementTemplate`). LOCKED identities: type `io.camunda.agenticai:aiagent:task:2`, element-template id `io.camunda.connectors.agenticai.ai-agent-task.v2`. Reuses the shared `OutboundConnectorAgentRequestHandler`; mirror the v1 `AiAgentFunction` groups and add a `capabilities` group:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.aiagent.agent.OutboundConnectorAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.provider.api.LlmProviderChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.OutboundConnectorAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.OutboundConnectorAgentRequestV2;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

/** AI Agent Task v2 connector (LLM-provider layer). Service-task flavor; reuses the shared handler. */
@OutboundConnector(
	name = "AI Agent Task",
	inputVariables = {"configuration", "data"},
	type = "io.camunda.agenticai:aiagent:task:2")
@ElementTemplate(
	id = "io.camunda.connectors.agenticai.ai-agent-task.v2",
	name = "AI Agent Task",
	description = "Execute a single AI-powered action with tool calling capabilities",
	keywords = {"AI", "AI Agent", "agentic orchestration"},
	documentationRef =
		"https://docs.camunda.io/docs/8.10/components/connectors/out-of-the-box-connectors/agentic-ai-aiagent-task/",
	engineVersion = "^8.10",
	version = 1,
	category = @ElementTemplate.Category(id = "aiTools", name = "AI Tools"),
	inputDataClass = OutboundConnectorAgentRequestV2.class,
	outputDataClass = AgentResponse.class,
	defaultResultVariable = "agent",
	propertyGroups = {
		@PropertyGroup(id = "provider", label = "Model provider", openByDefault = false),
		@PropertyGroup(id = "model", label = "Model", openByDefault = false),
		@PropertyGroup(id = "systemPrompt", label = "System prompt", openByDefault = false),
		@PropertyGroup(id = "userPrompt", label = "User prompt", openByDefault = false),
		@PropertyGroup(id = "tools", label = "Tools", openByDefault = false),
		@PropertyGroup(id = "memory", label = "Memory", openByDefault = false),
		@PropertyGroup(id = "limits", label = "Limits", openByDefault = false),
		@PropertyGroup(id = "response", label = "Response", openByDefault = false),
		@PropertyGroup(id = "capabilities", label = "Model capabilities", openByDefault = false)
	},
	icon = "aiagent.svg")
public class AiAgentTaskV2 implements AgentConnectorFunction {
	private final ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;
	private final OutboundConnectorAgentRequestHandler agentRequestHandler;

	public AiAgentTaskV2(
		ProcessDefinitionAdHocToolElementsResolver toolElementsResolver,
		OutboundConnectorAgentRequestHandler agentRequestHandler) {
		this.toolElementsResolver = toolElementsResolver;
		this.agentRequestHandler = agentRequestHandler;
	}

	@Override
	public AiAgentTaskConnectorResponse execute(OutboundConnectorContext context) {
		var request = context.bindVariables(OutboundConnectorAgentRequestV2.class);
		var config = request.configuration();
		var executionContext =
			new OutboundConnectorAgentExecutionContext(
				context.getJobContext(),
				request.data(),
				new LlmProviderChatModelApiConfiguration(config),
				config.model(),
				config.type(),
				toolElementsResolver);
		return agentRequestHandler.handleRequest(executionContext);
	}
}
```
   NOTE: copy the exact group tooltips from `AiAgentFunction` (v1) for `systemPrompt`/`userPrompt`/`tools`/`memory`/`response` verbatim so the generated v2 template wording matches v1 (abbreviated above for brevity; the code compiles without the tooltips — it is a fidelity note). The `capabilities` group backs the FEEL `capabilityOverride` property on the config members.

6. **Impl — `AiAgentSubProcessV2.java`** (Sub-process v2 connector; NO `@ElementTemplate` — its template is groovy-derived in Task 7; reuses the shared `JobWorkerAgentRequestHandler`):

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import io.camunda.connector.agenticai.aiagent.agent.JobWorkerAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.provider.api.LlmProviderChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerAgentRequestV2;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;

/** AI Agent Sub-process v2 connector (LLM-provider layer). Job worker on an ad-hoc sub-process. */
@OutboundConnector(
	name = AiAgentSubProcessV2.JOB_WORKER_NAME,
	type = AiAgentSubProcessV2.JOB_WORKER_TYPE,
	inputVariables = {
		AiAgentSubProcessV2.AD_HOC_SUB_PROCESS_ELEMENT_VARIABLE,
		AiAgentSubProcessV2.AGENT_CONTEXT_VARIABLE,
		AiAgentSubProcessV2.TOOL_CALL_RESULTS_VARIABLE,
		AiAgentSubProcessV2.CONFIGURATION_VARIABLE,
		AiAgentSubProcessV2.DATA_VARIABLE
	})
public class AiAgentSubProcessV2 implements AgentConnectorFunction {

	public static final String JOB_WORKER_NAME = "AI Agent Sub-process";
	public static final String JOB_WORKER_TYPE = "io.camunda.agenticai:aiagent:subprocess:2";

	public static final String AD_HOC_SUB_PROCESS_ELEMENT_VARIABLE = "adHocSubProcessElements";
	public static final String AGENT_CONTEXT_VARIABLE = "agentContext";
	public static final String TOOL_CALL_RESULTS_VARIABLE = "toolCallResults";
	public static final String CONFIGURATION_VARIABLE = "configuration";
	public static final String DATA_VARIABLE = "data";

	private final JobWorkerAgentRequestHandler agentRequestHandler;

	public AiAgentSubProcessV2(JobWorkerAgentRequestHandler agentRequestHandler) {
		this.agentRequestHandler = agentRequestHandler;
	}

	@Override
	public AiAgentSubProcessConnectorResponse execute(OutboundConnectorContext context)
		throws Exception {
		var request = context.bindVariables(JobWorkerAgentRequestV2.class);
		var config = request.configuration();
		var executionContext =
			new JobWorkerAgentExecutionContext(
				context.getJobContext(),
				request.data(),
				request.agentContext(),
				request.toolCallResults(),
				request.toolElements(),
				new LlmProviderChatModelApiConfiguration(config),
				config.model(),
				config.type());
		return agentRequestHandler.handleRequest(executionContext);
	}
}
```

7. **Impl — auto-config beans.** In `AgenticAiConnectorsAutoConfiguration.java`, add TWO connector beans (no new handler beans — they inject the EXISTING flavor handler beans). Add imports `io.camunda.connector.agenticai.aiagent.AiAgentTaskV2` and `AiAgentSubProcessV2`. Insert after the v1 job-worker bean:

```java
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBooleanProperty(
      value = "camunda.connector.agenticai.aiagent.task-v2.enabled",
      matchIfMissing = true)
  public AiAgentTaskV2 aiAgentTaskV2(
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver,
      OutboundConnectorAgentRequestHandler agentRequestHandler) {
    return new AiAgentTaskV2(toolElementsResolver, agentRequestHandler);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBooleanProperty(
      value = "camunda.connector.agenticai.aiagent.subprocess-v2.enabled",
      matchIfMissing = true)
  public AiAgentSubProcessV2 aiAgentSubProcessV2(
      JobWorkerAgentRequestHandler agentRequestHandler) {
    return new AiAgentSubProcessV2(agentRequestHandler);
  }
```
   NOTE: each v2 connector bean depends on the corresponding flavor handler bean (`OutboundConnectorAgentRequestHandler` / `JobWorkerAgentRequestHandler`), which are default-on. If an operator disables the v1 flavor toggle (`...outbound-connector.enabled` / `...job-worker.enabled`) the handler bean is absent and the matching v2 bean cannot be created — document this coupling in `ai-agent.md` §16 (Task 7). Do not add new handler beans.

8. **Run-pass (v2 entry-point + auto-config):**

```bash
mvn -q -o test -f connectors/agentic-ai/pom.xml -pl connectors/agentic-ai/connector-agentic-ai -Dtest=AiAgentV2EntryPointTest,AgenticAiConnectorsAutoConfigurationTest
```
Expected: `BUILD SUCCESS`. (If `AgenticAiConnectorsAutoConfigurationTest` enumerates expected beans, add `aiAgentTaskV2`/`aiAgentSubProcessV2`.)

9. **Full module build + e2e green:**

```bash
mvn -q -o clean install -f connectors/agentic-ai/pom.xml
mvn -q -o test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -am -Dtest=BaseAiAgentJobWorkerTest
```
Expected: `BUILD SUCCESS` both. The module build regenerates templates; if Task 7's pom generator entry is not yet added the v2 task template simply isn't emitted — the build still succeeds. Verify `git status` shows only source additions, no v1 JSON changes.

10. **Commit:**

```bash
mvn -q spotless:apply license:format -f connectors/agentic-ai/pom.xml
git add -A && git commit -m "Add AiAgentTaskV2/AiAgentSubProcessV2 connectors and v2 request records as new entry points reusing the shared handlers"
```

---

## Task 7 — v2 sub-process groovy transform + generated/committed v2 templates + README + docs

**Goal:** register v2 template generation (task via the maven plugin, sub-process via a groovy transform derived from the v2 task template), COMMIT the generated JSON, and update the element-templates README and module docs. Never hand-edit generated JSON.

### Files

- MODIFY `connectors/agentic-ai/connector-agentic-ai/pom.xml` (add v2 task connector to `<connectors>`; add a `gmavenplus` execution for the v2 sub-process transform; parameterize the existing v1 transform to keep it byte-identical)
- CREATE `connectors/agentic-ai/connector-agentic-ai/bin/transform-ai-agent-subprocess-v2-template.groovy` (or parameterize the existing script — see note)
- GENERATED (commit) `connectors/agentic-ai/connector-agentic-ai/element-templates/agenticai-ai-agent-task.v2.json`
- GENERATED (commit) `connectors/agentic-ai/connector-agentic-ai/element-templates/agenticai-ai-agent-subprocess.v2.json`
- MODIFY `connectors/agentic-ai/connector-agentic-ai/element-templates/README.md` (both Task + Sub-process tables)
- MODIFY `connectors/agentic-ai/AGENTS.md` (key entry points + element-templates note) and `docs/reference/ai-agent.md` (§12/§16/§25 as relevant)
- CREATE (test) `.../test/.../aiagent/AiAgentV2TemplateGenerationTest.java`

### Steps

1. **Write failing test** — CREATE `AiAgentV2TemplateGenerationTest.java` asserting the generated v2 templates exist and contain the discriminator dropdowns + FEEL override property. This test reads the JSON from the classpath/relative path produced by the build:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AiAgentV2TemplateGenerationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path TEMPLATES = Path.of("element-templates");

  @Test
  void taskV2TemplateExistsWithProviderDiscriminatorAndCapabilityOverride() throws Exception {
    final Path file = TEMPLATES.resolve("agenticai-ai-agent-task.v2.json");
    assertThat(Files.exists(file)).isTrue();
    final JsonNode json = MAPPER.readTree(Files.readString(file));

    assertThat(json.get("id").asText()).isEqualTo("io.camunda.connectors.agenticai.ai-agent-task.v2");

    final var propsText = json.get("properties").toString();
    // provider discriminator dropdown with anthropic + openai
    assertThat(propsText).contains("anthropic").contains("openai");
    // backend + auth discriminators
    assertThat(propsText).contains("bedrock").contains("compatible");
    // capability override FEEL property is present
    assertThat(propsText).contains("Model capability overrides");
  }

  @Test
  void subprocessV2TemplateExistsWithSubprocessTypeAndId() throws Exception {
    final Path file = TEMPLATES.resolve("agenticai-ai-agent-subprocess.v2.json");
    assertThat(Files.exists(file)).isTrue();
    final JsonNode json = MAPPER.readTree(Files.readString(file));

    assertThat(json.get("id").asText())
        .isEqualTo("io.camunda.connectors.agenticai.ai-agent-subprocess.v2");
    assertThat(json.at("/appliesTo/0").asText()).isEqualTo("bpmn:SubProcess");
    assertThat(json.toString()).contains("io.camunda.agenticai:aiagent:subprocess:2");
  }
}
```

2. **Run-fail:**

```bash
mvn -q -o test -f connectors/agentic-ai/pom.xml -pl connectors/agentic-ai/connector-agentic-ai -Dtest=AiAgentV2TemplateGenerationTest
```
Expected: assertion failure — `agenticai-ai-agent-task.v2.json` does not exist yet.

3. **Impl — pom `<connectors>` addition.** In `element-template-generator-maven-plugin`'s `<connectors>` block, add a new `<connector>` for the v2 task connector:

```xml
            <connector>
              <connectorClass>io.camunda.connector.agenticai.aiagent.AiAgentTaskV2</connectorClass>
              <files>
                <file>
                  <templateId>io.camunda.connectors.agenticai.ai-agent-task.v2</templateId>
                  <templateFileName>agenticai-ai-agent-task.v2.json</templateFileName>
                </file>
              </files>
              <generateHybridTemplates>true</generateHybridTemplates>
              <writeMetaInfFileGeneration>false</writeMetaInfFileGeneration>
            </connector>
```

4. **Impl — parameterize the existing v1 groovy transform (byte-identical v1) OR add a v2 script.** Recommended: create a dedicated v2 script to avoid any risk to v1 generation. CREATE `bin/transform-ai-agent-subprocess-v2-template.groovy` — a copy of `transform-ai-agent-job-worker-template.groovy` with only the metadata + type strings changed:

   - `json.id = "io.camunda.connectors.agenticai.ai-agent-subprocess.v2"`
   - `json.name = "AI Agent Sub-process"`
   - the `zeebe:taskDefinition`/`type` property value → `"io.camunda.agenticai:aiagent:subprocess:2"`
   - the `property.id == "id"` branch value → `"io.camunda.connectors.agenticai.ai-agent-subprocess.v2"`
   - keep ALL other structural transforms identical (appliesTo `bpmn:SubProcess`, `elementType.value` `bpmn:AdHocSubProcess`, the `outputCollection`/`outputElement` hidden props, the events group insertion after `limits`, the `includeAgentContext` insertion, the `data.agentContext` rebinding, the trailing hidden `agent` input mapping, the documentation-link replacement).

   Then add a `gmavenplus` execution in the pom (after the v1 `generate-job-worker-template` execution):

```xml
          <execution>
            <id>generate-subprocess-v2-template</id>
            <phase>process-classes</phase>
            <goals>
              <goal>execute</goal>
            </goals>
            <configuration>
              <scripts>
                <script>file:///${project.basedir}/bin/transform-ai-agent-subprocess-v2-template.groovy</script>
              </scripts>
              <properties>
                <property>
                  <name>sourceFile</name>
                  <value>${project.basedir}/element-templates/agenticai-ai-agent-task.v2.json</value>
                </property>
                <property>
                  <name>outputFile</name>
                  <value>${project.basedir}/element-templates/agenticai-ai-agent-subprocess.v2.json</value>
                </property>
              </properties>
            </configuration>
          </execution>
```
   (Also add a `generate-subprocess-v2-template-hybrid` execution mirroring the v1 hybrid execution if `generateHybridTemplates` is true for the v2 task connector — the v1 script auto-appends `-hybrid` when the source id contains `-hybrid`; the v2 script must do the same. Keep hybrid handling identical to v1.)

5. **Generate + commit the JSON:**

```bash
mvn -q -o clean compile -f connectors/agentic-ai/pom.xml
git status --short connectors/agentic-ai/connector-agentic-ai/element-templates
```
Expected: two NEW files `agenticai-ai-agent-task.v2.json`, `agenticai-ai-agent-subprocess.v2.json` (plus hybrid variants if enabled). **No changes to v1 JSON** (`agenticai-aiagent-outbound-connector.json` / `agenticai-aiagent-job-worker.json` must be untouched — verify with `git status`).

6. **Run-pass:**

```bash
mvn -q -o test -f connectors/agentic-ai/pom.xml -pl connectors/agentic-ai/connector-agentic-ai -Dtest=AiAgentV2TemplateGenerationTest
```
Expected: `BUILD SUCCESS`, both template assertions green.

7. **Docs.**
   - `element-templates/README.md`: per the maintenance rules, add v2 to BOTH the AI Agent Task table and the AI Agent Sub-process table. Check the generated `engines.camunda` field (`^8.10`): if it equals the current top row's minimum, bump per rule 2; if higher, insert a new top row per rule 3. Since v2 is a NEW connector type (task/subprocess split) rather than a version bump of v1, add it as new rows/links pointing at `./agenticai-ai-agent-task.v2.json` and `./agenticai-ai-agent-subprocess.v2.json`. Do not list hybrid templates.
   - `connectors/agentic-ai/AGENTS.md`: add `AiAgentTaskV2` / `AiAgentSubProcessV2` to the "Key entry points" table and note the v2 sub-process transform script under "Element templates".
   - `docs/reference/ai-agent.md`: §12 (framework abstraction) note the new `LlmProviderChatModelApiConfiguration` + `LlmProviderConfiguration` config surface and that LLM-provider factories arrive later (C7+), with the fail-loud-until-then behavior; §16 (auto-config) list the four new v2 beans + their toggles (`...aiagent.task-v2.enabled`, `...aiagent.subprocess-v2.enabled`); §25 (extension points) note the wire-format-first config package.

8. **Full module build + e2e green:**

```bash
mvn -q -o clean install -f connectors/agentic-ai/pom.xml
mvn -q -o test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -am -Dtest=BaseAiAgentJobWorkerTest
```
Expected: `BUILD SUCCESS` both.

9. **Commit:**

```bash
mvn -q spotless:apply license:format -f connectors/agentic-ai/pom.xml
git add -A && git commit -m "Generate and commit v2 element templates (task + sub-process), wire generation, update README and docs"
```

---

## Writing-plans self-review

### Spec coverage

| Ratified requirement | Task |
|---|---|
| New `chatmodel` config package | 2, 3 |
| Backend + conditional auth = per-member sealed sub-types (Anthropic direct/bedrock; OpenAI direct/compatible; compatible auth none/apiKey) | 2, 3 |
| `LlmProviderConfiguration` sealed + `@JsonTypeInfo`/`@JsonSubTypes`/`@TemplateDiscriminatorProperty`; accessors `type()`/`model()`/`@Nullable backend()`/`Optional<ModelCapabilitiesOverride> capabilityOverride()`; apiFamily only on OpenAI | 2, 3 |
| Reuse `TimeoutConfiguration`/`HttpUrl`; fresh chatmodel-local AWS auth (justified) | 2 |
| Full template annotations now (#7224 target shape) | 2, 3 |
| Finish deferred sparse-override resolver path; public `ModelCapabilitiesOverride` in capabilities package (config→framework); resolver param change + deep-merge highest-precedence + sparse-wins/inherit tests; field-name projection documented | 1 |
| `record LlmProviderChatModelApiConfiguration(LlmProviderConfiguration)` single-arg LOCKED | 4 |
| v2 connectors + v2 request records + entry-point mapping to `LlmProviderChatModelApiConfiguration`; LOCKED ids/types | 5 (boundary plumbing), 6 (connectors) |
| Fail-loud (clear `ConnectorException`, not NPE) until C7; no LLM-provider factory registered | 4 (registry), 6 (v2 path / no-NPE telemetry) |
| Sub-process groovy transform v2 + generation-validated committed JSON + README + docs | 7 |
| v1 byte-identical; e2e green each task; module builds green; regenerate+commit templates; @NullMarked; provider-style records; secret redaction | all tasks (checks embedded) |

### Placeholder scan

No "TBD"/"add appropriate X"/"similar to Task N" remain. Every new record/enum/sealed type has complete source. The Task 5 and Task 6 tests construct the shared `*RequestData` records with all-null components (records need no non-null args for these assertions), so there is no prompt-config placeholder. Task 5 step 11 gives a mechanical before→after transform (plus a worked example) for the ~13 positional `new AgentConfiguration(...)` test updates and instructs running the build to surface any missed — this is a bounded, enumerated edit, not an open placeholder. One deliberate instruction to copy verbatim the `systemPrompt`/`userPrompt`/`tools`/`memory`/`response` group tooltips from `AiAgentFunction` into `AiAgentTaskV2` (to keep the generated template wording consistent) — the code compiles without it; it is a fidelity note.

### Type consistency

- `ModelCapabilitiesResolver.resolve(...)` override param is `Optional<ModelCapabilitiesOverride>` in the interface (Task 1 step 4), the impl (step 5), and all callers (existing tests pass `Optional.empty()`, new tests pass `Optional.of(override)`). The removed `merge(...)` helper is fully inlined; `deepMerge`/`findBest`/`conservativeBase`/`mapper` retained.
- `LlmProviderConfiguration` accessors (`type()`, `model()`, `backend()`, `capabilityOverride()`) implemented by both members; `apiFamily`/`apiFamilyKey()` only on `OpenAiChatModel`. Discriminator strings: provider `anthropic`/`openai`; Anthropic backend `direct`/`bedrock`; OpenAI backend `direct`/`compatible`; compatible auth `none`/`apiKey`; AWS auth `credentials`/`apiKey`/`defaultCredentialsChain`. apiFamily keys `openai-completions`/`openai-responses` match the bundled `model-capabilities.yaml` families (verified).
- `LlmProviderChatModelApiConfiguration(LlmProviderConfiguration configuration)` — single component; capability override reached via `configuration.capabilityOverride()`; no second component. The v2 entry points build it from `request.configuration()`; the v1 entry points build `ProviderChatModelApiConfiguration(request.provider())` instead. Both flow through the identical `AgentConfiguration.chatModelApiConfiguration` field and the one-liner `chatModelApiRegistry.resolve(agentConfiguration.chatModelApiConfiguration())` in `BaseAgentRequestHandler`.
- `AgentConfiguration`'s first component is `ChatModelApiConfiguration chatModelApiConfiguration` (not the concrete provider), plus plain `String modelName`/`modelProvider` for telemetry. It is transient (never persisted), so this shape change is BC-safe. `CamundaAgentInstanceClient` reads `configuration.modelName()`/`.modelProvider()`; the bridge adapter unwraps `((ProviderChatModelApiConfiguration) configuration.chatModelApiConfiguration()).providerConfiguration()` (safe — the bridge factory only routes that impl). No null legacy provider exists on the v2 path, so the fail-loud registry error is reached without any NPE.
- ONE shared handler and ONE shared execution context per flavor serve both v1 and v2 — no v2 handler subclasses, no v2 execution-context subclasses. The version-agnostic context constructors receive the built `ChatModelApiConfiguration` + `modelName`/`modelProvider` + the shared `*RequestData` (reused verbatim by the v2 request records). The v2 request top-level field is named `configuration` (`LlmProviderConfiguration`), surfaced as the `configuration` input variable; v1's stays `provider`.
- Known layering tradeoff (accepted, flagged for #7537): `AgentConfiguration` (package `aiagent.model`) references `ChatModelApiConfiguration` (package `aiagent.framework.api`), which already references `model` via `ChatModelRequest` — a mutual package reference that compiles, is unenforced today, and touches only the neutral SPI (no vendor SDK).
