/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.translation;

import static io.camunda.connector.agenticai.aiagent.ChatModelTranslationFixture.DEFAULT_TIMEOUT;
import static io.camunda.connector.agenticai.aiagent.ChatModelTranslationFixture.translate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModelErrorCodes;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.EndpointAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.RequestCustomizations;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicConfiguration.Backend;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicConfiguration.Display;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicConfiguration.Effort;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicConfiguration.Thinking;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.aws.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.FoundryAuthentication;
import io.camunda.connector.agenticai.aiagent.request.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicAwsBedrockMantleBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicCustomBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicFoundryBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicModel.AnthropicEffort;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicModel.AnthropicPromptCaching;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicModel.AnthropicThinking;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicModel.ThinkingDisplay;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicModel.ThinkingMode;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicCustomEndpointAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.AwsAuthentication.AwsApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.AwsAuthentication.AwsDefaultCredentialsChainAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.AwsAuthentication.AwsStaticCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.OAuthClientCredentialsAuthentication;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class AnthropicConfigurationTranslationTest {

  private static AnthropicConfiguration anthropic(
      AnthropicBackend backend,
      @Nullable AnthropicModelParameters parameters,
      @Nullable TimeoutConfiguration timeouts) {
    return translate(
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(backend, new AnthropicModel("claude-x", parameters), timeouts)),
        AnthropicConfiguration.class);
  }

  private static AnthropicConfiguration anthropic(@Nullable AnthropicModelParameters parameters) {
    return anthropic(apiBackend(null), parameters, null);
  }

  private static AnthropicApiBackend apiBackend(@Nullable String endpoint) {
    return new AnthropicApiBackend(
        new AnthropicApiBackend.AnthropicApi("sk-ant-key", endpoint, null, null, null));
  }

  private static AnthropicModelParameters thinking(@Nullable AnthropicThinking thinking) {
    return new AnthropicModelParameters(null, thinking, null, null, null, null, null);
  }

  @Test
  void unsetOptionsStayAbsentAndTheDefaultsApplyOnce() {
    final var configuration = anthropic(apiBackend(null), null, null);

    assertThat(configuration.provider()).isEqualTo("anthropic");
    assertThat(configuration.descriptiveProvider()).isEqualTo("anthropic/anthropic-api");
    assertThat(configuration.model()).isEqualTo("claude-x");
    assertThat(configuration.timeout()).isEqualTo(DEFAULT_TIMEOUT);
    assertThat(configuration.maxTokens()).isEqualTo(4096);
    assertThat(configuration.effort()).isNull();
    assertThat(configuration.thinking()).isNull();
    assertThat(configuration.promptCaching()).isFalse();
    assertThat(configuration.temperature()).isNull();
    assertThat(configuration.topP()).isNull();
    assertThat(configuration.topK()).isNull();
    assertThat(configuration.backend())
        .isEqualTo(new Backend.Api("sk-ant-key", null, RequestCustomizations.NONE));
  }

  @Test
  void keepsConfiguredValues() {
    final var configuration =
        anthropic(
            apiBackend("https://proxy"),
            new AnthropicModelParameters(
                AnthropicEffort.XHIGH, null, new AnthropicPromptCaching(true), 2048, 0.2, 0.9, 40),
            new TimeoutConfiguration(Duration.ofSeconds(9)));

    assertThat(configuration.timeout()).isEqualTo(Duration.ofSeconds(9));
    assertThat(configuration.maxTokens()).isEqualTo(2048);
    assertThat(configuration.effort()).isEqualTo(Effort.XHIGH);
    assertThat(configuration.promptCaching()).isTrue();
    assertThat(configuration.temperature()).isEqualTo(0.2);
    assertThat(configuration.topP()).isEqualTo(0.9);
    assertThat(configuration.topK()).isEqualTo(40);
    assertThat(configuration.backend().endpoint()).isEqualTo("https://proxy");
  }

  @Test
  void promptCachingIsOnlyEnabledWhenExplicitlyEnabled() {
    for (final var caching :
        new AnthropicPromptCaching[] {
          null, new AnthropicPromptCaching(null), new AnthropicPromptCaching(false)
        }) {
      assertThat(
              anthropic(new AnthropicModelParameters(null, null, caching, null, null, null, null))
                  .promptCaching())
          .isFalse();
    }
  }

  @Test
  void modelDefaultEffortIsAbsentAndEveryOtherLevelIsKept() {
    assertThat(anthropic(effort(AnthropicEffort.MODEL_DEFAULT)).effort()).isNull();
    assertThat(anthropic(effort(AnthropicEffort.LOW)).effort()).isEqualTo(Effort.LOW);
    assertThat(anthropic(effort(AnthropicEffort.MEDIUM)).effort()).isEqualTo(Effort.MEDIUM);
    assertThat(anthropic(effort(AnthropicEffort.HIGH)).effort()).isEqualTo(Effort.HIGH);
    assertThat(anthropic(effort(AnthropicEffort.XHIGH)).effort()).isEqualTo(Effort.XHIGH);
    assertThat(anthropic(effort(AnthropicEffort.MAX)).effort()).isEqualTo(Effort.MAX);
  }

  private static AnthropicModelParameters effort(AnthropicEffort effort) {
    return new AnthropicModelParameters(effort, null, null, null, null, null, null);
  }

  @Test
  void thinkingIsAbsentUnlessAConcreteModeIsSet() {
    assertThat(anthropic(thinking(null)).thinking()).isNull();
    assertThat(anthropic(thinking(new AnthropicThinking(null, 2000, null))).thinking()).isNull();
    assertThat(
            anthropic(thinking(new AnthropicThinking(ThinkingMode.MODEL_DEFAULT, 2000, null)))
                .thinking())
        .isNull();
  }

  @Test
  void translatesEveryThinkingMode() {
    assertThat(
            anthropic(thinking(new AnthropicThinking(ThinkingMode.ENABLED, 2048, null))).thinking())
        .isEqualTo(new Thinking.Enabled(2048));
    assertThat(
            anthropic(thinking(new AnthropicThinking(ThinkingMode.ADAPTIVE, null, null)))
                .thinking())
        .isEqualTo(new Thinking.Adaptive(null));
    assertThat(
            anthropic(
                    thinking(
                        new AnthropicThinking(
                            ThinkingMode.ADAPTIVE, null, ThinkingDisplay.OMITTED)))
                .thinking())
        .isEqualTo(new Thinking.Adaptive(Display.OMITTED));
    assertThat(
            anthropic(
                    thinking(
                        new AnthropicThinking(
                            ThinkingMode.ADAPTIVE, null, ThinkingDisplay.SUMMARIZED)))
                .thinking())
        .isEqualTo(new Thinking.Adaptive(Display.SUMMARIZED));
    assertThat(
            anthropic(thinking(new AnthropicThinking(ThinkingMode.DISABLED, null, null)))
                .thinking())
        .isEqualTo(new Thinking.Disabled());
  }

  @Test
  void rejectsEnabledThinkingWithoutABudget() {
    assertThatThrownBy(
            () -> anthropic(thinking(new AnthropicThinking(ThinkingMode.ENABLED, null, null))))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(ChatModelErrorCodes.ERROR_CODE_UNSUPPORTED_MODEL_CONFIGURATION))
        .hasMessageContaining("claude-x");
  }

  @Test
  void translatesTheBedrockMantleBackendWithEveryAuthentication() {
    for (final var entry :
        Map
            .<io.camunda.connector.agenticai.aiagent.request.v2.AwsAuthentication,
                AwsAuthentication>
                of(
                    new AwsStaticCredentialsAuthentication("AKIA-1234", "s3cr3t-value"),
                    new AwsAuthentication.StaticCredentials("AKIA-1234", "s3cr3t-value"),
                    new AwsApiKeyAuthentication("token-9876"),
                    new AwsAuthentication.ApiKey("token-9876"),
                    new AwsDefaultCredentialsChainAuthentication(),
                    new AwsAuthentication.DefaultCredentialsChain())
            .entrySet()) {
      final var configuration =
          anthropic(
              new AnthropicAwsBedrockMantleBackend(
                  new AnthropicAwsBedrockMantleBackend.AwsBedrockMantleBackend(
                      "eu-west-1",
                      "https://vpce/anthropic",
                      entry.getKey(),
                      Map.of("h", "v"),
                      null,
                      Map.of("b", 1))),
              null,
              null);

      assertThat(configuration.descriptiveProvider()).isEqualTo("anthropic/aws-bedrock-mantle");
      assertThat(configuration.backend())
          .isEqualTo(
              new Backend.AwsBedrockMantle(
                  "eu-west-1",
                  "https://vpce/anthropic",
                  entry.getValue(),
                  new RequestCustomizations(Map.of("h", "v"), Map.of(), Map.of("b", 1))));
      assertThat(configuration.toString())
          .doesNotContain("AKIA-1234")
          .doesNotContain("s3cr3t-value")
          .doesNotContain("token-9876");
    }
  }

  @Test
  void translatesTheFoundryBackendWithEveryAuthentication() {
    final var apiKey =
        foundry(
            new io.camunda.connector.agenticai.aiagent.request.v2.FoundryAuthentication
                .ApiKeyAuthentication("foundry-key-1"));
    assertThat(apiKey.descriptiveProvider()).isEqualTo("anthropic/foundry");
    assertThat(apiKey.backend())
        .isEqualTo(
            new Backend.Foundry(
                "https://res.services.ai.azure.com",
                new FoundryAuthentication.ApiKey("foundry-key-1"),
                RequestCustomizations.NONE));

    final var clientCredentials =
        foundry(
            new io.camunda.connector.agenticai.aiagent.request.v2.FoundryAuthentication
                .ClientCredentialsAuthentication(
                "client-id",
                "client-secret-2",
                "tenant",
                "https://login.example",
                "scope/.default"));
    assertThat(clientCredentials.backend())
        .isEqualTo(
            new Backend.Foundry(
                "https://res.services.ai.azure.com",
                new FoundryAuthentication.ClientCredentials(
                    "client-id",
                    "client-secret-2",
                    "tenant",
                    "https://login.example",
                    "scope/.default"),
                RequestCustomizations.NONE));
    assertThat(clientCredentials.toString())
        .doesNotContain("client-secret-2")
        .doesNotContain("foundry-key-1");

    final var managedIdentity =
        foundry(
            new io.camunda.connector.agenticai.aiagent.request.v2.FoundryAuthentication
                .ManagedIdentityAuthentication("mi-id", null));
    assertThat(managedIdentity.backend())
        .isEqualTo(
            new Backend.Foundry(
                "https://res.services.ai.azure.com",
                new FoundryAuthentication.ManagedIdentity("mi-id", null),
                RequestCustomizations.NONE));
  }

  private static AnthropicConfiguration foundry(
      io.camunda.connector.agenticai.aiagent.request.v2.FoundryAuthentication authentication) {
    return anthropic(
        new AnthropicFoundryBackend(
            new AnthropicFoundryBackend.FoundryBackend(
                "https://res.services.ai.azure.com", authentication, null, null, null)),
        null,
        null);
  }

  @Test
  void translatesTheCustomBackendWithEveryAuthentication() {
    final var none = custom(new AnthropicCustomEndpointAuthentication.NoAuthentication());
    assertThat(none.descriptiveProvider()).isEqualTo("anthropic/custom");
    assertThat(none.backend())
        .isEqualTo(
            new Backend.Custom(
                "https://compatible.example",
                new EndpointAuthentication.None(),
                new RequestCustomizations(Map.of("h", "v"), Map.of("q", "1"), Map.of("b", 2))));

    assertThat(
            custom(new AnthropicCustomEndpointAuthentication.ApiKeyAuthentication("compat-key-3"))
                .backend())
        .isEqualTo(
            new Backend.Custom(
                "https://compatible.example",
                new EndpointAuthentication.ApiKey("compat-key-3"),
                new RequestCustomizations(Map.of("h", "v"), Map.of("q", "1"), Map.of("b", 2))));

    final var oauth =
        custom(
            new OAuthClientCredentialsAuthentication(
                "https://token",
                "client",
                "oauth-secret-4",
                "aud",
                OAuthClientCredentialsAuthentication.ClientAuthenticationMethod.BASIC_AUTH_HEADER,
                "read"));
    assertThat(oauth.backend())
        .isEqualTo(
            new Backend.Custom(
                "https://compatible.example",
                new EndpointAuthentication.OAuthClientCredentials(
                    "https://token",
                    "client",
                    "oauth-secret-4",
                    "aud",
                    EndpointAuthentication.ClientAuthenticationMethod.BASIC_AUTH_HEADER,
                    "read"),
                new RequestCustomizations(Map.of("h", "v"), Map.of("q", "1"), Map.of("b", 2))));
    assertThat(oauth.toString()).doesNotContain("oauth-secret-4");
  }

  private static AnthropicConfiguration custom(
      AnthropicCustomEndpointAuthentication authentication) {
    return anthropic(
        new AnthropicCustomBackend(
            new AnthropicCustomBackend.CustomBackend(
                "https://compatible.example",
                Map.of("h", "v"),
                Map.of("q", "1"),
                Map.of("b", 2),
                authentication)),
        null,
        null);
  }

  @Test
  void theApiKeyOfTheAnthropicApiIsRedacted() {
    assertThat(anthropic(null).toString()).doesNotContain("sk-ant-key");
  }
}
