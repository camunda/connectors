/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.capabilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.api.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.api.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.AgenticAiFrameworkProperties.ApiFamilyProperties;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.AgenticAiFrameworkProperties.ModelEntryProperties;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilitiesYaml.InputModalities;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilitiesYaml.OutputModalities;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModelCapabilitiesResolverTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  // --- Builder helpers -----------------------------------------------------

  private static AgenticAiFrameworkProperties props(Map<String, ApiFamilyProperties> families) {
    return new AgenticAiFrameworkProperties(families);
  }

  private static ApiFamilyProperties family(
      ModelCapabilitiesYaml defaults, Map<String, ModelEntryProperties> models) {
    return new ApiFamilyProperties(defaults, models);
  }

  private static ModelEntryProperties entry(ModelCapabilitiesYaml capabilities) {
    return new ModelEntryProperties(null, null, List.of(), capabilities);
  }

  private static ModelEntryProperties entryWithAliases(
      List<String> aliases, ModelCapabilitiesYaml capabilities) {
    return new ModelEntryProperties(null, null, aliases, capabilities);
  }

  /** A fully-populated capability block usable as an api-family default. */
  private static ModelCapabilitiesYaml fullDefaults(
      List<Modality> userMessage, List<Modality> toolResult) {
    return new ModelCapabilitiesYaml(
        new InputModalities(userMessage, toolResult),
        new OutputModalities(List.of(Modality.TEXT)),
        false,
        false,
        true,
        true,
        200000,
        8192);
  }

  private ModelCapabilitiesResolver resolverFor(AgenticAiFrameworkProperties props) {
    return new ModelCapabilitiesResolver(
        CapabilityMatrixFactory.build(props, objectMapper), objectMapper);
  }

  // --- Tests ---------------------------------------------------------------

  @Test
  void overrideShortCircuitsResolution() {
    final var resolver = resolverFor(props(Map.of()));
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
  void exactIdMatchInheritsDefaultsAndAppliesOverrides() {
    // defaults: DOCUMENT in user_message, max_output_tokens=8192, no reasoning
    // override on claude-opus-4-7: enable reasoning, max_output_tokens=32000
    final var defaults =
        fullDefaults(
            List.of(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT),
            List.of(Modality.TEXT, Modality.IMAGE));
    final var override = new ModelCapabilitiesYaml(null, null, true, true, null, null, null, 32000);

    final var resolver =
        resolverFor(
            props(
                Map.of(
                    "anthropic-messages",
                    family(
                        defaults,
                        Map.of(
                            "claude-opus-4-7",
                            entryWithAliases(List.of("claude-opus-latest"), override))))));

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
  void aliasMatchResolvesToSameEntry() {
    final var resolver =
        resolverFor(
            props(
                Map.of(
                    "anthropic-messages",
                    family(
                        fullDefaults(List.of(Modality.TEXT), List.of(Modality.TEXT)),
                        Map.of(
                            "claude-opus-4-7",
                            entryWithAliases(
                                List.of("claude-opus-latest"),
                                new ModelCapabilitiesYaml(
                                    null, null, null, null, null, null, null, 32000)))))));

    final var byAlias =
        resolver.resolve("anthropic-messages", "claude-opus-latest", Optional.empty());
    final var byId = resolver.resolve("anthropic-messages", "claude-opus-4-7", Optional.empty());

    assertThat(byAlias).isEqualTo(byId);
  }

  @Test
  void longestPatternWins() {
    // claude-opus-* (12 chars) beats claude-* (8 chars).
    final var models = new LinkedHashMap<String, ModelEntryProperties>();
    models.put(
        "claude-fallback",
        new ModelEntryProperties(
            null,
            List.of("claude-*"),
            List.of(),
            new ModelCapabilitiesYaml(null, null, false, null, null, null, null, null)));
    models.put(
        "claude-opus",
        new ModelEntryProperties(
            null,
            List.of("claude-opus-*"),
            List.of(),
            new ModelCapabilitiesYaml(null, null, true, null, null, null, null, null)));

    final var resolver =
        resolverFor(
            props(
                Map.of(
                    "anthropic-messages",
                    family(fullDefaults(List.of(Modality.TEXT), List.of(Modality.TEXT)), models))));

    final var caps = resolver.resolve("anthropic-messages", "claude-opus-3-5", Optional.empty());

    assertThat(caps.supportsReasoning()).isTrue();
  }

  @Test
  void deepMergeKeepsInheritedSubKeyWhenOverlayOverridesSibling() {
    // Defaults provide tool_result=[text,image]; override only user_message.
    final var defaults =
        fullDefaults(
            List.of(Modality.TEXT, Modality.IMAGE), List.of(Modality.TEXT, Modality.IMAGE));
    final var override =
        new ModelCapabilitiesYaml(
            new InputModalities(List.of(Modality.TEXT), null),
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    final var resolver =
        resolverFor(
            props(
                Map.of(
                    "anthropic-messages",
                    family(
                        defaults,
                        Map.of(
                            "claude-haiku",
                            new ModelEntryProperties(
                                null, List.of("claude-haiku-*"), List.of(), override))))));

    final var caps = resolver.resolve("anthropic-messages", "claude-haiku-4-5", Optional.empty());

    assertThat(caps.userMessageModalities()).containsExactly(Modality.TEXT);
    assertThat(caps.toolResultModalities()).containsExactly(Modality.TEXT, Modality.IMAGE);
  }

  @Test
  void unknownApiFamilyFallsThroughToConservativeDefaults() {
    final var resolver = resolverFor(props(Map.of()));

    final var caps = resolver.resolve("does-not-exist", "claude-opus-4-7", Optional.empty());

    assertThat(caps).isEqualTo(ModelCapabilitiesResolver.CONSERVATIVE_DEFAULTS);
  }

  @Test
  void unknownModelFallsThroughToConservativeDefaultsWhenNoFallbackPattern() {
    final var resolver =
        resolverFor(
            props(
                Map.of(
                    "anthropic-messages",
                    family(
                        fullDefaults(
                            List.of(Modality.TEXT, Modality.IMAGE), List.of(Modality.TEXT)),
                        Map.of("claude-opus-4-7", entry(emptyOverride()))))));

    final var caps = resolver.resolve("anthropic-messages", "claude-mystery", Optional.empty());

    assertThat(caps).isEqualTo(ModelCapabilitiesResolver.CONSERVATIVE_DEFAULTS);
  }

  @Test
  void rejectsEntryWithBothExplicitIdAndPattern() {
    final var props =
        props(
            Map.of(
                "anthropic-messages",
                family(
                    fullDefaults(List.of(Modality.TEXT), List.of(Modality.TEXT)),
                    Map.of(
                        "claude-opus-4-7",
                        new ModelEntryProperties(
                            "claude-opus-4-7",
                            List.of("claude-opus-*"),
                            List.of(),
                            emptyOverride())))));

    assertThatThrownBy(() -> CapabilityMatrixFactory.build(props, objectMapper))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("at most one of");
  }

  @Test
  void rejectsPatternEntryWithAliases() {
    final var props =
        props(
            Map.of(
                "anthropic-messages",
                family(
                    fullDefaults(List.of(Modality.TEXT), List.of(Modality.TEXT)),
                    Map.of(
                        "claude-opus",
                        new ModelEntryProperties(
                            null,
                            List.of("claude-opus-*"),
                            List.of("some-alias"),
                            emptyOverride())))));

    assertThatThrownBy(() -> CapabilityMatrixFactory.build(props, objectMapper))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cannot declare aliases");
  }

  @Test
  void mapKeyServesAsImplicitIdAndExactBeatsPattern() {
    final var defaults = fullDefaults(List.of(Modality.TEXT), List.of(Modality.TEXT));
    final var models = new LinkedHashMap<String, ModelEntryProperties>();
    // Map key is the id (no explicit id/pattern fields).
    models.put(
        "claude-opus-4-7",
        entry(new ModelCapabilitiesYaml(null, null, null, null, null, null, null, 64000)));
    // Pattern entry — explicit pattern field.
    models.put(
        "claude-opus",
        new ModelEntryProperties(
            null,
            List.of("claude-opus-*"),
            List.of(),
            new ModelCapabilitiesYaml(null, null, null, null, null, null, null, 8192)));

    final var resolver = resolverFor(props(Map.of("anthropic-messages", family(defaults, models))));

    final var byId = resolver.resolve("anthropic-messages", "claude-opus-4-7", Optional.empty());
    assertThat(byId.maxOutputTokens()).isEqualTo(64000);

    final var byPattern =
        resolver.resolve("anthropic-messages", "claude-opus-3-5", Optional.empty());
    assertThat(byPattern.maxOutputTokens()).isEqualTo(8192);
  }

  @Test
  void multiplePatternsPerEntry() {
    // pattern as a list — entry matches if any glob matches; longest matching glob's length
    // determines the entry's score for cross-entry longest-match selection.
    final var defaults = fullDefaults(List.of(Modality.TEXT), List.of(Modality.TEXT));
    final var entry =
        new ModelEntryProperties(
            null,
            List.of("gpt-4o*", "gpt-4-turbo*"),
            List.of(),
            new ModelCapabilitiesYaml(null, null, null, null, null, null, null, 16384));

    final var resolver =
        resolverFor(
            props(Map.of("openai-completions", family(defaults, Map.of("gpt-4o-family", entry)))));

    assertThat(
            resolver
                .resolve("openai-completions", "gpt-4o-mini", Optional.empty())
                .maxOutputTokens())
        .isEqualTo(16384);
    assertThat(
            resolver
                .resolve("openai-completions", "gpt-4-turbo-2024-04-09", Optional.empty())
                .maxOutputTokens())
        .isEqualTo(16384);
  }

  @Test
  void deepMergeReplacesListsWholesale() {
    final var defaults =
        fullDefaults(List.of(Modality.TEXT, Modality.IMAGE), List.of(Modality.TEXT));
    final var override =
        new ModelCapabilitiesYaml(
            new InputModalities(List.of(Modality.TEXT), null),
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    final var resolver =
        resolverFor(
            props(
                Map.of(
                    "openai-completions",
                    family(
                        defaults,
                        Map.of(
                            "gpt-4o",
                            new ModelEntryProperties(
                                null, List.of("gpt-4o*"), List.of(), override))))));

    final var caps = resolver.resolve("openai-completions", "gpt-4o-mini", Optional.empty());

    assertThat(caps.userMessageModalities()).containsExactly(Modality.TEXT);
  }

  @Test
  void globMatcherTreatsStarAsWildcard() {
    assertThat(ModelCapabilitiesResolver.matchesGlob("claude-opus-*", "claude-opus-4-7")).isTrue();
    assertThat(ModelCapabilitiesResolver.matchesGlob("claude-opus-*", "claude-sonnet-4-7"))
        .isFalse();
    assertThat(ModelCapabilitiesResolver.matchesGlob("gpt-*", "gpt-4o-mini")).isTrue();
    assertThat(ModelCapabilitiesResolver.matchesGlob("gpt-4.1*", "gpt-4.1-nano")).isTrue();
    assertThat(ModelCapabilitiesResolver.matchesGlob("gpt-4.1*", "gpt-4o")).isFalse();
  }

  private static ModelCapabilitiesYaml emptyOverride() {
    return new ModelCapabilitiesYaml(null, null, null, null, null, null, null, null);
  }
}
