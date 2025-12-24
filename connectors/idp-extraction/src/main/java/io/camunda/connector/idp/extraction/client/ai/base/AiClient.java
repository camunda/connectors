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
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentLinkParameters;
import java.time.Duration;

public abstract class AiClient {

  protected ChatModel chatModel;

  public String chat(String input) {
    return chatModel.chat(input);
  }

  public ChatResponse chat(String systemMessageText, String userMessageText) {
    ChatMessage systemMessage = new SystemMessage(systemMessageText);
    ChatMessage userMessage = new UserMessage(userMessageText);
    return chatModel.chat(systemMessage, userMessage);
  }

  public ChatResponse chat(String systemMessageText, String userMessageText, Document document) {
    String contentType = document.metadata() != null ? document.metadata().getContentType() : null;

    // If contentType is null, treat as PDF
    if (contentType == null) {
      contentType = "application/pdf";
    }

    SystemMessage systemMessage = new SystemMessage(systemMessageText);
    UserMessage message;

    // Try to generate a document link first
    try {
      DocumentLinkParameters linkParams = new DocumentLinkParameters(Duration.ofMinutes(2));
      String documentLink = document.generateLink(linkParams);
      message = asUserMessage(userMessageText, contentType, documentLink);
    } catch (Exception e) {
      // Fallback to base64 if link generation fails
      String base64Content = document.asBase64();
      message = asUserMessage(userMessageText, contentType, base64Content);
    }

    return chatModel.chat(systemMessage, message);
  }

  private UserMessage asUserMessage(
      String userMessageText, String contentType, String documentContent) {
    // Check if it's an image type
    if (contentType.startsWith("image/")) {
      return UserMessage.from(
          TextContent.from(userMessageText), ImageContent.from(documentContent));
    } else {
      // Treat everything else as PDF (including explicit application/pdf)
      return UserMessage.from(
          TextContent.from(userMessageText), PdfFileContent.from(documentContent));
    }
  }
}
