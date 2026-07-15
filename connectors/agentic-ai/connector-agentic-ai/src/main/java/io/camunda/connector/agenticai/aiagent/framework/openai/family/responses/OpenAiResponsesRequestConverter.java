/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai.family.responses;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextConfig;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseIncludable;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseTextConfig;
import com.openai.models.responses.Tool;
import com.openai.models.responses.WebSearchTool;
import io.camunda.connector.agenticai.aiagent.framework.api.LlmProviderChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.openai.OpenAiContentConverter;
import io.camunda.connector.agenticai.aiagent.framework.openai.OpenAiModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.openai.OpenAiRequestValidator;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
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
 * Maps a windowed {@link ConversationSnapshot} plus the resolved OpenAI Responses model
 * configuration to an OpenAI SDK {@link ResponseCreateParams} request, translating the domain
 * {@link Message} / {@link ToolCall} / {@link ToolCallResultContent} model into the wire shape via
 * the {@link OpenAiContentConverter} built for content parts.
 */
public class OpenAiResponsesRequestConverter {

  private final OpenAiContentConverter contentConverter;
  private final ObjectMapper objectMapper;

  public OpenAiResponsesRequestConverter(
      OpenAiContentConverter contentConverter, ObjectMapper objectMapper) {
    this.contentConverter = contentConverter;
    this.objectMapper = objectMapper;
  }

  /**
   * Convenience overload for callers that don't track the model-matched signal (see the {@code
   * modelMatched} overload): treats the model as matched, so reasoning validation applies as normal
   * whenever an effort is actually configured.
   */
  public ResponseCreateParams toResponseCreateParams(
      AgentExecutionContext ctx,
      ConversationSnapshot snapshot,
      OpenAiModelCapabilities capabilities) {
    return toResponseCreateParams(ctx, snapshot, capabilities, true);
  }

  public ResponseCreateParams toResponseCreateParams(
      AgentExecutionContext ctx,
      ConversationSnapshot snapshot,
      OpenAiModelCapabilities capabilities,
      boolean modelMatched) {
    final var cfg =
        (LlmProviderChatModelApiConfiguration) ctx.configuration().chatModelApiConfiguration();
    final OpenAiChatModel model = (OpenAiChatModel) cfg.configuration();
    final OpenAiConnection connection = model.openai();
    final String modelId = connection.model().model();
    final @Nullable OpenAiModelParameters params = connection.model().parameters();

    OpenAiRequestValidator.validate(connection, capabilities.reasoning(), modelMatched, modelId);

    final var builder = ResponseCreateParams.builder().model(modelId);

    applyModelParameters(builder, params);
    applyReasoning(builder, params);
    applySystemPrompt(builder, snapshot.messages());
    applyMessages(builder, snapshot.messages());
    applyTools(builder, snapshot.toolDefinitions());
    applyStructuredOutput(builder, ctx.configuration().response());
    applyServerTools(builder, connection);

    return builder.build();
  }

  private void applyModelParameters(
      ResponseCreateParams.Builder builder, @Nullable OpenAiModelParameters params) {
    if (params == null) {
      return;
    }
    if (params.maxCompletionTokens() != null) {
      builder.maxOutputTokens(params.maxCompletionTokens().longValue());
    }
    if (params.temperature() != null) {
      builder.temperature(params.temperature());
    }
    if (params.topP() != null) {
      builder.topP(params.topP());
    }
  }

  /**
   * Maps the validated {@code effort} dial onto the SDK's {@code reasoning} param. {@code
   * REASONING_ENCRYPTED_CONTENT} is always requested alongside effort so reasoning items can be
   * replayed on a subsequent turn (see {@link #assistantInputItems}); {@code store(false)} keeps
   * the request stateless (Zero Data Retention-compatible), matching the rest of this connector's
   * conversation-memory model, which persists reasoning itself rather than relying on OpenAI-side
   * state.
   */
  private void applyReasoning(
      ResponseCreateParams.Builder builder, @Nullable OpenAiModelParameters params) {
    final OpenAiEffort effort = params == null ? null : params.effort();
    if (effort == null) {
      return;
    }
    builder.reasoning(Reasoning.builder().effort(mapEffort(effort)).build());
    builder.addInclude(ResponseIncludable.REASONING_ENCRYPTED_CONTENT);
    builder.store(false);
  }

  private ReasoningEffort mapEffort(OpenAiEffort effort) {
    return ReasoningEffort.of(effort.name().toLowerCase(Locale.ROOT));
  }

  private void applySystemPrompt(ResponseCreateParams.Builder builder, List<Message> messages) {
    // Relies on the upstream invariant of a single, prepended SystemMessage: hoisting every
    // SystemMessage is equivalent to hoisting just the leading one.
    final String system =
        messages.stream()
            .filter(SystemMessage.class::isInstance)
            .map(SystemMessage.class::cast)
            .flatMap(m -> m.content().stream())
            .filter(TextContent.class::isInstance)
            .map(c -> ((TextContent) c).text())
            .collect(Collectors.joining("\n"));
    if (!system.isBlank()) {
      builder.instructions(system);
    }
  }

  private void applyMessages(ResponseCreateParams.Builder builder, List<Message> messages) {
    final List<ResponseInputItem> items = new ArrayList<>();
    for (final Message message : messages) {
      switch (message) {
        case SystemMessage ignored -> {} // hoisted to top-level instructions
        case UserMessage user -> items.add(userInputItem(user));
        case AssistantMessage assistant -> items.addAll(assistantInputItems(assistant));
        case ToolCallResultMessage toolResults -> items.addAll(toolResultInputItems(toolResults));
        default ->
            throw new IllegalArgumentException(
                "Unsupported message type: " + message.getClass().getSimpleName());
      }
    }
    builder.inputOfResponse(items);
  }

  private ResponseInputItem userInputItem(UserMessage user) {
    return ResponseInputItem.ofEasyInputMessage(
        EasyInputMessage.builder()
            .role(EasyInputMessage.Role.USER)
            .content(
                EasyInputMessage.Content.ofResponseInputMessageContentList(
                    contentConverter.toResponsesContentParts(user.content())))
            .build());
  }

  /**
   * Client tool calls always follow any replayed reasoning/provider-content items, mirroring the
   * Anthropic sibling's content-then-toolCalls grouping. Plain text/document/object content on an
   * assistant message (i.e. everything other than {@link ReasoningContent}/{@link ProviderContent})
   * has no request-side replay in this pilot converter -- known limitation, see class Javadoc.
   */
  private List<ResponseInputItem> assistantInputItems(AssistantMessage assistant) {
    final List<ResponseInputItem> items = new ArrayList<>();
    for (final Content content : assistant.content()) {
      switch (content) {
        case ReasoningContent reasoning -> {
          if (reasoning.providerPayload() != null) {
            items.add(
                objectMapper.convertValue(reasoning.providerPayload(), ResponseInputItem.class));
          } // null payload: nothing captured to replay for this turn
        }
        case ProviderContent providerContent -> {
          if (providerContent.payload() != null) {
            items.add(
                objectMapper.convertValue(providerContent.payload(), ResponseInputItem.class));
          }
        }
        default -> {} // plain text/document/object content has no request-side replay (see above)
      }
    }
    for (final ToolCall toolCall : assistant.toolCalls()) {
      items.add(
          ResponseInputItem.ofFunctionCall(
              ResponseFunctionToolCall.builder()
                  .callId(toolCall.id())
                  .name(toolCall.name())
                  .arguments(writeAsJson(toolCall.arguments()))
                  .build()));
    }
    return items;
  }

  private List<ResponseInputItem> toolResultInputItems(ToolCallResultMessage message) {
    final List<ResponseInputItem> items = new ArrayList<>();
    for (final ToolCallResultContent result : message.results()) {
      items.add(
          ResponseInputItem.ofFunctionCallOutput(
              ResponseInputItem.FunctionCallOutput.builder()
                  .callId(result.id())
                  .output(toTextOutput(result.content()))
                  .build()));
    }
    return items;
  }

  /**
   * Flattens a tool result's structured content to a single text blob: {@link TextContent} is
   * concatenated verbatim, anything else (documents, objects) is serialized to JSON. Deliberate
   * pilot simplification -- {@code FunctionCallOutput.Output} also supports a multimodal item-list
   * shape ({@code ofResponseFunctionCallOutputItemList}) for native inline images/files in tool
   * results, not wired up here.
   */
  private String toTextOutput(List<Content> content) {
    return content.stream()
        .map(c -> c instanceof TextContent text ? text.text() : writeAsJson(c))
        .collect(Collectors.joining("\n"));
  }

  private void applyTools(
      ResponseCreateParams.Builder builder, List<ToolDefinition> toolDefinitions) {
    for (final ToolDefinition definition : toolDefinitions) {
      final var toolBuilder =
          FunctionTool.builder()
              .name(definition.name())
              .parameters(
                  objectMapper.convertValue(
                      definition.inputSchema(), FunctionTool.Parameters.class))
              .strict(false);
      if (definition.description() != null) {
        toolBuilder.description(definition.description());
      }
      builder.addTool(Tool.ofFunction(toolBuilder.build()));
    }
  }

  private void applyStructuredOutput(
      ResponseCreateParams.Builder builder, @Nullable ResponseConfiguration response) {
    if (!(response != null && response.format() instanceof JsonResponseFormatConfiguration json)) {
      return;
    }
    builder.text(
        ResponseTextConfig.builder()
            .format(
                ResponseFormatTextConfig.ofJsonSchema(
                    ResponseFormatTextJsonSchemaConfig.builder()
                        .name(json.schemaName())
                        .schema(
                            objectMapper.convertValue(
                                json.schema(), ResponseFormatTextJsonSchemaConfig.Schema.class))
                        .strict(true)
                        .build()))
            .build());
  }

  private void applyServerTools(ResponseCreateParams.Builder builder, OpenAiConnection connection) {
    if (Boolean.TRUE.equals(connection.enableWebSearch())) {
      builder.addTool(
          Tool.ofWebSearch(WebSearchTool.builder().type(WebSearchTool.Type.WEB_SEARCH).build()));
    }
    if (Boolean.TRUE.equals(connection.enableCodeInterpreter())) {
      builder.addTool(
          Tool.ofCodeInterpreter(
              Tool.CodeInterpreter.builder()
                  .container(
                      Tool.CodeInterpreter.Container.ofCodeInterpreterToolAuto(
                          Tool.CodeInterpreter.Container.CodeInterpreterToolAuto.builder().build()))
                  .build()));
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
