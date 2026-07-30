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
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicCompatibleBackend;
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
            "backend": { "type": "anthropic-api", "apiKey": "sk-ant-123" },
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
    assertThat(((AnthropicApiBackend) anthropic.anthropic().backend()).apiKey())
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
  void deserialisesCompatibleBackendWithApiKeyAuthAndHeadersAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "anthropic",
          "anthropic": {
            "backend": {
              "type": "compatible",
              "endpoint": "https://compatible.example.com",
              "headers": { "X-Custom-Header": "value" },
              "compatibleAuthentication": { "type": "apiKey", "apiKey": "sk-compat-123" }
            },
            "model": { "model": "claude-sonnet-4-6" }
          }
        }
        """;

    final AnthropicChatModelConfiguration parsed =
        (AnthropicChatModelConfiguration) mapper.readValue(json, ProviderConfiguration.class);

    assertThat(parsed.anthropic().backend()).isInstanceOf(AnthropicCompatibleBackend.class);

    final AnthropicCompatibleBackend compatible =
        (AnthropicCompatibleBackend) parsed.anthropic().backend();
    assertThat(compatible.endpoint()).isEqualTo("https://compatible.example.com");
    assertThat(compatible.headers()).containsEntry("X-Custom-Header", "value");
    assertThat(compatible.compatibleAuthentication())
        .isEqualTo(new ApiKeyAuthentication("sk-compat-123"));

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void anthropicApiBackendRedactsApiKeyInToString() {
    final var backend = new AnthropicApiBackend("sk-ant-super-secret");

    assertThat(backend.toString()).doesNotContain("sk-ant-super-secret").contains("[REDACTED]");
  }

  @Test
  void anthropicApiBackendRejectsBlankApiKey() {
    final var config =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(
                new AnthropicApiBackend("  "),
                null,
                new AnthropicModel("claude-sonnet-4-6", null),
                null));

    final var violations = validator.validate(config);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("apiKey"));
  }

  @Test
  void thinkingBudgetTokensRejectsValuesBelowMinimum() {
    final var thinking = new AnthropicThinking(ThinkingMode.ENABLED, 512, null);
    final var parameters =
        new AnthropicModelParameters(null, thinking, null, null, null, null, null);
    final var config =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(
                new AnthropicApiBackend("sk-ant-123"),
                null,
                new AnthropicModel("claude-sonnet-4-6", parameters),
                null));

    final Set<ConstraintViolation<AnthropicChatModelConfiguration>> violations =
        validator.validate(config);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("budgetTokens"));
  }

  @Test
  void validAnthropicConfigurationHasNoViolations() {
    final var config =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(
                new AnthropicApiBackend("sk-ant-123"),
                null,
                new AnthropicModel("claude-sonnet-4-6", null),
                null));

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void requiredCompatibleFieldsAreEnforced() {
    final var config =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(
                new AnthropicCompatibleBackend(
                    "", null, null, null, new ApiKeyAuthentication("  ")),
                null,
                new AnthropicModel("claude-sonnet-4-6", null),
                null));

    final var violations = validator.validate(config);

    assertThat(violations)
        .anyMatch(v -> v.getPropertyPath().toString().contains("endpoint"))
        .anyMatch(v -> v.getPropertyPath().toString().contains("apiKey"));
  }
}
