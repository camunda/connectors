/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AgentV2ElementTemplateTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "agenticai-ai-agent-task.v2.json",
        "agenticai-ai-agent-subprocess.v2.json",
        "hybrid/agenticai-ai-agent-task.v2-hybrid.json",
        "hybrid/agenticai-ai-agent-subprocess.v2-hybrid.json"
      })
  void providerStepsAndPresets(String file) throws IOException {
    final var template = template(file);
    final var task = template("agenticai-ai-agent-task.v2.json");
    assertThat(template.get("steps")).isEqualTo(task.get("steps"));
    assertThat(template.get("presets")).isEqualTo(task.get("presets"));
    assertThat(template.get("steps")).hasSize(6);
    assertThat(template.get("presets")).hasSize(16);

    for (var entry :
        Map.of(
                "Anthropic",
                List.of(
                    "provider.anthropic.backend.type",
                    "Anthropic API",
                    "AWS Bedrock Mantle",
                    "Microsoft Foundry (Azure)",
                    "Anthropic custom / compatible endpoint"),
                "OpenAI",
                List.of(
                    "provider.openai.backend.type",
                    "OpenAI API",
                    "Microsoft Foundry (Azure)",
                    "OpenAI custom / compatible endpoint",
                    "Ollama",
                    "LM Studio",
                    "Camunda-provided LLM (SaaS only)"),
                "Google Gemini",
                List.of(
                    "provider.googleGemini.backend.type",
                    "Google Gemini API",
                    "Enterprise Agent Platform (Vertex AI)"),
                "Mistral AI",
                List.of(
                    "provider.mistral.backend.type",
                    "Mistral API",
                    "Mistral custom / compatible endpoint"))
            .entrySet()) {
      final var group = named(template.get("steps"), "name", entry.getKey());
      assertThat(group.has("keywords")).isFalse();
      assertThat(group.has("presetId")).isFalse();
      final var backendKey = entry.getValue().getFirst();
      assertThat(group.get("steps").findValuesAsText("name"))
          .containsExactlyElementsOf(entry.getValue().subList(1, entry.getValue().size()));
      final var backend = named(template.get("properties"), "id", backendKey);
      final var choices = backend.get("choices");
      for (int i = 0; i < choices.size(); i++) {
        final var leaf = group.get("steps").get(i);
        final var preset = preset(template, leaf.get("presetId").asText());
        assertThat(preset.get(backendKey)).isEqualTo(choices.get(i).get("value"));
        assertThat(preset.get("provider.type")).isEqualTo(backend.get("condition").get("equals"));
        assertThat(leaf.has("description")).isFalse();
        assertThat(leaf.get("keywords")).isNotEmpty();
      }
    }

    for (var entry :
        Map.of("ollama", "http://localhost:11434/v1", "lmstudio", "http://localhost:1234/v1")
            .entrySet()) {
      assertThat(preset(template, "type_openai_type_custom_" + entry.getKey()))
          .isEqualTo(
              JsonNodeFactory.instance
                  .objectNode()
                  .put("provider.type", "openai")
                  .put("provider.openai.backend.type", "custom")
                  .put("provider.openai.api.type", "completions")
                  .put("provider.openai.backend.custom.endpoint", entry.getValue())
                  .put("provider.openai.backend.custom.authentication.type", "none"));
    }
    assertThat(preset(template, "type_openai_type_custom_camunda"))
        .isEqualTo(
            JsonNodeFactory.instance
                .objectNode()
                .put("provider.type", "openai")
                .put("provider.openai.backend.type", "custom")
                .put("provider.openai.api.type", "completions")
                .put(
                    "provider.openai.backend.custom.endpoint",
                    "=camunda.secrets.CAMUNDA_PROVIDED_LLM_API_ENDPOINT")
                .put("provider.openai.backend.custom.authentication.type", "apiKey")
                .put(
                    "provider.openai.backend.custom.authentication.apiKey",
                    "=camunda.secrets.CAMUNDA_PROVIDED_LLM_API_KEY")
                .put(
                    "provider.openai.model.model",
                    "=camunda.secrets.CAMUNDA_PROVIDED_LLM_DEFAULT_MODEL"));
    final var openai = named(template.get("steps"), "name", "OpenAI").get("steps");
    assertThat(named(openai, "name", "Ollama").get("presetId").asText())
        .isEqualTo("type_openai_type_custom_ollama");
    assertThat(named(openai, "name", "LM Studio").get("presetId").asText())
        .isEqualTo("type_openai_type_custom_lmstudio");
    assertThat(named(openai, "name", "Camunda-provided LLM (SaaS only)").get("presetId").asText())
        .isEqualTo("type_openai_type_custom_camunda");
    assertThat(preset(template, "type_bedrock").get("provider.type").asText()).isEqualTo("bedrock");
    assertThat(preset(template, "type_custom").get("provider.type").asText()).isEqualTo("custom");
  }

  private static JsonNode preset(JsonNode template, String id) {
    return named(template.get("presets"), "id", id).get("properties");
  }

  private static JsonNode named(JsonNode entries, String key, String value) {
    for (JsonNode entry : entries) {
      if (entry.path(key).asText().equals(value)) {
        return entry;
      }
    }
    throw new AssertionError("Missing " + key + ": " + value);
  }

  private static JsonNode template(String file) throws IOException {
    return ConnectorsObjectMapperSupplier.getCopy()
        .readTree(Path.of("element-templates", file).toFile());
  }
}
