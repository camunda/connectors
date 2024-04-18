/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.client;

import static io.camunda.connector.jdbc.utils.ConnectionHelper.openConnection;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.jdbc.model.request.JdbcRequest;
import io.camunda.connector.jdbc.model.request.JdbcRequestData;
import io.camunda.connector.jdbc.model.response.JdbcResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.SqlStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record JdbiJdbcClient() implements JdbcClient {
  private static final Logger LOG = LoggerFactory.getLogger(JdbiJdbcClient.class);

  @Override
  public JdbcResponse executeRequest(JdbcRequest request) throws ConnectorException {
    JdbcRequestData data = request.data();
    try (Connection connection = openConnection(request)) {
      return internalExecuteRequest(data, connection);
    } catch (SQLException | IllegalAccessException e) {
      throw new ConnectorException(
          "Error while executing the query [" + data.query() + "]: " + e.getMessage());
    }
  }

  JdbcResponse internalExecuteRequest(JdbcRequestData data, Connection connection)
      throws SQLException, IllegalAccessException {
    JdbcResponse response;
    Jdbi jdbi = Jdbi.create(connection);
    if (data.isModifyingQuery()) {
      LOG.debug("Executing modifying query: {}", data.query());
      Integer result =
          jdbi.withHandle(
              handle -> bindVariables(handle.createUpdate(data.query()), data).execute());
      response = JdbcResponse.of(result);
    } else {
      // SELECT query
      LOG.debug("Executing query: {}", data.query());
      List<Map<String, Object>> result =
          jdbi.withHandle(
              handle -> bindVariables(handle.createQuery(data.query()), data).mapToMap().list());
      response = JdbcResponse.of(result);
    }
    LOG.debug("JdbcResponse: {}", response);
    return response;
  }

  /**
   * Bind the variables to the statement. The variables can be a {@link Map} or a {@link List}. If
   * the query contains a binding variable, the value will be bound to it.
   *
   * @see <a href="https://jdbi.org/releases/3.45.1/#_binding_arguments">Binding arguments</a>
   * @see <a href="https://jdbi.org/releases/3.45.1/#_positional_arguments">Positional arguments</a>
   * @see <a href="https://jdbi.org/releases/3.45.1/#_named_arguments">Named arguments</a>
   */
  private <T extends SqlStatement<T>> T bindVariables(T stmt, JdbcRequestData data) {
    var variables = data.variables();
    var query = data.query();
    if (variables == null) {
      return stmt;
    }
    switch (variables) {
        // Named parameters (:name, :id)
      case Map<?, ?> map ->
          map.forEach(
              (key, value) -> {
                if (hasBindingVariable(query, key.toString())
                    && Objects.requireNonNull(value) instanceof List<?> l) {
                  // Bind a list of values to a single named parameter (<myList>)
                  stmt.bindList(key.toString(), l);
                } else {
                  stmt.bind(key.toString(), value);
                }
              });
        // Positional parameters (?,?)
      case List<?> list -> {
        for (int i = 0; i < list.size(); i++) {
          stmt.bind(i, list.get(i));
        }
      }
      default ->
          throw new IllegalStateException(
              "Unexpected type: "
                  + variables.getClass().getName()
                  + ". Only Map and List are supported.");
    }
    return stmt;
  }

  /**
   * Check if the query contains a binding variable. See <a
   * href="https://jdbi.org/releases/3.45.1/#_binding_arguments">this</a> for more information.
   *
   * @param query the query to check
   * @param variable the variable to check
   * @return true if the query contains the variable, false otherwise
   */
  private boolean hasBindingVariable(String query, String variable) {
    return query.contains("<" + variable + ">");
  }
}
