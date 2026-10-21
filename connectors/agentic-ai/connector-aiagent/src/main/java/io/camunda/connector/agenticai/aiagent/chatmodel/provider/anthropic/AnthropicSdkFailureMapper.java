/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.ErrorType;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.agenticai.aiagent.chatmodel.envelope.SdkFailure;
import io.camunda.connector.agenticai.aiagent.chatmodel.envelope.SdkFailureMapper;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Recognizes the failures the Anthropic SDK reports for a response of the Anthropic API. */
public class AnthropicSdkFailureMapper implements SdkFailureMapper {

  @Override
  public Optional<SdkFailure> map(Throwable failure) {
    if (failure instanceof AnthropicServiceException e) {
      return Optional.of(
          new SdkFailure(
              e.statusCode(),
              e.errorType().map(ErrorType::asString).orElse(null),
              message(e),
              false));
    }
    return Optional.empty();
  }

  /**
   * The message of the error in the body of the response. The message of the SDK exception holds
   * the status and the whole body.
   */
  private static @Nullable String message(AnthropicServiceException e) {
    try {
      final var node = e.body().convert(JsonNode.class);
      if (node != null && node.path("error").path("message").isTextual()) {
        return node.path("error").path("message").asText();
      }
    } catch (RuntimeException unreadableBody) {
      // report the message of the SDK exception instead
    }
    return e.getMessage();
  }
}
