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
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies the prompt-caching presentation for every provider in all four current AI Agent v2
 * templates.
 */
class PromptCachingElementTemplateTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String GUIDANCE =
      "Can speed up responses and lower API costs by reusing text from recent requests. "
          + "Best for long conversations or large documents.";

  private static final List<Path> TEMPLATE_PATHS =
      List.of(
          Path.of("element-templates/agenticai-ai-agent-task.v2.json"),
          Path.of("element-templates/agenticai-ai-agent-subprocess.v2.json"),
          Path.of("element-templates/hybrid/agenticai-ai-agent-task.v2-hybrid.json"),
          Path.of("element-templates/hybrid/agenticai-ai-agent-subprocess.v2-hybrid.json"));

  private static final List<ExpectedPromptCachingProperty> EXPECTED_PROPERTIES =
      List.of(
          optional(
              "anthropic",
              "provider.anthropic.model.model",
              "provider.anthropic.model.parameters.promptCaching.enabled",
              "https://platform.claude.com/docs/en/build-with-claude/prompt-caching#automatic-caching"),
          optional(
              "bedrock",
              "provider.bedrock.model.model",
              "provider.bedrock.model.parameters.promptCaching.enabled",
              "https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-caching.html"),
          automatic(
              "openai",
              "provider.openai.model.model",
              "promptCaching.openai.status",
              "modeler:promptCachingOpenAI",
              "https://developers.openai.com/api/docs/guides/prompt-caching"),
          automatic(
              "google-gemini",
              "provider.googleGemini.model.model",
              "promptCaching.googleGemini.status",
              "modeler:promptCachingGoogleGemini",
              "https://ai.google.dev/gemini-api/docs/caching"),
          new ExpectedPromptCachingProperty(
              "unavailable custom",
              "custom",
              "provider.model",
              "promptCaching.custom.status",
              false,
              false,
              "property",
              "modeler:promptCachingCustom",
              "Not available.",
              "The prompt caching property does not control caching for custom implementations. "
                  + "Use a custom solution instead."));

  // Verifies each provider's prompt-caching property shape and placement in every template.
  @ParameterizedTest(name = "{0} + {1}")
  @MethodSource("templateAndPromptCachingProperties")
  void exposesPromptCachingForProvider(Path templatePath, ExpectedPromptCachingProperty expected)
      throws IOException {
    final var properties = properties(OBJECT_MAPPER.readTree(templatePath.toFile()));
    final var cachingProperties =
        properties.stream()
            .filter(property -> "Prompt caching".equals(property.path("label").asText()))
            .toList();
    final var cachingProperty = property(cachingProperties, expected.id());
    final var modelProperty = property(properties, expected.modelId());

    assertThat(properties.indexOf(cachingProperty))
        .isEqualTo(properties.indexOf(modelProperty) + 1);
    assertThat(cachingProperty.path("group").asText()).isEqualTo("model");
    assertThat(cachingProperty.path("type").asText()).isEqualTo("Boolean");
    assertThat(cachingProperty.path("condition").path("property").asText())
        .isEqualTo("provider.type");
    assertThat(cachingProperty.path("condition").path("equals").asText())
        .isEqualTo(expected.provider());
    assertThat(cachingProperty.path("editable").asBoolean(true)).isEqualTo(expected.editable());
    assertThat(cachingProperty.path("value").asBoolean()).isEqualTo(expected.enabled());
    assertThat(cachingProperty.path("binding").path("type").asText())
        .isEqualTo(expected.bindingType());
    assertThat(cachingProperty.path("binding").path("name").asText())
        .isEqualTo(expected.bindingName());
    assertThat(cachingProperty.path("tooltip").asText()).isEqualTo(expected.tooltip());

    if (expected.description() == null) {
      assertThat(cachingProperty.has("description")).isFalse();
    } else {
      assertThat(cachingProperty.path("description").asText()).isEqualTo(expected.description());
    }
  }

  // Verifies prompt-caching coverage stays aligned with the available providers.
  @ParameterizedTest(name = "{0}")
  @MethodSource("templatePaths")
  void exposesPromptCachingOnlyForSupportedProviders(Path templatePath) throws IOException {
    final var properties = properties(OBJECT_MAPPER.readTree(templatePath.toFile()));
    final var cachingProperties =
        properties.stream()
            .filter(property -> "Prompt caching".equals(property.path("label").asText()))
            .toList();

    assertThat(cachingProperties).hasSize(EXPECTED_PROPERTIES.size());
    assertThat(providerChoices(properties))
        .containsExactlyInAnyOrderElementsOf(
            EXPECTED_PROPERTIES.stream()
                .map(ExpectedPromptCachingProperty::provider)
                .collect(java.util.stream.Collectors.toSet()));
  }

  private static Stream<Arguments> templateAndPromptCachingProperties() {
    return TEMPLATE_PATHS.stream()
        .flatMap(
            templatePath ->
                EXPECTED_PROPERTIES.stream().map(expected -> Arguments.of(templatePath, expected)));
  }

  private static Stream<Path> templatePaths() {
    return TEMPLATE_PATHS.stream();
  }

  private static List<JsonNode> properties(JsonNode template) {
    final var properties = new ArrayList<JsonNode>();
    template.path("properties").forEach(properties::add);
    return properties;
  }

  private static JsonNode property(List<JsonNode> properties, String id) {
    return properties.stream()
        .filter(property -> id.equals(property.path("id").asText()))
        .findFirst()
        .orElseThrow();
  }

  private static Set<String> providerChoices(List<JsonNode> properties) {
    return property(properties, "provider.type")
        .path("choices")
        .valueStream()
        .map(choice -> choice.path("value").asText())
        .collect(java.util.stream.Collectors.toSet());
  }

  private static ExpectedPromptCachingProperty optional(
      String provider, String modelId, String id, String documentationUrl) {
    return new ExpectedPromptCachingProperty(
        "optional " + provider,
        provider,
        modelId,
        id,
        true,
        false,
        "zeebe:input",
        id,
        null,
        tooltip(documentationUrl));
  }

  private static ExpectedPromptCachingProperty automatic(
      String provider, String modelId, String id, String bindingName, String documentationUrl) {
    return new ExpectedPromptCachingProperty(
        "automatic " + provider,
        provider,
        modelId,
        id,
        false,
        true,
        "property",
        bindingName,
        "Automatic.",
        tooltip(documentationUrl));
  }

  private static String tooltip(String documentationUrl) {
    return GUIDANCE
        + "<br><br>See the <a href=\""
        + documentationUrl
        + "\" target=\"_blank\">caching documentation</a>.";
  }

  private record ExpectedPromptCachingProperty(
      String displayName,
      String provider,
      String modelId,
      String id,
      boolean editable,
      boolean enabled,
      String bindingType,
      String bindingName,
      String description,
      String tooltip) {

    @Override
    public String toString() {
      return displayName;
    }
  }
}
