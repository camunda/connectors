/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicCustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel.AnthropicEffort;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel.AnthropicThinking;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel.ThinkingMode;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.CustomEndpointAuthentication.ApiKeyAuthentication;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@Import(ValidationAutoConfiguration.class)
class AnthropicChatModelConfigurationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Autowired private Validator validator;

  @Test
  void deserialisesAnthropicApiBackendWithReasoningAndCachingAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "anthropic",
          "anthropic": {
            "backend": { "type": "anthropic-api", "anthropic": { "apiKey": "sk-ant-123" } },
            "model": {
              "model": "claude-sonnet-4-6",
              "parameters": {
                "maxTokens": 1024,
                "effort": "high",
                "thinking": { "mode": "enabled", "budgetTokens": 2048 },
                "promptCaching": { "enabled": true }
              }
            }
          }
        }
        """;

    final ProviderConfiguration parsed = mapper.readValue(json, ProviderConfiguration.class);

    assertThat(parsed).isInstanceOf(AnthropicChatModelConfiguration.class);
    assertThat(parsed.provider()).isEqualTo("anthropic");
    assertThat(parsed.model()).isEqualTo("claude-sonnet-4-6");

    final AnthropicChatModelConfiguration anthropic = (AnthropicChatModelConfiguration) parsed;
    assertThat(anthropic.anthropic().backend()).isInstanceOf(AnthropicApiBackend.class);
    assertThat(((AnthropicApiBackend) anthropic.anthropic().backend()).anthropic().apiKey())
        .isEqualTo("sk-ant-123");
    final AnthropicModelParameters parameters = anthropic.anthropic().model().parameters();
    assertThat(parameters).isNotNull();
    assertThat(parameters.promptCaching().enabled()).isTrue();
    assertThat(parameters.maxTokens()).isEqualTo(1024);
    assertThat(parameters.effort()).isEqualTo(AnthropicEffort.HIGH);
    assertThat(parameters.thinking())
        .isEqualTo(new AnthropicThinking(ThinkingMode.ENABLED, 2048, null));

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void deserialisesCustomBackendWithApiKeyAuthAndHeadersAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "anthropic",
          "anthropic": {
            "backend": {
              "type": "custom",
              "custom": {
                "endpoint": "https://custom.example.com",
                "headers": { "X-Custom-Header": "value" },
                "authentication": { "type": "apiKey", "apiKey": "sk-custom-123" }
              }
            },
            "model": { "model": "claude-sonnet-4-6" }
          }
        }
        """;

    final AnthropicChatModelConfiguration parsed =
        (AnthropicChatModelConfiguration) mapper.readValue(json, ProviderConfiguration.class);

    assertThat(parsed.anthropic().backend()).isInstanceOf(AnthropicCustomBackend.class);

    final AnthropicCustomBackend custom = (AnthropicCustomBackend) parsed.anthropic().backend();
    assertThat(custom.custom().endpoint()).isEqualTo("https://custom.example.com");
    assertThat(custom.custom().headers()).containsEntry("X-Custom-Header", "value");
    assertThat(custom.custom().authentication())
        .isEqualTo(new ApiKeyAuthentication("sk-custom-123"));

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void anthropicApiBackendRedactsApiKeyInToString() {
    final var backend =
        new AnthropicApiBackend(new AnthropicApiBackend.AnthropicApi("sk-ant-super-secret", null));

    assertThat(backend.toString()).doesNotContain("sk-ant-super-secret").contains("[REDACTED]");
  }

  @Test
  void anthropicApiBackendRejectsBlankApiKey() {
    final var config =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(
                new AnthropicApiBackend(new AnthropicApiBackend.AnthropicApi("  ", null)),
                new AnthropicModel("claude-sonnet-4-6", null),
                null));

    final var violations = validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("anthropic.backend.anthropic.apiKey");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            });
  }

  @Test
  void thinkingBudgetTokensRejectsValuesBelowMinimum() {
    final var thinking = new AnthropicThinking(ThinkingMode.ENABLED, 512, null);
    final var parameters =
        new AnthropicModelParameters(null, thinking, null, null, null, null, null);
    final var config =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(
                new AnthropicApiBackend(new AnthropicApiBackend.AnthropicApi("sk-ant-123", null)),
                new AnthropicModel("claude-sonnet-4-6", parameters),
                null));

    final Set<ConstraintViolation<AnthropicChatModelConfiguration>> violations =
        validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("anthropic.model.parameters.thinking.budgetTokens");
              assertThat(v.getMessage()).isEqualTo("must be greater than or equal to 1024");
            });
  }

  @Test
  void validAnthropicConfigurationHasNoViolations() {
    final var config =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(
                new AnthropicApiBackend(new AnthropicApiBackend.AnthropicApi("sk-ant-123", null)),
                new AnthropicModel("claude-sonnet-4-6", null),
                null));

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void requiredCustomFieldsAreEnforced() {
    final var config =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(
                new AnthropicCustomBackend(
                    new AnthropicCustomBackend.CustomBackend(
                        "", null, null, null, new ApiKeyAuthentication("  "))),
                new AnthropicModel("claude-sonnet-4-6", null),
                null));

    final var violations = validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("anthropic.backend.custom.endpoint");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            })
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("anthropic.backend.custom.authentication.apiKey");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            });
  }
}
