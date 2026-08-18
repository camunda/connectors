/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses;

import static io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OPENAI_ID;

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
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseTextConfig;
import com.openai.models.responses.Tool;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiContentConverter;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.MessageUtil;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi.ResponsesParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiEffort;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiRequestCustomizations;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

  public ResponseCreateParams toRequest(
      OpenAiChatModelConfiguration configuration,
      @Nullable ResponseConfiguration response,
      ConversationSnapshot snapshot) {
    final OpenAiConnection connection = configuration.openai();
    final String modelId = connection.model().model();
    final ResponsesParameters params = responsesParameters(connection);

    final var builder = ResponseCreateParams.builder().model(modelId);

    // Zero Data Retention-compatible: this connector persists conversation memory itself, so it
    // never relies on OpenAI-side response storage.
    builder.store(false);

    applyModelParameters(builder, params);
    applyReasoning(builder, params);
    applySystemPrompt(builder, snapshot.messages());
    applyMessages(builder, snapshot.messages());
    applyTools(builder, snapshot.toolDefinitions());
    applyStructuredOutput(builder, response);
    applyRequestCustomizations(builder, connection);

    return builder.build();
  }

  /**
   * This converter only handles the {@code responses} API family; routing a {@code completions}
   * family configuration here is a caller/family-dispatch bug, not a user-facing configuration
   * error, hence the unchecked exception rather than a {@code ConnectorException}. {@code
   * responses} itself is optional -- every one of its own fields is optional, so a modeler leaving
   * all of them unset means the object is absent entirely, not present-with-nulls.
   */
  private @Nullable ResponsesParameters responsesParameters(OpenAiConnection connection) {
    return switch (connection.api()) {
      case OpenAiResponsesApi responsesApi -> responsesApi.responses();
      default ->
          throw new IllegalArgumentException(
              "OpenAiResponsesRequestConverter requires the 'responses' API family, but was configured with '%s'"
                  .formatted(connection.api().type()));
    };
  }

  private void applyModelParameters(
      ResponseCreateParams.Builder builder, @Nullable ResponsesParameters params) {
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
   * Maps the {@code effort} dial onto the SDK's {@code reasoning} param. {@code
   * REASONING_ENCRYPTED_CONTENT} is always requested alongside effort so reasoning items can be
   * replayed on a subsequent turn (see {@link #assistantInputItems}).
   */
  private void applyReasoning(
      ResponseCreateParams.Builder builder, @Nullable ResponsesParameters params) {
    final OpenAiEffort effort = params == null ? null : params.effort();
    if (effort == null) {
      return;
    }
    builder.reasoning(Reasoning.builder().effort(mapEffort(effort)).build());
    builder.addInclude(ResponseIncludable.REASONING_ENCRYPTED_CONTENT);
  }

  private ReasoningEffort mapEffort(OpenAiEffort effort) {
    return ReasoningEffort.of(effort.name().toLowerCase(Locale.ROOT));
  }

  private void applySystemPrompt(ResponseCreateParams.Builder builder, List<Message> messages) {
    MessageUtil.leadingSystemMessage(messages)
        .map(MessageUtil::contentText)
        .filter(system -> !system.isBlank())
        .ifPresent(builder::instructions);
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
   * Replays the assistant turn's content as input items, in original order: reasoning/provider
   * content tagged for this provider first, then any remaining plain content (text/document/object)
   * as a single assistant-role message, then one item per tool call. Content tagged for a different
   * provider is dropped rather than replayed, since its payload is shaped for that provider's SDK
   * and would either throw or produce garbage if converted here.
   *
   * <p>Plain content is replayed via {@link EasyInputMessage} rather than the structured {@link
   * ResponseOutputMessage} shape, which requires a {@code msg_*}-namespaced {@code id} that {@link
   * AssistantMessage#messageId()} isn't guaranteed to carry.
   */
  private List<ResponseInputItem> assistantInputItems(AssistantMessage assistant) {
    final List<ResponseInputItem> items = new ArrayList<>();
    final List<Content> plainContent = new ArrayList<>();
    for (final Content content : assistant.content()) {
      switch (content) {
        // The SDK's own mapper (not the injected app ObjectMapper) is required here: it is the only
        // one that round-trips these captured payloads' absent-vs-null field tracking correctly.
        case ReasoningContent reasoning when OPENAI_ID.equals(reasoning.provider()) ->
            items.add(
                ObjectMappers.jsonMapper()
                    .convertValue(mergeReasoningText(reasoning), ResponseInputItem.class));
        case ReasoningContent ignored -> {}
        case ProviderContent providerContent when OPENAI_ID.equals(providerContent.provider()) ->
            items.add(
                ObjectMappers.jsonMapper()
                    .convertValue(providerContent.payload(), ResponseInputItem.class));
        case ProviderContent ignored -> {}
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

  /**
   * Reconstructs the reasoning item's {@code summary} field from {@link ReasoningContent#text()}
   * when the response side stripped it during extraction. If {@code payload} already carries a
   * {@code summary}, it is replayed verbatim instead. Returns the payload unchanged when {@code
   * text()} is absent.
   */
  private Object mergeReasoningText(ReasoningContent reasoning) {
    if (reasoning.text() == null) {
      return reasoning.payload();
    }
    if (!(reasoning.payload() instanceof Map<?, ?> rawPayload)) {
      throw new IllegalStateException(
          "Expected reasoning content payload to be a Map when text is present, got %s"
              .formatted(reasoning.payload().getClass().getSimpleName()));
    }
    if (rawPayload.containsKey("summary")) {
      return rawPayload;
    }
    final Map<String, Object> merged = new LinkedHashMap<>();
    rawPayload.forEach((k, v) -> merged.put(String.valueOf(k), v));
    merged.put("summary", List.of(Map.of("type", "summary_text", "text", reasoning.text())));
    return merged;
  }

  private List<ResponseInputItem> toolResultInputItems(ToolCallResultMessage message) {
    final List<ResponseInputItem> items = new ArrayList<>();
    for (final ToolCallResultContent result : message.results()) {
      // OpenAiContentConverter#toResponsesToolResultOutputItems always produces a list of typed
      // items, even for a single plain-text result, so this always uses the SDK's item-list output
      // shape (FunctionCallOutput.Output.ofResponseFunctionCallOutputItemList) rather than its
      // plain-string shortcut (.ofString).
      items.add(
          ResponseInputItem.ofFunctionCallOutput(
              ResponseInputItem.FunctionCallOutput.builder()
                  .callId(result.id())
                  .outputOfResponseFunctionCallOutputItemList(
                      contentConverter.toResponsesToolResultOutputItems(result.content()))
                  .build()));
    }
    return items;
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

  /**
   * Merges the backend's headers, query parameters, and body properties onto the request via the
   * shared {@link OpenAiRequestCustomizations}.
   */
  private void applyRequestCustomizations(
      ResponseCreateParams.Builder builder, OpenAiConnection connection) {
    final var customizations = connection.backend().requestCustomizations();
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
