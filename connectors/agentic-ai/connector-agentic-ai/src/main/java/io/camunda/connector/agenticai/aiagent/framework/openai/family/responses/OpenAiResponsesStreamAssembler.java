/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai.family.responses;

import com.openai.core.http.StreamResponse;
import com.openai.helpers.ResponseAccumulator;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseStreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives a streamed OpenAI Responses API response to a single, fully-assembled {@link Response},
 * equivalent to what the non-streaming API would have returned. Extracted as its own seam (rather
 * than inlined in the chat model API implementation) so tests can inject a canned {@link Response}
 * without needing to feed a full, valid raw event sequence through the vendor SDK's {@link
 * ResponseAccumulator}.
 */
@FunctionalInterface
public interface OpenAiResponsesStreamAssembler {

  Logger LOG = LoggerFactory.getLogger(OpenAiResponsesStreamAssembler.class);

  Response assemble(StreamResponse<ResponseStreamEvent> stream);

  /** Default implementation, backed by the vendor SDK's {@link ResponseAccumulator}. */
  static OpenAiResponsesStreamAssembler accumulating() {
    return stream -> {
      final ResponseAccumulator accumulator = ResponseAccumulator.create();
      stream.stream()
          .forEach(
              event -> {
                if (LOG.isTraceEnabled()) {
                  LOG.trace("OpenAI Responses stream event: {}", describeEvent(event));
                }
                accumulator.accumulate(event);
              });
      return accumulator.response();
    };
  }

  /**
   * Builds a compact, single-line, metadata-only description of a raw OpenAI Responses stream event
   * for TRACE logging: the event kind, never the event's full payload (text/JSON bodies, tool
   * arguments, etc.). Any event variant not recognized by this SDK version falls back to a generic
   * label instead of throwing.
   */
  static String describeEvent(ResponseStreamEvent event) {
    if (event.created().isPresent()) {
      return "created{id=%s}".formatted(event.created().get().response().id());
    } else if (event.outputItemAdded().isPresent()) {
      return "output_item.added{index=%d}".formatted(event.outputItemAdded().get().outputIndex());
    } else if (event.outputItemDone().isPresent()) {
      return "output_item.done{index=%d}".formatted(event.outputItemDone().get().outputIndex());
    } else if (event.outputTextDelta().isPresent()) {
      return "output_text.delta{}";
    } else if (event.outputTextDone().isPresent()) {
      return "output_text.done{}";
    } else if (event.functionCallArgumentsDelta().isPresent()) {
      return "function_call_arguments.delta{}";
    } else if (event.functionCallArgumentsDone().isPresent()) {
      return "function_call_arguments.done{}";
    } else if (event.reasoningSummaryTextDelta().isPresent()) {
      return "reasoning_summary_text.delta{}";
    } else if (event.reasoningSummaryTextDone().isPresent()) {
      return "reasoning_summary_text.done{}";
    } else if (event.completed().isPresent()) {
      return "completed{id=%s}".formatted(event.completed().get().response().id());
    } else if (event.failed().isPresent()) {
      return "failed{}";
    } else if (event.incomplete().isPresent()) {
      return "incomplete{}";
    } else if (event.error().isPresent()) {
      return "error{}";
    } else {
      return "unknown{eventClass=%s}".formatted(event.getClass().getSimpleName());
    }
  }
}
