/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStart;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseMetrics;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetrics;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningTextBlock;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * Accumulates the delta-based {@code ConverseStream} event sequence ({@code messageStart}, {@code
 * contentBlockStart}, {@code contentBlockDelta}, {@code contentBlockStop}, {@code messageStop},
 * {@code metadata}) emitted by {@code BedrockRuntimeAsyncClient.converseStream} into a {@link
 * ConverseResponse} equivalent to what the non-streaming {@code converse} operation would have
 * returned, so that {@link BedrockConverseResponseConverter} can be reused unchanged on the
 * assembled result.
 *
 * <p>One instance is created per streamed call and registered as the {@link
 * ConverseStreamResponseHandler.Visitor} (e.g. via {@code
 * ConverseStreamResponseHandler.builder().subscriber(assembler)}); once the stream completes,
 * {@link #converseResponse()} returns the assembled result.
 *
 * <p><strong>Ordering.</strong> Content blocks are keyed by {@code contentBlockIndex} in a {@link
 * TreeMap}, so the assembled {@link Message#content()} is always emitted in index order regardless
 * of the arrival order of interleaved block events.
 *
 * <p><strong>Tool-use input.</strong> {@code toolUse} deltas carry {@code input} as successive JSON
 * string fragments (see {@link
 * software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlockDelta#input()}); this assembler
 * concatenates them per block and parses the result into an AWS {@link Document} at {@code
 * contentBlockStop}, exactly once per block.
 *
 * <p><strong>Reasoning content.</strong> {@code reasoningContent} deltas carry {@code text}, {@code
 * signature} and {@code redactedContent} as three independent delta shapes (see {@link
 * ReasoningContentBlockDelta}); each is accumulated separately (text/signature as strings,
 * redactedContent as bytes) and only combined into one {@link ReasoningContentBlock} at {@code
 * contentBlockStop}. A block with any {@code redactedContent} bytes is assembled as a redacted
 * block; otherwise it is assembled as a {@code reasoningText} block (matching the Converse API's
 * mutually-exclusive {@code reasoningText}/{@code redactedContent} union).
 */
public final class BedrockConverseStreamAssembler implements ConverseStreamResponseHandler.Visitor {

  private final ObjectMapper objectMapper;

  private final NavigableMap<Integer, BlockAccumulator> accumulators = new TreeMap<>();
  private final NavigableMap<Integer, ContentBlock> finalizedBlocks = new TreeMap<>();

  private @Nullable String role;
  private @Nullable String stopReason;
  private @Nullable Document additionalModelResponseFields;
  private @Nullable TokenUsage usage;
  private @Nullable ConverseMetrics metrics;

  public BedrockConverseStreamAssembler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void visitMessageStart(MessageStartEvent event) {
    role = event.roleAsString();
  }

  @Override
  public void visitContentBlockStart(ContentBlockStartEvent event) {
    final BlockAccumulator accumulator = accumulatorAt(event.contentBlockIndex());
    final ContentBlockStart start = event.start();
    if (start != null && start.toolUse() != null) {
      accumulator.toolUseId = start.toolUse().toolUseId();
      accumulator.toolUseName = start.toolUse().name();
    }
  }

  @Override
  public void visitContentBlockDelta(ContentBlockDeltaEvent event) {
    final BlockAccumulator accumulator = accumulatorAt(event.contentBlockIndex());
    final var delta = event.delta();
    if (delta.text() != null) {
      accumulator.text.append(delta.text());
    }
    if (delta.toolUse() != null) {
      accumulator.toolInput.append(delta.toolUse().input());
    }
    final ReasoningContentBlockDelta reasoningDelta = delta.reasoningContent();
    if (reasoningDelta != null) {
      if (reasoningDelta.text() != null) {
        accumulator.reasoningText.append(reasoningDelta.text());
      }
      if (reasoningDelta.signature() != null) {
        accumulator.reasoningSignature.append(reasoningDelta.signature());
      }
      if (reasoningDelta.redactedContent() != null) {
        accumulator.reasoningRedactedContent.writeBytes(
            reasoningDelta.redactedContent().asByteArray());
      }
    }
  }

  @Override
  public void visitContentBlockStop(ContentBlockStopEvent event) {
    final int index = event.contentBlockIndex();
    final BlockAccumulator accumulator = accumulatorAt(index);
    finalizedBlocks.put(index, finalize(accumulator));
  }

  @Override
  public void visitMessageStop(MessageStopEvent event) {
    stopReason = event.stopReasonAsString();
    additionalModelResponseFields = event.additionalModelResponseFields();
  }

  @Override
  public void visitMetadata(ConverseStreamMetadataEvent event) {
    usage = event.usage();
    final ConverseStreamMetrics streamMetrics = event.metrics();
    if (streamMetrics != null) {
      metrics = ConverseMetrics.builder().latencyMs(streamMetrics.latencyMs()).build();
    }
  }

  /**
   * Returns the assembled {@link ConverseResponse}. May be called once the stream has completed
   * (after {@code visitMessageStop}/{@code visitMetadata} have been invoked).
   */
  public ConverseResponse converseResponse() {
    final List<ContentBlock> content = new ArrayList<>(finalizedBlocks.values());
    final Message message = Message.builder().role(role).content(content).build();
    final ConverseOutput output = ConverseOutput.builder().message(message).build();

    final ConverseResponse.Builder builder =
        ConverseResponse.builder().output(output).stopReason(stopReason);
    if (usage != null) {
      builder.usage(usage);
    }
    if (metrics != null) {
      builder.metrics(metrics);
    }
    if (additionalModelResponseFields != null) {
      builder.additionalModelResponseFields(additionalModelResponseFields);
    }
    return builder.build();
  }

  private BlockAccumulator accumulatorAt(int index) {
    return accumulators.computeIfAbsent(index, i -> new BlockAccumulator());
  }

  /**
   * Finalizes a single block's accumulated deltas into a {@link ContentBlock}, in the same three
   * shapes {@link BedrockConverseResponseConverter} understands: {@code toolUse} (seeded at {@code
   * contentBlockStart}), {@code reasoningContent} (redacted or text+signature), and otherwise plain
   * {@code text}.
   */
  private ContentBlock finalize(BlockAccumulator accumulator) {
    if (accumulator.toolUseId != null || accumulator.toolUseName != null) {
      final Document input = parseToolUseInput(accumulator.toolInput.toString());
      return ContentBlock.fromToolUse(
          ToolUseBlock.builder()
              .toolUseId(accumulator.toolUseId)
              .name(accumulator.toolUseName)
              .input(input)
              .build());
    }

    final boolean hasRedactedContent = accumulator.reasoningRedactedContent.size() > 0;
    final boolean hasReasoningText =
        !accumulator.reasoningText.isEmpty() || !accumulator.reasoningSignature.isEmpty();
    if (hasRedactedContent || hasReasoningText) {
      final ReasoningContentBlock.Builder reasoningContentBuilder = ReasoningContentBlock.builder();
      if (hasRedactedContent) {
        reasoningContentBuilder.redactedContent(
            SdkBytes.fromByteArray(accumulator.reasoningRedactedContent.toByteArray()));
      } else {
        final ReasoningTextBlock.Builder reasoningTextBuilder =
            ReasoningTextBlock.builder().text(accumulator.reasoningText.toString());
        if (!accumulator.reasoningSignature.isEmpty()) {
          reasoningTextBuilder.signature(accumulator.reasoningSignature.toString());
        }
        reasoningContentBuilder.reasoningText(reasoningTextBuilder.build());
      }
      return ContentBlock.fromReasoningContent(reasoningContentBuilder.build());
    }

    return ContentBlock.fromText(accumulator.text.toString());
  }

  /**
   * Parses the fully-concatenated tool-use input JSON string into an AWS {@link Document},
   * following the same plain-Java-value-tree conversion pattern used elsewhere in this package
   * (e.g. {@code BedrockConverseRequestConverter}). A blank concatenation (a no-argument tool call,
   * per Converse's own "empty object" convention) becomes an empty {@code Document} map rather than
   * a parse error.
   */
  private Document parseToolUseInput(String concatenatedInput) {
    if (concatenatedInput.isBlank()) {
      return Document.fromMap(Map.of());
    }
    try {
      return toDocument(objectMapper.readValue(concatenatedInput, Object.class));
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          ERROR_CODE_FAILED_MODEL_CALL,
          "Failed to parse Bedrock Converse streamed tool-use input JSON fragments: "
              + e.getMessage(),
          e);
    }
  }

  /**
   * Converts the parsed streamed tool-use input JSON value tree into an AWS {@link Document}. See
   * {@link BedrockConverseDocuments} for the conversion policy shared with the other Bedrock
   * Converse converters; a value that policy cannot make sense of either is reported here as a
   * failed model call, since it originates from the model's own (malformed) streamed response.
   */
  private Document toDocument(@Nullable Object value) {
    try {
      return BedrockConverseDocuments.toDocument(value, objectMapper);
    } catch (RuntimeException e) {
      throw new ConnectorException(
          ERROR_CODE_FAILED_MODEL_CALL,
          "Unsupported JSON value encountered while parsing Bedrock Converse streamed tool-use "
              + "input: "
              + e.getMessage(),
          e);
    }
  }

  /** Mutable per-{@code contentBlockIndex} accumulation state for one content block. */
  private static final class BlockAccumulator {
    private @Nullable String toolUseId;
    private @Nullable String toolUseName;
    private final StringBuilder text = new StringBuilder();
    private final StringBuilder toolInput = new StringBuilder();
    private final StringBuilder reasoningText = new StringBuilder();
    private final StringBuilder reasoningSignature = new StringBuilder();
    private final ByteArrayOutputStream reasoningRedactedContent = new ByteArrayOutputStream();
  }
}
