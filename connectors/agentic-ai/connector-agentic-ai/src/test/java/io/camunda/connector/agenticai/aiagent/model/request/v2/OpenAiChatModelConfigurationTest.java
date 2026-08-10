/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi.CompletionsParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi.ResponsesParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend.OpenAiApiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend.CustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiEffort;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.CustomEndpointAuthentication.ApiKeyAuthentication;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@Import(ValidationAutoConfiguration.class)
class OpenAiChatModelConfigurationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Autowired private Validator validator;

  @Test
  void rejectsBlankModel() {
    final var config = configuration(responsesApi(), openAiApiBackend("sk-test"), "");

    assertThat(validator.validate(config))
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString()).isEqualTo("openai.model.model");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            });
  }

  @Test
  void rejectsBlankApiKeyOnOpenAiApiBackend() {
    final var config = configuration(responsesApi(), openAiApiBackend(""), "gpt-5.5");

    final Set<ConstraintViolation<OpenAiChatModelConfiguration>> violations =
        validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString()).isEqualTo("openai.backend.openai.apiKey");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            });
  }

  @Test
  void rejectsNonHttpEndpointOnCustomBackend() {
    final var config = configuration(completionsApi(), customBackend("ftp://nope"), "gpt-5.5");

    assertThat(validator.validate(config))
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("openai.backend.custom.endpoint");
              assertThat(v.getMessage()).isEqualTo("Must be an HTTP or HTTPS URL");
            });
  }

  @Test
  void requiredCustomFieldsAreEnforced() {
    final var config =
        new OpenAiChatModelConfiguration(
            new OpenAiConnection(
                completionsApi(),
                new OpenAiCustomBackend(
                    new CustomBackend("", null, null, null, new ApiKeyAuthentication("  "))),
                new OpenAiModel("gpt-5.5"),
                null));

    final var violations = validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("openai.backend.custom.endpoint");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            })
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("openai.backend.custom.authentication.apiKey");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            });
  }

  @Test
  void redactsSecretsInToString() {
    assertThat(openAiApiBackend("sk-secret").openai().toString())
        .contains("[REDACTED]")
        .doesNotContain("sk-secret");
  }

  @Test
  void redactsHeadersAndQueryParametersAndRequestParametersInToString() {
    final var connection =
        new OpenAiApiConnection(
            "sk-secret",
            null,
            null,
            null,
            Map.of("Authorization", "Bearer secret-header"),
            Map.of("token", "secret-query"),
            Map.of("apiKey", "secret-request"));

    assertThat(connection.toString())
        .contains("[REDACTED]")
        .doesNotContain("secret-header")
        .doesNotContain("secret-query")
        .doesNotContain("secret-request");
  }

  @Test
  void customBackendRedactsHeadersAndRequestParametersInToString() {
    final var backend =
        new OpenAiCustomBackend(
            new CustomBackend(
                "https://custom.example.com",
                Map.of("Authorization", "Bearer secret"),
                Map.of("api-version", "2026-01-01"),
                Map.of("large_field", "large_value"),
                new ApiKeyAuthentication("sk-custom-super-secret")));

    final String toString = backend.toString();
    assertThat(toString).doesNotContain("sk-custom-super-secret", "Bearer secret", "large_value");
    assertThat(toString)
        .contains(
            "headers={Authorization=[REDACTED]}",
            "queryParameters={api-version=[REDACTED]}",
            "requestParameters={large_field=[REDACTED]}",
            "apiKey=[REDACTED]");
  }

  @Test
  void validConfigurationHasNoViolations() {
    final var config = configuration(responsesApi(), openAiApiBackend("sk-test"), "gpt-5.5");

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void deserialisesOpenAiApiBackendWithResponsesApiAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "openai",
          "openai": {
            "api": { "type": "responses", "responses": { "effort": "high", "maxOutputTokens": 1024 } },
            "backend": { "type": "openai-api", "openai": { "apiKey": "sk-test-123" } },
            "model": { "model": "gpt-5.5" }
          }
        }
        """;

    final ProviderConfiguration parsed = mapper.readValue(json, ProviderConfiguration.class);

    assertThat(parsed).isInstanceOf(OpenAiChatModelConfiguration.class);
    assertThat(parsed.provider()).isEqualTo("openai");
    assertThat(parsed.model()).isEqualTo("gpt-5.5");

    final OpenAiChatModelConfiguration openai = (OpenAiChatModelConfiguration) parsed;
    assertThat(openai.openai().api()).isInstanceOf(OpenAiResponsesApi.class);
    final ResponsesParameters parameters = ((OpenAiResponsesApi) openai.openai().api()).responses();
    assertThat(parameters.effort()).isEqualTo(OpenAiEffort.HIGH);
    assertThat(parameters.maxOutputTokens()).isEqualTo(1024);
    assertThat(openai.openai().backend()).isInstanceOf(OpenAiApiBackend.class);
    assertThat(((OpenAiApiBackend) openai.openai().backend()).openai().apiKey())
        .isEqualTo("sk-test-123");

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void deserialisesCompletionsApiWithMaxCompletionTokensAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "openai",
          "openai": {
            "api": { "type": "completions", "completions": { "effort": "minimal", "maxCompletionTokens": 512 } },
            "backend": { "type": "openai-api", "openai": { "apiKey": "sk-test-123" } },
            "model": { "model": "gpt-5.5-mini" }
          }
        }
        """;

    final OpenAiChatModelConfiguration parsed =
        (OpenAiChatModelConfiguration) mapper.readValue(json, ProviderConfiguration.class);

    assertThat(parsed.openai().api()).isInstanceOf(OpenAiCompletionsApi.class);
    final CompletionsParameters parameters =
        ((OpenAiCompletionsApi) parsed.openai().api()).completions();
    assertThat(parameters.effort()).isEqualTo(OpenAiEffort.MINIMAL);
    assertThat(parameters.maxCompletionTokens()).isEqualTo(512);

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void deserialisesCustomBackendWithApiKeyAuthAndHeadersAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "openai",
          "openai": {
            "api": { "type": "responses", "responses": {} },
            "backend": {
              "type": "custom",
              "custom": {
                "endpoint": "https://custom.example.com/v1",
                "headers": { "X-Custom-Header": "value" },
                "authentication": { "type": "apiKey", "apiKey": "sk-custom-123" }
              }
            },
            "model": { "model": "gpt-5.5" }
          }
        }
        """;

    final OpenAiChatModelConfiguration parsed =
        (OpenAiChatModelConfiguration) mapper.readValue(json, ProviderConfiguration.class);

    assertThat(parsed.openai().backend()).isInstanceOf(OpenAiCustomBackend.class);
    final OpenAiCustomBackend custom = (OpenAiCustomBackend) parsed.openai().backend();
    assertThat(custom.custom().endpoint()).isEqualTo("https://custom.example.com/v1");
    assertThat(custom.custom().headers()).containsEntry("X-Custom-Header", "value");
    assertThat(custom.custom().authentication())
        .isEqualTo(new ApiKeyAuthentication("sk-custom-123"));

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void effortValuesSerializeLowercase() throws Exception {
    for (final var entry :
        Map.of(
                OpenAiEffort.MINIMAL, "minimal",
                OpenAiEffort.LOW, "low",
                OpenAiEffort.MEDIUM, "medium",
                OpenAiEffort.HIGH, "high",
                OpenAiEffort.XHIGH, "xhigh",
                OpenAiEffort.MAX, "max")
            .entrySet()) {
      assertThat(mapper.writeValueAsString(entry.getKey()))
          .isEqualTo("\"" + entry.getValue() + "\"");
    }
  }

  @Test
  void defaultApiIsResponsesAndDefaultBackendIsOpenAiApi() throws Exception {
    final String json =
        """
        {
          "type": "openai",
          "openai": {
            "api": { "type": "responses", "responses": {} },
            "backend": { "type": "openai-api", "openai": { "apiKey": "sk-test-123" } },
            "model": { "model": "gpt-5.5" }
          }
        }
        """;

    final OpenAiChatModelConfiguration parsed =
        (OpenAiChatModelConfiguration) mapper.readValue(json, ProviderConfiguration.class);

    assertThat(parsed.openai().api()).isInstanceOf(OpenAiResponsesApi.class);
    assertThat(parsed.openai().backend()).isInstanceOf(OpenAiApiBackend.class);
  }

  private static OpenAiChatModelConfiguration configuration(
      OpenAiApi api, OpenAiBackend backend, String model) {
    return new OpenAiChatModelConfiguration(
        new OpenAiConnection(api, backend, new OpenAiModel(model), null));
  }

  private static OpenAiApi responsesApi() {
    return new OpenAiResponsesApi(new ResponsesParameters(null, null, null, null));
  }

  private static OpenAiApi completionsApi() {
    return new OpenAiCompletionsApi(new CompletionsParameters(null, null, null, null));
  }

  private static OpenAiApiBackend openAiApiBackend(String apiKey) {
    return new OpenAiApiBackend(
        new OpenAiApiConnection(apiKey, null, null, null, null, null, null));
  }

  private static OpenAiCustomBackend customBackend(String endpoint) {
    return new OpenAiCustomBackend(
        new CustomBackend(endpoint, null, null, null, new ApiKeyAuthentication("sk-custom")));
  }
}
