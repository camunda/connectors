/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sendgrid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.impl.ConnectorInputException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class SendGridRequestTest extends BaseTest {

  private SendGridRequest sendGridRequest;
  private OutboundConnectorContext context;

  @ParameterizedTest(name = "Validate null field # {index}")
  @MethodSource("failRequestCases")
  void validate_shouldThrowExceptionWhenLeastOneNotExistRequestField(String input) {
    // Given request without one required field
    sendGridRequest = gson.fromJson(input, SendGridRequest.class);
    context = getContextBuilderWithSecrets().variables(sendGridRequest).build();
    // When context.validate(sendGridRequest);
    // Then expect exception that one required field not set
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.validate(sendGridRequest),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
  }

  @ParameterizedTest(name = "Should replace secrets in request")
  @MethodSource("successReplaceSecretsRequestCases")
  void replaceSecrets_shouldReplaceSecretsWhenExistRequest(String input) {
    // Given request with secrets
    sendGridRequest = gson.fromJson(input, SendGridRequest.class);
    context = getContextBuilderWithSecrets().variables(sendGridRequest).build();
    // When
    context.replaceSecrets(sendGridRequest);
    // Then should replace secrets
    assertThat(sendGridRequest.getApiKey()).isEqualTo(ActualValue.API_KEY);
    assertThat(sendGridRequest.getFrom().getEmail()).isEqualTo(ActualValue.SENDER_EMAIL);
    assertThat(sendGridRequest.getFrom().getName()).isEqualTo(ActualValue.SENDER_NAME);
    assertThat(sendGridRequest.getTo().getEmail()).isEqualTo(ActualValue.RECEIVER_EMAIL);
    assertThat(sendGridRequest.getTo().getName()).isEqualTo(ActualValue.RECEIVER_NAME);
  }

  @ParameterizedTest(name = "Should replace secrets in content")
  @MethodSource("successReplaceSecretsContentRequestCases")
  void replaceSecrets_shouldReplaceSecretsWhenExistContentRequest(String input) {
    // Given request with secrets
    sendGridRequest = gson.fromJson(input, SendGridRequest.class);
    context = getContextBuilderWithSecrets().variables(sendGridRequest).build();
    // When
    context.replaceSecrets(sendGridRequest);
    // Then should replace secrets
    assertThat(sendGridRequest.getContent().getSubject()).isEqualTo(ActualValue.Content.SUBJECT);
    assertThat(sendGridRequest.getContent().getType()).isEqualTo(ActualValue.Content.TYPE);
    assertThat(sendGridRequest.getContent().getValue()).isEqualTo(ActualValue.Content.VALUE);
  }

  @ParameterizedTest(name = "Should replace secrets in template")
  @MethodSource("successReplaceSecretsTemplateRequestCases")
  void replaceSecrets_shouldReplaceSecretsWhenExistTemplateRequest(String input) {
    // Given request with secrets
    sendGridRequest = gson.fromJson(input, SendGridRequest.class);
    context = getContextBuilderWithSecrets().variables(sendGridRequest).build();
    // When
    context.replaceSecrets(sendGridRequest);
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
