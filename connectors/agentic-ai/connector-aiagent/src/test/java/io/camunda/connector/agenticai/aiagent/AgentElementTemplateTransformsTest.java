/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Asserts what the Groovy scripts in {@code bin/} inject into the generated AI Agent element
 * templates, so a change that breaks a transform fails the unit tests.
 */
class AgentElementTemplateTransformsTest {

  private static final String DEPRECATION_MESSAGE =
      "For Camunda 8.10+, please replace with the v2 version of the AI Agent element template. See documentation for details.";
  private static final String OUTPUT_ELEMENT_EXPRESSION =
      "={\n  id: toolCall._meta.id,\n  name: toolCall._meta.name,\n  content: toolCallResult,\n  completedAt: now()\n}";
  private static final String DEPRECATION_DOCUMENTATION_REF =
      "https://docs.camunda.io/docs/8.10/reference/announcements-release-notes/8100/8100-announcements/#agentic-orchestration";

  @ParameterizedTest
  @CsvSource({
    "agenticai-aiagent-outbound-connector.json,aiAgentTask",
    "hybrid/agenticai-aiagent-outbound-connector-hybrid.json,aiAgentTask",
    "agenticai-ai-agent-task.v2.json,aiAgentTask",
    "hybrid/agenticai-ai-agent-task.v2-hybrid.json,aiAgentTask",
    "agenticai-aiagent-job-worker.json,aiAgentSubProcess",
    "hybrid/agenticai-aiagent-job-worker-hybrid.json,aiAgentSubProcess",
    "agenticai-ai-agent-subprocess.v2.json,aiAgentSubProcess",
    "hybrid/agenticai-ai-agent-subprocess.v2-hybrid.json,aiAgentSubProcess"
  })
  void hasOneAgentDefinitionMarker(String file, String agentType) throws IOException {
    final var markers =
        properties(template(file)).stream()
            .filter(p -> p.path("binding").path("type").asText().equals("zeebe:agentDefinition"))
            .toList();
    assertThat(markers).hasSize(1);
    assertThat(markers.getFirst().get("value").asText()).isEqualTo(agentType);
    assertThat(markers.getFirst().get("binding").get("property").asText()).isEqualTo("agentType");
    assertThat(markers.getFirst().get("type").asText()).isEqualTo("Hidden");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "agenticai-aiagent-outbound-connector.json",
        "hybrid/agenticai-aiagent-outbound-connector-hybrid.json",
        "agenticai-aiagent-job-worker.json",
        "hybrid/agenticai-aiagent-job-worker-hybrid.json"
      })
  void v1TemplatesAreDeprecated(String file) throws IOException {
    final var template = template(file);
    assertThat(template.get("name").asText()).endsWith(" (Deprecated)");
    assertThat(template.get("deprecated").get("message").asText()).isEqualTo(DEPRECATION_MESSAGE);
    assertThat(template.get("deprecated").get("documentationRef").asText())
        .isEqualTo(DEPRECATION_DOCUMENTATION_REF);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "agenticai-ai-agent-task.v2.json",
        "hybrid/agenticai-ai-agent-task.v2-hybrid.json",
        "agenticai-ai-agent-subprocess.v2.json",
        "hybrid/agenticai-ai-agent-subprocess.v2-hybrid.json"
      })
  void v2TemplatesAreNotDeprecated(String file) throws IOException {
    final var template = template(file);
    assertThat(template.has("deprecated")).isFalse();
    assertThat(template.get("name").asText()).doesNotContain("Deprecated");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "agenticai-aiagent-outbound-connector.json",
        "hybrid/agenticai-aiagent-outbound-connector-hybrid.json",
        "agenticai-aiagent-job-worker.json",
        "hybrid/agenticai-aiagent-job-worker-hybrid.json"
      })
  void v1TemplatesUseLegacySystemPrompt(String file) throws IOException {
    final var legacyPrompt =
        Files.readString(Path.of("element-template-overrides", "v1-legacy-system-prompt.txt"))
            .stripTrailing();
    assertThat(
            property(properties(template(file)), "data.systemPrompt.prompt").get("value").asText())
        .isEqualTo("=\"" + legacyPrompt + "\"");
  }

  @ParameterizedTest
  @CsvSource({
    "agenticai-aiagent-job-worker.json,io.camunda.connectors.agenticai.aiagent.jobworker.v1,io.camunda.agenticai:aiagent-job-worker:1,AI Agent Sub-process (Deprecated)",
    "hybrid/agenticai-aiagent-job-worker-hybrid.json,io.camunda.connectors.agenticai.aiagent.jobworker.v1,io.camunda.agenticai:aiagent-job-worker:1,Hybrid AI Agent Sub-process (Deprecated)",
    "agenticai-ai-agent-subprocess.v2.json,io.camunda.connectors.agenticai.ai-agent-subprocess.v2,io.camunda.agenticai:aiagent:subprocess:2,AI Agent Sub-process",
    "hybrid/agenticai-ai-agent-subprocess.v2-hybrid.json,io.camunda.connectors.agenticai.ai-agent-subprocess.v2,io.camunda.agenticai:aiagent:subprocess:2,Hybrid AI Agent Sub-process"
  })
  void subProcessTemplateIsDerivedFromTaskTemplate(
      String file, String templateId, String connectorType, String name) throws IOException {
    final var template = template(file);
    final var hybrid = file.startsWith("hybrid/");
    assertThat(template.get("id").asText()).isEqualTo(hybrid ? templateId + "-hybrid" : templateId);
    assertThat(template.get("name").asText()).isEqualTo(name);
    assertThat(iterable(template.get("appliesTo")))
        .extracting(JsonNode::asText)
        .containsExactly("bpmn:SubProcess");
    assertThat(template.get("elementType").get("value").asText()).isEqualTo("bpmn:AdHocSubProcess");
    assertThat(template.toString()).doesNotContain("agentic-ai-aiagent-task/");

    assertThat(ids(iterable(template.get("groups")))).contains("events");

    final var properties = properties(template);
    assertThat(ids(properties))
        .doesNotContain(
            "data.tools.containerElementId",
            "data.tools.toolCallResults",
            "resultExpression",
            "data.agentContext")
        .contains("data.response.includeAgentContext");

    assertThat(taskDefinitionType(properties).get("value").asText()).isEqualTo(connectorType);

    final var outputCollection = property(properties, "outputCollection");
    assertThat(bindingType(outputCollection)).isEqualTo("zeebe:adHoc");
    assertThat(outputCollection.get("value").asText()).isEqualTo("toolCallResults");
    final var outputElement = property(properties, "outputElement");
    assertThat(bindingType(outputElement)).isEqualTo("zeebe:adHoc");
    assertThat(outputElement.get("value").asText()).isEqualTo(OUTPUT_ELEMENT_EXPRESSION);

    final var toolContainer =
        propertyWithBinding(properties, "zeebe:property", "io.camunda.agenticai.toolContainer");
    assertThat(toolContainer.get("value").asText()).isEqualTo("true");

    assertThat(property(properties, "id").get("value").asText()).isEqualTo(templateId);

    final var agentContext = property(properties, "agentContext");
    assertThat(agentContext.get("binding").get("name").asText()).isEqualTo("agentContext");
    assertThat(agentContext.get("optional").asBoolean()).isFalse();
    assertThat(agentContext.get("feel").asText()).isEqualTo("required");

    final var resultVariable = property(properties, "resultVariable");
    assertThat(bindingType(resultVariable)).isEqualTo("zeebe:output");
    assertThat(resultVariable.get("binding").get("source").asText()).isEqualTo("=agent");

    assertThat(property(properties, "data.events.behavior").get("group").asText())
        .isEqualTo("events");

    final var agentInput = propertyWithBinding(properties, "zeebe:input", "agent");
    // input mappings are evaluated in order: the empty agent input must not clear agent before
    // the agentContext input reads it
    assertThat(properties.indexOf(agentContext)).isLessThan(properties.indexOf(agentInput));
  }

  private static JsonNode taskDefinitionType(List<JsonNode> properties) {
    return properties.stream()
        .filter(p -> bindingType(p).equals("zeebe:taskDefinition"))
        .filter(p -> p.get("binding").path("property").asText().equals("type"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing zeebe:taskDefinition type property"));
  }

  private static JsonNode propertyWithBinding(
      List<JsonNode> properties, String bindingType, String bindingName) {
    return properties.stream()
        .filter(p -> bindingType(p).equals(bindingType))
        .filter(p -> p.get("binding").path("name").asText().equals(bindingName))
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("Missing " + bindingType + " property: " + bindingName));
  }

  private static String bindingType(JsonNode property) {
    return property.path("binding").path("type").asText();
  }

  private static JsonNode property(List<JsonNode> properties, String id) {
    return properties.stream()
        .filter(p -> p.path("id").asText().equals(id))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing property: " + id));
  }

  private static List<String> ids(List<JsonNode> entries) {
    return entries.stream().map(p -> p.path("id").asText(null)).toList();
  }

  private static List<JsonNode> properties(JsonNode template) {
    return iterable(template.get("properties"));
  }

  private static List<JsonNode> iterable(JsonNode array) {
    final var result = new ArrayList<JsonNode>();
    array.forEach(result::add);
    return result;
  }

  private static JsonNode template(String file) throws IOException {
    return ConnectorsObjectMapperSupplier.getCopy()
        .readTree(Path.of("element-templates", file).toFile());
  }
}
