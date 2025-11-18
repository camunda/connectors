/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.client.ai.base;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentLinkParameters;
import java.time.Duration;
import java.util.List;

public abstract class AiClient {

  protected ChatModel chatModel;

  public String chat(String input) {
    return chatModel.chat(input);
  }

  public String chat(List<ChatMessage> messages) {
    return chatModel.chat(messages).aiMessage().text();
  }

  public String chat(String input, Document document) {
    String contentType = document.metadata() != null ? document.metadata().getContentType() : null;

    // If contentType is null, treat as PDF
    if (contentType == null) {
      contentType = "application/pdf";
    }

    UserMessage message;

    // Try to generate a document link first
    try {
      DocumentLinkParameters linkParams = new DocumentLinkParameters(Duration.ofMinutes(2));
      String documentLink = document.generateLink(linkParams);

      // Check if it's an image type
      if (contentType.startsWith("image/")) {
        message = UserMessage.from(TextContent.from(input), ImageContent.from(documentLink));
      } else {
        // Treat everything else as PDF (including explicit application/pdf)
        message = UserMessage.from(TextContent.from(input), PdfFileContent.from(documentLink));
      }
    } catch (Exception e) {
      // Fallback to base64 if link generation fails
      String base64Content = document.asBase64();

      if (contentType.startsWith("image/")) {
        message =
            UserMessage.from(
                TextContent.from(input), ImageContent.from(base64Content, contentType));
      } else {
        message =
            UserMessage.from(
                TextContent.from(input), PdfFileContent.from(base64Content, contentType));
      }
    }

    return chatModel.chat(message).aiMessage().text();
  }
}
