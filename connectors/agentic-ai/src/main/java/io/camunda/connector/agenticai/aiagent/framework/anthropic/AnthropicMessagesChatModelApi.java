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
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.anthropic.models.messages.Usage;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatOptions;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatResponse;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatStreamListener;
import io.camunda.connector.agenticai.aiagent.framework.api.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.api.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.AssistantMessageBuilder;
import io.camunda.connector.agenticai.model.message.StopReason;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
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

  private static final ModelCapabilities CAPABILITIES =
      new ModelCapabilities(
          List.of(Modality.TEXT),
          List.of(Modality.TEXT),
          List.of(Modality.TEXT),
          false,
          false,
          false,
          true,
          null,
          null);

  private final AnthropicClient client;
  private final String model;
  @Nullable private final Long configuredMaxTokens;
  @Nullable private final Double temperature;
  @Nullable private final Double topP;
  @Nullable private final Long topK;

  public AnthropicMessagesChatModelApi(
      AnthropicClient client,
      String model,
      @Nullable Long configuredMaxTokens,
      @Nullable Double temperature,
      @Nullable Double topP,
      @Nullable Long topK) {
    this.client = Objects.requireNonNull(client, "client");
    this.model = Objects.requireNonNull(model, "model");
    this.configuredMaxTokens = configuredMaxTokens;
    this.temperature = temperature;
    this.topP = topP;
    this.topK = topK;
  }

  @Override
  public ModelCapabilities capabilities() {
    return CAPABILITIES;
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
    final var blocks = textOnlyBlocks(message.content());
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
    final var blocks =
        message.results().stream().map(AnthropicMessagesChatModelApi::toolResultBlock).toList();
    return MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(blocks).build();
  }

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

  private static ContentBlockParam toolResultBlock(ToolCallResult result) {
    final var b = ToolResultBlockParam.builder().toolUseId(result.id());
    final var content = result.content();
    if (content == null) {
      b.content(ToolCallResult.CONTENT_NO_RESULT);
    } else if (content instanceof String s) {
      b.content(s);
    } else {
      b.contentAsJson(content);
    }
    final var interrupted =
        result.properties() != null
            && Boolean.TRUE.equals(result.properties().get(ToolCallResult.PROPERTY_INTERRUPTED));
    if (interrupted) {
      b.isError(true);
    }
    return ContentBlockParam.ofToolResult(b.build());
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
