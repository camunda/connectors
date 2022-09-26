/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SlackConnectorRequestTest extends BaseTest {

  private SlackRequest request;
  private OutboundConnectorContext context;

  @BeforeEach
  public void beforeEach() {
    request = new SlackRequest();
    context =
        OutboundConnectorContextBuilder.create()
            .secret(TOKEN_KEY, ACTUAL_TOKEN)
            .secret(METHOD, ACTUAL_METHOD)
            .secret(CHANNEL_KEY, ACTUAL_CHANNEL)
            .secret(TEXT_KEY, ACTUAL_TEXT)
            .build();
  }

  @Test
  void validate_shouldThrowExceptionWhenLeastOneNotExistRequestField() {
    request.setToken(TOKEN);
    request.setMethod(METHOD);
    request.setData(null);
    // When context validate request
    // Then expect ConnectorInputException
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.validate(request),
            "ConnectorInputException was expected");
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
  }

  @Test
  void replaceSecrets_shouldDoNotReplaceMethod() {
    request.setMethod(ACTUAL_METHOD);
    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create().secret(ACTUAL_METHOD, METHOD).build();
    context.replaceSecrets(request);
    assertThat(request.getMethod()).isEqualTo(ACTUAL_METHOD);
  }

  @Test
  void replaceSecrets_shouldReplaceSecrets() {
    ChatPostMessageData chatPostMessageData = new ChatPostMessageData();
    chatPostMessageData.setChannel(SECRETS + CHANNEL_KEY);
    chatPostMessageData.setText(SECRETS + TEXT_KEY);
    request.setToken(SECRETS + TOKEN_KEY);
    request.setMethod(ACTUAL_METHOD);
    request.setData(chatPostMessageData);
    context.replaceSecrets(request);
    assertThat(request.getMethod()).isEqualTo(ACTUAL_METHOD);
    assertThat(request.getToken()).isEqualTo(ACTUAL_TOKEN);
    assertThat(request.getData()).isInstanceOf(ChatPostMessageData.class);
  }
}
