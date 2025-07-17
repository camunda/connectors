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

  @Nested
  class BedrockConnectionTest {

    @BeforeEach
    void setUp() {
      environment.set("CAMUNDA_CONNECTOR_RUNTIME_SAAS", null);
    }

    @Test
    void validationShouldFail_WhenSaaSAndDefaultCredentialChainUsed() {
      environment.set("CAMUNDA_CONNECTOR_RUNTIME_SAAS", "true");

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
      environment.set("CAMUNDA_CONNECTOR_RUNTIME_SAAS", "true");

      final var connection =
          createConnection(
              new AwsAuthentication.AwsStaticCredentialsAuthentication("key", "secret"));
      assertThat(validator.validate(connection)).isEmpty();
    }

    @Test
    void validationShouldSucceed_WhenNotSaaSAndNotDefaultCredentialChainUsed() {
      final var connection =
          createConnection(
              new AwsAuthentication.AwsStaticCredentialsAuthentication("key", "secret"));
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
}
