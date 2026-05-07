/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.anthropic.models.messages.Usage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatOptions;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatResponse;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatStreamListener;
import io.camunda.connector.agenticai.aiagent.framework.api.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.multimodal.DocumentModality;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.AssistantMessageBuilder;
import io.camunda.connector.agenticai.model.message.StopReason;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

/**
 * Native {@link ChatModelApi} for the Anthropic Messages API (direct backend), driving the {@code
 * anthropic-java} SDK's blocking {@code messages().create(...)} endpoint.
 *
 * <p>Phase B scope (text-only): user / assistant / tool-result content is restricted to text;
 * multimodal content blocks, reasoning round-tripping and prompt caching are deferred to Phase E.
 * {@link ChatOptions#reasoning()} and {@link ChatOptions#cacheRetention()} are accepted but ignored
 * in this phase. Streaming is not yet wired in either — {@link ChatStreamListener} is accepted but
 * no events are emitted.
 */
public class AnthropicMessagesChatModelApi implements ChatModelApi {

  private static final long DEFAULT_MAX_TOKENS = 4096L;

  private final AnthropicClient client;
  private final String model;
  private final ObjectMapper objectMapper;
  private final ModelCapabilities capabilities;
  @Nullable private final Long configuredMaxTokens;
  @Nullable private final Double temperature;
  @Nullable private final Double topP;
  @Nullable private final Long topK;

  public AnthropicMessagesChatModelApi(
      AnthropicClient client,
      String model,
      ObjectMapper objectMapper,
      ModelCapabilities capabilities,
      @Nullable Long configuredMaxTokens,
      @Nullable Double temperature,
      @Nullable Double topP,
      @Nullable Long topK) {
    this.client = Objects.requireNonNull(client, "client");
    this.model = Objects.requireNonNull(model, "model");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
    this.configuredMaxTokens = configuredMaxTokens;
    this.temperature = temperature;
    this.topP = topP;
    this.topK = topK;
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
      final var response = client.messages().create(params);
      return CompletableFuture.completedFuture(new ChatResponse(toAssistantMessage(response)));
    } catch (RuntimeException e) {
      return CompletableFuture.failedFuture(wrapModelCallFailure(e));
    }
  }

  private MessageCreateParams buildParams(ChatRequest request, ChatOptions options) {
    final var builder =
        MessageCreateParams.builder().model(model).maxTokens(resolveMaxTokens(options));

    Optional.ofNullable(temperature).ifPresent(builder::temperature);
    Optional.ofNullable(topP).ifPresent(builder::topP);
    Optional.ofNullable(topK).ifPresent(builder::topK);

    final var messages = request.messages();
    if (messages != null) {
      for (var message : messages) {
        switch (message) {
          case SystemMessage system -> builder.system(systemPrompt(system));
          case UserMessage user -> builder.addMessage(toMessageParam(user));
          case AssistantMessage assistant -> builder.addMessage(toMessageParam(assistant));
          case ToolCallResultMessage toolResult -> builder.addMessage(toMessageParam(toolResult));
          default ->
              throw new IllegalArgumentException(
                  "Unsupported message type: " + message.getClass().getSimpleName());
        }
      }
    }

    final var toolDefinitions = request.toolDefinitions();
    if (toolDefinitions != null && !toolDefinitions.isEmpty()) {
      builder.tools(toolDefinitions.stream().map(this::toToolUnion).toList());
    }

    return builder.build();
  }

  private long resolveMaxTokens(ChatOptions options) {
    if (options != null && options.maxOutputTokens() != null) {
      return options.maxOutputTokens().longValue();
    }
    return configuredMaxTokens != null ? configuredMaxTokens : DEFAULT_MAX_TOKENS;
  }

  private static String systemPrompt(SystemMessage system) {
    if (system.content() == null || system.content().isEmpty()) {
      return "";
    }
    if (system.content().size() == 1 && system.content().getFirst() instanceof TextContent t) {
      return t.text();
    }
    throw new IllegalArgumentException(
        "SystemMessage currently only supports a single TextContent block.");
  }

  private MessageParam toMessageParam(UserMessage message) {
    final var blocks = messageContentBlocks(message.content());
    return MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(blocks).build();
  }

  private MessageParam toMessageParam(AssistantMessage message) {
    final var blocks = new ArrayList<ContentBlockParam>();
    if (message.content() != null) {
      blocks.addAll(textOnlyBlocks(message.content()));
    }
    if (message.toolCalls() != null) {
      for (var call : message.toolCalls()) {
        blocks.add(toolUseBlock(call));
      }
    }
    return MessageParam.builder()
        .role(MessageParam.Role.ASSISTANT)
        .contentOfBlockParams(blocks)
        .build();
  }

  private MessageParam toMessageParam(ToolCallResultMessage message) {
    final var blocks = message.results().stream().map(this::toolResultBlock).toList();
    return MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(blocks).build();
  }

  /**
   * User-message blocks: text + multimodal documents (image, PDF). The {@link
   * io.camunda.connector.agenticai.aiagent.framework.strategy.ToolCallResultStrategy} has already
   * validated user-message documents against the model's {@code userMessageModalities}, so any
   * {@link DocumentContent} reaching this point is known to be supported.
   */
  private static List<ContentBlockParam> messageContentBlocks(List<Content> content) {
    if (content == null) {
      return List.of();
    }
    final var blocks = new ArrayList<ContentBlockParam>();
    for (var c : content) {
      blocks.add(messageContentBlock(c));
    }
    return blocks;
  }

  private static ContentBlockParam messageContentBlock(Content content) {
    if (content instanceof DocumentContent doc) {
      final var modality = DocumentModality.of(doc.document());
      return switch (modality) {
        case IMAGE -> ContentBlockParam.ofImage(imageBlockParam(doc.document()));
        case PDF -> ContentBlockParam.ofDocument(pdfBlockParam(doc.document()));
        default ->
            throw new IllegalArgumentException(
                "Document modality "
                    + modality
                    + " is not supported in Anthropic user/tool messages "
                    + "(only image + PDF emit natively); the strategy should have routed this "
                    + "document to a synthetic UserMessage or rejected it.");
      };
    }
    return textOnlyBlock(content);
  }

  /**
   * Assistant-message blocks: text only (assistant turns we send back to the model don't carry
   * documents). Used by the assistant-message conversion path.
   */
  private static List<ContentBlockParam> textOnlyBlocks(List<Content> content) {
    if (content == null) {
      return List.of();
    }
    final var blocks = new ArrayList<ContentBlockParam>();
    for (var c : content) {
      blocks.add(textOnlyBlock(c));
    }
    return blocks;
  }

  private static ContentBlockParam textOnlyBlock(Content content) {
    if (content instanceof TextContent text) {
      return ContentBlockParam.ofText(TextBlockParam.builder().text(text.text()).build());
    }
    if (content instanceof ObjectContent object) {
      return ContentBlockParam.ofText(
          TextBlockParam.builder().text(String.valueOf(object.content())).build());
    }
    throw new IllegalArgumentException(
        "Unsupported content block for text-only Anthropic Messages API: "
            + content.getClass().getSimpleName());
  }

  private static ImageBlockParam imageBlockParam(Document document) {
    final var mimeType = document.metadata().getContentType();
    return ImageBlockParam.builder()
        .source(
            Base64ImageSource.builder()
                .data(document.asBase64())
                .mediaType(toAnthropicImageMediaType(mimeType))
                .build())
        .build();
  }

  private static DocumentBlockParam pdfBlockParam(Document document) {
    return DocumentBlockParam.builder()
        .source(Base64PdfSource.builder().data(document.asBase64()).build())
        .build();
  }

  private static Base64ImageSource.MediaType toAnthropicImageMediaType(String mimeType) {
    return switch (mimeType.toLowerCase().trim()) {
      case "image/jpeg" -> Base64ImageSource.MediaType.IMAGE_JPEG;
      case "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG;
      case "image/gif" -> Base64ImageSource.MediaType.IMAGE_GIF;
      case "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP;
      default ->
          throw new IllegalArgumentException(
              "Unsupported image MIME type for Anthropic image source: " + mimeType);
    };
  }

  private static ContentBlockParam toolUseBlock(ToolCall call) {
    final var inputBuilder = ToolUseBlockParam.Input.builder();
    if (call.arguments() != null) {
      call.arguments()
          .forEach((key, value) -> inputBuilder.putAdditionalProperty(key, JsonValue.from(value)));
    }
    return ContentBlockParam.ofToolUse(
        ToolUseBlockParam.builder()
            .id(call.id())
            .name(call.name())
            .input(inputBuilder.build())
            .build());
  }

  private ContentBlockParam toolResultBlock(ToolCallResult result) {
    final var b = ToolResultBlockParam.builder().toolUseId(result.id());
    final var inlineBlocks = toolResultContentBlocks(result);
    if (inlineBlocks != null) {
      b.contentOfBlocks(inlineBlocks);
    } else {
      final var content = result.content();
      if (content == null) {
        b.content(ToolCallResult.CONTENT_NO_RESULT);
      } else if (content instanceof String s) {
        b.content(s);
      } else {
        b.contentAsJson(content);
      }
    }
    final var interrupted =
        result.properties() != null
            && Boolean.TRUE.equals(result.properties().get(ToolCallResult.PROPERTY_INTERRUPTED));
    if (interrupted) {
      b.isError(true);
    }
    return ContentBlockParam.ofToolResult(b.build());
  }

  /**
   * Builds the {@code [text, image, document]} block list for a tool result whose {@code
   * contentBlocks} were populated by the strategy with inline-supported documents. Returns null
   * when no inline routing happened — caller falls back to the string / JSON content path.
   *
   * <p>Shape: one text block with the serialised tool-result content (so the model still sees the
   * structured JSON the result came from, with document references in place), followed by one image
   * / document block per inline document.
   */
  private List<ToolResultBlockParam.Content.Block> toolResultContentBlocks(ToolCallResult result) {
    if (result.contentBlocks() == null || result.contentBlocks().isEmpty()) {
      return null;
    }
    final var blocks = new ArrayList<ToolResultBlockParam.Content.Block>();
    blocks.add(
        ToolResultBlockParam.Content.Block.ofText(
            TextBlockParam.builder().text(serializedToolResultText(result)).build()));
    for (var block : result.contentBlocks()) {
      if (!(block instanceof DocumentContent doc)) {
        throw new IllegalArgumentException(
            "Unsupported inline tool-result content block type: "
                + block.getClass().getSimpleName());
      }
      final var modality = DocumentModality.of(doc.document());
      switch (modality) {
        case IMAGE ->
            blocks.add(ToolResultBlockParam.Content.Block.ofImage(imageBlockParam(doc.document())));
        case PDF ->
            blocks.add(
                ToolResultBlockParam.Content.Block.ofDocument(pdfBlockParam(doc.document())));
        default ->
            throw new IllegalArgumentException(
                "Document modality "
                    + modality
                    + " is not supported in Anthropic tool result blocks (only image + PDF emit "
                    + "natively); the strategy should have routed this document to a synthetic "
                    + "UserMessage.");
      }
    }
    return blocks;
  }

  private String serializedToolResultText(ToolCallResult result) {
    final var content = result.content();
    if (content == null) {
      return ToolCallResult.CONTENT_NO_RESULT;
    }
    if (content instanceof String s) {
      return s;
    }
    try {
      return objectMapper.writeValueAsString(content);
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          ERROR_CODE_FAILED_MODEL_CALL,
          "Failed to serialise tool call result content to JSON for tool '%s': %s"
              .formatted(result.name(), e.getOriginalMessage()));
    }
  }

  private ToolUnion toToolUnion(ToolDefinition definition) {
    final var tool =
        Tool.builder().name(definition.name()).inputSchema(toInputSchema(definition.inputSchema()));
    if (definition.description() != null) {
      tool.description(definition.description());
    }
    return ToolUnion.ofTool(tool.build());
  }

  private static final Set<String> KNOWN_SCHEMA_KEYS = Set.of("type", "properties", "required");

  @SuppressWarnings("unchecked")
  private static Tool.InputSchema toInputSchema(Map<String, Object> schemaMap) {
    final var builder = Tool.InputSchema.builder();
    if (schemaMap == null || schemaMap.isEmpty()) {
      return builder.build();
    }

    final var type = schemaMap.get("type");
    if (type != null) {
      builder.type(JsonValue.from(type));
    }

    final var properties = schemaMap.get("properties");
    if (properties instanceof Map<?, ?> propsMap) {
      final var pb = Tool.InputSchema.Properties.builder();
      ((Map<String, Object>) propsMap)
          .forEach((key, value) -> pb.putAdditionalProperty(key, JsonValue.from(value)));
      builder.properties(pb.build());
    }

    final var required = schemaMap.get("required");
    if (required instanceof List<?> reqList) {
      builder.required(reqList.stream().map(String::valueOf).toList());
    }

    schemaMap.forEach(
        (key, value) -> {
          if (!KNOWN_SCHEMA_KEYS.contains(key)) {
            builder.putAdditionalProperty(key, JsonValue.from(value));
          }
        });

    return builder.build();
  }

  private AssistantMessage toAssistantMessage(Message message) {
    final var builder = AssistantMessage.builder();
    builder.metadata(Map.of("timestamp", ZonedDateTime.now()));

    if (StringUtils.isNotBlank(message.id())) {
      builder.messageId(message.id());
    }
    final var modelId = message.model().asString();
    if (StringUtils.isNotBlank(modelId)) {
      builder.modelId(modelId);
    }

    final var contentBlocks = new ArrayList<Content>();
    final var toolCalls = new ArrayList<ToolCall>();
    for (ContentBlock block : message.content()) {
      if (block.isText()) {
        final var text = block.asText().text();
        if (StringUtils.isNotBlank(text)) {
          contentBlocks.add(TextContent.textContent(text));
        }
      } else if (block.isToolUse()) {
        final var toolUse = block.asToolUse();
        toolCalls.add(toToolCall(toolUse.id(), toolUse.name(), toolUse._input()));
      }
      // ignore other block types (thinking / server-tool-* / etc.) for the text-only first cut
    }
    if (!contentBlocks.isEmpty()) {
      builder.content(contentBlocks);
    }
    builder.toolCalls(toolCalls);

    message
        .stopReason()
        .map(AnthropicMessagesChatModelApi::toStopReason)
        .ifPresent(builder::stopReason);
    builder.usage(toTokenUsage(message.usage()));

    return finalizeBuilder(builder);
  }

  private static AssistantMessage finalizeBuilder(AssistantMessageBuilder builder) {
    return builder.build();
  }

  @SuppressWarnings("unchecked")
  private static ToolCall toToolCall(String id, String name, JsonValue input) {
    final var arguments =
        Optional.ofNullable(input.convert(Map.class))
            .map(map -> (Map<String, Object>) map)
            .orElseGet(LinkedHashMap::new);
    return ToolCall.builder().id(id).name(name).arguments(arguments).build();
  }

  private static StopReason toStopReason(com.anthropic.models.messages.StopReason stopReason) {
    final var known = stopReason.known();
    if (known == null) {
      return null;
    }
    return switch (known) {
      case END_TURN, STOP_SEQUENCE -> StopReason.STOP;
      case MAX_TOKENS -> StopReason.LENGTH;
      case TOOL_USE -> StopReason.TOOL_USE;
      case PAUSE_TURN -> StopReason.STOP;
      case REFUSAL -> StopReason.CONTENT_FILTERED;
    };
  }

  private static AgentMetrics.TokenUsage toTokenUsage(Usage usage) {
    if (usage == null) {
      return AgentMetrics.TokenUsage.empty();
    }
    final var builder =
        AgentMetrics.TokenUsage.builder()
            .inputTokenCount((int) usage.inputTokens())
            .outputTokenCount((int) usage.outputTokens());
    usage.cacheReadInputTokens().ifPresent(v -> builder.cacheReadInputTokenCount(v.intValue()));
    usage
        .cacheCreationInputTokens()
        .ifPresent(v -> builder.cacheCreationInputTokenCount(v.intValue()));
    return builder.build();
  }

  private static ConnectorException wrapModelCallFailure(RuntimeException e) {
    final var message =
        Optional.ofNullable(e.getMessage())
            .filter(StringUtils::isNotBlank)
            .orElseGet(() -> e.getClass().getSimpleName());
    return new ConnectorException(
        ERROR_CODE_FAILED_MODEL_CALL, "Anthropic Messages call failed: %s".formatted(message), e);
  }
}
