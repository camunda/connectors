/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model;

import static io.camunda.connector.jdbc.outbound.OutboundBaseTest.getContextBuilderWithSecrets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.jdbc.BaseTest;
import io.camunda.connector.jdbc.model.request.JdbcRequest;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class JdbcRequestTest extends BaseTest {
  private OutboundConnectorContext context;

  @ParameterizedTest
  @MethodSource("failRequestValidationTestCases")
  void shouldThrowExceptionWhenMissingOrBadRequestField(String input) {
    // Given request without one required field
    context =
        getContextBuilderWithSecrets()
            .validation(new DefaultValidationProvider())
            .variables(input)
            .build();
    // When context.validate(JdbcRequest);
    // Then expect exception that one required field not set
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.bindVariables(JdbcRequest.class),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
  }
}
