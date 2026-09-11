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
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Guards configuration-template details that Hub consumes from the generated v2 artifacts. */
class ReusableCredentialElementTemplateTest {

  private static final ObjectMapper OBJECT_MAPPER = ConnectorsObjectMapperSupplier.getCopy();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "element-templates/agenticai-ai-agent-task.v2.json",
        "element-templates/agenticai-ai-agent-subprocess.v2.json",
        "element-templates/hybrid/agenticai-ai-agent-task.v2-hybrid.json",
        "element-templates/hybrid/agenticai-ai-agent-subprocess.v2-hybrid.json"
      })
  void marksSecretsInReusableCredentials(String templatePath) throws Exception {
    JsonNode template = OBJECT_MAPPER.readTree(Path.of(templatePath).toFile());

    assertSecret(
        template, "io.camunda:agentic-ai-microsoft-foundry-credential:1", "authentication.apiKey");
    assertSecret(
        template,
        "io.camunda:agentic-ai-microsoft-foundry-credential:1",
        "authentication.clientSecret");
    assertSecret(
        template, "io.camunda:agentic-ai-vertex-ai-credential:1", "authentication.jsonKey");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "element-templates/agenticai-ai-agent-task.v2.json",
        "element-templates/agenticai-ai-agent-subprocess.v2.json",
        "element-templates/hybrid/agenticai-ai-agent-task.v2-hybrid.json",
        "element-templates/hybrid/agenticai-ai-agent-subprocess.v2-hybrid.json"
      })
  void exposesBedrockCredentialFamiliesWithOneApplicableChooser(String templatePath)
      throws Exception {
    JsonNode template = OBJECT_MAPPER.readTree(Path.of(templatePath).toFile());
    List<JsonNode> properties = template.path("properties").valueStream().toList();

    assertAuthenticationFamily(properties, "provider.bedrock.authentication");
    assertAuthenticationFamily(
        properties, "provider.anthropic.backend.awsBedrockMantle.authentication");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "element-templates/agenticai-ai-agent-task.v2.json",
        "element-templates/agenticai-ai-agent-subprocess.v2.json",
        "element-templates/hybrid/agenticai-ai-agent-task.v2-hybrid.json",
        "element-templates/hybrid/agenticai-ai-agent-subprocess.v2-hybrid.json"
      })
  void requiresCredentialsWithoutInlineProviderFallbacks(String templatePath) throws Exception {
    JsonNode template = OBJECT_MAPPER.readTree(Path.of(templatePath).toFile());
    List<JsonNode> properties = template.path("properties").valueStream().toList();
    List<JsonNode> credentials =
        properties.stream()
            .filter(property -> "Configuration".equals(property.path("type").asText()))
            .toList();

    assertThat(credentials)
        .hasSize(11)
        .allSatisfy(
            credential -> {
              assertThat(credential.path("constraints").path("notEmpty").asBoolean()).isTrue();
              assertThat(credential.path("optional").asBoolean()).isFalse();
              assertThat(credential.path("condition").toString())
                  .doesNotContain("isEmpty", "inlineAuthentication");
            });

    List<String> fallbackPaths =
        List.of(
            "provider.anthropic.backend.anthropic.apiKey",
            "provider.anthropic.backend.custom.authentication",
            "provider.anthropic.backend.awsBedrockMantle.authentication.inlineAuthentication",
            "provider.anthropic.backend.awsBedrockMantle.authentication.apiKey",
            "provider.bedrock.authentication.inlineAuthentication",
            "provider.bedrock.authentication.apiKey",
            "provider.openai.backend.openai.apiKey",
            "provider.openai.backend.openai.organizationId",
            "provider.openai.backend.openai.projectId",
            "provider.openai.backend.foundry.endpoint",
            "provider.openai.backend.foundry.authentication",
            "provider.openai.backend.custom.authentication",
            "provider.googleGemini.backend.googleGeminiApi.apiKey",
            "provider.googleGemini.backend.googleVertexAi.projectId",
            "provider.googleGemini.backend.googleVertexAi.region",
            "provider.googleGemini.backend.googleVertexAi.authentication");
    assertThat(properties)
        .extracting(property -> property.path("binding").path("name").asText())
        .noneMatch(
            binding ->
                fallbackPaths.stream()
                    .anyMatch(path -> binding.equals(path) || binding.startsWith(path + ".")));

    JsonNode gateway = property(properties, "provider.anthropic.backend.custom.credential");
    assertThat(gateway.path("condition").toString()).doesNotContain("authentication.type");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "element-templates/agenticai-ai-agent-task.v2.json",
        "element-templates/agenticai-ai-agent-subprocess.v2.json",
        "element-templates/hybrid/agenticai-ai-agent-task.v2-hybrid.json",
        "element-templates/hybrid/agenticai-ai-agent-subprocess.v2-hybrid.json"
      })
  void retainsOptionalConnectionOverrides(String templatePath) throws Exception {
    List<JsonNode> properties =
        OBJECT_MAPPER
            .readTree(Path.of(templatePath).toFile())
            .path("properties")
            .valueStream()
            .toList();

    for (String id :
        List.of(
            "provider.anthropic.backend.custom.endpoint",
            "provider.openai.backend.custom.endpoint",
            "provider.anthropic.backend.awsBedrockMantle.region",
            "provider.bedrock.region")) {
      JsonNode override = property(properties, id);
      assertThat(override.path("optional").asBoolean()).isTrue();
      assertThat(override.path("constraints").path("notEmpty").asBoolean()).isFalse();
    }
  }

  private static void assertAuthenticationFamily(List<JsonNode> properties, String prefix) {
    JsonNode family =
        properties.stream()
            .filter(property -> (prefix + ".type").equals(property.path("id").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(
            family
                .path("choices")
                .valueStream()
                .map(choice -> choice.path("value").asText())
                .collect(java.util.stream.Collectors.toSet()))
        .isEqualTo(Set.of("awsIam", "bedrockApiKey"));

    JsonNode awsCredential = property(properties, prefix + ".awsCredential");
    JsonNode apiKeyCredential = property(properties, prefix + ".bedrockApiKeyCredential");
    assertThat(awsCredential.path("type").asText()).isEqualTo("Configuration");
    assertThat(apiKeyCredential.path("type").asText()).isEqualTo("Configuration");
    assertThat(awsCredential.path("condition").toString()).contains("\"equals\":\"awsIam\"");
    assertThat(apiKeyCredential.path("condition").toString())
        .contains("\"equals\":\"bedrockApiKey\"");
    assertThat(awsCredential.path("constraints").path("notEmpty").asBoolean()).isTrue();
    assertThat(apiKeyCredential.path("constraints").path("notEmpty").asBoolean()).isTrue();
  }

  private static void assertSecret(JsonNode template, String configurationId, String bindingName) {
    List<JsonNode> configurations = template.path("configurationTemplates").valueStream().toList();
    JsonNode configuration =
        configurations.stream()
            .filter(candidate -> configurationId.equals(candidate.path("id").asText()))
            .findFirst()
            .orElseThrow();
    JsonNode property =
        configuration
            .path("properties")
            .valueStream()
            .filter(
                candidate -> bindingName.equals(candidate.path("binding").path("name").asText()))
            .findFirst()
            .orElseThrow();

    assertThat(property.path("secret").asBoolean()).isTrue();
  }

  private static JsonNode property(List<JsonNode> properties, String id) {
    return properties.stream()
        .filter(candidate -> id.equals(candidate.path("id").asText()))
        .findFirst()
        .orElseThrow();
  }
}
