/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.channel;

import com.microsoft.graph.models.AadUserConversationMember;
import com.microsoft.graph.models.Channel;
import com.microsoft.graph.models.ChannelMembershipType;
import com.microsoft.graph.requests.ConversationMemberCollectionPage;
import com.microsoft.graph.requests.ConversationMemberCollectionResponse;
import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.model.Member;
import io.camunda.connector.model.request.MSTeamsRequestData;
import java.util.List;
import java.util.Objects;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotBlank;
import okhttp3.Request;

public class CreateChannel extends MSTeamsRequestData {
  @NotBlank @Secret protected String groupId;
  @NotBlank @Secret private String name;
  @Secret private String description;
  @NotBlank private String channelType;
  private String owner;

  @AssertTrue(message = "property owner is required")
  private boolean isOwnerValid() {
    if (!ChannelMembershipType.STANDARD.name().equalsIgnoreCase(channelType)) {
      return owner != null && !owner.isBlank();
    }
    return true;
  }

  public Channel invoke(final GraphServiceClient<Request> graphClient) {
    ChannelMembershipType type = ChannelMembershipType.valueOf(channelType.toUpperCase());
    Channel channel = new Channel();
    channel.displayName = name;
    channel.description = description;
    channel.membershipType = type;

    if (type != ChannelMembershipType.STANDARD) {

      AadUserConversationMember members = new AadUserConversationMember();
      members
          .additionalDataManager()
          .put(Member.USER_DATA_BIND, Member.toGraphJsonPrimitive(owner));
      members.additionalDataManager().put(Member.USER_DATA_TYPE, Member.USER_CONVERSATION_MEMBER);
      members.roles = Member.OWNER_ROLES;

      ConversationMemberCollectionResponse conversationMemberCollectionResponse =
          new ConversationMemberCollectionResponse();
      conversationMemberCollectionResponse.value = List.of(members);

      channel.members =
          new ConversationMemberCollectionPage(conversationMemberCollectionResponse, null);
    }

    return graphClient.teams(groupId).channels().buildRequest().post(channel);
  }

  public String getGroupId() {
    return groupId;
  }

  public void setGroupId(final String groupId) {
    this.groupId = groupId;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String description) {
    this.description = description;
  }

  public String getChannelType() {
    return channelType;
  }

  public void setChannelType(final String channelType) {
    this.channelType = channelType;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(final String owner) {
    this.owner = owner;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CreateChannel that = (CreateChannel) o;
    return Objects.equals(groupId, that.groupId)
        && Objects.equals(name, that.name)
        && Objects.equals(description, that.description)
        && Objects.equals(channelType, that.channelType)
        && Objects.equals(owner, that.owner);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupId, name, description, channelType, owner);
  }

  @Override
  public String toString() {
    return "CreateChannel{"
        + "groupId='"
        + groupId
        + "'"
        + ", name='"
        + name
        + "'"
        + ", description='"
        + description
        + "'"
        + ", channelType='"
        + channelType
        + "'"
        + ", owner='"
        + owner
        + "'"
        + "}";
  }
}
