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
import org.junit.jupiter.api.Test;

class AiAgentV2TemplateGenerationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path TEMPLATES = Path.of("element-templates");

  @Test
  void taskV2TemplateExistsWithProviderDiscriminatorAndCapabilityOverride() throws Exception {
    final Path file = TEMPLATES.resolve("agenticai-ai-agent-task.v2.json");
    assertThat(Files.exists(file)).isTrue();
    final JsonNode json = MAPPER.readTree(Files.readString(file));

    assertThat(json.get("id").asText())
        .isEqualTo("io.camunda.connectors.agenticai.ai-agent-task.v2");

    final var propsText = json.get("properties").toString();
    // provider discriminator dropdown with anthropic + openai
    assertThat(propsText).contains("anthropic").contains("openai");
    // backend + auth discriminators
    assertThat(propsText).contains("bedrock").contains("compatible");
    // capability override FEEL property is present
    assertThat(propsText).contains("Model capability overrides");
  }

  @Test
  void subprocessV2TemplateExistsWithSubprocessTypeAndId() throws Exception {
    final Path file = TEMPLATES.resolve("agenticai-ai-agent-subprocess.v2.json");
    assertThat(Files.exists(file)).isTrue();
    final JsonNode json = MAPPER.readTree(Files.readString(file));

    assertThat(json.get("id").asText())
        .isEqualTo("io.camunda.connectors.agenticai.ai-agent-subprocess.v2");
    assertThat(json.at("/appliesTo/0").asText()).isEqualTo("bpmn:SubProcess");
    assertThat(json.toString()).contains("io.camunda.agenticai:aiagent:subprocess:2");
  }
}
