/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.client.ai.base;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;

public abstract class AiClient {

  protected ChatModel chatModel;

  public String chat(String userMessageText) {
    return chatModel.chat(UserMessage.from(userMessageText)).aiMessage().text();
  }

  public String chat(String systemMessageText, String userMessageText) {
    return chatModel
        .chat(SystemMessage.from(systemMessageText), UserMessage.from(userMessageText))
        .aiMessage()
        .text();
  }
}
