/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlockParam;
import io.camunda.connector.agenticai.aiagent.framework.api.LlmProviderChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Maps a windowed {@link ConversationSnapshot} plus the resolved Anthropic model configuration to
 * an Anthropic SDK {@link MessageCreateParams} request, translating the domain {@link Message} /
 * {@link ToolCall} / {@link ToolCallResultContent} model into the wire shape via the {@link
 * AnthropicContentConverter} built for content blocks.
 */
public class AnthropicMessageRequestConverter {

  static final long DEFAULT_MAX_TOKENS = 4096L;

  private final AnthropicContentConverter contentConverter;

  public AnthropicMessageRequestConverter(AnthropicContentConverter contentConverter) {
    this.contentConverter = contentConverter;
  }

  public MessageCreateParams toMessageCreateParams(
      AgentExecutionContext ctx, ConversationSnapshot snapshot, ModelCapabilities capabilities) {
    final var cfg =
        (LlmProviderChatModelApiConfiguration) ctx.configuration().chatModelApiConfiguration();
    final var model = (AnthropicChatModel) cfg.configuration();
    final var params = model.anthropic().model().parameters();

    final var builder =
        MessageCreateParams.builder()
            .model(model.anthropic().model().model())
            .maxTokens(resolveMaxTokens(params, capabilities));

    applyModelParameters(builder, params);
    applySystemPrompt(builder, snapshot.messages());
    applyMessages(builder, snapshot.messages());
    applyTools(builder, snapshot.toolDefinitions());
    applyResponseFormat(builder, ctx.configuration().response());

    return builder.build();
  }

  private long resolveMaxTokens(
      @Nullable AnthropicModelParameters params, ModelCapabilities capabilities) {
    if (params != null && params.maxTokens() != null) {
      return params.maxTokens().longValue();
    }
    if (capabilities.maxOutputTokens() != null) {
      return capabilities.maxOutputTokens().longValue();
    }
    return DEFAULT_MAX_TOKENS;
  }

  // temperature()/topP()/topK() are deprecated in anthropic-java 2.48.0: models released after
  // Claude Opus 4.6 reject arbitrary values for these (a narrow backwards-compatible value is
  // still accepted), and newer models drop them entirely. The connector's model configuration
  // still exposes them for all the other, still-supported models, so keep mapping them; do not
  // remove.
  @SuppressWarnings("deprecation")
  private void applyModelParameters(
      MessageCreateParams.Builder builder, @Nullable AnthropicModelParameters params) {
    if (params == null) {
      return;
    }
    if (params.temperature() != null) {
      builder.temperature(params.temperature());
    }
    if (params.topP() != null) {
      builder.topP(params.topP());
    }
    if (params.topK() != null) {
      builder.topK(params.topK().longValue());
    }
  }

  private void applySystemPrompt(MessageCreateParams.Builder builder, List<Message> messages) {
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
      builder.system(system);
    }
  }

  private void applyMessages(MessageCreateParams.Builder builder, List<Message> messages) {
    // The SDK builder tracks `messages` as unset (not merely empty) until either `.messages(...)`
    // or `.addMessage(...)` is called at least once; `build()` then throws IllegalStateException
    // for an all-system (or otherwise empty) snapshot. Seed an empty list up front so `addMessage`
    // always has an initialized, mutable backing list to append to.
    builder.messages(List.of());
    for (final Message message : messages) {
      switch (message) {
        case SystemMessage ignored -> {} // hoisted to top-level system
        case UserMessage user ->
            builder.addMessage(
                MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(contentConverter.toContentBlockParams(user.content()))
                    .build());
        case AssistantMessage assistant -> builder.addMessage(assistantParam(assistant));
        case ToolCallResultMessage toolResults -> builder.addMessage(toolResultParam(toolResults));
        default ->
            throw new IllegalArgumentException(
                "Unsupported message type: " + message.getClass().getSimpleName());
      }
    }
  }

  private MessageParam assistantParam(AssistantMessage assistant) {
    final List<ContentBlockParam> blocks =
        new ArrayList<>(contentConverter.toContentBlockParams(assistant.content()));
    for (final ToolCall toolCall : assistant.toolCalls()) {
      blocks.add(
          ContentBlockParam.ofToolUse(
              ToolUseBlockParam.builder()
                  .id(toolCall.id())
                  .name(toolCall.name())
                  .input(toInput(toolCall.arguments()))
                  .build()));
    }
    return MessageParam.builder()
        .role(MessageParam.Role.ASSISTANT)
        .contentOfBlockParams(blocks)
        .build();
  }

  private MessageParam toolResultParam(ToolCallResultMessage message) {
    final List<ContentBlockParam> blocks = new ArrayList<>();
    for (final ToolCallResultContent result : message.results()) {
      blocks.add(
          ContentBlockParam.ofToolResult(
              ToolResultBlockParam.builder()
                  .toolUseId(result.id())
                  .contentOfBlocks(contentConverter.toToolResultBlocks(result.content()))
                  .build()));
    }
    return MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(blocks).build();
  }

  private void applyTools(
      MessageCreateParams.Builder builder, List<ToolDefinition> toolDefinitions) {
    for (final ToolDefinition definition : toolDefinitions) {
      final var toolBuilder =
          Tool.builder()
              .name(definition.name())
              .inputSchema(toInputSchema(definition.inputSchema()));
      if (definition.description() != null) {
        toolBuilder.description(definition.description());
      }
      builder.addTool(toolBuilder.build());
    }
  }

  private Tool.InputSchema toInputSchema(Map<String, Object> schema) {
    // input_schema is a JSON-schema object; feed properties/required/$defs/etc. through
    // additionalProperties so the whole schema serialises verbatim (the SDK owns "type": "object"
    // as a dedicated, validated field defaulting to that value, so it must be excluded here to
    // avoid emitting a duplicate "type" key).
    final Map<String, JsonValue> additional = new LinkedHashMap<>();
    schema.forEach(
        (k, v) -> {
          if (!"type".equals(k)) {
            additional.put(k, JsonValue.from(v));
          }
        });
    return Tool.InputSchema.builder().additionalProperties(additional).build();
  }

  private void applyResponseFormat(
      MessageCreateParams.Builder builder, @Nullable ResponseConfiguration response) {
    if (response == null
        || !(response.format() instanceof JsonResponseFormatConfiguration json)
        || json.schema() == null) {
      return; // TEXT / parseJson has no request-side effect (mirror the bridge)
    }
    final Map<String, JsonValue> schema = new LinkedHashMap<>();
    json.schema().forEach((k, v) -> schema.put(k, JsonValue.from(v)));
    builder.outputConfig(
        OutputConfig.builder()
            .format(
                JsonOutputFormat.builder()
                    .schema(JsonOutputFormat.Schema.builder().additionalProperties(schema).build())
                    .build())
            .build());
  }

  private ToolUseBlockParam.Input toInput(Map<String, Object> arguments) {
    final Map<String, JsonValue> converted = new LinkedHashMap<>();
    arguments.forEach((k, v) -> converted.put(k, JsonValue.from(v)));
    return ToolUseBlockParam.Input.builder().putAllAdditionalProperties(converted).build();
  }
}
