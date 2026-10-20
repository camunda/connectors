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
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class LinkedSystemPromptElementTemplateTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final List<Path> TEMPLATE_PATHS =
      List.of(
          Path.of("element-templates/agenticai-ai-agent-task.v2.json"),
          Path.of("element-templates/hybrid/agenticai-ai-agent-task.v2-hybrid.json"));

  @ParameterizedTest(name = "{0}")
  @MethodSource("templatePaths")
  void exposesInlineAndLinkedSystemPromptControls(Path templatePath) throws IOException {
    final var template = OBJECT_MAPPER.readTree(templatePath.toFile());
    final var properties = properties(template);
    final var instructionSource = property(properties, "instructionSource");
    final var inlinePrompt = property(properties, "data.systemPrompt.prompt");
    final var resourceType = linkedResourceProperty(properties, "systemPrompt", "resourceType");
    final var resourceId = property(properties, "systemPrompt.resourceId");
    final var bindingType = property(properties, "systemPrompt.bindingType");
    final var versionTag = property(properties, "systemPrompt.versionTag");

    assertThat(template.path("version").asInt()).isEqualTo(2);
    assertThat(instructionSource.path("label").asText()).isEqualTo("Type");
    assertThat(instructionSource.path("value").asText()).isEqualTo("inline");
    assertThat(instructionSource.path("binding").path("type").asText())
        .isEqualTo("zeebe:taskHeader");
    assertThat(instructionSource.path("binding").path("key").asText())
        .isEqualTo("instructionSource");
    assertThat(instructionSource.path("choices"))
        .extracting(choice -> choice.path("value").asText())
        .containsExactly("inline", "resource");

    assertThat(inlinePrompt.path("label").asText()).isEqualTo("Prompt");
    assertThat(inlinePrompt.path("condition").path("property").asText())
        .isEqualTo("instructionSource");
    assertThat(inlinePrompt.path("condition").path("equals").asText()).isEqualTo("inline");

    assertThat(resourceType.path("value").asText()).isEqualTo("system-prompt");
    assertLinkedCondition(resourceType);
    assertThat(resourceId.path("label").asText()).isEqualTo("Prompt ID");
    assertThat(resourceId.path("constraints").path("notEmpty").asBoolean()).isTrue();
    assertLinkedCondition(resourceId);
    assertThat(bindingType.path("label").asText()).isEqualTo("Binding");
    assertThat(bindingType.path("value").asText()).isEqualTo("latest");
    assertThat(bindingType.path("choices"))
        .extracting(choice -> choice.path("value").asText())
        .containsExactly("latest", "deployment", "versionTag");
    assertLinkedCondition(bindingType);
    assertThat(
            properties.stream()
                .filter(
                    property ->
                        "zeebe:taskHeader".equals(property.path("binding").path("type").asText())
                            && "systemPromptBinding"
                                .equals(property.path("binding").path("key").asText()))
                .map(property -> property.path("value").asText()))
        .containsExactly("latest", "deployment", "versionTag");
    assertThat(versionTag.path("condition").path("allMatch"))
        .extracting(
            condition ->
                condition.path("property").asText() + "=" + condition.path("equals").asText())
        .containsExactly("instructionSource=resource", "systemPrompt.bindingType=versionTag");

    assertThat(properties.indexOf(instructionSource)).isLessThan(properties.indexOf(inlinePrompt));
    assertThat(properties.indexOf(inlinePrompt)).isLessThan(properties.indexOf(resourceType));
    assertThat(properties.indexOf(resourceType)).isLessThan(properties.indexOf(resourceId));
    assertThat(properties.indexOf(resourceId)).isLessThan(properties.indexOf(bindingType));
    assertThat(properties.indexOf(bindingType)).isLessThan(properties.indexOf(versionTag));
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

  private static JsonNode linkedResourceProperty(
      List<JsonNode> properties, String linkName, String bindingProperty) {
    return properties.stream()
        .filter(
            property ->
                "zeebe:linkedResource".equals(property.path("binding").path("type").asText())
                    && linkName.equals(property.path("binding").path("linkName").asText())
                    && bindingProperty.equals(property.path("binding").path("property").asText()))
        .findFirst()
        .orElseThrow();
  }

  private static void assertLinkedCondition(JsonNode property) {
    final var condition = property.path("condition").path("allMatch").get(0);
    assertThat(condition.path("property").asText()).isEqualTo("instructionSource");
    assertThat(condition.path("equals").asText()).isEqualTo("resource");
  }
}
