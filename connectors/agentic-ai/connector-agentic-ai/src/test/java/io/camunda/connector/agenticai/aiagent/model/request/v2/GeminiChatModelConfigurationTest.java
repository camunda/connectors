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
                "thinking": { "thinkingLevel": "high" }
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
    assertThat(parameters.thinking()).isEqualTo(new GeminiThinking(null, GeminiThinkingLevel.HIGH));

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
    final var thinking = new GeminiThinking(1024, GeminiThinkingLevel.HIGH);
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
    final var thinking = new GeminiThinking(1024, null);
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
  void thinkingBudgetBelowMinusOneIsRejected() {
    final var thinking = new GeminiThinking(-2, null);
    final var parameters = new GeminiModelParameters(null, null, null, null, thinking);
    final var config =
        new GeminiChatModelConfiguration(
            new GeminiConnection(
                new GeminiApiBackend(new GeminiApiBackend.GoogleGeminiApi("gm-123", null)),
                new GeminiModel("gemini-3-pro-preview", parameters),
                null));

    final var violations = validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("googleGemini.model.parameters.thinking.thinkingBudget");
              assertThat(v.getMessage()).isEqualTo("must be greater than or equal to -1");
            });
  }

  @Test
  void thinkingBudgetOfMinusOneHasNoViolations() {
    final var thinking = new GeminiThinking(-1, null);
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
    final var thinking = new GeminiThinking(1024, GeminiThinkingLevel.MODEL_DEFAULT);
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
    final var thinking = new GeminiThinking(1024, null);

    assertThat(thinking.thinkingLevel()).isEqualTo(GeminiThinkingLevel.MODEL_DEFAULT);
  }

  @Test
  void onlyThinkingLevelSetHasNoViolations() {
    final var thinking = new GeminiThinking(null, GeminiThinkingLevel.LOW);
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

  @Test
  void deserialisesVertexAiBackendWithServiceAccountCredentialsAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "google-gemini",
          "googleGemini": {
            "backend": {
              "type": "google-vertex-ai",
              "googleVertexAi": {
                "projectId": "my-project",
                "region": "us-central1",
                "authentication": {
                  "type": "serviceAccountCredentials",
                  "jsonKey": "{\\"type\\":\\"service_account\\"}"
                }
              }
            },
            "model": { "model": "gemini-3-pro-preview" }
          }
        }
        """;

    final ProviderConfiguration parsed = mapper.readValue(json, ProviderConfiguration.class);

    assertThat(parsed).isInstanceOf(GeminiChatModelConfiguration.class);
    assertThat(parsed.provider()).isEqualTo("google-gemini");
    assertThat(parsed.model()).isEqualTo("gemini-3-pro-preview");

    final GeminiChatModelConfiguration gemini = (GeminiChatModelConfiguration) parsed;
    assertThat(gemini.googleGemini().backend()).isInstanceOf(GeminiVertexAiBackend.class);

    final GoogleVertexAi vertexAi =
        ((GeminiVertexAiBackend) gemini.googleGemini().backend()).googleVertexAi();
    assertThat(vertexAi.projectId()).isEqualTo("my-project");
    assertThat(vertexAi.region()).isEqualTo("us-central1");
    assertThat(vertexAi.endpoint()).isNull();
    assertThat(vertexAi.authentication())
        .isEqualTo(new ServiceAccountCredentialsAuthentication("{\"type\":\"service_account\"}"));

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void deserialisesVertexAiBackendWithApplicationDefaultCredentialsAndRoundTrips()
      throws Exception {
    final String json =
        """
        {
          "type": "google-gemini",
          "googleGemini": {
            "backend": {
              "type": "google-vertex-ai",
              "googleVertexAi": {
                "projectId": "my-project",
                "region": "us-central1",
                "endpoint": "https://example.com",
                "authentication": { "type": "applicationDefaultCredentials" }
              }
            },
            "model": { "model": "gemini-3-pro-preview" }
          }
        }
        """;

    final ProviderConfiguration parsed = mapper.readValue(json, ProviderConfiguration.class);
    final GeminiChatModelConfiguration gemini = (GeminiChatModelConfiguration) parsed;
    final GoogleVertexAi vertexAi =
        ((GeminiVertexAiBackend) gemini.googleGemini().backend()).googleVertexAi();

    assertThat(vertexAi.endpoint()).isEqualTo("https://example.com");
    assertThat(vertexAi.authentication())
        .isEqualTo(new ApplicationDefaultCredentialsAuthentication());

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void serviceAccountCredentialsAuthenticationRedactsJsonKeyInToString() {
    final var authentication = new ServiceAccountCredentialsAuthentication("super-secret-key");

    final String toString = authentication.toString();

    assertThat(toString).doesNotContain("super-secret-key");
    assertThat(toString).isEqualTo("ServiceAccountCredentialsAuthentication{jsonKey=[REDACTED]}");
  }

  @Test
  void vertexAiBackendRejectsBlankProjectIdAndRegion() {
    final var authentication = new ServiceAccountCredentialsAuthentication("key-json");
    final var backendWithBlanks =
        new GeminiVertexAiBackend(new GoogleVertexAi("  ", "  ", null, authentication));

    final var violations =
        validator.validate(
            new GeminiChatModelConfiguration(
                new GeminiConnection(
                    backendWithBlanks, new GeminiModel("gemini-3-pro-preview", null), null)));

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("googleGemini.backend.googleVertexAi.projectId");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            })
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("googleGemini.backend.googleVertexAi.region");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            });
  }

  @Test
  void vertexAiServiceAccountCredentialsRejectsBlankJsonKey() {
    final var config =
        vertexAiChatModelConfiguration(new ServiceAccountCredentialsAuthentication("  "));

    final var violations = validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("googleGemini.backend.googleVertexAi.authentication.jsonKey");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            });
  }

  @Test
  void vertexAiBackendRejectsMissingAuthentication() {
    final var config =
        new GeminiChatModelConfiguration(
            new GeminiConnection(
                new GeminiVertexAiBackend(
                    new GoogleVertexAi("my-project", "us-central1", null, null)),
                new GeminiModel("gemini-3-pro-preview", null),
                null));

    final var violations = validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("googleGemini.backend.googleVertexAi.authentication");
              assertThat(v.getMessage()).isEqualTo("must not be null");
            });
  }

  @Test
  void validVertexAiConfigurationHasNoViolations() {
    final var config =
        vertexAiChatModelConfiguration(new ServiceAccountCredentialsAuthentication("key-json"));

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void vertexAiApplicationDefaultCredentialsRejectedOnSaaS() {
    environment.set(ConnectorUtils.CONNECTOR_RUNTIME_SAAS_ENV_VARIABLE, "true");
    final var config =
        vertexAiChatModelConfiguration(new ApplicationDefaultCredentialsAuthentication());

    assertThat(validator.validate(config))
        .extracting(ConstraintViolation::getMessage)
        .contains("Application default credentials for Google Vertex AI are not supported on SaaS");
  }

  @Test
  void vertexAiApplicationDefaultCredentialsAllowedWhenNotSaaS() {
    final var config =
        vertexAiChatModelConfiguration(new ApplicationDefaultCredentialsAuthentication());

    assertThat(validator.validate(config)).isEmpty();
  }

  private static GoogleVertexAi vertexAiConfig(GoogleVertexAiAuthentication authentication) {
    return new GoogleVertexAi("my-project", "us-central1", null, authentication);
  }

  private static GeminiChatModelConfiguration vertexAiChatModelConfiguration(
      GoogleVertexAiAuthentication authentication) {
    return new GeminiChatModelConfiguration(
        new GeminiConnection(
            new GeminiVertexAiBackend(vertexAiConfig(authentication)),
            new GeminiModel("gemini-3-pro-preview", null),
            null));
  }
}
