/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.request;

import static io.camunda.connector.jdbc.OutboundBaseTest.getContextBuilderWithSecrets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.jdbc.BaseTest;
import io.camunda.connector.jdbc.model.request.connection.DetailedConnection;
import io.camunda.connector.jdbc.model.request.connection.UriConnection;
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
            "ConnectorInputException was expected");
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
  }

  @ParameterizedTest
  @MethodSource("successTestCases")
  void shouldSucceedSuccessCases(String input) {
    // Given request with all required fields
    context =
        getContextBuilderWithSecrets()
            .validation(new DefaultValidationProvider())
            .variables(input)
            .build();
    // When context.validate(JdbcRequest);
    // Then expect no exception
    context.bindVariables(JdbcRequest.class);
  }

  @ParameterizedTest
  @MethodSource("successTestCasesWithSecrets")
  void shouldSucceedSecretsSuccessCases(String input) {
    // Given request with all required fields
    context =
        getContextBuilderWithSecrets()
            .validation(new DefaultValidationProvider())
            .variables(input)
            .build();
    // When context.validate(JdbcRequest);
    // Then expect no exception
    var request = context.bindVariables(JdbcRequest.class);
    assertThat(request.data().query()).isEqualTo(ActualValue.Data.Query.QUERY);
    assertThat(request.data().variables())
        .isEqualTo("[" + ActualValue.Data.Variables.VARIABLES + "]");
    if (request.connection() instanceof UriConnection c) {
      assertThat(c.uri()).isEqualTo(ActualValue.Connection.URI);
    }
    if (request.connection() instanceof DetailedConnection c) {
      assertThat(c.host()).isEqualTo(ActualValue.Connection.HOST);
      assertThat(c.port()).isEqualTo(ActualValue.Connection.PORT);
      assertThat(c.username()).isEqualTo(ActualValue.Connection.USERNAME);
      assertThat(c.password()).isEqualTo(ActualValue.Connection.PASSWORD);
    }
  }
}
