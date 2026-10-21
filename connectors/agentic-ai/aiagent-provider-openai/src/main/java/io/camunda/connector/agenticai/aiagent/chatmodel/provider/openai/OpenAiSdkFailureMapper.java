/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import com.openai.errors.BadRequestException;
import com.openai.errors.OpenAIServiceException;
import io.camunda.connector.agenticai.aiagent.chatmodel.envelope.SdkFailure;
import io.camunda.connector.agenticai.aiagent.chatmodel.envelope.SdkFailureMapper;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Recognizes the failures the OpenAI SDK reports for a response of an OpenAI-compatible API. Also
 * serves the providers that use this SDK for their OpenAI-compatible API.
 */
public class OpenAiSdkFailureMapper implements SdkFailureMapper {

  // The error codes are plain strings, not modeled as an enum by the SDK. An over-length request is
  // rejected outright with an HTTP 400 carrying this code, on both API families.
  private static final String ERROR_CODE_CONTEXT_LENGTH_EXCEEDED = "context_length_exceeded";

  @Override
  public Optional<SdkFailure> map(Throwable failure) {
    if (failure instanceof OpenAIServiceException e) {
      final var code = e.code().orElse(null);
      return Optional.of(
          new SdkFailure(
              e.statusCode(),
              code != null ? code : e.type().orElse(null),
              withoutStatusPrefix(e),
              e instanceof BadRequestException && ERROR_CODE_CONTEXT_LENGTH_EXCEEDED.equals(code)));
    }
    return Optional.empty();
  }

  /** The message of the SDK exception starts with the status, which the envelope reports itself. */
  private static @Nullable String withoutStatusPrefix(OpenAIServiceException e) {
    final var prefix = e.statusCode() + ": ";
    final var message = e.getMessage();
    return message != null && message.startsWith(prefix)
        ? message.substring(prefix.length())
        : message;
  }
}
