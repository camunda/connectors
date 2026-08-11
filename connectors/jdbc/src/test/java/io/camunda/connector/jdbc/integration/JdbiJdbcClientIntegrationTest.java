/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.validation.ConfigurationValidationResult.Status;
import io.camunda.connector.jdbc.model.request.JdbcRequest;
import io.camunda.connector.jdbc.model.request.JdbcRequestData;
import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import io.camunda.connector.jdbc.model.request.connection.JdbcConnectionConfiguration;
import io.camunda.connector.jdbc.model.request.connection.JdbcConnectionValidator;
import io.camunda.connector.jdbc.utils.ConnectionHelper;
import io.camunda.connector.test.utils.DockerImages;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.sql.Connection;
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
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

@ExtendWith(MockitoExtension.class)
@SlowTest
public class JdbiJdbcClientIntegrationTest extends IntegrationBaseTest {

  public static final String PROVIDE_SQL_SERVERS_CONFIG =
      "io.camunda.connector.jdbc.integration.JdbiJdbcClientIntegrationTest#provideSqlServersConfig";
  static final MSSQLServerContainer msSqlServer =
      new MSSQLServerContainer<>(DockerImages.get(MSSQL)).acceptLicense();
  static final MySQLContainer mySqlServer = new MySQLContainer<>(DockerImages.get(MYSQL));
  static final PostgreSQLContainer postgreServer =
      new PostgreSQLContainer<>(DockerImages.get(POSTGRES));
  static final MariaDBContainer mariaDbServer =
      new MariaDBContainer<>(DockerImageName.parse(DockerImages.get(MARIADB)));
  static final OracleContainer oracleServer = new OracleContainer(DockerImages.get(ORACLE));

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
    oracleServer.start();

    sqlServersConfig =
        IntegrationTestConfig.from(
            mySqlServer, msSqlServer, postgreServer, mariaDbServer, oracleServer);

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
    oracleServer.stop();
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
      if (config.database() == SupportedDatabase.ORACLE) {
        // Oracle does not support CREATE DATABASE
        return;
      }
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
              config.properties(),
              config.jsonType()),
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
      if (config.database() == SupportedDatabase.ORACLE) {
        // Oracle does not support CREATE DATABASE
        return;
      }
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
                          config.properties(),
                          config.jsonType())));
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
                          config.properties(),
                          config.jsonType())));
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

  @Nested
  class JsonTests {

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldParseJson_whenJsonColumnTypeSupported(IntegrationTestConfig config)
        throws SQLException {
      config
          .jsonType()
          .forEach(
              jsonType -> {
                try {
                  addJsonColumn(config, jsonType);
                  selectJsonDataAndAssertSuccess(config);
                  dropJsonColumn(config);
                } catch (SQLException | JsonProcessingException e) {
                  throw new RuntimeException(e);
                }
              });
    }
  }

  /**
   * Out-of-band validation of a stored connection credential, against every supported product. The
   * point of running this for real is the failure classification: telling "the database rejected
   * this login" from "the database is unreachable" relies on SQL states and vendor error codes that
   * differ per product, and only a real server reports the ones it actually reports.
   */
  private static JdbcConnectionConfiguration credential(
      IntegrationTestConfig config, String password) {
    return new JdbcConnectionConfiguration(
        config.database(),
        config.host(),
        config.port(),
        config.databaseName(),
        config.username(),
        password);
  }

  /**
   * A credential carries no connection properties (see {@code
   * JdbcConnectionConfiguration#toDetailedConnection}), so a product that needs one to be reachable
   * at all cannot be reached through one. Only SQL Server is affected here: the test server's
   * certificate is self-signed and its driver requires {@code encrypt=false} to accept it.
   */
  private static void assumeReachableWithoutConnectionProperties(IntegrationTestConfig config) {
    assumeTrue(
        config.properties() == null || config.properties().isEmpty(),
        "a stored credential cannot carry the connection properties this product needs");
  }

  /** A product whose driver and URL scheme cannot reach {@code config}'s server. */
  private static SupportedDatabase aDifferentDatabaseFrom(IntegrationTestConfig config) {
    return config.database() == SupportedDatabase.POSTGRESQL
        ? SupportedDatabase.ORACLE
        : SupportedDatabase.POSTGRESQL;
  }

  /**
   * The database a bound credential names must drive execution, not only validation. Otherwise the
   * credential supplies the host, port and login while the connector's own field still picks the
   * driver and URL scheme, and the two can disagree with nothing reporting it.
   */
  @Nested
  class ConnectionCredentialPrecedenceTests {

    private final JdbcRequestData data = new JdbcRequestData(false, "SELECT 1");

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldConnectUsingTheDatabaseTheCredentialNames(IntegrationTestConfig config)
        throws Exception {
      assumeReachableWithoutConnectionProperties(config);
      var request =
          new JdbcRequest(
              credential(config, config.password()), aDifferentDatabaseFrom(config), null, data);

      assertThat(request.database()).isEqualTo(config.database());
      try (Connection connection = ConnectionHelper.openConnection(request)) {
        assertThat(connection.isClosed()).isFalse();
      }
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldConnectWhenOnlyTheCredentialNamesADatabase(IntegrationTestConfig config)
        throws Exception {
      // The shape a modeler produces when they expect the credential to supply the whole
      // connection.
      assumeReachableWithoutConnectionProperties(config);
      var request = new JdbcRequest(credential(config, config.password()), null, null, data);

      try (Connection connection = ConnectionHelper.openConnection(request)) {
        assertThat(connection.isClosed()).isFalse();
      }
    }

    /**
     * Negative control: the same mismatched product really does fail when no credential overrides
     * it, so the two tests above are not passing for some unrelated reason.
     */
    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldFailWhenTheMismatchedDatabaseIsNotOverriddenByACredential(
        IntegrationTestConfig config) {
      assumeReachableWithoutConnectionProperties(config);
      var inline = credential(config, config.password()).toDetailedConnection();
      var request = new JdbcRequest(aDifferentDatabaseFrom(config), inline, data);

      assertThatThrownBy(() -> ConnectionHelper.openConnection(request))
          .isInstanceOf(ConnectorException.class);
    }
  }

  @Nested
  class ConnectionCredentialValidationTests {

    private final JdbcConnectionValidator validator = new JdbcConnectionValidator();

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldSucceed_whenTheCredentialCanLogIn(IntegrationTestConfig config) {
      assumeReachableWithoutConnectionProperties(config);

      var result = validator.validate(credential(config, config.password()));

      assertThat(result.status()).isEqualTo(Status.SUCCESS);
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldReportUnauthorized_whenThePasswordIsWrong(IntegrationTestConfig config) {
      assumeReachableWithoutConnectionProperties(config);

      var result = validator.validate(credential(config, config.password() + "wrong"));

      assertThat(result.status()).isEqualTo(Status.FAILURE);
      assertThat(result.code()).isEqualTo("UNAUTHORIZED");
      assertThat(result.message()).doesNotContain(config.password(), config.username());
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldReportError_whenTheDatabaseIsUnreachable(IntegrationTestConfig config) {
      // A closed port must not read as a rejected login: an operator told "unauthorized" would go
      // looking for the wrong problem.
      var unreachable =
          new JdbcConnectionConfiguration(
              config.database(),
              config.host(),
              "1",
              config.databaseName(),
              config.username(),
              config.password());

      var result = validator.validate(unreachable);

      assertThat(result.status()).isEqualTo(Status.FAILURE);
      assertThat(result.code()).isEqualTo("ERROR");
    }
  }
}
