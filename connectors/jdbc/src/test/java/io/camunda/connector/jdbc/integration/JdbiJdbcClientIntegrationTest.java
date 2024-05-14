/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.error.ConnectorException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
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
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@ExtendWith(MockitoExtension.class)
public class JdbiJdbcClientIntegrationTest extends IntegrationBaseTest {

  public static final String PROVIDE_SQL_SERVERS_CONFIG =
      "io.camunda.connector.jdbc.integration.JdbiJdbcClientIntegrationTest#provideSqlServersConfig";
  static final MSSQLServerContainer msSqlServer = new MSSQLServerContainer().acceptLicense();
  static final MySQLContainer mySqlServer = new MySQLContainer<>();
  static final PostgreSQLContainer postgreServer = new PostgreSQLContainer();
  static final MariaDBContainer mariaDbServer =
      new MariaDBContainer(DockerImageName.parse("mariadb:11.3.2"));

  static List<IntegrationTestConfig> sqlServersConfig;

  static Stream<IntegrationTestConfig> provideSqlServersConfig() {
    return sqlServersConfig.stream();
  }

  @BeforeAll
  public static void setUp() throws SQLException {
    String password = "password&the#restofit";
    mySqlServer.withPassword(password);
    mariaDbServer.withPassword(password);
    mySqlServer.withPassword(password);
    postgreServer.withPassword(password);

    msSqlServer.start();
    mySqlServer.start();
    postgreServer.start();
    mariaDbServer.start();
    sqlServersConfig =
        IntegrationTestConfig.from(mySqlServer, msSqlServer, postgreServer, mariaDbServer);

    for (IntegrationTestConfig config : sqlServersConfig) {
      createEmployeeTable(config);
    }
  }

  @AfterAll
  public static void tearDown() {
    msSqlServer.stop();
    mySqlServer.stop();
    postgreServer.stop();
    mariaDbServer.stop();
  }

  @BeforeEach
  public void insertData() throws SQLException {
    for (IntegrationTestConfig config : sqlServersConfig) {
      insertDefaultEmployees(config);
    }
  }

  @AfterEach
  public void cleanUp() throws SQLException {
    for (IntegrationTestConfig config : sqlServersConfig) {
      cleanUp(config);
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

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldDeleteData_whenDeleteQuery(IntegrationTestConfig config) throws SQLException {
      deleteDataAndAssertSuccess(config);
      assertEmployeeDeleted(config);
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldCreateTable_whenCreateTableQuery(IntegrationTestConfig config)
        throws SQLException {
      createTableAndAssertSuccess(config, "TestTable", "id INT PRIMARY KEY, name VARCHAR(255)");
      selectAll(config, "TestTable");
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldCreateDatabase_whenCreateTableQuery(IntegrationTestConfig config)
        throws SQLException {
      createDatabaseAndAssertSuccess(config, "mydb");
      createTableAndAssertSuccess(
          new IntegrationTestConfig(
              config.database(),
              config.url(),
              config.host(),
              config.port(),
              config.rootUser(),
              Optional.ofNullable(config.rootUser()).orElse(config.username()),
              config.password(),
              "mydb",
              config.properties()),
          "TestTable",
          "id INT PRIMARY KEY, name VARCHAR(255)");
      cleanUpDatabase(config, "mydb");
    }
  }

  @Nested
  class WrongModifyingParameterValueTests {

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldThrowConnectorException_whenIsModifyingIsFalseWhileSelectingInDb(
        IntegrationTestConfig config) {
      selectDataAndAssertNoResult(config);
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldThrowConnectorException_whenIsModifyingIsFalseWhileInsertingInDb(
        IntegrationTestConfig config) {
      insertDataAndAssertThrows(config);
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldThrowConnectorException_whenIsModifyingIsFalseWhileUpdatingInDb(
        IntegrationTestConfig config) {
      updateDataAndAssertThrows(config);
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldThrowConnectorException_whenIsModifyingIsFalseWhileDeletingInDb(
        IntegrationTestConfig config) {
      deleteDataAndAssertThrows(config);
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldThrowConnectorException_whenIsModifyingIsFalseWhileCreatingTableInDb(
        IntegrationTestConfig config) {
      createTableAndAssertThrows(config, "TestTable", "id INT PRIMARY KEY, name VARCHAR(255)");
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldThrowConnectorException_whenIsModifyingIsFalseWhileCreatingDatabaseInDb(
        IntegrationTestConfig config) throws SQLException {
      createDatabaseAndAssertThrows(config, "mydb");
      cleanUpDatabase(config, "mydb");
    }
  }

  @Nested
  class WrongAuthenticationTests {

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldThrowConnectorException_whenWrongUriConnection(IntegrationTestConfig config) {
      ConnectorException exception =
          assertThrows(
              ConnectorException.class,
              () ->
                  selectDataAndAssertSuccess(
                      new IntegrationTestConfig(
                          config.database(),
                          config.url(),
                          config.host(),
                          config.port(),
                          config.rootUser(),
                          config.username(),
                          config.password() + "wrong",
                          config.databaseName(),
                          config.properties())));
      assertThat(exception.getMessage()).contains("Cannot create the Database connection");
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldThrowConnectorException_whenWrongDetailedConnection(
        IntegrationTestConfig config) {
      ConnectorException exception =
          assertThrows(
              ConnectorException.class,
              () ->
                  selectDataAndAssertSuccess(
                      new IntegrationTestConfig(
                          config.database(),
                          config.url(),
                          config.host(),
                          config.port(),
                          config.rootUser(),
                          config.username(),
                          config.password() + "wrong",
                          config.databaseName(),
                          config.properties())));
      assertThat(exception.getMessage()).contains("Cannot create the Database connection");
    }
  }

  @Nested
  class ParametersTests {

    @Nested
    class DeleteTests {
      @ParameterizedTest
      @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
      public void shouldDeleteData_whenDeleteQueryWithNamedParameters(IntegrationTestConfig config)
          throws SQLException {
        deleteDataWithNamedParametersAndAssertSuccess(config);
        assertEmployeeDeleted(config);
      }

      @ParameterizedTest
      @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
      public void shouldDeleteData_whenDeleteQueryWithPositionalParameters(
          IntegrationTestConfig config) throws SQLException {
        deleteDataWithPositionalParametersAndAssertSuccess(config);
        assertEmployeeDeleted(config);
      }

      @ParameterizedTest
      @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
      public void shouldDeleteData_whenDeleteQueryWithBindingParameters(
          IntegrationTestConfig config) throws SQLException {
        deleteDataWithBindingParametersAndAssertSuccess(config);
        assertEmployeeDeleted(config);
      }
    }

    @Nested
    class UpdateTests {
      @ParameterizedTest
      @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
      public void shouldUpdateData_whenUpdateQueryWithNamedParameters(IntegrationTestConfig config)
          throws SQLException {
        updateDataWithNamedParametersAndAssertSuccess(config);
        assertEmployeeUpdated(config);
      }

      @ParameterizedTest
      @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
      public void shouldUpdateData_whenUpdateQueryWithPositionalParameters(
          IntegrationTestConfig config) throws SQLException {
        updateDataWithPositionalParametersAndAssertSuccess(config);
        assertEmployeeUpdated(config);
      }

      @ParameterizedTest
      @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
      public void shouldUpdateData_whenUpdateQueryWithBindingParameters(
          IntegrationTestConfig config) throws SQLException {
        updateDataWithBindingParametersAndAssertSuccess(config);
        assertEmployeeUpdated(config);
      }
    }

    @Nested
    class InsertTests {
      @ParameterizedTest
      @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
      public void shouldInsertData_whenInsertQueryWithNamedParameters(IntegrationTestConfig config)
          throws SQLException {
        insertDataWithNamedParametersAndAssertSuccess(config);
        assertNewEmployeeCreated(config);
      }

      @ParameterizedTest
      @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
      public void shouldInsertData_whenInsertQueryWithPositionalParameters(
          IntegrationTestConfig config) throws SQLException {
        insertDataWithPositionalParametersAndAssertSuccess(config);
        assertNewEmployeeCreated(config);
      }

      @ParameterizedTest
      @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
      public void shouldInsertData_whenInsertQueryWithBindingParameters(
          IntegrationTestConfig config) throws SQLException {
        insertDataWithBindingParametersAndAssertSuccess(config);
        assertNewEmployeeCreated(config);
      }
    }

    @Nested
    class SelectTests {
      @ParameterizedTest
      @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
      public void shouldReturnResultList_whenSelectQueryWithNamedParameters(
          IntegrationTestConfig config) {
        // "SELECT * FROM Employee WHERE name = :name", Map.of("name", "John Doe")
        selectDataWithNamedParametersAndAssertSuccess(config);
      }

      @ParameterizedTest
      @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
      public void shouldReturnResultList_whenSelectQueryWithPositionalParameters(
          IntegrationTestConfig config) {
        // "SELECT * FROM Employee WHERE name = ?", List.of("John Doe")
        selectDataWithPositionalParametersAndAssertSuccess(config);
      }

      @ParameterizedTest
      @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
      public void
          shouldThrowUnableToCreateStatementException_whenSelectQueryWhereInWithNamedParameters(
              IntegrationTestConfig config) {
        // "SELECT * FROM Employee WHERE name IN (:nameList)", Map.of("nameList", List.of("John
        // Doe", "Jane Doe"))
        // NOT ALLOWED
        selectDataWithNamedParametersWhereInAndAssertThrows(config);
      }

      @ParameterizedTest
      @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
      public void
          shouldThrowUnableToCreateStatementException_whenSelectQueryWhereInWithUselessParameters(
              IntegrationTestConfig config) {
        // "SELECT * FROM Employee WHERE name IN (\"John Doe\", \"Jane Doe\")",
        /// Map.of("uselessVar", List.of("John Doe", "Jane Doe"))
        // NOT ALLOWED
        selectDataWithUselessNamedParametersWhereInAndAssertThrows(config);
      }

      @ParameterizedTest
      @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
      public void
          shouldThrowUnableToCreateStatementException_whenSelectQueryWhereInWithMissingParameters(
              IntegrationTestConfig config) {
        // "SELECT * FROM Employee WHERE name = :name", Map.of())
        // NOT ALLOWED
        selectDataWithMissingNamedParametersWhereInAndAssertThrows(config);
      }

      @ParameterizedTest
      @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
      public void shouldReturnResultList_whenSelectQueryWhereInWithPositionalParameters(
          IntegrationTestConfig config) {
        // "SELECT * FROM Employee WHERE name IN (?, ?)", List.of("John Doe", "Jane Doe")
        selectDataWithPositionalParametersWhereInAndAssertSuccess(config);
      }

      @ParameterizedTest
      @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
      public void shouldReturnResultList_whenSelectQueryWhereInWithBindingParameters(
          IntegrationTestConfig config) {
        // "SELECT * FROM Employee WHERE name IN (?, ?)", List.of("John Doe", "Jane Doe")
        selectDataWithBindingParametersWhereInAndAssertSuccess(config);
      }
    }
  }
}
