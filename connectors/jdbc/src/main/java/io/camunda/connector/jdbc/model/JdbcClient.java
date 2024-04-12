/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model;

import static io.camunda.connector.jdbc.utils.ConnectionHelper.openConnection;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.jdbc.model.request.JdbcRequest;
import io.camunda.connector.jdbc.model.request.JdbcRequestData;
import io.camunda.connector.jdbc.model.response.JdbcResponse;
import java.sql.Connection;
import java.sql.SQLException;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public sealed interface JdbcClient permits JdbcClient.ApacheJdbcClient {
  JdbcResponse executeRequest(JdbcRequest request) throws ConnectorException;

  record ApacheJdbcClient(QueryRunner runner) implements JdbcClient {
    private static final Logger LOG = LoggerFactory.getLogger(ApacheJdbcClient.class);

    public ApacheJdbcClient() {
      this(new QueryRunner());
    }

    @Override
    public JdbcResponse executeRequest(JdbcRequest request) throws ConnectorException {
      JdbcRequestData data = request.data();
      try (Connection connection = openConnection(request)) {
        return internalExecuteRequest(data, connection);
      } catch (SQLException e) {
        throw new ConnectorException("Error while executing the query " + data.query(), e);
      }
    }

    JdbcResponse internalExecuteRequest(JdbcRequestData data, Connection connection)
        throws SQLException {
      JdbcResponse response;
      if (data.isModifyingQuery()) {
        LOG.debug("Executing modifying query: {}", data.query());
        response = JdbcResponse.of(runner.execute(connection, data.query(), data.variables()));
      } else {
        LOG.debug("Executing query: {}", data.query());
        MapListHandler beanListHandler = new MapListHandler();
        response = JdbcResponse.of(runner.query(connection, data.query(), beanListHandler));
      }
      LOG.debug("JdbcResponse: {}", response);
      return response;
    }
  }
}
