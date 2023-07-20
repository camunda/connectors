/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.chat;

import com.microsoft.graph.models.AadUserConversationMember;
import com.microsoft.graph.models.Chat;
import com.microsoft.graph.models.ChatType;
import com.microsoft.graph.models.ConversationMember;
import com.microsoft.graph.requests.ConversationMemberCollectionPage;
import com.microsoft.graph.requests.ConversationMemberCollectionResponse;
import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.model.Member;
import io.camunda.connector.model.request.MSTeamsRequestData;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import okhttp3.Request;

public class CreateChat extends MSTeamsRequestData {

  @NotBlank private String chatType;
  @NotNull @Secret private List<Member> members;

  @Override
  public Object invoke(final GraphServiceClient<Request> graphClient) {

    Chat chat = new Chat();
    chat.chatType = ChatType.valueOf(chatType.toUpperCase());
    LinkedList<ConversationMember> membersList = new LinkedList<>();

    for (Member member : members) {
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

  public String getChatType() {
    return chatType;
  }

  public void setChatType(final String chatType) {
    this.chatType = chatType;
  }

  public List<Member> getMembers() {
    return members;
  }

  public void setMembers(final List<Member> members) {
    this.members = members;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CreateChat that = (CreateChat) o;
    return Objects.equals(chatType, that.chatType) && Objects.equals(members, that.members);
  }

  @Override
  public int hashCode() {
    return Objects.hash(chatType, members);
  }

  @Override
  public String toString() {
    return "CreateChat{" + "chatType='" + chatType + "'" + ", members=" + members + "}";
  }
}
