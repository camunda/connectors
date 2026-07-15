/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaCodeExecutionTool20250522;
import com.anthropic.models.beta.messages.BetaCodeExecutionTool20250825;
import com.anthropic.models.beta.messages.BetaCodeExecutionTool20260120;
import com.anthropic.models.beta.messages.BetaCodeExecutionTool20260521;
import com.anthropic.models.beta.messages.BetaContainerParams;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaSkillParams;
import com.anthropic.models.beta.messages.BetaThinkingConfigAdaptive;
import com.anthropic.models.beta.messages.BetaThinkingConfigDisabled;
import com.anthropic.models.beta.messages.BetaThinkingConfigEnabled;
import com.anthropic.models.beta.messages.BetaThinkingConfigParam;
import com.anthropic.models.beta.messages.BetaTool;
import com.anthropic.models.beta.messages.BetaToolResultBlockParam;
import com.anthropic.models.beta.messages.BetaToolUseBlockParam;
import com.anthropic.models.beta.messages.BetaWebFetchTool20250910;
import com.anthropic.models.beta.messages.BetaWebFetchTool20260209;
import com.anthropic.models.beta.messages.BetaWebFetchTool20260309;
import com.anthropic.models.beta.messages.BetaWebFetchTool20260318;
import com.anthropic.models.beta.messages.BetaWebSearchTool20250305;
import com.anthropic.models.beta.messages.BetaWebSearchTool20260209;
import com.anthropic.models.beta.messages.BetaWebSearchTool20260318;
import com.anthropic.models.beta.messages.MessageCreateParams;
import io.camunda.connector.agenticai.aiagent.framework.api.LlmProviderChatModelApiConfiguration;
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
import java.util.Objects;
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

  // Built-in server tools (code_execution / web_search / web_fetch) are user-version-configurable
  // because the revision determines calling behavior and header requirements. Defaults track the
  // latest GA/dynamic revisions; the version fields let users downgrade for ZDR or older models.
  //
  // code_execution: the GA revisions (20250825/20260120/20260521) need NO anthropic-beta header;
  // only the legacy Python-only 20250522 requires code-execution-2025-05-22. The default 20260521
  // (>= 20260120) is also what lets the default dynamic-filtering web tools run in the same request
  // (from 20260209 the web tools default allowedCallers to ["code_execution_20260120"], sharing the
  // container; providing an OLDER code_execution alongside them is rejected by the API). The same
  // resolved version applies whether code execution is enabled explicitly or implicitly via Skills.
  //
  // web_search/web_fetch are General Availability (no beta header at any revision). The default
  // dynamic-filtering revisions (20260318) run inside code execution; the basic/direct revisions
  // (20250305/20250910) call directly, are ZDR-eligible, and work on all models (downgrade path).
  //
  // Unrecognized versions fall back to the raw-type escape hatch: the latest typed builder with
  // `.type(JsonValue.from(raw))` overridden. Verified via a serialization round-trip (see the
  // converter test) that the generated build() does not validate and `name` defaults independently
  // of `type`, so this produces `{"type":"<raw>","name":"<tool>"}` without any other field
  // changing.
  static final String CODE_EXECUTION_DEFAULT_VERSION = "code_execution_20260521";

  static final String WEB_SEARCH_BASIC_VERSION = "web_search_20250305";

  static final String WEB_SEARCH_DEFAULT_VERSION = "web_search_20260318";

  static final String WEB_FETCH_BASIC_VERSION = "web_fetch_20250910";

  static final String WEB_FETCH_DEFAULT_VERSION = "web_fetch_20260318";

  /** Documented maximum number of skills that may be configured per request. */
  static final int MAX_SKILLS = 8;

  private final AnthropicContentConverter contentConverter;

  public AnthropicMessageRequestConverter(AnthropicContentConverter contentConverter) {
    this.contentConverter = contentConverter;
  }

  /**
   * Convenience overload for callers that don't track the model-matched signal (see the {@code
   * modelMatched} overload): treats the model as matched, so reasoning validation (spec §6, rule 1)
   * applies as normal whenever thinking/effort is actually configured. Every production caller goes
   * through the {@code modelMatched} overload instead, threading the real signal from {@link
   * io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilitiesResolver#matches}.
   */
  public MessageCreateParams toMessageCreateParams(
      AgentExecutionContext ctx,
      ConversationSnapshot snapshot,
      AnthropicModelCapabilities capabilities) {
    return toMessageCreateParams(ctx, snapshot, capabilities, true);
  }

  public MessageCreateParams toMessageCreateParams(
      AgentExecutionContext ctx,
      ConversationSnapshot snapshot,
      AnthropicModelCapabilities capabilities,
      boolean modelMatched) {
    final var cfg =
        (LlmProviderChatModelApiConfiguration) ctx.configuration().chatModelApiConfiguration();
    final var model = (AnthropicChatModel) cfg.configuration();
    final var params = model.anthropic().model().parameters();
    final String modelId = model.anthropic().model().model();

    AnthropicReasoningValidator.validate(params, capabilities.reasoning(), modelMatched, modelId);

    final var builder =
        MessageCreateParams.builder()
            .model(modelId)
            .maxTokens(resolveMaxTokens(params, capabilities));

    applyModelParameters(builder, params);
    applyReasoning(builder, params);
    applySystemPrompt(builder, snapshot.messages());
    applyMessages(builder, snapshot.messages());
    applyTools(builder, snapshot.toolDefinitions());
    applyOutputConfig(builder, ctx.configuration().response(), params);
    applySkillsAndBuiltInTools(builder, model.anthropic());

    return builder.build();
  }

  private long resolveMaxTokens(
      @Nullable AnthropicModelParameters params, AnthropicModelCapabilities capabilities) {
    if (params != null && params.maxTokens() != null) {
      return params.maxTokens().longValue();
    }
    if (capabilities.core().maxOutputTokens() != null) {
      return capabilities.core().maxOutputTokens().longValue();
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
   * Maps validated {@code thinking} configuration (spec §5) onto the SDK's {@code thinking} union.
   * {@code mode == null} (the modeler left the dropdown blank) means unset - no thinking param is
   * emitted and the model's own default applies. Wire enum values use {@code name().toLowerCase()}
   * ({@code ThinkingMode}/{@code ThinkingDisplay} already carry matching lowercase {@code
   * JsonProperty} values, see those enums).
   *
   * <p>Defensively skips emitting {@code ENABLED} without a budget rather than throwing an NPE: for
   * a matched model {@link AnthropicReasoningValidator} already guarantees a budget is present
   * (rule 3), but an unmatched/custom model bypasses that validation entirely, so this guard is the
   * only thing standing between a misconfigured custom model and a builder-time crash.
   */
  private void applyReasoning(
      MessageCreateParams.Builder builder, @Nullable AnthropicModelParameters params) {
    final var thinking = params == null ? null : params.thinking();
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
  // No known real Anthropic Skills/code-execution scenario interleaves this way -- server blocks
  // and client tool_use blocks are documented as appearing in separate, non-interleaved groups --
  // so this grouping is intentional; only restructure if a genuine interleaving case surfaces (see
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
   * Wires the beta container with the configured Agent Skills, the built-in {@code
   * code_execution}/{@code web_search}/{@code web_fetch} server tools (auto-added for skills, or
   * added when their respective toggle is enabled), and the beta headers skills and code execution
   * need. A no-op (no container, no added tool, no beta headers) when no skills are configured and
   * every toggle is absent/false, keeping behavior identical to before Skills/tool-toggle support
   * was added.
   *
   * <p>{@code code_execution} is added AT MOST ONCE (skills auto-require it; the explicit toggle
   * may independently request it; both share this single addition), at the configured version, so
   * enabling both never duplicates the tool.
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
      addCodeExecutionTool(builder, connection.codeExecutionVersion());
    }

    if (Boolean.TRUE.equals(connection.enableWebSearch())) {
      addWebSearchTool(builder, connection.webSearchVersion());
    }

    if (Boolean.TRUE.equals(connection.enableWebFetch())) {
      addWebFetchTool(builder, connection.webFetchVersion());
    }
  }

  /**
   * Adds the {@code code_execution} server tool for the given (optional) version string, defaulting
   * to the latest GA revision (see the class-level comment). Only the legacy {@code 20250522}
   * revision requires a beta header; the GA revisions emit none. Unknown versions use the raw-type
   * escape hatch on the latest revision's builder.
   */
  private void addCodeExecutionTool(MessageCreateParams.Builder builder, @Nullable String version) {
    final String resolved =
        (version == null || version.isBlank()) ? CODE_EXECUTION_DEFAULT_VERSION : version;
    switch (resolved) {
      case "code_execution_20250522" -> {
        builder.addTool(BetaCodeExecutionTool20250522.builder().build());
        builder.addBeta(AnthropicBeta.CODE_EXECUTION_2025_05_22);
      }
      case "code_execution_20250825" ->
          builder.addTool(BetaCodeExecutionTool20250825.builder().build());
      case "code_execution_20260120" ->
          builder.addTool(BetaCodeExecutionTool20260120.builder().build());
      case CODE_EXECUTION_DEFAULT_VERSION ->
          builder.addTool(BetaCodeExecutionTool20260521.builder().build());
      default ->
          builder.addTool(
              BetaCodeExecutionTool20260521.builder().type(JsonValue.from(resolved)).build());
    }
  }

  /**
   * Adds the {@code web_search} server tool for the given (optional) version string, defaulting to
   * the latest dynamic-filtering revision (see the class-level comment above). Unknown versions use
   * the raw-type escape hatch on the base revision's builder.
   */
  private void addWebSearchTool(MessageCreateParams.Builder builder, @Nullable String version) {
    final String resolved =
        (version == null || version.isBlank()) ? WEB_SEARCH_DEFAULT_VERSION : version;
    switch (resolved) {
      case WEB_SEARCH_BASIC_VERSION -> builder.addTool(BetaWebSearchTool20250305.builder().build());
      case "web_search_20260209" -> builder.addTool(BetaWebSearchTool20260209.builder().build());
      case "web_search_20260318" -> builder.addTool(BetaWebSearchTool20260318.builder().build());
      default ->
          builder.addTool(
              BetaWebSearchTool20250305.builder().type(JsonValue.from(resolved)).build());
    }
  }

  /**
   * Adds the {@code web_fetch} server tool for the given (optional) version string, defaulting to
   * the latest dynamic-filtering revision (see the class-level comment above). Unknown versions use
   * the raw-type escape hatch on the base revision's builder.
   */
  private void addWebFetchTool(MessageCreateParams.Builder builder, @Nullable String version) {
    final String resolved =
        (version == null || version.isBlank()) ? WEB_FETCH_DEFAULT_VERSION : version;
    switch (resolved) {
      case WEB_FETCH_BASIC_VERSION -> builder.addTool(BetaWebFetchTool20250910.builder().build());
      case "web_fetch_20260209" -> builder.addTool(BetaWebFetchTool20260209.builder().build());
      case "web_fetch_20260309" -> builder.addTool(BetaWebFetchTool20260309.builder().build());
      case "web_fetch_20260318" -> builder.addTool(BetaWebFetchTool20260318.builder().build());
      default ->
          builder.addTool(
              BetaWebFetchTool20250910.builder().type(JsonValue.from(resolved)).build());
    }
  }

  /**
   * Maps both the structured-output JSON schema (spec: unchanged from before this task) and the
   * validated {@code effort} dial (spec §5) onto the single {@code output_config} field. Both are
   * built into ONE {@link BetaOutputConfig} and applied via a single {@code builder.outputConfig}
   * call: {@code MessageCreateParams.Builder#outputConfig(BetaOutputConfig)} is a plain setter that
   * replaces the whole field, so two separate calls (one for the schema, one for effort) would
   * silently drop whichever was set first whenever both are configured together.
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
      return; // TEXT / parseJson with no effort has no request-side effect (mirror the bridge)
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
      final String customEffort = params == null ? null : params.customEffort();
      final BetaOutputConfig.Effort wireEffort =
          effort == AnthropicEffort.CUSTOM
              ? BetaOutputConfig.Effort.of(Objects.requireNonNullElse(customEffort, ""))
              : BetaOutputConfig.Effort.of(effort.name().toLowerCase());
      outputConfigBuilder.effort(wireEffort);
    }

    builder.outputConfig(outputConfigBuilder.build());
  }

  private BetaToolUseBlockParam.Input toInput(Map<String, Object> arguments) {
    final Map<String, JsonValue> converted = new LinkedHashMap<>();
    arguments.forEach((k, v) -> converted.put(k, JsonValue.from(v)));
    return BetaToolUseBlockParam.Input.builder().putAllAdditionalProperties(converted).build();
  }
}
