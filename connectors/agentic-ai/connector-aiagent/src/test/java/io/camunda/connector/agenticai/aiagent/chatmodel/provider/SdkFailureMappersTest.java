/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.core.JsonValue;
import com.google.genai.errors.ClientException;
import com.openai.core.http.Headers;
import com.openai.errors.BadRequestException;
import com.openai.errors.RateLimitException;
import com.openai.models.ErrorObject;
import io.camunda.connector.agenticai.aiagent.chatmodel.envelope.SdkFailure;
import io.camunda.connector.agenticai.aiagent.chatmodel.envelope.SdkFailureMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicSdkFailureMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockSdkFailureMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiSdkFailureMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiSdkFailureMapper;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

class SdkFailureMappersTest {

  static Stream<SdkFailureMapper> mappers() {
    return Stream.of(
        new AnthropicSdkFailureMapper(),
        new OpenAiSdkFailureMapper(),
        new GeminiSdkFailureMapper(),
        new BedrockSdkFailureMapper());
  }

  @ParameterizedTest
  @MethodSource("mappers")
  void ignoresFailureThatIsNoResponseOfTheProvider(SdkFailureMapper mapper) {
    assertThat(mapper.map(new java.io.IOException("connection reset"))).isEmpty();
    assertThat(mapper.map(new IllegalStateException("no chunks"))).isEmpty();
  }

  @Nested
  class Anthropic {

    private final AnthropicSdkFailureMapper mapper = new AnthropicSdkFailureMapper();

    @Test
    void mapsStatusAndErrorType() {
      final var body =
          JsonValue.from(
              Map.of("error", Map.of("type", "rate_limit_error", "message", "slow down")));
      final var failure =
          com.anthropic.errors.RateLimitException.builder()
              .headers(com.anthropic.core.http.Headers.builder().build())
              .body(body)
              .build();

      assertThat(mapper.map(failure))
          .hasValueSatisfying(
              mapped -> {
                assertThat(mapped.statusCode()).isEqualTo(429);
                assertThat(mapped.type()).isEqualTo("rate_limit_error");
                assertThat(mapped.message()).isEqualTo("slow down");
                assertThat(mapped.contextWindowExceeded()).isFalse();
              });
    }
  }

  @Nested
  class OpenAi {

    private final OpenAiSdkFailureMapper mapper = new OpenAiSdkFailureMapper();

    @Test
    void mapsContextLengthExceededOfBadRequestToContextWindowExceeded() {
      assertThat(mapper.map(badRequest("context_length_exceeded", "Context length exceeded.")))
          .hasValueSatisfying(
              mapped -> {
                assertThat(mapped.statusCode()).isEqualTo(400);
                assertThat(mapped.type()).isEqualTo("context_length_exceeded");
                assertThat(mapped.message()).isEqualTo("Context length exceeded.");
                assertThat(mapped.contextWindowExceeded()).isTrue();
              });
    }

    @Test
    void doesNotMapOtherBadRequestToContextWindowExceeded() {
      assertThat(mapper.map(badRequest("invalid_api_key", "Incorrect API key provided.")))
          .hasValueSatisfying(
              mapped -> {
                assertThat(mapped.type()).isEqualTo("invalid_api_key");
                assertThat(mapped.contextWindowExceeded()).isFalse();
              });
    }

    @Test
    void usesErrorTypeWhenErrorHasNoCode() {
      final var failure =
          RateLimitException.builder()
              .headers(Headers.builder().build())
              .error(
                  ErrorObject.builder()
                      .code((String) null)
                      .message("slow down")
                      .param((String) null)
                      .type("rate_limit")
                      .build())
              .build();

      assertThat(mapper.map(failure))
          .hasValueSatisfying(
              mapped -> {
                assertThat(mapped.statusCode()).isEqualTo(429);
                assertThat(mapped.type()).isEqualTo("rate_limit");
              });
    }

    private static BadRequestException badRequest(String code, String message) {
      return BadRequestException.builder()
          .headers(Headers.builder().build())
          .error(
              ErrorObject.builder()
                  .code(code)
                  .message(message)
                  .param((String) null)
                  .type("invalid_request_error")
                  .build())
          .build();
    }
  }

  @Nested
  class Gemini {

    private final GeminiSdkFailureMapper mapper = new GeminiSdkFailureMapper();

    @Test
    void mapsStatusAndStatusText() {
      assertThat(mapper.map(new ClientException(429, "RESOURCE_EXHAUSTED", "slow down")))
          .hasValue(new SdkFailure(429, "RESOURCE_EXHAUSTED", "slow down", false));
    }

    @Test
    void mapsOverLengthPromptToContextWindowExceeded() {
      final var failure =
          new ClientException(
              400,
              "INVALID_ARGUMENT",
              "The input token count (1236488) exceeds the maximum number of tokens allowed"
                  + " (1048576).");

      assertThat(mapper.map(failure))
          .hasValueSatisfying(mapped -> assertThat(mapped.contextWindowExceeded()).isTrue());
    }

    @Test
    void doesNotMapOtherInvalidArgumentToContextWindowExceeded() {
      assertThat(mapper.map(new ClientException(400, "INVALID_ARGUMENT", "Bad schema")))
          .hasValueSatisfying(mapped -> assertThat(mapped.contextWindowExceeded()).isFalse());
    }
  }

  @Nested
  class Bedrock {

    private final BedrockSdkFailureMapper mapper = new BedrockSdkFailureMapper();

    @Test
    void mapsStatusAndAwsErrorCode() {
      final var failure =
          AwsServiceException.builder()
              .statusCode(429)
              .message("slow down")
              .awsErrorDetails(
                  AwsErrorDetails.builder()
                      .errorCode("ThrottlingException")
                      .errorMessage("slow down")
                      .serviceName("BedrockRuntime")
                      .build())
              .build();

      assertThat(mapper.map(failure))
          .hasValueSatisfying(
              mapped -> {
                assertThat(mapped.statusCode()).isEqualTo(429);
                assertThat(mapped.type()).isEqualTo("ThrottlingException");
                assertThat(mapped.message()).isEqualTo("slow down");
                assertThat(mapped.contextWindowExceeded()).isFalse();
              });
    }

    @Test
    void mapsFailureWithoutErrorDetailsToNoType() {
      final var failure = AwsServiceException.builder().statusCode(503).message("down").build();

      assertThat(mapper.map(failure))
          .hasValueSatisfying(mapped -> assertThat(mapped.type()).isNull());
    }
  }
}
