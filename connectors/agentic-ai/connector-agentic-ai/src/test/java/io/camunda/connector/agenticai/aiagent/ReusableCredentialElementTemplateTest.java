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
import java.util.Map;
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
  void defaultsToOpenAiResponsesWithCustomEndpoint(String templatePath) throws Exception {
    List<JsonNode> properties =
        OBJECT_MAPPER
            .readTree(Path.of(templatePath).toFile())
            .path("properties")
            .valueStream()
            .toList();

    assertThat(property(properties, "provider.type").path("value").asText()).isEqualTo("openai");
    assertThat(property(properties, "provider.openai.api.type").path("value").asText())
        .isEqualTo("responses");
    assertThat(property(properties, "provider.openai.backend.type").path("value").asText())
        .isEqualTo("custom");
  }

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

    assertSecret(template, "io.camunda:agentic-ai-anthropic-api-credential:1", "apiKey");
    assertSecret(template, "io.camunda:agentic-ai-openai-api-credential:1", "apiKey");
    assertSecret(template, "io.camunda:agentic-ai-google-gemini-api-credential:1", "apiKey");
    assertSecret(template, "io.camunda:agentic-ai-bedrock-api-key-credential:1", "apiKey");
    assertSecret(
        template, "io.camunda:agentic-ai-microsoft-foundry-credential:1", "authentication.apiKey");
    assertSecret(
        template,
        "io.camunda:agentic-ai-microsoft-foundry-credential:1",
        "authentication.clientSecret");
    assertSecret(
        template, "io.camunda:agentic-ai-vertex-ai-credential:1", "authentication.jsonKey");
    assertSecret(template, "io.camunda:agentic-ai-gateway-credential:1", "authentication.apiKey");
    assertSecret(
        template, "io.camunda:agentic-ai-gateway-credential:1", "authentication.clientSecret");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "element-templates/agenticai-ai-agent-task.v2.json",
        "element-templates/agenticai-ai-agent-subprocess.v2.json",
        "element-templates/hybrid/agenticai-ai-agent-task.v2-hybrid.json",
        "element-templates/hybrid/agenticai-ai-agent-subprocess.v2-hybrid.json"
      })
  void marksInlineAuthenticationSecrets(String templatePath) throws Exception {
    List<JsonNode> properties =
        OBJECT_MAPPER
            .readTree(Path.of(templatePath).toFile())
            .path("properties")
            .valueStream()
            .toList();
    List<JsonNode> secrets =
        properties.stream()
            .filter(field -> field.path("id").asText().startsWith("provider."))
            .filter(
                field ->
                    List.of(".apiKey", ".accessKey", ".secretKey", ".clientSecret", ".jsonKey")
                        .stream()
                        .anyMatch(field.path("id").asText()::endsWith))
            .toList();

    assertThat(secrets)
        .hasSize(16)
        .allSatisfy(
            field ->
                assertThat(field.path("secret").asBoolean())
                    .as(field.path("id").asText())
                    .isTrue());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "element-templates/agenticai-ai-agent-task.v2.json",
        "element-templates/agenticai-ai-agent-subprocess.v2.json",
        "element-templates/hybrid/agenticai-ai-agent-task.v2-hybrid.json",
        "element-templates/hybrid/agenticai-ai-agent-subprocess.v2-hybrid.json"
      })
  void exposesApiKeyAndOAuthInsideTheGatewayCredential(String templatePath) throws Exception {
    JsonNode template = OBJECT_MAPPER.readTree(Path.of(templatePath).toFile());
    JsonNode gateway =
        template
            .path("configurationTemplates")
            .valueStream()
            .filter(
                configuration ->
                    "io.camunda:agentic-ai-gateway-credential:1"
                        .equals(configuration.path("id").asText()))
            .findFirst()
            .orElseThrow();
    List<JsonNode> properties = gateway.path("properties").valueStream().toList();
    JsonNode authentication = property(properties, "authentication.type");

    assertThat(authentication.path("value").asText()).isEqualTo("apiKey");
    assertThat(
            authentication
                .path("choices")
                .valueStream()
                .map(choice -> choice.path("value").asText())
                .toList())
        .containsExactlyInAnyOrder("apiKey", "oauth-client-credentials-flow");
    assertThat(property(properties, "authentication.apiKey").path("condition").toString())
        .contains("\"equals\":\"apiKey\"");
    for (String field :
        List.of(
            "oauthTokenEndpoint",
            "clientId",
            "clientSecret",
            "clientAuthentication",
            "audience",
            "scopes")) {
      assertThat(property(properties, "authentication." + field).path("condition").toString())
          .contains("\"equals\":\"oauth-client-credentials-flow\"");
    }
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
  void offersOptionalCredentialsWithConditionalInlineFallbacks(String templatePath)
      throws Exception {
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
              assertThat(credential.path("constraints").path("notEmpty").asBoolean()).isFalse();
              assertThat(credential.path("optional").asBoolean()).isTrue();
              assertThat(credential.path("condition").toString())
                  .doesNotContain("isEmpty", "inlineAuthentication");
            });

    Map<String, List<String>> fallbacks =
        Map.ofEntries(
            Map.entry("provider.anthropic.backend.anthropic.credential", List.of("apiKey")),
            Map.entry(
                "provider.anthropic.backend.custom.credential",
                List.of("authentication", "endpoint")),
            Map.entry(
                "provider.openai.backend.openai.credential",
                List.of("apiKey", "organizationId", "projectId")),
            Map.entry(
                "provider.openai.backend.foundry.credential",
                List.of("endpoint", "authentication")),
            Map.entry(
                "provider.openai.backend.custom.credential", List.of("endpoint", "authentication")),
            Map.entry(
                "provider.googleGemini.backend.googleGeminiApi.credential", List.of("apiKey")),
            Map.entry(
                "provider.googleGemini.backend.googleVertexAi.credential",
                List.of("projectId", "region", "authentication")),
            Map.entry(
                "provider.bedrock.authentication.awsCredential", List.of("inlineAuthentication")),
            Map.entry("provider.bedrock.authentication.bedrockApiKeyCredential", List.of("apiKey")),
            Map.entry(
                "provider.anthropic.backend.awsBedrockMantle.authentication.awsCredential",
                List.of("inlineAuthentication")),
            Map.entry(
                "provider.anthropic.backend.awsBedrockMantle.authentication.bedrockApiKeyCredential",
                List.of("apiKey")));
    fallbacks.forEach(
        (chooserId, paths) -> {
          String prefix = chooserId.substring(0, chooserId.lastIndexOf('.') + 1);
          for (String path : paths) {
            String id = prefix + path;
            List<JsonNode> fields =
                properties.stream()
                    .filter(
                        field ->
                            field.path("id").asText().equals(id)
                                || field.path("id").asText().startsWith(id + "."))
                    .toList();
            assertThat(fields)
                .as(id)
                .isNotEmpty()
                .allSatisfy(
                    field -> {
                      assertEmptyCondition(field, chooserId, true);
                      assertThat(properties.indexOf(property(properties, chooserId)))
                          .isLessThan(properties.indexOf(field));
                    });
          }
        });

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
  void requiresInlineConnectionsAndRetainsOptionalOverrides(String templatePath) throws Exception {
    List<JsonNode> properties =
        OBJECT_MAPPER
            .readTree(Path.of(templatePath).toFile())
            .path("properties")
            .valueStream()
            .toList();

    for (String prefix :
        List.of("provider.anthropic.backend.custom", "provider.openai.backend.custom")) {
      assertConnectionPair(
          properties, prefix + ".endpoint", prefix + ".endpointOverride", prefix + ".credential");
    }
    for (String prefix :
        List.of("provider.anthropic.backend.awsBedrockMantle", "provider.bedrock")) {
      assertConnectionPair(
          properties,
          prefix + ".region",
          prefix + ".iamRegionOverride",
          prefix + ".authentication.awsCredential");
      assertConnectionPair(
          properties,
          prefix + ".apiKeyRegion",
          prefix + ".apiKeyRegionOverride",
          prefix + ".authentication.bedrockApiKeyCredential");
      assertThat(
              property(properties, prefix + ".apiKeyRegion").path("binding").path("name").asText())
          .isEqualTo(prefix + ".region");
      for (String field : List.of("region", "iamRegionOverride")) {
        assertThat(property(properties, prefix + "." + field).path("condition").toString())
            .contains("\"equals\":\"awsIam\"");
      }
      for (String field : List.of("apiKeyRegion", "apiKeyRegionOverride")) {
        assertThat(property(properties, prefix + "." + field).path("condition").toString())
            .contains("\"equals\":\"bedrockApiKey\"");
      }
    }
  }

  private static void assertConnectionPair(
      List<JsonNode> properties, String inlineId, String overrideId, String chooserId) {
    JsonNode inline = property(properties, inlineId);
    JsonNode override = property(properties, overrideId);
    assertThat(inline.path("constraints").path("notEmpty").asBoolean()).isTrue();
    assertThat(override.path("constraints").path("notEmpty").asBoolean()).isFalse();
    assertThat(override.path("optional").asBoolean()).isTrue();
    assertThat(override.path("binding")).isEqualTo(inline.path("binding"));
    assertEmptyCondition(inline, chooserId, true);
    assertEmptyCondition(override, chooserId, false);
  }

  private static void assertEmptyCondition(JsonNode field, String chooserId, boolean empty) {
    assertThat(field.path("condition").path("allMatch").valueStream().toList())
        .anySatisfy(
            condition -> {
              assertThat(condition.path("property").asText()).isEqualTo(chooserId);
              assertThat(condition.path("isEmpty").isBoolean()).isTrue();
              assertThat(condition.path("isEmpty").asBoolean()).isEqualTo(empty);
            });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "element-templates/agenticai-ai-agent-task.v2.json",
        "element-templates/agenticai-ai-agent-subprocess.v2.json",
        "element-templates/hybrid/agenticai-ai-agent-task.v2-hybrid.json",
        "element-templates/hybrid/agenticai-ai-agent-subprocess.v2-hybrid.json"
      })
  void validatesCredentialEndpointsInGeneratedForms(String templatePath) throws Exception {
    JsonNode template = OBJECT_MAPPER.readTree(Path.of(templatePath).toFile());
    for (String credentialId :
        List.of(
            "io.camunda:agentic-ai-gateway-credential:1",
            "io.camunda:agentic-ai-microsoft-foundry-credential:1")) {
      JsonNode credential =
          template
              .path("configurationTemplates")
              .valueStream()
              .filter(candidate -> credentialId.equals(candidate.path("id").asText()))
              .findFirst()
              .orElseThrow();
      JsonNode endpoint =
          property(credential.path("properties").valueStream().toList(), "endpoint");
      JsonNode constraints = endpoint.path("constraints");

      assertThat(constraints.path("notEmpty").asBoolean()).isTrue();
      String pattern = constraints.path("pattern").path("value").asText();
      assertThat(pattern).isEqualTo("^https?://.+");
      assertThat("https://gateway.example/v1").matches(pattern);
      assertThat("http://localhost:8080").matches(pattern);
      assertThat("ftp://gateway.example").doesNotMatch(pattern);
      assertThat("gateway.example").doesNotMatch(pattern);
      assertThat("https://").doesNotMatch(pattern);
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
    assertThat(awsCredential.path("optional").asBoolean()).isTrue();
    assertThat(apiKeyCredential.path("optional").asBoolean()).isTrue();
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
