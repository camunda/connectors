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
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyBinding;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.slack.outbound.SlackResponse;
import io.camunda.connector.slack.outbound.caller.FileUploader;
import io.camunda.connector.slack.outbound.mapper.BlockBuilder;
import io.camunda.connector.slack.outbound.utils.DataLookupService;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

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
            label = "Thread",
            id = "data.thread",
            group = "channel",
            optional = true,
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "data.thread"))
        String thread,
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
        MessageType messageType,
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
            binding = @PropertyBinding(name = "data.blockContent"),
            constraints = @PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "data.messageType",
                    equals = "messageBlock"),
            defaultValue =
                "=[\n\t{\n\t\t\"type\": \"header\",\n\t\t\"text\": {\n\t\t\t\"type\": \"plain_text\",\n\t\t\t\"text\": \"New request\"\n\t\t}\n\t},\n\t{\n\t\t\"type\": \"section\",\n\t\t\"fields\": [\n\t\t\t{\n\t\t\t\t\"type\": \"mrkdwn\",\n\t\t\t\t\"text\": \"*Type:*\\nPaid Time Off\"\n\t\t\t},\n\t\t\t{\n\t\t\t\t\"type\": \"mrkdwn\",\n\t\t\t\t\"text\": \"*Created by:*\\n<example.com|John Doe>\"\n\t\t\t}\n\t\t]\n\t},\n\t{\n\t\t\"type\": \"section\",\n\t\t\"fields\": [\n\t\t\t{\n\t\t\t\t\"type\": \"mrkdwn\",\n\t\t\t\t\"text\": \"*When:*\\nAug 10 - Aug 13\"\n\t\t\t}\n\t\t]\n\t},\n\t{\n\t\t\"type\": \"section\",\n\t\t\"text\": {\n\t\t\t\"type\": \"mrkdwn\",\n\t\t\t\"text\": \"<https://example.com|View request>\"\n\t\t}\n\t}\n]")
        JsonNode blockContent,
    @TemplateProperty(
            id = "data.documents",
            group = "message",
            label = "attachments",
            feel = FeelMode.required,
            binding = @PropertyBinding(name = "data.documents"),
            type = TemplateProperty.PropertyType.String,
            optional = true,
            description =
                "<a href=\"https://docs.camunda.io/docs/apis-tools/camunda-api-rest/specifications/upload-document-alpha/\">Camunda documents</a> can be added as attachments")
        List<Document> documents)
    implements SlackRequestData {
  @Override
  public SlackResponse invoke(MethodsClient methodsClient) throws IOException, SlackApiException {
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
    if (StringUtils.isNotBlank(thread)) {
      requestBuilder.threadTs(thread);
    }
    if (MessageType.plainText.equals(messageType)) {
      requestBuilder.text(text);
      if (documents != null && !documents.isEmpty()) {
        requestBuilder.blocks(
            BlockBuilder.create(new FileUploader(methodsClient))
                .documents(documents)
                .getLayoutBlocks());
      }
    } else {
      requestBuilder.blocks(
          BlockBuilder.create(new FileUploader(methodsClient))
              .documents(documents)
              .text(text)
              .blockContent(blockContent)
              .getLayoutBlocks());
    }
    ChatPostMessageResponse chatPostMessageResponse =
        methodsClient.chatPostMessage(requestBuilder.build());
    if (chatPostMessageResponse.isOk()) {
      return new ChatPostMessageSlackResponse(chatPostMessageResponse);
    } else {
      String error = chatPostMessageResponse.getError();
      Object errors = chatPostMessageResponse.getErrors();
      String errorMessage =
          (error != null ? error : "Unknown error")
              + (errors != null
                  ? " caused by:" + errors.toString()
                  : "No additional error details");
      throw new RuntimeException(errorMessage);
    }
  }

  @AssertTrue(message = "Text or block content required to post a message")
  private boolean isContentSupplied() {
    return StringUtils.isNotBlank(text) || blockContent != null;
  }
}
