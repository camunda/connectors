/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.microsoft.graph.models.Channel;
import com.microsoft.graph.models.ConversationMember;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.teams.TeamsRequestBuilder;
import com.microsoft.graph.teams.item.TeamItemRequestBuilder;
import com.microsoft.graph.teams.item.channels.ChannelsRequestBuilder;
import io.camunda.connector.BaseTest;
import io.camunda.connector.model.Member;
import io.camunda.connector.model.request.data.CreateChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateChannelTest extends BaseTest {

  private CreateChannel createChannel;
  @Mock private GraphServiceClient graphServiceClient;

  @Mock private TeamsRequestBuilder teamsRequestBuilder;
  @Mock private TeamItemRequestBuilder teamItemRequestBuilder;
  @Mock private ChannelsRequestBuilder channelsRequestBuilder;
  @Captor private ArgumentCaptor<Channel> channelArgumentCaptor;

  @BeforeEach
  public void init() {
    createChannel =
        new CreateChannel(
            ActualValue.Channel.GROUP_ID,
            ActualValue.Channel.NAME,
            ActualValue.Channel.DESCRIPTION,
            ActualValue.Channel.CHANNEL_TYPE_STANDARD,
            ActualValue.Channel.OWNER);
    when(graphServiceClient.teams()).thenReturn(teamsRequestBuilder);
    when(teamsRequestBuilder.byTeamId(ActualValue.Channel.GROUP_ID))
        .thenReturn(teamItemRequestBuilder);
    when(teamItemRequestBuilder.channels()).thenReturn(channelsRequestBuilder);
    when(channelsRequestBuilder.post(channelArgumentCaptor.capture())).thenReturn(new Channel());
  }

  @ParameterizedTest
  @ValueSource(strings = {"private", "shared"})
  public void invoke_shouldReturnChannelAndSetMembersWhenChannelTypeIsNotStandard(
      String channelType) {
    // Given
    createChannel =
        new CreateChannel(
            ActualValue.Channel.GROUP_ID,
            ActualValue.Channel.NAME,
            ActualValue.Channel.DESCRIPTION,
            channelType,
            ActualValue.Channel.OWNER);
    // When
    Object result = operationFactory.getService(createChannel).invoke(graphServiceClient);
    // Then
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(Channel.class);
    Channel value = channelArgumentCaptor.getValue();
    assertThat(value.getDisplayName()).isEqualTo(ActualValue.Channel.NAME);
    assertThat(value.getDescription()).isEqualTo(ActualValue.Channel.DESCRIPTION);

    ConversationMember member = value.getMembers().getFirst();

    assertThat(member.getRoles().getFirst()).isEqualTo(Member.OWNER_ROLES.getFirst());
    assertThat(member.getAdditionalData().get(Member.USER_DATA_BIND))
        .isEqualTo(Member.toAdditionalDataValue(ActualValue.Channel.OWNER));
  }

  @Test
  public void invoke__shouldReturnChannelAndSetMembersWhenChannelTypeIsStandard() {
    // Given
    createChannel =
        new CreateChannel(
            ActualValue.Channel.GROUP_ID,
            ActualValue.Channel.NAME,
            ActualValue.Channel.DESCRIPTION,
            ActualValue.Channel.CHANNEL_TYPE_STANDARD,
            ActualValue.Channel.OWNER);
    // When
    Object result = operationFactory.getService(createChannel).invoke(graphServiceClient);
    // Then
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(Channel.class);
    Channel value = channelArgumentCaptor.getValue();
    assertThat(value.getDisplayName()).isEqualTo(ActualValue.Channel.NAME);
    assertThat(value.getDescription()).isEqualTo(ActualValue.Channel.DESCRIPTION);
    assertThat(value.getMembers()).isNull();
  }

  @ParameterizedTest
  @MethodSource("createChannelValidationFailTestCases")
  public void validate_shouldThrowExceptionWhenLeastOneRequiredFieldNotExist(String input) {
    assertValidationException(input);
  }
}
