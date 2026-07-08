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
import io.camunda.connector.agenticai.aiagent.framework.capabilities.CapabilityMatrix.ApiFamily;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.CapabilityMatrix.ModelEntry;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilitiesData.InputModalities;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilitiesData.OutputModalities;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModelCapabilitiesResolverTest {

  private final ObjectMapper mapper = new ObjectMapper();

  // --- Builder helpers -----------------------------------------------------

  /** A fully-populated capability block usable as an api-family default. */
  private static ModelCapabilitiesData fullDefaults(
      List<Modality> userMessage, List<Modality> toolResult) {
    return new ModelCapabilitiesData(
        new InputModalities(userMessage, toolResult),
        new OutputModalities(List.of(Modality.TEXT)),
        false,
        false,
        true,
        true,
        200000,
        8192);
  }

  private JsonNode node(ModelCapabilitiesData yaml) {
    return mapper.valueToTree(yaml);
  }

  private ModelCapabilitiesResolver resolverFor(Map<String, ApiFamily> families) {
    return new ModelCapabilitiesResolverImpl(new CapabilityMatrix(families), mapper);
  }

  // --- Tests -----------------------------------------------------------

  @Test
  void overridePresentShortCircuitsResolutionAndReturnsVerbatim() {
    final var resolver = resolverFor(Map.of());
    final var override =
        new ModelCapabilities(
            List.of(Modality.TEXT, Modality.IMAGE, Modality.AUDIO),
            List.of(Modality.TEXT),
            List.of(Modality.TEXT),
            true,
            true,
            true,
            true,
            12345,
            6789);

    final var result = resolver.resolve("anthropic-messages", "anything", Optional.of(override));

    assertThat(result).isSameAs(override);
  }

  @Test
  void unknownApiFamilyFallsThroughToConservativeDefaults() {
    final var resolver = resolverFor(Map.of());

    final var caps = resolver.resolve("does-not-exist", "claude-opus-4-7", Optional.empty());

    assertThat(caps).isEqualTo(ModelCapabilitiesResolverImpl.CONSERVATIVE_DEFAULTS);
    assertThat(caps.userMessageModalities()).containsExactly(Modality.TEXT);
    assertThat(caps.toolResultModalities()).containsExactly(Modality.TEXT);
    assertThat(caps.assistantMessageModalities()).containsExactly(Modality.TEXT);
    assertThat(caps.supportsReasoning()).isFalse();
    assertThat(caps.contextWindow()).isNull();
    assertThat(caps.maxOutputTokens()).isNull();
  }

  @Test
  void unknownModelInKnownFamilyFallsThroughToFamilyDefaultsNotConservativeDefaults() {
    final var defaults =
        node(fullDefaults(List.of(Modality.TEXT, Modality.IMAGE), List.of(Modality.TEXT)));
    final var entry =
        new ModelEntry("claude-opus-4-7", List.of(), List.of(), mapper.createObjectNode());
    final var resolver =
        resolverFor(Map.of("anthropic-messages", new ApiFamily(defaults, List.of(entry))));

    final var caps = resolver.resolve("anthropic-messages", "claude-mystery", Optional.empty());

    // The family declares its own (non-conservative) defaults, so an unmatched model gets those,
    // not the fully conservative baseline.
    assertThat(caps).isNotEqualTo(ModelCapabilitiesResolverImpl.CONSERVATIVE_DEFAULTS);
    assertThat(caps.userMessageModalities()).containsExactly(Modality.TEXT, Modality.IMAGE);
    assertThat(caps.toolResultModalities()).containsExactly(Modality.TEXT);
    assertThat(caps.supportsPromptCaching()).isTrue();
    assertThat(caps.supportsParallelToolCalls()).isTrue();
    assertThat(caps.contextWindow()).isEqualTo(200000);
    assertThat(caps.maxOutputTokens()).isEqualTo(8192);
  }

  @Test
  void multiplePatternsOnOneEntryBothResolve() {
    final var defaults = node(fullDefaults(List.of(Modality.TEXT), List.of(Modality.TEXT)));
    final var overlay =
        node(new ModelCapabilitiesData(null, null, true, null, null, null, null, null));
    final var entry =
        new ModelEntry(null, List.of(), List.of("claude-opus-4-6*", "claude-opus-4-7*"), overlay);
    final var resolver =
        resolverFor(Map.of("anthropic-messages", new ApiFamily(defaults, List.of(entry))));

    final var byFirstPattern =
        resolver.resolve("anthropic-messages", "claude-opus-4-6", Optional.empty());
    final var bySecondPattern =
        resolver.resolve("anthropic-messages", "claude-opus-4-7", Optional.empty());

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
        node(new ModelCapabilitiesData(null, null, true, true, null, null, null, 32000));
    final var entry =
        new ModelEntry("claude-opus-4-7", List.of("claude-opus-latest"), List.of(), overlay);

    final var resolver =
        resolverFor(Map.of("anthropic-messages", new ApiFamily(defaults, List.of(entry))));

    final var caps = resolver.resolve("anthropic-messages", "claude-opus-4-7", Optional.empty());

    assertThat(caps.userMessageModalities())
        .containsExactly(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT);
    assertThat(caps.toolResultModalities()).containsExactly(Modality.TEXT, Modality.IMAGE);
    assertThat(caps.supportsReasoning()).isTrue();
    assertThat(caps.supportsReasoningSignatureRoundtrip()).isTrue();
    assertThat(caps.supportsPromptCaching()).isTrue();
    assertThat(caps.contextWindow()).isEqualTo(200000);
    assertThat(caps.maxOutputTokens()).isEqualTo(32000);
  }

  @Test
  void aliasMatchResolvesToSameEntryAsExactId() {
    final var defaults = node(fullDefaults(List.of(Modality.TEXT), List.of(Modality.TEXT)));
    final var overlay =
        node(new ModelCapabilitiesData(null, null, null, null, null, null, null, 32000));
    final var entry =
        new ModelEntry("claude-opus-4-7", List.of("claude-opus-latest"), List.of(), overlay);
    final var resolver =
        resolverFor(Map.of("anthropic-messages", new ApiFamily(defaults, List.of(entry))));

    final var byAlias =
        resolver.resolve("anthropic-messages", "claude-opus-latest", Optional.empty());
    final var byId = resolver.resolve("anthropic-messages", "claude-opus-4-7", Optional.empty());

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
            node(new ModelCapabilitiesData(null, null, false, null, null, null, null, null))));
    models.put(
        "claude-opus",
        new ModelEntry(
            null,
            List.of(),
            List.of("claude-opus-*"),
            node(new ModelCapabilitiesData(null, null, true, null, null, null, null, null))));

    final var resolver =
        resolverFor(
            Map.of("anthropic-messages", new ApiFamily(defaults, List.copyOf(models.values()))));

    final var caps = resolver.resolve("anthropic-messages", "claude-opus-3-5", Optional.empty());

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
            new ModelCapabilitiesData(
                new InputModalities(List.of(Modality.TEXT), null),
                null,
                null,
                null,
                null,
                null,
                null,
                null));
    final var entry = new ModelEntry(null, List.of(), List.of("claude-haiku-*"), overlay);

    final var resolver =
        resolverFor(Map.of("anthropic-messages", new ApiFamily(defaults, List.of(entry))));

    final var caps = resolver.resolve("anthropic-messages", "claude-haiku-4-5", Optional.empty());

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
   * ModelCapabilitiesData} instance through the same mapper. Dropping the annotations would break
   * this specific round-trip even though a valueToTree/treeToValue round-trip through the same
   * mapper instance would stay self-consistent either way.
   */
  @Test
  void resolvesLowercaseYamlModalityStringsFromRawJsonNode() throws Exception {
    final JsonNode overlay =
        mapper.readTree("{\"input_modalities\": {\"user_message\": [\"text\", \"image\"]}}");
    final var defaults = node(fullDefaults(List.of(Modality.TEXT), List.of(Modality.TEXT)));
    final var entry = new ModelEntry("claude-x", List.of(), List.of(), overlay);

    final var resolver =
        resolverFor(Map.of("anthropic-messages", new ApiFamily(defaults, List.of(entry))));

    final var caps = resolver.resolve("anthropic-messages", "claude-x", Optional.empty());

    assertThat(caps.userMessageModalities()).containsExactly(Modality.TEXT, Modality.IMAGE);
  }
}
