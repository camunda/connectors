/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.chatmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.anthropic.AnthropicEffort;
import io.camunda.connector.agenticai.aiagent.framework.anthropic.ThinkingMode;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilitiesOverride;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicBackend.AnthropicBedrockBackend;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicBackend.AnthropicDirectBackend;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicModel.AnthropicThinking;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.shared.ChatModelAwsAuthentication.AwsStaticCredentialsAuthentication;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AnthropicChatModelTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void deserialisesDirectBackendViaTypeDiscriminatorAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "anthropic",
          "anthropic": {
            "backend": { "type": "direct", "apiKey": "sk-ant-123" },
            "model": { "model": "claude-sonnet-4-6", "parameters": { "maxTokens": 1024 } }
          }
        }
        """;

    final LlmProviderConfiguration parsed = mapper.readValue(json, LlmProviderConfiguration.class);

    assertThat(parsed).isInstanceOf(AnthropicChatModel.class);
    assertThat(parsed.providerType()).isEqualTo("anthropic");
    assertThat(parsed.model()).isEqualTo("claude-sonnet-4-6");
    assertThat(parsed.backend()).isEqualTo("direct");
    assertThat(parsed.capabilityOverride()).isNull();

    final AnthropicChatModel anthropic = (AnthropicChatModel) parsed;
    assertThat(anthropic.backend()).isEqualTo("direct");
    assertThat(anthropic.anthropic().backend()).isInstanceOf(AnthropicDirectBackend.class);

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, LlmProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void deserialisesBedrockBackendWithStaticCredentials() throws Exception {
    final String json =
        """
        {
          "type": "anthropic",
          "anthropic": {
            "backend": {
              "type": "bedrock",
              "region": "eu-west-1",
              "authentication": { "type": "credentials", "accessKey": "AKIA", "secretKey": "shh" }
            },
            "model": { "model": "claude-sonnet-4-6" }
          }
        }
        """;

    final AnthropicChatModel parsed =
        (AnthropicChatModel) mapper.readValue(json, LlmProviderConfiguration.class);

    assertThat(parsed.backend()).isEqualTo("bedrock");
    assertThat(parsed.anthropic().backend()).isInstanceOf(AnthropicBedrockBackend.class);
    final AnthropicBedrockBackend bedrock = (AnthropicBedrockBackend) parsed.anthropic().backend();
    assertThat(bedrock.region()).isEqualTo("eu-west-1");
    assertThat(bedrock.authentication()).isInstanceOf(AwsStaticCredentialsAuthentication.class);
    // secrets redacted in toString
    assertThat(bedrock.authentication().toString()).doesNotContain("shh").contains("REDACTED");
  }

  @Test
  void directBackendRejectsBlankApiKey() {
    final var model =
        new AnthropicChatModel(
            new AnthropicConnection(
                new AnthropicDirectBackend(null, "  "),
                new AnthropicModel(
                    "claude-sonnet-4-6",
                    new AnthropicModelParameters(1, null, null, null, null, null, null)),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

    final var violations = validator.validate(model);
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("apiKey"));
  }

  @Test
  void validAnthropicModelHasNoViolations() {
    final var model =
        new AnthropicChatModel(
            new AnthropicConnection(
                new AnthropicDirectBackend(null, "sk-ant-123"),
                new AnthropicModel("claude-sonnet-4-6", null),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

    assertThat(validator.validate(model)).isEmpty();
    assertThat(model.capabilityOverride()).isNull();
  }

  @Test
  void deserialisesPopulatedCapabilityOverrideAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "anthropic",
          "anthropic": {
            "backend": { "type": "direct", "apiKey": "sk-ant-123" },
            "model": { "model": "claude-sonnet-4-6" },
            "capabilityOverride": {
              "userMessageModalities": ["text", "image"],
              "contextWindow": 4242
            }
          }
        }
        """;

    final AnthropicChatModel parsed =
        (AnthropicChatModel) mapper.readValue(json, LlmProviderConfiguration.class);

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
    final var direct = new AnthropicDirectBackend(null, "sk-ant-super-secret");

    assertThat(direct.toString()).doesNotContain("sk-ant-super-secret").contains("[REDACTED]");
  }

  @Test
  void deserialisesThinkingAndEffortAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "anthropic",
          "anthropic": {
            "backend": { "type": "direct", "apiKey": "sk-ant-123" },
            "model": {
              "model": "claude-sonnet-4-6",
              "parameters": {
                "thinking": { "mode": "enabled", "budgetTokens": 2048 },
                "effort": "high"
              }
            }
          }
        }
        """;

    final AnthropicChatModel parsed =
        (AnthropicChatModel) mapper.readValue(json, LlmProviderConfiguration.class);

    final AnthropicModelParameters parameters = parsed.anthropic().model().parameters();
    assertThat(parameters).isNotNull();
    assertThat(parameters.thinking())
        .isEqualTo(new AnthropicThinking(ThinkingMode.ENABLED, 2048, null));
    assertThat(parameters.effort()).isEqualTo(AnthropicEffort.HIGH);
    assertThat(parameters.customEffort()).isNull();

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, LlmProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void deserialisesCustomEffortAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "anthropic",
          "anthropic": {
            "backend": { "type": "direct", "apiKey": "sk-ant-123" },
            "model": {
              "model": "claude-sonnet-4-6",
              "parameters": {
                "effort": "custom",
                "customEffort": "ultra"
              }
            }
          }
        }
        """;

    final AnthropicChatModel parsed =
        (AnthropicChatModel) mapper.readValue(json, LlmProviderConfiguration.class);

    final AnthropicModelParameters parameters = parsed.anthropic().model().parameters();
    assertThat(parameters).isNotNull();
    assertThat(parameters.effort()).isEqualTo(AnthropicEffort.CUSTOM);
    assertThat(parameters.customEffort()).isEqualTo("ultra");

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, LlmProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void thinkingBudgetTokensRejectsValuesBelowMinimum() {
    final var thinking = new AnthropicThinking(ThinkingMode.ENABLED, 512, null);
    final var parameters =
        new AnthropicModelParameters(null, null, null, null, null, null, thinking);
    final var model =
        new AnthropicChatModel(
            new AnthropicConnection(
                new AnthropicDirectBackend(null, "sk-ant-123"),
                new AnthropicModel("claude-sonnet-4-6", parameters),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

    final Set<ConstraintViolation<AnthropicChatModel>> violations = validator.validate(model);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("budgetTokens"));
  }
}
