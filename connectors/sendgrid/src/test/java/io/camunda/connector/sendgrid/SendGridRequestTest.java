/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sendgrid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.impl.ConnectorInputException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class SendGridRequestTest extends BaseTest {

  @ParameterizedTest(name = "Validate null field # {index}")
  @MethodSource("failRequestCases")
  void validate_shouldThrowExceptionWhenLeastOneNotExistRequestField(String input) {
    // Given request without one required field
    var context = getContextBuilderWithSecrets().variables(input).build();
    // When context.validate(sendGridRequest);
    // Then expect exception that one required field not set
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.bindVariables(SendGridRequest.class),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
  }

  @ParameterizedTest
  @MethodSource("failTestWithWrongSenderEmail")
  public void validate_shouldThrowExceptionWhenSenderEmailIsBlankOrNull(String input) {
    // Given request without one required field
    var context = getContextBuilderWithSecrets().variables(input).build();
    // When context.validate(sendGridRequest);
    // Then expect exception that one required field not set
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.bindVariables(SendGridRequest.class),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage()).contains("senderEmail");
  }

  @ParameterizedTest
  @MethodSource("failTestWithWrongSenderName")
  public void validate_shouldThrowExceptionWhenSenderNameIsBlankOrNull(String input) {
    // Given request without one required field
    var context = getContextBuilderWithSecrets().variables(input).build();
    // When context.validate(sendGridRequest);
    // Then expect exception that one required field not set
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.bindVariables(SendGridRequest.class),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage()).contains("senderName");
  }

  @ParameterizedTest
  @MethodSource("failTestWithWrongReceiverEmail")
  public void validate_shouldThrowExceptionWhenReceiverEmailIsBlankOrNull(String input) {
    // Given request without one required field
    var context = getContextBuilderWithSecrets().variables(input).build();
    // When context.validate(sendGridRequest);
    // Then expect exception that one required field not set
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.bindVariables(SendGridRequest.class),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage()).contains("receiverEmail");
  }

  @ParameterizedTest
  @MethodSource("failTestWithWrongReceiverName")
  public void validate_shouldThrowExceptionWhenReceiverNameIsBlankOrNull(String input) {
    // Given request without one required field
    var context = getContextBuilderWithSecrets().variables(input).build();
    // When context.validate(sendGridRequest);
    // Then expect exception that one required field not set
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.bindVariables(SendGridRequest.class),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage()).contains("receiverName");
  }

  @ParameterizedTest(name = "Should replace secrets in template")
  @MethodSource("successReplaceSecretsTemplateRequestCases")
  void replaceSecrets_shouldReplaceSecretsWhenExistTemplateRequest(String input) {
    // Given request with secrets
    var context = getContextBuilderWithSecrets().variables(input).build();
    // When
    var sendGridRequest = context.bindVariables(SendGridRequest.class);
    // Then should replace secrets
    assertThat(sendGridRequest.getTemplate().getId()).isEqualTo(ActualValue.Template.ID);
    assertThat(
            sendGridRequest.getTemplate().getData().get(ActualValue.Template.Data.KEY_SHIP_ADDRESS))
        .isEqualTo(ActualValue.Template.Data.SHIP_ADDRESS);
    assertThat(
            sendGridRequest.getTemplate().getData().get(ActualValue.Template.Data.KEY_ACCOUNT_NAME))
        .isEqualTo(ActualValue.Template.Data.ACCOUNT_NAME);
    assertThat(sendGridRequest.getTemplate().getData().get(ActualValue.Template.Data.KEY_SHIP_ZIP))
        .isEqualTo(ActualValue.Template.Data.SHIP_ZIP);
  }
}
