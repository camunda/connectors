/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral;

import com.fasterxml.jackson.core.type.TypeReference;
import com.openai.core.JsonField;
import com.openai.core.JsonNull;
import com.openai.core.JsonValue;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Accumulates a streamed Mistral response into a single {@link ChatCompletion}, the same way the
 * vendor SDK's {@code ChatCompletionAccumulator} does, except {@code content} may arrive as a
 * chunked array (a {@code thinking} chunk, then a {@code text} chunk) instead of a plain string;
 * the vendor accumulator's typed, string-only {@code content()} accessor throws on that shape. This
 * class instead reads the raw {@code delta._content()} value and self-detects per delta whether it
 * is a string or a chunk array.
 *
 * <p>Duplicated wholesale rather than subclassing the vendor accumulator: it's a final Kotlin class
 * with a private constructor reachable only via its own {@code create()}, and every field the
 * content-accumulation logic would need to share is private, so there is no extension point to hook
 * into. Everything but {@code content} mirrors the vendor accumulator field-for-field (find it in
 * the {@code openai-java-core} sources jar) so an SDK upgrade can be diffed against it directly;
 * log probabilities and the legacy singular {@code function_call} are dropped rather than ported
 * since neither is ever read by this connector.
 *
 * <p>One deliberate divergence from the vendor accumulator: {@code usage} is applied to the builder
 * unconditionally, before that chunk's {@code choices} are processed. OpenAI always sends {@code
 * usage} on its own trailing, choices-less chunk; Mistral sends it on the very same chunk that also
 * carries the closing delta and {@code finishReason}. Mirroring the vendor's order would silently
 * drop that chunk's choices and fail with "choices is required" once the stream completes.
 */
final class ChunkedContentChatCompletionAccumulator {

  private final Map<Long, ChatCompletion.Choice.Builder> choiceBuilders = new TreeMap<>();
  private final Map<Long, ChatCompletionMessage.Builder> messageBuilders = new TreeMap<>();
  private final Map<Long, ContentAccumulator> messageContents = new TreeMap<>();
  private final Map<Long, String> messageRefusals = new TreeMap<>();
  private final Map<Long, Map<Long, ChatCompletionMessageFunctionToolCall.Builder>>
      toolCallBuilders = new TreeMap<>();
  private final Map<Long, Map<Long, ChatCompletionMessageFunctionToolCall.Function.Builder>>
      toolCallFunctionBuilders = new TreeMap<>();
  private final Map<Long, Map<Long, String>> toolCallFunctionArgs = new TreeMap<>();
  private final Map<Long, Boolean> isFinished = new TreeMap<>();

  private ChatCompletion.@Nullable Builder chatCompletionBuilder;
  private @Nullable ChatCompletion chatCompletion;

  private ChunkedContentChatCompletionAccumulator() {}

  static ChunkedContentChatCompletionAccumulator create() {
    return new ChunkedContentChatCompletionAccumulator();
  }

  /** Only valid after the last chunk (finish reason, plus an optional trailing usage chunk). */
  ChatCompletion chatCompletion() {
    if (chatCompletion == null) {
      throw new IllegalStateException("Final chat completion chunk(s) not yet received.");
    }
    return chatCompletion;
  }

  ChatCompletionChunk accumulate(ChatCompletionChunk chunk) {
    final ChatCompletion.Builder builder = ensureChatCompletionBuilder();

    // Set eagerly, unlike the vendor accumulator -- see the class javadoc.
    chunk.usage().ifPresent(builder::usage);

    if (chunk.choices().isEmpty()) {
      if (chunk.usage().isPresent() && chatCompletion != null) {
        chatCompletion = builder.build();
      }
      return chunk;
    }

    builder
        .id(chunk.id())
        .created(chunk.created())
        .model(chunk.model())
        .systemFingerprint(chunk._systemFingerprint())
        .putAllAdditionalProperties(chunk._additionalProperties());
    chunk
        .serviceTier()
        .ifPresent(tier -> builder.serviceTier(ChatCompletion.ServiceTier.of(tier.asString())));

    for (final ChatCompletionChunk.Choice choice : chunk.choices()) {
      final long index = choice.index();
      final ChatCompletion.Choice.Builder choiceBuilder =
          choiceBuilders.computeIfAbsent(index, i -> ChatCompletion.Choice.builder().index(i));

      accumulateMessage(index, choice.delta());
      choiceBuilder.additionalProperties(choice._additionalProperties());

      if (choice.finishReason().isPresent()) {
        choiceBuilder.finishReason(
            ChatCompletion.Choice.FinishReason.of(choice.finishReason().get().asString()));
        isFinished.put(index, true);
        if (choiceBuilders.keySet().stream()
            .allMatch(i -> Boolean.TRUE.equals(isFinished.get(i)))) {
          chatCompletion = ensureChatCompletionBuilder().choices(buildChoices()).build();
        }
      } else {
        choiceBuilder.finishReason(JsonNull.of());
      }
    }

    return chunk;
  }

  private ChatCompletion.Builder ensureChatCompletionBuilder() {
    if (chatCompletionBuilder == null) {
      chatCompletionBuilder = ChatCompletion.builder();
    }
    return chatCompletionBuilder;
  }

  private void accumulateMessage(long index, ChatCompletionChunk.Choice.Delta delta) {
    final ChatCompletionMessage.Builder messageBuilder =
        messageBuilders.computeIfAbsent(index, i -> ChatCompletionMessage.builder());
    messageContents
        .computeIfAbsent(index, i -> new ContentAccumulator())
        .accumulate(delta._content());
    delta.refusal().ifPresent(r -> messageRefusals.merge(index, r, String::concat));
    // The role defaults to "assistant"; no need to set it explicitly if absent.
    delta.role().ifPresent(role -> messageBuilder.role(JsonValue.from(role.asString())));

    delta
        .toolCalls()
        .ifPresent(
            toolCalls ->
                toolCalls.forEach(deltaToolCall -> accumulateToolCall(index, deltaToolCall)));

    messageBuilder.putAllAdditionalProperties(delta._additionalProperties());
  }

  private void accumulateToolCall(
      long index, ChatCompletionChunk.Choice.Delta.ToolCall deltaToolCall) {
    final long toolCallIndex = deltaToolCall.index();

    toolCallBuilders
        .computeIfAbsent(index, i -> new TreeMap<>())
        .computeIfAbsent(
            toolCallIndex,
            i ->
                ChatCompletionMessageFunctionToolCall.builder()
                    .id(deltaToolCall._id())
                    .additionalProperties(deltaToolCall._additionalProperties()));

    final var function =
        deltaToolCall
            .function()
            .orElseThrow(() -> new OpenAIInvalidDataException("Tool call chunk missing function."));

    toolCallFunctionBuilders
        .computeIfAbsent(index, i -> new TreeMap<>())
        .computeIfAbsent(
            toolCallIndex,
            i ->
                ChatCompletionMessageFunctionToolCall.Function.builder()
                    .name(function._name())
                    .additionalProperties(deltaToolCall._additionalProperties()));

    toolCallFunctionArgs
        .computeIfAbsent(index, i -> new TreeMap<>())
        .merge(toolCallIndex, function.arguments().orElse(""), String::concat);
  }

  private List<ChatCompletion.Choice> buildChoices() {
    final List<ChatCompletion.Choice> choices = new ArrayList<>();
    for (final var entry : choiceBuilders.entrySet()) {
      choices.add(
          entry
              .getValue()
              .message(buildMessage(entry.getKey()))
              // The SDK builder requires this field to be set even though this connector never
              // requests log probabilities.
              .logprobs((ChatCompletion.Choice.Logprobs) null)
              .build());
    }
    return choices;
  }

  private ChatCompletionMessage buildMessage(long index) {
    final ChatCompletionMessage.Builder builder = messageBuilders.get(index);
    if (builder == null) {
      throw new OpenAIInvalidDataException("Missing message for index " + index + ".");
    }
    builder
        .content(messageContents.getOrDefault(index, new ContentAccumulator()).build())
        .refusal(messageRefusals.get(index))
        .toolCalls(buildToolCalls(index));
    return builder.build();
  }

  private List<ChatCompletionMessageToolCall> buildToolCalls(long index) {
    final Map<Long, ChatCompletionMessageFunctionToolCall.Builder> builders =
        toolCallBuilders.get(index);
    if (builders == null) {
      return List.of();
    }
    final List<ChatCompletionMessageToolCall> toolCalls = new ArrayList<>();
    for (final var entry : builders.entrySet()) {
      final long toolCallIndex = entry.getKey();
      toolCalls.add(
          ChatCompletionMessageToolCall.ofFunction(
              entry.getValue().function(buildFunction(index, toolCallIndex)).build()));
    }
    return toolCalls;
  }

  private ChatCompletionMessageFunctionToolCall.Function buildFunction(
      long index, long toolCallIndex) {
    final Map<Long, ChatCompletionMessageFunctionToolCall.Function.Builder> functionBuilders =
        toolCallFunctionBuilders.get(index);
    final ChatCompletionMessageFunctionToolCall.Function.Builder functionBuilder =
        functionBuilders == null ? null : functionBuilders.get(toolCallIndex);
    if (functionBuilder == null) {
      throw new OpenAIInvalidDataException(
          "Missing function builder for index " + index + "." + toolCallIndex + ".");
    }
    final Map<Long, String> functionArgs = toolCallFunctionArgs.get(index);
    final String arguments = functionArgs == null ? null : functionArgs.get(toolCallIndex);
    if (arguments == null) {
      throw new OpenAIInvalidDataException(
          "Missing function arguments for index " + index + "." + toolCallIndex + ".");
    }
    return functionBuilder.arguments(arguments).build();
  }

  /**
   * Accumulates one message's {@code content} deltas, self-detecting per delta whether the raw
   * value is a plain string or a chunk array. Once any array delta has been seen, accumulation
   * switches to chunk mode for the rest of the message, even if a later delta reverts to a plain
   * string (observed in real traffic once the thinking phase has ended) -- that string is appended
   * to the currently open {@code text} chunk. Only a chunk-type change closes the currently open
   * chunk; a {@code thinking} chunk's own {@code closed} field is not treated as a signal (real
   * traffic sets it true on roughly every other delta throughout the reasoning phase -- per
   * Mistral's schema it exists only for an unrelated prefixing feature).
   */
  private static final class ContentAccumulator {

    private final StringBuilder plainText = new StringBuilder();
    private final List<Map<String, Object>> closedChunks = new ArrayList<>();
    private @Nullable String openChunkType;
    private final Map<String, Object> openChunkExtra = new LinkedHashMap<>();
    private final StringBuilder openChunkText = new StringBuilder();
    private final List<Object> openChunkThinkingItems = new ArrayList<>();
    private boolean openChunkThinkingReconstructible = true;
    private boolean chunked = false;

    void accumulate(JsonField<String> rawContent) {
      final Optional<List<JsonValue>> chunks = rawContent.asArray();
      if (chunks.isPresent()) {
        if (!chunked && !plainText.isEmpty()) {
          // Preserve any plain-string deltas seen before the first array delta as a leading text
          // chunk instead of silently dropping them.
          closedChunks.add(Map.of("type", "text", "text", plainText.toString()));
          plainText.setLength(0);
        }
        chunked = true;
        for (final JsonValue chunkValue : chunks.get()) {
          accumulateChunk(chunkValue);
        }
        return;
      }
      rawContent
          .asString()
          .ifPresent(
              text -> {
                if (chunked) {
                  if (openChunkType == null) {
                    // Defensive fallback: not observed in real traffic once chunk mode has
                    // started, since only a type change closes a chunk and always opens the
                    // next one immediately.
                    openChunkType = "text";
                  }
                  openChunkText.append(text);
                } else {
                  plainText.append(text);
                }
              });
    }

    private void accumulateChunk(JsonValue chunkValue) {
      final Map<String, Object> raw =
          chunkValue.convert(new TypeReference<Map<String, Object>>() {});
      if (raw == null) {
        return;
      }
      final Object type = raw.get("type");
      if (!(type instanceof String typeName)) {
        return;
      }
      // Only thinking/text are true multi-delta streamed types; every other type (reference,
      // image_url, document_url, file, input_audio) arrives whole in a single delta, so two
      // back-to-back chunks of the same "other" type are two distinct chunks, not one
      // continuation.
      final boolean continuesOpenChunk =
          typeName.equals(openChunkType)
              && ("thinking".equals(typeName) || "text".equals(typeName));
      if (!continuesOpenChunk) {
        closeOpenChunk();
        openChunkType = typeName;
      }
      raw.forEach(
          (key, value) -> {
            if (!"type".equals(key) && !"thinking".equals(key) && !"text".equals(key)) {
              openChunkExtra.put(key, value);
            }
          });
      if ("thinking".equals(typeName)) {
        final Object thinking = raw.get("thinking");
        if (thinking instanceof List<?> items && !items.isEmpty()) {
          // Only a single-item {"type":"text","text":...} delta is byte-identically
          // reconstructible from joined text; anything else must be preserved verbatim.
          if (!isSingleReconstructibleTextItem(items)) {
            openChunkThinkingReconstructible = false;
          }
          for (final Object item : items) {
            openChunkThinkingItems.add(item);
            if (item instanceof Map<?, ?> map && map.get("text") instanceof String fragment) {
              openChunkText.append(fragment);
            }
          }
        }
      } else if ("text".equals(typeName) && raw.get("text") instanceof String fragment) {
        openChunkText.append(fragment);
      }
    }

    private static boolean isSingleReconstructibleTextItem(List<?> items) {
      return items.size() == 1
          && items.get(0) instanceof Map<?, ?> item
          && item.size() == 2
          && "text".equals(item.get("type"))
          && item.get("text") instanceof String;
    }

    private void closeOpenChunk() {
      if (openChunkType == null) {
        return;
      }
      final Map<String, Object> chunk = new LinkedHashMap<>(openChunkExtra);
      chunk.put("type", openChunkType);
      if ("thinking".equals(openChunkType)) {
        chunk.put(
            "thinking",
            openChunkThinkingReconstructible
                ? List.of(Map.of("type", "text", "text", openChunkText.toString()))
                : List.copyOf(openChunkThinkingItems));
        // Only default to closed if no delta on this chunk carried its own value.
        chunk.putIfAbsent("closed", true);
      } else if ("text".equals(openChunkType)) {
        chunk.put("text", openChunkText.toString());
      }
      closedChunks.add(chunk);
      openChunkText.setLength(0);
      openChunkExtra.clear();
      openChunkThinkingItems.clear();
      openChunkThinkingReconstructible = true;
    }

    JsonField<String> build() {
      if (!chunked) {
        return JsonField.ofNullable(plainText.isEmpty() ? null : plainText.toString());
      }
      closeOpenChunk();
      openChunkType = null;
      return JsonValue.from(closedChunks);
    }
  }
}
