/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.models;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.model.embedding.models.GoogleVertexAiEmbeddingModelProvider.GoogleVertexAiAuthentication;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
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
class GoogleVertexAiEmbeddingModelProviderTest {

  public static final String RUNTIME_SAAS_EVN_VAR = "CAMUNDA_CONNECTOR_RUNTIME_SAAS";
  @Autowired private Validator validator;
  @SystemStub private EnvironmentVariables environment;

  @BeforeEach
  void setUp() {
    environment.set(RUNTIME_SAAS_EVN_VAR, null);
  }

  @Test
  void validationShouldFail_WhenSaaSAndDefaultCredentialChainUsed() {
    environment.set("CAMUNDA_CONNECTOR_RUNTIME_SAAS", "true");
    final var provider = createProviderWithDefaultCredentials();
    final var violations = validator.validate(provider);
    assertThat(violations)
        .hasSize(1)
        .extracting(ConstraintViolation::getMessage)
        .containsExactly("Google Vertex AI is not supported on SaaS");
  }

  @Test
  void validationShouldSucceed_WhenNotSaaSAndDefaultCredentialChainUsed() {
    final var provider = createProviderWithDefaultCredentials();
    final var violations = validator.validate(provider);
    assertThat(violations).isEmpty();
  }

  @Test
  void validationShouldSucceed_WhenSaaSAndServiceAccountCredentialsUsed() {
    environment.set(RUNTIME_SAAS_EVN_VAR, "true");
    final var provider = createProviderWithServiceAccountCredentials();
    final var violations = validator.validate(provider);
    assertThat(violations).isEmpty();
  }

  @Test
  void validationShouldSucceed_WhenNotSaaSAndServiceAccountCredentialsUsed() {
    final var provider = createProviderWithServiceAccountCredentials();
    final var violations = validator.validate(provider);
    assertThat(violations).isEmpty();
  }

  private GoogleVertexAiEmbeddingModelProvider createProviderWithDefaultCredentials() {
    return new GoogleVertexAiEmbeddingModelProvider(
        "my-project-id",
        "us-central1",
        new GoogleVertexAiAuthentication.ApplicationDefaultCredentialsAuthentication(),
        "embedding-model",
        768,
        "google",
        3);
  }

  private GoogleVertexAiEmbeddingModelProvider createProviderWithServiceAccountCredentials() {
    return new GoogleVertexAiEmbeddingModelProvider(
        "my-project-id",
        "us-central1",
        new GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication("{}"),
        "embedding-model",
        768,
        "google",
        3);
  }
}
