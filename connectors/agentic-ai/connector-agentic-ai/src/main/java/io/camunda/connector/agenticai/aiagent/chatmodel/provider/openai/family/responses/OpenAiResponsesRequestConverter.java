/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.JsonValue;
import com.openai.core.ObjectMappers;
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
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModel.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModel.OpenAiModel.OpenAiModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiEffort;
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
    final OpenAiChatModel model = (OpenAiChatModel) ctx.configuration().chatModelApiConfiguration();
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
    applyCompatibleRequestParameters(builder, connection);

    return builder.build();
  }

  private void applyModelParameters(
      ResponseCreateParams.Builder builder, @Nullable OpenAiModelParameters params) {
    if (params == null) {
      return;
    }
    if (params.maxOutputTokens() != null) {
      builder.maxOutputTokens(params.maxOutputTokens().longValue());
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
   * Client tool calls always follow any replayed reasoning/provider-content/plain-content items,
   * mirroring the Anthropic sibling's content-then-toolCalls grouping. Plain text/document/object
   * content (i.e. everything other than {@link ReasoningContent}/{@link ProviderContent}) is
   * collected in encounter order and replayed as a single assistant-role message input item, placed
   * after any reasoning/provider-content items and before tool calls -- matching the order the
   * model originally produced them in (reasoning/text before a function_call). No item is emitted
   * when the assistant turn carries no plain content (e.g. tool-calls-only or reasoning-only
   * turns).
   *
   * <p>The reasoning/provider-content payloads are replayed via the SDK's own {@link
   * ObjectMappers#jsonMapper()} rather than the injected app {@link ObjectMapper}: the captured
   * payload's Kotlin-generated absent-vs-null field tracking only round-trips correctly through
   * that mapper (see {@code OpenAiResponsesResponseConverter#toRawMap}) -- mirrors the Anthropic
   * sibling's raw block replay.
   */
  private List<ResponseInputItem> assistantInputItems(AssistantMessage assistant) {
    final List<ResponseInputItem> items = new ArrayList<>();
    final List<Content> plainContent = new ArrayList<>();
    for (final Content content : assistant.content()) {
      switch (content) {
        case ReasoningContent reasoning -> {
          if (reasoning.providerPayload() != null) {
            items.add(
                ObjectMappers.jsonMapper()
                    .convertValue(reasoning.providerPayload(), ResponseInputItem.class));
          } // null payload: nothing captured to replay for this turn
        }
        case ProviderContent providerContent -> {
          if (providerContent.payload() != null) {
            items.add(
                ObjectMappers.jsonMapper()
                    .convertValue(providerContent.payload(), ResponseInputItem.class));
          }
        }
        default -> plainContent.add(content); // Text/Object/Document: replayed as a message below
      }
    }
    if (!plainContent.isEmpty()) {
      items.add(
          ResponseInputItem.ofEasyInputMessage(
              EasyInputMessage.builder()
                  .role(EasyInputMessage.Role.ASSISTANT)
                  .content(
                      EasyInputMessage.Content.ofResponseInputMessageContentList(
                          contentConverter.toResponsesContentParts(plainContent)))
                  .build()));
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
      final var builder = ResponseInputItem.FunctionCallOutput.builder().callId(result.id());
      if (containsDocument(result.content())) {
        // Documents only reach here when the capability matrix declares the modality supported in
        // tool results; emit them natively (input_image/input_file) via the multimodal item-list
        // shape so the model can actually read them, mirroring the Anthropic sibling.
        builder.outputOfResponseFunctionCallOutputItemList(
            contentConverter.toToolResultOutputItems(result.content()));
      } else {
        builder.output(toTextOutput(result.content()));
      }
      items.add(ResponseInputItem.ofFunctionCallOutput(builder.build()));
    }
    return items;
  }

  private static boolean containsDocument(List<Content> content) {
    return content.stream().anyMatch(DocumentContent.class::isInstance);
  }

  /**
   * Flattens a text-only tool result to a single string blob: {@link TextContent} is concatenated
   * verbatim, {@link ObjectContent} is unwrapped to its raw {@code content()} before being
   * serialized to JSON (matching {@code AnthropicContentConverter}'s and {@link
   * OpenAiContentConverter}'s handling - otherwise the polymorphic {@link Content} envelope itself,
   * including its {@code type} discriminator, would leak onto the wire), anything else (documents)
   * falls back to serializing the whole content value. Used when the result carries no document
   * content; results containing documents take the multimodal item-list path instead (see {@link
   * #toolResultInputItems}).
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

  /**
   * The OpenAI-compatible backend allows passing arbitrary additional request-body parameters (e.g.
   * vendor-specific extensions unsupported by the SDK's typed builder). {@code headers}/ {@code
   * queryParameters} on the same backend are applied at the client level (see {@code
   * OpenAiOkHttpClientFactory}), but body parameters can only be merged here, at request-building
   * time.
   */
  private void applyCompatibleRequestParameters(
      ResponseCreateParams.Builder builder, OpenAiConnection connection) {
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
