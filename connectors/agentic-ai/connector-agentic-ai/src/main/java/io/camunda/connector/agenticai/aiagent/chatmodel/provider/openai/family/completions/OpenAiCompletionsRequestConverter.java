/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.ReasoningEffort;
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
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.OpenAiRequestCustomizations;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.MessageUtil;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi.CompletionsParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiEffort;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Maps a windowed {@link ConversationSnapshot} plus the resolved OpenAI Chat Completions model
 * configuration to an OpenAI SDK {@link ChatCompletionCreateParams} request, translating the domain
 * {@link Message} / {@link ToolCall} / {@link ToolCallResultContent} model into the wire shape via
 * the {@link OpenAiContentConverter} built for content parts.
 *
 * <p>Deliberate subset of the sibling {@link
 * io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesRequestConverter}:
 * reasoning is limited to the input-only {@code reasoning_effort} dial (no encrypted-content replay
 * or stateless-store toggle, unlike the Responses sibling's {@code reasoning} object -- Completions
 * has no reasoning-item replay mechanism at all) and tool results are always flattened to plain
 * text rather than replayed as multimodal item lists.
 */
public class OpenAiCompletionsRequestConverter {

  private final OpenAiContentConverter contentConverter;
  private final ObjectMapper objectMapper;

  public OpenAiCompletionsRequestConverter(
      OpenAiContentConverter contentConverter, ObjectMapper objectMapper) {
    this.contentConverter = contentConverter;
    this.objectMapper = objectMapper;
  }

  public ChatCompletionCreateParams toRequest(
      OpenAiChatModelConfiguration configuration,
      @Nullable ResponseConfiguration response,
      ConversationSnapshot snapshot) {
    final OpenAiConnection connection = configuration.openai();
    final String modelId = connection.model().model();
    final CompletionsParameters params = completionsParameters(connection);

    final var builder = ChatCompletionCreateParams.builder().model(modelId);

    // Chat Completions streaming omits `usage` unless `stream_options.include_usage=true`; this
    // converter's calls are always streamed, so request usage so token metrics
    // (input/output/cached) are populated. Set unconditionally, on every request.
    builder.streamOptions(ChatCompletionStreamOptions.builder().includeUsage(true).build());

    // Zero Data Retention-compatible: this connector persists conversation memory itself, so it
    // never relies on OpenAI-side response storage.
    builder.store(false);

    applyModelParameters(builder, params);
    applyReasoning(builder, params);
    applyMessages(builder, snapshot.messages());
    applyTools(builder, snapshot.toolDefinitions());
    applyStructuredOutput(builder, response);
    applyRequestCustomizations(builder, connection);

    return builder.build();
  }

  /**
   * This converter only handles the {@code completions} API family; routing a {@code responses}
   * family configuration here is a caller/family-dispatch bug, not a user-facing configuration
   * error, hence the unchecked exception rather than a {@code ConnectorException}. {@code
   * completions} itself is optional -- every one of its own fields is optional, so a modeler
   * leaving all of them unset means the object is absent entirely, not present-with-nulls.
   */
  private @Nullable CompletionsParameters completionsParameters(OpenAiConnection connection) {
    return switch (connection.api()) {
      case OpenAiCompletionsApi completionsApi -> completionsApi.completions();
      default ->
          throw new IllegalArgumentException(
              "OpenAiCompletionsRequestConverter requires the 'completions' API family, but was configured with '%s'"
                  .formatted(connection.api().type()));
    };
  }

  private void applyModelParameters(
      ChatCompletionCreateParams.Builder builder, @Nullable CompletionsParameters params) {
    if (params == null) {
      return;
    }
    if (params.maxCompletionTokens() != null) {
      builder.maxCompletionTokens(params.maxCompletionTokens().longValue());
    }
    if (params.temperature() != null) {
      builder.temperature(params.temperature());
    }
    if (params.topP() != null) {
      builder.topP(params.topP());
    }
  }

  /**
   * Maps the {@code effort} dial onto the SDK's {@code reasoning_effort} param. Unlike the
   * Responses sibling's {@code applyReasoning}, there is no encrypted-content include: Completions
   * has no reasoning-item replay mechanism at all.
   */
  private void applyReasoning(
      ChatCompletionCreateParams.Builder builder, @Nullable CompletionsParameters params) {
    final OpenAiEffort effort = params == null ? null : params.effort();
    if (effort == null) {
      return;
    }
    builder.reasoningEffort(ReasoningEffort.of(effort.name().toLowerCase(Locale.ROOT)));
  }

  private void applyMessages(ChatCompletionCreateParams.Builder builder, List<Message> messages) {
    final List<ChatCompletionMessageParam> items = new ArrayList<>();
    for (final Message message : messages) {
      switch (message) {
        case SystemMessage system ->
            items.add(ChatCompletionMessageParam.ofSystem(systemMessage(system)));
        case UserMessage user -> items.add(ChatCompletionMessageParam.ofUser(userMessage(user)));
        case AssistantMessage assistant ->
            items.add(ChatCompletionMessageParam.ofAssistant(assistantMessage(assistant)));
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
        .content(MessageUtil.systemPromptText(system))
        .build();
  }

  private ChatCompletionUserMessageParam userMessage(UserMessage user) {
    return ChatCompletionUserMessageParam.builder()
        .content(
            ChatCompletionUserMessageParam.Content.ofArrayOfContentParts(
                contentConverter.toCompletionsContentParts(user.content())))
        .build();
  }

  /**
   * {@link ReasoningContent} and {@link ProviderContent} have no wire representation on the
   * Completions family (reasoning is input-only via {@code reasoning_effort}, with no
   * reasoning-item or server-tool replay mechanism at all, see the class Javadoc) and are silently
   * dropped; everything else (text/document/object) is flattened to a single text blob.
   */
  private ChatCompletionAssistantMessageParam assistantMessage(AssistantMessage assistant) {
    final var builder = ChatCompletionAssistantMessageParam.builder();

    final List<Content> plainContent =
        assistant.content().stream()
            .filter(c -> !(c instanceof ReasoningContent) && !(c instanceof ProviderContent))
            .toList();
    if (!plainContent.isEmpty()) {
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
                          .arguments(writeAsJson(toolCall.arguments()))
                          .build())
                  .build()));
    }

    return builder.build();
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
   * concatenated verbatim, {@link ObjectContent} is unwrapped to its raw {@code content()} before
   * being serialized to JSON (otherwise the polymorphic {@link Content} envelope itself, including
   * its {@code type} discriminator, would leak onto the wire), anything else (documents) falls back
   * to serializing the whole content value. Tool results are always text-only on the Completions
   * family, so this is the sole tool-result serialization path (unlike the Responses sibling, there
   * is no multimodal item-list shape).
   */
  private String toTextOutput(List<Content> content) {
    return content.stream()
        .map(
            c -> {
              if (c instanceof TextContent text) {
                return text.text();
              } else if (c instanceof ObjectContent obj) {
                return writeAsJson(obj.content());
              } else {
                return writeAsJson(c);
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
    builder.responseFormat(
        ResponseFormatJsonSchema.builder()
            .jsonSchema(
                ResponseFormatJsonSchema.JsonSchema.builder()
                    .name(json.schemaName())
                    .schema(
                        objectMapper.convertValue(
                            json.schema(), ResponseFormatJsonSchema.JsonSchema.Schema.class))
                    .strict(true)
                    .build())
            .build());
  }

  /**
   * Merges the backend's headers, query parameters, and body properties onto the request, shared
   * with the Responses sibling via {@link OpenAiRequestCustomizations}.
   */
  private void applyRequestCustomizations(
      ChatCompletionCreateParams.Builder builder, OpenAiConnection connection) {
    final var customizations = OpenAiRequestCustomizations.from(connection);
    customizations.headers().forEach(builder::putAdditionalHeader);
    customizations.queryParameters().forEach(builder::putAdditionalQueryParam);
    customizations
        .bodyProperties()
        .forEach((k, v) -> builder.putAdditionalBodyProperty(k, JsonValue.from(v)));
  }

  private String writeAsJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize content to JSON", e);
    }
  }
}
