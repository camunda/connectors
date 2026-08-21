/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_UNSUPPORTED_MODEL_CONFIGURATION;

import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.ThinkingLevel;
import com.google.genai.types.Tool;
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
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiModel.GeminiModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiModel.GeminiThinking;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiModel.GeminiThinkingLevel;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.api.error.ConnectorException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Maps a windowed {@link ConversationSnapshot} plus the resolved Gemini model configuration to the
 * Gemini SDK's {@link GenerateContentConfig} (model parameters, thinking, system instruction,
 * tools, structured output) and {@link Content} history (message-by-message), translating the
 * domain {@link Message} / {@link ToolCall} / {@link ToolCallResultContent} model into the wire
 * shape via the {@link GeminiContentConverter} built for parts.
 *
 * <p>Two methods rather than one combined request object: {@code
 * GeminiChatModel#generateContentStream(model, contents, config)} (Task 6) takes contents and
 * config as separate arguments, so returning them separately avoids an intermediate wrapper type
 * with no other consumer.
 */
public class GeminiContentRequestConverter {

  private static final String ROLE_USER = "user";
  private static final String ROLE_MODEL = "model";

  private final GeminiContentConverter contentConverter;

  public GeminiContentRequestConverter(GeminiContentConverter contentConverter) {
    this.contentConverter = contentConverter;
  }

  public GenerateContentConfig toGenerateContentConfig(
      GeminiChatModelConfiguration configuration,
      @Nullable ResponseConfiguration response,
      ConversationSnapshot snapshot) {
    final var params = configuration.googleGemini().model().parameters();

    final var builder = GenerateContentConfig.builder();

    applyModelParameters(builder, params);
    applyThinking(builder, params);
    applySystemInstruction(builder, snapshot);
    applyTools(builder, snapshot.toolDefinitions());
    applyStructuredOutput(builder, response);

    return builder.build();
  }

  public List<Content> toContents(ConversationSnapshot snapshot) {
    // Seed an empty list so an all-system/empty snapshot returns empty rather than null.
    final List<Content> contents = new ArrayList<>();
    for (final Message message : snapshot.messages()) {
      switch (message) {
        case SystemMessage ignored -> {
          // hoisted to systemInstruction, see applySystemInstruction()
        }
        case UserMessage user ->
            addContent(contents, ROLE_USER, contentConverter.toParts(user.content()));
        case AssistantMessage assistant ->
            addContent(contents, ROLE_MODEL, assistantParts(assistant));
        case ToolCallResultMessage toolResults ->
            addContent(contents, ROLE_USER, toolResultParts(toolResults));
        default ->
            throw new IllegalArgumentException(
                "Unsupported message type: " + message.getClass().getSimpleName());
      }
    }
    return contents;
  }

  /**
   * Skips emitting a {@link Content} for a message that would otherwise carry an empty {@code
   * parts} list (e.g. a {@link UserMessage} or {@link AssistantMessage} with no content and, for
   * the latter, no tool calls) -- Gemini's own examples never send an empty parts array, and this
   * mirrors the Anthropic converter's care to never emit an empty content array.
   */
  private void addContent(List<Content> contents, String role, List<Part> parts) {
    if (parts.isEmpty()) {
      return;
    }
    contents.add(Content.builder().role(role).parts(parts).build());
  }

  private void applyModelParameters(
      GenerateContentConfig.Builder builder, @Nullable GeminiModelParameters params) {
    if (params == null) {
      return;
    }
    if (params.temperature() != null) {
      builder.temperature(params.temperature().floatValue());
    }
    if (params.topP() != null) {
      builder.topP(params.topP().floatValue());
    }
    if (params.topK() != null) {
      builder.topK(params.topK().floatValue());
    }
    if (params.maxTokens() != null) {
      builder.maxOutputTokens(params.maxTokens());
    }
  }

  /**
   * Maps {@code thinking} onto the SDK's {@link ThinkingConfig} when the modeler has opted in via
   * {@code enabled}. {@code thinkingBudget} xor an explicit {@code thinkingLevel}, never both;
   * {@link GeminiThinking#isBothThinkingBudgetAndLevelSet()}'s {@code @AssertFalse} bean validation
   * should already prevent both being set, but this is defended here too rather than silently
   * picking one.
   */
  private void applyThinking(
      GenerateContentConfig.Builder builder, @Nullable GeminiModelParameters params) {
    final GeminiThinking thinking = params == null ? null : params.thinking();
    if (thinking == null || !Boolean.TRUE.equals(thinking.enabled())) {
      return;
    }

    final Integer budget = thinking.thinkingBudget();
    // GeminiThinking's compact constructor already normalizes a null thinkingLevel to
    // MODEL_DEFAULT; this repeats the default defensively since the record component itself is
    // still typed @Nullable (a caller could in principle bypass the compact constructor).
    final GeminiThinkingLevel level =
        Objects.requireNonNullElse(thinking.thinkingLevel(), GeminiThinkingLevel.MODEL_DEFAULT);
    final boolean explicitLevel = level != GeminiThinkingLevel.MODEL_DEFAULT;
    if (budget != null && explicitLevel) {
      throw new ConnectorException(
          ERROR_CODE_UNSUPPORTED_MODEL_CONFIGURATION,
          "thinking.thinkingBudget and thinking.thinkingLevel are mutually exclusive");
    }

    // Thoughts are not returned unless explicitly asked for (per ThinkingConfig#includeThoughts:
    // "If true, thoughts are returned only if the model supports thought and thoughts are
    // available"); enabling thinking at all is the signal to also request them back. Matches
    // Anthropic, which always returns thinking content once thinking is enabled and only lets
    // ThinkingDisplay control how it is formatted.
    final var thinkingConfigBuilder = ThinkingConfig.builder().includeThoughts(true);
    if (budget != null) {
      thinkingConfigBuilder.thinkingBudget(budget);
    } else {
      thinkingConfigBuilder.thinkingLevel(toThinkingLevel(level));
    }
    builder.thinkingConfig(thinkingConfigBuilder.build());
  }

  private ThinkingLevel.Known toThinkingLevel(GeminiThinkingLevel level) {
    return switch (level) {
      case MODEL_DEFAULT -> ThinkingLevel.Known.THINKING_LEVEL_UNSPECIFIED;
      case MINIMAL -> ThinkingLevel.Known.MINIMAL;
      case LOW -> ThinkingLevel.Known.LOW;
      case MEDIUM -> ThinkingLevel.Known.MEDIUM;
      case HIGH -> ThinkingLevel.Known.HIGH;
    };
  }

  private void applySystemInstruction(
      GenerateContentConfig.Builder builder, ConversationSnapshot snapshot) {
    final var systemMessage = MessageUtil.leadingSystemMessage(snapshot.messages());
    if (systemMessage.isEmpty()) {
      return;
    }
    final List<Part> parts = contentConverter.toParts(systemMessage.get().content());
    if (parts.isEmpty()) {
      return;
    }
    builder.systemInstruction(Content.builder().parts(parts).build());
  }

  private void applyTools(
      GenerateContentConfig.Builder builder, List<ToolDefinition> toolDefinitions) {
    if (toolDefinitions.isEmpty()) {
      return;
    }
    final List<FunctionDeclaration> declarations = new ArrayList<>();
    for (final ToolDefinition definition : toolDefinitions) {
      final var declarationBuilder =
          FunctionDeclaration.builder()
              .name(definition.name())
              .parametersJsonSchema(definition.inputSchema());
      if (definition.description() != null) {
        declarationBuilder.description(definition.description());
      }
      declarations.add(declarationBuilder.build());
    }
    // A single Tool carrying every function declaration -- Gemini's own convention, not one Tool
    // per function (which the API rejects).
    builder.tools(Tool.builder().functionDeclarations(declarations).build());
  }

  /**
   * Maps the structured-output JSON schema via {@code responseJsonSchema}, the SDK's raw
   * JSON-Schema passthrough (as opposed to {@code responseSchema}, which requires translating into
   * the SDK's own restricted {@code Schema} type). This preserves the domain schema byte-for-byte,
   * mirroring the {@code additionalProperties} passthrough {@code
   * AnthropicMessageRequestConverter#applyOutputConfig} uses for the same field. Per the SDK's own
   * {@code responseJsonSchema} javadoc, {@code responseMimeType} must accompany it.
   */
  private void applyStructuredOutput(
      GenerateContentConfig.Builder builder, @Nullable ResponseConfiguration response) {
    if (!(response != null && response.format() instanceof JsonResponseFormatConfiguration json)) {
      return;
    }
    builder.responseMimeType("application/json");
    if (json.schema() != null) {
      builder.responseJsonSchema(json.schema());
    }
  }

  private List<Part> assistantParts(AssistantMessage assistant) {
    final List<Part> parts = new ArrayList<>(contentConverter.toParts(assistant.content()));
    for (final ToolCall toolCall : assistant.toolCalls()) {
      parts.add(toFunctionCallPart(toolCall));
    }
    return parts;
  }

  /**
   * Rebuilds a {@code functionCall} {@link Part} for replay, restoring the {@code thoughtSignature}
   * Gemini 3 stamps on the original call -- Gemini 3 rejects a follow-up tool-calling request whose
   * replayed {@code functionCall} dropped it. {@code
   * GeminiContentResponseConverter#toolCallMetadata} captures that signature onto {@link
   * ToolCall#metadata()}, namespaced under {@link GeminiChatModelConfiguration#GOOGLE_GEMINI_ID}
   * exactly like {@code ToolCallMetadataDecorator} does for the langchain4j path; this reads it
   * back from that same key. Absent for a Gemini 2.5 response (no signature to begin with) or a
   * conversation persisted before this was captured -- the field is omitted rather than failing in
   * that case.
   */
  private Part toFunctionCallPart(ToolCall toolCall) {
    final Part part =
        Part.builder()
            .functionCall(
                FunctionCall.builder()
                    .id(toolCall.id())
                    .name(toolCall.name())
                    .args(toolCall.arguments())
                    .build())
            .build();

    final byte @Nullable [] signature = thoughtSignatureBytes(toolCall.metadata());
    return signature == null ? part : part.toBuilder().thoughtSignature(signature).build();
  }

  private byte @Nullable [] thoughtSignatureBytes(@Nullable Map<String, Object> toolCallMetadata) {
    if (toolCallMetadata == null) {
      return null;
    }
    final Object namespaced = toolCallMetadata.get(GeminiChatModelConfiguration.GOOGLE_GEMINI_ID);
    if (!(namespaced instanceof Map<?, ?> namespacedMetadata)) {
      return null;
    }
    final Object signature =
        namespacedMetadata.get(GeminiContentConverter.THOUGHT_SIGNATURE_METADATA_KEY);
    if (signature == null) {
      return null;
    }
    if (signature instanceof byte[] bytes) {
      return bytes;
    }
    if (signature instanceof String base64) {
      return Base64.getDecoder().decode(base64);
    }
    throw new ConnectorException(
        ERROR_CODE_FAILED_MODEL_CALL,
        "Unsupported %s metadata value type '%s'"
            .formatted(
                GeminiContentConverter.THOUGHT_SIGNATURE_METADATA_KEY,
                signature.getClass().getSimpleName()));
  }

  private List<Part> toolResultParts(ToolCallResultMessage message) {
    final List<Part> parts = new ArrayList<>();
    for (final ToolCallResultContent result : message.results()) {
      parts.addAll(toolResultParts(result));
    }
    return parts;
  }

  /**
   * Converts a single tool result into {@link Part}s, stamping {@code name}/{@code id} onto the
   * {@code functionResponse} part(s) {@link GeminiContentConverter#toFunctionResponseParts(List)}
   * deliberately leaves blank (see that method's javadoc -- the correlating identity lives on this
   * {@link ToolCallResultContent}, one level up from the individual {@link
   * io.camunda.connector.agenticai.aiagent.model.message.content.Content} items it converts).
   *
   * <p>{@code toFunctionResponseParts} emits one {@link Part} per {@code Content} item, so a
   * multi-element result (e.g. two {@code TextContent}s) comes back as multiple sibling {@code
   * functionResponse} parts. Per that method's javadoc, these must be merged into a single {@code
   * functionResponse} rather than sent as siblings sharing one name/id -- {@link
   * #mergeFunctionResponseParts(List, String, String)} does that, combining their {@code response}
   * payloads. Non-{@code functionResponse} parts (e.g. a document's {@code inlineData}) pass
   * through unchanged; they carry no name/id to begin with.
   *
   * <p>A tool returning null/blank content normalizes to an empty {@code List<Content>} ({@link
   * ToolCallResultContent#contentFromObject}), which would otherwise produce no parts at all here,
   * leaving the preceding {@code functionCall} without a correlated response. Fall back to a single
   * {@link ToolCallResult#CONTENT_NO_RESULT} functionResponse in that case, same convention already
   * used for a canceled tool call.
   */
  private List<Part> toolResultParts(ToolCallResultContent result) {
    final List<Part> parts = contentConverter.toFunctionResponseParts(result.content());

    final List<Part> functionResponseParts =
        parts.stream().filter(part -> part.functionResponse().isPresent()).toList();
    final List<Part> otherParts =
        parts.stream().filter(part -> part.functionResponse().isEmpty()).toList();

    final List<Part> merged = new ArrayList<>();
    if (!functionResponseParts.isEmpty()) {
      merged.add(mergeFunctionResponseParts(functionResponseParts, result.name(), result.id()));
    } else if (otherParts.isEmpty()) {
      final var noResultParts =
          contentConverter.toFunctionResponseParts(
              List.of(TextContent.textContent(ToolCallResult.CONTENT_NO_RESULT)));
      merged.add(mergeFunctionResponseParts(noResultParts, result.name(), result.id()));
    }
    merged.addAll(otherParts);
    return merged;
  }

  private Part mergeFunctionResponseParts(
      List<Part> functionResponseParts, @Nullable String name, @Nullable String id) {
    final List<Object> outputs =
        functionResponseParts.stream()
            .map(
                part ->
                    part.functionResponse().orElseThrow().response().orElseThrow().get("output"))
            .toList();
    final Object output = outputs.size() == 1 ? outputs.get(0) : outputs;

    final var responseBuilder = FunctionResponse.builder().response(Map.of("output", output));
    if (name != null) {
      responseBuilder.name(name);
    }
    if (id != null) {
      responseBuilder.id(id);
    }
    return Part.builder().functionResponse(responseBuilder.build()).build();
  }
}
