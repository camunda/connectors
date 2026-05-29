/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.agentcoreharness;

import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.ContentMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessContentBlock;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessConversationRole;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessMessage;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessSystemContentBlock;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessToolResultBlock;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessToolResultContentBlock;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessToolUseBlock;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessToolUseStatus;

/** Converts between Camunda messages and AWS Harness message formats. */
public class HarnessMessageConverter {

  /**
   * Extracts system prompt content blocks from messages.
   *
   * @param messages the Camunda messages
   * @return list of HarnessSystemContentBlock for system messages
   */
  public List<HarnessSystemContentBlock> extractSystemPrompt(List<Message> messages) {
    return messages.stream()
        .filter(SystemMessage.class::isInstance)
        .map(SystemMessage.class::cast)
        .flatMap(msg -> toSystemContentBlocks(msg).stream())
        .toList();
  }

  /**
   * Converts Camunda messages to Harness messages (excluding system messages).
   *
   * @param messages the Camunda messages
   * @return list of HarnessMessage
   */
  public List<HarnessMessage> toHarnessMessages(List<Message> messages) {
    List<HarnessMessage> result = new ArrayList<>();

    for (Message message : messages) {
      if (message instanceof SystemMessage) {
        // System messages are handled separately via systemPrompt
        continue;
      } else if (message instanceof UserMessage userMessage) {
        result.add(toHarnessMessage(userMessage));
      } else if (message instanceof AssistantMessage assistantMessage) {
        result.add(toHarnessMessage(assistantMessage));
      } else if (message instanceof ToolCallResultMessage toolResultMessage) {
        result.add(toHarnessMessage(toolResultMessage));
      }
    }

    return result;
  }

  private List<HarnessSystemContentBlock> toSystemContentBlocks(SystemMessage systemMessage) {
    return extractTextContent(systemMessage).stream()
        .map(text -> HarnessSystemContentBlock.fromText(text))
        .toList();
  }

  private HarnessMessage toHarnessMessage(UserMessage userMessage) {
    List<HarnessContentBlock> contentBlocks =
        extractTextContent(userMessage).stream()
            .map(HarnessContentBlock::fromText)
            .map(HarnessContentBlock.class::cast)
            .toList();

    return HarnessMessage.builder()
        .role(HarnessConversationRole.USER)
        .content(contentBlocks)
        .build();
  }

  private HarnessMessage toHarnessMessage(AssistantMessage assistantMessage) {
    List<HarnessContentBlock> contentBlocks = new ArrayList<>();

    // Add text content
    for (String text : extractTextContent(assistantMessage)) {
      contentBlocks.add(HarnessContentBlock.fromText(text));
    }

    // Add tool use blocks
    if (assistantMessage.toolCalls() != null) {
      for (ToolCall toolCall : assistantMessage.toolCalls()) {
        contentBlocks.add(
            HarnessContentBlock.fromToolUse(
                HarnessToolUseBlock.builder()
                    .toolUseId(toolCall.id())
                    .name(toolCall.name())
                    .input(mapToDocument(toolCall.arguments()))
                    .build()));
      }
    }

    return HarnessMessage.builder()
        .role(HarnessConversationRole.ASSISTANT)
        .content(contentBlocks)
        .build();
  }

  private HarnessMessage toHarnessMessage(ToolCallResultMessage toolResultMessage) {
    List<HarnessContentBlock> contentBlocks =
        toolResultMessage.results().stream()
            .map(this::toToolResultContentBlock)
            .map(HarnessContentBlock::fromToolResult)
            .toList();

    return HarnessMessage.builder()
        .role(HarnessConversationRole.USER)
        .content(contentBlocks)
        .build();
  }

  private HarnessToolResultBlock toToolResultContentBlock(ToolCallResult result) {
    String contentText = result.content() != null ? result.content().toString() : "";
    boolean isError =
        Boolean.TRUE.equals(result.properties().get(ToolCallResult.PROPERTY_INTERRUPTED));

    return HarnessToolResultBlock.builder()
        .toolUseId(result.id())
        .status(isError ? HarnessToolUseStatus.ERROR : HarnessToolUseStatus.SUCCESS)
        .content(List.of(HarnessToolResultContentBlock.fromText(contentText)))
        .build();
  }

  private List<String> extractTextContent(ContentMessage message) {
    if (message.content() == null || message.content().isEmpty()) {
      return List.of();
    }

    return message.content().stream()
        .filter(TextContent.class::isInstance)
        .map(TextContent.class::cast)
        .map(TextContent::text)
        .toList();
  }

  @SuppressWarnings("unchecked")
  private Document mapToDocument(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return Document.fromMap(Map.of());
    }
    return Document.fromMap(
        map.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    Map.Entry::getKey, e -> valueToDocument(e.getValue()))));
  }

  @SuppressWarnings("unchecked")
  private Document valueToDocument(Object value) {
    if (value == null) {
      return Document.fromNull();
    } else if (value instanceof String s) {
      return Document.fromString(s);
    } else if (value instanceof Number n) {
      return Document.fromNumber(n.toString());
    } else if (value instanceof Boolean b) {
      return Document.fromBoolean(b);
    } else if (value instanceof Map<?, ?> m) {
      return mapToDocument((Map<String, Object>) m);
    } else if (value instanceof List<?> l) {
      return Document.fromList(l.stream().map(this::valueToDocument).toList());
    } else {
      return Document.fromString(value.toString());
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> documentToMap(Document document) {
    if (document == null || document.isNull()) {
      return Map.of();
    }
    return document.asMap().entrySet().stream()
        .collect(
            java.util.stream.Collectors.toMap(
                Map.Entry::getKey, e -> documentToValue(e.getValue())));
  }

  private Object documentToValue(Document document) {
    if (document == null || document.isNull()) {
      return null;
    } else if (document.isString()) {
      return document.asString();
    } else if (document.isNumber()) {
      return document.asNumber();
    } else if (document.isBoolean()) {
      return document.asBoolean();
    } else if (document.isMap()) {
      return documentToMap(document);
    } else if (document.isList()) {
      return document.asList().stream().map(this::documentToValue).toList();
    }
    return null;
  }
}
