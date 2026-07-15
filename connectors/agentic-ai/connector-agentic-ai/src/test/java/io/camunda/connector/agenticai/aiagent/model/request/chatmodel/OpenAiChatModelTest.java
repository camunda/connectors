/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.chatmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilitiesOverride;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.CompatibleAuthentication.CompatibleApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.CompatibleAuthentication.CompatibleNoAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.OpenAiBackend.OpenAiCompatibleBackend;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.OpenAiBackend.OpenAiDirectBackend;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.OpenAiModel;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiChatModelTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void deserialisesDirectCompletionsAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "openai",
          "openai": {
            "apiFamily": "completions",
            "backend": { "type": "direct", "apiKey": "sk-oai", "organizationId": "org-1" },
            "model": { "model": "gpt-5.4", "parameters": { "temperature": 0.2 } }
          }
        }
        """;

    final LlmProviderConfiguration parsed = mapper.readValue(json, LlmProviderConfiguration.class);

    assertThat(parsed).isInstanceOf(OpenAiChatModel.class);
    final OpenAiChatModel openai = (OpenAiChatModel) parsed;
    assertThat(openai.providerType()).isEqualTo("openai");
    assertThat(openai.model()).isEqualTo("gpt-5.4");
    assertThat(openai.backend()).isEqualTo("direct");
    assertThat(openai.openai().apiFamily()).isEqualTo(OpenAiApiFamily.COMPLETIONS);
    assertThat(openai.apiFamilyKey()).isEqualTo("openai-completions");
    assertThat(openai.openai().backend()).isInstanceOf(OpenAiDirectBackend.class);

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, LlmProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void responsesFamilyMapsToResponsesKey() throws Exception {
    final String json =
        """
        {
          "type": "openai",
          "openai": {
            "apiFamily": "responses",
            "backend": { "type": "direct", "apiKey": "sk-oai" },
            "model": { "model": "gpt-5.4" }
          }
        }
        """;
    final OpenAiChatModel openai =
        (OpenAiChatModel) mapper.readValue(json, LlmProviderConfiguration.class);
    assertThat(openai.openai().apiFamily()).isEqualTo(OpenAiApiFamily.RESPONSES);
    assertThat(openai.apiFamilyKey()).isEqualTo("openai-responses");
  }

  @Test
  void deserialisesCompatibleBackendWithApiKeyAuthAndCustomSurface() throws Exception {
    final String json =
        """
        {
          "type": "openai",
          "openai": {
            "apiFamily": "completions",
            "backend": {
              "type": "compatible",
              "endpoint": "https://gateway.example.com/v1",
              "headers": { "X-Tenant": "acme" },
              "queryParameters": { "api-version": "2024-10" },
              "requestParameters": { "seed": 7 },
              "authentication": { "type": "apiKey", "apiKey": "compat-secret" }
            },
            "model": { "model": "custom-model" }
          }
        }
        """;

    final OpenAiChatModel openai =
        (OpenAiChatModel) mapper.readValue(json, LlmProviderConfiguration.class);

    assertThat(openai.backend()).isEqualTo("compatible");
    final OpenAiCompatibleBackend compatible = (OpenAiCompatibleBackend) openai.openai().backend();
    assertThat(compatible.endpoint()).isEqualTo("https://gateway.example.com/v1");
    assertThat(compatible.headers()).containsEntry("X-Tenant", "acme");
    assertThat(compatible.queryParameters()).containsEntry("api-version", "2024-10");
    assertThat(compatible.requestParameters()).containsEntry("seed", 7);
    assertThat(compatible.authentication()).isInstanceOf(CompatibleApiKeyAuthentication.class);
    assertThat(compatible.authentication().toString()).doesNotContain("compat-secret");
  }

  @Test
  void compatibleBackendSupportsNoAuth() throws Exception {
    final String json =
        """
        {
          "type": "openai",
          "openai": {
            "apiFamily": "completions",
            "backend": {
              "type": "compatible",
              "endpoint": "https://gateway.example.com/v1",
              "authentication": { "type": "none" }
            },
            "model": { "model": "custom-model" }
          }
        }
        """;
    final OpenAiChatModel openai =
        (OpenAiChatModel) mapper.readValue(json, LlmProviderConfiguration.class);
    final OpenAiCompatibleBackend compatible = (OpenAiCompatibleBackend) openai.openai().backend();
    assertThat(compatible.authentication()).isInstanceOf(CompatibleNoAuthentication.class);
  }

  @Test
  void directBackendRejectsBlankApiKey() {
    final var model =
        new OpenAiChatModel(
            new OpenAiConnection(
                OpenAiApiFamily.COMPLETIONS,
                new OpenAiDirectBackend("   ", null, null),
                new OpenAiModel("gpt-5.4", null),
                null,
                null,
                null,
                null));
    assertThat(validator.validate(model))
        .anyMatch(v -> v.getPropertyPath().toString().contains("apiKey"));
  }

  @Test
  void validCompatibleModelHasNoViolations() {
    final var model =
        new OpenAiChatModel(
            new OpenAiConnection(
                OpenAiApiFamily.RESPONSES,
                new OpenAiCompatibleBackend(
                    "https://gateway.example.com/v1",
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    new CompatibleNoAuthentication()),
                new OpenAiModel("custom-model", null),
                null,
                null,
                null,
                null));
    assertThat(validator.validate(model)).isEmpty();
  }

  @Test
  void deserialisesPopulatedCapabilityOverrideAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "openai",
          "openai": {
            "apiFamily": "completions",
            "backend": { "type": "direct", "apiKey": "sk-oai" },
            "model": { "model": "gpt-5.4" },
            "capabilityOverride": {
              "userMessageModalities": ["text", "image"],
              "contextWindow": 4242
            }
          }
        }
        """;

    final OpenAiChatModel parsed =
        (OpenAiChatModel) mapper.readValue(json, LlmProviderConfiguration.class);

    final ModelCapabilitiesOverride override = parsed.capabilityOverride();
    assertThat(override).isNotNull();
    assertThat(override.userMessageModalities()).containsExactly(Modality.TEXT, Modality.IMAGE);
    assertThat(override.contextWindow()).isEqualTo(4242);
    assertThat(override.maxOutputTokens()).isNull();

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, LlmProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void directBackendRedactsApiKeyInToString() {
    final var direct = new OpenAiDirectBackend("sk-oai-super-secret", null, null);

    assertThat(direct.toString()).doesNotContain("sk-oai-super-secret").contains("[REDACTED]");
  }

  @Test
  void deserializesEffortAndServerToolToggles() throws Exception {
    var json =
        """
        {
          "type": "openai",
          "openai": {
            "apiFamily": "responses",
            "backend": { "type": "direct", "apiKey": "k" },
            "model": { "model": "gpt-5", "parameters": { "effort": "high" } },
            "enableWebSearch": true,
            "enableCodeInterpreter": true
          }
        }
        """;
    var model = (OpenAiChatModel) mapper.readValue(json, LlmProviderConfiguration.class);
    assertThat(model.openai().model().parameters().effort()).isEqualTo(OpenAiEffort.HIGH);
    assertThat(model.openai().enableWebSearch()).isTrue();
    assertThat(model.openai().enableCodeInterpreter()).isTrue();
  }

  @Test
  void effortJsonValueIsLowercase() throws Exception {
    assertThat(mapper.writeValueAsString(OpenAiEffort.MINIMAL)).isEqualTo("\"minimal\"");
    assertThat(mapper.writeValueAsString(OpenAiEffort.XHIGH)).isEqualTo("\"xhigh\"");
    assertThat(mapper.writeValueAsString(OpenAiEffort.MAX)).isEqualTo("\"max\"");
  }
}
