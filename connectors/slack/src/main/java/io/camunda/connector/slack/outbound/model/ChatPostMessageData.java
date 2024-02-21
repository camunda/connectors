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
import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyBinding;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.slack.outbound.SlackResponse;
import io.camunda.connector.slack.outbound.utils.DataLookupService;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

@TemplateSubType(id = "chat.postMessage", label = "Post message")
public record ChatPostMessageData(
    @TemplateProperty(
            label = "Channel/user name/email",
            id = "data.channel",
            group = "channel",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "data.channel"))
        @NotBlank
        String channel,
    @TemplateProperty(
            label = "Message type",
            id = "data.messageType",
            group = "message",
            defaultValue = "plainText",
            type = PropertyType.Dropdown,
            binding = @PropertyBinding(name = "data.messageType"),
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "plainText", label = "Plain text"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "messageBlock",
                  label = "Message block")
            },
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "method",
                    equals = "chat.postMessage"))
        String messageType,
    @TemplateProperty(
            label = "Message",
            id = "data.text",
            group = "message",
            feel = FeelMode.optional,
            optional = false,
            binding = @PropertyBinding(name = "data.text"),
            type = PropertyType.Text,
            constraints = @PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "data.messageType",
                    equals = "plainText"))
        String text,
    @TemplateProperty(
            label = "Message block",
            description =
                "An array of rich message content blocks. Learn more at the <a href=\"https://api.slack.com/reference/surfaces/formatting#stack_of_blocks\" target=\"_blank\">official Slack documentation page</a>",
            id = "data.blockContent",
            group = "message",
            feel = FeelMode.required,
            optional = false,
            binding = @PropertyBinding(name = "data.blockContent"),
            constraints = @PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "data.messageType",
                    equals = "messageBlock"),
            defaultValue =
                "=[\n\t{\n\t\t\"type\": \"header\",\n\t\t\"text\": {\n\t\t\t\"type\": \"plain_text\",\n\t\t\t\"text\": \"New request\"\n\t\t}\n\t},\n\t{\n\t\t\"type\": \"section\",\n\t\t\"fields\": [\n\t\t\t{\n\t\t\t\t\"type\": \"mrkdwn\",\n\t\t\t\t\"text\": \"*Type:*\\nPaid Time Off\"\n\t\t\t},\n\t\t\t{\n\t\t\t\t\"type\": \"mrkdwn\",\n\t\t\t\t\"text\": \"*Created by:*\\n<example.com|John Doe>\"\n\t\t\t}\n\t\t]\n\t},\n\t{\n\t\t\"type\": \"section\",\n\t\t\"fields\": [\n\t\t\t{\n\t\t\t\t\"type\": \"mrkdwn\",\n\t\t\t\t\"text\": \"*When:*\\nAug 10 - Aug 13\"\n\t\t\t}\n\t\t]\n\t},\n\t{\n\t\t\"type\": \"section\",\n\t\t\"text\": {\n\t\t\t\"type\": \"mrkdwn\",\n\t\t\t\"text\": \"<https://example.com|View request>\"\n\t\t}\n\t}\n]")
        JsonNode blockContent)
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
