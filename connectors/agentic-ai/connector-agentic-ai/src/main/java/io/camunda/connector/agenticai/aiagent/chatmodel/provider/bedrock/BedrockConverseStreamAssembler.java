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
import software.amazon.awssdk.services.bedrockruntime.model.Citation;
import software.amazon.awssdk.services.bedrockruntime.model.CitationGeneratedContent;
import software.amazon.awssdk.services.bedrockruntime.model.CitationsContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.CitationsDelta;
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
 * Accumulates the delta-based {@code ConverseStream} event sequence into a {@link ConverseResponse}
 * equivalent to what the non-streaming {@code converse} operation would have returned, so {@link
 * BedrockConverseResponseConverter} can be reused unchanged on the assembled result.
 *
 * <p>One instance is created per streamed call and registered as the {@link
 * ConverseStreamResponseHandler.Visitor}; once the stream completes, {@link #converseResponse()}
 * returns the assembled result. Content blocks are keyed by {@code contentBlockIndex} in a {@link
 * TreeMap}, so they're emitted in index order regardless of arrival order. Tool-use input arrives
 * as successive JSON string fragments, concatenated per block and parsed into a {@link Document} at
 * {@code contentBlockStop}. Reasoning content arrives as three independent delta shapes ({@code
 * text}, {@code signature}, {@code redactedContent}; see {@link ReasoningContentBlockDelta}), each
 * accumulated separately and combined into one {@link ReasoningContentBlock} at {@code
 * contentBlockStop} - redacted if any {@code redactedContent} bytes arrived, else a {@code
 * reasoningText} block. Each {@code citation} delta carries one complete {@link Citation} to append
 * to the block's citation list (unlike text, its fields never arrive fragmented across multiple
 * deltas); a block with at least one accumulated citation finalizes as a {@link
 * CitationsContentBlock} pairing the accumulated text with the accumulated citations, rather than
 * as plain text.
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
  private boolean messageStopReceived;

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
      accumulator.toolUseType = start.toolUse().typeAsString();
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
    final CitationsDelta citationDelta = delta.citation();
    if (citationDelta != null) {
      accumulator.citations.add(
          BedrockConverseSdkPojoCodec.replay(
              BedrockConverseSdkPojoCodec.capture(citationDelta), Citation::builder));
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
    messageStopReceived = true;
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
   * Returns the assembled {@link ConverseResponse}. Throws {@link IllegalStateException} if the
   * stream hasn't completed yet ({@code visitMessageStop} not received), rather than returning a
   * response silently assembled from a partial event sequence.
   */
  public ConverseResponse converseResponse() {
    if (!messageStopReceived) {
      throw new IllegalStateException(
          "converseResponse() called before the stream completed (no messageStop event received)");
    }
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
   * Finalizes a single block's accumulated deltas into a {@link ContentBlock}: {@code toolUse}
   * (seeded at {@code contentBlockStart}), {@code reasoningContent} (redacted or text+signature),
   * {@code citationsContent} if any citation deltas arrived, and otherwise plain {@code text}. The
   * first two are shapes {@link BedrockConverseResponseConverter} maps explicitly; {@code
   * citationsContent} instead falls through its generic unmapped-block preservation.
   */
  private ContentBlock finalize(BlockAccumulator accumulator) {
    if (accumulator.toolUseId != null || accumulator.toolUseName != null) {
      final Document input = parseToolUseInput(accumulator.toolInput.toString());
      final ToolUseBlock.Builder toolUseBuilder =
          ToolUseBlock.builder()
              .toolUseId(accumulator.toolUseId)
              .name(accumulator.toolUseName)
              .input(input);
      if (accumulator.toolUseType != null) {
        toolUseBuilder.type(accumulator.toolUseType);
      }
      return ContentBlock.fromToolUse(toolUseBuilder.build());
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

    if (!accumulator.citations.isEmpty()) {
      return ContentBlock.fromCitationsContent(
          CitationsContentBlock.builder()
              .content(CitationGeneratedContent.builder().text(accumulator.text.toString()).build())
              .citations(accumulator.citations)
              .build());
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
      return toAwsDocument(objectMapper.readValue(concatenatedInput, Object.class));
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
  private Document toAwsDocument(@Nullable Object value) {
    try {
      return BedrockConverseDocuments.toAwsDocument(value, objectMapper);
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
    private @Nullable String toolUseType;
    private final StringBuilder text = new StringBuilder();
    private final StringBuilder toolInput = new StringBuilder();
    private final StringBuilder reasoningText = new StringBuilder();
    private final StringBuilder reasoningSignature = new StringBuilder();
    private final ByteArrayOutputStream reasoningRedactedContent = new ByteArrayOutputStream();
    private final List<Citation> citations = new ArrayList<>();
  }
}
