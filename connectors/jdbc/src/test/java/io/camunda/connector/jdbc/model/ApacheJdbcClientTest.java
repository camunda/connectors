/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.connector.jdbc.model.client.JdbcClient;
import io.camunda.connector.jdbc.model.request.JdbcRequest;
import io.camunda.connector.jdbc.model.request.JdbcRequestData;
import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import io.camunda.connector.jdbc.model.request.connection.UriConnection;
import io.camunda.connector.jdbc.model.response.JdbcResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
public class ApacheJdbcClientTest {
  private JdbcClient.ApacheJdbcClient apacheJdbcClient;

  @Mock private Connection connection;
  @Mock private QueryRunner queryRunner;

  private void mockQueryRunner() throws SQLException {
    when(queryRunner.execute(any(Connection.class), any(String.class), any(Object[].class)))
        .thenReturn(1);
    when(queryRunner.query(any(Connection.class), any(String.class), any(MapListHandler.class)))
        .thenReturn(Arrays.asList(Map.of()));
  }

  @Test
  public void shouldReturnListAndNullModifiedRows_whenNotModifyingQuery() throws SQLException {
    mockQueryRunner();
    apacheJdbcClient = new JdbcClient.ApacheJdbcClient(queryRunner);
    JdbcRequest jdbcRequest =
        new JdbcRequest(
            SupportedDatabase.MSSQL,
            new UriConnection("", null),
            new JdbcRequestData(false, "SELECT * FROM table", null));
    JdbcResponse result = apacheJdbcClient.internalExecuteRequest(jdbcRequest.data(), connection);

    assertNull(result.modifiedRows());
    assertNotNull(result.resultSet());
  }

  @Test
  public void shouldReturnModifiedRowsAndNullList_whenModifyingQuery() throws SQLException {
    mockQueryRunner();
    apacheJdbcClient = new JdbcClient.ApacheJdbcClient(queryRunner);
    JdbcRequest jdbcRequest =
        new JdbcRequest(
            SupportedDatabase.MSSQL,
            new UriConnection("", null),
            new JdbcRequestData(true, "UPDATE table SET column = value WHERE column = value"));
    JdbcResponse result = apacheJdbcClient.internalExecuteRequest(jdbcRequest.data(), connection);

    assertNotNull(result.modifiedRows());
    assertNull(result.resultSet());
  }
}
