/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicAuthentication.AnthropicApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicBackend;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleGenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleGenAiProviderConfiguration.GoogleBackend;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.ApiFamily;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiAuthentication.OpenAiApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import io.camunda.connector.agenticai.util.TestObjectMapperSupplier;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link
 * io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfigurationDeserializer}.
 *
 * <p>Covers every row of the ADR 005 migration table:
 *
 * <ol>
 *   <li>Old bedrock + Anthropic model → AnthropicProviderConfiguration(backend=bedrock)
 *   <li>Old bedrock + Amazon model → BedrockProviderConfiguration (passthrough)
 *   <li>Old googleVertexAi → GoogleGenAiProviderConfiguration(backend=vertex)
 *   <li>anthropic (no backend) → backend=direct injected
 *   <li>anthropic (no auth type) → auth.type=apiKey injected
 *   <li>openai (no backend) → backend=openai, apiFamily=completions injected
 *   <li>Old azureOpenAi → openai/foundry with auth + endpoint mapping
 *   <li>Old openaiCompatible → openai/custom with auth discriminator injected
 *   <li>Idempotence: deserialize old → serialize → deserialize → same result
 *   <li>ConnectorsObjectMapper round-trip: no collision with document/FEEL modules
 * </ol>
 */
class ProviderConfigurationDeserializerTest {

  // Use the ConnectorsObjectMapper configuration (ignores unknown properties, handles
  // Java time types) — required so that round-trip serialization of records whose
  // @AssertFalse boolean methods become JSON properties does not fail on re-read.
  private static final ObjectMapper MAPPER = TestObjectMapperSupplier.getInstance();

  // ─────────────────────────────────────────────────────────────────────────────
  // Rule 1: old bedrock + Anthropic model → anthropic/bedrock
  // ─────────────────────────────────────────────────────────────────────────────

  @Test
  void rule1_oldBedrockWithAnthropicModel_becomesAnthropicBackendBedrock() throws Exception {
    var json =
        """
        {
          "type": "bedrock",
          "bedrock": {
            "region": "us-east-1",
            "authentication": { "type": "credentials", "accessKey": "ak", "secretKey": "sk" },
            "model": { "model": "anthropic.claude-sonnet-4-5" }
          }
        }
        """;

    var result = MAPPER.readValue(json, ProviderConfiguration.class);

    assertThat(result).isInstanceOf(AnthropicProviderConfiguration.class);
    var anthropic = (AnthropicProviderConfiguration) result;
    assertThat(anthropic.anthropic().backend()).isEqualTo(AnthropicBackend.BEDROCK);
    assertThat(anthropic.anthropic().model().model()).isEqualTo("anthropic.claude-sonnet-4-5");
  }

  @Test
  void rule1_oldBedrockWithAnthropicCrossRegionModelPrefix_becomesAnthropicBackendBedrock()
      throws Exception {
    var json =
        """
        {
          "type": "bedrock",
          "bedrock": {
            "region": "us-east-1",
            "authentication": { "type": "defaultCredentialsChain" },
            "model": { "model": "anthropic.claude-3-haiku-20240307-v1:0" }
          }
        }
        """;

    var result = MAPPER.readValue(json, ProviderConfiguration.class);

    assertThat(result).isInstanceOf(AnthropicProviderConfiguration.class);
    var anthropic = (AnthropicProviderConfiguration) result;
    assertThat(anthropic.anthropic().backend()).isEqualTo(AnthropicBackend.BEDROCK);
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Rule 2: old bedrock + non-Anthropic model → passthrough
  // ─────────────────────────────────────────────────────────────────────────────

  @Test
  void rule2_oldBedrockWithAmazonModel_passthrough() throws Exception {
    var json =
        """
        {
          "type": "bedrock",
          "bedrock": {
            "region": "us-east-1",
            "authentication": { "type": "credentials", "accessKey": "ak", "secretKey": "sk" },
            "model": { "model": "amazon.nova-pro-v1:0" }
          }
        }
        """;

    var result = MAPPER.readValue(json, ProviderConfiguration.class);

    assertThat(result).isInstanceOf(BedrockProviderConfiguration.class);
    var bedrock = (BedrockProviderConfiguration) result;
    assertThat(bedrock.bedrock().model().model()).isEqualTo("amazon.nova-pro-v1:0");
  }

  @Test
  void rule2_oldBedrockWithMetaModel_passthrough() throws Exception {
    var json =
        """
        {
          "type": "bedrock",
          "bedrock": {
            "region": "eu-west-1",
            "authentication": { "type": "credentials", "accessKey": "ak", "secretKey": "sk" },
            "model": { "model": "meta.llama3-8b-instruct-v1:0" }
          }
        }
        """;

    var result = MAPPER.readValue(json, ProviderConfiguration.class);

    assertThat(result).isInstanceOf(BedrockProviderConfiguration.class);
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Rule 3: googleVertexAi → googleGenAi/vertex
  // ─────────────────────────────────────────────────────────────────────────────

  @Test
  void rule3_oldGoogleVertexAi_becomesGoogleGenAiBackendVertex() throws Exception {
    var json =
        """
        {
          "type": "googleVertexAi",
          "googleVertexAi": {
            "projectId": "my-project",
            "region": "us-central1",
            "authentication": { "type": "serviceAccountCredentials", "jsonKey": "{}" },
            "model": { "model": "gemini-1.5-flash" }
          }
        }
        """;

    var result = MAPPER.readValue(json, ProviderConfiguration.class);

    assertThat(result).isInstanceOf(GoogleGenAiProviderConfiguration.class);
    var google = (GoogleGenAiProviderConfiguration) result;
    assertThat(google.googleGenAi().backend()).isEqualTo(GoogleBackend.VERTEX);
    assertThat(google.googleGenAi().projectId()).isEqualTo("my-project");
    assertThat(google.googleGenAi().region()).isEqualTo("us-central1");
    assertThat(google.googleGenAi().model().model()).isEqualTo("gemini-1.5-flash");
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Rule 4: anthropic without backend → inject backend=direct
  // ─────────────────────────────────────────────────────────────────────────────

  @Test
  void rule4_anthropicWithoutBackend_injectsBackendDirect() throws Exception {
    var json =
        """
        {
          "type": "anthropic",
          "anthropic": {
            "authentication": { "type": "apiKey", "apiKey": "my-key" },
            "model": { "model": "claude-sonnet-4-6" }
          }
        }
        """;

    var result = MAPPER.readValue(json, ProviderConfiguration.class);

    assertThat(result).isInstanceOf(AnthropicProviderConfiguration.class);
    var anthropic = (AnthropicProviderConfiguration) result;
    assertThat(anthropic.anthropic().backend()).isEqualTo(AnthropicBackend.DIRECT);
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Rule 5: anthropic with authentication but missing type discriminator
  // ─────────────────────────────────────────────────────────────────────────────

  @Test
  void rule5_anthropicWithoutAuthType_injectsApiKeyDiscriminator() throws Exception {
    var json =
        """
        {
          "type": "anthropic",
          "anthropic": {
            "backend": "direct",
            "authentication": { "apiKey": "my-secret-key" },
            "model": { "model": "claude-sonnet-4-6" }
          }
        }
        """;

    var result = MAPPER.readValue(json, ProviderConfiguration.class);

    assertThat(result).isInstanceOf(AnthropicProviderConfiguration.class);
    var anthropic = (AnthropicProviderConfiguration) result;
    assertThat(anthropic.anthropic().authentication())
        .isInstanceOf(AnthropicApiKeyAuthentication.class);
    assertThat(((AnthropicApiKeyAuthentication) anthropic.anthropic().authentication()).apiKey())
        .isEqualTo("my-secret-key");
  }

  @Test
  void rule4and5_anthropicWithoutBackendOrAuthType_injectsBoth() throws Exception {
    var json =
        """
        {
          "type": "anthropic",
          "anthropic": {
            "authentication": { "apiKey": "combined-key" },
            "model": { "model": "claude-haiku-4-5" }
          }
        }
        """;

    var result = MAPPER.readValue(json, ProviderConfiguration.class);

    assertThat(result).isInstanceOf(AnthropicProviderConfiguration.class);
    var anthropic = (AnthropicProviderConfiguration) result;
    assertThat(anthropic.anthropic().backend()).isEqualTo(AnthropicBackend.DIRECT);
    assertThat(anthropic.anthropic().authentication())
        .isInstanceOf(AnthropicApiKeyAuthentication.class);
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Rule 6: openai without backend/apiFamily → inject defaults
  // ─────────────────────────────────────────────────────────────────────────────

  @Test
  void rule6_openaiWithoutBackend_injectsBackendOpenaiAndApiFamilyCompletions() throws Exception {
    var json =
        """
        {
          "type": "openai",
          "openai": {
            "authentication": { "type": "apiKey", "apiKey": "sk-test" },
            "model": { "model": "gpt-4o" }
          }
        }
        """;

    var result = MAPPER.readValue(json, ProviderConfiguration.class);

    assertThat(result).isInstanceOf(OpenAiProviderConfiguration.class);
    var openai = (OpenAiProviderConfiguration) result;
    assertThat(openai.openai().backend()).isEqualTo(OpenAiBackend.OPENAI);
    assertThat(openai.openai().apiFamily()).isEqualTo(ApiFamily.COMPLETIONS);
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Rule 7: azureOpenAi → openai/foundry
  // ─────────────────────────────────────────────────────────────────────────────

  @Test
  void rule7_azureOpenAiWithApiKey_becomesOpenAiFoundryWithFieldMapping() throws Exception {
    var json =
        """
        {
          "type": "azureOpenAi",
          "azureOpenAi": {
            "endpoint": "https://my-azure.openai.azure.com",
            "authentication": { "type": "apiKey", "apiKey": "azure-api-key" },
            "model": { "deploymentName": "gpt-4o-deployment", "parameters": { "maxCompletionTokens": 1000 } }
          }
        }
        """;

    var result = MAPPER.readValue(json, ProviderConfiguration.class);

    assertThat(result).isInstanceOf(OpenAiProviderConfiguration.class);
    var openai = (OpenAiProviderConfiguration) result;
    assertThat(openai.openai().backend()).isEqualTo(OpenAiBackend.FOUNDRY);
    assertThat(openai.openai().apiFamily()).isEqualTo(ApiFamily.COMPLETIONS);
    assertThat(openai.openai().endpoint()).isEqualTo("https://my-azure.openai.azure.com");
    assertThat(openai.openai().authentication()).isInstanceOf(OpenAiApiKeyAuthentication.class);
    assertThat(((OpenAiApiKeyAuthentication) openai.openai().authentication()).apiKey())
        .isEqualTo("azure-api-key");
    // deploymentName maps to model.model
    assertThat(openai.openai().model().model()).isEqualTo("gpt-4o-deployment");
    assertThat(openai.openai().model().parameters().maxCompletionTokens()).isEqualTo(1000);
  }

  @Test
  void rule7_azureOpenAiWithClientCredentials_becomesOpenAiFoundry() throws Exception {
    var json =
        """
        {
          "type": "azureOpenAi",
          "azureOpenAi": {
            "endpoint": "https://my-azure.openai.azure.com",
            "authentication": {
              "type": "clientCredentials",
              "clientId": "cid",
              "clientSecret": "csecret",
              "tenantId": "tid"
            },
            "model": { "deploymentName": "gpt-4o-turbo" }
          }
        }
        """;

    var result = MAPPER.readValue(json, ProviderConfiguration.class);

    assertThat(result).isInstanceOf(OpenAiProviderConfiguration.class);
    var openai = (OpenAiProviderConfiguration) result;
    assertThat(openai.openai().backend()).isEqualTo(OpenAiBackend.FOUNDRY);
    assertThat(openai.openai().authentication())
        .isInstanceOf(
            OpenAiProviderConfiguration.OpenAiAuthentication.OpenAiClientCredentialsAuthentication
                .class);
    assertThat(openai.openai().model().model()).isEqualTo("gpt-4o-turbo");
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Rule 8: openaiCompatible → openai/custom
  // ─────────────────────────────────────────────────────────────────────────────

  @Test
  void rule8_openaiCompatible_becomesOpenAiCustomWithAuthDiscriminator() throws Exception {
    var json =
        """
        {
          "type": "openaiCompatible",
          "openaiCompatible": {
            "endpoint": "https://my-ollama.local/v1",
            "authentication": { "apiKey": "ollama-key" },
            "headers": { "X-Custom": "value" },
            "queryParameters": { "version": "v2" },
            "model": { "model": "llama3" }
          }
        }
        """;

    var result = MAPPER.readValue(json, ProviderConfiguration.class);

    assertThat(result).isInstanceOf(OpenAiProviderConfiguration.class);
    var openai = (OpenAiProviderConfiguration) result;
    assertThat(openai.openai().backend()).isEqualTo(OpenAiBackend.CUSTOM);
    assertThat(openai.openai().apiFamily()).isEqualTo(ApiFamily.COMPLETIONS);
    assertThat(openai.openai().endpoint()).isEqualTo("https://my-ollama.local/v1");
    assertThat(openai.openai().authentication()).isInstanceOf(OpenAiApiKeyAuthentication.class);
    assertThat(((OpenAiApiKeyAuthentication) openai.openai().authentication()).apiKey())
        .isEqualTo("ollama-key");
    assertThat(openai.openai().headers()).containsEntry("X-Custom", "value");
    assertThat(openai.openai().queryParameters()).containsEntry("version", "v2");
    assertThat(openai.openai().model().model()).isEqualTo("llama3");
  }

  @Test
  void rule8_openaiCompatibleWithoutAuth_becomesOpenAiCustomWithNullableApiKey() throws Exception {
    var json =
        """
        {
          "type": "openaiCompatible",
          "openaiCompatible": {
            "endpoint": "https://my-vllm.local/v1",
            "model": { "model": "mistral-7b" }
          }
        }
        """;

    var result = MAPPER.readValue(json, ProviderConfiguration.class);

    assertThat(result).isInstanceOf(OpenAiProviderConfiguration.class);
    var openai = (OpenAiProviderConfiguration) result;
    assertThat(openai.openai().backend()).isEqualTo(OpenAiBackend.CUSTOM);
    assertThat(openai.openai().model().model()).isEqualTo("mistral-7b");
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Rule 9: Idempotence — deserialize old → serialize → deserialize → same result
  // ─────────────────────────────────────────────────────────────────────────────

  @Test
  void idempotence_oldBedrockAnthropicModel_roundTripProducesSameResult() throws Exception {
    var oldJson =
        """
        {
          "type": "bedrock",
          "bedrock": {
            "region": "us-east-1",
            "authentication": { "type": "credentials", "accessKey": "ak", "secretKey": "sk" },
            "model": { "model": "anthropic.claude-sonnet-4-5" }
          }
        }
        """;

    var first = MAPPER.readValue(oldJson, ProviderConfiguration.class);
    var serialized = MAPPER.writeValueAsString(first);
    var second = MAPPER.readValue(serialized, ProviderConfiguration.class);

    assertThat(second).isInstanceOf(AnthropicProviderConfiguration.class);
    assertThat(second).usingRecursiveComparison().isEqualTo(first);
  }

  @Test
  void idempotence_azureOpenAi_roundTripProducesSameResult() throws Exception {
    var oldJson =
        """
        {
          "type": "azureOpenAi",
          "azureOpenAi": {
            "endpoint": "https://my-azure.openai.azure.com",
            "authentication": { "type": "apiKey", "apiKey": "azure-api-key" },
            "model": { "deploymentName": "gpt-4o-deployment" }
          }
        }
        """;

    var first = MAPPER.readValue(oldJson, ProviderConfiguration.class);
    var serialized = MAPPER.writeValueAsString(first);
    var second = MAPPER.readValue(serialized, ProviderConfiguration.class);

    assertThat(second).isInstanceOf(OpenAiProviderConfiguration.class);
    assertThat(second).usingRecursiveComparison().isEqualTo(first);
  }

  @Test
  void idempotence_openaiCompatible_roundTripProducesSameResult() throws Exception {
    var oldJson =
        """
        {
          "type": "openaiCompatible",
          "openaiCompatible": {
            "endpoint": "https://my-ollama.local/v1",
            "authentication": { "apiKey": "ollama-key" },
            "model": { "model": "llama3" }
          }
        }
        """;

    var first = MAPPER.readValue(oldJson, ProviderConfiguration.class);
    var serialized = MAPPER.writeValueAsString(first);
    var second = MAPPER.readValue(serialized, ProviderConfiguration.class);

    assertThat(second).isInstanceOf(OpenAiProviderConfiguration.class);
    assertThat(second).usingRecursiveComparison().isEqualTo(first);
  }

  @Test
  void idempotence_googleVertexAi_roundTripProducesSameResult() throws Exception {
    var oldJson =
        """
        {
          "type": "googleVertexAi",
          "googleVertexAi": {
            "projectId": "my-project",
            "region": "us-central1",
            "authentication": { "type": "serviceAccountCredentials", "jsonKey": "{}" },
            "model": { "model": "gemini-1.5-flash" }
          }
        }
        """;

    var first = MAPPER.readValue(oldJson, ProviderConfiguration.class);
    var serialized = MAPPER.writeValueAsString(first);
    var second = MAPPER.readValue(serialized, ProviderConfiguration.class);

    assertThat(second).isInstanceOf(GoogleGenAiProviderConfiguration.class);
    assertThat(second).usingRecursiveComparison().isEqualTo(first);
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Rule 10: ConnectorsObjectMapper round-trip — no collision with document/FEEL modules
  // ─────────────────────────────────────────────────────────────────────────────

  @Test
  void connectorsObjectMapper_roundTripAnthropicConfiguration_noCollisionWithDocumentOrFeelModules()
      throws Exception {
    var connectorsMapper = TestObjectMapperSupplier.getInstance();

    var original =
        new AnthropicProviderConfiguration(
            new AnthropicProviderConfiguration.AnthropicConnection(
                null,
                AnthropicBackend.DIRECT,
                new AnthropicApiKeyAuthentication("my-key"),
                null,
                new AnthropicProviderConfiguration.AnthropicModel("claude-sonnet-4-6", null)));

    var json = connectorsMapper.writeValueAsString(original);
    var deserialized = connectorsMapper.readValue(json, ProviderConfiguration.class);

    assertThat(deserialized).isInstanceOf(AnthropicProviderConfiguration.class);
    assertThat(deserialized).usingRecursiveComparison().isEqualTo(original);
  }

  @Test
  void connectorsObjectMapper_canDeserializeLegacyShapes_withFullModuleStack() throws Exception {
    var connectorsMapper = TestObjectMapperSupplier.getInstance();

    var json =
        """
        {
          "type": "azureOpenAi",
          "azureOpenAi": {
            "endpoint": "https://my-foundry.azure.com",
            "authentication": { "type": "apiKey", "apiKey": "foundry-key" },
            "model": { "deploymentName": "gpt-4o" }
          }
        }
        """;

    var result = connectorsMapper.readValue(json, ProviderConfiguration.class);

    assertThat(result).isInstanceOf(OpenAiProviderConfiguration.class);
    var openai = (OpenAiProviderConfiguration) result;
    assertThat(openai.openai().backend()).isEqualTo(OpenAiBackend.FOUNDRY);
    assertThat(openai.openai().model().model()).isEqualTo("gpt-4o");
  }
}
