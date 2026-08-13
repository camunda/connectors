/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockChatModelConfiguration.BedrockConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockChatModelConfiguration.BedrockModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockChatModelConfiguration.BedrockModel.BedrockModelParameters;
import io.camunda.connector.agenticai.aiagent.util.ConnectorUtils;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Map;
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
class BedrockChatModelConfigurationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Autowired private Validator validator;
  @SystemStub private EnvironmentVariables environment;

  @BeforeEach
  void setUp() {
    environment.set(ConnectorUtils.CONNECTOR_RUNTIME_SAAS_ENV_VARIABLE, null);
  }

  @Test
  void deserialisesBedrockConfigurationWithStaticCredentialsAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "bedrock",
          "bedrock": {
            "region": "eu-central-1",
            "authentication": { "type": "credentials", "accessKey": "AKIA123", "secretKey": "secret123" },
            "model": {
              "model": "us.amazon.nova-2-lite-v1:0",
              "parameters": {
                "maxTokens": 1024,
                "temperature": 0.7,
                "topP": 0.9,
                "promptCaching": { "enabled": true }
              }
            }
          }
        }
        """;

    final ProviderConfiguration parsed = mapper.readValue(json, ProviderConfiguration.class);

    assertThat(parsed).isInstanceOf(BedrockChatModelConfiguration.class);
    assertThat(parsed.provider()).isEqualTo("bedrock");
    assertThat(parsed.model()).isEqualTo("us.amazon.nova-2-lite-v1:0");

    final BedrockChatModelConfiguration bedrock = (BedrockChatModelConfiguration) parsed;
    assertThat(bedrock.bedrock().region()).isEqualTo("eu-central-1");
    assertThat(bedrock.bedrock().endpoint()).isNull();
    assertThat(bedrock.bedrock().authentication())
        .isEqualTo(
            new AwsAuthentication.AwsStaticCredentialsAuthentication("AKIA123", "secret123"));

    final BedrockModelParameters parameters = bedrock.bedrock().model().parameters();
    assertThat(parameters).isNotNull();
    assertThat(parameters.promptCaching().enabled()).isTrue();
    assertThat(parameters.maxTokens()).isEqualTo(1024);
    assertThat(parameters.temperature()).isEqualTo(0.7);
    assertThat(parameters.topP()).isEqualTo(0.9);

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void deserialisesBedrockConfigurationWithCustomEndpointAndApiKeyAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "bedrock",
          "bedrock": {
            "region": "eu-central-1",
            "endpoint": "https://vpce-example.vpce.amazonaws.com",
            "authentication": { "type": "apiKey", "apiKey": "bedrock-secret-key" },
            "model": { "model": "us.amazon.nova-2-lite-v1:0" }
          }
        }
        """;

    final BedrockChatModelConfiguration parsed =
        (BedrockChatModelConfiguration) mapper.readValue(json, ProviderConfiguration.class);

    assertThat(parsed.bedrock().endpoint()).isEqualTo("https://vpce-example.vpce.amazonaws.com");
    assertThat(parsed.bedrock().authentication())
        .isEqualTo(new AwsAuthentication.AwsApiKeyAuthentication("bedrock-secret-key"));

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void bedrockConnectionRedactsHeadersQueryParametersAndBodyPropertiesInToString() {
    final var connection =
        new BedrockConnection(
            "eu-central-1",
            null,
            new AwsAuthentication.AwsStaticCredentialsAuthentication("AKIA123", "secret123"),
            Map.of("X-Custom-Header", "some-header-value"),
            Map.of("some-query-param", "some-query-value"),
            Map.of("some-body-param", "some-body-value"),
            null,
            new BedrockModel("us.amazon.nova-2-lite-v1:0", null));

    assertThat(connection.toString())
        .doesNotContain("some-header-value")
        .doesNotContain("some-query-value")
        .doesNotContain("some-body-value")
        .contains("headers={X-Custom-Header=[REDACTED]}")
        .contains("queryParameters={some-query-param=[REDACTED]}")
        .contains("bodyProperties={some-body-param=[REDACTED]}");
  }

  @Test
  void requiredBedrockFieldsAreEnforced() {
    final var config =
        new BedrockChatModelConfiguration(
            new BedrockConnection(
                "",
                null,
                new AwsAuthentication.AwsStaticCredentialsAuthentication("", ""),
                null,
                null,
                null,
                null,
                new BedrockModel("us.amazon.nova-2-lite-v1:0", null)));

    final var violations = validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString()).isEqualTo("bedrock.region");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            })
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("bedrock.authentication.accessKey");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            })
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("bedrock.authentication.secretKey");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            });
  }

  @Test
  void apiKeyAuthenticationRejectsBlankApiKey() {
    final var config = bedrockConfig(new AwsAuthentication.AwsApiKeyAuthentication("  "));

    final var violations = validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString()).isEqualTo("bedrock.authentication.apiKey");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            });
  }

  @Test
  void bedrockModelRejectsBlankModelId() {
    final var config =
        new BedrockChatModelConfiguration(
            new BedrockConnection(
                "eu-central-1",
                null,
                new AwsAuthentication.AwsDefaultCredentialsChainAuthentication(),
                null,
                null,
                null,
                null,
                new BedrockModel("", null)));

    final var violations = validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString()).isEqualTo("bedrock.model.model");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
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

  @Test
  void staticCredentialsAllowedOnSaaS() {
    environment.set(ConnectorUtils.CONNECTOR_RUNTIME_SAAS_ENV_VARIABLE, "true");
    final var config =
        bedrockConfig(
            new AwsAuthentication.AwsStaticCredentialsAuthentication("AKIA123", "secret123"));

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void temperatureAcceptsValuesAboveOneWithNoUpperBound() {
    final var parameters = new BedrockModelParameters(null, null, 1.5, null);
    final var config = bedrockConfigWithParameters(parameters);

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void temperatureRejectsValuesBelowZero() {
    final var parameters = new BedrockModelParameters(null, null, -0.1, null);
    final var config = bedrockConfigWithParameters(parameters);

    final var violations = validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("bedrock.model.parameters.temperature");
              assertThat(v.getMessage()).isEqualTo("must be greater than or equal to 0.0");
            });
  }

  @Test
  void topPRejectsValuesAboveOne() {
    final var parameters = new BedrockModelParameters(null, null, null, 1.5);
    final var config = bedrockConfigWithParameters(parameters);

    final var violations = validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString()).isEqualTo("bedrock.model.parameters.topP");
              assertThat(v.getMessage()).isEqualTo("must be less than or equal to 1.0");
            });
  }

  @Test
  void maxTokensRejectsZero() {
    final var parameters = new BedrockModelParameters(null, 0, null, null);
    final var config = bedrockConfigWithParameters(parameters);

    final var violations = validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("bedrock.model.parameters.maxTokens");
              assertThat(v.getMessage()).isEqualTo("must be greater than or equal to 1");
            });
  }

  @Test
  void validBedrockConfigurationHasNoViolations() {
    final var config =
        bedrockConfig(new AwsAuthentication.AwsDefaultCredentialsChainAuthentication());

    assertThat(validator.validate(config)).isEmpty();
  }

  private static BedrockChatModelConfiguration bedrockConfig(AwsAuthentication authentication) {
    return new BedrockChatModelConfiguration(
        new BedrockConnection(
            "eu-central-1",
            null,
            authentication,
            null,
            null,
            null,
            null,
            new BedrockModel("us.amazon.nova-2-lite-v1:0", null)));
  }

  private static BedrockChatModelConfiguration bedrockConfigWithParameters(
      BedrockModelParameters parameters) {
    return new BedrockChatModelConfiguration(
        new BedrockConnection(
            "eu-central-1",
            null,
            new AwsAuthentication.AwsDefaultCredentialsChainAuthentication(),
            null,
            null,
            null,
            null,
            new BedrockModel("us.amazon.nova-2-lite-v1:0", parameters)));
  }
}
