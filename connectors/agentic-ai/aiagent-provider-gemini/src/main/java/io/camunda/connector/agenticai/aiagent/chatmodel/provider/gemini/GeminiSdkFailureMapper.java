/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini;

import com.google.genai.errors.ApiException;
import io.camunda.connector.agenticai.aiagent.chatmodel.envelope.SdkFailure;
import io.camunda.connector.agenticai.aiagent.chatmodel.envelope.SdkFailureMapper;
import java.util.Optional;

/** Recognizes the failures the Gemini SDK reports for a response of the Gemini API. */
public class GeminiSdkFailureMapper implements SdkFailureMapper {

  // Gemini has no dedicated exception type or error code for an over-length prompt: the HTTP status
  // and status text (400, INVALID_ARGUMENT) are shared with many unrelated validation failures, so
  // detection falls back to this stable substring of the message. It is identical across the
  // Developer API and Vertex AI backends; the token counts around it vary per request.
  private static final String CONTEXT_WINDOW_EXCEEDED_MESSAGE =
      "exceeds the maximum number of tokens allowed";

  @Override
  public Optional<SdkFailure> map(Throwable failure) {
    if (failure instanceof ApiException e) {
      return Optional.of(
          new SdkFailure(
              e.code(),
              e.status(),
              e.message(),
              e.code() == 400
                  && e.message() != null
                  && e.message().contains(CONTEXT_WINDOW_EXCEEDED_MESSAGE)));
    }
    return Optional.empty();
  }
}
