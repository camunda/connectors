/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.conversations.ConversationsCreateRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.conversations.ConversationsCreateResponse;
import com.slack.api.model.Conversation;
import com.slack.api.model.Message;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.slack.ConversationsCreateData.Visibility;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class SlackFunctionTest extends BaseTest {

  private OutboundConnectorContext context;
  private Slack slack;

  @BeforeEach
  public void init() {
    slack = Mockito.mock(Slack.class);
  }

  @Test
  public void chatPost_shouldExecuteRequestAndReturnResult() throws Exception {
    // given
    SlackFunction slackFunction = new SlackFunction(slack);

    ChatPostMessageResponse expectedResponse = new ChatPostMessageResponse();
    expectedResponse.setOk(true);
    expectedResponse.setTs("Test");
    expectedResponse.setChannel("@test");
    expectedResponse.setMessage(new Message());

    MethodsClient methodsClient = Mockito.mock(MethodsClient.class);
    when(methodsClient.chatPostMessage(any(ChatPostMessageRequest.class)))
        .thenReturn(expectedResponse);

    when(slack.methods(ACTUAL_TOKEN)).thenReturn(methodsClient);

    // when
    var chatPostMessageData = new ChatPostMessageData();
    chatPostMessageData.setChannel(SECRETS + CHANNEL_KEY);
    chatPostMessageData.setText(SECRETS + TEXT_KEY);
    provideContext(chatPostMessageData, ACTUAL_POST_MESSAGE_METHOD);

    Object actualResponse = slackFunction.execute(context);

    // then
    Assertions.assertThat(actualResponse).isInstanceOf(ChatPostMessageSlackResponse.class);
    Assertions.assertThat(actualResponse).isInstanceOf(SlackResponse.class);
    ChatPostMessageSlackResponse actualResponseAsObject =
        (ChatPostMessageSlackResponse) actualResponse;
    Assertions.assertThat(actualResponseAsObject.getChannel())
        .isEqualTo(expectedResponse.getChannel());
    Assertions.assertThat(actualResponseAsObject.getTs()).isEqualTo(expectedResponse.getTs());
    Assertions.assertThat(actualResponseAsObject.getMessage().getText())
        .isEqualTo(expectedResponse.getMessage().getText());
  }

  @Test
  public void createChannel_shouldExecuteRequestAndReturnResult() throws Exception {
    // given
    SlackFunction slackFunction = new SlackFunction(slack);

    var expectedResponse = new ConversationsCreateResponse();
    var conversation = new Conversation();
    conversation.setId(UUID.randomUUID().toString());
    conversation.setName("test-channel");
    expectedResponse.setOk(true);
    expectedResponse.setChannel(conversation);

    MethodsClient methodsClient = Mockito.mock(MethodsClient.class);
    when(methodsClient.conversationsCreate(any(ConversationsCreateRequest.class)))
        .thenReturn(expectedResponse);

    when(slack.methods(ACTUAL_TOKEN)).thenReturn(methodsClient);

    // when
    var conversationsCreateData = new ConversationsCreateData();
    conversationsCreateData.setNewChannelName(SECRETS + CHANNEL_KEY);
    conversationsCreateData.setVisibility(Visibility.PUBLIC);
    provideContext(conversationsCreateData, ACTUAL_CREATE_CHANNEL_METHOD);

    Object actualResponse = slackFunction.execute(context);

    // then
    Assertions.assertThat(actualResponse).isInstanceOf(ConversationsCreateSlackResponse.class);
    Assertions.assertThat(actualResponse).isInstanceOf(SlackResponse.class);
    ConversationsCreateSlackResponse actualResponseAsObject =
        (ConversationsCreateSlackResponse) actualResponse;
    Assertions.assertThat(actualResponseAsObject.getChannel().getId())
        .isEqualTo(expectedResponse.getChannel().getId());
    Assertions.assertThat(actualResponseAsObject.getChannel().getName())
        .isEqualTo(expectedResponse.getChannel().getName());
  }

  private <T extends SlackRequestData> void provideContext(T data, String method) {
    SlackRequest<T> request = new SlackRequest<>();
    request.setToken(SECRETS + TOKEN_KEY);
    request.setMethod(method);
    request.setData(data);

    context =
        OutboundConnectorContextBuilder.create()
            .secret(TOKEN_KEY, ACTUAL_TOKEN)
            .secret(CHANNEL_KEY, ACTUAL_CHANNEL)
            .secret(TEXT_KEY, ACTUAL_TEXT)
            .variables(GSON.toJson(request))
            .build();
  }
}
