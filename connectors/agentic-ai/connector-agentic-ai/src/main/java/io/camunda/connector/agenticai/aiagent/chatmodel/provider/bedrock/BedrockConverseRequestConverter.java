/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_UNSUPPORTED_MODEL_CONFIGURATION;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.MessageUtil;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseModel.BedrockConverseModelParameters;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.api.error.ConnectorException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.services.bedrockruntime.model.CachePointBlock;
import software.amazon.awssdk.services.bedrockruntime.model.CachePointType;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.JsonSchemaDefinition;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.OutputConfig;
import software.amazon.awssdk.services.bedrockruntime.model.OutputFormat;
import software.amazon.awssdk.services.bedrockruntime.model.OutputFormatStructure;
import software.amazon.awssdk.services.bedrockruntime.model.OutputFormatType;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * Maps a windowed {@link ConversationSnapshot} plus the resolved Bedrock model configuration to a
 * Bedrock Converse SDK {@link ConverseStreamRequest}, translating the domain {@code Message} /
 * {@link ToolCall} / {@link ToolCallResultContent} model into the wire shape via the {@link
 * BedrockConverseContentConverter} built for content blocks.
 *
 * <p>Note: {@code software.amazon.awssdk.core.document.Document} (the generic AWS "any JSON value"
 * type used for tool input schemas and {@code additionalModelRequestFields}) is always fully
 * qualified below, and {@code software.amazon.awssdk.services.bedrockruntime.model.Message} (this
 * converter's wire-level output) is imported unqualified, since both the AWS SDK and this module's
 * domain model each have a type literally named {@code Message}; the domain type is never
 * referenced by name here (only via {@link ConversationSnapshot#messages()} and {@code var}).
 */
public class BedrockConverseRequestConverter {

  private final BedrockConverseContentConverter contentConverter;
  private final ObjectMapper objectMapper;

  public BedrockConverseRequestConverter(
      BedrockConverseContentConverter contentConverter, ObjectMapper objectMapper) {
    this.contentConverter = contentConverter;
    this.objectMapper = objectMapper;
  }

  public ConverseStreamRequest toConverseStreamRequest(
      BedrockConverseChatModelConfiguration configuration,
      @Nullable ResponseConfiguration response,
      ConversationSnapshot snapshot) {
    final var connection = configuration.bedrock();
    final var model = connection.model();
    final var params = model.parameters();

    final var builder = ConverseStreamRequest.builder().modelId(model.model());

    applyInferenceConfig(builder, params);

    final List<SystemContentBlock> system = buildSystemPrompt(snapshot);
    final List<Message> messages = buildMessages(snapshot);
    final List<Tool> tools = buildTools(snapshot.toolDefinitions());

    // Mutates system/tools/messages in place before they're attached to the builder below, so the
    // checkpoint placement can inspect the fully-built prefix - a single
    // sequential pass of builder calls can't express "checkpoint at the end of whichever of
    // system/tools is present" without first knowing both are final.
    applyPromptCaching(params, system, tools, messages);

    if (!system.isEmpty()) {
      builder.system(system);
    }
    builder.messages(messages);
    if (!tools.isEmpty()) {
      builder.toolConfig(ToolConfiguration.builder().tools(tools).build());
    }

    applyOutputConfig(builder, response);
    applyAdditionalModelRequestFields(builder, connection);
    applyOverrideConfiguration(builder, connection);

    return builder.build();
  }

  // temperature()/topP() narrow Double -> Float at this mapping site; maxTokens
  // stays Integer -> Integer. Each is applied only when non-null, independently of the others.
  private void applyInferenceConfig(
      ConverseStreamRequest.Builder builder, @Nullable BedrockConverseModelParameters params) {
    if (params == null) {
      return;
    }

    final var inferenceConfigBuilder = InferenceConfiguration.builder();
    boolean any = false;
    if (params.maxTokens() != null) {
      inferenceConfigBuilder.maxTokens(params.maxTokens());
      any = true;
    }
    if (params.temperature() != null) {
      inferenceConfigBuilder.temperature(params.temperature().floatValue());
      any = true;
    }
    if (params.topP() != null) {
      inferenceConfigBuilder.topP(params.topP().floatValue());
      any = true;
    }
    if (any) {
      builder.inferenceConfig(inferenceConfigBuilder.build());
    }
  }

  private List<SystemContentBlock> buildSystemPrompt(ConversationSnapshot snapshot) {
    final List<SystemContentBlock> system = new ArrayList<>();
    final var systemMessage = MessageUtil.leadingSystemMessage(snapshot.messages());
    if (systemMessage.isEmpty()) {
      return system;
    }

    final String text =
        systemMessage.get().content().stream()
            .filter(TextContent.class::isInstance)
            .map(c -> ((TextContent) c).text())
            .collect(Collectors.joining("\n"));
    if (!text.isBlank()) {
      system.add(SystemContentBlock.fromText(text));
    }
    return system;
  }

  private List<Message> buildMessages(ConversationSnapshot snapshot) {
    final List<Message> messages = new ArrayList<>();
    for (final var message : snapshot.messages()) {
      if (message instanceof SystemMessage) {
        continue; // hoisted to top-level system
      }
      switch (message) {
        case UserMessage user ->
            messages.add(
                Message.builder()
                    .role(ConversationRole.USER)
                    .content(contentConverter.toContentBlocks(user.content()))
                    .build());
        case AssistantMessage assistant -> messages.add(assistantMessage(assistant));
        case ToolCallResultMessage toolResults -> messages.add(toolResultMessage(toolResults));
        default ->
            throw new ConnectorException(
                ERROR_CODE_UNSUPPORTED_MODEL_CONFIGURATION,
                "Unsupported message type: " + message.getClass().getSimpleName());
      }
    }
    return messages;
  }

  private Message assistantMessage(AssistantMessage assistant) {
    final List<ContentBlock> blocks =
        new ArrayList<>(contentConverter.toContentBlocks(assistant.content()));
    for (final ToolCall toolCall : assistant.toolCalls()) {
      blocks.add(
          ContentBlock.fromToolUse(
              ToolUseBlock.builder()
                  .toolUseId(toolCall.id())
                  .name(toolCall.name())
                  .input(toDocument(toolCall.arguments()))
                  .build()));
    }
    return Message.builder().role(ConversationRole.ASSISTANT).content(blocks).build();
  }

  private Message toolResultMessage(ToolCallResultMessage message) {
    final List<ContentBlock> blocks = new ArrayList<>();
    for (final ToolCallResultContent result : message.results()) {
      blocks.add(
          ContentBlock.fromToolResult(
              ToolResultBlock.builder()
                  .toolUseId(result.id())
                  .content(contentConverter.toToolResultBlocks(result.content()))
                  .build()));
    }
    return Message.builder().role(ConversationRole.USER).content(blocks).build();
  }

  private List<Tool> buildTools(List<ToolDefinition> toolDefinitions) {
    final List<Tool> tools = new ArrayList<>();
    for (final ToolDefinition definition : toolDefinitions) {
      final var specBuilder =
          ToolSpecification.builder()
              .name(definition.name())
              .inputSchema(ToolInputSchema.fromJson(toDocument(definition.inputSchema())));
      if (definition.description() != null) {
        specBuilder.description(definition.description());
      }
      tools.add(Tool.builder().toolSpec(specBuilder.build()).build());
    }
    return tools;
  }

  /**
   * Applies the prompt-caching checkpoint placement: since checkpoints chain {@code tools -> system
   * -> messages} and the minimum-token-count is cumulative across all three, a single checkpoint at
   * the end of {@code system[]} already caches the whole {@code [tools][system]} prefix; a
   * checkpoint at the end of {@code tools[]} is only emitted when there is no system prompt to
   * anchor to. Independently, a second, moving checkpoint is placed at the end of the last
   * message's content so the growing conversation prefix is cached each turn. When prompt caching
   * is disabled (or unset), no checkpoint is emitted anywhere.
   */
  private void applyPromptCaching(
      @Nullable BedrockConverseModelParameters params,
      List<SystemContentBlock> system,
      List<Tool> tools,
      List<Message> messages) {
    final var promptCaching = params == null ? null : params.promptCaching();
    if (promptCaching == null || !Boolean.TRUE.equals(promptCaching.enabled())) {
      return;
    }

    if (!system.isEmpty()) {
      system.add(SystemContentBlock.fromCachePoint(defaultCachePoint()));
    } else if (!tools.isEmpty()) {
      tools.add(Tool.fromCachePoint(defaultCachePoint()));
    }

    final int index = lastCacheableMessageIndex(messages);
    if (index >= 0) {
      final Message target = messages.get(index);
      final List<ContentBlock> content = new ArrayList<>(target.content());
      content.add(ContentBlock.fromCachePoint(defaultCachePoint()));
      messages.set(index, target.toBuilder().content(content).build());
    }
  }

  /**
   * Finds the last message eligible for the moving checkpoint. Confirmed empirically against the
   * real API (AWS returns "extraneous key [cachePoint] is not permitted" otherwise): Bedrock
   * rejects a {@code cachePoint} block in any message that contains a {@code toolUse}, {@code
   * toolResult}, or {@code reasoningContent} block -- only messages made entirely of {@code
   * text}/{@code image}/{@code document} content qualify. This matches every placement example in
   * AWS's own docs, where a cachePoint only ever follows {@code text}/{@code image}. In an agentic
   * loop this means the checkpoint cannot advance past a tool round-trip -- it stays pinned to the
   * last plain-text message -- but it still moves for multi-turn conversations without tool calls
   * in between.
   */
  private static int lastCacheableMessageIndex(List<Message> messages) {
    for (int i = messages.size() - 1; i >= 0; i--) {
      final List<ContentBlock> content = messages.get(i).content();
      if (!content.isEmpty()
          && content.stream()
              .noneMatch(
                  block ->
                      block.toolUse() != null
                          || block.toolResult() != null
                          || block.reasoningContent() != null)) {
        return i;
      }
    }
    return -1;
  }

  // No ttl is ever set: AWS's default 5-minute TTL applies, and the "longer TTL must precede
  // shorter" ordering rule cannot be violated by a request that never specifies one.
  private static CachePointBlock defaultCachePoint() {
    return CachePointBlock.builder().type(CachePointType.DEFAULT).build();
  }

  /**
   * Maps a {@link JsonResponseFormatConfiguration} onto Converse's native structured-output
   * mechanism. {@link
   * io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.TextResponseFormatConfiguration}
   * (and a null {@code response}) emits nothing - {@code parseJson} is client-side.
   */
  private void applyOutputConfig(
      ConverseStreamRequest.Builder builder, @Nullable ResponseConfiguration response) {
    if (!(response != null && response.format() instanceof JsonResponseFormatConfiguration json)) {
      return;
    }

    final var jsonSchemaBuilder =
        JsonSchemaDefinition.builder().schema(writeAsJson(json.schema())).name(json.schemaName());
    builder.outputConfig(
        OutputConfig.builder()
            .textFormat(
                OutputFormat.builder()
                    .type(OutputFormatType.JSON_SCHEMA)
                    .structure(OutputFormatStructure.fromJsonSchema(jsonSchemaBuilder.build()))
                    .build())
            .build());
  }

  private void applyAdditionalModelRequestFields(
      ConverseStreamRequest.Builder builder, BedrockConverseConnection connection) {
    final Map<String, Object> bodyProperties = connection.bodyProperties();
    if (bodyProperties == null || bodyProperties.isEmpty()) {
      return;
    }
    builder.additionalModelRequestFields(toDocument(bodyProperties));
  }

  /** Merges the escape-hatch {@code headers} and {@code queryParameters} onto the request. */
  private void applyOverrideConfiguration(
      ConverseStreamRequest.Builder builder, BedrockConverseConnection connection) {
    final Map<String, String> headers = connection.headers();
    final Map<String, String> queryParameters = connection.queryParameters();
    if ((headers == null || headers.isEmpty())
        && (queryParameters == null || queryParameters.isEmpty())) {
      return;
    }

    builder.overrideConfiguration(
        c -> {
          if (headers != null) {
            headers.forEach(c::putHeader);
          }
          if (queryParameters != null) {
            queryParameters.forEach(c::putRawQueryParameter);
          }
        });
  }

  private String writeAsJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize response JSON schema", e);
    }
  }

  /**
   * Converts an already-deserialized JSON value tree (as produced by FEEL evaluation or JSON
   * deserialization: maps, lists, strings, numbers, booleans, null, or an arbitrary POJO) into the
   * AWS SDK's generic {@code Document} value tree, used for tool input schemas ({@link
   * ToolInputSchema#fromJson}) and {@code additionalModelRequestFields}. See {@link
   * BedrockConverseDocuments} for the conversion policy shared with the other Bedrock Converse
   * converters; a value that policy cannot make sense of either is reported here as an unsupported
   * model configuration, since both call sites of this method (tool input schemas and request
   * parameters) originate from the connector's own configuration/tool definitions.
   */
  private software.amazon.awssdk.core.document.Document toDocument(@Nullable Object value) {
    try {
      return BedrockConverseDocuments.toDocument(value, objectMapper);
    } catch (RuntimeException e) {
      throw new ConnectorException(
          ERROR_CODE_UNSUPPORTED_MODEL_CONFIGURATION,
          "Cannot convert value of type '%s' to a Bedrock Document: %s"
              .formatted(value == null ? "null" : value.getClass().getName(), e.getMessage()),
          e);
    }
  }
}
