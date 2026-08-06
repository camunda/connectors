/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_UNSUPPORTED_MODEL_CONFIGURATION;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import com.anthropic.models.messages.ThinkingConfigEnabled;
import com.anthropic.models.messages.ThinkingConfigParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlockParam;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.MessageUtil;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicCustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel.AnthropicEffort;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel.AnthropicThinking;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel.ThinkingMode;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.api.error.ConnectorException;
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
      AnthropicChatModelConfiguration configuration,
      @Nullable ResponseConfiguration response,
      ConversationSnapshot snapshot) {
    final var connection = configuration.anthropic();
    final var params = connection.model().parameters();
    final String modelId = connection.model().model();

    final var builder =
        MessageCreateParams.builder().model(modelId).maxTokens(resolveMaxTokens(params));

    applyModelParameters(builder, params);
    applyThinking(builder, params, modelId);
    applySystemPrompt(builder, snapshot);
    applyMessages(builder, snapshot.messages());
    applyTools(builder, snapshot.toolDefinitions());
    applyOutputConfig(builder, params, response);
    applyPromptCaching(builder, params);
    applyRequestCustomizations(builder, connection);

    return builder.build();
  }

  private long resolveMaxTokens(@Nullable AnthropicModelParameters params) {
    if (params != null && params.maxTokens() != null) {
      return params.maxTokens().longValue();
    }
    return DEFAULT_MAX_TOKENS;
  }

  // temperature()/topP()/topK() are deprecated in the Anthropic SDK: models released after Claude
  // Opus 4.6 reject arbitrary values for these (a narrow backwards-compatible value is still
  // accepted), and newer models drop them entirely. The connector's model configuration still
  // exposes them for all the other, still-supported models, so keep mapping them; do not remove.
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

  /**
   * Maps the {@code thinking} configuration onto the SDK's {@code thinking} union. {@code mode ==
   * null} (the modeler left the dropdown blank) means unset - no thinking param is emitted and the
   * model's own default applies. Wire enum values use {@code name().toLowerCase()} ({@link
   * ThinkingMode}/{@code ThinkingDisplay} already carry matching lowercase {@code JsonProperty}
   * values, see those enums).
   */
  private void applyThinking(
      MessageCreateParams.Builder builder,
      @Nullable AnthropicModelParameters params,
      String modelId) {
    final var thinking = params == null ? null : params.thinking();
    validateThinking(thinking, modelId);

    final ThinkingMode mode = thinking == null ? null : thinking.mode();
    if (thinking == null || mode == null) {
      return;
    }

    switch (mode) {
      case ENABLED -> {
        if (thinking.budgetTokens() != null) {
          builder.thinking(
              ThinkingConfigParam.ofEnabled(
                  ThinkingConfigEnabled.builder()
                      .budgetTokens(thinking.budgetTokens().longValue())
                      .build()));
        }
      }
      case ADAPTIVE -> {
        final var adaptiveBuilder = ThinkingConfigAdaptive.builder();
        if (thinking.display() != null) {
          adaptiveBuilder.display(
              ThinkingConfigAdaptive.Display.of(thinking.display().name().toLowerCase()));
        }
        builder.thinking(ThinkingConfigParam.ofAdaptive(adaptiveBuilder.build()));
      }
      case DISABLED ->
          builder.thinking(
              ThinkingConfigParam.ofDisabled(ThinkingConfigDisabled.builder().build()));
    }
  }

  // The SDK's ThinkingConfigEnabled has no meaningful default budget, so ENABLED requires an
  // explicit budgetTokens value; a thinking object with a null mode is unset and never checked.
  private void validateThinking(@Nullable AnthropicThinking thinking, String modelId) {
    if (thinking != null
        && thinking.mode() == ThinkingMode.ENABLED
        && thinking.budgetTokens() == null) {
      throw new ConnectorException(
          ERROR_CODE_UNSUPPORTED_MODEL_CONFIGURATION,
          "Thinking mode ENABLED requires a budget tokens value for model '%s'".formatted(modelId));
    }
  }

  private void applySystemPrompt(
      MessageCreateParams.Builder builder, ConversationSnapshot snapshot) {
    final var systemMessage = MessageUtil.leadingSystemMessage(snapshot.messages());
    if (systemMessage.isEmpty()) {
      return;
    }
    final String system =
        systemMessage.get().content().stream()
            .filter(TextContent.class::isInstance)
            .map(c -> ((TextContent) c).text())
            .collect(Collectors.joining("\n"));
    if (!system.isBlank()) {
      builder.system(system);
    }
  }

  /**
   * Enables Anthropic automatic prompt caching: sets a single top-level {@code cache_control:
   * {"type":"ephemeral"}} so the API applies the cache breakpoint to the last cacheable block,
   * caching the whole prefix (system prompt + tool definitions + prior messages) with the default
   * 5-minute TTL. A cross-request cache hit requires a byte-identical prefix; the system prompt and
   * tools stay stable across turns, but the sliding message window shifts the message-history
   * portion of the prefix once it starts evicting the oldest messages.
   */
  private void applyPromptCaching(
      MessageCreateParams.Builder builder, @Nullable AnthropicModelParameters params) {
    final var promptCaching = params == null ? null : params.promptCaching();
    if (promptCaching != null && Boolean.TRUE.equals(promptCaching.enabled())) {
      builder.cacheControl(CacheControlEphemeral.builder().build());
    }
  }

  /**
   * Merges the backend's headers, query parameters, and body properties onto the request. The
   * {@code custom} backend exposes these as regular properties; the {@code anthropic-api} backend
   * exposes the same extension points as hidden properties for special scenarios not covered by the
   * modeler UI (e.g. routing through an intermediary that requires extra headers).
   */
  private void applyRequestCustomizations(
      MessageCreateParams.Builder builder, AnthropicConnection connection) {
    final RequestCustomizations customizations = requestCustomizations(connection.backend());
    if (customizations.headers() != null) {
      customizations.headers().forEach(builder::putAdditionalHeader);
    }
    if (customizations.queryParameters() != null) {
      customizations.queryParameters().forEach(builder::putAdditionalQueryParam);
    }
    if (customizations.bodyProperties() != null) {
      customizations
          .bodyProperties()
          .forEach((k, v) -> builder.putAdditionalBodyProperty(k, JsonValue.from(v)));
    }
  }

  private RequestCustomizations requestCustomizations(AnthropicBackend backend) {
    return switch (backend) {
      case AnthropicApiBackend apiBackend ->
          new RequestCustomizations(
              apiBackend.anthropic().headers(),
              apiBackend.anthropic().queryParameters(),
              apiBackend.anthropic().bodyProperties());
      case AnthropicCustomBackend custom ->
          new RequestCustomizations(
              custom.custom().headers(),
              custom.custom().queryParameters(),
              custom.custom().bodyProperties());
    };
  }

  private record RequestCustomizations(
      @Nullable Map<String, String> headers,
      @Nullable Map<String, String> queryParameters,
      @Nullable Map<String, Object> bodyProperties) {}

  private void applyMessages(MessageCreateParams.Builder builder, List<Message> messages) {
    // Seed an empty list so build() doesn't throw for an all-system/empty snapshot
    builder.messages(List.of());
    for (final Message message : messages) {
      if (message instanceof SystemMessage) {
        continue; // hoisted to top-level system
      }
      switch (message) {
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
      final var toolUseBuilder =
          ToolUseBlockParam.builder()
              .id(toolCall.id())
              .name(toolCall.name())
              .input(toInput(toolCall.arguments()));
      blocks.add(ContentBlockParam.ofToolUse(toolUseBuilder.build()));
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

  /**
   * Maps both the {@code effort} dial and the structured-output JSON schema onto the single {@code
   * output_config} field via one {@link OutputConfig} builder call: {@code outputConfig()} is a
   * plain setter that replaces the whole field, so two separate calls would drop whichever was set
   * first.
   */
  private void applyOutputConfig(
      MessageCreateParams.Builder builder,
      @Nullable AnthropicModelParameters params,
      @Nullable ResponseConfiguration response) {
    final AnthropicEffort effort = params == null ? null : params.effort();
    final Map<String, Object> jsonSchema =
        response != null && response.format() instanceof JsonResponseFormatConfiguration json
            ? json.schema()
            : null;

    if (effort == null && jsonSchema == null) {
      return;
    }

    final var outputConfigBuilder = OutputConfig.builder();

    if (effort != null) {
      outputConfigBuilder.effort(OutputConfig.Effort.of(effort.name().toLowerCase()));
    }

    if (jsonSchema != null) {
      // same additionalProperties passthrough as toInputSchema() above
      final Map<String, JsonValue> schema = new LinkedHashMap<>();
      jsonSchema.forEach((k, v) -> schema.put(k, JsonValue.from(v)));
      outputConfigBuilder.format(
          JsonOutputFormat.builder()
              .schema(JsonOutputFormat.Schema.builder().additionalProperties(schema).build())
              .build());
    }

    builder.outputConfig(outputConfigBuilder.build());
  }

  // ToolUseBlockParam.Input has no typed fields; arguments flow entirely through
  // additionalProperties.
  private ToolUseBlockParam.Input toInput(Map<String, Object> arguments) {
    final Map<String, JsonValue> converted = new LinkedHashMap<>();
    arguments.forEach((k, v) -> converted.put(k, JsonValue.from(v)));
    return ToolUseBlockParam.Input.builder().putAllAdditionalProperties(converted).build();
  }
}
