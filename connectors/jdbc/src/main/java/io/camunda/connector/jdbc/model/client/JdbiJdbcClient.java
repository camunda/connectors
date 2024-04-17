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
              handle ->
                  bindVariables(handle.createUpdate(data.query()), data.variables()).execute());
      response = JdbcResponse.of(result);
    } else {
      // SELECT query
      LOG.debug("Executing query: {}", data.query());
      List<Map<String, Object>> result =
          jdbi.withHandle(
              handle ->
                  bindVariables(handle.createQuery(data.query()), data.variables())
                      .mapToMap()
                      .list());
      response = JdbcResponse.of(result);
    }
    LOG.debug("JdbcResponse: {}", response);
    return response;
  }

  private <T extends SqlStatement<T>> T bindVariables(T stmt, Object variables) {
    if (variables == null) {
      return stmt;
    }
    switch (variables) {
      case Map<?, ?> map -> map.forEach(
          (key, value) -> {
            if (Objects.requireNonNull(value) instanceof List<?> l) {
              bindVariables(stmt, l);
            } else {
              stmt.bind(key.toString(), value);
            }
          });
      case List<?> list -> {
        for (int i = 0; i < list.size(); i++) {
          stmt.bind(i, list.get(i));
        }
      }
      default -> throw new IllegalStateException(
          "Unexpected type: "
              + variables.getClass().getName()
              + ". Only Map and List are supported.");
    }
    return stmt;
  }
}
