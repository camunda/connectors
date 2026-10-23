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
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AgentV2ElementTemplateTest {

  @ParameterizedTest
  @CsvSource({
    "agenticai-ai-agent-task.v2.json,Agent task",
    "agenticai-ai-agent-subprocess.v2.json,Agent sub-process",
    "hybrid/agenticai-ai-agent-task.v2-hybrid.json,Agent task",
    "hybrid/agenticai-ai-agent-subprocess.v2-hybrid.json,Agent sub-process"
  })
  void providerStepsAndPresets(String file, String kind) throws IOException {
    final var tagged = template(file);
    final var template = untagLeafNames(tagged, kind + ": ");
    final var task = untagLeafNames(template("agenticai-ai-agent-task.v2.json"), "Agent task: ");
    assertThat(template.get("steps")).isEqualTo(task.get("steps"));
    assertThat(template.get("presets")).isEqualTo(task.get("presets"));
    final var leafPaths = new ArrayList<String>();
    collectLeafPaths(tagged.get("steps"), "", leafPaths);
    assertThat(leafPaths).hasSize(16).doesNotHaveDuplicates();
    final var providerNames =
        List.of(
            "Anthropic",
            "AWS Bedrock Converse",
            "OpenAI",
            "OpenAI compatible",
            "Google Gemini",
            "Mistral AI",
            "Custom Implementation (Self-Managed/Hybrid only)");
    assertThat(template.get("steps")).hasSize(providerNames.size());
    for (int i = 0; i < providerNames.size(); i++) {
      assertThat(template.get("steps").get(i).get("name").asText()).isEqualTo(providerNames.get(i));
    }
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
                    "OpenAI custom / compatible endpoint"),
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
    final var steps = template.get("steps");
    final var compatible = named(steps, "name", "OpenAI compatible");
    assertThat(compatible.has("presetId")).isFalse();
    assertThat(compatible.has("keywords")).isFalse();
    assertThat(compatible.get("description").asText())
        .isEqualTo("Models like Ollama, LM Studio, and Camunda-provided LLM.");
    assertThat(compatible.get("steps").findValuesAsText("name"))
        .containsExactly("Ollama", "LM Studio", "Camunda-provided LLM (SaaS only)");
    assertThat(named(compatible.get("steps"), "name", "Ollama").get("presetId").asText())
        .isEqualTo("type_openai_type_custom_ollama");
    assertThat(named(compatible.get("steps"), "name", "LM Studio").get("presetId").asText())
        .isEqualTo("type_openai_type_custom_lmstudio");
    assertThat(
            named(compatible.get("steps"), "name", "Camunda-provided LLM (SaaS only)")
                .get("presetId")
                .asText())
        .isEqualTo("type_openai_type_custom_camunda");
    assertThat(
            named(compatible.get("steps"), "name", "Camunda-provided LLM (SaaS only)")
                .get("description")
                .asText())
        .isEqualTo("Only on select plans.");
    assertThat(preset(template, "type_bedrock").get("provider.type").asText()).isEqualTo("bedrock");
    assertThat(preset(template, "type_custom").get("provider.type").asText()).isEqualTo("custom");
  }

  @ParameterizedTest
  @CsvSource({
    "agenticai-ai-agent-task.v2.json,ai-agent-task.v2.migrates-from.json,io.camunda.connectors.agenticai.aiagent.v1",
    "hybrid/agenticai-ai-agent-task.v2-hybrid.json,ai-agent-task.v2-hybrid.migrates-from.json,io.camunda.connectors.agenticai.aiagent.v1-hybrid",
    "agenticai-ai-agent-subprocess.v2.json,ai-agent-subprocess.v2.migrates-from.json,io.camunda.connectors.agenticai.aiagent.jobworker.v1",
    "hybrid/agenticai-ai-agent-subprocess.v2-hybrid.json,ai-agent-subprocess.v2-hybrid.migrates-from.json,io.camunda.connectors.agenticai.aiagent.jobworker.v1-hybrid"
  })
  void migratesFromRecipe(String file, String recipeFile, String sourceTemplateId)
      throws IOException {
    final var recipe =
        (ObjectNode)
            ConnectorsObjectMapperSupplier.getCopy()
                .readTree(Path.of("element-template-overrides", recipeFile).toFile());
    assertThat(recipe.has("$schema")).isTrue();
    recipe.remove("$schema");
    final var migratesFrom = template(file).path("metadata").path("migratesFrom");
    assertThat(migratesFrom).isEqualTo(recipe);
    assertThat(migratesFrom.has("$schema")).isFalse();
    assertThat(migratesFrom.get("sources")).hasSize(1);
    assertThat(migratesFrom.get("sources").get(0).get("sourceTemplateId").asText())
        .isEqualTo(sourceTemplateId);
  }

  private static JsonNode untagLeafNames(JsonNode template, String tag) {
    final var copy = template.deepCopy();
    untagSteps(copy.get("steps"), tag);
    return copy;
  }

  private static void untagSteps(JsonNode steps, String tag) {
    for (JsonNode step : steps) {
      if (step.has("presetId")) {
        final var name = step.get("name").asText();
        assertThat(name).startsWith(tag);
        ((ObjectNode) step).put("name", name.substring(tag.length()));
      } else {
        untagSteps(step.path("steps"), tag);
      }
    }
  }

  private static void collectLeafPaths(JsonNode steps, String parent, List<String> paths) {
    for (JsonNode step : steps) {
      final var name = step.get("name").asText();
      if (step.has("presetId")) {
        paths.add(parent + name);
      } else {
        collectLeafPaths(step.path("steps"), parent + name + " > ", paths);
      }
    }
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
