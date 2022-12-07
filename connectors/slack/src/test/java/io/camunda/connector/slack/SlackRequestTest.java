/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.slack.model.ChatPostMessageData;
import io.camunda.connector.slack.model.ChatPostMessageSlackResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SlackRequestTest extends BaseTest {

  @ParameterizedTest()
  @MethodSource("replaceSecretsSuccessTestCases")
  public void replaceSecrets_shouldReplaceSecrets(String input) {
    // Given
    SlackRequest<ChatPostMessageData> requestData = gson.fromJson(input, SlackRequest.class);
    context = getContextBuilderWithSecrets().build();
    // When
    context.replaceSecrets(requestData);
    // Then
    assertThat(requestData.getToken()).isEqualTo(ActualValue.TOKEN);
    assertThat(requestData.getMethod()).isEqualTo(ActualValue.METHOD);
    assertThat(requestData.getData().getChannel()).isEqualTo(ActualValue.ChatPostMessageData.EMAIL);
    assertThat(requestData.getData().getText()).contains(ActualValue.ChatPostMessageData.TEXT);
  }

  @ParameterizedTest()
  @MethodSource("validateRequiredFieldsFailTestCases")
  void validate_shouldThrowExceptionWhenLeastRequestFieldOneNotExist(String input) {
    SlackRequest requestData = gson.fromJson(input, SlackRequest.class);
    context = getContextBuilderWithSecrets().build();
    // When context validate request
    // Then expect ConnectorInputException
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.validate(requestData),
            "ConnectorInputException was expected");
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
  }

  @ParameterizedTest()
  @ValueSource(strings = {"", "  ", "     "})
  void validate_shouldThrowExceptionWhenMethodIsBlank(String input) {
    // Given
    SlackRequest<ChatPostMessageData> slackRequest = new SlackRequest();
    slackRequest.setData(
        new ChatPostMessageData() {
          @Override
          public SlackResponse invoke(final MethodsClient methodsClient) {
            return new ChatPostMessageSlackResponse(new ChatPostMessageResponse());
          }
        });
    slackRequest.setMethod(input);
    slackRequest.setToken(ActualValue.TOKEN);
    context = getContextBuilderWithSecrets().build();
    // When context validate request
    // Then expect ConnectorInputException
    assertThrows(
        ConnectorInputException.class,
        () -> context.validate(slackRequest),
        "ConnectorInputException was expected");
  }
}
