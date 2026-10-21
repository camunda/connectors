/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.translation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.chatmodel.connection.EndpointAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.RequestCustomizations;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral.MistralConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral.MistralConfiguration.Backend;
import io.camunda.connector.agenticai.aiagent.configuration.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import io.camunda.connector.agenticai.aiagent.configuration.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.ApiProperties;
import io.camunda.connector.agenticai.aiagent.request.V1ToV2ProviderConfigurationMapperImpl;
import io.camunda.connector.agenticai.aiagent.request.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.MistralChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.MistralChatModelConfiguration.MistralBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.MistralChatModelConfiguration.MistralBackend.MistralApiBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.MistralChatModelConfiguration.MistralBackend.MistralApiBackend.MistralApiConnection;
import io.camunda.connector.agenticai.aiagent.request.v2.MistralChatModelConfiguration.MistralBackend.MistralCustomBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.MistralChatModelConfiguration.MistralConnection;
import io.camunda.connector.agenticai.aiagent.request.v2.MistralChatModelConfiguration.MistralEffort;
import io.camunda.connector.agenticai.aiagent.request.v2.MistralChatModelConfiguration.MistralModel;
import io.camunda.connector.agenticai.aiagent.request.v2.MistralChatModelConfiguration.MistralParameters;
import io.camunda.connector.agenticai.aiagent.request.v2.OAuthClientCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.OAuthClientCredentialsAuthentication.ClientAuthenticationMethod;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiCustomEndpointAuthentication;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ChatModelConfigurationTranslatorTest {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(3);

  private final ChatModelProperties properties =
      new ChatModelProperties(new ApiProperties(DEFAULT_TIMEOUT), null);
  private final ChatModelConfigurationTranslator translator =
      new ChatModelConfigurationTranslator(
          new V1ToV2ProviderConfigurationMapperImpl(properties), DEFAULT_TIMEOUT);

  private MistralConfiguration mistral(
      MistralBackend backend,
      @Nullable MistralParameters parameters,
      @Nullable TimeoutConfiguration timeouts) {
    return (MistralConfiguration)
        translator.translate(
            new MistralChatModelConfiguration(
                new MistralConnection(
                    backend, new MistralModel("mistral-x"), parameters, timeouts)));
  }

  private static MistralApiBackend apiBackend(@Nullable String endpoint) {
    return new MistralApiBackend(new MistralApiConnection("key", endpoint, null, null, null));
  }

  @Test
  void mistralUnsetOptionsResolveToProviderDefaults() {
    final var configuration = mistral(apiBackend(null), null, null);

    assertThat(configuration.model()).isEqualTo("mistral-x");
    assertThat(configuration.timeout()).isEqualTo(DEFAULT_TIMEOUT);
    assertThat(configuration.maxTokens()).isNull();
    assertThat(configuration.effort()).isNull();
    assertThat(configuration.temperature()).isNull();
    assertThat(configuration.topP()).isNull();
    assertThat(configuration.backend())
        .isEqualTo(
            new Backend.Api("key", Backend.Api.DEFAULT_ENDPOINT, RequestCustomizations.NONE));
  }

  @Test
  void mistralKeepsConfiguredValues() {
    final var configuration =
        mistral(
            new MistralApiBackend(
                new MistralApiConnection(
                    "key",
                    "https://eu.api.mistral.ai/v1",
                    Map.of("X-Custom", "value"),
                    Map.of("api-version", "1"),
                    Map.of("extra", 1))),
            new MistralParameters(512, MistralEffort.HIGH, 0.5, 0.9),
            new TimeoutConfiguration(Duration.ofSeconds(7)));

    assertThat(configuration.timeout()).isEqualTo(Duration.ofSeconds(7));
    assertThat(configuration.maxTokens()).isEqualTo(512);
    assertThat(configuration.effort()).isEqualTo(MistralConfiguration.Effort.HIGH);
    assertThat(configuration.temperature()).isEqualTo(0.5);
    assertThat(configuration.topP()).isEqualTo(0.9);
    assertThat(configuration.backend())
        .isEqualTo(
            new Backend.Api(
                "key",
                "https://eu.api.mistral.ai/v1",
                new RequestCustomizations(
                    Map.of("X-Custom", "value"), Map.of("api-version", "1"), Map.of("extra", 1))));
  }

  @Test
  void mistralModelDefaultEffortIsAbsent() {
    final var configuration =
        mistral(
            apiBackend(null),
            new MistralParameters(null, MistralEffort.MODEL_DEFAULT, null, null),
            null);

    assertThat(configuration.effort()).isNull();
  }

  @Test
  void mistralNonPositiveTimeoutFallsBackToTheDefault() {
    final var configuration =
        mistral(apiBackend(null), null, new TimeoutConfiguration(Duration.ZERO));

    assertThat(configuration.timeout()).isEqualTo(DEFAULT_TIMEOUT);
  }

  @Test
  void mistralCustomBackendTranslatesEveryAuthentication() {
    assertThat(customAuthentication(new OpenAiCustomEndpointAuthentication.NoAuthentication()))
        .isEqualTo(new EndpointAuthentication.None());
    assertThat(
            customAuthentication(new OpenAiCustomEndpointAuthentication.ApiKeyAuthentication("k")))
        .isEqualTo(new EndpointAuthentication.ApiKey("k"));
    assertThat(
            customAuthentication(
                new OAuthClientCredentialsAuthentication(
                    "https://token",
                    "id",
                    "secret",
                    "audience",
                    ClientAuthenticationMethod.CREDENTIALS_BODY,
                    "scope")))
        .isEqualTo(
            new EndpointAuthentication.OAuthClientCredentials(
                "https://token",
                "id",
                "secret",
                "audience",
                EndpointAuthentication.ClientAuthenticationMethod.CREDENTIALS_BODY,
                "scope"));
  }

  private EndpointAuthentication customAuthentication(
      OpenAiCustomEndpointAuthentication authentication) {
    final var configuration =
        mistral(
            new MistralCustomBackend(
                new MistralCustomBackend.CustomBackend(
                    "https://custom", null, null, null, authentication)),
            null,
            null);
    return ((Backend.Custom) configuration.backend()).authentication();
  }

  @Test
  void secretsAreRedactedInToString() {
    final var configuration = mistral(apiBackend(null), null, null);
    final var oauth =
        new EndpointAuthentication.OAuthClientCredentials(
            "https://token",
            "id",
            "secret-value",
            null,
            EndpointAuthentication.ClientAuthenticationMethod.BASIC_AUTH_HEADER,
            null);

    assertThat(configuration.toString()).doesNotContain("key");
    assertThat(oauth.toString()).doesNotContain("secret-value").doesNotContain("id=");
    assertThat(new EndpointAuthentication.ApiKey("secret-value").toString())
        .doesNotContain("secret-value");
  }
}
