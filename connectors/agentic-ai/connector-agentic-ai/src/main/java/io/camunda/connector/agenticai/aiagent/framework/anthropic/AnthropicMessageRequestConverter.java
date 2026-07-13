/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaCodeExecutionTool20250825;
import com.anthropic.models.beta.messages.BetaContainerParams;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaSkillParams;
import com.anthropic.models.beta.messages.BetaTool;
import com.anthropic.models.beta.messages.BetaToolResultBlockParam;
import com.anthropic.models.beta.messages.BetaToolUseBlockParam;
import com.anthropic.models.beta.messages.BetaWebFetchTool20260318;
import com.anthropic.models.beta.messages.BetaWebSearchTool20260318;
import com.anthropic.models.beta.messages.MessageCreateParams;
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
 * an Anthropic SDK (beta messages client) {@link MessageCreateParams} request, translating the
 * domain {@link Message} / {@link ToolCall} / {@link ToolCallResultContent} model into the wire
 * shape via the {@link AnthropicContentConverter} built for content blocks.
 *
 * <p>Uses the <strong>beta</strong> messages client types (rather than the stable {@code
 * com.anthropic.models.messages} family) since the beta client is required for upcoming Skills
 * support; this migration is otherwise behavior-identical.
 */
public class AnthropicMessageRequestConverter {

  static final long DEFAULT_MAX_TOKENS = 4096L;

  // No typed com.anthropic.models.beta.AnthropicBeta constant exists for this beta identifier in
  // anthropic-java-core 2.48.0 (only the older CODE_EXECUTION_2025_05_22 is present); use the raw
  // string beta overload for this one and the typed enum for the other two skill-related betas.
  static final String CODE_EXECUTION_BETA = "code-execution-2025-08-25";

  // Web search and web fetch are General Availability, not beta, features as of anthropic-java
  // 2.48.0: both BetaWebSearchTool20260318/BetaWebFetchTool20260318 (used here) AND their
  // equivalents in the STABLE com.anthropic.models.messages package accept them directly, and no
  // com.anthropic.models.beta.AnthropicBeta constant (nor any raw string in the SDK sources)
  // references either tool. No anthropic-beta header is added for them. Using the newest (2026-03-
  // 18) tool revision of each, verified via javap against the same jar, since it is a strict
  // superset of the earlier revisions (response inclusion controls, still zero required builder
  // args) and both accept a no-arg builder().build() matching this connector's plain on/off toggle.

  /** Documented maximum number of skills that may be configured per request. */
  static final int MAX_SKILLS = 8;

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
    applySkillsAndBuiltInTools(builder, model.anthropic());

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
   * Wires the beta container with the configured Agent Skills, the built-in {@code
   * code_execution}/{@code web_search}/{@code web_fetch} server tools (auto-added for skills, or
   * added when their respective toggle is enabled), and the beta headers skills and code execution
   * need. A no-op (no container, no added tool, no beta headers) when no skills are configured and
   * every toggle is absent/false, keeping behavior identical to before Skills/tool-toggle support
   * was added.
   *
   * <p>{@code code_execution} is added and its beta header emitted AT MOST ONCE: skills
   * auto-require it to execute, and the explicit toggle may independently request it, but both
   * routes share this single addition so enabling both never emits a duplicate tool or beta.
   */
  private void applySkillsAndBuiltInTools(
      MessageCreateParams.Builder builder, AnthropicChatModel.AnthropicConnection connection) {
    final List<String> skills = connection.skills();
    final boolean hasSkills = skills != null && !skills.isEmpty();

    if (skills != null && !skills.isEmpty()) {
      if (skills.size() > MAX_SKILLS) {
        throw new IllegalArgumentException(
            "At most %d Anthropic skills may be configured, got %d"
                .formatted(MAX_SKILLS, skills.size()));
      }

      final var containerBuilder = BetaContainerParams.builder();
      for (final String raw : skills) {
        final AnthropicSkillReference skill = AnthropicSkillReference.parse(raw);
        containerBuilder.addSkill(
            BetaSkillParams.builder()
                .type(skill.type())
                .skillId(skill.skillId())
                .version(skill.version())
                .build());
      }
      builder.container(containerBuilder.build());

      builder.addBeta(AnthropicBeta.SKILLS_2025_10_02).addBeta(AnthropicBeta.FILES_API_2025_04_14);
    }

    // Skills execute inside the beta container via the code_execution tool; auto-add it so users
    // don't have to separately enable it. An explicit "enable code execution" toggle requests the
    // same tool independently. Route both through this single addition so the tool/beta are never
    // duplicated when skills are configured AND the toggle is enabled.
    if (hasSkills || Boolean.TRUE.equals(connection.enableCodeExecution())) {
      builder.addTool(BetaCodeExecutionTool20250825.builder().build());
      builder.addBeta(CODE_EXECUTION_BETA);
    }

    if (Boolean.TRUE.equals(connection.enableWebSearch())) {
      builder.addTool(BetaWebSearchTool20260318.builder().build());
    }

    if (Boolean.TRUE.equals(connection.enableWebFetch())) {
      builder.addTool(BetaWebFetchTool20260318.builder().build());
    }
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
        BetaOutputConfig.builder()
            .format(
                BetaJsonOutputFormat.builder()
                    .schema(
                        BetaJsonOutputFormat.Schema.builder().additionalProperties(schema).build())
                    .build())
            .build());
  }

  private BetaToolUseBlockParam.Input toInput(Map<String, Object> arguments) {
    final Map<String, JsonValue> converted = new LinkedHashMap<>();
    arguments.forEach((k, v) -> converted.put(k, JsonValue.from(v)));
    return BetaToolUseBlockParam.Input.builder().putAllAdditionalProperties(converted).build();
  }
}
