/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation.channel;

import com.microsoft.graph.models.AadUserConversationMember;
import com.microsoft.graph.models.Channel;
import com.microsoft.graph.models.ChannelMembershipType;
import com.microsoft.graph.requests.ConversationMemberCollectionPage;
import com.microsoft.graph.requests.ConversationMemberCollectionResponse;
import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.model.Member;
import io.camunda.connector.model.request.data.CreateChannel;
import java.util.List;
import okhttp3.Request;

public record CreateChannelOperation(CreateChannel model) implements ChannelOperation {
  @Override
  public Channel invoke(final GraphServiceClient<Request> graphClient) {
    ChannelMembershipType type = ChannelMembershipType.valueOf(model.channelType().toUpperCase());
    Channel channel = new Channel();
    channel.displayName = model.name();
    channel.description = model.description();
    channel.membershipType = type;

    if (type != ChannelMembershipType.STANDARD) {

      AadUserConversationMember members = new AadUserConversationMember();
      members
          .additionalDataManager()
          .put(Member.USER_DATA_BIND, Member.toGraphJsonPrimitive(model.owner()));
      members.additionalDataManager().put(Member.USER_DATA_TYPE, Member.USER_CONVERSATION_MEMBER);
      members.roles = Member.OWNER_ROLES;

      ConversationMemberCollectionResponse conversationMemberCollectionResponse =
          new ConversationMemberCollectionResponse();
      conversationMemberCollectionResponse.value = List.of(members);

      channel.members =
          new ConversationMemberCollectionPage(conversationMemberCollectionResponse, null);
    }

    return graphClient.teams(model.groupId()).channels().buildRequest().post(channel);
  }
}
