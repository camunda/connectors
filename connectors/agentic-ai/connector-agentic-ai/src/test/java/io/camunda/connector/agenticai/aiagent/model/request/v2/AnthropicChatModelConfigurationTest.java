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
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicFoundryBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel.AnthropicEffort;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel.AnthropicThinking;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel.ThinkingMode;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicCustomEndpointAuthentication.ApiKeyAuthentication;
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
    assertThat(parsed.descriptiveProvider()).isEqualTo("anthropic/anthropic-api");
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
  void deserialisesModelDefaultEffortAndThinkingModeAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "anthropic",
          "anthropic": {
            "backend": { "type": "anthropic-api", "anthropic": { "apiKey": "sk-ant-123" } },
            "model": {
              "model": "claude-sonnet-4-6",
              "parameters": {
                "effort": "modelDefault",
                "thinking": { "mode": "modelDefault" }
              }
            }
          }
        }
        """;

    final AnthropicChatModelConfiguration parsed =
        (AnthropicChatModelConfiguration) mapper.readValue(json, ProviderConfiguration.class);

    final AnthropicModelParameters parameters = parsed.anthropic().model().parameters();
    assertThat(parameters).isNotNull();
    assertThat(parameters.effort()).isEqualTo(AnthropicEffort.MODEL_DEFAULT);
    assertThat(parameters.thinking().mode()).isEqualTo(ThinkingMode.MODEL_DEFAULT);

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
  void deserialisesCustomBackendWithOAuthClientCredentialsAuthAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "anthropic",
          "anthropic": {
            "backend": {
              "type": "custom",
              "custom": {
                "endpoint": "https://custom.example.com",
                "authentication": {
                  "type": "oauth-client-credentials-flow",
                  "oauthTokenEndpoint": "https://auth.example.com/oauth/token",
                  "clientId": "client-123",
                  "clientSecret": "secret-123",
                  "scopes": "read:llm"
                }
              }
            },
            "model": { "model": "claude-sonnet-4-6" }
          }
        }
        """;

    final AnthropicChatModelConfiguration parsed =
        (AnthropicChatModelConfiguration) mapper.readValue(json, ProviderConfiguration.class);

    final AnthropicCustomBackend custom = (AnthropicCustomBackend) parsed.anthropic().backend();
    assertThat(custom.custom().authentication())
        .isEqualTo(
            new OAuthClientCredentialsAuthentication(
                "https://auth.example.com/oauth/token",
                "client-123",
                "secret-123",
                null,
                OAuthClientCredentialsAuthentication.ClientAuthenticationMethod.BASIC_AUTH_HEADER,
                "read:llm"));

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void oAuthClientCredentialsAuthenticationRedactsSecretsInToString() {
    final var auth =
        new OAuthClientCredentialsAuthentication(
            "https://auth.example.com/oauth/token",
            "client-123",
            "super-secret-value",
            null,
            OAuthClientCredentialsAuthentication.ClientAuthenticationMethod.BASIC_AUTH_HEADER,
            null);

    assertThat(auth.toString())
        .contains("clientId=[REDACTED]", "clientSecret=[REDACTED]")
        .doesNotContain("client-123", "super-secret-value");
  }

  @Test
  void requiredOAuthClientCredentialsFieldsAreEnforced() {
    final var config =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(
                new AnthropicCustomBackend(
                    new AnthropicCustomBackend.CustomBackend(
                        "https://custom.example.com",
                        null,
                        null,
                        null,
                        new OAuthClientCredentialsAuthentication("", "", "", null, null, null))),
                new AnthropicModel("claude-sonnet-4-6", null),
                null));

    final var violations = validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("anthropic.backend.custom.authentication.oauthTokenEndpoint");
              assertThat(v.getMessage()).isEqualTo("must not be empty");
            })
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("anthropic.backend.custom.authentication.clientId");
              assertThat(v.getMessage()).isEqualTo("must not be empty");
            })
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("anthropic.backend.custom.authentication.clientSecret");
              assertThat(v.getMessage()).isEqualTo("must not be empty");
            });
  }

  @Test
  void anthropicApiBackendRedactsApiKeyHeadersAndBodyPropertiesInToString() {
    final var backend =
        new AnthropicApiBackend(
            new AnthropicApiBackend.AnthropicApi(
                null,
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
                null, "sk-ant-super-secret", null, Map.of(), Map.of(), Map.of()));
    assertThat(backendWithEmptyMaps.toString())
        .contains("headers={}", "queryParameters={}", "bodyProperties={}");

    final var backendWithNullMaps =
        new AnthropicApiBackend(
            new AnthropicApiBackend.AnthropicApi(
                null, "sk-ant-super-secret", null, null, null, null));
    assertThat(backendWithNullMaps.toString())
        .contains("headers=null", "queryParameters=null", "bodyProperties=null");
  }

  @Test
  void anthropicApiBackendRejectsBlankApiKey() {
    final var config =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(
                new AnthropicApiBackend(
                    new AnthropicApiBackend.AnthropicApi(null, "  ", null, null, null, null)),
                new AnthropicModel("claude-sonnet-4-6", null),
                null));

    final var violations = validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("anthropic.backend.anthropic.apiKeyPresent");
              assertThat(v.getMessage())
                  .isEqualTo(
                      "Anthropic API key is required from the credential or element template");
            });
  }

  @Test
  void anthropicApiBackendRejectsMissingApiKeyAndCredential() {
    final var config =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(
                new AnthropicApiBackend(
                    new AnthropicApiBackend.AnthropicApi(null, null, null, null, null, null)),
                new AnthropicModel("claude-sonnet-4-6", null),
                null));

    final var violations = validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("anthropic.backend.anthropic.apiKeyPresent");
              assertThat(v.getMessage())
                  .isEqualTo(
                      "Anthropic API key is required from the credential or element template");
            });
  }

  @Test
  void anthropicApiBackendResolvesApiKeyFromCredentialWithNoViolations() {
    final var config =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(
                new AnthropicApiBackend(
                    new AnthropicApiBackend.AnthropicApi(
                        new AnthropicApiCredential("sk-ant-from-credential"),
                        null,
                        null,
                        null,
                        null,
                        null)),
                new AnthropicModel("claude-sonnet-4-6", null),
                null));

    assertThat(validator.validate(config)).isEmpty();
    assertThat(((AnthropicApiBackend) config.anthropic().backend()).anthropic().effectiveApiKey())
        .isEqualTo("sk-ant-from-credential");
  }

  @Test
  void anthropicApiBackendCredentialTakesPrecedenceOverInlineApiKey() {
    final var config =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(
                new AnthropicApiBackend(
                    new AnthropicApiBackend.AnthropicApi(
                        new AnthropicApiCredential("sk-ant-from-credential"),
                        "sk-ant-inline",
                        null,
                        null,
                        null,
                        null)),
                new AnthropicModel("claude-sonnet-4-6", null),
                null));

    assertThat(validator.validate(config)).isEmpty();
    assertThat(((AnthropicApiBackend) config.anthropic().backend()).anthropic().effectiveApiKey())
        .isEqualTo("sk-ant-from-credential");
  }

  @Test
  void anthropicApiCredentialRedactsSecretInToString() {
    assertThat(new AnthropicApiCredential("sk-ant-secret").toString())
        .doesNotContain("sk-ant-secret")
        .isEqualTo("AnthropicApiCredential{apiKey=[REDACTED]}");
  }

  @Test
  void deserialisesAnthropicApiBackendWithCredentialAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "anthropic",
          "anthropic": {
            "backend": {
              "type": "anthropic-api",
              "anthropic": { "anthropicApiCredential": { "apiKey": "sk-ant-from-credential" } }
            },
            "model": { "model": "claude-sonnet-4-6" }
          }
        }
        """;

    final AnthropicChatModelConfiguration parsed =
        (AnthropicChatModelConfiguration) mapper.readValue(json, ProviderConfiguration.class);

    assertThat(validator.validate(parsed)).isEmpty();
    assertThat(((AnthropicApiBackend) parsed.anthropic().backend()).anthropic().effectiveApiKey())
        .isEqualTo("sk-ant-from-credential");

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
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
                    new AnthropicApiBackend.AnthropicApi(
                        null, "sk-ant-123", null, null, null, null)),
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
                    new AnthropicApiBackend.AnthropicApi(
                        null, "sk-ant-123", null, null, null, null)),
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
                    new AnthropicApiBackend.AnthropicApi(
                        null, "sk-ant-123", null, null, null, null)),
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
                    new AnthropicApiBackend.AnthropicApi(
                        null, "sk-ant-123", null, null, null, null)),
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
                "authentication": {
                  "type": "awsIam",
                  "awsIam": { "type": "credentials", "accessKey": "AKIA123", "secretKey": "secret123" }
                }
              }
            },
            "model": { "model": "claude-sonnet-4-6" }
          }
        }
        """;

    final AnthropicChatModelConfiguration parsed =
        (AnthropicChatModelConfiguration) mapper.readValue(json, ProviderConfiguration.class);

    assertThat(parsed.anthropic().backend()).isInstanceOf(AnthropicAwsBedrockMantleBackend.class);
    assertThat(parsed.descriptiveProvider()).isEqualTo("anthropic/aws-bedrock-mantle");
    final AnthropicAwsBedrockMantleBackend bedrockBackend =
        (AnthropicAwsBedrockMantleBackend) parsed.anthropic().backend();
    assertThat(bedrockBackend.awsBedrockMantle().region()).isEqualTo("eu-central-1");
    assertThat(bedrockBackend.awsBedrockMantle().endpoint()).isNull();
    assertThat(bedrockBackend.awsBedrockMantle().authentication())
        .isEqualTo(iamStatic("AKIA123", "secret123"));

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
        .isEqualTo(apiKeyInline("bedrock-secret-key"));

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void bedrockStaticCredentialsAuthenticationRedactsSecretsInToString() {
    final var auth = iamStatic("AKIA123", "secret123");

    assertThat(auth.toString())
        .doesNotContain("AKIA123")
        .doesNotContain("secret123")
        .contains("[REDACTED]");
  }

  @Test
  void bedrockApiKeyAuthenticationRedactsApiKeyInToString() {
    final var auth = apiKeyInline("bedrock-secret-key");

    assertThat(auth.toString()).doesNotContain("bedrock-secret-key").contains("[REDACTED]");
  }

  @Test
  void bedrockApiKeyCredentialRedactsSecretInToString() {
    assertThat(new BedrockApiKeyCredential("bedrock-secret-key").toString())
        .doesNotContain("bedrock-secret-key")
        .isEqualTo("BedrockApiKeyCredential{apiKey=[REDACTED]}");
  }

  @Test
  void requiredBedrockFieldsAreEnforced() {
    final var config =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(
                new AnthropicAwsBedrockMantleBackend(
                    new AnthropicAwsBedrockMantleBackend.AwsBedrockMantleBackend(
                        "", null, iamStatic("", ""), null, null, null)),
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
              assertThat(v.getPropertyPath().toString()).endsWith("accessKey");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            })
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString()).endsWith("secretKey");
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
    final var config = bedrockConfig(iamDefaultChain());

    assertThat(validator.validate(config))
        .extracting(ConstraintViolation::getMessage)
        .contains("AWS default credentials chain is not supported on SaaS");
  }

  @Test
  void bedrockDefaultCredentialsChainAllowedWhenNotSaaS() {
    final var config = bedrockConfig(iamDefaultChain());

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void bedrockApiKeyAuthenticationRejectedWhenNeitherCredentialNorInlineKeyPresent() {
    final var config = bedrockConfig(new AwsAuthentication.AwsApiKeyAuthentication(null, null));

    assertThat(validator.validate(config))
        .extracting(ConstraintViolation::getMessage)
        .contains("An AWS Bedrock API key is required from the credential or element template");
  }

  @Test
  void bedrockApiKeyAuthenticationPresentWhenOnlyCredentialBound() {
    final var config =
        bedrockConfig(
            new AwsAuthentication.AwsApiKeyAuthentication(
                new BedrockApiKeyCredential("credential-key"), null));

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void bedrockApiKeyAuthenticationCredentialTakesPrecedenceOverInlineKey() {
    final var authentication =
        new AwsAuthentication.AwsApiKeyAuthentication(
            new BedrockApiKeyCredential("credential-key"), "inline-key");

    assertThat(authentication.effectiveApiKey()).isEqualTo("credential-key");
    assertThat(validator.validate(bedrockConfig(authentication))).isEmpty();
  }

  @Test
  void bedrockIamAuthenticationPresentWhenOnlyCredentialBound() {
    final var authentication = iamCredential(staticAwsCredential());

    assertThat(authentication.getAwsIamWhenNoCredentialBound()).isNull();
    assertThat(authentication.isAuthenticationPresent()).isTrue();
    assertThat(validator.validate(bedrockConfig(authentication))).isEmpty();
  }

  @Test
  void bedrockIamAuthenticationRejectedWhenNeitherCredentialNorInlineMethodPresent() {
    final var authentication = new AwsAuthentication.AwsIamAuthentication(null, null);

    assertThat(validator.validate(bedrockConfig(authentication)))
        .extracting(ConstraintViolation::getMessage)
        .contains("AWS IAM authentication is required from the credential or element template");
  }

  @Test
  void bedrockIamAuthenticationCredentialTakesPrecedenceOverInlineMethod() {
    final var authentication =
        new AwsAuthentication.AwsIamAuthentication(
            staticAwsCredential(),
            new AwsAuthentication.AwsIamAuthenticationMethod.AwsStaticCredentialsAuthentication(
                "inline-access", "inline-secret"));

    assertThat(authentication.getAwsIamWhenNoCredentialBound()).isNull();
    assertThat(validator.validate(bedrockConfig(authentication))).isEmpty();
  }

  @Test
  void bedrockBoundDefaultCredentialsChainRejectedOnSaaS() {
    environment.set(ConnectorUtils.CONNECTOR_RUNTIME_SAAS_ENV_VARIABLE, "true");
    final var config = bedrockConfig(iamCredential(defaultChainAwsCredential()));

    assertThat(validator.validate(config))
        .extracting(ConstraintViolation::getMessage)
        .contains("AWS default credentials chain is not supported on SaaS");
  }

  @Test
  void bedrockBoundStaticCredentialAllowedOnSaaS() {
    environment.set(ConnectorUtils.CONNECTOR_RUNTIME_SAAS_ENV_VARIABLE, "true");
    final var config = bedrockConfig(iamCredential(staticAwsCredential()));

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

  private static AwsAuthentication.AwsIamAuthentication iamStatic(
      String accessKey, String secretKey) {
    return new AwsAuthentication.AwsIamAuthentication(
        null,
        new AwsAuthentication.AwsIamAuthenticationMethod.AwsStaticCredentialsAuthentication(
            accessKey, secretKey));
  }

  private static AwsAuthentication.AwsIamAuthentication iamDefaultChain() {
    return new AwsAuthentication.AwsIamAuthentication(
        null,
        new AwsAuthentication.AwsIamAuthenticationMethod
            .AwsDefaultCredentialsChainAuthentication());
  }

  private static AwsAuthentication.AwsIamAuthentication iamCredential(
      io.camunda.connector.aws.model.impl.AwsCredentialConfiguration awsCredential) {
    return new AwsAuthentication.AwsIamAuthentication(awsCredential, null);
  }

  private static AwsAuthentication.AwsApiKeyAuthentication apiKeyInline(String apiKey) {
    return new AwsAuthentication.AwsApiKeyAuthentication(null, apiKey);
  }

  private static io.camunda.connector.aws.model.impl.AwsCredentialConfiguration
      staticAwsCredential() {
    return new io.camunda.connector.aws.model.impl.AwsCredentialConfiguration(
        new io.camunda.connector.aws.model.impl.AwsAuthentication
            .AwsStaticCredentialsAuthentication("AKIA-bound", "secret-bound"),
        "eu-central-1");
  }

  private static io.camunda.connector.aws.model.impl.AwsCredentialConfiguration
      defaultChainAwsCredential() {
    return new io.camunda.connector.aws.model.impl.AwsCredentialConfiguration(
        new io.camunda.connector.aws.model.impl.AwsAuthentication
            .AwsDefaultCredentialsChainAuthentication(),
        "eu-central-1");
  }

  @Test
  void deserialisesFoundryBackendWithApiKeyAuthAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "anthropic",
          "anthropic": {
            "backend": {
              "type": "foundry",
              "foundry": {
                "endpoint": "https://your-resource.services.ai.azure.com",
                "authentication": { "type": "apiKey", "apiKey": "foundry-secret-key" }
              }
            },
            "model": { "model": "claude-sonnet-4-6" }
          }
        }
        """;

    final AnthropicChatModelConfiguration parsed =
        (AnthropicChatModelConfiguration) mapper.readValue(json, ProviderConfiguration.class);

    assertThat(parsed.anthropic().backend()).isInstanceOf(AnthropicFoundryBackend.class);
    final AnthropicFoundryBackend foundryBackend =
        (AnthropicFoundryBackend) parsed.anthropic().backend();
    assertThat(foundryBackend.foundry().endpoint())
        .isEqualTo("https://your-resource.services.ai.azure.com");
    assertThat(foundryBackend.foundry().authentication())
        .isEqualTo(new FoundryAuthentication.ApiKeyAuthentication(null, "foundry-secret-key"));

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void deserialisesFoundryBackendWithClientCredentialsAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "anthropic",
          "anthropic": {
            "backend": {
              "type": "foundry",
              "foundry": {
                "endpoint": "https://your-resource.services.ai.azure.com",
                "authentication": {
                  "type": "clientCredentials",
                  "clientId": "client-id",
                  "clientSecret": "client-secret",
                  "tenantId": "tenant-id"
                }
              }
            },
            "model": { "model": "claude-sonnet-4-6" }
          }
        }
        """;

    final AnthropicChatModelConfiguration parsed =
        (AnthropicChatModelConfiguration) mapper.readValue(json, ProviderConfiguration.class);

    final AnthropicFoundryBackend foundryBackend =
        (AnthropicFoundryBackend) parsed.anthropic().backend();
    assertThat(foundryBackend.foundry().authentication())
        .isEqualTo(
            new FoundryAuthentication.ClientCredentialsAuthentication(
                null, "client-id", "client-secret", "tenant-id", null, null));

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void deserialisesFoundryApiKeyAuthWithCredentialAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "anthropic",
          "anthropic": {
            "backend": {
              "type": "foundry",
              "foundry": {
                "endpoint": "https://your-resource.services.ai.azure.com",
                "authentication": {
                  "type": "apiKey",
                  "foundryApiKeyCredential": { "apiKey": "foundry-secret-from-credential" }
                }
              }
            },
            "model": { "model": "claude-sonnet-4-6" }
          }
        }
        """;

    final AnthropicChatModelConfiguration parsed =
        (AnthropicChatModelConfiguration) mapper.readValue(json, ProviderConfiguration.class);

    assertThat(validator.validate(parsed)).isEmpty();
    final AnthropicFoundryBackend foundryBackend =
        (AnthropicFoundryBackend) parsed.anthropic().backend();
    assertThat(
            ((FoundryAuthentication.ApiKeyAuthentication) foundryBackend.foundry().authentication())
                .effectiveApiKey())
        .isEqualTo("foundry-secret-from-credential");

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void deserialisesFoundryClientCredentialsAuthWithCredentialAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "anthropic",
          "anthropic": {
            "backend": {
              "type": "foundry",
              "foundry": {
                "endpoint": "https://your-resource.services.ai.azure.com",
                "authentication": {
                  "type": "clientCredentials",
                  "foundryClientCredentialsCredential": {
                    "clientId": "client-from-credential",
                    "clientSecret": "secret-from-credential",
                    "tenantId": "tenant-from-credential",
                    "authorityHost": "https://login.microsoftonline.us/"
                  }
                }
              }
            },
            "model": { "model": "claude-sonnet-4-6" }
          }
        }
        """;

    final AnthropicChatModelConfiguration parsed =
        (AnthropicChatModelConfiguration) mapper.readValue(json, ProviderConfiguration.class);

    assertThat(validator.validate(parsed)).isEmpty();
    final AnthropicFoundryBackend foundryBackend =
        (AnthropicFoundryBackend) parsed.anthropic().backend();
    final var authentication =
        (FoundryAuthentication.ClientCredentialsAuthentication)
            foundryBackend.foundry().authentication();
    assertThat(authentication.effectiveClientId()).isEqualTo("client-from-credential");
    assertThat(authentication.effectiveClientSecret()).isEqualTo("secret-from-credential");
    assertThat(authentication.effectiveTenantId()).isEqualTo("tenant-from-credential");
    assertThat(authentication.effectiveAuthorityHost())
        .isEqualTo("https://login.microsoftonline.us/");

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void foundryCredentialsRedactSecretsInToString() {
    assertThat(new FoundryApiKeyCredential("foundry-secret-key").toString())
        .doesNotContain("foundry-secret-key")
        .isEqualTo("FoundryApiKeyCredential{apiKey=[REDACTED]}");

    final String clientCredentialsToString =
        new FoundryClientCredentialsCredential(
                "client-id",
                "client-secret-value",
                "tenant-id",
                "https://login.microsoftonline.us/")
            .toString();
    assertThat(clientCredentialsToString)
        .doesNotContain("client-secret-value")
        .contains("clientId=client-id", "clientSecret=[REDACTED]", "tenantId=tenant-id");
  }

  @Test
  void requiredFoundryFieldsAreEnforced() {
    final var config =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(
                new AnthropicFoundryBackend(
                    new AnthropicFoundryBackend.FoundryBackend(
                        "",
                        new FoundryAuthentication.ApiKeyAuthentication(null, "  "),
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
                  .isEqualTo("anthropic.backend.foundry.endpoint");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            })
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("anthropic.backend.foundry.authentication.apiKeyPresent");
              assertThat(v.getMessage())
                  .isEqualTo(
                      "A Microsoft Foundry API key is required from the credential or element"
                          + " template");
            });
  }

  @Test
  void foundryApiBackendResolvesApiKeyFromCredentialWithNoViolations() {
    final var config =
        foundryConfig(
            new FoundryAuthentication.ApiKeyAuthentication(
                new FoundryApiKeyCredential("foundry-secret-from-credential"), null));

    assertThat(validator.validate(config)).isEmpty();
    assertThat(
            ((FoundryAuthentication.ApiKeyAuthentication)
                    ((AnthropicFoundryBackend) config.anthropic().backend())
                        .foundry()
                        .authentication())
                .effectiveApiKey())
        .isEqualTo("foundry-secret-from-credential");
  }

  @Test
  void foundryApiBackendCredentialTakesPrecedenceOverInlineApiKey() {
    final var config =
        foundryConfig(
            new FoundryAuthentication.ApiKeyAuthentication(
                new FoundryApiKeyCredential("from-credential"), "from-inline"));

    assertThat(validator.validate(config)).isEmpty();
    assertThat(
            ((FoundryAuthentication.ApiKeyAuthentication)
                    ((AnthropicFoundryBackend) config.anthropic().backend())
                        .foundry()
                        .authentication())
                .effectiveApiKey())
        .isEqualTo("from-credential");
  }

  @Test
  void foundryClientCredentialsAuthenticationResolvesFromCredentialWithNoViolations() {
    final var config =
        foundryConfig(
            new FoundryAuthentication.ClientCredentialsAuthentication(
                new FoundryClientCredentialsCredential(
                    "client-from-credential",
                    "secret-from-credential",
                    "tenant-from-credential",
                    "https://login.microsoftonline.us/"),
                null,
                null,
                null,
                null,
                null));

    assertThat(validator.validate(config)).isEmpty();
    final var authentication =
        (FoundryAuthentication.ClientCredentialsAuthentication)
            ((AnthropicFoundryBackend) config.anthropic().backend()).foundry().authentication();
    assertThat(authentication.effectiveClientId()).isEqualTo("client-from-credential");
    assertThat(authentication.effectiveClientSecret()).isEqualTo("secret-from-credential");
    assertThat(authentication.effectiveTenantId()).isEqualTo("tenant-from-credential");
    assertThat(authentication.effectiveAuthorityHost())
        .isEqualTo("https://login.microsoftonline.us/");
  }

  @Test
  void foundryClientCredentialsAuthenticationCredentialTakesPrecedenceOverInlineFields() {
    final var config =
        foundryConfig(
            new FoundryAuthentication.ClientCredentialsAuthentication(
                new FoundryClientCredentialsCredential(
                    "client-from-credential",
                    "secret-from-credential",
                    "tenant-from-credential",
                    "https://login.microsoftonline.us/"),
                "client-inline",
                "secret-inline",
                "tenant-inline",
                "https://login.microsoftonline.com/",
                null));

    assertThat(validator.validate(config)).isEmpty();
    final var authentication =
        (FoundryAuthentication.ClientCredentialsAuthentication)
            ((AnthropicFoundryBackend) config.anthropic().backend()).foundry().authentication();
    assertThat(authentication.effectiveClientId()).isEqualTo("client-from-credential");
    assertThat(authentication.effectiveClientSecret()).isEqualTo("secret-from-credential");
    assertThat(authentication.effectiveTenantId()).isEqualTo("tenant-from-credential");
    assertThat(authentication.effectiveAuthorityHost())
        .isEqualTo("https://login.microsoftonline.us/");
  }

  @Test
  void foundryClientCredentialsAuthenticationFallsBackToInlineAuthorityHostWithoutCredential() {
    final var config =
        foundryConfig(
            new FoundryAuthentication.ClientCredentialsAuthentication(
                null,
                "client-inline",
                "secret-inline",
                "tenant-inline",
                "https://login.microsoftonline.com/",
                null));

    assertThat(validator.validate(config)).isEmpty();
    final var authentication =
        (FoundryAuthentication.ClientCredentialsAuthentication)
            ((AnthropicFoundryBackend) config.anthropic().backend()).foundry().authentication();
    assertThat(authentication.effectiveAuthorityHost())
        .isEqualTo("https://login.microsoftonline.com/");
  }

  @Test
  void foundryClientCredentialsAuthenticationRejectsMissingFieldsAndCredential() {
    final var config =
        foundryConfig(
            new FoundryAuthentication.ClientCredentialsAuthentication(
                null, null, null, null, null, null));

    assertThat(validator.validate(config))
        .extracting(ConstraintViolation::getMessage)
        .contains(
            "Microsoft Foundry client ID, client secret and tenant ID are required from the"
                + " credential or element template");
  }

  @Test
  void foundryManagedIdentityRejectedOnSaaS() {
    environment.set(ConnectorUtils.CONNECTOR_RUNTIME_SAAS_ENV_VARIABLE, "true");
    final var config =
        foundryConfig(new FoundryAuthentication.ManagedIdentityAuthentication(null, null));

    assertThat(validator.validate(config))
        .extracting(ConstraintViolation::getMessage)
        .contains("Managed identity authentication is not supported on SaaS");
  }

  @Test
  void foundryManagedIdentityAllowedWhenNotSaaS() {
    final var config =
        foundryConfig(new FoundryAuthentication.ManagedIdentityAuthentication(null, null));

    assertThat(validator.validate(config)).isEmpty();
  }

  private static AnthropicChatModelConfiguration foundryConfig(
      FoundryAuthentication authentication) {
    return new AnthropicChatModelConfiguration(
        new AnthropicConnection(
            new AnthropicFoundryBackend(
                new AnthropicFoundryBackend.FoundryBackend(
                    "https://your-resource.services.ai.azure.com",
                    authentication,
                    null,
                    null,
                    null)),
            new AnthropicModel("claude-sonnet-4-6", null),
            null));
  }
}
