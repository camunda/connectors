/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiModel.GeminiModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiModel.GeminiThinking;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiModel.GeminiThinkingLevel;
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
class GeminiChatModelConfigurationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Autowired private Validator validator;

  @Test
  void deserialisesGeminiApiBackendWithModelParametersAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "google-gemini",
          "googleGemini": {
            "backend": { "type": "google-gemini-api", "googleGeminiApi": { "apiKey": "gm-123" } },
            "model": {
              "model": "gemini-3-pro-preview",
              "parameters": {
                "maxTokens": 1024,
                "temperature": 0.5,
                "topP": 0.9,
                "topK": 40,
                "thinking": { "enabled": true, "thinkingLevel": "high" }
              }
            }
          }
        }
        """;

    final ProviderConfiguration parsed = mapper.readValue(json, ProviderConfiguration.class);

    assertThat(parsed).isInstanceOf(GeminiChatModelConfiguration.class);
    assertThat(parsed.provider()).isEqualTo("google-gemini");
    assertThat(parsed.model()).isEqualTo("gemini-3-pro-preview");

    final GeminiChatModelConfiguration gemini = (GeminiChatModelConfiguration) parsed;
    assertThat(gemini.googleGemini().backend()).isInstanceOf(GeminiApiBackend.class);
    assertThat(((GeminiApiBackend) gemini.googleGemini().backend()).googleGeminiApi().apiKey())
        .isEqualTo("gm-123");
    final GeminiModelParameters parameters = gemini.googleGemini().model().parameters();
    assertThat(parameters).isNotNull();
    assertThat(parameters.maxTokens()).isEqualTo(1024);
    assertThat(parameters.temperature()).isEqualTo(0.5);
    assertThat(parameters.topP()).isEqualTo(0.9);
    assertThat(parameters.topK()).isEqualTo(40);
    assertThat(parameters.thinking())
        .isEqualTo(new GeminiThinking(true, null, GeminiThinkingLevel.HIGH));

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void googleGeminiApiRedactsApiKeyInToString() {
    final var backend =
        new GeminiApiBackend(
            new GeminiApiBackend.GoogleGeminiApi("gm-super-secret", "https://example.com"));

    final String toString = backend.toString();
    assertThat(toString).doesNotContain("gm-super-secret");
    assertThat(toString).contains("apiKey=[REDACTED]", "endpoint=https://example.com");
  }

  @Test
  void geminiApiBackendRejectsBlankApiKey() {
    final var config =
        new GeminiChatModelConfiguration(
            new GeminiConnection(
                new GeminiApiBackend(new GeminiApiBackend.GoogleGeminiApi("  ", null)),
                new GeminiModel("gemini-3-pro-preview", null),
                null));

    final var violations = validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("googleGemini.backend.googleGeminiApi.apiKey");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            });
  }

  @Test
  void bothThinkingBudgetAndLevelSetIsRejected() {
    final var thinking = new GeminiThinking(true, 1024, GeminiThinkingLevel.HIGH);
    final var parameters = new GeminiModelParameters(null, null, null, null, thinking);
    final var config =
        new GeminiChatModelConfiguration(
            new GeminiConnection(
                new GeminiApiBackend(new GeminiApiBackend.GoogleGeminiApi("gm-123", null)),
                new GeminiModel("gemini-3-pro-preview", parameters),
                null));

    final Set<ConstraintViolation<GeminiChatModelConfiguration>> violations =
        validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v ->
                assertThat(v.getMessage())
                    .isEqualTo(
                        "thinking.thinkingBudget and thinking.thinkingLevel are mutually"
                            + " exclusive"));
  }

  @Test
  void onlyThinkingBudgetSetHasNoViolations() {
    final var thinking = new GeminiThinking(true, 1024, null);
    final var parameters = new GeminiModelParameters(null, null, null, null, thinking);
    final var config =
        new GeminiChatModelConfiguration(
            new GeminiConnection(
                new GeminiApiBackend(new GeminiApiBackend.GoogleGeminiApi("gm-123", null)),
                new GeminiModel("gemini-3-pro-preview", parameters),
                null));

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void onlyThinkingLevelSetHasNoViolations() {
    final var thinking = new GeminiThinking(true, null, GeminiThinkingLevel.LOW);
    final var parameters = new GeminiModelParameters(null, null, null, null, thinking);
    final var config =
        new GeminiChatModelConfiguration(
            new GeminiConnection(
                new GeminiApiBackend(new GeminiApiBackend.GoogleGeminiApi("gm-123", null)),
                new GeminiModel("gemini-3-pro-preview", parameters),
                null));

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void budgetWithModelDefaultLevelHasNoViolations() {
    final var thinking = new GeminiThinking(true, 1024, GeminiThinkingLevel.MODEL_DEFAULT);
    final var parameters = new GeminiModelParameters(null, null, null, null, thinking);
    final var config =
        new GeminiChatModelConfiguration(
            new GeminiConnection(
                new GeminiApiBackend(new GeminiApiBackend.GoogleGeminiApi("gm-123", null)),
                new GeminiModel("gemini-3-pro-preview", parameters),
                null));

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void omittedThinkingLevelDefaultsToModelDefault() {
    final var thinking = new GeminiThinking(true, 1024, null);

    assertThat(thinking.thinkingLevel()).isEqualTo(GeminiThinkingLevel.MODEL_DEFAULT);
  }

  @Test
  void validGeminiConfigurationHasNoViolations() {
    final var config =
        new GeminiChatModelConfiguration(
            new GeminiConnection(
                new GeminiApiBackend(new GeminiApiBackend.GoogleGeminiApi("gm-123", null)),
                new GeminiModel("gemini-3-pro-preview", null),
                null));

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void requiredModelFieldIsEnforced() {
    final var config =
        new GeminiChatModelConfiguration(
            new GeminiConnection(
                new GeminiApiBackend(new GeminiApiBackend.GoogleGeminiApi("gm-123", null)),
                new GeminiModel("  ", null),
                null));

    final var violations = validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString()).isEqualTo("googleGemini.model.model");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            });
  }
}
