/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.rabbitmq.outbound.model.RabbitMqRequest;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RabbitMqRequestValidationTest extends OutboundBaseTest {

  private OutboundConnectorContext context;

  @ParameterizedTest
  @MethodSource("failValidationRequiredFieldsTest")
  void validate_shouldThrowExceptionWhenLeastOneNotExistRequestField(String input) {
    // Given request without one required field
    context =
        getContextBuilderWithSecrets()
            .validation(new DefaultValidationProvider())
            .variables(input)
            .build();
    // When context.validate(sendGridRequest);
    // Then expect exception that one required field not set
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.bindVariables(RabbitMqRequest.class),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
  }
}
