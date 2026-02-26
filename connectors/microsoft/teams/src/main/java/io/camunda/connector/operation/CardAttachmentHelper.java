/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.ChatMessage;
import com.microsoft.graph.models.ChatMessageAttachment;
import com.microsoft.graph.models.ItemBody;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.suppliers.ObjectMapperSupplier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CardAttachmentHelper {

  private static final ObjectMapper OBJECT_MAPPER = ObjectMapperSupplier.objectMapper();

  private CardAttachmentHelper() {}

  /**
   * Configures the ChatMessage body and card attachments.
   *
   * <p>When {@code attachmentsJson} is provided, the JSON is parsed into {@link
   * ChatMessageAttachment} objects and set on the message. If the body content does not already
   * contain {@code <attachment id="...">} tags for each attachment, they are appended
   * automatically, and the content type is switched to HTML.
   *
   * @param chatMessage the ChatMessage to configure
   * @param content the message body content
   * @param bodyType the content type ("TEXT" or "HTML"), may be null
   * @param attachmentsJson optional JSON string (array or single object) of card attachments
   */
  public static void configureMessageBody(
      ChatMessage chatMessage, String content, String bodyType, String attachmentsJson) {

    ItemBody body = new ItemBody();
    BodyType resolvedBodyType =
        Optional.ofNullable(bodyType)
            .map(type -> BodyType.forValue(type.toLowerCase(Locale.ROOT)))
            .orElse(BodyType.Text);

    if (attachmentsJson != null && !attachmentsJson.isBlank()) {
      List<ChatMessageAttachment> attachments = parseAttachmentsJson(attachmentsJson);
      chatMessage.setAttachments(attachments);

      String updatedContent = content;
      boolean needsAutoAppend = false;
      for (ChatMessageAttachment att : attachments) {
        String id = att.getId();
        if (id != null && !content.contains("<attachment id=\"" + id + "\">")) {
          needsAutoAppend = true;
          break;
        }
      }

      if (needsAutoAppend) {
        StringBuilder sb = new StringBuilder(content);
        for (ChatMessageAttachment att : attachments) {
          String id = att.getId();
          if (id != null && !content.contains("<attachment id=\"" + id + "\">")) {
            sb.append("<attachment id=\"").append(id).append("\"></attachment>");
          }
        }
        updatedContent = sb.toString();
        resolvedBodyType = BodyType.Html;
      }
      body.setContent(updatedContent);
    } else {
      body.setContent(content);
    }

    body.setContentType(resolvedBodyType);
    chatMessage.setBody(body);
  }

  static List<ChatMessageAttachment> parseAttachmentsJson(String json) {
    try {
      JsonNode rootNode = OBJECT_MAPPER.readTree(json);
      List<JsonNode> nodes = new ArrayList<>();
      if (rootNode.isArray()) {
        rootNode.forEach(nodes::add);
      } else if (rootNode.isObject()) {
        nodes.add(rootNode);
      } else {
        throw new ConnectorInputException(
            new IllegalArgumentException(
                "attachmentsJson must be a JSON object or array, got: " + rootNode.getNodeType()));
      }

      List<ChatMessageAttachment> attachments = new ArrayList<>();
      for (JsonNode node : nodes) {
        attachments.add(toAttachment(node));
      }
      return attachments;
    } catch (JsonProcessingException e) {
      throw new ConnectorInputException(
          new IllegalArgumentException("Invalid JSON in attachmentsJson: " + e.getMessage(), e));
    }
  }

  private static ChatMessageAttachment toAttachment(JsonNode node) {
    ChatMessageAttachment attachment = new ChatMessageAttachment();

    if (node.has("id")) {
      attachment.setId(node.get("id").asText());
    }
    if (node.has("contentType")) {
      attachment.setContentType(node.get("contentType").asText());
    }
    if (node.has("contentUrl") && !node.get("contentUrl").isNull()) {
      attachment.setContentUrl(node.get("contentUrl").asText());
    }
    if (node.has("content") && !node.get("content").isNull()) {
      attachment.setContent(node.get("content").asText());
    }
    if (node.has("name") && !node.get("name").isNull()) {
      attachment.setName(node.get("name").asText());
    }
    if (node.has("thumbnailUrl") && !node.get("thumbnailUrl").isNull()) {
      attachment.setThumbnailUrl(node.get("thumbnailUrl").asText());
    }

    // Preserve unknown fields via additionalData
    Map<String, Object> additionalData = new HashMap<>();
    node.fields()
        .forEachRemaining(
            entry -> {
              String key = entry.getKey();
              if (!isKnownField(key)) {
                additionalData.put(key, nodeToObject(entry.getValue()));
              }
            });
    if (!additionalData.isEmpty()) {
      attachment.setAdditionalData(additionalData);
    }

    return attachment;
  }

  private static boolean isKnownField(String field) {
    return "id".equals(field)
        || "contentType".equals(field)
        || "contentUrl".equals(field)
        || "content".equals(field)
        || "name".equals(field)
        || "thumbnailUrl".equals(field);
  }

  private static Object nodeToObject(JsonNode node) {
    if (node.isNull()) {
      return null;
    } else if (node.isTextual()) {
      return node.asText();
    } else if (node.isNumber()) {
      return node.numberValue();
    } else if (node.isBoolean()) {
      return node.asBoolean();
    } else {
      return node.toString();
    }
  }
}
