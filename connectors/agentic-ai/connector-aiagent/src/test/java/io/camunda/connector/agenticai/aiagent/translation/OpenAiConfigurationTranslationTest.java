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

import io.camunda.connector.agenticai.aiagent.chatmodel.connection.EndpointAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.RequestCustomizations;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.FoundryAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiConfiguration.Api;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiConfiguration.Backend;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiConfiguration.Effort;
import io.camunda.connector.agenticai.aiagent.request.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.OAuthClientCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiApi;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend.OpenAiApiConnection;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiFoundryBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiEffort;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiModel;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiCustomEndpointAuthentication;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class OpenAiConfigurationTranslationTest {

  private static OpenAiConfiguration openAi(
      OpenAiApi api, OpenAiBackend backend, @Nullable TimeoutConfiguration timeouts) {
    return translate(
        new OpenAiChatModelConfiguration(
            new OpenAiConnection(api, backend, new OpenAiModel("gpt-x"), timeouts)),
        OpenAiConfiguration.class);
  }

  private static OpenAiBackend apiBackend(
      @Nullable String organizationId, @Nullable String projectId, @Nullable String endpoint) {
    return new OpenAiApiBackend(
        new OpenAiApiConnection(
            "sk-openai-key", organizationId, projectId, endpoint, null, null, null));
  }

  @Test
  void unsetOptionsStayAbsentAndTheDefaultsApplyOnce() {
    for (final var api :
        new OpenAiApi[] {new OpenAiResponsesApi(null), new OpenAiCompletionsApi(null)}) {
      final var configuration = openAi(api, apiBackend(null, null, null), null);

      assertThat(configuration.provider()).isEqualTo("openai");
      assertThat(configuration.model()).isEqualTo("gpt-x");
      assertThat(configuration.timeout()).isEqualTo(DEFAULT_TIMEOUT);
      assertThat(configuration.api().effort()).isNull();
      assertThat(configuration.api().temperature()).isNull();
      assertThat(configuration.api().topP()).isNull();
      assertThat(configuration.backend())
          .isEqualTo(
              new Backend.Api("sk-openai-key", null, null, null, RequestCustomizations.NONE));
    }
    assertThat(
            openAi(new OpenAiResponsesApi(null), apiBackend(null, null, null), null)
                .descriptiveProvider())
        .isEqualTo("openai/responses/openai-api");
    assertThat(
            openAi(new OpenAiCompletionsApi(null), apiBackend(null, null, null), null)
                .descriptiveProvider())
        .isEqualTo("openai/completions/openai-api");
  }

  @Test
  void translatesTheParametersOfEachApiFamily() {
    final var responses =
        openAi(
            new OpenAiResponsesApi(
                new OpenAiResponsesApi.ResponsesParameters(256, OpenAiEffort.XHIGH, 0.5, 0.8)),
            apiBackend(null, null, null),
            new TimeoutConfiguration(Duration.ofSeconds(11)));
    assertThat(responses.api()).isEqualTo(new Api.Responses(256, Effort.XHIGH, 0.5, 0.8));
    assertThat(responses.timeout()).isEqualTo(Duration.ofSeconds(11));

    final var completions =
        openAi(
            new OpenAiCompletionsApi(
                new OpenAiCompletionsApi.CompletionsParameters(
                    512, OpenAiEffort.MINIMAL, 1.5, 0.2)),
            apiBackend(null, null, null),
            null);
    assertThat(completions.api()).isEqualTo(new Api.Completions(512, Effort.MINIMAL, 1.5, 0.2));
  }

  @Test
  void modelDefaultEffortIsAbsentAndEveryOtherLevelIsKept() {
    final var expected =
        Map.of(
            OpenAiEffort.MINIMAL, Effort.MINIMAL,
            OpenAiEffort.LOW, Effort.LOW,
            OpenAiEffort.MEDIUM, Effort.MEDIUM,
            OpenAiEffort.HIGH, Effort.HIGH,
            OpenAiEffort.XHIGH, Effort.XHIGH,
            OpenAiEffort.MAX, Effort.MAX);
    for (final var entry : expected.entrySet()) {
      assertThat(effort(entry.getKey()).api().effort()).isEqualTo(entry.getValue());
    }
    assertThat(effort(OpenAiEffort.MODEL_DEFAULT).api().effort()).isNull();
  }

  private static OpenAiConfiguration effort(OpenAiEffort effort) {
    return openAi(
        new OpenAiResponsesApi(
            new OpenAiResponsesApi.ResponsesParameters(null, effort, null, null)),
        apiBackend(null, null, null),
        null);
  }

  @Test
  void blankOptionalValuesOfTheApiBackendAreAbsent() {
    final var blank = openAi(new OpenAiResponsesApi(null), apiBackend(" ", "", null), null);
    assertThat(blank.backend())
        .isEqualTo(new Backend.Api("sk-openai-key", null, null, null, RequestCustomizations.NONE));

    final var set =
        openAi(
            new OpenAiResponsesApi(null), apiBackend("org-1", "proj-1", "https://proxy/v1"), null);
    assertThat(set.backend())
        .isEqualTo(
            new Backend.Api(
                "sk-openai-key",
                "org-1",
                "proj-1",
                "https://proxy/v1",
                RequestCustomizations.NONE));
    assertThat(set.toString()).doesNotContain("sk-openai-key");
  }

  @Test
  void translatesTheFoundryBackendWithEveryAuthentication() {
    final var apiKey =
        foundry(
            "2025-01-01",
            new io.camunda.connector.agenticai.aiagent.request.v2.FoundryAuthentication
                .ApiKeyAuthentication("foundry-key-1"));
    assertThat(apiKey.descriptiveProvider()).isEqualTo("openai/responses/foundry");
    assertThat(apiKey.backend())
        .isEqualTo(
            new Backend.Foundry(
                "https://res.openai.azure.com",
                "2025-01-01",
                new FoundryAuthentication.ApiKey("foundry-key-1"),
                new RequestCustomizations(Map.of("h", "v"), Map.of(), Map.of())));
    assertThat(apiKey.toString()).doesNotContain("foundry-key-1");

    final var blankVersion =
        foundry(
            " ",
            new io.camunda.connector.agenticai.aiagent.request.v2.FoundryAuthentication
                .ManagedIdentityAuthentication("mi-id", "scope"));
    assertThat(blankVersion.backend())
        .isEqualTo(
            new Backend.Foundry(
                "https://res.openai.azure.com",
                null,
                new FoundryAuthentication.ManagedIdentity("mi-id", "scope"),
                new RequestCustomizations(Map.of("h", "v"), Map.of(), Map.of())));

    final var clientCredentials =
        foundry(
            null,
            new io.camunda.connector.agenticai.aiagent.request.v2.FoundryAuthentication
                .ClientCredentialsAuthentication(
                "client-id", "client-secret-2", "tenant", null, null));
    assertThat(clientCredentials.backend())
        .isEqualTo(
            new Backend.Foundry(
                "https://res.openai.azure.com",
                null,
                new FoundryAuthentication.ClientCredentials(
                    "client-id", "client-secret-2", "tenant", null, null),
                new RequestCustomizations(Map.of("h", "v"), Map.of(), Map.of())));
    assertThat(clientCredentials.toString()).doesNotContain("client-secret-2");
  }

  private static OpenAiConfiguration foundry(
      @Nullable String apiVersion,
      io.camunda.connector.agenticai.aiagent.request.v2.FoundryAuthentication authentication) {
    return openAi(
        new OpenAiResponsesApi(null),
        new OpenAiFoundryBackend(
            new OpenAiFoundryBackend.FoundryBackend(
                "https://res.openai.azure.com",
                apiVersion,
                authentication,
                Map.of("h", "v"),
                null,
                null)),
        null);
  }

  @Test
  void translatesTheCustomBackendWithEveryAuthentication() {
    final var none = custom(new OpenAiCustomEndpointAuthentication.NoAuthentication());
    assertThat(none.descriptiveProvider()).isEqualTo("openai/completions/custom");
    assertThat(none.backend())
        .isEqualTo(
            new Backend.Custom(
                "https://compatible.example",
                new EndpointAuthentication.None(),
                new RequestCustomizations(Map.of(), Map.of("q", "1"), Map.of("b", 2))));

    assertThat(
            custom(new OpenAiCustomEndpointAuthentication.ApiKeyAuthentication("compat-key-3"))
                .backend())
        .isEqualTo(
            new Backend.Custom(
                "https://compatible.example",
                new EndpointAuthentication.ApiKey("compat-key-3"),
                new RequestCustomizations(Map.of(), Map.of("q", "1"), Map.of("b", 2))));

    final var oauth =
        custom(
            new OAuthClientCredentialsAuthentication(
                "https://token",
                "client",
                "oauth-secret-4",
                null,
                OAuthClientCredentialsAuthentication.ClientAuthenticationMethod.CREDENTIALS_BODY,
                null));
    assertThat(oauth.backend())
        .isEqualTo(
            new Backend.Custom(
                "https://compatible.example",
                new EndpointAuthentication.OAuthClientCredentials(
                    "https://token",
                    "client",
                    "oauth-secret-4",
                    null,
                    EndpointAuthentication.ClientAuthenticationMethod.CREDENTIALS_BODY,
                    null),
                new RequestCustomizations(Map.of(), Map.of("q", "1"), Map.of("b", 2))));
    assertThat(oauth.toString()).doesNotContain("oauth-secret-4");
  }

  private static OpenAiConfiguration custom(OpenAiCustomEndpointAuthentication authentication) {
    return openAi(
        new OpenAiCompletionsApi(null),
        new OpenAiCustomBackend(
            new OpenAiCustomBackend.CustomBackend(
                "https://compatible.example",
                null,
                Map.of("q", "1"),
                Map.of("b", 2),
                authentication)),
        null);
  }
}
