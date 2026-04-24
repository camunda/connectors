/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.AwsAgentCoreAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.AwsAgentCoreMemoryStorageConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AzureAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AzureOpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AzureOpenAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.BedrockConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication.ApplicationDefaultCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GoogleVertexAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GoogleVertexAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GoogleVertexAiModel.GoogleVertexAiModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.provider.MistralProviderConfiguration.MistralAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.MistralProviderConfiguration.MistralConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.MistralProviderConfiguration.MistralModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration.OpenAiCompatibleAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration.OpenAiCompatibleConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration.OpenAiCompatibleModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.util.ConnectorUtils;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith({SpringExtension.class, SystemStubsExtension.class})
@Import(ValidationAutoConfiguration.class)
class ProviderConfigurationTest {

  private static final TimeoutConfiguration TIMEOUT =
      new TimeoutConfiguration(Duration.ofSeconds(30));
  private static final String HTTP_URL_VALIDATION_MESSAGE = "Must be an HTTP or HTTPS URL";

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
          createConnection(null, new AwsAuthentication.AwsDefaultCredentialsChainAuthentication());
      assertThat(validator.validate(connection))
          .hasSize(1)
          .extracting(ConstraintViolation::getMessage)
          .containsExactly("AWS default credentials chain is not supported on SaaS");
    }

    @Test
    void validationShouldSucceed_WhenNotSaaSAndDefaultCredentialChainUsed() {
      final var connection =
          createConnection(null, new AwsAuthentication.AwsDefaultCredentialsChainAuthentication());
      assertThat(validator.validate(connection)).isEmpty();
    }

    @Test
    void validationShouldSucceed_WhenSaaSAndNotDefaultCredentialChainUsed() {
      simulateSaaSEnvironment();
      final var connection =
          createConnection(
              null, new AwsAuthentication.AwsStaticCredentialsAuthentication("key", "secret"));
      assertThat(validator.validate(connection)).isEmpty();
    }

    @Test
    void validationShouldSucceed_WhenNotSaaSAndNotDefaultCredentialChainUsed() {
      final var connection =
          createConnection(
              null, new AwsAuthentication.AwsStaticCredentialsAuthentication("key", "secret"));
      assertThat(validator.validate(connection)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.aiagent.model.request.ProviderConfigurationTest#validHttpUrls")
    @NullSource
    void shouldAcceptValidEndpoint(String endpoint) {
      var connection =
          createConnection(
              endpoint, new AwsAuthentication.AwsStaticCredentialsAuthentication("key", "secret"));
      assertThat(validator.validate(connection)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.aiagent.model.request.ProviderConfigurationTest#invalidHttpUrls")
    void shouldRejectInvalidEndpoint(String endpoint) {
      var connection =
          createConnection(
              endpoint, new AwsAuthentication.AwsStaticCredentialsAuthentication("key", "secret"));
      assertThat(validator.validate(connection))
          .extracting(ConstraintViolation::getMessage)
          .contains(HTTP_URL_VALIDATION_MESSAGE);
    }

    private BedrockConnection createConnection(String endpoint, AwsAuthentication authentication) {
      return new BedrockConnection(
          "eu-central-1",
          endpoint,
          authentication,
          TIMEOUT,
          new BedrockProviderConfiguration.BedrockModel(
              "test",
              new BedrockProviderConfiguration.BedrockModel.BedrockModelParameters(
                  null, null, null)));
    }
  }

  @Nested
  class AnthropicConnectionTest {

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.aiagent.model.request.ProviderConfigurationTest#validHttpUrls")
    @NullSource
    void shouldAcceptValidEndpoint(String endpoint) {
      var connection =
          new AnthropicConnection(
              endpoint,
              new AnthropicAuthentication("key"),
              TIMEOUT,
              new AnthropicModel("model", null));
      assertThat(validator.validate(connection)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.aiagent.model.request.ProviderConfigurationTest#invalidHttpUrls")
    void shouldRejectInvalidEndpoint(String endpoint) {
      var connection =
          new AnthropicConnection(
              endpoint,
              new AnthropicAuthentication("key"),
              TIMEOUT,
              new AnthropicModel("model", null));
      assertThat(validator.validate(connection))
          .extracting(ConstraintViolation::getMessage)
          .contains(HTTP_URL_VALIDATION_MESSAGE);
    }
  }

  @Nested
  class AzureOpenAiConnectionTest {

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.aiagent.model.request.ProviderConfigurationTest#validHttpUrls")
    void shouldAcceptValidEndpoint(String endpoint) {
      var connection =
          new AzureOpenAiConnection(
              endpoint,
              new AzureAuthentication.AzureApiKeyAuthentication("key"),
              TIMEOUT,
              new AzureOpenAiModel("deployment", null));
      assertThat(validator.validate(connection)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.aiagent.model.request.ProviderConfigurationTest#invalidHttpUrls")
    void shouldRejectInvalidUrlEndpoint(String endpoint) {
      var connection =
          new AzureOpenAiConnection(
              endpoint,
              new AzureAuthentication.AzureApiKeyAuthentication("key"),
              TIMEOUT,
              new AzureOpenAiModel("deployment", null));
      assertThat(validator.validate(connection))
          .extracting(ConstraintViolation::getMessage)
          .contains(HTTP_URL_VALIDATION_MESSAGE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldRejectBlankEndpoint(String endpoint) {
      var connection =
          new AzureOpenAiConnection(
              endpoint,
              new AzureAuthentication.AzureApiKeyAuthentication("key"),
              TIMEOUT,
              new AzureOpenAiModel("deployment", null));
      assertThat(validator.validate(connection))
          .extracting(ConstraintViolation::getMessage)
          .contains("must not be blank");
    }
  }

  @Nested
  class OpenAiCompatibleConnectionTest {

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.aiagent.model.request.ProviderConfigurationTest#validHttpUrls")
    void shouldAcceptValidEndpoint(String endpoint) {
      var connection =
          new OpenAiCompatibleConnection(
              endpoint,
              new OpenAiCompatibleAuthentication("key"),
              null,
              null,
              TIMEOUT,
              new OpenAiCompatibleModel("model", null));
      assertThat(validator.validate(connection)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.aiagent.model.request.ProviderConfigurationTest#invalidHttpUrls")
    void shouldRejectInvalidUrlEndpoint(String endpoint) {
      var connection =
          new OpenAiCompatibleConnection(
              endpoint,
              new OpenAiCompatibleAuthentication("key"),
              null,
              null,
              TIMEOUT,
              new OpenAiCompatibleModel("model", null));
      assertThat(validator.validate(connection))
          .extracting(ConstraintViolation::getMessage)
          .contains(HTTP_URL_VALIDATION_MESSAGE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldRejectBlankEndpoint(String endpoint) {
      var connection =
          new OpenAiCompatibleConnection(
              endpoint,
              new OpenAiCompatibleAuthentication("key"),
              null,
              null,
              TIMEOUT,
              new OpenAiCompatibleModel("model", null));
      assertThat(validator.validate(connection))
          .extracting(ConstraintViolation::getMessage)
          .contains("must not be blank");
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

  @Nested
  class MistralConnectionTest {

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.aiagent.model.request.ProviderConfigurationTest#validHttpUrls")
    void shouldAcceptValidEndpoint(String endpoint) {
      var connection =
          new MistralConnection(
              endpoint,
              new MistralAuthentication("key"),
              TIMEOUT,
              new MistralModel("mistral-large-latest", null));
      assertThat(validator.validate(connection)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.aiagent.model.request.ProviderConfigurationTest#invalidHttpUrls")
    void shouldRejectInvalidUrlEndpoint(String endpoint) {
      var connection =
          new MistralConnection(
              endpoint,
              new MistralAuthentication("key"),
              TIMEOUT,
              new MistralModel("mistral-large-latest", null));
      assertThat(validator.validate(connection))
          .extracting(ConstraintViolation::getMessage)
          .contains(HTTP_URL_VALIDATION_MESSAGE);
    }

    @ParameterizedTest
    @NullSource
    void shouldAcceptNullEndpoint(String endpoint) {
      // Null endpoint is allowed for Mistral (defaults to https://api.mistral.ai/v1)
      var connection =
          new MistralConnection(
              endpoint,
              new MistralAuthentication("key"),
              TIMEOUT,
              new MistralModel("mistral-large-latest", null));
      assertThat(validator.validate(connection)).isEmpty();
    }
  }

  @Nested
  class AgentCoreMemoryConfigurationTest {

    @Test
    void validationShouldFail_WhenSaaSAndDefaultCredentialChainUsed() {
      simulateSaaSEnvironment();
      final var config =
          createConfig(new AwsAgentCoreAuthentication.AwsDefaultCredentialsChainAuthentication());
      assertThat(validator.validate(config))
          .hasSize(1)
          .extracting(ConstraintViolation::getMessage)
          .containsExactly("AWS default credentials chain is not supported on SaaS");
    }

    @Test
    void validationShouldSucceed_WhenNotSaaSAndDefaultCredentialChainUsed() {
      final var config =
          createConfig(new AwsAgentCoreAuthentication.AwsDefaultCredentialsChainAuthentication());
      assertThat(validator.validate(config)).isEmpty();
    }

    @Test
    void validationShouldSucceed_WhenSaaSAndStaticCredentialsUsed() {
      simulateSaaSEnvironment();
      final var config =
          createConfig(
              new AwsAgentCoreAuthentication.AwsStaticCredentialsAuthentication("key", "secret"));
      assertThat(validator.validate(config)).isEmpty();
    }

    @Test
    void validationShouldSucceed_WhenNotSaaSAndStaticCredentialsUsed() {
      final var config =
          createConfig(
              new AwsAgentCoreAuthentication.AwsStaticCredentialsAuthentication("key", "secret"));
      assertThat(validator.validate(config)).isEmpty();
    }

    private AwsAgentCoreMemoryStorageConfiguration createConfig(
        AwsAgentCoreAuthentication authentication) {
      return new AwsAgentCoreMemoryStorageConfiguration(
          "us-east-1", null, authentication, "mem-123", "actor-1");
    }
  }

  private void simulateSaaSEnvironment() {
    environment.set(ConnectorUtils.CONNECTOR_RUNTIME_SAAS_ENV_VARIABLE, "true");
  }

  static Stream<String> validHttpUrls() {
    return Stream.of(
        "https://api.example.com", "http://localhost:8080", "https://my-endpoint.local/v1/chat");
  }

  static Stream<String> invalidHttpUrls() {
    return Stream.of("not-a-url", "ftp://files.example.com", "http://", "https://", "  ");
  }
}
