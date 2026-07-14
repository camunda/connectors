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
import com.anthropic.models.beta.messages.BetaRawContentBlockDelta;
import com.anthropic.models.beta.messages.BetaRawContentBlockDeltaEvent;
import com.anthropic.models.beta.messages.BetaRawContentBlockStartEvent;
import com.anthropic.models.beta.messages.BetaRawContentBlockStopEvent;
import com.anthropic.models.beta.messages.BetaRawMessageDeltaEvent;
import com.anthropic.models.beta.messages.BetaRawMessageStartEvent;
import com.anthropic.models.beta.messages.BetaRawMessageStreamEvent;
import com.anthropic.models.beta.messages.BetaStopReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  Logger LOG = LoggerFactory.getLogger(AnthropicMessageStreamAssembler.class);

  BetaMessage assemble(StreamResponse<BetaRawMessageStreamEvent> stream);

  /** Default implementation, backed by the vendor SDK's {@link BetaMessageAccumulator}. */
  static AnthropicMessageStreamAssembler accumulating() {
    return stream -> {
      final BetaMessageAccumulator accumulator = BetaMessageAccumulator.create();
      stream.stream()
          .forEach(
              event -> {
                if (LOG.isTraceEnabled()) {
                  LOG.trace("Anthropic stream event: {}", describeEvent(event));
                }
                accumulator.accumulate(event);
              });
      return accumulator.message();
    };
  }

  /**
   * Builds a compact, single-line, metadata-only description of a raw Anthropic stream event for
   * TRACE logging: the event kind plus its discriminating metadata (block index/type, delta type,
   * stop reason, usage), never the event's full payload (text/JSON bodies, tool inputs, etc.). Any
   * event variant not recognized by this SDK version falls back to a generic label instead of
   * throwing.
   */
  static String describeEvent(BetaRawMessageStreamEvent event) {
    if (event.isMessageStart()) {
      final BetaRawMessageStartEvent messageStart = event.asMessageStart();
      final BetaMessage message = messageStart.message();
      return "message_start{id=%s, model=%s}".formatted(message.id(), message.model().asString());
    } else if (event.isContentBlockStart()) {
      final BetaRawContentBlockStartEvent contentBlockStart = event.asContentBlockStart();
      return "content_block_start{index=%d, blockType=%s}"
          .formatted(
              contentBlockStart.index(),
              describeContentBlockType(contentBlockStart.contentBlock()));
    } else if (event.isContentBlockDelta()) {
      final BetaRawContentBlockDeltaEvent contentBlockDelta = event.asContentBlockDelta();
      return "content_block_delta{index=%d, deltaType=%s}"
          .formatted(contentBlockDelta.index(), describeDeltaType(contentBlockDelta.delta()));
    } else if (event.isContentBlockStop()) {
      final BetaRawContentBlockStopEvent contentBlockStop = event.asContentBlockStop();
      return "content_block_stop{index=%d}".formatted(contentBlockStop.index());
    } else if (event.isMessageDelta()) {
      final BetaRawMessageDeltaEvent messageDelta = event.asMessageDelta();
      final String stopReason =
          messageDelta.delta().stopReason().map(BetaStopReason::asString).orElse("<none>");
      return "message_delta{stopReason=%s, outputTokens=%d}"
          .formatted(stopReason, messageDelta.usage().outputTokens());
    } else if (event.isMessageStop()) {
      return "message_stop{}";
    } else {
      return "unknown{eventClass=%s}".formatted(event.getClass().getSimpleName());
    }
  }

  /**
   * Resolves the started block's discriminating TYPE (e.g. {@code text}, {@code tool_use}, {@code
   * server_tool_use}, {@code code_execution_tool_result}, {@code container_upload}, {@code
   * thinking}, ...) without exposing the block's payload. Falls back to a generic label for a block
   * variant not recognized by this SDK version.
   */
  private static String describeContentBlockType(BetaRawContentBlockStartEvent.ContentBlock block) {
    if (block.isText()) {
      return "text";
    } else if (block.isThinking()) {
      return "thinking";
    } else if (block.isRedactedThinking()) {
      return "redacted_thinking";
    } else if (block.isToolUse()) {
      return "tool_use";
    } else if (block.isServerToolUse()) {
      return "server_tool_use";
    } else if (block.isWebSearchToolResult()) {
      return "web_search_tool_result";
    } else if (block.isWebFetchToolResult()) {
      return "web_fetch_tool_result";
    } else if (block.isAdvisorToolResult()) {
      return "advisor_tool_result";
    } else if (block.isCodeExecutionToolResult()) {
      return "code_execution_tool_result";
    } else if (block.isBashCodeExecutionToolResult()) {
      return "bash_code_execution_tool_result";
    } else if (block.isTextEditorCodeExecutionToolResult()) {
      return "text_editor_code_execution_tool_result";
    } else if (block.isToolSearchToolResult()) {
      return "tool_search_tool_result";
    } else if (block.isMcpToolUse()) {
      return "mcp_tool_use";
    } else if (block.isMcpToolResult()) {
      return "mcp_tool_result";
    } else if (block.isContainerUpload()) {
      return "container_upload";
    } else if (block.isCompaction()) {
      return "compaction";
    } else if (block.isFallback()) {
      return "fallback";
    } else {
      return "unknown";
    }
  }

  /**
   * Resolves a content-block delta's discriminating TYPE (e.g. {@code text_delta}, {@code
   * input_json_delta}, {@code thinking_delta}, ...) without exposing the delta's body. Falls back
   * to a generic label for a delta variant not recognized by this SDK version.
   */
  private static String describeDeltaType(BetaRawContentBlockDelta delta) {
    if (delta.isText()) {
      return "text_delta";
    } else if (delta.isInputJson()) {
      return "input_json_delta";
    } else if (delta.isCitations()) {
      return "citations_delta";
    } else if (delta.isThinking()) {
      return "thinking_delta";
    } else if (delta.isSignature()) {
      return "signature_delta";
    } else if (delta.isCompaction()) {
      return "compaction_delta";
    } else {
      return "unknown";
    }
  }
}
