/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.mapper;

import io.camunda.connector.aws.bedrock.model.BedrockContent;
import io.camunda.connector.aws.bedrock.model.BedrockMessage;
import io.camunda.connector.api.document.Document;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

public class MessageMapper {

  private final BedrockContentMapper bedrockContentMapper;

  public MessageMapper(BedrockContentMapper bedrockContentMapper) {
    this.bedrockContentMapper = bedrockContentMapper;
  }

  public List<Message> mapToMessages(List<BedrockMessage> bedrockMessages) {
    if (bedrockMessages != null) {
      return bedrockMessages.stream().map(this::mapToMessage).toList();
    }
    return List.of();
  }

  public Message mapToMessage(BedrockMessage bedrockMessage) {
    return Message.builder()
        .content(bedrockContentMapper.mapToContentBlocks(bedrockMessage.getContentList()))
        .role(bedrockMessage.getRole())
        .build();
  }

  public BedrockMessage mapToBedrockMessage(Message message) {
    List<BedrockContent> bedrockContents =
        bedrockContentMapper.mapToBedrockContent(message.content());
    return new BedrockMessage(message.roleAsString(), bedrockContents);
  }

  public BedrockMessage mapToBedrockMessage(List<Document> documents, String newMessage) {
    String user = ConversationRole.USER.toString();
    List<BedrockContent> contentList =
        new ArrayList<>(bedrockContentMapper.documentsToBedrockContent(documents));
    contentList.add(bedrockContentMapper.messageToBedrockContent(newMessage));

    return new BedrockMessage(user, contentList);
  }
}
