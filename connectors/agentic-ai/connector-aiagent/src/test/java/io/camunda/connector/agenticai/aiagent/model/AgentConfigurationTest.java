/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link AgentConfiguration#fingerprint()} is the change-detection and dedup key for the {@code
 * CONFIGURATION} agent-instance history item: it must stay stable for unchanged configuration and
 * change whenever anything the engine records on that item (model, provider, system prompt,
 * model-call limit, tools) changes.
 */
class AgentConfigurationTest {

  private static AgentConfiguration configuration(
      String model,
      String systemPrompt,
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

  private static AgentConfiguration configurationWithTool(Map<String, Object> inputSchema) {
    return configuration("gpt-4o", "Be nice.", null, List.of(tool("getWeather", inputSchema)));
  }

  private static ToolDefinition tool(String name, Map<String, Object> inputSchema) {
    return ToolDefinition.builder()
        .name(name)
        .description("Get the weather forecast")
        .inputSchema(inputSchema)
        .build();
  }

  /** A map that keeps the given entries in the given order, like a map built by Jackson. */
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

  private static Map<String, Object> schemaWithParameters(String... parameters) {
    final var properties = new LinkedHashMap<String, Object>();
    for (String parameter : parameters) {
      properties.put(parameter, ordered("type", "string", "description", parameter));
    }
    return ordered(
        "type", "object", "properties", properties, "required", Arrays.asList(parameters));
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

    @Test
    void keyOrderOfSchemaMapsAtAnyDepthDoesNotChangeFingerprint() {
      final var first =
          ordered(
              "type",
              "object",
              "properties",
              ordered(
                  "days",
                  ordered(
                      "minimum",
                      1,
                      "maximum",
                      7,
                      "items",
                      List.of(ordered("a", 1, "b", ordered("c", 1, "d", 2)))),
                  "city",
                  ordered("type", "string", "enum", List.of("Berlin", "Paris"))),
              "required",
              List.of("days", "city"));
      final var second =
          ordered(
              "required",
              List.of("days", "city"),
              "properties",
              ordered(
                  "days",
                  ordered(
                      "items",
                      List.of(ordered("b", ordered("d", 2, "c", 1), "a", 1)),
                      "maximum",
                      7,
                      "minimum",
                      1),
                  "city",
                  ordered("enum", List.of("Berlin", "Paris"), "type", "string")),
              "type",
              "object");

      assertThat(configurationWithTool(first).fingerprint())
          .isEqualTo(configurationWithTool(second).fingerprint());
    }

    @Test
    void keyOrderOfSchemaMapsInsideAParameterNamedPropertiesDoesNotChangeFingerprint() {
      final var first =
          ordered(
              "properties", ordered("properties", ordered("type", "string", "description", "d")));
      final var second =
          ordered(
              "properties", ordered("properties", ordered("description", "d", "type", "string")));

      assertThat(configurationWithTool(first).fingerprint())
          .isEqualTo(configurationWithTool(second).fingerprint());
    }

    @Test
    void orderOfParametersChangesFingerprint() {
      final var first = configurationWithTool(schemaWithParameters("city", "days")).fingerprint();
      final var second = configurationWithTool(schemaWithParameters("days", "city")).fingerprint();

      assertThat(first).isNotEqualTo(second);
    }

    @Test
    void orderOfRequiredChangesFingerprint() {
      final var first =
          ordered(
              "properties",
              schemaWithParameters("a", "b").get("properties"),
              "required",
              List.of("a", "b"));
      final var second =
          ordered(
              "properties",
              schemaWithParameters("a", "b").get("properties"),
              "required",
              List.of("b", "a"));

      assertThat(configurationWithTool(first).fingerprint())
          .isNotEqualTo(configurationWithTool(second).fingerprint());
    }

    @Test
    void orderOfToolsChangesFingerprint() {
      final var getWeather = tool("getWeather", Map.of("type", "object"));
      final var getTime = tool("getTime", Map.of("type", "object"));

      final var first =
          configuration("gpt-4o", "Be nice.", null, List.of(getWeather, getTime)).fingerprint();
      final var second =
          configuration("gpt-4o", "Be nice.", null, List.of(getTime, getWeather)).fingerprint();

      assertThat(first).isNotEqualTo(second);
    }

    @Test
    void changedSchemaDetailChangesFingerprint() {
      final var first =
          configurationWithTool(ordered("properties", ordered("days", ordered("maximum", 7))));
      final var second =
          configurationWithTool(ordered("properties", ordered("days", ordered("maximum", 8))));

      assertThat(first.fingerprint()).isNotEqualTo(second.fingerprint());
    }

    @Test
    void toolWithoutDescriptionHasAFingerprint() {
      final var tools = List.of(ToolDefinition.builder().name("getWeather").build());

      assertThat(configuration("gpt-4o", "Be nice.", null, tools).fingerprint())
          .matches("[0-9a-f]{64}");
    }

    @Test
    void toolDefinitionHoldsNoSet() {
      // the iteration order of a set is unspecified and no flag of the mapper sorts it
      assertThat(ToolDefinition.class.getRecordComponents())
          .noneMatch(component -> Set.class.isAssignableFrom(component.getType()));
    }

    @Test
    void numbersHashTheSameFreshAndAfterAPersistenceRoundTrip() throws JsonProcessingException {
      final var mapper = ConnectorsObjectMapperSupplier.getCopy();
      final var schema =
          ordered(
              "properties",
              ordered(
                  "price",
                      ordered("minimum", new BigDecimal("0.10"), "maximum", new BigDecimal("2.50")),
                  "count", ordered("maximum", new BigDecimal("1E+2")),
                  "ratio",
                      ordered(
                          "default",
                          BigDecimal.ONE.divide(new BigDecimal(3), MathContext.DECIMAL128)),
                  "big", ordered("maximum", new BigDecimal("9007199254740993")),
                  "scaled", ordered("maximum", 100.0)));
      final var fresh = configurationWithTool(schema);

      final var json = mapper.writeValueAsString(fresh.toolDefinitions());
      final List<ToolDefinition> reloaded = mapper.readValue(json, new TypeReference<>() {});

      assertThat(fresh.withToolDefinitions(reloaded).fingerprint()).isEqualTo(fresh.fingerprint());
    }

    @Test
    void numbersThatDifferChangeFingerprint() {
      final var first = configurationWithTool(ordered("maximum", 9007199254740992L));
      final var second = configurationWithTool(ordered("maximum", 9007199254740993L));

      assertThat(first.fingerprint()).isNotEqualTo(second.fingerprint());
    }

    @Test
    void fingerprintIsTheDigestOfTheCanonicalJsonOfTheConfiguration()
        throws NoSuchAlgorithmException {
      final var configuration = weatherConfiguration();

      final var json =
          """
          ["gpt-4o","%s","Be nice.",10,[["getWeather","Get the weather forecast",\
          {"properties":{"city":{"type":"string"},"days":{"type":"integer"}},"required":["city"],"type":"object"},\
          ["city","days"]]]]\
          """
              .formatted(configuration.chatModel().descriptiveProvider());
      final var expected =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(json.getBytes(StandardCharsets.UTF_8)));

      assertThat(configuration.fingerprint()).isEqualTo(expected);
    }

    @Test
    void numbersNullsEmptyListsAndDatesHaveACanonicalText() throws NoSuchAlgorithmException {
      final var configuration = edgeCaseConfiguration();

      final var json =
          """
          ["gpt-4o","%s","Be nice.",10,[["t","Get the weather forecast",\
          {"properties":{"limit":{"default":null,"enum":[],"exact":9007199254740993,"maximum":100.0,\
          "minimum":0.1,"since":"2026-11-04","timeout":"PT1H","type":"number"}},"type":"object"},\
          ["limit"]]]]\
          """
              .formatted(configuration.chatModel().descriptiveProvider());
      final var expected =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(json.getBytes(StandardCharsets.UTF_8)));

      assertThat(configuration.fingerprint()).isEqualTo(expected);
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

    /**
     * The digests are literals: a change to the mapper, to Jackson or to the serialization of a
     * number, a date or a null that moves the hash input fails here, and with it the CONFIGURATION
     * history item of every tracked agent instance would change on upgrade.
     */
    @ParameterizedTest
    @MethodSource("pinnedFingerprints")
    void fingerprintsOfFixedConfigurationsArePinned(
        AgentConfiguration configuration, String digest) {
      assertThat(configuration.fingerprint()).isEqualTo(digest);
    }

    static Stream<Arguments> pinnedFingerprints() {
      return Stream.of(
          argumentSet(
              "a tool with named parameters",
              weatherConfiguration(),
              "54cad7783b01af263376f0345902a5dc34a3039eb9e6212370e0311654c8be91"),
          argumentSet(
              "numbers, a null, an empty list, a date and a duration",
              edgeCaseConfiguration(),
              "d3a1689d430b256ecc32ee95a679b614341b23b6fc97e054e3e58e5bf93fbb2e"));
    }

    private static AgentConfiguration weatherConfiguration() {
      return configuration(
          "gpt-4o",
          "Be nice.",
          10,
          List.of(
              tool(
                  "getWeather",
                  ordered(
                      "type",
                      "object",
                      "properties",
                      ordered(
                          "city", ordered("type", "string"), "days", ordered("type", "integer")),
                      "required",
                      List.of("city")))));
    }

    private static AgentConfiguration edgeCaseConfiguration() {
      final var schema =
          ordered(
              "type",
              "object",
              "properties",
              ordered(
                  "limit",
                  ordered(
                      "type",
                      "number",
                      "default",
                      null,
                      "minimum",
                      new BigDecimal("0.10"),
                      "maximum",
                      new BigDecimal("1E+2"),
                      "exact",
                      9007199254740993L,
                      "since",
                      LocalDate.of(2026, 11, 4),
                      "timeout",
                      Duration.ofHours(1),
                      "enum",
                      List.of())));
      return configuration("gpt-4o", "Be nice.", 10, List.of(tool("t", schema)));
    }
  }
}
