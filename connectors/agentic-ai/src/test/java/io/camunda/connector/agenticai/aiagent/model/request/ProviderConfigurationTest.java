/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.BedrockConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication.ApplicationDefaultCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GoogleVertexAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GoogleVertexAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GoogleVertexAiModel.GoogleVertexAiModelParameters;
import io.camunda.connector.agenticai.util.ConnectorUtils;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith({SpringExtension.class, SystemStubsExtension.class})
@Import(ValidationAutoConfiguration.class)
class ProviderConfigurationTest {

  @Autowired private Validator validator;
  @SystemStub private EnvironmentVariables environment;

  @BeforeEach
  void setUp() {
    environment.set(ConnectorUtils.CONNECTOR_RUNTIME_SAAS_ENV_VARIABLE, null);
  }

  @Nested
  class BedrockConnectionTest {

    @Test
    void validationShouldFail_WhenSaaSAndDefaultCredentialChainUsed() {
      simulateSaaSEnvironment();
      final var connection =
          createConnection(new AwsAuthentication.AwsDefaultCredentialsChainAuthentication());
      assertThat(validator.validate(connection))
          .hasSize(1)
          .extracting(ConstraintViolation::getMessage)
          .containsExactly("AWS default credentials chain is not supported on SaaS");
    }

    @Test
    void validationShouldSucceed_WhenNotSaaSAndDefaultCredentialChainUsed() {
      final var connection =
          createConnection(new AwsAuthentication.AwsDefaultCredentialsChainAuthentication());
      assertThat(validator.validate(connection)).isEmpty();
    }

    @Test
    void validationShouldSucceed_WhenSaaSAndNotDefaultCredentialChainUsed() {
      simulateSaaSEnvironment();
      final var connection =
          createConnection(new AwsAuthentication.AwsStaticCredentialsAuthentication("key", "secret"));
      assertThat(validator.validate(connection)).isEmpty();
    }

    @Test
    void validationShouldSucceed_WhenNotSaaSAndNotDefaultCredentialChainUsed() {
      final var connection =
          createConnection(new AwsAuthentication.AwsStaticCredentialsAuthentication("key", "secret"));
      assertThat(validator.validate(connection)).isEmpty();
    }

    private BedrockConnection createConnection(AwsAuthentication authentication) {
      return new BedrockConnection(
          "eu-central-1",
          null,
          authentication,
          new BedrockProviderConfiguration.BedrockModel(
              "test",
              new BedrockProviderConfiguration.BedrockModel.BedrockModelParameters(
                  null, null, null)));
    }
  }

  @Nested
  class GoogleVertexAiConnectionTest {

    @Test
    void validationShouldSucceed_WhenNotSaaS() {
      final var connection = createConnectionWithApplicationDefaultCredentials();
      assertThat(validator.validate(connection)).isEmpty();
    }

    @Test
    void validationShouldFail_WhenSaaS() {
      simulateSaaSEnvironment();
      final var connection = createConnectionWithApplicationDefaultCredentials();
      assertThat(validator.validate(connection))
          .hasSize(1)
          .extracting(ConstraintViolation::getMessage)
          .containsExactly("Google Vertex AI is not supported on SaaS");
    }

    @Test
    void validationShouldSucceed_WhenSaaSAndServiceAccountCredentialsUsed() {
      simulateSaaSEnvironment();
      final var connection = createConnectionWithServiceAccountCredentials();
      assertThat(validator.validate(connection)).isEmpty();
    }

    @Test
    void validationShouldSucceed_WhenNotSaaSAndServiceAccountCredentialsUsed() {
      final var connection = createConnectionWithServiceAccountCredentials();
      assertThat(validator.validate(connection)).isEmpty();
    }

    private static GoogleVertexAiConnection createConnectionWithApplicationDefaultCredentials() {
      return new GoogleVertexAiConnection(
          "my-project-id",
          "us-central1",
          new ApplicationDefaultCredentialsAuthentication(),
          new GoogleVertexAiModel(
              "gemini-1.5-flash", new GoogleVertexAiModelParameters(null, null, null, null)));
    }

    private static GoogleVertexAiConnection createConnectionWithServiceAccountCredentials() {
      return new GoogleVertexAiConnection(
          "my-project-id",
          "us-central1",
          new ServiceAccountCredentialsAuthentication("{}"),
          new GoogleVertexAiModel(
              "gemini-1.5-flash", new GoogleVertexAiModelParameters(null, null, null, null)));
    }
  }

  private void simulateSaaSEnvironment() {
    environment.set(ConnectorUtils.CONNECTOR_RUNTIME_SAAS_ENV_VARIABLE, "true");
  }
}
