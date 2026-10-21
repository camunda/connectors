/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.chatmodel.connection.EndpointAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.RequestCustomizations;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.aws.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral.MistralConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral.MistralConfiguration.Backend;
import io.camunda.connector.agenticai.aiagent.configuration.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import io.camunda.connector.agenticai.aiagent.configuration.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.ApiProperties;
import io.camunda.connector.agenticai.aiagent.request.V1ToV2ProviderConfigurationMapperImpl;
import io.camunda.connector.agenticai.aiagent.request.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.BedrockConverseChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseConnection;
import io.camunda.connector.agenticai.aiagent.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseModel;
import io.camunda.connector.agenticai.aiagent.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseModel.BedrockConverseModelParameters;
import io.camunda.connector.agenticai.aiagent.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseModel.BedrockConversePromptCaching;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiApiBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiApiBackend.GoogleGeminiApi;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiVertexAiBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiVertexAiBackend.GoogleVertexAi;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiBackend.GoogleVertexAiAuthentication.ApplicationDefaultCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiBackend.GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiConnection;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiModel;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiModel.GeminiModelParameters;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiModel.GeminiThinking;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiModel.GeminiThinkingLevel;
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
import io.camunda.connector.api.error.ConnectorInputException;
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

  private GeminiConfiguration gemini(
      GeminiChatModelConfiguration.GeminiBackend backend,
      @Nullable GeminiModelParameters parameters,
      @Nullable TimeoutConfiguration timeouts) {
    return (GeminiConfiguration)
        translator.translate(
            new GeminiChatModelConfiguration(
                new GeminiConnection(backend, new GeminiModel("gemini-x", parameters), timeouts)));
  }

  private static GeminiApiBackend geminiApi(@Nullable String endpoint) {
    return new GeminiApiBackend(new GoogleGeminiApi("key", endpoint));
  }

  @Test
  void geminiUnsetOptionsStayAbsent() {
    final var configuration = gemini(geminiApi(null), null, null);

    assertThat(configuration.timeout()).isEqualTo(DEFAULT_TIMEOUT);
    assertThat(configuration.maxTokens()).isNull();
    assertThat(configuration.temperature()).isNull();
    assertThat(configuration.topP()).isNull();
    assertThat(configuration.topK()).isNull();
    assertThat(configuration.thinking()).isNull();
    assertThat(configuration.backend()).isEqualTo(new GeminiConfiguration.Backend.Api("key", null));
  }

  @Test
  void geminiKeepsConfiguredParameters() {
    final var configuration =
        gemini(
            geminiApi("https://endpoint"),
            new GeminiModelParameters(
                256, 0.4, 0.8, 30, new GeminiThinking(null, GeminiThinkingLevel.MEDIUM)),
            new TimeoutConfiguration(Duration.ofSeconds(9)));

    assertThat(configuration.timeout()).isEqualTo(Duration.ofSeconds(9));
    assertThat(configuration.maxTokens()).isEqualTo(256);
    assertThat(configuration.temperature()).isEqualTo(0.4);
    assertThat(configuration.topP()).isEqualTo(0.8);
    assertThat(configuration.topK()).isEqualTo(30);
    assertThat(configuration.thinking())
        .isEqualTo(
            new GeminiConfiguration.Thinking.Level(GeminiConfiguration.ThinkingLevel.MEDIUM));
    assertThat(configuration.backend())
        .isEqualTo(new GeminiConfiguration.Backend.Api("key", "https://endpoint"));
  }

  @Test
  void geminiThinkingIsAbsentUnlessABudgetOrAnExplicitLevelIsSet() {
    assertThat(
            gemini(
                    geminiApi(null),
                    new GeminiModelParameters(
                        null, null, null, null, new GeminiThinking(null, null)),
                    null)
                .thinking())
        .isNull();
    assertThat(
            gemini(
                    geminiApi(null),
                    new GeminiModelParameters(
                        null,
                        null,
                        null,
                        null,
                        new GeminiThinking(-1, GeminiThinkingLevel.MODEL_DEFAULT)),
                    null)
                .thinking())
        .isEqualTo(new GeminiConfiguration.Thinking.Budget(-1));
  }

  @Test
  void geminiRejectsABudgetTogetherWithAnExplicitLevel() {
    final var parameters =
        new GeminiModelParameters(
            null, null, null, null, new GeminiThinking(2048, GeminiThinkingLevel.HIGH));

    assertThatThrownBy(() -> gemini(geminiApi(null), parameters, null))
        .isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void geminiTranslatesTheVertexAiBackend() {
    assertThat(
            gemini(
                    new GeminiVertexAiBackend(
                        new GoogleVertexAi(
                            "project",
                            "us-central1",
                            null,
                            new ServiceAccountCredentialsAuthentication("{json}"))),
                    null,
                    null)
                .backend())
        .isEqualTo(
            new GeminiConfiguration.Backend.VertexAi(
                "project",
                "us-central1",
                null,
                new GeminiConfiguration.Authentication.ServiceAccountKey("{json}")));
    assertThat(
            gemini(
                    new GeminiVertexAiBackend(
                        new GoogleVertexAi(
                            "project",
                            "us-central1",
                            "https://endpoint",
                            new ApplicationDefaultCredentialsAuthentication())),
                    null,
                    null)
                .backend())
        .isEqualTo(
            new GeminiConfiguration.Backend.VertexAi(
                "project",
                "us-central1",
                "https://endpoint",
                new GeminiConfiguration.Authentication.ApplicationDefaultCredentials()));
  }

  private BedrockConfiguration bedrock(
      @Nullable BedrockConverseModelParameters parameters,
      @Nullable TimeoutConfiguration timeouts,
      @Nullable Map<String, String> headers,
      @Nullable Map<String, String> queryParameters,
      @Nullable Map<String, Object> bodyProperties) {
    return (BedrockConfiguration)
        translator.translate(
            new BedrockConverseChatModelConfiguration(
                new BedrockConverseConnection(
                    "us-east-1",
                    null,
                    new io.camunda.connector.agenticai.aiagent.request.v2.AwsAuthentication
                        .AwsDefaultCredentialsChainAuthentication(),
                    headers,
                    queryParameters,
                    bodyProperties,
                    timeouts,
                    new BedrockConverseModel("nova-x", parameters))));
  }

  @Test
  void bedrockUnsetOptionsStayAbsentAndPromptCachingIsOff() {
    final var configuration = bedrock(null, null, null, null, null);

    assertThat(configuration.timeout()).isEqualTo(DEFAULT_TIMEOUT);
    assertThat(configuration.promptCaching()).isFalse();
    assertThat(configuration.maxTokens()).isNull();
    assertThat(configuration.temperature()).isNull();
    assertThat(configuration.topP()).isNull();
    assertThat(configuration.customizations()).isEqualTo(RequestCustomizations.NONE);
    assertThat(configuration.authentication())
        .isEqualTo(new AwsAuthentication.DefaultCredentialsChain());
  }

  @Test
  void bedrockPromptCachingIsOnlyEnabledWhenExplicitlyEnabled() {
    assertThat(
            bedrock(
                    new BedrockConverseModelParameters(
                        new BedrockConversePromptCaching(null), null, null, null),
                    null,
                    null,
                    null,
                    null)
                .promptCaching())
        .isFalse();
    assertThat(
            bedrock(
                    new BedrockConverseModelParameters(
                        new BedrockConversePromptCaching(false), null, null, null),
                    null,
                    null,
                    null,
                    null)
                .promptCaching())
        .isFalse();
  }

  @Test
  void bedrockKeepsConfiguredValuesAndTranslatesEveryAuthentication() {
    final var configuration =
        bedrock(
            new BedrockConverseModelParameters(
                new BedrockConversePromptCaching(true), 512, 0.3, 0.7),
            new TimeoutConfiguration(Duration.ofSeconds(5)),
            Map.of("h", "v"),
            Map.of("q", "1"),
            Map.of("b", 2));

    assertThat(configuration.promptCaching()).isTrue();
    assertThat(configuration.maxTokens()).isEqualTo(512);
    assertThat(configuration.temperature()).isEqualTo(0.3);
    assertThat(configuration.topP()).isEqualTo(0.7);
    assertThat(configuration.timeout()).isEqualTo(Duration.ofSeconds(5));
    assertThat(configuration.customizations())
        .isEqualTo(new RequestCustomizations(Map.of("h", "v"), Map.of("q", "1"), Map.of("b", 2)));

    for (final var authentication :
        java.util.List.<io.camunda.connector.agenticai.aiagent.request.v2.AwsAuthentication>of(
            new io.camunda.connector.agenticai.aiagent.request.v2.AwsAuthentication
                .AwsStaticCredentialsAuthentication("AKIA-1234", "s3cr3t-value"),
            new io.camunda.connector.agenticai.aiagent.request.v2.AwsAuthentication
                .AwsApiKeyAuthentication("token-9876"))) {
      final var translated =
          (BedrockConfiguration)
              translator.translate(
                  new BedrockConverseChatModelConfiguration(
                      new BedrockConverseConnection(
                          "us-east-1",
                          "https://endpoint",
                          authentication,
                          null,
                          null,
                          null,
                          null,
                          new BedrockConverseModel("nova-x", null))));
      assertThat(translated.endpoint()).isEqualTo("https://endpoint");
      assertThat(translated.authentication().toString())
          .doesNotContain("AKIA-1234")
          .doesNotContain("s3cr3t-value")
          .doesNotContain("token-9876");
    }
  }

  @Test
  void customProviderTranslatesToTheGenericCustomConfiguration() {
    final var translated =
        translator.translate(
            new io.camunda.connector.agenticai.aiagent.request.v2.CustomProviderConfiguration(
                "acme-llm", "acme-model", Map.of("apiKey", "secret-value")));

    assertThat(translated)
        .isEqualTo(
            new io.camunda.connector.agenticai.aiagent.model.chatmodel.CustomChatModelConfiguration(
                "acme-llm", "acme-model", Map.of("apiKey", "secret-value")));
    assertThat(translated.provider()).isEqualTo("acme-llm");
    assertThat(translated.descriptiveProvider()).isEqualTo("acme-llm");
    assertThat(translated.model()).isEqualTo("acme-model");
    assertThat(translated.toString()).doesNotContain("secret-value");
  }
}
