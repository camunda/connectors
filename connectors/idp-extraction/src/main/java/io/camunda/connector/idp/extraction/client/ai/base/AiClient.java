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
import java.util.List;

public abstract class AiClient {

  protected ChatModel chatModel;

  public String chat(String input) {
    return chatModel.chat(input);
  }

  public String chat(List<ChatMessage> messages) {
    return chatModel.chat(messages).aiMessage().text();
  }

  public String chatWithPdf(String textPrompt, String pdfUrl) {
    UserMessage message =
        UserMessage.from(TextContent.from(textPrompt), PdfFileContent.from(pdfUrl));
    return chatModel.chat(message).aiMessage().text();
  }

  public String chatWithImage(String textPrompt, String imageUrl) {
    UserMessage message =
        UserMessage.from(TextContent.from(textPrompt), ImageContent.from(imageUrl));
    return chatModel.chat(message).aiMessage().text();
  }
}
