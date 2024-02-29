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
import com.microsoft.graph.requests.ConversationMemberCollectionPage;
import com.microsoft.graph.requests.ConversationMemberCollectionResponse;
import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.model.Member;
import io.camunda.connector.model.request.chat.CreateChat;
import java.util.LinkedList;
import okhttp3.Request;

public final class CreateChatChatOperation implements ChatOperation {
  private final CreateChat model;

  public CreateChatChatOperation(final CreateChat createChat) {
    this.model = createChat;
  }

  @Override
  public Object invoke(final GraphServiceClient<Request> graphClient) {

    Chat chat = new Chat();
    chat.chatType = ChatType.valueOf(model.chatType().toUpperCase());
    LinkedList<ConversationMember> membersList = new LinkedList<>();

    for (Member member : model.members()) {
      AadUserConversationMember conversationMembers = new AadUserConversationMember();
      conversationMembers.roles = member.getRoles();
      conversationMembers
          .additionalDataManager()
          .put(Member.USER_DATA_BIND, member.getAsGraphJsonPrimitive());
      conversationMembers
          .additionalDataManager()
          .put(Member.USER_DATA_TYPE, Member.USER_CONVERSATION_MEMBER);
      membersList.add(conversationMembers);
    }

    ConversationMemberCollectionResponse conversationMemberCollectionResponse =
        new ConversationMemberCollectionResponse();
    conversationMemberCollectionResponse.value = membersList;
    chat.members = new ConversationMemberCollectionPage(conversationMemberCollectionResponse, null);

    return graphClient.chats().buildRequest().post(chat);
  }
}
