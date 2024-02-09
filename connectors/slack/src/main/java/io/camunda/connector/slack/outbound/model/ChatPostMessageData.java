/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.slack.outbound.SlackResponse;
import io.camunda.connector.slack.outbound.utils.DataLookupService;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

public record ChatPostMessageData(@NotBlank String channel, String text, JsonNode blockContent)
    implements SlackRequestData {
  @Override
  public SlackResponse invoke(MethodsClient methodsClient) throws SlackApiException, IOException {
    if (!isContentSupplied()) {
      throw new ConnectorException("Text or block content required to post a message");
    }

    String filteredChannel = this.channel;
    if (channel.startsWith("@")) {
      filteredChannel = DataLookupService.getUserIdByUserName(channel.substring(1), methodsClient);
    } else if (DataLookupService.isEmail(channel)) {
      filteredChannel = DataLookupService.getUserIdByEmail(channel, methodsClient);
    }

    var requestBuilder = ChatPostMessageRequest.builder().channel(filteredChannel);

    // Note: both text and block content can co-exist
    if (StringUtils.isNotBlank(text)) {
      // Temporary workaround related to camunda/zeebe#9859
      requestBuilder.text(StringEscapeUtils.unescapeJson(text));
      // Enables plain text message formatting
      requestBuilder.linkNames(true);
    }

    if (blockContent != null) {
      if (!blockContent.isArray()) {
        throw new ConnectorException("Block section must be an array");
      }
      requestBuilder.blocksAsString(blockContent.toString());
    }

    var request = requestBuilder.build();

    ChatPostMessageResponse chatPostMessageResponse = methodsClient.chatPostMessage(request);
    if (chatPostMessageResponse.isOk()) {
      return new ChatPostMessageSlackResponse(chatPostMessageResponse);
    } else {
      throw new RuntimeException(chatPostMessageResponse.getError());
    }
  }

  @AssertTrue(message = "Text or block content required to post a message")
  private boolean isContentSupplied() {
    return StringUtils.isNotBlank(text) || blockContent != null;
  }
}
