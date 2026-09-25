/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.ReasoningEffort;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.ResponseFormatJsonSchema;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionStreamOptions;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiStrictJsonSchemas;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.MessageUtil;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiRequestCustomizations;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Maps a windowed {@link ConversationSnapshot} plus a provider-neutral {@link
 * CompletionsRequestSpec} to an OpenAI SDK {@link ChatCompletionCreateParams} request, translating
 * the domain {@link Message} / {@link ToolCall} / {@link ToolCallResultContent} model into the wire
 * shape via {@link OpenAiContentConverter} for generic JSON serialization and the injected {@link
 * OpenAiCompletionsContentChunkStrategy} for user-message content parts. This converter has no
 * dependency on any specific {@code ProviderConfiguration} subtype -- the OpenAI provider and any
 * other caller of the Chat Completions wire format (e.g. the Mistral provider) each build their own
 * {@link CompletionsRequestSpec} plus their own {@link OpenAiCompletionsContentChunkStrategy}.
 *
 * <p>Reasoning is mapped via the input-only {@code reasoning_effort} dial plus, where applicable,
 * replay of a prior turn's reasoning content: a {@link ReasoningContent} is replayed
 * byte-faithfully as part of a chunked {@code content} array (see {@link #assistantMessage}) only
 * when it was produced by this same provider instance (its {@code provider} tag matches this
 * converter's own {@code providerId}) <em>and</em> its payload is shaped like the chunked {@code
 * thinking} chunk {@link OpenAiCompletionsResponseConverter} emits -- both are required, since a
 * payload shape alone doesn't uniquely identify the producing family: Anthropic's raw
 * thinking-block payload also keeps a {@code type: "thinking"} field after its own text extraction.
 * This is what Mistral's reasoning-capable models require to keep reasoning quality across turns.
 * Any other {@link ReasoningContent} (e.g. carried over from a different provider after a
 * mid-conversation provider switch) and every {@link ProviderContent} have no representation on
 * this family and are dropped. Tool results are always flattened to plain text.
 */
public class OpenAiCompletionsRequestConverter {

  private final OpenAiContentConverter contentConverter;
  private final OpenAiCompletionsContentChunkStrategy contentChunkStrategy;
  private final ObjectMapper objectMapper;
  private final String providerId;

  public OpenAiCompletionsRequestConverter(
      OpenAiContentConverter contentConverter,
      OpenAiCompletionsContentChunkStrategy contentChunkStrategy,
      ObjectMapper objectMapper,
      String providerId) {
    this.contentConverter = contentConverter;
    this.contentChunkStrategy = contentChunkStrategy;
    this.objectMapper = objectMapper;
    this.providerId = providerId;
  }

  public ChatCompletionCreateParams toRequest(
      CompletionsRequestSpec spec,
      @Nullable ResponseConfiguration response,
      ConversationSnapshot snapshot) {
    final var builder = ChatCompletionCreateParams.builder().model(spec.model());

    // Chat Completions streaming omits `usage` unless `stream_options.include_usage=true`; this
    // converter's calls are always streamed, so request usage so token metrics
    // (input/output/cached) are populated. Set unconditionally, on every request.
    builder.streamOptions(ChatCompletionStreamOptions.builder().includeUsage(true).build());

    applyModelParameters(builder, spec);
    applyReasoning(builder, spec);
    applyMessages(builder, snapshot.messages());
    applyTools(builder, snapshot.toolDefinitions());
    applyStructuredOutput(builder, response);
    applyRequestCustomizations(builder, spec.customizations());

    return builder.build();
  }

  private void applyModelParameters(
      ChatCompletionCreateParams.Builder builder, CompletionsRequestSpec spec) {
    // maxCompletionTokens (OpenAI's current wire name) and maxTokens (the older name several
    // OpenAI-compatible APIs still require instead) are mutually exclusive: a caller's spec sets
    // at most one, matching its own provider's wire parameter.
    if (spec.maxCompletionTokens() != null) {
      builder.maxCompletionTokens(spec.maxCompletionTokens());
    }
    if (spec.maxTokens() != null) {
      builder.maxTokens(spec.maxTokens());
    }
    if (spec.temperature() != null) {
      builder.temperature(spec.temperature());
    }
    if (spec.topP() != null) {
      builder.topP(spec.topP());
    }
  }

  /** Maps the spec's {@code reasoningEffort} dial onto the SDK's {@code reasoning_effort} param. */
  private void applyReasoning(
      ChatCompletionCreateParams.Builder builder, CompletionsRequestSpec spec) {
    if (spec.reasoningEffort() != null) {
      builder.reasoningEffort(ReasoningEffort.of(spec.reasoningEffort()));
    }
  }

  private void applyMessages(ChatCompletionCreateParams.Builder builder, List<Message> messages) {
    final List<ChatCompletionMessageParam> items = new ArrayList<>();
    for (final Message message : messages) {
      switch (message) {
        case SystemMessage system ->
            items.add(ChatCompletionMessageParam.ofSystem(systemMessage(system)));
        case UserMessage user -> items.add(ChatCompletionMessageParam.ofUser(userMessage(user)));
        case AssistantMessage assistant -> {
          final ChatCompletionAssistantMessageParam assistantMessage = assistantMessage(assistant);
          if (assistantMessage != null) {
            items.add(ChatCompletionMessageParam.ofAssistant(assistantMessage));
          }
        }
        case ToolCallResultMessage toolResults -> items.addAll(toolResultMessages(toolResults));
        default ->
            throw new IllegalArgumentException(
                "Unsupported message type: " + message.getClass().getSimpleName());
      }
    }
    builder.messages(items);
  }

  private ChatCompletionSystemMessageParam systemMessage(SystemMessage system) {
    return ChatCompletionSystemMessageParam.builder()
        .content(MessageUtil.contentText(system))
        .build();
  }

  private ChatCompletionUserMessageParam userMessage(UserMessage user) {
    return ChatCompletionUserMessageParam.builder()
        .content(
            ChatCompletionUserMessageParam.Content.ofArrayOfContentParts(
                contentChunkStrategy.toContentParts(user.content())))
        .build();
  }

  /**
   * Flattens plain content (text/document/object) to a single text blob, unless the message also
   * carries a replayable chunked {@link ReasoningContent} (see the class Javadoc), in which case
   * {@code content} is rebuilt as a chunked array instead via {@link #toChunkedContent}. Any other
   * {@link ReasoningContent} and every {@link ProviderContent} have no wire representation on this
   * family and are dropped.
   *
   * <p>Returns {@code null} when nothing representable remains and there are no tool calls either:
   * the Completions API requires an assistant message to carry {@code content} unless it carries a
   * tool/function call, so such a message is omitted entirely rather than sent empty.
   */
  private @Nullable ChatCompletionAssistantMessageParam assistantMessage(
      AssistantMessage assistant) {
    final List<Content> plainContent =
        assistant.content().stream()
            .filter(c -> !(c instanceof ReasoningContent) && !(c instanceof ProviderContent))
            .toList();
    final boolean hasChunkedReasoning =
        assistant.content().stream()
            .anyMatch(c -> c instanceof ReasoningContent rc && isChunkedReasoningContent(rc));
    if (plainContent.isEmpty() && !hasChunkedReasoning && assistant.toolCalls().isEmpty()) {
      return null;
    }

    final var builder = ChatCompletionAssistantMessageParam.builder();
    if (hasChunkedReasoning) {
      builder.content(JsonValue.from(toChunkedContent(assistant.content())));
    } else if (!plainContent.isEmpty()) {
      builder.content(toTextOutput(plainContent));
    }

    for (final ToolCall toolCall : assistant.toolCalls()) {
      builder.addToolCall(
          ChatCompletionMessageToolCall.ofFunction(
              ChatCompletionMessageFunctionToolCall.builder()
                  .id(toolCall.id())
                  .function(
                      ChatCompletionMessageFunctionToolCall.Function.builder()
                          .name(toolCall.name())
                          .arguments(contentConverter.writeAsJson(toolCall.arguments()))
                          .build())
                  .build()));
    }

    return builder.build();
  }

  /**
   * A {@link ReasoningContent} is only replayable as a chunked {@code thinking} chunk if it was
   * produced by this same provider ({@code provider} tag matches this converter's own {@code
   * providerId}) <em>and</em> its payload is shaped the way {@link
   * OpenAiCompletionsResponseConverter} produces it. Both checks are required: payload shape alone
   * isn't a unique signal -- Anthropic's raw thinking-block payload also carries a {@code type:
   * "thinking"} field after its own text extraction, so a provider-tag-only or shape-only check
   * would misclassify reasoning content replayed after a mid-conversation provider switch.
   */
  private boolean isChunkedReasoningContent(ReasoningContent reasoning) {
    return providerId.equals(reasoning.provider())
        && reasoning.payload() instanceof Map<?, ?> payload
        && "thinking".equals(payload.get("type"));
  }

  /**
   * Rebuilds the assistant message's {@code content} as a chunked array, preserving the original
   * order of its {@link Content} entries: a replayable {@link ReasoningContent} (see {@link
   * #isChunkedReasoningContent}) becomes a {@code thinking} chunk via {@link
   * #toChunkedThinkingChunk}, and every other plain content entry becomes a {@code text} chunk.
   * Only called once {@link #assistantMessage} has established at least one replayable {@link
   * ReasoningContent} is present, so the result is never empty.
   */
  private List<Map<String, Object>> toChunkedContent(List<Content> content) {
    final List<Map<String, Object>> chunks = new ArrayList<>();
    for (final Content c : content) {
      if (c instanceof ReasoningContent reasoning && isChunkedReasoningContent(reasoning)) {
        chunks.add(toChunkedThinkingChunk(reasoning));
      } else if (!(c instanceof ReasoningContent) && !(c instanceof ProviderContent)) {
        chunks.add(Map.of("type", "text", "text", toTextOutput(List.of(c))));
      }
    }
    return chunks;
  }

  /**
   * If {@link OpenAiCompletionsResponseConverter#toReasoningContent} left {@code thinking} in the
   * payload (not byte-identically reconstructible from {@link ReasoningContent#text()} alone), it
   * is replayed verbatim instead. Otherwise reinserts the lifted-out {@code text()} back into the
   * payload's {@code thinking} field, reconstructing the single-item chunk it was extracted from.
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> toChunkedThinkingChunk(ReasoningContent reasoning) {
    final Map<String, Object> payload = (Map<String, Object>) reasoning.payload();
    if (payload.containsKey("thinking")) {
      return new LinkedHashMap<>(payload);
    }
    final Map<String, Object> chunk = new LinkedHashMap<>(payload);
    chunk.put(
        "thinking",
        List.of(Map.of("type", "text", "text", reasoning.text() == null ? "" : reasoning.text())));
    return chunk;
  }

  private List<ChatCompletionMessageParam> toolResultMessages(ToolCallResultMessage message) {
    final List<ChatCompletionMessageParam> items = new ArrayList<>();
    for (final ToolCallResultContent result : message.results()) {
      items.add(
          ChatCompletionMessageParam.ofTool(
              ChatCompletionToolMessageParam.builder()
                  .toolCallId(result.id())
                  .content(toTextOutput(result.content()))
                  .build()));
    }
    return items;
  }

  /**
   * Flattens a message's structured content to a single text blob: {@link TextContent} is
   * concatenated verbatim, {@link ObjectContent} is unwrapped to its raw {@code content()}, and
   * {@link DocumentContent} is unwrapped to its raw {@code document()} reference -- in both cases
   * so the polymorphic {@link Content} envelope itself, including its {@code type} discriminator,
   * never leaks onto the wire. A document's bytes are already delivered to the model elsewhere, so
   * this reference is never accompanied by a native embed, matching the tool-result document
   * handling on the Responses and Anthropic siblings. Anything else falls back to serializing the
   * whole content value. Tool results are always text-only on this family.
   */
  private String toTextOutput(List<Content> content) {
    return content.stream()
        .map(
            c -> {
              if (c instanceof TextContent text) {
                return text.text();
              } else if (c instanceof ObjectContent obj) {
                return contentConverter.writeAsJson(obj.content());
              } else if (c instanceof DocumentContent doc) {
                return contentConverter.writeAsJson(doc.document());
              } else {
                return contentConverter.writeAsJson(c);
              }
            })
        .collect(Collectors.joining("\n"));
  }

  private void applyTools(
      ChatCompletionCreateParams.Builder builder, List<ToolDefinition> toolDefinitions) {
    for (final ToolDefinition definition : toolDefinitions) {
      final var functionBuilder =
          FunctionDefinition.builder()
              .name(definition.name())
              .parameters(
                  objectMapper.convertValue(definition.inputSchema(), FunctionParameters.class));
      if (definition.description() != null) {
        functionBuilder.description(definition.description());
      }
      builder.addTool(
          ChatCompletionTool.ofFunction(
              ChatCompletionFunctionTool.builder().function(functionBuilder.build()).build()));
    }
  }

  private void applyStructuredOutput(
      ChatCompletionCreateParams.Builder builder, @Nullable ResponseConfiguration response) {
    if (!(response != null && response.format() instanceof JsonResponseFormatConfiguration json)) {
      return;
    }
    if (!json.hasSchema()) {
      // JSON mode without a schema: constrain the model to valid JSON without a structure.
      builder.responseFormat(ResponseFormatJsonObject.builder().build());
      return;
    }
    builder.responseFormat(
        ResponseFormatJsonSchema.builder()
            .jsonSchema(
                ResponseFormatJsonSchema.JsonSchema.builder()
                    .name(json.schemaName())
                    .schema(
                        objectMapper.convertValue(
                            OpenAiStrictJsonSchemas.forStrictMode(json.schema(), objectMapper),
                            ResponseFormatJsonSchema.JsonSchema.Schema.class))
                    .strict(true)
                    .build())
            .build());
  }

  /**
   * Merges the spec's headers, query parameters, and body properties onto the request via the
   * shared {@link OpenAiRequestCustomizations}.
   */
  private void applyRequestCustomizations(
      ChatCompletionCreateParams.Builder builder, OpenAiRequestCustomizations customizations) {
    customizations.headers().forEach(builder::putAdditionalHeader);
    customizations.queryParameters().forEach(builder::putAdditionalQueryParam);
    customizations
        .bodyProperties()
        .forEach((k, v) -> builder.putAdditionalBodyProperty(k, JsonValue.from(v)));
  }
}
