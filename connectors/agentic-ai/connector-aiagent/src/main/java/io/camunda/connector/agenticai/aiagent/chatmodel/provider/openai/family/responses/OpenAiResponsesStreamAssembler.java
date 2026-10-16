/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses;

import com.openai.core.http.StreamResponse;
import com.openai.helpers.ResponseAccumulator;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseStreamEvent;

/**
 * Drives a streamed OpenAI Responses API response to a single, fully-assembled {@link Response},
 * equivalent to what the non-streaming API would have returned. Extracted as its own seam (rather
 * than inlined in the chat model API implementation) so tests can inject a canned {@link Response}
 * without needing to feed a full, valid raw event sequence through the vendor SDK's {@link
 * ResponseAccumulator}.
 */
@FunctionalInterface
public interface OpenAiResponsesStreamAssembler {

  Response assemble(StreamResponse<ResponseStreamEvent> stream);

  /** Default implementation, backed by the vendor SDK's {@link ResponseAccumulator}. */
  static OpenAiResponsesStreamAssembler accumulating() {
    return stream -> {
      final ResponseAccumulator accumulator = ResponseAccumulator.create();
      stream.stream().forEach(accumulator::accumulate);
      return accumulator.response();
    };
  }
}
