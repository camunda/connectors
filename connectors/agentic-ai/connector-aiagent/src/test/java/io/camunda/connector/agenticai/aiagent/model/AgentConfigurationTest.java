/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.request.v1.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v1.OpenAiProviderConfiguration.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.request.v1.OpenAiProviderConfiguration.OpenAiModel;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend.OpenAiApiConnection;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend.CustomBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiCustomEndpointAuthentication.ApiKeyAuthentication;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link AgentConfiguration#fingerprint()} is the change-detection and dedup key for the {@code
 * CONFIGURATION} agent-instance history item: it must stay stable for unchanged configuration and
 * change whenever anything the engine records on that item (model, provider, system prompt,
 * model-call limit, tools) changes.
 */
class AgentConfigurationTest {

  private static AgentConfiguration configuration(
      String model,
      @Nullable String systemPrompt,
      @Nullable Integer maxModelCalls,
      List<ToolDefinition> tools) {
    return new AgentConfiguration(
            new OpenAiProviderConfiguration(
                new OpenAiConnection(null, null, new OpenAiModel(model, null))),
            new SystemPromptConfiguration(systemPrompt),
            null,
            null,
            maxModelCalls != null ? new LimitsConfiguration(maxModelCalls) : null,
            null,
            null)
        .withToolDefinitions(tools);
  }

  private static AgentConfiguration configurationWithOpenAiBackend(OpenAiBackend backend) {
    return new AgentConfiguration(
        new OpenAiChatModelConfiguration(
            new OpenAiChatModelConfiguration.OpenAiConnection(
                new OpenAiCompletionsApi(null),
                backend,
                new OpenAiChatModelConfiguration.OpenAiModel("gpt-5.5"),
                null)),
        new SystemPromptConfiguration("Be nice."),
        null,
        null,
        null,
        null,
        null);
  }

  @Nested
  class WithToolDefinitions {

    @Test
    void returnsACopyCarryingTheGivenTools() {
      final var original = configuration("gpt-4o", "Be nice.", null, List.of());
      final var tools =
          List.of(ToolDefinition.builder().name("getWeather").description("d").build());

      final var updated = original.withToolDefinitions(tools);

      assertThat(original.toolDefinitions()).isEmpty();
      assertThat(updated.toolDefinitions()).isEqualTo(tools);
    }
  }

  @Nested
  class Fingerprint {

    @Test
    void equalConfigurationYieldsEqualFingerprint() {
      final var tools =
          List.of(
              ToolDefinition.builder()
                  .name("getWeather")
                  .description("Get the weather forecast")
                  .inputSchema(Map.of("type", "object"))
                  .build());

      final var first = configuration("gpt-4o", "Be nice.", 10, tools).fingerprint();
      final var second = configuration("gpt-4o", "Be nice.", 10, tools).fingerprint();

      assertThat(first).isEqualTo(second);
    }

    @Test
    void changedModelYieldsDifferentFingerprint() {
      final var first = configuration("gpt-4o", "Be nice.", null, List.of()).fingerprint();
      final var second = configuration("gpt-4o-mini", "Be nice.", null, List.of()).fingerprint();

      assertThat(first).isNotEqualTo(second);
    }

    @Test
    void changedSystemPromptYieldsDifferentFingerprint() {
      final var first = configuration("gpt-4o", "Be nice.", null, List.of()).fingerprint();
      final var second = configuration("gpt-4o", "Be mean.", null, List.of()).fingerprint();

      assertThat(first).isNotEqualTo(second);
    }

    @Test
    void nullSystemPromptHasAStableAndDistinctFingerprint() {
      final var fingerprint = configuration("gpt-4o", null, null, List.of()).fingerprint();

      assertThat(fingerprint)
          .isEqualTo(configuration("gpt-4o", null, null, List.of()).fingerprint())
          .isNotEqualTo(configuration("gpt-4o", "", null, List.of()).fingerprint())
          .isNotEqualTo(configuration("gpt-4o", "null", null, List.of()).fingerprint());
    }

    @Test
    void unsetMaxModelCallsYieldsSameFingerprintAsExplicitDefault() {
      final var unset = configuration("gpt-4o", "Be nice.", null, List.of()).fingerprint();
      final var explicit =
          configuration("gpt-4o", "Be nice.", AgentConfiguration.DEFAULT_MAX_MODEL_CALLS, List.of())
              .fingerprint();

      assertThat(unset).isEqualTo(explicit);
    }

    @Test
    void changedMaxModelCallsYieldsDifferentFingerprint() {
      final var first = configuration("gpt-4o", "Be nice.", 10, List.of()).fingerprint();
      final var second = configuration("gpt-4o", "Be nice.", 20, List.of()).fingerprint();

      assertThat(first).isNotEqualTo(second);
    }

    @Test
    void changedToolsYieldsDifferentFingerprint() {
      final var toolsA =
          List.of(ToolDefinition.builder().name("getWeather").description("d").build());
      final var toolsB = List.of(ToolDefinition.builder().name("getTime").description("d").build());

      final var first = configuration("gpt-4o", "Be nice.", null, toolsA).fingerprint();
      final var second = configuration("gpt-4o", "Be nice.", null, toolsB).fingerprint();

      assertThat(first).isNotEqualTo(second);
    }

    @Test
    void changedBackendYieldsDifferentFingerprintEvenWithSameProviderModelAndPrompt() {
      final var apiBackend =
          new OpenAiApiBackend(
              new OpenAiApiConnection("sk-test", null, null, null, null, null, null));
      final var customBackend =
          new OpenAiCustomBackend(
              new CustomBackend(
                  "https://custom.example.com/v1",
                  null,
                  null,
                  null,
                  new ApiKeyAuthentication("sk-test")));

      final var first = configurationWithOpenAiBackend(apiBackend);
      final var second = configurationWithOpenAiBackend(customBackend);

      assertThat(first.chatModel().provider()).isEqualTo(second.chatModel().provider());
      assertThat(first.fingerprint()).isNotEqualTo(second.fingerprint());
    }

    private static String fingerprintOfSchema(Map<String, Object> schema) {
      return configuration(
              "gpt-4o",
              "Be nice.",
              null,
              List.of(
                  ToolDefinition.builder().name("t").description("d").inputSchema(schema).build()))
          .fingerprint();
    }

    private static Map<String, Object> ordered(Object... keysAndValues) {
      if (keysAndValues.length % 2 != 0) {
        throw new IllegalArgumentException("Expected pairs of a key and a value");
      }
      final var map = new LinkedHashMap<String, Object>();
      for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
        map.put((String) keysAndValues[i], keysAndValues[i + 1]);
      }
      return map;
    }

    @Test
    void topLevelSchemaKeyOrderIsIrrelevant() {
      final var props = ordered("a", ordered("type", "string"));
      final var first = ordered("type", "object", "properties", props, "required", List.of("a"));
      final var second = ordered("required", List.of("a"), "properties", props, "type", "object");

      assertThat(fingerprintOfSchema(first)).isEqualTo(fingerprintOfSchema(second));
    }

    @Test
    void nestedSchemaKeywordOrderIsIrrelevant() {
      final var first =
          ordered("properties", ordered("city", ordered("type", "string", "minLength", 1)));
      final var second =
          ordered("properties", ordered("city", ordered("minLength", 1, "type", "string")));

      assertThat(fingerprintOfSchema(first)).isEqualTo(fingerprintOfSchema(second));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {"$defs", "definitions", "patternProperties", "dependentSchemas", "dependencies"})
    void namedSchemaCalledPropertiesIgnoresKeywordOrder(String keyword) {
      final var first =
          ordered(keyword, ordered("properties", ordered("type", "string", "description", "d")));
      final var second =
          ordered(keyword, ordered("properties", ordered("description", "d", "type", "string")));

      assertThat(fingerprintOfSchema(first)).isEqualTo(fingerprintOfSchema(second));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {"$defs", "definitions", "patternProperties", "dependentSchemas", "dependencies"})
    void namedSchemaCalledPropertiesRetainsParameterOrder(String keyword) {
      final var first =
          ordered(
              keyword,
              ordered(
                  "properties",
                  ordered(
                      "properties",
                      ordered("a", ordered("type", "string"), "b", ordered("type", "string")))));
      final var second =
          ordered(
              keyword,
              ordered(
                  "properties",
                  ordered(
                      "properties",
                      ordered("b", ordered("type", "string"), "a", ordered("type", "string")))));

      assertThat(fingerprintOfSchema(first)).isNotEqualTo(fingerprintOfSchema(second));
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "const"})
    void dataValueWithPropertiesKeyIgnoresKeyOrder(String keyword) {
      final var first = ordered(keyword, ordered("properties", ordered("a", 1, "b", 2)));
      final var second = ordered(keyword, ordered("properties", ordered("b", 2, "a", 1)));

      assertThat(fingerprintOfSchema(first)).isEqualTo(fingerprintOfSchema(second));
    }

    @ParameterizedTest
    @ValueSource(strings = {"enum", "examples"})
    void dataListWithPropertiesKeyIgnoresKeyOrder(String keyword) {
      final var first = ordered(keyword, List.of(ordered("properties", ordered("a", 1, "b", 2))));
      final var second = ordered(keyword, List.of(ordered("properties", ordered("b", 2, "a", 1))));

      assertThat(fingerprintOfSchema(first)).isEqualTo(fingerprintOfSchema(second));
    }

    @Test
    void schemaKeywordOrderInsideArraysIsIrrelevant() {
      final var first = ordered("anyOf", List.of(ordered("type", "string", "minLength", 1)));
      final var second = ordered("anyOf", List.of(ordered("minLength", 1, "type", "string")));

      assertThat(fingerprintOfSchema(first)).isEqualTo(fingerprintOfSchema(second));
    }

    @Test
    void nestedParameterOrderRemainsSignificant() {
      final var first =
          ordered(
              "properties",
              ordered(
                  "address",
                  ordered(
                      "properties",
                      ordered(
                          "city",
                          ordered("type", "string"),
                          "country",
                          ordered("type", "string")))));
      final var second =
          ordered(
              "properties",
              ordered(
                  "address",
                  ordered(
                      "properties",
                      ordered(
                          "country",
                          ordered("type", "string"),
                          "city",
                          ordered("type", "string")))));

      assertThat(fingerprintOfSchema(first)).isNotEqualTo(fingerprintOfSchema(second));
    }

    @Test
    void parameterOrderIsSignificant() {
      final var first =
          ordered(
              "type",
              "object",
              "properties",
              ordered("a", ordered("type", "string"), "b", ordered("type", "number")));
      final var second =
          ordered(
              "type",
              "object",
              "properties",
              ordered("b", ordered("type", "number"), "a", ordered("type", "string")));

      assertThat(fingerprintOfSchema(first)).isNotEqualTo(fingerprintOfSchema(second));
    }

    @Test
    void listOrderIsSignificant() {
      final var first = ordered("required", List.of("a", "b"));
      final var second = ordered("required", List.of("b", "a"));

      assertThat(fingerprintOfSchema(first)).isNotEqualTo(fingerprintOfSchema(second));
    }

    @Test
    void toolOrderIsSignificant() {
      final var a = ToolDefinition.builder().name("a").description("d").build();
      final var b = ToolDefinition.builder().name("b").description("d").build();

      assertThat(configuration("gpt-4o", "p", null, List.of(a, b)).fingerprint())
          .isNotEqualTo(configuration("gpt-4o", "p", null, List.of(b, a)).fingerprint());
    }

    @Test
    void changedSchemaDetailYieldsDifferentFingerprint() {
      final var base =
          ordered("properties", ordered("a", ordered("type", "string", "description", "x")));
      final var otherType =
          ordered("properties", ordered("a", ordered("type", "number", "description", "x")));
      final var otherDescription =
          ordered("properties", ordered("a", ordered("type", "string", "description", "y")));

      assertThat(fingerprintOfSchema(base)).isNotEqualTo(fingerprintOfSchema(otherType));
      assertThat(fingerprintOfSchema(base)).isNotEqualTo(fingerprintOfSchema(otherDescription));
    }

    @Test
    void changedToolNameOrDescriptionYieldsDifferentFingerprint() {
      final Function<ToolDefinition, String> fingerprint =
          tool -> configuration("gpt-4o", "p", null, List.of(tool)).fingerprint();

      final var base = ToolDefinition.builder().name("a").description("d").build();
      assertThat(fingerprint.apply(base))
          .isNotEqualTo(fingerprint.apply(base.withName("b")))
          .isNotEqualTo(fingerprint.apply(base.withDescription("e")))
          .isNotEqualTo(fingerprint.apply(base.withDescription(null)));
    }

    @Test
    void changedProviderYieldsDifferentFingerprint() {
      final var openAi = configuration("gpt-4o", "p", null, List.of());
      final var other =
          configurationWithOpenAiBackend(
              new OpenAiApiBackend(
                  new OpenAiApiConnection("sk-test", null, null, null, null, null, null)));

      assertThat(openAi.fingerprint()).isNotEqualTo(other.fingerprint());
    }

    @Test
    void changedNumberYieldsDifferentFingerprint() {
      assertThat(fingerprintOfSchema(ordered("maxItems", 5)))
          .isNotEqualTo(fingerprintOfSchema(ordered("maxItems", 6)))
          .isNotEqualTo(fingerprintOfSchema(ordered("minimum", 0.5)));
      assertThat(fingerprintOfSchema(ordered("minimum", 0.5)))
          .isNotEqualTo(fingerprintOfSchema(ordered("minimum", 0.6)));
    }

    @Test
    void decimalNumbersHashAsTheirRoundTrippedValue() {
      final var third = BigDecimal.ONE.divide(BigDecimal.valueOf(3), MathContext.DECIMAL128);

      assertThat(fingerprintOfSchema(ordered("minimum", new BigDecimal("1E+1"))))
          .isEqualTo(fingerprintOfSchema(ordered("minimum", 10.0)))
          .isEqualTo(fingerprintOfSchema(ordered("minimum", 10)));
      assertThat(fingerprintOfSchema(ordered("minimum", third)))
          .isEqualTo(fingerprintOfSchema(ordered("minimum", third.doubleValue())));
    }

    @Test
    void integralDecimalsKeepFullPrecision() {
      final long aboveDoublePrecision = (1L << 53) + 1;

      assertThat(fingerprintOfSchema(ordered("maximum", new BigDecimal(aboveDoublePrecision))))
          .isEqualTo(fingerprintOfSchema(ordered("maximum", aboveDoublePrecision)))
          .isNotEqualTo(fingerprintOfSchema(ordered("maximum", aboveDoublePrecision - 1)));
      assertThat(fingerprintOfSchema(ordered("maximum", new BigDecimal(1L << 53))))
          .isEqualTo(fingerprintOfSchema(ordered("maximum", 1L << 53)));
    }

    @Test
    void nullAndAbsentAreDistinct() {
      // the constructor accepts a null value, the builder's immutable copy does not
      final Function<Map<String, Object>, String> fingerprint =
          schema ->
              configuration("gpt-4o", "p", null, List.of(new ToolDefinition("t", "d", schema)))
                  .fingerprint();

      assertThat(fingerprint.apply(ordered("default", null)))
          .isNotEqualTo(fingerprint.apply(ordered()));
    }

    @Test
    void definitionsRebuiltThroughRoundTripsKeepTheirFingerprint() throws Exception {
      final var schema =
          ordered(
              "$schema",
              "https://json-schema.org/draft/2020-12/schema",
              "description",
              "d",
              "additionalProperties",
              false,
              "type",
              "object",
              "properties",
              ordered(
                  "city", ordered("type", "string", "description", "c"),
                  "days", ordered("type", "integer", "minimum", 1)),
              "required",
              List.of("city", "days"));
      final var tool =
          ToolDefinition.builder().name("getWeather").description("w").inputSchema(schema).build();
      final var original = configuration("gpt-4o", "Be nice.", null, List.of(tool)).fingerprint();

      var context = AgentContext.builder().toolDefinitions(List.of(tool)).build();
      for (int i = 0; i < 3; i++) {
        context =
            TestObjectMapperSupplier.INSTANCE.readValue(
                TestObjectMapperSupplier.INSTANCE.writeValueAsString(context), AgentContext.class);
        assertThat(
                configuration("gpt-4o", "Be nice.", null, context.toolDefinitions()).fingerprint())
            .isEqualTo(original);
      }
    }

    @Test
    void fingerprintIsNeverBlank() {
      assertThat(configuration("gpt-4o", "Be nice.", null, List.of()).fingerprint()).isNotBlank();
    }

    @Test
    void fingerprintIsACollisionResistantDigest() {
      // guards against a 32-bit Integer.hashCode()-based fingerprint: it doubles as both
      // change-detection and the engine's CONFIGURATION history item id, so a collision would
      // silently suppress a real update or dedup two distinct configurations
      final var fingerprint = configuration("gpt-4o", "Be nice.", null, List.of()).fingerprint();
      assertThat(fingerprint).matches("[0-9a-f]{64}");
    }
  }
}
