/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionCallOutputItem;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputContent;
import com.openai.models.responses.ResponseInputFile;
import com.openai.models.responses.ResponseInputFileContent;
import com.openai.models.responses.ResponseInputImage;
import com.openai.models.responses.ResponseInputImageContent;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseInputText;
import com.openai.models.responses.ResponseInputTextContent;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseUsage;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatOptions;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatResponse;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatStreamListener;
import io.camunda.connector.agenticai.aiagent.framework.api.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.content.ContentTextSerializer;
import io.camunda.connector.agenticai.aiagent.framework.multimodal.DocumentModality;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.StopReason;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

/**
 * Native {@link ChatModelApi} for the OpenAI Responses endpoint, driving the {@code openai-java}
 * SDK's blocking {@code responses().create(...)} call.
 *
 * <p>Phase D scope (text-only): user / assistant / tool-result content is restricted to text.
 * Encrypted reasoning round-tripping, prompt caching ({@code prompt_cache_key}), and multimodal
 * input items are deferred to Phase E. Streaming is not yet wired in either.
 *
 * <p>Used by the {@code openai} discriminator when {@code apiFamily = RESPONSES}. The factory still
 * builds the same OkHttp {@link OpenAIClient}; the choice of impl class is the only thing that
 * changes between the two API families.
 */
public class OpenAiResponsesChatModelApi implements ChatModelApi {

  private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {};

  private final OpenAIClient client;
  private final String model;
  private final ObjectMapper objectMapper;
  private final ModelCapabilities capabilities;
  @Nullable private final Long configuredMaxOutputTokens;
  @Nullable private final Double temperature;
  @Nullable private final Double topP;

  public OpenAiResponsesChatModelApi(
      OpenAIClient client,
      String model,
      ObjectMapper objectMapper,
      ModelCapabilities capabilities,
      @Nullable Long configuredMaxOutputTokens,
      @Nullable Double temperature,
      @Nullable Double topP) {
    this.client = Objects.requireNonNull(client, "client");
    this.model = Objects.requireNonNull(model, "model");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
    this.configuredMaxOutputTokens = configuredMaxOutputTokens;
    this.temperature = temperature;
    this.topP = topP;
  }

  @Override
  public ModelCapabilities capabilities() {
    return capabilities;
  }

  @Override
  public CompletableFuture<ChatResponse> complete(
      ChatRequest request, ChatOptions options, ChatStreamListener listener) {
    try {
      final var params = buildParams(request, options);
      final var response = client.responses().create(params);
      return CompletableFuture.completedFuture(new ChatResponse(toAssistantMessage(response)));
    } catch (RuntimeException e) {
      return CompletableFuture.failedFuture(wrapModelCallFailure(e));
    }
  }

  private ResponseCreateParams buildParams(ChatRequest request, ChatOptions options) {
    final var builder = ResponseCreateParams.builder().model(model);

    final var maxTokens = resolveMaxOutputTokens(options);
    if (maxTokens != null) {
      builder.maxOutputTokens(maxTokens);
    }
    Optional.ofNullable(temperature).ifPresent(builder::temperature);
    Optional.ofNullable(topP).ifPresent(builder::topP);

    final var inputItems = new ArrayList<ResponseInputItem>();
    final var messages = request.messages();
    if (messages != null) {
      for (var message : messages) {
        switch (message) {
          case SystemMessage system -> builder.instructions(extractText(system.content()));
          case UserMessage user -> inputItems.add(toUserInputItem(user));
          case AssistantMessage assistant -> addAssistantInputItems(inputItems, assistant);
          case ToolCallResultMessage toolResults ->
              addToolResultInputItems(inputItems, toolResults);
          default ->
              throw new IllegalArgumentException(
                  "Unsupported message type: " + message.getClass().getSimpleName());
        }
      }
    }
    if (!inputItems.isEmpty()) {
      builder.inputOfResponse(inputItems);
    }

    final var toolDefinitions = request.toolDefinitions();
    if (toolDefinitions != null && !toolDefinitions.isEmpty()) {
      builder.tools(OpenAiToolConverter.toResponsesTools(toolDefinitions));
    }

    return builder.build();
  }

  @Nullable
  private Long resolveMaxOutputTokens(ChatOptions options) {
    if (options != null && options.maxOutputTokens() != null) {
      return options.maxOutputTokens().longValue();
    }
    return configuredMaxOutputTokens;
  }

  private String extractText(List<Content> content) {
    if (content == null || content.isEmpty()) {
      return "";
    }
    final var sb = new StringBuilder();
    for (var c : content) {
      sb.append(ContentTextSerializer.toText(c, objectMapper));
    }
    return sb.toString();
  }

  private void addAssistantInputItems(
      List<ResponseInputItem> inputItems, AssistantMessage message) {
    final var text = message.content() != null ? extractText(message.content()) : "";
    if (StringUtils.isNotEmpty(text)) {
      inputItems.add(
          ResponseInputItem.ofEasyInputMessage(
              EasyInputMessage.builder()
                  .role(EasyInputMessage.Role.ASSISTANT)
                  .content(text)
                  .build()));
    }
    if (message.toolCalls() != null) {
      for (var call : message.toolCalls()) {
        inputItems.add(ResponseInputItem.ofFunctionCall(toFunctionToolCall(call)));
      }
    }
  }

  private ResponseFunctionToolCall toFunctionToolCall(ToolCall call) {
    final var args = call.arguments() != null ? toJsonString(call.arguments()) : "{}";
    return ResponseFunctionToolCall.builder()
        .callId(call.id())
        .name(call.name())
        .arguments(args)
        .build();
  }

  private void addToolResultInputItems(
      List<ResponseInputItem> inputItems, ToolCallResultMessage message) {
    for (var result : message.results()) {
      inputItems.add(ResponseInputItem.ofFunctionCallOutput(toFunctionCallOutput(result)));
    }
  }

  private ResponseInputItem.FunctionCallOutput toFunctionCallOutput(ToolCallResult result) {
    final var b = ResponseInputItem.FunctionCallOutput.builder();
    if (result.id() != null) {
      b.callId(result.id());
    }
    final var inlineItems = toolResultMultimodalItems(result);
    if (inlineItems != null) {
      b.outputOfResponseFunctionCallOutputItemList(inlineItems);
    } else {
      final var content = result.content();
      if (content == null) {
        b.output(ToolCallResult.CONTENT_NO_RESULT);
      } else if (content instanceof String s) {
        b.output(s);
      } else {
        b.output(toJsonString(content));
      }
    }
    return b.build();
  }

  /**
   * Builds the {@code [text, image, file]} list for a tool result whose {@code contentBlocks} were
   * populated by the strategy with inline-supported documents. Returns null when no inline routing
   * happened — caller falls back to the string content path.
   *
   * <p>Shape: a single text item with the serialised tool-result content (JSON / string), followed
   * by one image / file item per inline document.
   */
  private List<ResponseFunctionCallOutputItem> toolResultMultimodalItems(ToolCallResult result) {
    if (result.contentBlocks() == null || result.contentBlocks().isEmpty()) {
      return null;
    }
    final var items = new ArrayList<ResponseFunctionCallOutputItem>();
    items.add(
        ResponseFunctionCallOutputItem.ofInputText(
            ResponseInputTextContent.builder().text(serializedToolResultText(result)).build()));
    for (var block : result.contentBlocks()) {
      if (!(block instanceof DocumentContent doc)) {
        throw new IllegalArgumentException(
            "Unsupported inline tool-result content block type: "
                + block.getClass().getSimpleName());
      }
      final var modality = DocumentModality.of(doc.document());
      switch (modality) {
        case IMAGE ->
            items.add(
                ResponseFunctionCallOutputItem.ofInputImage(
                    ResponseInputImageContent.builder()
                        .imageUrl(toDataUrl(doc.document()))
                        .detail(ResponseInputImageContent.Detail.AUTO)
                        .build()));
        case DOCUMENT ->
            items.add(
                ResponseFunctionCallOutputItem.ofInputFile(
                    ResponseInputFileContent.builder()
                        .fileData(toDataUrl(doc.document()))
                        .filename(safeFilename(doc.document()))
                        .build()));
        default ->
            throw new IllegalArgumentException(
                "Document modality "
                    + modality
                    + " is not supported in OpenAI Responses tool-result content (only image + "
                    + "document emit natively); the strategy should have routed this document to "
                    + "a synthetic UserMessage.");
      }
    }
    return items;
  }

  private String serializedToolResultText(ToolCallResult result) {
    final var content = result.content();
    if (content == null) {
      return ToolCallResult.CONTENT_NO_RESULT;
    }
    return content instanceof String s ? s : toJsonString(content);
  }

  /**
   * Builds the {@link ResponseInputItem} for a user message. Pure-text messages keep the legacy
   * {@code content(String)} path; messages with multimodal content blocks (image / document,
   * validated by the strategy) emit a {@code List<ResponseInputContent>} on the same {@code
   * EasyInputMessage}.
   */
  private ResponseInputItem toUserInputItem(UserMessage user) {
    final var content = user.content();
    if (!hasMultimodalContent(content)) {
      return ResponseInputItem.ofEasyInputMessage(
          EasyInputMessage.builder()
              .role(EasyInputMessage.Role.USER)
              .content(extractText(content))
              .build());
    }
    final var items = new ArrayList<ResponseInputContent>();
    for (var c : content) {
      if (c instanceof DocumentContent doc) {
        items.add(documentInputContent(doc.document()));
      } else {
        items.add(
            ResponseInputContent.ofInputText(
                ResponseInputText.builder()
                    .text(ContentTextSerializer.toText(c, objectMapper))
                    .build()));
      }
    }
    return ResponseInputItem.ofEasyInputMessage(
        EasyInputMessage.builder()
            .role(EasyInputMessage.Role.USER)
            .contentOfResponseInputMessageContentList(items)
            .build());
  }

  private static boolean hasMultimodalContent(List<Content> content) {
    if (content == null) {
      return false;
    }
    for (var c : content) {
      if (c instanceof DocumentContent) {
        return true;
      }
    }
    return false;
  }

  private static ResponseInputContent documentInputContent(Document document) {
    final var modality = DocumentModality.of(document);
    return switch (modality) {
      case IMAGE ->
          ResponseInputContent.ofInputImage(
              ResponseInputImage.builder()
                  .imageUrl(toDataUrl(document))
                  .detail(ResponseInputImage.Detail.AUTO)
                  .build());
      case DOCUMENT ->
          ResponseInputContent.ofInputFile(
              ResponseInputFile.builder()
                  .fileData(toDataUrl(document))
                  .filename(safeFilename(document))
                  .build());
      default ->
          throw new IllegalArgumentException(
              "Document modality "
                  + modality
                  + " is not supported in OpenAI Responses user message content (only image + "
                  + "document emit natively); the strategy should have rejected this user-message "
                  + "document.");
    };
  }

  private static String toDataUrl(Document document) {
    final var mimeType = document.metadata().getContentType();
    return "data:" + mimeType + ";base64," + document.asBase64();
  }

  private static String safeFilename(Document document) {
    final var name = document.metadata() != null ? document.metadata().getFileName() : null;
    return StringUtils.isNotBlank(name) ? name : "document";
  }

  private String toJsonString(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize tool argument value", e);
    }
  }

  private static Optional<String> resolveModelId(Response response) {
    final var model = response.model();
    if (model.isString()) {
      return Optional.of(model.asString());
    }
    if (model.isChat()) {
      return Optional.of(model.asChat().asString());
    }
    if (model.isOnly()) {
      return Optional.of(model.asOnly().asString());
    }
    return Optional.empty();
  }

  private AssistantMessage toAssistantMessage(Response response) {
    final var builder = AssistantMessage.builder();
    builder.metadata(Map.of("timestamp", ZonedDateTime.now()));

    if (StringUtils.isNotBlank(response.id())) {
      builder.messageId(response.id());
    }
    resolveModelId(response).filter(StringUtils::isNotBlank).ifPresent(builder::modelId);

    final var content = new ArrayList<Content>();
    final var toolCalls = new ArrayList<ToolCall>();
    for (ResponseOutputItem item : response.output()) {
      if (item.isMessage()) {
        appendOutputMessageText(item.asMessage(), content);
      } else if (item.isFunctionCall()) {
        toolCalls.add(toToolCall(item.asFunctionCall()));
      }
      // ignore reasoning / web search / file search etc. for the text-only first cut
    }
    if (!content.isEmpty()) {
      builder.content(content);
    }
    builder.toolCalls(toolCalls);

    builder.stopReason(toStopReason(response, !toolCalls.isEmpty()));

    response.usage().map(OpenAiResponsesChatModelApi::toTokenUsage).ifPresent(builder::usage);

    return builder.build();
  }

  private static void appendOutputMessageText(
      ResponseOutputMessage outputMessage, List<Content> content) {
    for (var part : outputMessage.content()) {
      if (part.isOutputText()) {
        final var text = part.asOutputText().text();
        if (StringUtils.isNotBlank(text)) {
          content.add(TextContent.textContent(text));
        }
      }
      // refusal blocks are ignored in the text-only first cut
    }
  }

  private ToolCall toToolCall(ResponseFunctionToolCall functionCall) {
    final var arguments = parseArguments(functionCall.arguments());
    return ToolCall.builder()
        .id(functionCall.callId())
        .name(functionCall.name())
        .arguments(arguments)
        .build();
  }

  private Map<String, Object> parseArguments(String json) {
    if (json == null || json.isBlank()) {
      return new LinkedHashMap<>();
    }
    try {
      return objectMapper.readValue(json, MAP_TYPE_REF);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse tool call arguments JSON", e);
    }
  }

  private static StopReason toStopReason(Response response, boolean hasToolCalls) {
    if (hasToolCalls) {
      return StopReason.TOOL_USE;
    }
    final var status = response.status().flatMap(s -> Optional.ofNullable(s.known())).orElse(null);
    if (status == null) {
      return null;
    }
    return switch (status) {
      case COMPLETED -> StopReason.STOP;
      case INCOMPLETE ->
          response
              .incompleteDetails()
              .flatMap(d -> d.reason())
              .flatMap(r -> Optional.ofNullable(r.known()))
              .map(
                  known ->
                      switch (known) {
                        case MAX_OUTPUT_TOKENS -> StopReason.LENGTH;
                        case CONTENT_FILTER -> StopReason.CONTENT_FILTERED;
                      })
              .orElse(StopReason.STOP);
      case FAILED -> StopReason.ERROR;
      case CANCELLED -> StopReason.ABORTED;
      case IN_PROGRESS, QUEUED -> null;
    };
  }

  private static AgentMetrics.TokenUsage toTokenUsage(ResponseUsage usage) {
    final var builder =
        AgentMetrics.TokenUsage.builder()
            .inputTokenCount((int) usage.inputTokens())
            .outputTokenCount((int) usage.outputTokens());
    final var inputDetails = usage.inputTokensDetails();
    if (inputDetails != null) {
      builder.cacheReadInputTokenCount((int) inputDetails.cachedTokens());
    }
    final var outputDetails = usage.outputTokensDetails();
    if (outputDetails != null) {
      builder.reasoningTokenCount((int) outputDetails.reasoningTokens());
    }
    return builder.build();
  }

  private static ConnectorException wrapModelCallFailure(RuntimeException e) {
    final var message =
        Optional.ofNullable(e.getMessage())
            .filter(StringUtils::isNotBlank)
            .orElseGet(() -> e.getClass().getSimpleName());
    return new ConnectorException(
        ERROR_CODE_FAILED_MODEL_CALL, "OpenAI Responses call failed: %s".formatted(message), e);
  }
}
