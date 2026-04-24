/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.OpenAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.OpenAiModel.OpenAiModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.AzureAuthentication.AzureApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.AzureAuthentication.AzureClientCredentialsAuthentication;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AzureFoundryProviderConfigurationDeserializationTest {

  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

  @Nested
  class AnthropicFamily {

    @Test
    void roundtrips_with_api_key_auth() throws Exception {
      String json =
          """
          {
            "type": "azureAiFoundry",
            "azureAiFoundry": {
              "endpoint": "https://example.services.ai.azure.com",
              "authentication": {"type": "apiKey", "apiKey": "k"},
              "model": {
                "family": "anthropic",
                "deploymentName": "claude-sonnet-4-6",
                "parameters": {"maxTokens": 1024, "temperature": 0.7, "topP": 0.9, "topK": 40}
              }
            }
          }
          """;

      ProviderConfiguration parsed = mapper.readValue(json, ProviderConfiguration.class);

      assertThat(parsed).isInstanceOf(AzureFoundryProviderConfiguration.class);
      var conn = ((AzureFoundryProviderConfiguration) parsed).azureAiFoundry();
      assertThat(conn.endpoint()).isEqualTo("https://example.services.ai.azure.com");
      assertThat(conn.authentication()).isInstanceOf(AzureApiKeyAuthentication.class);
      assertThat(conn.model()).isInstanceOf(AnthropicModel.class);
      AnthropicModel anthropic = (AnthropicModel) conn.model();
      assertThat(anthropic.deploymentName()).isEqualTo("claude-sonnet-4-6");
      assertThat(anthropic.parameters())
          .isEqualTo(new AnthropicModelParameters(1024, 0.7, 0.9, 40));
    }

    @Test
    void roundtrips_with_client_credentials_auth() throws Exception {
      String json =
          """
          {
            "type": "azureAiFoundry",
            "azureAiFoundry": {
              "endpoint": "https://example.services.ai.azure.com",
              "authentication": {
                "type": "clientCredentials",
                "clientId": "c",
                "clientSecret": "s",
                "tenantId": "t",
                "authorityHost": "https://login.microsoftonline.com"
              },
              "model": {
                "family": "anthropic",
                "deploymentName": "claude-sonnet-4-6",
                "parameters": {}
              }
            }
          }
          """;

      AzureFoundryProviderConfiguration parsed =
          (AzureFoundryProviderConfiguration) mapper.readValue(json, ProviderConfiguration.class);

      assertThat(parsed.azureAiFoundry().authentication())
          .isInstanceOf(AzureClientCredentialsAuthentication.class);
    }
  }

  @Nested
  class OpenAiFamily {

    @Test
    void roundtrips() throws Exception {
      String json =
          """
          {
            "type": "azureAiFoundry",
            "azureAiFoundry": {
              "endpoint": "https://example.services.ai.azure.com",
              "authentication": {"type": "apiKey", "apiKey": "k"},
              "model": {
                "family": "openai",
                "deploymentName": "gpt-4o",
                "parameters": {"maxTokens": 2048, "temperature": 1.0, "topP": 0.95}
              }
            }
          }
          """;

      AzureFoundryProviderConfiguration parsed =
          (AzureFoundryProviderConfiguration) mapper.readValue(json, ProviderConfiguration.class);

      assertThat(parsed.azureAiFoundry().model()).isInstanceOf(OpenAiModel.class);
      OpenAiModel openai = (OpenAiModel) parsed.azureAiFoundry().model();
      assertThat(openai.deploymentName()).isEqualTo("gpt-4o");
      assertThat(openai.parameters()).isEqualTo(new OpenAiModelParameters(2048, 1.0, 0.95));
    }
  }

  @Nested
  class LegacyAzureOpenAiCompat {

    @Test
    void pre_refactor_azure_openai_json_still_deserializes() throws Exception {
      // Legacy BPMN process definitions use "type": "azureOpenAi" — must still resolve to
      // the existing provider class post-M1 extraction of AzureAuthentication.
      String json =
          """
          {
            "type": "azureOpenAi",
            "azureOpenAi": {
              "endpoint": "https://legacy.openai.azure.com",
              "authentication": {"type": "apiKey", "apiKey": "k"},
              "model": {"deploymentName": "gpt-4o"}
            }
          }
          """;

      ProviderConfiguration parsed = mapper.readValue(json, ProviderConfiguration.class);

      assertThat(parsed).isInstanceOf(AzureOpenAiProviderConfiguration.class);
    }
  }
}
