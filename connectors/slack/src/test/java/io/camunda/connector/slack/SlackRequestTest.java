/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.slack.outbound.SlackRequest;
import io.camunda.connector.slack.outbound.model.ChatPostMessageData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SlackRequestTest extends BaseTest {

  @ParameterizedTest()
  @MethodSource("replaceSecretsSuccessTestCases")
  public void replaceSecrets_shouldReplaceSecrets(String input) {
    // Given
    context = getContextBuilderWithSecrets().variables(input).build();
    // When
    SlackRequest<ChatPostMessageData> requestData = context.bindVariables(SlackRequest.class);
    // Then
    assertThat(requestData.getToken()).isEqualTo(ActualValue.TOKEN);
    assertThat(requestData.getData().getChannel()).isEqualTo(ActualValue.ChatPostMessageData.EMAIL);
    assertThat(requestData.getData().getText()).contains(ActualValue.ChatPostMessageData.TEXT);
  }

  @ParameterizedTest()
  @MethodSource("validateRequiredFieldsFailTestCases")
  void validate_shouldThrowExceptionWhenLeastRequestFieldOneNotExist(String input) {
    context = getContextBuilderWithSecrets().variables(input).build();
    assertThrows(
        Throwable.class, () -> context.bindVariables(SlackRequest.class), "Exception was expected");
  }

  @ParameterizedTest()
  @ValueSource(strings = {"", "  ", "     "})
  void validate_shouldThrowExceptionWhenMethodIsBlank(String input) {
    context = getContextBuilderWithSecrets().variables("{}").build();
    // When context validate request
    // Then expect ConnectorInputException
    assertThrows(
        ConnectorInputException.class,
        () -> context.bindVariables(SlackRequest.class),
        "ConnectorInputException was expected");
  }
}
