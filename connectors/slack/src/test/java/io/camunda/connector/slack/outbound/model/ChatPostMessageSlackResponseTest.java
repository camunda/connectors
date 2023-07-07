/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatPostMessageSlackResponseTest {
  private static final String CHANNEL = "channel";
  private static final String TEAM = "team";
  private static final String APP_ID = "app-id";
  private static final String TYPE = "type";
  private static final String TEXT = "text";
  private static final String USER = "user";
  private static final String BOT_ID = "bot id";
  private static final String TS = "_ ts _";

  @Mock private ChatPostMessageResponse chatPostMessageResponse;
  @Mock private Message message;

  @Test
  public void shouldReturnCorrectResponse() {
    // Given
    when(chatPostMessageResponse.getMessage()).thenReturn(message);
    when(chatPostMessageResponse.getChannel()).thenReturn(CHANNEL);
    when(chatPostMessageResponse.getTs()).thenReturn(TS);
    when(message.getTeam()).thenReturn(TEAM);
    when(message.getAppId()).thenReturn(APP_ID);
    when(message.getType()).thenReturn(TYPE);
    when(message.getText()).thenReturn(TEXT);
    when(message.getUser()).thenReturn(USER);
    when(message.getBotId()).thenReturn(BOT_ID);
    when(message.getTs()).thenReturn(TS);
    // When
    ChatPostMessageSlackResponse response =
        new ChatPostMessageSlackResponse(chatPostMessageResponse);
    // Then
    assertThat(response.getTs()).isEqualTo(TS);
    assertThat(response.getChannel()).isEqualTo(CHANNEL);

    ChatPostMessageSlackResponse.Message responseMessage = response.getMessage();
    assertThat(responseMessage.getTeam()).isEqualTo(TEAM);
    assertThat(responseMessage.getAppId()).isEqualTo(APP_ID);
    assertThat(responseMessage.getType()).isEqualTo(TYPE);
    assertThat(responseMessage.getText()).isEqualTo(TEXT);
    assertThat(responseMessage.getUser()).isEqualTo(USER);
    assertThat(responseMessage.getBotId()).isEqualTo(BOT_ID);
    assertThat(responseMessage.getTs()).isEqualTo(TS);
  }
}
