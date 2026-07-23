/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaThinkingConfigAdaptive;
import com.anthropic.models.beta.messages.BetaThinkingConfigDisabled;
import com.anthropic.models.beta.messages.BetaThinkingConfigEnabled;
import com.anthropic.models.beta.messages.BetaThinkingConfigParam;
import com.anthropic.models.beta.messages.BetaTool;
import com.anthropic.models.beta.messages.BetaToolResultBlockParam;
import com.anthropic.models.beta.messages.BetaToolUseBlockParam;
import com.anthropic.models.beta.messages.MessageCreateParams;
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
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicCompatibleBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel.AnthropicModelParameters;
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
 * an Anthropic SDK (beta messages client) {@link MessageCreateParams} request, translating the
 * domain {@link Message} / {@link ToolCall} / {@link ToolCallResultContent} model into the wire
 * shape via the {@link AnthropicContentConverter} built for content blocks.
 *
 * <p>Uses the <strong>beta</strong> messages client types (rather than the stable {@code
 * com.anthropic.models.messages} family) for output_config effort support; behavior is otherwise
 * unaffected by that choice.
 */
public class AnthropicMessageRequestConverter {

  static final long DEFAULT_MAX_TOKENS = 4096L;

  private final AnthropicContentConverter contentConverter;

  public AnthropicMessageRequestConverter(AnthropicContentConverter contentConverter) {
    this.contentConverter = contentConverter;
  }

  public MessageCreateParams toMessageCreateParams(
      AgentExecutionContext ctx, ConversationSnapshot snapshot) {
    final var model = (AnthropicChatModelConfiguration) ctx.configuration().chatModel();
    final var connection = model.anthropic();
    final var params = connection.model().parameters();
    final String modelId = connection.model().model();

    final var builder =
        MessageCreateParams.builder().model(modelId).maxTokens(resolveMaxTokens(params));

    applyModelParameters(builder, params);
    applyReasoning(builder, params, modelId);
    applySystemPrompt(builder, snapshot.messages());
    applyMessages(builder, snapshot.messages());
    applyTools(builder, snapshot.toolDefinitions());
    applyOutputConfig(builder, ctx.configuration().response(), params);
    applyPromptCaching(builder, connection);
    applyCompatibleRequestParameters(builder, connection);

    return builder.build();
  }

  private long resolveMaxTokens(@Nullable AnthropicModelParameters params) {
    if (params != null && params.maxTokens() != null) {
      return params.maxTokens().longValue();
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

  /**
   * Maps the {@code thinking} configuration onto the SDK's {@code thinking} union, after the one
   * surviving structural check (see {@link AnthropicReasoningValidator}). {@code mode == null} (the
   * modeler left the dropdown blank) means unset - no thinking param is emitted and the model's own
   * default applies. Wire enum values use {@code name().toLowerCase()} ({@link ThinkingMode}/{@code
   * ThinkingDisplay} already carry matching lowercase {@code JsonProperty} values, see those
   * enums).
   */
  private void applyReasoning(
      MessageCreateParams.Builder builder,
      @Nullable AnthropicModelParameters params,
      String modelId) {
    final var thinking = params == null ? null : params.thinking();
    AnthropicReasoningValidator.validate(thinking, modelId);

    final ThinkingMode mode = thinking == null ? null : thinking.mode();
    if (thinking == null || mode == null) {
      return;
    }

    switch (mode) {
      case ENABLED -> {
        if (thinking.budgetTokens() != null) {
          builder.thinking(
              BetaThinkingConfigParam.ofEnabled(
                  BetaThinkingConfigEnabled.builder()
                      .budgetTokens(thinking.budgetTokens().longValue())
                      .build()));
        }
      }
      case ADAPTIVE -> {
        final var adaptiveBuilder = BetaThinkingConfigAdaptive.builder();
        if (thinking.display() != null) {
          adaptiveBuilder.display(
              BetaThinkingConfigAdaptive.Display.of(thinking.display().name().toLowerCase()));
        }
        builder.thinking(BetaThinkingConfigParam.ofAdaptive(adaptiveBuilder.build()));
      }
      case DISABLED ->
          builder.thinking(
              BetaThinkingConfigParam.ofDisabled(BetaThinkingConfigDisabled.builder().build()));
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

  /**
   * Enables Anthropic automatic prompt caching: sets a single top-level {@code cache_control:
   * {"type":"ephemeral"}} so the API automatically applies the cache breakpoint to the last
   * cacheable block, caching the whole prefix (system prompt + tool definitions + prior messages)
   * with the default 5-minute TTL. No {@code anthropic-beta} header or per-block marker is
   * required. A cross-request cache hit requires a byte-identical prefix; the system prompt and
   * tools stay stable across turns, but the sliding message window shifts the message-history
   * portion of the prefix once it starts evicting the oldest messages.
   */
  private void applyPromptCaching(
      MessageCreateParams.Builder builder, AnthropicConnection connection) {
    if (Boolean.TRUE.equals(connection.enablePromptCaching())) {
      builder.cacheControl(BetaCacheControlEphemeral.builder().build());
    }
  }

  /**
   * Merges the {@code compatible} backend's raw request (body) parameters onto the built request
   * (e.g. vendor-specific extensions unsupported by the SDK's typed builder). {@code headers}/
   * {@code queryParameters} on the same backend are applied at the client level (see {@code
   * AnthropicOkHttpClientFactory}), but body parameters can only be merged here, at request-
   * building time.
   */
  private void applyCompatibleRequestParameters(
      MessageCreateParams.Builder builder, AnthropicConnection connection) {
    if (connection.backend() instanceof AnthropicCompatibleBackend compatible
        && compatible.requestParameters() != null) {
      compatible
          .requestParameters()
          .forEach((k, v) -> builder.putAdditionalBodyProperty(k, JsonValue.from(v)));
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
                BetaMessageParam.builder()
                    .role(BetaMessageParam.Role.USER)
                    .contentOfBetaContentBlockParams(
                        contentConverter.toContentBlockParams(user.content()))
                    .build());
        case AssistantMessage assistant -> builder.addMessage(assistantParam(assistant));
        case ToolCallResultMessage toolResults -> builder.addMessage(toolResultParam(toolResults));
        default ->
            throw new IllegalArgumentException(
                "Unsupported message type: " + message.getClass().getSimpleName());
      }
    }
  }

  // Known limitation: `content` (including any ProviderContent server-tool blocks, e.g.
  // server_tool_use/code_execution_tool_result, in their original order) is always emitted BEFORE
  // `toolCalls` (client tool_use blocks) below, since the domain model splits an assistant
  // message's server-tool blocks and client tool calls into two separate ordered lists that don't
  // record their relative position. A response that interleaves a client tool_use BETWEEN two
  // server blocks therefore cannot be replayed with that exact interleaving on the request side.
  // No known real Anthropic scenario interleaves this way -- server blocks and client tool_use
  // blocks are documented as appearing in separate, non-interleaved groups -- so this grouping is
  // intentional; only restructure if a genuine interleaving case surfaces (see
  // AnthropicMessageRequestConverterTest#appendsClientToolCallsAfterProviderContentBlocksRegardlessOfOriginalInterleaving).
  private BetaMessageParam assistantParam(AssistantMessage assistant) {
    final List<BetaContentBlockParam> blocks =
        new ArrayList<>(contentConverter.toContentBlockParams(assistant.content()));
    for (final ToolCall toolCall : assistant.toolCalls()) {
      blocks.add(
          BetaContentBlockParam.ofToolUse(
              BetaToolUseBlockParam.builder()
                  .id(toolCall.id())
                  .name(toolCall.name())
                  .input(toInput(toolCall.arguments()))
                  .build()));
    }
    return BetaMessageParam.builder()
        .role(BetaMessageParam.Role.ASSISTANT)
        .contentOfBetaContentBlockParams(blocks)
        .build();
  }

  private BetaMessageParam toolResultParam(ToolCallResultMessage message) {
    final List<BetaContentBlockParam> blocks = new ArrayList<>();
    for (final ToolCallResultContent result : message.results()) {
      blocks.add(
          BetaContentBlockParam.ofToolResult(
              BetaToolResultBlockParam.builder()
                  .toolUseId(result.id())
                  .contentOfBlocks(contentConverter.toToolResultBlocks(result.content()))
                  .build()));
    }
    return BetaMessageParam.builder()
        .role(BetaMessageParam.Role.USER)
        .contentOfBetaContentBlockParams(blocks)
        .build();
  }

  private void applyTools(
      MessageCreateParams.Builder builder, List<ToolDefinition> toolDefinitions) {
    for (final ToolDefinition definition : toolDefinitions) {
      final var toolBuilder =
          BetaTool.builder()
              .name(definition.name())
              .inputSchema(toInputSchema(definition.inputSchema()));
      if (definition.description() != null) {
        toolBuilder.description(definition.description());
      }
      builder.addTool(toolBuilder.build());
    }
  }

  private BetaTool.InputSchema toInputSchema(Map<String, Object> schema) {
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
    return BetaTool.InputSchema.builder().additionalProperties(additional).build();
  }

  /**
   * Maps both the structured-output JSON schema and the {@code effort} dial onto the single {@code
   * output_config} field. Both are built into ONE {@link BetaOutputConfig} and applied via a single
   * {@code builder.outputConfig} call: {@code
   * MessageCreateParams.Builder#outputConfig(BetaOutputConfig)} is a plain setter that replaces the
   * whole field, so two separate calls (one for the schema, one for effort) would silently drop
   * whichever was set first whenever both are configured together.
   */
  private void applyOutputConfig(
      MessageCreateParams.Builder builder,
      @Nullable ResponseConfiguration response,
      @Nullable AnthropicModelParameters params) {
    final Map<String, Object> jsonSchema =
        response != null && response.format() instanceof JsonResponseFormatConfiguration json
            ? json.schema()
            : null;
    final AnthropicEffort effort = params == null ? null : params.effort();

    if (jsonSchema == null && effort == null) {
      return; // TEXT / parseJson with no effort has no request-side effect (mirrors the
      // LangChain4j-routed path)
    }

    final var outputConfigBuilder = BetaOutputConfig.builder();

    if (jsonSchema != null) {
      final Map<String, JsonValue> schema = new LinkedHashMap<>();
      jsonSchema.forEach((k, v) -> schema.put(k, JsonValue.from(v)));
      outputConfigBuilder.format(
          BetaJsonOutputFormat.builder()
              .schema(BetaJsonOutputFormat.Schema.builder().additionalProperties(schema).build())
              .build());
    }

    if (effort != null) {
      outputConfigBuilder.effort(BetaOutputConfig.Effort.of(effort.name().toLowerCase()));
    }

    builder.outputConfig(outputConfigBuilder.build());
  }

  private BetaToolUseBlockParam.Input toInput(Map<String, Object> arguments) {
    final Map<String, JsonValue> converted = new LinkedHashMap<>();
    arguments.forEach((k, v) -> converted.put(k, JsonValue.from(v)));
    return BetaToolUseBlockParam.Input.builder().putAllAdditionalProperties(converted).build();
  }
}
