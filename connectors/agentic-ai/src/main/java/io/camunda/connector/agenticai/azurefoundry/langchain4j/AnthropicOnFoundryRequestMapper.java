/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.azurefoundry.langchain4j;

import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
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
import dev.langchain4j.model.chat.request.ChatRequest;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps a langchain4j {@link ChatRequest} to an Anthropic SDK {@link MessageCreateParams}.
 *
 * <p>Stateful (carries the {@link AnthropicModel} config) but thread-safe (config is immutable).
 */
class AnthropicOnFoundryRequestMapper {

  private static final Logger LOG = LoggerFactory.getLogger(AnthropicOnFoundryRequestMapper.class);
  private static final long DEFAULT_MAX_TOKENS = 1024L;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final AnthropicModel modelConfig;

  AnthropicOnFoundryRequestMapper(AnthropicModel modelConfig) {
    this.modelConfig = modelConfig;
  }

  MessageCreateParams toMessageCreateParams(ChatRequest request) {
    var builder = MessageCreateParams.builder();

    // Model
    builder.model(modelConfig.deploymentName());

    // Model parameters from config (required + optional)
    var params = modelConfig.parameters();
    if (params != null && params.maxTokens() != null) {
      builder.maxTokens((long) params.maxTokens());
    } else {
      builder.maxTokens(DEFAULT_MAX_TOKENS);
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
        LOG.warn(
            "Failed to parse tool arguments JSON for tool '{}' (id={}); using empty input. {}",
            toolRequest.name(),
            toolRequest.id(),
            e.getMessage());
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
        LOG.warn(
            "Failed to parse tool input schema for tool '{}'; emitting empty schema. {}",
            spec.name(),
            e.getMessage());
      }
    }

    var toolBuilder = Tool.builder().name(spec.name()).inputSchema(schemaBuilder.build());
    if (spec.description() != null) {
      toolBuilder.description(spec.description());
    }
    return toolBuilder.build();
  }
}
