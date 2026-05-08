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
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicAuthentication.AnthropicApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicAuthentication.AnthropicClientCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicBackend;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.BedrockConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleGenAiProviderConfiguration.GoogleGenAiAuthentication.ApplicationDefaultCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleGenAiProviderConfiguration.GoogleGenAiAuthentication.ServiceAccountCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleGenAiProviderConfiguration.GoogleGenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleGenAiProviderConfiguration.GoogleGenAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleGenAiProviderConfiguration.GoogleGenAiModel.GoogleGenAiModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiAuthentication.OpenAiApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiAuthentication.OpenAiClientCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiModel;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
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

    @Test
    void validationShouldFail_WhenAnthropicModelUsedWithBedrock() {
      var connection =
          createConnection(
              null,
              new AwsAuthentication.AwsStaticCredentialsAuthentication("key", "secret"),
              "anthropic.claude-sonnet-4-5");
      assertThat(validator.validate(connection))
          .hasSize(1)
          .extracting(ConstraintViolation::getMessage)
          .containsExactly(
              "Anthropic models must be configured via the Anthropic provider with backend = BEDROCK");
    }

    @Test
    void validationShouldSucceed_WhenNonAnthropicModelUsedWithBedrock() {
      var connection =
          createConnection(
              null,
              new AwsAuthentication.AwsStaticCredentialsAuthentication("key", "secret"),
              "amazon.nova-pro-v1:0");
      assertThat(validator.validate(connection)).isEmpty();
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

    private BedrockConnection createConnection(
        String endpoint, AwsAuthentication authentication, String modelId) {
      return new BedrockConnection(
          "eu-central-1",
          endpoint,
          authentication,
          TIMEOUT,
          new BedrockProviderConfiguration.BedrockModel(
              modelId,
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
              null,
              new AnthropicApiKeyAuthentication("key"),
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
              null,
              new AnthropicApiKeyAuthentication("key"),
              TIMEOUT,
              new AnthropicModel("model", null));
      assertThat(validator.validate(connection))
          .extracting(ConstraintViolation::getMessage)
          .contains(HTTP_URL_VALIDATION_MESSAGE);
    }

    @Test
    void validationShouldSucceed_WhenApiKeyAuthenticationUsed() {
      var connection =
          new AnthropicConnection(
              null,
              null,
              new AnthropicApiKeyAuthentication("my-api-key"),
              TIMEOUT,
              new AnthropicModel("model", null));
      assertThat(validator.validate(connection)).isEmpty();
    }

    @Test
    void validationShouldSucceed_WhenClientCredentialsAuthUsedWithFoundryBackend() {
      var connection =
          new AnthropicConnection(
              null,
              AnthropicBackend.FOUNDRY,
              new AnthropicClientCredentialsAuthentication(
                  "client-id", "client-secret", "tenant-id", null),
              TIMEOUT,
              new AnthropicModel("model", null));
      assertThat(validator.validate(connection)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
        value = AnthropicBackend.class,
        names = {"DIRECT", "BEDROCK", "VERTEX"})
    void validationShouldFail_WhenClientCredentialsAuthUsedWithNonFoundryBackend(
        AnthropicBackend backend) {
      var connection =
          new AnthropicConnection(
              null,
              backend,
              new AnthropicClientCredentialsAuthentication(
                  "client-id", "client-secret", "tenant-id", null),
              TIMEOUT,
              new AnthropicModel("model", null));
      assertThat(validator.validate(connection))
          .hasSize(1)
          .extracting(ConstraintViolation::getMessage)
          .containsExactly(
              "Client credentials authentication is only supported for the FOUNDRY backend");
    }
  }

  @Nested
  class OpenAiConnectionTest {

    @Test
    void validationShouldSucceed_WhenOpenAIBackendWithApiKeyAuth() {
      var connection =
          new OpenAiConnection(
              OpenAiBackend.OPENAI,
              new OpenAiApiKeyAuthentication("my-api-key", null, null),
              TIMEOUT,
              new OpenAiModel("gpt-4o", null),
              null,
              null,
              null,
              null);
      assertThat(validator.validate(connection)).isEmpty();
    }

    @Test
    void validationShouldSucceed_WhenFoundryBackendWithClientCredentialsAuth() {
      var connection =
          new OpenAiConnection(
              OpenAiBackend.FOUNDRY,
              new OpenAiClientCredentialsAuthentication(
                  "client-id", "client-secret", "tenant-id", null),
              TIMEOUT,
              new OpenAiModel("gpt-4o", null),
              null,
              "https://my-foundry-endpoint.azure.com",
              null,
              null);
      assertThat(validator.validate(connection)).isEmpty();
    }

    @Test
    void validationShouldSucceed_WhenFoundryBackendWithApiKeyAuth() {
      var connection =
          new OpenAiConnection(
              OpenAiBackend.FOUNDRY,
              new OpenAiApiKeyAuthentication("my-api-key", null, null),
              TIMEOUT,
              new OpenAiModel("gpt-4o", null),
              null,
              "https://my-foundry-endpoint.azure.com",
              null,
              null);
      assertThat(validator.validate(connection)).isEmpty();
    }

    @Test
    void validationShouldSucceed_WhenCustomBackendWithApiKeyAuth() {
      var connection =
          new OpenAiConnection(
              OpenAiBackend.CUSTOM,
              new OpenAiApiKeyAuthentication(null, null, null),
              TIMEOUT,
              new OpenAiModel("some-model", null),
              null,
              "https://custom-endpoint.local/v1",
              null,
              null);
      // apiKey may be null for CUSTOM
      assertThat(validator.validate(connection)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
        value = OpenAiBackend.class,
        names = {"OPENAI", "CUSTOM"})
    void validationShouldFail_WhenClientCredentialsAuthUsedWithNonFoundryBackend(
        OpenAiBackend backend) {
      var connection =
          new OpenAiConnection(
              backend,
              new OpenAiClientCredentialsAuthentication(
                  "client-id", "client-secret", "tenant-id", null),
              TIMEOUT,
              new OpenAiModel("gpt-4o", null),
              null,
              "https://some-endpoint.local",
              null,
              null);
      assertThat(validator.validate(connection))
          .hasSize(1)
          .extracting(ConstraintViolation::getMessage)
          .containsExactly(
              "Client credentials authentication is only supported for the FOUNDRY backend");
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.aiagent.model.request.ProviderConfigurationTest#validHttpUrls")
    @NullSource
    void shouldAcceptValidEndpoint(String endpoint) {
      var connection =
          new OpenAiConnection(
              OpenAiBackend.OPENAI,
              new OpenAiApiKeyAuthentication("key", null, null),
              TIMEOUT,
              new OpenAiModel("gpt-4o", null),
              null,
              endpoint,
              null,
              null);
      assertThat(validator.validate(connection)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.aiagent.model.request.ProviderConfigurationTest#invalidHttpUrls")
    void shouldRejectInvalidEndpoint(String endpoint) {
      var connection =
          new OpenAiConnection(
              OpenAiBackend.OPENAI,
              new OpenAiApiKeyAuthentication("key", null, null),
              TIMEOUT,
              new OpenAiModel("gpt-4o", null),
              null,
              endpoint,
              null,
              null);
      assertThat(validator.validate(connection))
          .extracting(ConstraintViolation::getMessage)
          .contains(HTTP_URL_VALIDATION_MESSAGE);
    }

    @Test
    void validationShouldSucceed_WhenNullBackendDefaultsToOpenAI() {
      // Compact constructor sets null backend → OPENAI
      var connection =
          new OpenAiConnection(
              new OpenAiApiKeyAuthentication("my-api-key", null, null),
              TIMEOUT,
              new OpenAiModel("gpt-4o", null));
      assertThat(connection.backend()).isEqualTo(OpenAiBackend.OPENAI);
      assertThat(validator.validate(connection)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
        value = OpenAiBackend.class,
        names = {"FOUNDRY", "CUSTOM"})
    void validationShouldFail_WhenEndpointMissingForBackendThatRequiresIt(OpenAiBackend backend) {
      var connection =
          new OpenAiConnection(
              backend,
              new OpenAiApiKeyAuthentication("my-api-key", null, null),
              TIMEOUT,
              new OpenAiModel("gpt-4o", null),
              null,
              null,
              null,
              null);
      assertThat(validator.validate(connection))
          .hasSize(1)
          .extracting(ConstraintViolation::getMessage)
          .containsExactly("Endpoint is required for FOUNDRY and CUSTOM backends");
    }

    @ParameterizedTest
    @EnumSource(
        value = OpenAiBackend.class,
        names = {"FOUNDRY", "CUSTOM"})
    void validationShouldFail_WhenEndpointBlankForBackendThatRequiresIt(OpenAiBackend backend) {
      var connection =
          new OpenAiConnection(
              backend,
              new OpenAiApiKeyAuthentication("my-api-key", null, null),
              TIMEOUT,
              new OpenAiModel("gpt-4o", null),
              null,
              "   ",
              null,
              null);
      // Both @HttpUrl and @AssertFalse validations trigger for blank endpoint
      assertThat(validator.validate(connection))
          .hasSize(2)
          .extracting(ConstraintViolation::getMessage)
          .containsExactlyInAnyOrder(
              "Must be an HTTP or HTTPS URL",
              "Endpoint is required for FOUNDRY and CUSTOM backends");
    }

    @ParameterizedTest
    @EnumSource(
        value = OpenAiBackend.class,
        names = {"FOUNDRY", "CUSTOM"})
    void validationShouldSucceed_WhenEndpointProvidedForBackendThatRequiresIt(
        OpenAiBackend backend) {
      var connection =
          new OpenAiConnection(
              backend,
              new OpenAiApiKeyAuthentication("my-api-key", null, null),
              TIMEOUT,
              new OpenAiModel("gpt-4o", null),
              null,
              "https://my-endpoint.local/v1",
              null,
              null);
      assertThat(validator.validate(connection)).isEmpty();
    }
  }

  @Nested
  class GoogleGenAiConnectionTest {

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
          .containsExactly("Google GenAI is not supported on SaaS");
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

    private static GoogleGenAiConnection createConnectionWithApplicationDefaultCredentials() {
      return new GoogleGenAiConnection(
          "my-project-id",
          "us-central1",
          new ApplicationDefaultCredentialsAuthentication(),
          new GoogleGenAiModel(
              "gemini-1.5-flash", new GoogleGenAiModelParameters(null, null, null, null)),
          null);
    }

    private static GoogleGenAiConnection createConnectionWithServiceAccountCredentials() {
      return new GoogleGenAiConnection(
          "my-project-id",
          "us-central1",
          new ServiceAccountCredentialsAuthentication("{}"),
          new GoogleGenAiModel(
              "gemini-1.5-flash", new GoogleGenAiModelParameters(null, null, null, null)),
          null);
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
