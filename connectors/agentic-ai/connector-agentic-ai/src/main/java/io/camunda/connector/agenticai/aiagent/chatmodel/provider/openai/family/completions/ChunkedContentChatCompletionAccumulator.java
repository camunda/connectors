/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Accumulates a streamed OpenAI Chat Completions API response into a single {@link ChatCompletion}
 * the same way the vendor SDK's {@code ChatCompletionAccumulator} does, except for {@code content}:
 * that accumulator reads {@code delta.content()} through the SDK's typed (string-only) accessor,
 * which throws as soon as any delta carries a chunked array instead (see {@link
 * OpenAiCompletionsResponseConverter} for why an OpenAI-compatible endpoint like Mistral does
 * this). This accumulator reads the raw {@code delta._content()} value instead and self-detects,
 * per delta, whether it is a plain string or a chunk array -- exactly the same shape-detection the
 * response converter applies to the finished message, and independent of any caller-supplied flag
 * -- so it drives both reasoning and non-reasoning models correctly through the one class.
 *
 * <p>A stream observed for a Magistral-class model runs through three phases: a plain string
 * (initial content before any chunk arrives, if any), then one or more chunked-array deltas
 * accumulating a {@code thinking} chunk's text one fragment at a time, then further chunked-array
 * deltas accumulating a {@code text} chunk once thinking is done -- observed in real traffic to
 * arrive in the very same delta as the thinking chunk's closing fragment. Once the array shape has
 * been observed at all, every open chunk is tracked by type (switching type finalizes the
 * previously open chunk) and the assembled message's {@code content} is emitted as a chunk array,
 * even if a later delta reverts to plain-string chunks (observed in real traffic once the thinking
 * phase has ended): those are simply appended to the currently open {@code text} chunk. A message
 * that never sees an array delta at all is emitted as a plain string, byte-identical to what the
 * vendor accumulator would have produced.
 *
 * <p>Everything other than {@code content} (id/created/model, tool calls, refusal, role, usage,
 * finish reason) is accumulated the same way the vendor accumulator does it, field for field and
 * method for method, deliberately kept structurally close to {@code
 * com.openai.helpers.ChatCompletionAccumulator} (find it in the {@code openai-java-core} sources
 * jar) so an SDK upgrade can be diffed against it directly; log probabilities are the one thing
 * dropped rather than ported, since this connector never requests them.
 *
 * <p>One deliberate behavioral divergence from the vendor accumulator: {@code usage} is applied to
 * the builder unconditionally, before that chunk's {@code choices} are processed, rather than
 * treating any usage-carrying chunk as choices-less. OpenAI always sends {@code usage} on its own
 * separate trailing chunk with an empty {@code choices} array, which is what the vendor accumulator
 * assumes; Mistral instead sends {@code usage} on the very same chunk that also carries the closing
 * delta and {@code finishReason}. Mirroring the vendor's ordering exactly would silently drop that
 * chunk's choices, leaving the final {@link ChatCompletion.Builder} without {@code choices} set and
 * failing with "choices is required, but was not set" once the stream completed.
 *
 * <p>Subclassing the vendor accumulator and overriding only content handling isn't possible: it's a
 * Kotlin class with no {@code open} modifier (final by default, cannot be extended), a private
 * constructor reachable only through its own {@code create()} factory, and every field the {@code
 * content}-accumulation logic would need to share with the rest of the class ({@code
 * messageContents}, {@code messageBuilders}, etc.) is {@code private}. There is no extension point
 * to hook a custom content strategy into, so this class re-implements the whole algorithm rather
 * than overriding a part of it.
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

  /**
   * Gets the final accumulated chat completion. Only valid after the last chunk (the one carrying
   * the finish reason, plus an optional trailing usage-only chunk) has been passed to {@link
   * #accumulate}, mirroring the vendor accumulator's {@code chatCompletion()}.
   */
  ChatCompletion chatCompletion() {
    if (chatCompletion == null) {
      throw new IllegalStateException("Final chat completion chunk(s) not yet received.");
    }
    return chatCompletion;
  }

  ChatCompletionChunk accumulate(ChatCompletionChunk chunk) {
    final ChatCompletion.Builder builder = ensureChatCompletionBuilder();

    // Set eagerly, unlike the vendor accumulator: OpenAI sends usage on its own trailing
    // choices-less chunk, but Mistral sends it on the very same chunk that also carries the
    // final finishReason -- setting it unconditionally here, before that chunk's choices are
    // processed below, means both wire shapes end up with usage present on the built
    // ChatCompletion. Deferring this behind the emptiness check below would silently drop the
    // choices that arrive in that same combined chunk.
    chunk.usage().ifPresent(builder::usage);

    if (chunk.choices().isEmpty()) {
      // A usage-only trailing chunk (OpenAI's shape). The chat completion, if already finished
      // by an earlier chunk, needs rebuilding to pick up the usage just set above; if not yet
      // finished, there is nothing more to do until the finishing chunk arrives.
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
              // Log probabilities are never requested by this connector; set explicitly to null
              // (rather than left unset) since the SDK's builder requires the field to be set.
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
   * value is a plain string or a chunk array (see the class Javadoc). Once any array delta has been
   * seen, accumulation switches to chunk mode for the rest of the message, even if a later delta
   * reverts to a plain string -- that string is simply appended to the currently open {@code text}
   * chunk (observed in real traffic once the thinking phase has ended). Any plain-string deltas
   * seen before that switch are flushed as a leading {@code text} chunk rather than dropped.
   */
  private static final class ContentAccumulator {

    private final StringBuilder plainText = new StringBuilder();
    private final List<Map<String, Object>> closedChunks = new ArrayList<>();
    private @Nullable String openChunkType;
    private final StringBuilder openChunkText = new StringBuilder();
    private boolean chunked = false;

    void accumulate(JsonField<String> rawContent) {
      final Optional<List<JsonValue>> chunks = rawContent.asArray();
      if (chunks.isPresent()) {
        if (!chunked && !plainText.isEmpty()) {
          // Preserve any plain-string deltas seen before the first array delta as a leading text
          // chunk -- build()'s chunked branch never reads plainText, so without this the prefix
          // would otherwise be silently dropped.
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
                  // A plain-string delta arriving after chunk mode has started continues the
                  // currently open chunk (observed once the thinking phase has ended).
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
      if (!typeName.equals(openChunkType)) {
        closeOpenChunk();
        openChunkType = typeName;
      }
      if ("thinking".equals(typeName)) {
        final Object thinking = raw.get("thinking");
        if (thinking instanceof List<?> items) {
          for (final Object item : items) {
            if (item instanceof Map<?, ?> map && map.get("text") instanceof String fragment) {
              openChunkText.append(fragment);
            }
          }
        }
      } else if ("text".equals(typeName) && raw.get("text") instanceof String fragment) {
        openChunkText.append(fragment);
      }
    }

    private void closeOpenChunk() {
      if (openChunkType == null) {
        return;
      }
      if ("thinking".equals(openChunkType)) {
        closedChunks.add(
            Map.of(
                "type",
                "thinking",
                "thinking",
                List.of(Map.of("type", "text", "text", openChunkText.toString())),
                "closed",
                true));
      } else {
        closedChunks.add(Map.of("type", openChunkType, "text", openChunkText.toString()));
      }
      openChunkText.setLength(0);
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
