/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.graph.models.Channel;
import com.microsoft.graph.models.ConversationMember;
import com.microsoft.graph.requests.ChannelCollectionRequest;
import com.microsoft.graph.requests.ChannelCollectionRequestBuilder;
import com.microsoft.graph.requests.ConversationMemberCollectionPage;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.TeamRequestBuilder;
import io.camunda.connector.BaseTest;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.model.MSTeamsRequest;
import io.camunda.connector.model.Member;
import okhttp3.Request;
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
  @Mock private GraphServiceClient<Request> graphServiceClient;

  @Mock private TeamRequestBuilder teamRequestBuilder;
  @Mock private ChannelCollectionRequestBuilder channelCollectionRequestBuilder;
  @Mock private ChannelCollectionRequest channelCollectionRequest;
  @Captor private ArgumentCaptor<Channel> channelArgumentCaptor;

  @BeforeEach
  public void init() {
    createChannel = new CreateChannel();
    createChannel.setChannelType(ActualValue.Channel.CHANNEL_TYPE_STANDARD);
    createChannel.setDescription(ActualValue.Channel.DESCRIPTION);
    createChannel.setName(ActualValue.Channel.NAME);
    createChannel.setGroupId(ActualValue.Channel.GROUP_ID);
    createChannel.setOwner(ActualValue.Channel.OWNER);
    when(graphServiceClient.teams(ActualValue.Channel.GROUP_ID)).thenReturn(teamRequestBuilder);
    when(teamRequestBuilder.channels()).thenReturn(channelCollectionRequestBuilder);
    when(channelCollectionRequestBuilder.buildRequest()).thenReturn(channelCollectionRequest);
    when(channelCollectionRequest.post(channelArgumentCaptor.capture())).thenReturn(new Channel());
  }

  @ParameterizedTest
  @ValueSource(strings = {"private", "shared"})
  public void invoke_shouldReturnChannelAndSetMembersWhenChannelTypeIsNotStandard(
      String channelType) {
    // Given
    createChannel.setChannelType(channelType);
    // When
    Channel invoke = createChannel.invoke(graphServiceClient);
    // Then
    assertThat(invoke).isNotNull();
    Channel value = channelArgumentCaptor.getValue();
    assertThat(value.displayName).isEqualTo(ActualValue.Channel.NAME);
    assertThat(value.description).isEqualTo(ActualValue.Channel.DESCRIPTION);

    ConversationMemberCollectionPage members = value.members;
    ConversationMember conversationMember = members.getCurrentPage().get(0);
    assertThat(conversationMember.roles.get(0)).isEqualTo(Member.OWNER_ROLES.get(0));

    assertThat(conversationMember.additionalDataManager().get(Member.USER_DATA_TYPE))
        .isEqualTo(Member.USER_CONVERSATION_MEMBER);
    assertThat(conversationMember.additionalDataManager().get(Member.USER_DATA_BIND))
        .isEqualTo(Member.toGraphJsonPrimitive(ActualValue.Channel.OWNER));
  }

  @Test
  public void invoke__shouldReturnChannelAndSetMembersWhenChannelTypeIsStandard() {
    // Given
    createChannel.setChannelType(ActualValue.Channel.CHANNEL_TYPE_STANDARD);
    // When
    Channel invoke = createChannel.invoke(graphServiceClient);
    // Then
    assertThat(invoke).isNotNull();
    Channel value = channelArgumentCaptor.getValue();
    assertThat(value.displayName).isEqualTo(ActualValue.Channel.NAME);
    assertThat(value.description).isEqualTo(ActualValue.Channel.DESCRIPTION);
    assertThat(value.members).isNull();
  }

  @ParameterizedTest
  @MethodSource("createChannelValidationFailTestCases")
  public void validate_shouldThrowExceptionWhenLeastOneRequiredFieldNotExist(String input)
      throws JsonProcessingException {
    // Given request without one required field
    MSTeamsRequest request = objectMapper.readValue(input, MSTeamsRequest.class);
    OutboundConnectorContext context = getContextBuilderWithSecrets().variables(input).build();
    // When context.validate;
    // Then expect exception that one required field not set
    assertThat(request.getData()).isInstanceOf(CreateChannel.class);
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.validate(request.getData()),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
  }
}
