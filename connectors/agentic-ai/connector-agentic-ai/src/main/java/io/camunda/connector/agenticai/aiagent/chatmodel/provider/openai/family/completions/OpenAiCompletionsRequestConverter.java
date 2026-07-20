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
import io.camunda.connector.agenticai.aiagent.chatmodel.V2ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiModelCapabilities;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiRequestValidator;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
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
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.OpenAiModel.OpenAiModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiEffort;
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
 * <p>Deliberate pilot subset of the sibling {@code OpenAiResponsesRequestConverter}: reasoning is
 * limited to the input-only {@code reasoning_effort} dial (no encrypted-content replay or
 * stateless-store toggle, unlike the Responses sibling's {@code reasoning} object -- Completions
 * has no reasoning-item replay mechanism at all) and there are no server tools (web search/code
 * interpreter require the Responses API and are rejected by {@link OpenAiRequestValidator}).
 */
public class OpenAiCompletionsRequestConverter {

  private final OpenAiContentConverter contentConverter;
  private final ObjectMapper objectMapper;

  public OpenAiCompletionsRequestConverter(
      OpenAiContentConverter contentConverter, ObjectMapper objectMapper) {
    this.contentConverter = contentConverter;
    this.objectMapper = objectMapper;
  }

  /**
   * Convenience overload for callers that don't track the model-matched signal (see the {@code
   * modelMatched} overload): treats the model as matched, so reasoning validation applies as normal
   * whenever an effort is actually configured.
   */
  public ChatCompletionCreateParams toChatCompletionCreateParams(
      AgentExecutionContext ctx,
      ConversationSnapshot snapshot,
      OpenAiModelCapabilities capabilities) {
    return toChatCompletionCreateParams(ctx, snapshot, capabilities, true);
  }

  public ChatCompletionCreateParams toChatCompletionCreateParams(
      AgentExecutionContext ctx,
      ConversationSnapshot snapshot,
      OpenAiModelCapabilities capabilities,
      boolean modelMatched) {
    final var cfg = (V2ChatModelApiConfiguration) ctx.configuration().chatModelApiConfiguration();
    final OpenAiChatModel model = (OpenAiChatModel) cfg.configuration();
    final OpenAiConnection connection = model.openai();
    final String modelId = connection.model().model();
    final @Nullable OpenAiModelParameters params = connection.model().parameters();

    OpenAiRequestValidator.validate(connection, capabilities.reasoning(), modelMatched, modelId);

    final var builder = ChatCompletionCreateParams.builder().model(modelId);

    // Chat Completions streaming omits `usage` unless `stream_options.include_usage=true`; this
    // converter's calls are always streamed, so request usage so token metrics
    // (input/output/cached) are populated.
    builder.streamOptions(ChatCompletionStreamOptions.builder().includeUsage(true).build());

    applyModelParameters(builder, params);
    applyReasoning(builder, params);
    applyMessages(builder, snapshot.messages());
    applyTools(builder, snapshot.toolDefinitions());
    applyStructuredOutput(builder, ctx.configuration().response());
    applyCompatibleRequestParameters(builder, connection);

    return builder.build();
  }

  private void applyModelParameters(
      ChatCompletionCreateParams.Builder builder, @Nullable OpenAiModelParameters params) {
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
   * Maps the validated {@code effort} dial onto the SDK's {@code reasoning_effort} param. Unlike
   * the Responses sibling's {@code applyReasoning}, there is no encrypted-content include and no
   * {@code store(false)} toggle: Completions has neither a reasoning-item replay mechanism nor
   * server-side state to opt out of.
   */
  private void applyReasoning(
      ChatCompletionCreateParams.Builder builder, @Nullable OpenAiModelParameters params) {
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
    final String text =
        system.content().stream()
            .filter(TextContent.class::isInstance)
            .map(c -> ((TextContent) c).text())
            .collect(Collectors.joining("\n"));
    return ChatCompletionSystemMessageParam.builder().content(text).build();
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
   * dropped; everything else (text/document/object) is flattened to a single text blob, matching
   * the plain-content replay done by the Responses sibling.
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
   * being serialized to JSON (matching {@code AnthropicContentConverter}'s and {@link
   * OpenAiContentConverter}'s handling - otherwise the polymorphic {@link Content} envelope itself,
   * including its {@code type} discriminator, would leak onto the wire), anything else (documents)
   * falls back to serializing the whole content value. Multimodal tool results already arrive
   * pre-flattened to text upstream by the capability matrix's text-only tool-result fallback for
   * the Completions family, so this is really only exercised for the JSON-object fallback case.
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
   * The OpenAI-compatible backend allows passing arbitrary additional request-body parameters (e.g.
   * vendor-specific extensions unsupported by the SDK's typed builder). {@code headers}/ {@code
   * queryParameters} on the same backend are applied at the client level (see {@code
   * OpenAiOkHttpClientFactory}), but body parameters can only be merged here, at request-building
   * time.
   */
  private void applyCompatibleRequestParameters(
      ChatCompletionCreateParams.Builder builder, OpenAiConnection connection) {
    if (connection.backend()
            instanceof OpenAiChatModel.OpenAiBackend.OpenAiCompatibleBackend compatible
        && compatible.requestParameters() != null) {
      compatible
          .requestParameters()
          .forEach((k, v) -> builder.putAdditionalBodyProperty(k, JsonValue.from(v)));
    }
  }

  private String writeAsJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize content to JSON", e);
    }
  }
}
