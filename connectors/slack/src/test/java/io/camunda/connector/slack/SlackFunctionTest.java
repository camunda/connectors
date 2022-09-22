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
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.Message;
import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.test.ConnectorContextBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class SlackFunctionTest extends BaseTest {

  private ConnectorContext context;
  private Slack slack;

  @BeforeEach
  public void init() {
    slack = Mockito.mock(Slack.class);

    SlackRequest request = new SlackRequest();
    request.setToken(SECRETS + TOKEN_KEY);
    request.setMethod(ACTUAL_METHOD);

    ChatPostMessageData chatPostMessageData = new ChatPostMessageData();
    chatPostMessageData.setChannel(SECRETS + CHANNEL_KEY);
    chatPostMessageData.setText(SECRETS + TEXT_KEY);
    request.setData(chatPostMessageData);

    context =
        ConnectorContextBuilder.create()
            .secret(TOKEN_KEY, ACTUAL_TOKEN)
            .secret(CHANNEL_KEY, ACTUAL_CHANNEL)
            .secret(TEXT_KEY, ACTUAL_TEXT)
            .variables(GSON.toJson(request))
            .build();
  }

  @Test
  public void execute_shouldExecuteRequestAndReturnResult() throws Exception {
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
}
