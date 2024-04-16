/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import org.jdbi.v3.core.Jdbi;
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
      jdbi.withHandle(handle -> handle.createUpdate(data.query()).execute());
      response = JdbcResponse.of(connection.createStatement().execute(data.query()));
    } else {
      LOG.debug("Executing query: {}", data.query());
      if (data.variables() != null && !(data.variables() instanceof Map)) {
        throw new IllegalAccessException("Variables must be a map when performing a SELECT query");
      }
      List<Map<String, Object>> result =
          jdbi.withHandle(
              handle ->
                  handle
                      .createQuery(data.query())
                      .bindMap((Map<String, Object>) data.variables())
                      .mapToMap()
                      .list());
      response = JdbcResponse.of(result);
    }
    LOG.debug("JdbcResponse: {}", response);
    return response;
  }
}
