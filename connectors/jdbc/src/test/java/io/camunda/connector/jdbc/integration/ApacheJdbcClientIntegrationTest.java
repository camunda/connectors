/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.integration;

import java.sql.SQLException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

@ExtendWith(MockitoExtension.class)
public class ApacheJdbcClientIntegrationTest extends IntegrationBaseTest {
  public static final String PROVIDE_SQL_SERVERS_CONFIG =
      "io.camunda.connector.jdbc.integration.ApacheJdbcClientIntegrationTest#provideSqlServersConfig";

  static final MSSQLServerContainer msSqlServer = new MSSQLServerContainer().acceptLicense();
  static final MySQLContainer mySqlServer = new MySQLContainer<>();
  static final PostgreSQLContainer postgreServer = new PostgreSQLContainer();

  static Stream<IntegrationTestConfig> provideSqlServersConfig() {
    return IntegrationTestConfig.from(mySqlServer, msSqlServer, postgreServer).stream();
  }

  @BeforeAll
  public static void setUp() throws SQLException {
    msSqlServer.start();
    mySqlServer.start();
    postgreServer.start();

    for (IntegrationTestConfig config :
        IntegrationTestConfig.from(mySqlServer, msSqlServer, postgreServer)) {
      createEmployeeTable(config);
    }
  }

  @AfterAll
  public static void tearDown() {
    msSqlServer.stop();
    mySqlServer.stop();
    postgreServer.stop();
  }

  @BeforeEach
  public void insertData() throws SQLException {
    for (IntegrationTestConfig config :
        IntegrationTestConfig.from(mySqlServer, msSqlServer, postgreServer)) {
      insertDefaultEmployees(config);
    }
  }

  @AfterEach
  public void cleanUp() throws SQLException {
    for (IntegrationTestConfig config :
        IntegrationTestConfig.from(mySqlServer, msSqlServer, postgreServer)) {
      deleteAllEmployees(config);
    }
  }

  @Nested
  class HappyPathTests {

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldReturnResultList_whenSelectQuery(IntegrationTestConfig config) {
      selectDataAndAssertSuccess(config);
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldInsertData_whenInsertQuery(IntegrationTestConfig config) throws SQLException {
      insertDataAndAssertSuccess(config);
      assertNewEmployeeCreated(config);
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldUpdateData_whenUpdateQuery(IntegrationTestConfig config) throws SQLException {
      updateDataAndAssertSuccess(config);
      assertEmployeeUpdated(config);
    }
  }
}
