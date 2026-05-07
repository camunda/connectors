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
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartImage;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.completions.CompletionUsage;
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
 * Native {@link ChatModelApi} for the OpenAI Chat Completions endpoint, driving the {@code
 * openai-java} SDK's blocking {@code chat().completions().create(...)} call.
 *
 * <p>Phase C scope (text-only): user / assistant / tool-result content is restricted to text;
 * multimodal content blocks, reasoning round-tripping (encrypted reasoning items don't apply to
 * Chat Completions), and prompt caching are deferred to Phase E. {@link
 * ChatOptions#cacheRetention()} is accepted but ignored. Streaming is not yet wired in either —
 * {@link ChatStreamListener} is accepted but no events are emitted.
 *
 * <p>Used by both the {@code openai} and {@code openaiCompatible} discriminators (the OkHttp {@link
 * OpenAIClient} differs only in baseUrl / auth construction, which the factory handles).
 */
public class OpenAiChatCompletionsChatModelApi implements ChatModelApi {

  private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {};

  private final OpenAIClient client;
  private final String model;
  private final ObjectMapper objectMapper;
  private final ModelCapabilities capabilities;
  @Nullable private final Long configuredMaxCompletionTokens;
  @Nullable private final Double temperature;
  @Nullable private final Double topP;

  public OpenAiChatCompletionsChatModelApi(
      OpenAIClient client,
      String model,
      ObjectMapper objectMapper,
      ModelCapabilities capabilities,
      @Nullable Long configuredMaxCompletionTokens,
      @Nullable Double temperature,
      @Nullable Double topP) {
    this.client = Objects.requireNonNull(client, "client");
    this.model = Objects.requireNonNull(model, "model");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
    this.configuredMaxCompletionTokens = configuredMaxCompletionTokens;
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
      final var completion = client.chat().completions().create(params);
      return CompletableFuture.completedFuture(new ChatResponse(toAssistantMessage(completion)));
    } catch (RuntimeException e) {
      return CompletableFuture.failedFuture(wrapModelCallFailure(e));
    }
  }

  private ChatCompletionCreateParams buildParams(ChatRequest request, ChatOptions options) {
    final var builder = ChatCompletionCreateParams.builder().model(model);

    final var maxTokens = resolveMaxCompletionTokens(options);
    if (maxTokens != null) {
      builder.maxCompletionTokens(maxTokens);
    }
    Optional.ofNullable(temperature).ifPresent(builder::temperature);
    Optional.ofNullable(topP).ifPresent(builder::topP);

    final var messages = request.messages();
    if (messages != null) {
      for (var message : messages) {
        switch (message) {
          case SystemMessage system -> builder.addSystemMessage(systemPrompt(system));
          case UserMessage user -> addUserMessage(builder, user);
          case AssistantMessage assistant -> builder.addMessage(toAssistantParam(assistant));
          case ToolCallResultMessage toolResults -> addToolResultMessages(builder, toolResults);
          default ->
              throw new IllegalArgumentException(
                  "Unsupported message type: " + message.getClass().getSimpleName());
        }
      }
    }

    final var toolDefinitions = request.toolDefinitions();
    if (toolDefinitions != null && !toolDefinitions.isEmpty()) {
      builder.tools(OpenAiToolConverter.toTools(toolDefinitions));
    }

    return builder.build();
  }

  @Nullable
  private Long resolveMaxCompletionTokens(ChatOptions options) {
    if (options != null && options.maxOutputTokens() != null) {
      return options.maxOutputTokens().longValue();
    }
    return configuredMaxCompletionTokens;
  }

  private String systemPrompt(SystemMessage system) {
    return extractText(system.content());
  }

  /**
   * Routes a {@link UserMessage} onto either the legacy text-only {@code addUserMessage(String)}
   * path or the multimodal {@code addUserMessageOfArrayOfContentParts(...)} path. The latter is
   * used when the message contains at least one {@link DocumentContent} (image / document,
   * validated upstream by {@code ToolCallResultStrategy}).
   */
  private void addUserMessage(ChatCompletionCreateParams.Builder builder, UserMessage user) {
    final var content = user.content();
    if (!hasMultimodalContent(content)) {
      builder.addUserMessage(extractText(content));
      return;
    }
    final var parts = new ArrayList<ChatCompletionContentPart>();
    for (var c : content) {
      if (c instanceof DocumentContent doc) {
        parts.add(documentPart(doc.document()));
      } else {
        parts.add(
            ChatCompletionContentPart.ofText(
                ChatCompletionContentPartText.builder()
                    .text(ContentTextSerializer.toText(c, objectMapper))
                    .build()));
      }
    }
    builder.addUserMessageOfArrayOfContentParts(parts);
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

  private static ChatCompletionContentPart documentPart(Document document) {
    final var modality = DocumentModality.of(document);
    return switch (modality) {
      case IMAGE ->
          ChatCompletionContentPart.ofImageUrl(
              ChatCompletionContentPartImage.builder()
                  .imageUrl(
                      ChatCompletionContentPartImage.ImageUrl.builder()
                          .url(toDataUrl(document))
                          .detail(ChatCompletionContentPartImage.ImageUrl.Detail.AUTO)
                          .build())
                  .build());
      case DOCUMENT ->
          ChatCompletionContentPart.ofFile(
              ChatCompletionContentPart.File.builder()
                  .file(
                      ChatCompletionContentPart.File.FileObject.builder()
                          .fileData(toDataUrl(document))
                          .filename(safeFilename(document))
                          .build())
                  .build());
      default ->
          throw new IllegalArgumentException(
              "Document modality "
                  + modality
                  + " is not supported in OpenAI Chat Completions user-message content (only "
                  + "image + document emit natively); the strategy should have rejected this "
                  + "user-message document.");
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

  private ChatCompletionAssistantMessageParam toAssistantParam(AssistantMessage message) {
    final var builder = ChatCompletionAssistantMessageParam.builder();
    final var text = message.content() != null ? extractText(message.content()) : "";
    if (StringUtils.isNotEmpty(text)) {
      builder.content(text);
    }
    if (message.toolCalls() != null) {
      for (var call : message.toolCalls()) {
        builder.addToolCall(toFunctionToolCall(call));
      }
    }
    return builder.build();
  }

  private ChatCompletionMessageToolCall toFunctionToolCall(ToolCall call) {
    final var args = call.arguments() != null ? toJsonString(call.arguments()) : "{}";
    return ChatCompletionMessageToolCall.ofFunction(
        ChatCompletionMessageFunctionToolCall.builder()
            .id(call.id())
            .function(
                ChatCompletionMessageFunctionToolCall.Function.builder()
                    .name(call.name())
                    .arguments(args)
                    .build())
            .build());
  }

  private void addToolResultMessages(
      ChatCompletionCreateParams.Builder builder, ToolCallResultMessage message) {
    for (var result : message.results()) {
      builder.addMessage(toToolResultParam(result));
    }
  }

  private ChatCompletionToolMessageParam toToolResultParam(ToolCallResult result) {
    final var b = ChatCompletionToolMessageParam.builder();
    if (result.id() != null) {
      b.toolCallId(result.id());
    }
    final var content = result.content();
    if (content == null) {
      b.content(ToolCallResult.CONTENT_NO_RESULT);
    } else if (content instanceof String s) {
      b.content(s);
    } else {
      b.content(toJsonString(content));
    }
    return b.build();
  }

  private String toJsonString(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize tool argument value", e);
    }
  }

  private AssistantMessage toAssistantMessage(ChatCompletion completion) {
    if (completion.choices().isEmpty()) {
      throw new IllegalStateException("OpenAI Chat Completions returned no choices");
    }

    final var choice = completion.choices().getFirst();
    final var message = choice.message();
    final var builder = AssistantMessage.builder();
    builder.metadata(Map.of("timestamp", ZonedDateTime.now()));

    if (StringUtils.isNotBlank(completion.id())) {
      builder.messageId(completion.id());
    }
    if (StringUtils.isNotBlank(completion.model())) {
      builder.modelId(completion.model());
    }

    final var content = new ArrayList<Content>();
    message
        .content()
        .filter(StringUtils::isNotBlank)
        .map(TextContent::textContent)
        .ifPresent(content::add);
    if (!content.isEmpty()) {
      builder.content(content);
    }

    final var toolCalls = new ArrayList<ToolCall>();
    message
        .toolCalls()
        .ifPresent(
            calls -> {
              for (var call : calls) {
                if (call.isFunction()) {
                  toolCalls.add(toToolCall(call.asFunction()));
                }
              }
            });
    builder.toolCalls(toolCalls);

    builder.stopReason(toStopReason(choice.finishReason()));

    completion
        .usage()
        .map(OpenAiChatCompletionsChatModelApi::toTokenUsage)
        .ifPresent(builder::usage);

    return builder.build();
  }

  private ToolCall toToolCall(ChatCompletionMessageFunctionToolCall functionCall) {
    final var function = functionCall.function();
    final var arguments = parseArguments(function.arguments());
    return ToolCall.builder()
        .id(functionCall.id())
        .name(function.name())
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

  private static StopReason toStopReason(ChatCompletion.Choice.FinishReason finishReason) {
    if (finishReason == null) {
      return null;
    }
    final var known = finishReason.known();
    if (known == null) {
      return null;
    }
    return switch (known) {
      case STOP -> StopReason.STOP;
      case LENGTH -> StopReason.LENGTH;
      case TOOL_CALLS, FUNCTION_CALL -> StopReason.TOOL_USE;
      case CONTENT_FILTER -> StopReason.CONTENT_FILTERED;
    };
  }

  private static AgentMetrics.TokenUsage toTokenUsage(CompletionUsage usage) {
    final var builder =
        AgentMetrics.TokenUsage.builder()
            .inputTokenCount((int) usage.promptTokens())
            .outputTokenCount((int) usage.completionTokens());
    usage
        .promptTokensDetails()
        .flatMap(CompletionUsage.PromptTokensDetails::cachedTokens)
        .ifPresent(v -> builder.cacheReadInputTokenCount(v.intValue()));
    usage
        .completionTokensDetails()
        .flatMap(CompletionUsage.CompletionTokensDetails::reasoningTokens)
        .ifPresent(v -> builder.reasoningTokenCount(v.intValue()));
    return builder.build();
  }

  private static ConnectorException wrapModelCallFailure(RuntimeException e) {
    final var message =
        Optional.ofNullable(e.getMessage())
            .filter(StringUtils::isNotBlank)
            .orElseGet(() -> e.getClass().getSimpleName());
    return new ConnectorException(
        ERROR_CODE_FAILED_MODEL_CALL,
        "OpenAI Chat Completions call failed: %s".formatted(message),
        e);
  }
}
