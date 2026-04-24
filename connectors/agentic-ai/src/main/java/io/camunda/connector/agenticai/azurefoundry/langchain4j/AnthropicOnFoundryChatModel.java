/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.azurefoundry.langchain4j;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.errors.UnprocessableEntityException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorInputException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * langchain4j {@link ChatModel} adapter that wraps an Anthropic SDK {@link AnthropicClient}.
 *
 * <p>Translates langchain4j {@link ChatRequest} → Anthropic {@link MessageCreateParams}, forwards
 * the call, and converts the resulting {@link Message} back into a langchain4j {@link
 * ChatResponse}.
 *
 * <p>Scope: core chat (system + user/assistant messages), tool use (tool_use + tool_result
 * round-trip), stop-reason mapping, and token usage. Vision, prompt caching, extended thinking, and
 * streaming are deferred to a later milestone.
 */
public class AnthropicOnFoundryChatModel implements ChatModel {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final AnthropicClient client;
  private final AnthropicModel modelConfig;

  public AnthropicOnFoundryChatModel(AnthropicClient client, AnthropicModel modelConfig) {
    this.client = client;
    this.modelConfig = modelConfig;
  }

  public AnthropicModel modelConfig() {
    return modelConfig;
  }

  @Override
  public ChatResponse doChat(ChatRequest request) {
    try {
      MessageCreateParams params = buildParams(request);
      Message response = client.messages().create(params);
      return buildChatResponse(response);
    } catch (BadRequestException
        | UnauthorizedException
        | PermissionDeniedException
        | NotFoundException
        | UnprocessableEntityException ex) {
      throw new ConnectorInputException(ex);
    } catch (RateLimitException | InternalServerException ex) {
      throw new ConnectorException(String.valueOf(ex.statusCode()), ex.getMessage(), ex);
    } catch (AnthropicException ex) {
      throw new ConnectorException("ANTHROPIC_ERROR", ex.getMessage(), ex);
    }
  }

  // -------------------------------------------------------------------------
  // Request translation
  // -------------------------------------------------------------------------

  private MessageCreateParams buildParams(ChatRequest request) {
    var builder = MessageCreateParams.builder();

    // Model
    builder.model(modelConfig.deploymentName());

    // Model parameters from config (required + optional)
    var params = modelConfig.parameters();
    if (params != null && params.maxTokens() != null) {
      builder.maxTokens((long) params.maxTokens());
    } else {
      builder.maxTokens(1024L);
    }
    if (params != null && params.temperature() != null) {
      builder.temperature(params.temperature());
    }
    if (params != null && params.topP() != null) {
      builder.topP(params.topP());
    }
    if (params != null && params.topK() != null) {
      builder.topK((long) params.topK());
    }

    // Override from ChatRequest parameters if present
    if (request.maxOutputTokens() != null) {
      builder.maxTokens((long) request.maxOutputTokens());
    }
    if (request.temperature() != null) {
      builder.temperature(request.temperature());
    }
    if (request.topP() != null) {
      builder.topP(request.topP());
    }
    if (request.topK() != null) {
      builder.topK((long) request.topK());
    }

    // Messages — split system messages from conversation messages
    List<ChatMessage> messages = request.messages();
    Optional<String> systemText = extractSystemText(messages);
    systemText.ifPresent(builder::system);

    List<MessageParam> messageParams = buildMessageParams(messages);
    builder.messages(messageParams);

    // Tools
    List<ToolSpecification> toolSpecs = request.toolSpecifications();
    if (toolSpecs != null && !toolSpecs.isEmpty()) {
      for (ToolSpecification spec : toolSpecs) {
        builder.addTool(buildAnthropicTool(spec));
      }
    }

    return builder.build();
  }

  private Optional<String> extractSystemText(List<ChatMessage> messages) {
    return messages.stream()
        .filter(m -> m instanceof SystemMessage)
        .map(m -> ((SystemMessage) m).text())
        .reduce((a, b) -> a + "\n" + b);
  }

  private List<MessageParam> buildMessageParams(List<ChatMessage> messages) {
    // Group consecutive tool-result messages into a single user message block
    // (Anthropic API: tool results must be in a user message as ContentBlockParams)
    List<MessageParam> result = new ArrayList<>();
    List<ContentBlockParam> pendingToolResults = new ArrayList<>();

    for (ChatMessage message : messages) {
      switch (message.type()) {
        case SYSTEM -> {
          // system messages handled separately
          flushToolResults(pendingToolResults, result);
        }
        case USER -> {
          flushToolResults(pendingToolResults, result);
          result.add(buildUserMessage((UserMessage) message));
        }
        case AI -> {
          flushToolResults(pendingToolResults, result);
          result.add(buildAssistantMessage((AiMessage) message));
        }
        case TOOL_EXECUTION_RESULT -> {
          // Collect tool results — they will be emitted as a single user message
          pendingToolResults.add(buildToolResultBlock((ToolExecutionResultMessage) message));
        }
        default -> {
          // Unknown message types are skipped
          flushToolResults(pendingToolResults, result);
        }
      }
    }
    flushToolResults(pendingToolResults, result);
    return result;
  }

  private void flushToolResults(
      List<ContentBlockParam> pendingToolResults, List<MessageParam> result) {
    if (pendingToolResults.isEmpty()) {
      return;
    }
    result.add(
        MessageParam.builder()
            .role(MessageParam.Role.USER)
            .contentOfBlockParams(List.copyOf(pendingToolResults))
            .build());
    pendingToolResults.clear();
  }

  private MessageParam buildUserMessage(UserMessage message) {
    return MessageParam.builder()
        .role(MessageParam.Role.USER)
        .content(message.singleText())
        .build();
  }

  private MessageParam buildAssistantMessage(AiMessage aiMessage) {
    List<ContentBlockParam> blocks = new ArrayList<>();

    // Text content
    String text = aiMessage.text();
    if (text != null && !text.isBlank()) {
      blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(text).build()));
    }

    // Tool execution requests → tool_use blocks
    if (aiMessage.hasToolExecutionRequests()) {
      for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
        blocks.add(buildToolUseBlock(toolRequest));
      }
    }

    if (blocks.isEmpty()) {
      // Fallback: empty text block to satisfy Anthropic's non-empty content requirement
      blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text("").build()));
    }

    return MessageParam.builder()
        .role(MessageParam.Role.ASSISTANT)
        .contentOfBlockParams(blocks)
        .build();
  }

  private ContentBlockParam buildToolUseBlock(ToolExecutionRequest toolRequest) {
    // Parse the JSON arguments string into a ToolUseBlockParam.Input
    var inputBuilder = ToolUseBlockParam.Input.builder();
    String arguments = toolRequest.arguments();
    if (arguments != null && !arguments.isBlank()) {
      try {
        JsonNode node = OBJECT_MAPPER.readTree(arguments);
        node.fields()
            .forEachRemaining(
                entry ->
                    inputBuilder.putAdditionalProperty(
                        entry.getKey(), com.anthropic.core.JsonValue.from(entry.getValue())));
      } catch (JsonProcessingException e) {
        // If arguments can't be parsed as JSON, treat as empty input
      }
    }
    return ContentBlockParam.ofToolUse(
        ToolUseBlockParam.builder()
            .id(toolRequest.id())
            .name(toolRequest.name())
            .input(inputBuilder.build())
            .build());
  }

  private ContentBlockParam buildToolResultBlock(ToolExecutionResultMessage message) {
    var builder =
        ToolResultBlockParam.builder()
            .toolUseId(message.id())
            .content(message.text() != null ? message.text() : "");
    if (Boolean.TRUE.equals(message.isError())) {
      builder.isError(true);
    }
    return ContentBlockParam.ofToolResult(builder.build());
  }

  private Tool buildAnthropicTool(ToolSpecification spec) {
    var schemaBuilder = Tool.InputSchema.builder();

    if (spec.parameters() != null) {
      // Use the JSON representation from langchain4j — toJson() gives us the full schema JSON
      try {
        JsonNode schemaNode = OBJECT_MAPPER.readTree(spec.parameters().toString());
        // properties
        JsonNode propertiesNode = schemaNode.get("properties");
        if (propertiesNode != null && !propertiesNode.isEmpty()) {
          var propertiesBuilder = Tool.InputSchema.Properties.builder();
          propertiesNode
              .fields()
              .forEachRemaining(
                  entry ->
                      propertiesBuilder.putAdditionalProperty(
                          entry.getKey(), com.anthropic.core.JsonValue.from(entry.getValue())));
          schemaBuilder.properties(propertiesBuilder.build());
        }
        // required
        JsonNode requiredNode = schemaNode.get("required");
        if (requiredNode != null && requiredNode.isArray()) {
          List<String> required = new ArrayList<>();
          requiredNode.forEach(n -> required.add(n.asText()));
          schemaBuilder.required(required);
        }
      } catch (JsonProcessingException e) {
        // If schema can't be parsed, emit an empty schema
      }
    }

    var toolBuilder = Tool.builder().name(spec.name()).inputSchema(schemaBuilder.build());
    if (spec.description() != null) {
      toolBuilder.description(spec.description());
    }
    return toolBuilder.build();
  }

  // -------------------------------------------------------------------------
  // Response translation
  // -------------------------------------------------------------------------

  private ChatResponse buildChatResponse(Message message) {
    String text = null;
    List<ToolExecutionRequest> toolRequests = new ArrayList<>();

    for (ContentBlock block : message.content()) {
      if (block.isText()) {
        String blockText = block.asText().text();
        text = (text == null) ? blockText : text + blockText;
      } else if (block.isToolUse()) {
        toolRequests.add(buildToolExecutionRequest(block));
      }
      // Other block types (thinking, server tools, etc.) are skipped
    }

    AiMessage aiMessage =
        toolRequests.isEmpty()
            ? new AiMessage(text != null ? text : "")
            : new AiMessage(text, toolRequests);

    FinishReason finishReason = mapStopReason(message.stopReason().orElse(null));
    TokenUsage tokenUsage = mapTokenUsage(message.usage());

    return ChatResponse.builder()
        .aiMessage(aiMessage)
        .finishReason(finishReason)
        .tokenUsage(tokenUsage)
        .id(message.id())
        .modelName(message.model().toString())
        .build();
  }

  private ToolExecutionRequest buildToolExecutionRequest(ContentBlock block) {
    var toolUse = block.asToolUse();
    // _input() returns JsonValue; convert it to a JSON string for langchain4j
    String argumentsJson;
    try {
      argumentsJson = OBJECT_MAPPER.writeValueAsString(toolUse._input().convert(Object.class));
    } catch (JsonProcessingException e) {
      argumentsJson = "{}";
    }
    return ToolExecutionRequest.builder()
        .id(toolUse.id())
        .name(toolUse.name())
        .arguments(argumentsJson)
        .build();
  }

  private FinishReason mapStopReason(StopReason stopReason) {
    if (stopReason == null) {
      return FinishReason.OTHER;
    }
    if (stopReason.equals(StopReason.END_TURN) || stopReason.equals(StopReason.STOP_SEQUENCE)) {
      return FinishReason.STOP;
    }
    if (stopReason.equals(StopReason.TOOL_USE)) {
      return FinishReason.TOOL_EXECUTION;
    }
    if (stopReason.equals(StopReason.MAX_TOKENS)) {
      return FinishReason.LENGTH;
    }
    return FinishReason.OTHER;
  }

  private TokenUsage mapTokenUsage(com.anthropic.models.messages.Usage usage) {
    int input = (int) usage.inputTokens();
    int output = (int) usage.outputTokens();
    return new TokenUsage(input, output, input + output);
  }

  // -------------------------------------------------------------------------
  // Unsupported capabilities (deferred)
  // -------------------------------------------------------------------------

  @Override
  public java.util.Set<dev.langchain4j.model.chat.Capability> supportedCapabilities() {
    return Collections.emptySet();
  }
}
