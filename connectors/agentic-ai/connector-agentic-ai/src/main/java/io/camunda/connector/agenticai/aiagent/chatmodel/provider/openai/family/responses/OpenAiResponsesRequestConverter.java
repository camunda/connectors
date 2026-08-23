/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses;

import static io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OPENAI_ID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.JsonValue;
import com.openai.core.ObjectMappers;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextConfig;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseIncludable;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseTextConfig;
import com.openai.models.responses.Tool;
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
import java.util.UUID;
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
   * Requests {@code REASONING_ENCRYPTED_CONTENT} unconditionally, independent of whether {@code
   * effort} is configured: a reasoning-capable model can apply its own default reasoning effort
   * even without an explicit {@code reasoning} param, and this connector always runs with {@code
   * store(false)} (see {@link #toRequest}), so {@code encrypted_content} is the only way such a
   * reasoning item can be replayed on a subsequent turn (see {@link #assistantInputItems}) rather
   * than losing its chain of thought. The {@code effort} dial itself, mapped onto the SDK's {@code
   * reasoning} param, stays conditional on explicit configuration.
   */
  private void applyReasoning(
      ResponseCreateParams.Builder builder, @Nullable ResponsesParameters params) {
    builder.addInclude(ResponseIncludable.REASONING_ENCRYPTED_CONTENT);

    final OpenAiEffort effort = params == null ? null : params.effort();
    if (effort == null) {
      return;
    }
    builder.reasoning(Reasoning.builder().effort(mapEffort(effort)).build());
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
   * <p>Plain content is replayed as a {@link ResponseOutputMessage}, not the {@link
   * EasyInputMessage} shape used for user messages: the Responses API accepts only {@code
   * output_text}/{@code refusal} content parts for the assistant role, and {@link
   * EasyInputMessage}'s part-list shape (built from {@link
   * OpenAiContentConverter#toResponsesContentParts}) is exclusively {@code input_text}/{@code
   * input_image}/{@code input_file}, which the API rejects for that role. See {@link
   * #assistantContentInputItem(AssistantMessage, List)} for the item's exact shape.
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
      items.add(assistantContentInputItem(assistant, plainContent));
    }
    for (final ToolCall toolCall : assistant.toolCalls()) {
      items.add(
          ResponseInputItem.ofFunctionCall(
              ResponseFunctionToolCall.builder()
                  .callId(toolCall.id())
                  .name(toolCall.name())
                  .arguments(contentConverter.writeAsJson(toolCall.arguments()))
                  .build()));
    }
    return items;
  }

  /**
   * Builds the assistant-role input item: a {@link ResponseOutputMessage} with one {@code
   * output_text} part per {@link Content} block. {@link DocumentContent}/{@link ObjectContent}
   * become an {@code output_text} part carrying their JSON serialization, since that part type
   * carries text only. Refusals never reach here -- the response converter turns them into a thrown
   * exception, not assistant content.
   */
  private ResponseInputItem assistantContentInputItem(
      AssistantMessage assistant, List<Content> plainContent) {
    final List<ResponseOutputMessage.Content> parts =
        plainContent.stream().map(this::outputTextPart).toList();
    return ResponseInputItem.ofResponseOutputMessage(
        ResponseOutputMessage.builder()
            .id(assistantMessageId(assistant))
            .status(ResponseOutputMessage.Status.COMPLETED)
            .content(parts)
            .build());
  }

  private ResponseOutputMessage.Content outputTextPart(Content content) {
    final String text =
        switch (content) {
          case TextContent t -> t.text();
          case ObjectContent obj -> contentConverter.writeAsJson(obj.content());
          case DocumentContent doc -> contentConverter.writeAsJson(doc.document());
          default ->
              throw new IllegalStateException(
                  "Reasoning/provider content is routed by assistantInputItems before reaching "
                      + "plain-content handling and must never appear here: "
                      + content.getClass().getSimpleName());
        };
    return ResponseOutputMessage.Content.ofOutputText(
        ResponseOutputText.builder().text(text).annotations(List.of()).build());
  }

  /**
   * The {@code id} to replay an assistant message under. A genuine Responses-origin turn's {@link
   * AssistantMessage#messageId()} is the {@code msg_*} output-item id {@code
   * OpenAiResponsesResponseConverter} captured from the real response, so it is passed through
   * verbatim. Any other value -- absent (e.g. v1 LangChain4j-sourced history, a legacy record) or
   * foreign-namespaced (e.g. Completions' {@code chatcmpl_*}, or another provider's own id scheme
   * after a family/provider switch) -- is rejected by the API ({@code "Expected an ID that begins
   * with msg"}), so a fresh {@code msg_}-namespaced id is synthesized instead; {@code id} is
   * required to build a {@link ResponseOutputMessage} at all, so replay can't simply omit it here.
   */
  private String assistantMessageId(AssistantMessage assistant) {
    final String messageId = assistant.messageId();
    return messageId != null && messageId.startsWith("msg_")
        ? messageId
        : "msg_" + UUID.randomUUID().toString().replace("-", "");
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
    if (json.schema() == null) {
      // JSON mode without a schema: constrain the model to valid JSON without a structure.
      builder.text(
          ResponseTextConfig.builder()
              .format(
                  ResponseFormatTextConfig.ofJsonObject(ResponseFormatJsonObject.builder().build()))
              .build());
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
                                OpenAiStrictJsonSchemas.forStrictMode(json.schema(), objectMapper),
                                ResponseFormatTextJsonSchemaConfig.Schema.class))
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
}
