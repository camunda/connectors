/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.chatmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicBackend.AnthropicBedrockBackend;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicBackend.AnthropicDirectBackend;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.shared.ChatModelAwsAuthentication.AwsStaticCredentialsAuthentication;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
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
                    "claude-sonnet-4-6", new AnthropicModelParameters(1, null, null, null)),
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
                null));

    assertThat(validator.validate(model)).isEmpty();
    assertThat(model.capabilityOverride()).isNull();
  }
}
