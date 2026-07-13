/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import com.anthropic.core.http.StreamResponse;
import com.anthropic.helpers.BetaMessageAccumulator;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaRawMessageStreamEvent;

/**
 * Drives a streamed Anthropic Messages API (beta client) response to a single, fully-assembled
 * {@link BetaMessage}, equivalent to what the non-streaming API would have returned. Extracted as
 * its own seam (rather than inlined in {@link AnthropicChatModelApi}) so tests can inject a canned
 * {@link BetaMessage} without needing to feed a full, valid raw event sequence through the vendor
 * SDK's {@link BetaMessageAccumulator} (which throws unless driven from a {@code message_start}
 * through a {@code message_stop} event).
 *
 * <p>Uses the <strong>beta</strong> messages client types (rather than the stable {@code
 * com.anthropic.models.messages} family) since the beta client is required for upcoming Skills
 * support; this migration is otherwise behavior-identical.
 */
@FunctionalInterface
public interface AnthropicMessageStreamAssembler {

  BetaMessage assemble(StreamResponse<BetaRawMessageStreamEvent> stream);

  /** Default implementation, backed by the vendor SDK's {@link BetaMessageAccumulator}. */
  static AnthropicMessageStreamAssembler accumulating() {
    return stream -> {
      final BetaMessageAccumulator accumulator = BetaMessageAccumulator.create();
      stream.stream().forEach(accumulator::accumulate);
      return accumulator.message();
    };
  }
}
