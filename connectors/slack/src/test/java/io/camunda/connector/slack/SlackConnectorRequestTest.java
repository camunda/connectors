/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.api.Validator;
import io.camunda.connector.test.ConnectorContextBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SlackConnectorRequestTest extends BaseTest {

  private SlackRequest request;
  private Validator validator;
  private ConnectorContext context;

  @BeforeEach
  public void beforeEach() {
    request = new SlackRequest();
    validator = new Validator();
    context =
        ConnectorContextBuilder.create()
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
    request.validateWith(validator);
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> validator.evaluate(),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage())
        .isEqualTo("Evaluation failed with following errors: Property required: Slack API - Data");
  }

  @Test
  void replaceSecrets_shouldDoNotReplaceMethod() {
    request.setMethod(ACTUAL_METHOD);
    ConnectorContext context =
        ConnectorContextBuilder.create().secret(ACTUAL_METHOD, METHOD).build();
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
