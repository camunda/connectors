/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.request;

import static io.camunda.connector.jdbc.OutboundBaseTest.getContextBuilderWithSecrets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.jdbc.BaseTest;
import io.camunda.connector.jdbc.model.request.connection.DetailedConnection;
import io.camunda.connector.jdbc.model.request.connection.UriConnection;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import org.junit.jupiter.api.Test;
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

  /**
   * Exercises the full runtime path (JSON -> Jackson binding -> {@code @Valid} cascade), which
   * {@code ConnectionHelperTest} does not cover since it constructs {@code JdbcRequest} directly.
   * Only the bound connection credential is present; no inline connection fields.
   */
  @Test
  void bindVariablesSucceedsWithOnlyConfigurationProvided() {
    String variables =
        """
        {
          "database": "POSTGRESQL",
          "configuration": {
            "host": "cred-host",
            "port": "5432",
            "databaseName": "cred-db",
            "username": "cred-user",
            "password": "cred-pass"
          },
          "data": { "returnResults": false, "query": "SELECT 1" }
        }
        """;
    var context = OutboundConnectorContextBuilder.create().variables(variables).build();

    var request = context.bindVariables(JdbcRequest.class);

    assertThat(request.connection()).isNull();
    assertThat(request.configuration()).isNotNull();
    assertThat(request.configuration().host()).isEqualTo("cred-host");
  }

  /** Only inline connection fields are present; no bound configuration. */
  @Test
  void bindVariablesSucceedsWithOnlyConnectionProvided() {
    String variables =
        """
        {
          "database": "MYSQL",
          "connection": {
            "authType": "detailed",
            "host": "localhost",
            "port": "5868",
            "username": "myLogin",
            "password": "mySecretPassword"
          },
          "data": { "returnResults": false, "query": "SELECT * FROM users" }
        }
        """;
    var context = OutboundConnectorContextBuilder.create().variables(variables).build();

    var request = context.bindVariables(JdbcRequest.class);

    assertThat(request.connection()).isNotNull();
    assertThat(request.configuration()).isNull();
  }

  /**
   * Neither a connection nor a configuration is present: {@code isConnectionSourceProvided()} must
   * fail the {@code @Valid} cascade during binding, not just when {@code
   * ConnectionHelper#resolveConnection} is called later.
   */
  @Test
  void bindVariablesFailsWhenNeitherConnectionNorConfigurationProvided() {
    String variables =
        """
        {
          "database": "MYSQL",
          "data": { "returnResults": false, "query": "SELECT * FROM users" }
        }
        """;
    var context = OutboundConnectorContextBuilder.create().variables(variables).build();

    assertThatThrownBy(() -> context.bindVariables(JdbcRequest.class))
        .hasMessageContaining("connection credential");
  }

  /**
   * Reproduces the actual Modeler-generated shape for a credential-only diagram: {@code
   * connection.authType} is an unconditional zeebe:input with a static default ({@code "uri"}), so
   * it is always present even though the user never filled in the (conditionally hidden) {@code
   * connection.uri}. This must not fail validation now that {@code configuration} is bound and
   * takes precedence.
   */
  @Test
  void bindVariablesSucceedsWithCredentialBoundDespiteUnconditionalInlineDiscriminator() {
    String variables =
        """
        {
          "database": "POSTGRESQL",
          "connection": { "authType": "uri" },
          "configuration": {
            "host": "cred-host",
            "port": "5432",
            "databaseName": "cred-db",
            "username": "cred-user",
            "password": "cred-pass"
          },
          "data": { "returnResults": false, "query": "SELECT 1" }
        }
        """;
    var context = OutboundConnectorContextBuilder.create().variables(variables).build();

    var request = context.bindVariables(JdbcRequest.class);

    assertThat(request.configuration()).isNotNull();
    assertThat(request.configuration().host()).isEqualTo("cred-host");
  }

  /** Without a bound credential, the same incomplete inline connection must still fail. */
  @Test
  void bindVariablesFailsWithIncompleteInlineConnectionWhenNoCredentialBound() {
    String variables =
        """
        {
          "database": "POSTGRESQL",
          "connection": { "authType": "uri" },
          "data": { "returnResults": false, "query": "SELECT 1" }
        }
        """;
    var context = OutboundConnectorContextBuilder.create().variables(variables).build();

    assertThatThrownBy(() -> context.bindVariables(JdbcRequest.class)).hasMessageContaining("uri");
  }
}
