/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseModel.BedrockConverseModelParameters;
import io.camunda.connector.agenticai.aiagent.util.ConnectorUtils;
import io.camunda.connector.aws.model.impl.AwsCredentialConfiguration;
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
class BedrockConverseChatModelConfigurationTest {

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
            "authentication": {
              "type": "awsIam",
              "method": { "type": "credentials", "accessKey": "AKIA123", "secretKey": "secret123" }
            },
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

    assertThat(parsed).isInstanceOf(BedrockConverseChatModelConfiguration.class);
    assertThat(parsed.provider()).isEqualTo("bedrock");
    assertThat(parsed.descriptiveProvider()).isEqualTo(parsed.provider());
    assertThat(parsed.model()).isEqualTo("us.amazon.nova-2-lite-v1:0");

    final BedrockConverseChatModelConfiguration bedrock =
        (BedrockConverseChatModelConfiguration) parsed;
    assertThat(bedrock.bedrock().region()).isEqualTo("eu-central-1");
    assertThat(bedrock.bedrock().endpoint()).isNull();
    assertThat(bedrock.bedrock().authentication()).isEqualTo(iamStatic("AKIA123", "secret123"));

    final BedrockConverseModelParameters parameters = bedrock.bedrock().model().parameters();
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

    final BedrockConverseChatModelConfiguration parsed =
        (BedrockConverseChatModelConfiguration) mapper.readValue(json, ProviderConfiguration.class);

    assertThat(parsed.bedrock().endpoint()).isEqualTo("https://vpce-example.vpce.amazonaws.com");
    assertThat(parsed.bedrock().authentication()).isEqualTo(apiKeyInline("bedrock-secret-key"));

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void deserialisesBedrockConfigurationWithBoundAwsCredentialAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "bedrock",
          "bedrock": {
            "region": "eu-central-1",
            "authentication": {
              "type": "awsIam",
              "awsCredential": {
                "authentication": { "type": "credentials", "accessKey": "AKIA-bound", "secretKey": "secret-bound" },
                "region": "eu-central-1"
              }
            },
            "model": { "model": "us.amazon.nova-2-lite-v1:0" }
          }
        }
        """;

    final BedrockConverseChatModelConfiguration parsed =
        (BedrockConverseChatModelConfiguration) mapper.readValue(json, ProviderConfiguration.class);

    final var authentication =
        (AwsAuthentication.AwsIamAuthentication) parsed.bedrock().authentication();
    assertThat(authentication.awsCredential()).isNotNull();
    assertThat(authentication.method()).isNull();
    assertThat(authentication.getMethodWhenNoCredentialBound()).isNull();
    assertThat(authentication.isAuthenticationPresent()).isTrue();

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void deserialisesBedrockConfigurationWithBoundBedrockApiKeyCredentialAndRoundTrips()
      throws Exception {
    final String json =
        """
        {
          "type": "bedrock",
          "bedrock": {
            "region": "eu-central-1",
            "authentication": {
              "type": "apiKey",
              "bedrockApiKeyCredential": { "apiKey": "bedrock-bound-key" }
            },
            "model": { "model": "us.amazon.nova-2-lite-v1:0" }
          }
        }
        """;

    final BedrockConverseChatModelConfiguration parsed =
        (BedrockConverseChatModelConfiguration) mapper.readValue(json, ProviderConfiguration.class);

    final var authentication =
        (AwsAuthentication.AwsApiKeyAuthentication) parsed.bedrock().authentication();
    assertThat(authentication.bedrockApiKeyCredential()).isNotNull();
    assertThat(authentication.apiKey()).isNull();
    assertThat(authentication.effectiveApiKey()).isEqualTo("bedrock-bound-key");
    assertThat(authentication.isApiKeyPresent()).isTrue();

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void bedrockConnectionRedactsHeadersQueryParametersAndBodyPropertiesInToString() {
    final var connection =
        new BedrockConverseConnection(
            "eu-central-1",
            null,
            iamStatic("AKIA123", "secret123"),
            Map.of("X-Custom-Header", "some-header-value"),
            Map.of("some-query-param", "some-query-value"),
            Map.of("some-body-param", "some-body-value"),
            null,
            new BedrockConverseModel("us.amazon.nova-2-lite-v1:0", null));

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
        new BedrockConverseChatModelConfiguration(
            new BedrockConverseConnection(
                "",
                null,
                iamStatic("", ""),
                null,
                null,
                null,
                null,
                new BedrockConverseModel("us.amazon.nova-2-lite-v1:0", null)));

    final var violations = validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString()).isEqualTo("bedrock.region");
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
  void apiKeyAuthenticationRejectsBlankApiKey() {
    final var config = bedrockConfig(apiKeyInline("  "));

    final var violations = validator.validate(config);

    assertThat(violations)
        .extracting(ConstraintViolation::getMessage)
        .contains("An AWS Bedrock API key is required from the credential or element template");
  }

  @Test
  void apiKeyAuthenticationRejectedWhenNeitherCredentialNorInlineKeyPresent() {
    final var config = bedrockConfig(new AwsAuthentication.AwsApiKeyAuthentication(null, null));

    assertThat(validator.validate(config))
        .extracting(ConstraintViolation::getMessage)
        .contains("An AWS Bedrock API key is required from the credential or element template");
  }

  @Test
  void apiKeyAuthenticationPresentWhenOnlyCredentialBound() {
    final var config =
        bedrockConfig(
            new AwsAuthentication.AwsApiKeyAuthentication(
                new BedrockApiKeyCredential("credential-key"), null));

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void apiKeyAuthenticationCredentialTakesPrecedenceOverInlineKey() {
    final var authentication =
        new AwsAuthentication.AwsApiKeyAuthentication(
            new BedrockApiKeyCredential("credential-key"), "inline-key");

    assertThat(authentication.effectiveApiKey()).isEqualTo("credential-key");
    assertThat(validator.validate(bedrockConfig(authentication))).isEmpty();
  }

  @Test
  void bedrockModelRejectsBlankModelId() {
    final var config =
        new BedrockConverseChatModelConfiguration(
            new BedrockConverseConnection(
                "eu-central-1",
                null,
                iamDefaultChain(),
                null,
                null,
                null,
                null,
                new BedrockConverseModel("", null)));

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
  void staticCredentialsAllowedOnSaaS() {
    environment.set(ConnectorUtils.CONNECTOR_RUNTIME_SAAS_ENV_VARIABLE, "true");
    final var config = bedrockConfig(iamStatic("AKIA123", "secret123"));

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void iamAuthenticationPresentWhenOnlyCredentialBound() {
    final var authentication = iamCredential(staticAwsCredential());

    assertThat(authentication.getMethodWhenNoCredentialBound()).isNull();
    assertThat(authentication.isAuthenticationPresent()).isTrue();
    assertThat(validator.validate(bedrockConfig(authentication))).isEmpty();
  }

  @Test
  void iamAuthenticationRejectedWhenNeitherCredentialNorInlineMethodPresent() {
    final var authentication = new AwsAuthentication.AwsIamAuthentication(null, null);

    assertThat(validator.validate(bedrockConfig(authentication)))
        .extracting(ConstraintViolation::getMessage)
        .contains("AWS IAM authentication is required from the credential or element template");
  }

  @Test
  void iamAuthenticationCredentialTakesPrecedenceOverInlineMethod() {
    final var authentication =
        new AwsAuthentication.AwsIamAuthentication(
            staticAwsCredential(),
            new AwsAuthentication.AwsIamAuthenticationMethod.AwsStaticCredentialsAuthentication(
                "inline-access", "inline-secret"));

    assertThat(authentication.getMethodWhenNoCredentialBound()).isNull();
    assertThat(authentication.isAuthenticationPresent()).isTrue();
    assertThat(validator.validate(bedrockConfig(authentication))).isEmpty();
  }

  @Test
  void usesDefaultCredentialsChainTrueForInlineDefaultChain() {
    assertThat(iamDefaultChain().usesDefaultCredentialsChain()).isTrue();
  }

  @Test
  void usesDefaultCredentialsChainTrueForBoundDefaultChainCredential() {
    assertThat(iamCredential(defaultChainAwsCredential()).usesDefaultCredentialsChain()).isTrue();
  }

  @Test
  void usesDefaultCredentialsChainFalseForStaticCredentialsOrApiKey() {
    assertThat(iamStatic("AKIA123", "secret123").usesDefaultCredentialsChain()).isFalse();
    assertThat(iamCredential(staticAwsCredential()).usesDefaultCredentialsChain()).isFalse();
  }

  @Test
  void boundDefaultCredentialsChainRejectedOnSaaS() {
    environment.set(ConnectorUtils.CONNECTOR_RUNTIME_SAAS_ENV_VARIABLE, "true");
    final var config = bedrockConfig(iamCredential(defaultChainAwsCredential()));

    assertThat(validator.validate(config))
        .extracting(ConstraintViolation::getMessage)
        .contains("AWS default credentials chain is not supported on SaaS");
  }

  @Test
  void boundStaticCredentialAllowedOnSaaS() {
    environment.set(ConnectorUtils.CONNECTOR_RUNTIME_SAAS_ENV_VARIABLE, "true");
    final var config = bedrockConfig(iamCredential(staticAwsCredential()));

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void temperatureAcceptsValuesAboveOneWithNoUpperBound() {
    final var parameters = new BedrockConverseModelParameters(null, null, 1.5, null);
    final var config = bedrockConfigWithParameters(parameters);

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void temperatureRejectsValuesBelowZero() {
    final var parameters = new BedrockConverseModelParameters(null, null, -0.1, null);
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
    final var parameters = new BedrockConverseModelParameters(null, null, null, 1.5);
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
    final var parameters = new BedrockConverseModelParameters(null, 0, null, null);
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
    final var config = bedrockConfig(iamDefaultChain());

    assertThat(validator.validate(config)).isEmpty();
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
      AwsCredentialConfiguration awsCredential) {
    return new AwsAuthentication.AwsIamAuthentication(awsCredential, null);
  }

  private static AwsAuthentication.AwsApiKeyAuthentication apiKeyInline(String apiKey) {
    return new AwsAuthentication.AwsApiKeyAuthentication(null, apiKey);
  }

  private static AwsCredentialConfiguration staticAwsCredential() {
    return new AwsCredentialConfiguration(
        new io.camunda.connector.aws.model.impl.AwsAuthentication
            .AwsStaticCredentialsAuthentication("AKIA-bound", "secret-bound"),
        "eu-central-1");
  }

  private static AwsCredentialConfiguration defaultChainAwsCredential() {
    return new AwsCredentialConfiguration(
        new io.camunda.connector.aws.model.impl.AwsAuthentication
            .AwsDefaultCredentialsChainAuthentication(),
        "eu-central-1");
  }

  private static BedrockConverseChatModelConfiguration bedrockConfig(
      AwsAuthentication authentication) {
    return new BedrockConverseChatModelConfiguration(
        new BedrockConverseConnection(
            "eu-central-1",
            null,
            authentication,
            null,
            null,
            null,
            null,
            new BedrockConverseModel("us.amazon.nova-2-lite-v1:0", null)));
  }

  private static BedrockConverseChatModelConfiguration bedrockConfigWithParameters(
      BedrockConverseModelParameters parameters) {
    return new BedrockConverseChatModelConfiguration(
        new BedrockConverseConnection(
            "eu-central-1",
            null,
            iamDefaultChain(),
            null,
            null,
            null,
            null,
            new BedrockConverseModel("us.amazon.nova-2-lite-v1:0", parameters)));
  }
}
