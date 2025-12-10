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
import com.microsoft.graph.models.ConversationMember;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import io.camunda.connector.model.Member;
import io.camunda.connector.model.request.data.CreateChannel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;

public record CreateChannelOperation(CreateChannel model) implements ChannelOperation {
  @Override
  public Channel invoke(final GraphServiceClient graphClient) {

    ChannelMembershipType type =
        ChannelMembershipType.forValue(model.channelType().toLowerCase(Locale.ROOT));

    Channel channel = new Channel();
    channel.setOdataType("#Microsoft.Graph.channel");
    channel.setMembershipType(type);
    channel.setDisplayName(model.name());
    channel.setDescription(model.description());

    if (type != ChannelMembershipType.Standard) {
      LinkedList<ConversationMember> members = new LinkedList<ConversationMember>();
      AadUserConversationMember conversationMember = new AadUserConversationMember();

      conversationMember.setRoles(Member.OWNER_ROLES);
      HashMap<String, Object> additionalData = new HashMap<String, Object>();
      additionalData.put(Member.USER_DATA_BIND, Member.toAdditionalDataValue(model.owner()));
      conversationMember.setAdditionalData(additionalData);
      members.add(conversationMember);
      channel.setMembers(members);
    }

    return graphClient.teams().byTeamId(model.groupId()).channels().post(channel);
  }
}
