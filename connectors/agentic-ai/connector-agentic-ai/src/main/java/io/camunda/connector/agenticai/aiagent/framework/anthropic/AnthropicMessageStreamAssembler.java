/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import com.anthropic.core.http.StreamResponse;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.RawMessageStreamEvent;

/**
 * Drives a streamed Anthropic Messages API response to a single, fully-assembled {@link Message},
 * equivalent to what the non-streaming API would have returned. Extracted as its own seam (rather
 * than inlined in {@link AnthropicChatModelApi}) so tests can inject a canned {@link Message}
 * without needing to feed a full, valid raw event sequence through the vendor SDK's {@link
 * MessageAccumulator} (which throws unless driven from a {@code message_start} through a {@code
 * message_stop} event).
 */
@FunctionalInterface
public interface AnthropicMessageStreamAssembler {

  Message assemble(StreamResponse<RawMessageStreamEvent> stream);

  /** Default implementation, backed by the vendor SDK's {@link MessageAccumulator}. */
  static AnthropicMessageStreamAssembler accumulating() {
    return stream -> {
      final MessageAccumulator accumulator = MessageAccumulator.create();
      stream.stream().forEach(accumulator::accumulate);
      return accumulator.message();
    };
  }
}
