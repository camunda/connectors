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
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicAwsBedrockMantleBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicCustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel.AnthropicEffort;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel.AnthropicThinking;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel.ThinkingMode;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.CustomEndpointAuthentication.ApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.util.ConnectorUtils;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith({SpringExtension.class, SystemStubsExtension.class})
@Import(ValidationAutoConfiguration.class)
class AnthropicChatModelConfigurationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Autowired private Validator validator;
  @SystemStub private EnvironmentVariables environment;

  @BeforeEach
  void setUp() {
    environment.set(ConnectorUtils.CONNECTOR_RUNTIME_SAAS_ENV_VARIABLE, null);
  }

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
  void anthropicApiBackendRedactsApiKeyHeadersAndBodyPropertiesInToString() {
    final var backend =
        new AnthropicApiBackend(
            new AnthropicApiBackend.AnthropicApi(
                "sk-ant-super-secret",
                null,
                Map.of("Authorization", "Bearer secret"),
                Map.of("api-version", "2026-01-01"),
                Map.of("large_field", "large_value")));

    final String toString = backend.toString();
    assertThat(toString).doesNotContain("sk-ant-super-secret", "Bearer secret", "large_value");
    assertThat(toString)
        .contains(
            "apiKey=[REDACTED]",
            "headers={Authorization=[REDACTED]}",
            "queryParameters={api-version=[REDACTED]}",
            "bodyProperties={large_field=[REDACTED]}");
  }

  @Test
  void customBackendRedactsHeadersAndBodyPropertiesInToString() {
    final var backend =
        new AnthropicCustomBackend(
            new AnthropicCustomBackend.CustomBackend(
                "https://custom.example.com",
                Map.of("Authorization", "Bearer secret"),
                Map.of("api-version", "2026-01-01"),
                Map.of("large_field", "large_value"),
                new ApiKeyAuthentication("sk-custom-super-secret")));

    final String toString = backend.toString();
    assertThat(toString).doesNotContain("sk-custom-super-secret", "Bearer secret", "large_value");
    assertThat(toString)
        .contains(
            "headers={Authorization=[REDACTED]}",
            "queryParameters={api-version=[REDACTED]}",
            "bodyProperties={large_field=[REDACTED]}",
            "apiKey=[REDACTED]");
  }

  @Test
  void redactsEmptyAndNullHeadersDistinctlyInToString() {
    final var backendWithEmptyMaps =
        new AnthropicApiBackend(
            new AnthropicApiBackend.AnthropicApi(
                "sk-ant-super-secret", null, Map.of(), Map.of(), Map.of()));
    assertThat(backendWithEmptyMaps.toString())
        .contains("headers={}", "queryParameters={}", "bodyProperties={}");

    final var backendWithNullMaps =
        new AnthropicApiBackend(
            new AnthropicApiBackend.AnthropicApi("sk-ant-super-secret", null, null, null, null));
    assertThat(backendWithNullMaps.toString())
        .contains("headers=null", "queryParameters=null", "bodyProperties=null");
  }

  @Test
  void anthropicApiBackendRejectsBlankApiKey() {
    final var config =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(
                new AnthropicApiBackend(
                    new AnthropicApiBackend.AnthropicApi("  ", null, null, null, null)),
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
                new AnthropicApiBackend(
                    new AnthropicApiBackend.AnthropicApi("sk-ant-123", null, null, null, null)),
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
  void
      thinkingBudgetTokensRejectsValuesAtOrAboveTheEffectiveDefaultMaxTokensWhenMaxTokensIsUnset() {
    final var thinking =
        new AnthropicThinking(
            ThinkingMode.ENABLED, (int) AnthropicModelParameters.DEFAULT_MAX_TOKENS, null);
    final var parameters =
        new AnthropicModelParameters(null, thinking, null, null, null, null, null);
    final var config =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(
                new AnthropicApiBackend(
                    new AnthropicApiBackend.AnthropicApi("sk-ant-123", null, null, null, null)),
                new AnthropicModel("claude-sonnet-4-6", parameters),
                null));

    final Set<ConstraintViolation<AnthropicChatModelConfiguration>> violations =
        validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v ->
                assertThat(v.getMessage())
                    .isEqualTo("thinking.budgetTokens must be less than maxTokens"));
  }

  @Test
  void thinkingBudgetTokensBelowTheEffectiveDefaultMaxTokensHasNoViolationsWhenMaxTokensIsUnset() {
    final var thinking =
        new AnthropicThinking(
            ThinkingMode.ENABLED, (int) AnthropicModelParameters.DEFAULT_MAX_TOKENS - 1, null);
    final var parameters =
        new AnthropicModelParameters(null, thinking, null, null, null, null, null);
    final var config =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(
                new AnthropicApiBackend(
                    new AnthropicApiBackend.AnthropicApi("sk-ant-123", null, null, null, null)),
                new AnthropicModel("claude-sonnet-4-6", parameters),
                null));

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void validAnthropicConfigurationHasNoViolations() {
    final var config =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(
                new AnthropicApiBackend(
                    new AnthropicApiBackend.AnthropicApi("sk-ant-123", null, null, null, null)),
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

  @Test
  void deserialisesBedrockBackendWithStaticCredentialsAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "anthropic",
          "anthropic": {
            "backend": {
              "type": "aws-bedrock-mantle",
              "awsBedrockMantle": {
                "region": "eu-central-1",
                "authentication": { "type": "credentials", "accessKey": "AKIA123", "secretKey": "secret123" }
              }
            },
            "model": { "model": "claude-sonnet-4-6" }
          }
        }
        """;

    final AnthropicChatModelConfiguration parsed =
        (AnthropicChatModelConfiguration) mapper.readValue(json, ProviderConfiguration.class);

    assertThat(parsed.anthropic().backend()).isInstanceOf(AnthropicAwsBedrockMantleBackend.class);
    final AnthropicAwsBedrockMantleBackend bedrockBackend =
        (AnthropicAwsBedrockMantleBackend) parsed.anthropic().backend();
    assertThat(bedrockBackend.awsBedrockMantle().region()).isEqualTo("eu-central-1");
    assertThat(bedrockBackend.awsBedrockMantle().endpoint()).isNull();
    assertThat(bedrockBackend.awsBedrockMantle().authentication())
        .isEqualTo(
            new AwsAuthentication.AwsStaticCredentialsAuthentication("AKIA123", "secret123"));

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void deserialisesBedrockBackendWithCustomEndpointAndApiKeyAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "anthropic",
          "anthropic": {
            "backend": {
              "type": "aws-bedrock-mantle",
              "awsBedrockMantle": {
                "region": "eu-central-1",
                "endpoint": "https://vpce-example.vpce.amazonaws.com/anthropic",
                "authentication": { "type": "apiKey", "apiKey": "bedrock-secret-key" }
              }
            },
            "model": { "model": "claude-sonnet-4-6" }
          }
        }
        """;

    final AnthropicChatModelConfiguration parsed =
        (AnthropicChatModelConfiguration) mapper.readValue(json, ProviderConfiguration.class);

    final AnthropicAwsBedrockMantleBackend bedrockBackend =
        (AnthropicAwsBedrockMantleBackend) parsed.anthropic().backend();
    assertThat(bedrockBackend.awsBedrockMantle().endpoint())
        .isEqualTo("https://vpce-example.vpce.amazonaws.com/anthropic");
    assertThat(bedrockBackend.awsBedrockMantle().authentication())
        .isEqualTo(new AwsAuthentication.AwsApiKeyAuthentication("bedrock-secret-key"));

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void bedrockStaticCredentialsAuthenticationRedactsSecretsInToString() {
    final var auth =
        new AwsAuthentication.AwsStaticCredentialsAuthentication("AKIA123", "secret123");

    assertThat(auth.toString())
        .doesNotContain("AKIA123")
        .doesNotContain("secret123")
        .contains("[REDACTED]");
  }

  @Test
  void bedrockApiKeyAuthenticationRedactsApiKeyInToString() {
    final var auth = new AwsAuthentication.AwsApiKeyAuthentication("bedrock-secret-key");

    assertThat(auth.toString()).doesNotContain("bedrock-secret-key").contains("[REDACTED]");
  }

  @Test
  void requiredBedrockFieldsAreEnforced() {
    final var config =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(
                new AnthropicAwsBedrockMantleBackend(
                    new AnthropicAwsBedrockMantleBackend.AwsBedrockMantleBackend(
                        "",
                        null,
                        new AwsAuthentication.AwsStaticCredentialsAuthentication("", ""),
                        null,
                        null,
                        null)),
                new AnthropicModel("claude-sonnet-4-6", null),
                null));

    final var violations = validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("anthropic.backend.awsBedrockMantle.region");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            })
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("anthropic.backend.awsBedrockMantle.authentication.accessKey");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            })
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("anthropic.backend.awsBedrockMantle.authentication.secretKey");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            });
  }

  @Test
  void missingBedrockContainerIsRejectedWithoutThrowingOnSaaS() {
    environment.set(ConnectorUtils.CONNECTOR_RUNTIME_SAAS_ENV_VARIABLE, "true");
    final var config =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(
                new AnthropicAwsBedrockMantleBackend(null),
                new AnthropicModel("claude-sonnet-4-6", null),
                null));

    assertThat(validator.validate(config))
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("anthropic.backend.awsBedrockMantle");
              assertThat(v.getMessage()).isEqualTo("must not be null");
            });
  }

  @Test
  void bedrockDefaultCredentialsChainRejectedOnSaaS() {
    environment.set(ConnectorUtils.CONNECTOR_RUNTIME_SAAS_ENV_VARIABLE, "true");
    final var config =
        bedrockConfig(new AwsAuthentication.AwsDefaultCredentialsChainAuthentication());

    assertThat(validator.validate(config))
        .extracting(ConstraintViolation::getMessage)
        .contains("AWS default credentials chain is not supported on SaaS");
  }

  @Test
  void bedrockDefaultCredentialsChainAllowedWhenNotSaaS() {
    final var config =
        bedrockConfig(new AwsAuthentication.AwsDefaultCredentialsChainAuthentication());

    assertThat(validator.validate(config)).isEmpty();
  }

  private static AnthropicChatModelConfiguration bedrockConfig(AwsAuthentication authentication) {
    return new AnthropicChatModelConfiguration(
        new AnthropicConnection(
            new AnthropicAwsBedrockMantleBackend(
                new AnthropicAwsBedrockMantleBackend.AwsBedrockMantleBackend(
                    "eu-central-1", null, authentication, null, null, null)),
            new AnthropicModel("claude-sonnet-4-6", null),
            null));
  }
}
