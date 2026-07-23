/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import com.anthropic.core.JsonObject;
import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.InputJsonDelta;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.RawContentBlockDeltaEvent;
import com.anthropic.models.messages.RawContentBlockStartEvent;
import com.anthropic.models.messages.RawContentBlockStopEvent;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives a streamed Anthropic Messages API response to a single, fully-assembled {@link Message},
 * equivalent to what the non-streaming API would have returned. Extracted as its own seam (rather
 * than inlined in the Anthropic chat model implementation) so tests can inject a canned {@link
 * Message} without needing to feed a full, valid raw event sequence through the vendor SDK's {@link
 * MessageAccumulator} (which throws unless driven from a {@code message_start} through a {@code
 * message_stop} event).
 */
@FunctionalInterface
public interface AnthropicMessageStreamAssembler {

  Message assemble(StreamResponse<RawMessageStreamEvent> stream);

  /**
   * Default implementation, backed by the vendor SDK's {@link MessageAccumulator}.
   *
   * <p>Works around a vendor SDK bug: {@link MessageAccumulator} requires at least one {@code
   * input_json_delta} event for any tool-input-tracking content block ({@code tool_use} / {@code
   * server_tool_use}), throwing {@link AnthropicInvalidDataException} ("Missing input JSON for
   * index N") otherwise. Anthropic's {@code web_search} server tool delivers its input inline in
   * {@code content_block_start} with zero {@code input_json_delta} events, which trips this bug
   * ({@code code_execution} streams deltas normally and is unaffected). See {@code
   * AnthropicMessageStreamAssemblerTest} for a reproduction.
   *
   * <p>The workaround ({@link InlineToolInputShim}) retries assembly with a synthetic {@code
   * input_json_delta} injected for the affected block(s), but only after the unmodified vendor
   * accumulation attempt actually throws that specific error — so a future SDK/API fix silently
   * disables this shim rather than corrupting data. If the vendor accumulator ever succeeds without
   * hitting the shim despite an inline tool-input candidate being present, a one-time warning is
   * logged asking for the shim to be removed.
   */
  static AnthropicMessageStreamAssembler accumulating() {
    return InlineToolInputShim::assemble;
  }
}

/**
 * Package-private workaround for the vendor SDK bug described on {@link
 * AnthropicMessageStreamAssembler#accumulating()}. Kept out of the public interface since an
 * interface cannot declare private (mutable) state, and this shim needs a one-time-warning flag.
 */
final class InlineToolInputShim {

  private static final Logger LOG = LoggerFactory.getLogger(AnthropicMessageStreamAssembler.class);
  private static final AtomicBoolean SHIM_OBSOLETE_WARNED = new AtomicBoolean(false);

  private InlineToolInputShim() {}

  static Message assemble(StreamResponse<RawMessageStreamEvent> stream) {
    final List<RawMessageStreamEvent> events = stream.stream().toList();
    final Map<Long, String> shimTargets = scanInlineToolInputWithoutDelta(events);

    try {
      final Message message = accumulate(events);
      if (!shimTargets.isEmpty()) {
        warnShimObsoleteOnce();
      }
      return message;
    } catch (AnthropicInvalidDataException e) {
      if (!shimTargets.isEmpty() && isMissingInputJson(e)) {
        return accumulate(injectSyntheticInputJsonDeltas(events, shimTargets));
      }
      throw e;
    }
  }

  private static Message accumulate(List<RawMessageStreamEvent> events) {
    final MessageAccumulator accumulator = MessageAccumulator.create();
    events.forEach(accumulator::accumulate);
    return accumulator.message();
  }

  private static boolean isMissingInputJson(AnthropicInvalidDataException e) {
    return e.getMessage() != null && e.getMessage().contains("Missing input JSON");
  }

  /**
   * Scans the raw event stream for tool-input-tracking blocks whose input was delivered inline in
   * {@code content_block_start} and for which no {@code input_json_delta} event ever arrived for
   * that block's index.
   */
  private static Map<Long, String> scanInlineToolInputWithoutDelta(
      List<RawMessageStreamEvent> events) {
    final Map<Long, String> inlineCandidates = new HashMap<>();
    final Set<Long> sawRealInputJsonDelta = new HashSet<>();

    for (final RawMessageStreamEvent event : events) {
      if (event.contentBlockStart().isPresent()) {
        final RawContentBlockStartEvent start = event.contentBlockStart().get();
        extractNonEmptyInlineToolInput(start.contentBlock())
            .ifPresent(json -> inlineCandidates.put(start.index(), json));
      } else if (event.contentBlockDelta().isPresent()
          && event.contentBlockDelta().get().delta().inputJson().isPresent()) {
        sawRealInputJsonDelta.add(event.contentBlockDelta().get().index());
      }
    }

    final Map<Long, String> shimTargets = new LinkedHashMap<>();
    for (final Map.Entry<Long, String> entry : inlineCandidates.entrySet()) {
      if (!sawRealInputJsonDelta.contains(entry.getKey())) {
        shimTargets.put(entry.getKey(), entry.getValue());
      }
    }
    return shimTargets;
  }

  // Note: the stable RawContentBlockStartEvent.ContentBlock union has no mcpToolUse() variant
  // (MCP connector support is a beta-only feature and out of scope for this PR) -- only
  // serverToolUse()/toolUse() need checking here.
  private static Optional<String> extractNonEmptyInlineToolInput(
      RawContentBlockStartEvent.ContentBlock contentBlock) {
    final JsonValue input;
    if (contentBlock.serverToolUse().isPresent()) {
      input = contentBlock.serverToolUse().get()._input();
    } else if (contentBlock.toolUse().isPresent()) {
      input = contentBlock.toolUse().get()._input();
    } else {
      return Optional.empty();
    }

    if (!(input instanceof JsonObject jo) || jo.values().isEmpty()) {
      return Optional.empty();
    }

    try {
      return Optional.of(ObjectMappers.jsonMapper().writeValueAsString(jo));
    } catch (JsonProcessingException e) {
      // defensive: don't let serialization failure of the shim candidate break assembly
      return Optional.empty();
    }
  }

  private static List<RawMessageStreamEvent> injectSyntheticInputJsonDeltas(
      List<RawMessageStreamEvent> events, Map<Long, String> shimTargets) {
    final List<RawMessageStreamEvent> result = new ArrayList<>(events.size() + shimTargets.size());
    for (final RawMessageStreamEvent event : events) {
      if (event.contentBlockStop().isPresent()) {
        final RawContentBlockStopEvent stop = event.contentBlockStop().get();
        final String partialJson = shimTargets.get(stop.index());
        if (partialJson != null) {
          result.add(syntheticInputJsonDeltaEvent(stop.index(), partialJson));
        }
      }
      result.add(event);
    }
    return result;
  }

  private static RawMessageStreamEvent syntheticInputJsonDeltaEvent(
      long index, String partialJson) {
    return RawMessageStreamEvent.ofContentBlockDelta(
        RawContentBlockDeltaEvent.builder()
            .index(index)
            .delta(InputJsonDelta.builder().partialJson(partialJson).build())
            .build());
  }

  private static void warnShimObsoleteOnce() {
    if (SHIM_OBSOLETE_WARNED.compareAndSet(false, true)) {
      LOG.warn(
          "Anthropic streamed a tool-input block inline with no input_json_delta events, yet "
              + "MessageAccumulator assembled it without the workaround. The inline-tool-input "
              + "shim in AnthropicMessageStreamAssembler appears obsolete (SDK/API fixed upstream) "
              + "and can be removed.");
    }
  }
}
