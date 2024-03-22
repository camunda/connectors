/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation.chat;

import com.microsoft.graph.models.AadUserConversationMember;
import com.microsoft.graph.models.Chat;
import com.microsoft.graph.models.ChatType;
import com.microsoft.graph.models.ConversationMember;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import io.camunda.connector.model.Member;
import io.camunda.connector.model.request.data.CreateChat;
import java.util.HashMap;
import java.util.LinkedList;

public final class CreateChatOperation implements ChatOperation {
  private final CreateChat model;

  public CreateChatOperation(final CreateChat createChat) {
    this.model = createChat;
  }

  @Override
  public Object invoke(final GraphServiceClient graphClient) {

    Chat chat = new Chat();
    // backward compatibility
    var chatTypeString =
        model.chatType().equalsIgnoreCase("one_on_one") ? "oneOnOne" : model.chatType();

    chat.setChatType(ChatType.forValue(chatTypeString));
    if (model.topic() != null) {
      chat.setTopic(model.topic());
    }

    LinkedList<ConversationMember> membersList = new LinkedList<>();

    for (Member member : model.members()) {
      AadUserConversationMember conversationMember = new AadUserConversationMember();
      conversationMember.setRoles(member.getRoles());

      HashMap<String, Object> additionalData = new HashMap<>();
      additionalData.put(Member.USER_DATA_BIND, member.getAsAdditionalDataValue());
      conversationMember.setAdditionalData(additionalData);
      membersList.add(conversationMember);
    }

    chat.setMembers(membersList);

    return graphClient.chats().post(chat);
  }
}
