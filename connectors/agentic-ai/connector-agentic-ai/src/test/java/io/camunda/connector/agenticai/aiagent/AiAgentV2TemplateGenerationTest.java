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
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/**
 * Asserts that the generated v2 element templates expose the native Anthropic provider (introduced
 * in #7211) with its {@code anthropic-api} and {@code compatible} backends, and that the currently
 * single-valued {@code api} property is generated as hidden.
 *
 * <p>These templates are generated artifacts (see {@code connector-agentic-ai/AGENTS.md} § Element
 * templates); this test guards their shape, not their generation mechanism.
 */
class AiAgentV2TemplateGenerationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path TEMPLATES = Path.of("element-templates");

  @Test
  void taskV2TemplateExposesAnthropicProviderWithBackendsAndHiddenApi() throws Exception {
    final Path file = TEMPLATES.resolve("agenticai-ai-agent-task.v2.json");
    assertThat(Files.exists(file)).isTrue();
    final JsonNode json = MAPPER.readTree(Files.readString(file));

    assertThat(json.get("id").asText())
        .isEqualTo("io.camunda.connectors.agenticai.ai-agent-task.v2");

    assertProviderAndBackendsAndApi(json);
  }

  @Test
  void subprocessV2TemplateExposesAnthropicProviderWithBackendsAndHiddenApi() throws Exception {
    final Path file = TEMPLATES.resolve("agenticai-ai-agent-subprocess.v2.json");
    assertThat(Files.exists(file)).isTrue();
    final JsonNode json = MAPPER.readTree(Files.readString(file));

    assertThat(json.get("id").asText())
        .isEqualTo("io.camunda.connectors.agenticai.ai-agent-subprocess.v2");

    assertProviderAndBackendsAndApi(json);
  }

  private void assertProviderAndBackendsAndApi(JsonNode json) {
    final JsonNode properties = json.get("properties");
    assertThat(properties).isNotNull();

    // provider dropdown includes an "Anthropic" entry (type: "anthropic")
    final JsonNode providerType = findProperty(properties, "provider.type");
    assertThat(providerType.get("type").asText()).isEqualTo("Dropdown");
    assertThat(choiceNames(providerType)).contains("Anthropic");
    assertThat(choiceValues(providerType)).contains("anthropic");

    // both backend entries appear with their expected labels
    final JsonNode backendType = findProperty(properties, "provider.anthropic.backend.type");
    assertThat(backendType.get("type").asText()).isEqualTo("Dropdown");
    assertThat(backendType.get("value").asText()).isEqualTo("anthropic-api");
    assertThat(choiceNamesByValue(backendType, "anthropic-api")).isEqualTo("Anthropic API");
    assertThat(choiceNamesByValue(backendType, "compatible"))
        .isEqualTo("Custom / compatible endpoint");

    // the api field is present but hidden, since it is single-valued
    final JsonNode apiField = findProperty(properties, "provider.anthropic.api");
    assertThat(apiField.get("type").asText()).isEqualTo("Hidden");
    assertThat(apiField.get("value").asText()).isEqualTo("messages");
  }

  private JsonNode findProperty(JsonNode properties, String id) {
    return StreamSupport.stream(properties.spliterator(), false)
        .filter(property -> id.equals(property.path("id").asText()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No property with id '" + id + "' found"));
  }

  private List<String> choiceNames(JsonNode property) {
    return StreamSupport.stream(property.get("choices").spliterator(), false)
        .map(choice -> choice.get("name").asText())
        .toList();
  }

  private List<String> choiceValues(JsonNode property) {
    return StreamSupport.stream(property.get("choices").spliterator(), false)
        .map(choice -> choice.get("value").asText())
        .toList();
  }

  private String choiceNamesByValue(JsonNode property, String value) {
    return StreamSupport.stream(property.get("choices").spliterator(), false)
        .filter(choice -> value.equals(choice.get("value").asText()))
        .map(choice -> choice.get("name").asText())
        .findFirst()
        .orElseThrow(() -> new AssertionError("No choice with value '" + value + "' found"));
  }
}
