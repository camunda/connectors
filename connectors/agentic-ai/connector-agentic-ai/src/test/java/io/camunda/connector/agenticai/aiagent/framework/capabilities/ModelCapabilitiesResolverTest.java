/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.capabilities;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.anthropic.AnthropicModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.anthropic.AnthropicModelCapabilitiesData;
import io.camunda.connector.agenticai.aiagent.framework.anthropic.AnthropicModelCapabilitiesData.InputModalities;
import io.camunda.connector.agenticai.aiagent.framework.anthropic.AnthropicModelCapabilitiesData.OutputModalities;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.CapabilityMatrix.ApiFamily;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.CapabilityMatrix.ModelEntry;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ModelCapabilitiesResolverTest {

  private final ObjectMapper mapper = new ObjectMapper();

  // --- Builder helpers -----------------------------------------------------

  /** A fully-populated capability block usable as an api-family default. */
  private static AnthropicModelCapabilitiesData fullDefaults(
      List<Modality> userMessage, List<Modality> toolResult) {
    return new AnthropicModelCapabilitiesData(
        new InputModalities(userMessage, toolResult),
        new OutputModalities(List.of(Modality.TEXT)),
        false,
        false,
        true,
        true,
        200000,
        8192);
  }

  /**
   * A distinctive, non-conservative family-defaults block for the override-merge tests: every field
   * differs from both {@link #fullDefaults} and the conservative fallback so an inheritance
   * assertion actually proves the value came from this base rather than from some other layer.
   */
  private static AnthropicModelCapabilitiesData richDefaults() {
    return new AnthropicModelCapabilitiesData(
        new InputModalities(
            List.of(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT), List.of(Modality.TEXT)),
        new OutputModalities(List.of(Modality.TEXT, Modality.IMAGE)),
        true,
        true,
        true,
        true,
        500000,
        64000);
  }

  private JsonNode node(AnthropicModelCapabilitiesData yaml) {
    return mapper.valueToTree(yaml);
  }

  private ModelCapabilitiesResolver resolverFor(Map<String, ApiFamily> families) {
    return new ModelCapabilitiesResolverImpl(new CapabilityMatrix(families), mapper);
  }

  private AnthropicModelCapabilities resolveA(
      ModelCapabilitiesResolver resolver,
      String family,
      String model,
      @Nullable String backend,
      Optional<ModelCapabilitiesOverride> override) {
    return resolver.resolve(family, model, backend, override, AnthropicModelCapabilitiesData.class);
  }

  // --- Tests -----------------------------------------------------------

  @Test
  void overrideDeepMergesOnTopOfResolvedBaseWithSparseFieldsWinning() {
    // No entry matches "claude-opus-4-7" here, so the resolved base is exactly the family
    // defaults below: supportsReasoning=true, toolResult=[text] (plus the other distinctive,
    // non-conservative values from richDefaults()). The sparse override flips supportsReasoning
    // off and widens toolResult, leaving every other field to inherit from that resolved base.
    final var defaults = node(richDefaults());
    final var resolver =
        resolverFor(Map.of("anthropic-messages", new ApiFamily(defaults, List.of())));

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

    final var base =
        resolveA(resolver, "anthropic-messages", "claude-opus-4-7", null, Optional.empty());
    final var merged =
        resolveA(resolver, "anthropic-messages", "claude-opus-4-7", null, Optional.of(override));

    // sanity-check the base actually is the distinctive, non-conservative family defaults
    assertThat(base.supportsReasoning()).isTrue();
    assertThat(base.toolResultModalities()).containsExactly(Modality.TEXT);

    // overridden fields win
    assertThat(merged.toolResultModalities()).containsExactly(Modality.TEXT, Modality.IMAGE);
    assertThat(merged.supportsReasoning()).isFalse();
    // untouched fields inherit from the resolved base verbatim
    assertThat(merged.userMessageModalities()).isEqualTo(base.userMessageModalities());
    assertThat(merged.assistantMessageModalities()).isEqualTo(base.assistantMessageModalities());
    assertThat(merged.supportsPromptCaching()).isEqualTo(base.supportsPromptCaching());
    assertThat(merged.core().contextWindow()).isEqualTo(base.core().contextWindow());
    assertThat(merged.core().maxOutputTokens()).isEqualTo(base.core().maxOutputTokens());
  }

  @Test
  void overrideScalarFieldsWinAndOthersInherit() {
    // Same distinctive, non-conservative family defaults as above (no entry matches the model
    // id, so the resolved base is exactly these defaults) - used here to prove that untouched
    // fields inherit the actual base values rather than merely happening to match a fallback.
    final var defaults = node(richDefaults());
    final var resolver =
        resolverFor(Map.of("anthropic-messages", new ApiFamily(defaults, List.of())));
    final var base =
        resolveA(resolver, "anthropic-messages", "claude-opus-4-7", null, Optional.empty());

    final var override =
        new ModelCapabilitiesOverride(null, null, null, null, null, null, null, 4242, 777);
    final var merged =
        resolveA(resolver, "anthropic-messages", "claude-opus-4-7", null, Optional.of(override));

    assertThat(merged.core().contextWindow()).isEqualTo(4242);
    assertThat(merged.core().maxOutputTokens()).isEqualTo(777);
    assertThat(base.supportsReasoning()).isTrue();
    assertThat(merged.supportsReasoning()).isEqualTo(base.supportsReasoning());
    assertThat(merged.userMessageModalities()).isEqualTo(base.userMessageModalities());
  }

  @Test
  void overrideAppliesOverConservativeDefaultsWhenFamilyUnknown() {
    final var resolver = resolverFor(Map.of());
    final var override =
        new ModelCapabilitiesOverride(
            List.of(Modality.TEXT, Modality.IMAGE), null, null, true, null, null, null, null, null);

    final var merged =
        resolveA(resolver, "does-not-exist", "whatever", null, Optional.of(override));

    // conservative base is text-only, everything false; override widens userMessage + reasoning
    assertThat(merged.userMessageModalities()).containsExactly(Modality.TEXT, Modality.IMAGE);
    assertThat(merged.supportsReasoning()).isTrue();
    assertThat(merged.toolResultModalities()).containsExactly(Modality.TEXT);
    assertThat(merged.supportsPromptCaching()).isFalse();
  }

  @Test
  void unknownApiFamilyFallsThroughToConservativeDefaults() {
    final var resolver = resolverFor(Map.of());

    final var caps =
        resolveA(resolver, "does-not-exist", "claude-opus-4-7", null, Optional.empty());

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
    assertThat(caps.userMessageModalities()).containsExactly(Modality.TEXT);
    assertThat(caps.toolResultModalities()).containsExactly(Modality.TEXT);
    assertThat(caps.assistantMessageModalities()).containsExactly(Modality.TEXT);
    assertThat(caps.supportsReasoning()).isFalse();
    assertThat(caps.core().contextWindow()).isNull();
    assertThat(caps.core().maxOutputTokens()).isNull();
  }

  @Test
  void unknownModelInKnownFamilyFallsThroughToFamilyDefaultsNotConservativeDefaults() {
    final var defaults =
        node(fullDefaults(List.of(Modality.TEXT, Modality.IMAGE), List.of(Modality.TEXT)));
    final var entry =
        new ModelEntry("claude-opus-4-7", List.of(), List.of(), null, mapper.createObjectNode());
    final var resolver =
        resolverFor(Map.of("anthropic-messages", new ApiFamily(defaults, List.of(entry))));

    final var caps =
        resolveA(resolver, "anthropic-messages", "claude-mystery", null, Optional.empty());

    // The family declares its own (non-conservative) defaults, so an unmatched model gets those,
    // not the fully conservative baseline.
    assertThat(caps.core().contextWindow()).isNotNull();
    assertThat(caps.userMessageModalities()).containsExactly(Modality.TEXT, Modality.IMAGE);
    assertThat(caps.toolResultModalities()).containsExactly(Modality.TEXT);
    assertThat(caps.supportsPromptCaching()).isTrue();
    assertThat(caps.supportsParallelToolCalls()).isTrue();
    assertThat(caps.core().contextWindow()).isEqualTo(200000);
    assertThat(caps.core().maxOutputTokens()).isEqualTo(8192);
  }

  @Test
  void multiplePatternsOnOneEntryBothResolve() {
    final var defaults = node(fullDefaults(List.of(Modality.TEXT), List.of(Modality.TEXT)));
    final var overlay =
        node(new AnthropicModelCapabilitiesData(null, null, true, null, null, null, null, null));
    final var entry =
        new ModelEntry(
            null, List.of(), List.of("claude-opus-4-6*", "claude-opus-4-7*"), null, overlay);
    final var resolver =
        resolverFor(Map.of("anthropic-messages", new ApiFamily(defaults, List.of(entry))));

    final var byFirstPattern =
        resolveA(resolver, "anthropic-messages", "claude-opus-4-6", null, Optional.empty());
    final var bySecondPattern =
        resolveA(resolver, "anthropic-messages", "claude-opus-4-7", null, Optional.empty());

    assertThat(byFirstPattern.supportsReasoning()).isTrue();
    assertThat(bySecondPattern.supportsReasoning()).isTrue();
    assertThat(byFirstPattern).isEqualTo(bySecondPattern);
  }

  @Test
  void exactIdMatchInheritsFamilyDefaultsAndAppliesEntryOverlay() {
    final var defaults =
        node(
            fullDefaults(
                List.of(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT),
                List.of(Modality.TEXT, Modality.IMAGE)));
    final var overlay =
        node(new AnthropicModelCapabilitiesData(null, null, true, true, null, null, null, 32000));
    final var entry =
        new ModelEntry("claude-opus-4-7", List.of("claude-opus-latest"), List.of(), null, overlay);

    final var resolver =
        resolverFor(Map.of("anthropic-messages", new ApiFamily(defaults, List.of(entry))));

    final var caps =
        resolveA(resolver, "anthropic-messages", "claude-opus-4-7", null, Optional.empty());

    assertThat(caps.userMessageModalities())
        .containsExactly(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT);
    assertThat(caps.toolResultModalities()).containsExactly(Modality.TEXT, Modality.IMAGE);
    assertThat(caps.supportsReasoning()).isTrue();
    assertThat(caps.supportsReasoningSignatureRoundtrip()).isTrue();
    assertThat(caps.supportsPromptCaching()).isTrue();
    assertThat(caps.core().contextWindow()).isEqualTo(200000);
    assertThat(caps.core().maxOutputTokens()).isEqualTo(32000);
  }

  @Test
  void aliasMatchResolvesToSameEntryAsExactId() {
    final var defaults = node(fullDefaults(List.of(Modality.TEXT), List.of(Modality.TEXT)));
    final var overlay =
        node(new AnthropicModelCapabilitiesData(null, null, null, null, null, null, null, 32000));
    final var entry =
        new ModelEntry("claude-opus-4-7", List.of("claude-opus-latest"), List.of(), null, overlay);
    final var resolver =
        resolverFor(Map.of("anthropic-messages", new ApiFamily(defaults, List.of(entry))));

    final var byAlias =
        resolveA(resolver, "anthropic-messages", "claude-opus-latest", null, Optional.empty());
    final var byId =
        resolveA(resolver, "anthropic-messages", "claude-opus-4-7", null, Optional.empty());

    assertThat(byAlias).isEqualTo(byId);
  }

  @Test
  void longestGlobPatternWinsAcrossEntries() {
    // claude-opus-* (12 chars) beats claude-* (8 chars).
    final var defaults = node(fullDefaults(List.of(Modality.TEXT), List.of(Modality.TEXT)));
    final var models = new LinkedHashMap<String, ModelEntry>();
    models.put(
        "claude-fallback",
        new ModelEntry(
            null,
            List.of(),
            List.of("claude-*"),
            null,
            node(
                new AnthropicModelCapabilitiesData(
                    null, null, false, null, null, null, null, null))));
    models.put(
        "claude-opus",
        new ModelEntry(
            null,
            List.of(),
            List.of("claude-opus-*"),
            null,
            node(
                new AnthropicModelCapabilitiesData(
                    null, null, true, null, null, null, null, null))));

    final var resolver =
        resolverFor(
            Map.of("anthropic-messages", new ApiFamily(defaults, List.copyOf(models.values()))));

    final var caps =
        resolveA(resolver, "anthropic-messages", "claude-opus-3-5", null, Optional.empty());

    assertThat(caps.supportsReasoning()).isTrue();
  }

  @Test
  void deepMergeKeepsInheritedSiblingKeyWhenOverlayTouchesOnlyOneField() {
    // Defaults provide tool_result=[text,image]; the entry overlay only touches user_message.
    final var defaults =
        node(
            fullDefaults(
                List.of(Modality.TEXT, Modality.IMAGE), List.of(Modality.TEXT, Modality.IMAGE)));
    final var overlay =
        node(
            new AnthropicModelCapabilitiesData(
                new InputModalities(List.of(Modality.TEXT), null),
                null,
                null,
                null,
                null,
                null,
                null,
                null));
    final var entry = new ModelEntry(null, List.of(), List.of("claude-haiku-*"), null, overlay);

    final var resolver =
        resolverFor(Map.of("anthropic-messages", new ApiFamily(defaults, List.of(entry))));

    final var caps =
        resolveA(resolver, "anthropic-messages", "claude-haiku-4-5", null, Optional.empty());

    assertThat(caps.userMessageModalities()).containsExactly(Modality.TEXT);
    assertThat(caps.toolResultModalities()).containsExactly(Modality.TEXT, Modality.IMAGE);
  }

  @Test
  void globMatcherTreatsStarAsWildcard() {
    assertThat(ModelCapabilitiesResolverImpl.matchesGlob("claude-opus-*", "claude-opus-4-7"))
        .isTrue();
    assertThat(ModelCapabilitiesResolverImpl.matchesGlob("claude-opus-*", "claude-sonnet-4-7"))
        .isFalse();
    assertThat(ModelCapabilitiesResolverImpl.matchesGlob("gpt-*", "gpt-4o-mini")).isTrue();
    assertThat(ModelCapabilitiesResolverImpl.matchesGlob("gpt-4.1*", "gpt-4.1-nano")).isTrue();
    assertThat(ModelCapabilitiesResolverImpl.matchesGlob("gpt-4.1*", "gpt-4o")).isFalse();
  }

  /**
   * Guards the {@code @JsonProperty} lowercase annotations on {@link Modality}: this builds the
   * capability overlay from a raw {@link JsonNode} containing literal lowercase modality strings
   * (as the bundled YAML property source produces), not by serialising a {@link
   * AnthropicModelCapabilitiesData} instance through the same mapper. Dropping the annotations
   * would break this specific round-trip even though a valueToTree/treeToValue round-trip through
   * the same mapper instance would stay self-consistent either way.
   */
  @Test
  void resolvesLowercaseYamlModalityStringsFromRawJsonNode() throws Exception {
    final JsonNode overlay =
        mapper.readTree("{\"input_modalities\": {\"user_message\": [\"text\", \"image\"]}}");
    final var defaults = node(fullDefaults(List.of(Modality.TEXT), List.of(Modality.TEXT)));
    final var entry = new ModelEntry("claude-x", List.of(), List.of(), null, overlay);

    final var resolver =
        resolverFor(Map.of("anthropic-messages", new ApiFamily(defaults, List.of(entry))));

    final var caps = resolveA(resolver, "anthropic-messages", "claude-x", null, Optional.empty());

    assertThat(caps.userMessageModalities()).containsExactly(Modality.TEXT, Modality.IMAGE);
  }

  // --- Backend dimension tests ------------------------------------------

  @Test
  void backendNullResolvesBackendAgnosticEntryAsBefore() {
    final var defaults = node(fullDefaults(List.of(Modality.TEXT), List.of(Modality.TEXT)));
    final var overlay =
        node(new AnthropicModelCapabilitiesData(null, null, true, null, null, null, null, null));
    final var entry = new ModelEntry("claude-opus-4-7", List.of(), List.of(), null, overlay);
    final var resolver =
        resolverFor(Map.of("anthropic-messages", new ApiFamily(defaults, List.of(entry))));

    final var caps =
        resolveA(resolver, "anthropic-messages", "claude-opus-4-7", null, Optional.empty());

    assertThat(caps.supportsReasoning()).isTrue();
  }

  @Test
  void backendSpecificEntryLayersOverBackendAgnosticEntry() {
    // Reviewer's example: gpt-5* on Azure Foundry has a smaller context window than the direct
    // API, everything else (reasoning, max-output-tokens) is inherited from the agnostic entry.
    final var defaults = node(fullDefaults(List.of(Modality.TEXT), List.of(Modality.TEXT)));
    final var gpt5 =
        new ModelEntry(
            null,
            List.of(),
            List.of("gpt-5*"),
            null,
            node(
                new AnthropicModelCapabilitiesData(
                    null, null, true, null, null, null, 1050000, 128000)));
    final var gpt5Foundry =
        new ModelEntry(
            null,
            List.of(),
            List.of("gpt-5*"),
            "azure-foundry",
            node(
                new AnthropicModelCapabilitiesData(
                    null, null, null, null, null, null, 200000, null)));
    final var resolver =
        resolverFor(
            Map.of("openai-responses", new ApiFamily(defaults, List.of(gpt5, gpt5Foundry))));

    final var onFoundry =
        resolveA(resolver, "openai-responses", "gpt-5.5", "azure-foundry", Optional.empty());

    // Overridden by the backend-specific entry:
    assertThat(onFoundry.core().contextWindow()).isEqualTo(200000);
    // Carried through from the backend-agnostic entry (untouched by the foundry overlay):
    assertThat(onFoundry.supportsReasoning()).isTrue();
    assertThat(onFoundry.core().maxOutputTokens()).isEqualTo(128000);
    // Carried through from family defaults (untouched by either entry):
    assertThat(onFoundry.supportsPromptCaching()).isTrue();
    assertThat(onFoundry.supportsParallelToolCalls()).isTrue();

    final var direct =
        resolveA(resolver, "openai-responses", "gpt-5.5", "direct", Optional.empty());
    final var noBackend = resolveA(resolver, "openai-responses", "gpt-5.5", null, Optional.empty());

    // A different (or absent) backend never sees the foundry entry: both resolve to the plain
    // gpt-5 values.
    assertThat(direct.core().contextWindow()).isEqualTo(1050000);
    assertThat(direct).isEqualTo(noBackend);
  }

  @Test
  void backendWithNoSpecificEntryFallsBackToAgnosticEntry() {
    final var defaults = node(fullDefaults(List.of(Modality.TEXT), List.of(Modality.TEXT)));
    final var overlay =
        node(
            new AnthropicModelCapabilitiesData(
                null, null, true, null, null, null, 1050000, 128000));
    final var entry = new ModelEntry(null, List.of(), List.of("gpt-5*"), null, overlay);
    final var resolver =
        resolverFor(Map.of("openai-responses", new ApiFamily(defaults, List.of(entry))));

    final var caps = resolveA(resolver, "openai-responses", "gpt-5.5", "bedrock", Optional.empty());

    assertThat(caps.supportsReasoning()).isTrue();
    assertThat(caps.core().contextWindow()).isEqualTo(1050000);
    assertThat(caps.core().maxOutputTokens()).isEqualTo(128000);
  }

  @Test
  void backendSpecificOnlyEntryLayersOnFamilyDefaultsWithNoAgnosticMatch() {
    final var defaults = node(fullDefaults(List.of(Modality.TEXT), List.of(Modality.TEXT)));
    final var overlay =
        node(new AnthropicModelCapabilitiesData(null, null, null, null, null, null, 100000, null));
    final var bedrockOnly =
        new ModelEntry(null, List.of(), List.of("claude-*"), "bedrock", overlay);
    final var resolver =
        resolverFor(Map.of("anthropic-messages", new ApiFamily(defaults, List.of(bedrockOnly))));

    final var caps =
        resolveA(resolver, "anthropic-messages", "claude-mystery", "bedrock", Optional.empty());

    // No backend-agnostic candidate exists at all; the backend-specific entry still layers on
    // top of the family defaults.
    assertThat(caps.core().contextWindow()).isEqualTo(100000);
    assertThat(caps.supportsPromptCaching()).isTrue();
    assertThat(caps.supportsParallelToolCalls()).isTrue();

    // Without a backend, there's no agnostic candidate either, so it falls through to plain
    // family defaults.
    final var withoutBackend =
        resolveA(resolver, "anthropic-messages", "claude-mystery", null, Optional.empty());
    assertThat(withoutBackend.core().contextWindow()).isEqualTo(200000);
  }
}
