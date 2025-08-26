/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.integration;

import static io.camunda.connector.jdbc.integration.IntegrationBaseTest.Employee.INSERT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.jdbc.model.client.JdbcClient;
import io.camunda.connector.jdbc.model.client.JdbiJdbcClient;
import io.camunda.connector.jdbc.model.client.JdbiJsonHelper;
import io.camunda.connector.jdbc.model.request.JdbcRequest;
import io.camunda.connector.jdbc.model.request.JdbcRequestData;
import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import io.camunda.connector.jdbc.model.request.connection.DetailedConnection;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.result.NoResultsException;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.UnableToCreateStatementException;
import org.jdbi.v3.jackson2.Jackson2Plugin;

public abstract class IntegrationBaseTest {

  static final String DEFAULT_ADDRESS_JSON =
      "{\"street\":\"123 Main St\",\"city\":\"New York\",\"zip\":\"10001\"}";
  static final Employee NEW_EMPLOYEE = new Employee(7, "Eve", 55, "HR");

  static final List<Employee> DEFAULT_EMPLOYEES =
      List.of(
          new Employee(1, "John Doe", 30, "IT"),
          new Employee(2, "Jane Doe", 25, "HR"),
          new Employee(3, "Alice", 35, "Finance"),
          new Employee(4, "Bob", 40, "IT"),
          new Employee(5, "Charlie", 45, "HR"),
          new Employee(6, "David", 50, "Finance"));

  private final JdbcClient jdbiJdbcClient = new JdbiJdbcClient();

  static void createEmployeeTable(IntegrationTestConfig config) throws SQLException {
    try (Connection conn =
            DriverManager.getConnection(config.url(), config.username(), config.password());
        Statement stmt = conn.createStatement()) {
      if (config.database() == SupportedDatabase.ORACLE) {
        stmt.executeUpdate(Employee.CREATE_TABLE_ORACLE);
        return;
      }
      if (config.databaseName() != null) {
        stmt.executeUpdate("USE " + config.databaseName());
      }
      stmt.executeUpdate(Employee.CREATE_TABLE);
    }
  }

  void addJsonColumn(IntegrationTestConfig config, String jsonDatabaseType) throws SQLException {
    try (Connection conn =
            DriverManager.getConnection(config.url(), config.username(), config.password());
        Statement stmt = conn.createStatement()) {

      if (config.databaseName() != null && config.database() != SupportedDatabase.ORACLE) {
        stmt.executeUpdate("USE " + config.databaseName());
      }

      String addColumnSQL = "ALTER TABLE Employee ADD json " + jsonDatabaseType;
      if (config.database() == SupportedDatabase.ORACLE
          && Objects.equals(jsonDatabaseType, "VARCHAR2")) {
        addColumnSQL = addColumnSQL + "(4000)";
      }
      stmt.executeUpdate(addColumnSQL);
      String dummyJson;
      switch (config.database()) {
        case MYSQL, MARIADB, ORACLE -> dummyJson = "'" + DEFAULT_ADDRESS_JSON + "'";
        case POSTGRESQL -> dummyJson = "'" + DEFAULT_ADDRESS_JSON + "'::json";
        case MSSQL -> dummyJson = "'" + DEFAULT_ADDRESS_JSON + "'";
        default ->
            throw new UnsupportedOperationException("Unsupported database: " + config.database());
      }
      String updateSQL = "UPDATE Employee SET json = " + dummyJson;
      stmt.executeUpdate(updateSQL);
    }
  }

  void dropJsonColumn(IntegrationTestConfig config) throws SQLException {
    try (Connection conn =
            DriverManager.getConnection(config.url(), config.username(), config.password());
        Statement stmt = conn.createStatement()) {

      if (config.database() == SupportedDatabase.ORACLE) {
        stmt.executeUpdate("ALTER TABLE Employee DROP COLUMN \"JSON\"");
        return;
      }
      if (config.databaseName() != null) {
        stmt.executeUpdate("USE " + config.databaseName());
      }
      stmt.executeUpdate("ALTER TABLE Employee DROP COLUMN json");
    }
  }

  List<Map<String, Object>> selectAll(IntegrationTestConfig config, String tableName)
      throws SQLException {
    try (Connection conn =
        DriverManager.getConnection(config.url(), config.username(), config.password())) {
      // using jdbi
      var jdbi = Jdbi.create(conn);
      jdbi.installPlugin(new Jackson2Plugin());
      try (var handle = jdbi.open()) {
        if (config.databaseName() != null && config.database() != SupportedDatabase.ORACLE) {
          handle.execute("USE " + config.databaseName());
        }
        Query q = handle.createQuery("SELECT * FROM " + tableName + " ORDER BY id ASC");
        return JdbiJsonHelper.mapToParsedMap(conn.getMetaData().getDatabaseProductName(), q).list();
      }
    }
  }

  void insertDefaultEmployees(IntegrationTestConfig config) throws SQLException {
    try (Connection conn =
            DriverManager.getConnection(config.url(), config.username(), config.password());
        Statement stmt = conn.createStatement()) {
      String sql =
          INSERT.formatted(
              DEFAULT_EMPLOYEES.stream()
                  .map(Employee::toInsertQueryFormat)
                  .collect(Collectors.joining(",")));
      if (config.databaseName() != null && config.database() != SupportedDatabase.ORACLE) {
        stmt.executeUpdate("USE " + config.databaseName());
      }
      stmt.executeUpdate(sql);
    }
  }

  void cleanUp(IntegrationTestConfig config) throws SQLException {
    try (Connection conn =
            DriverManager.getConnection(config.url(), config.username(), config.password());
        Statement stmt = conn.createStatement()) {
      if (config.databaseName() != null && config.database() != SupportedDatabase.ORACLE) {
        stmt.executeUpdate("USE " + config.databaseName());
      }
      stmt.executeUpdate("DELETE FROM Employee");
      stmt.executeUpdate("DROP TABLE IF EXISTS TestTable");
    }
  }

  void selectDataAndAssertSuccess(IntegrationTestConfig config) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(true, "SELECT * FROM Employee ORDER BY Id ASC"));
    var response = jdbiJdbcClient.executeRequest(request);
    assertNull(response.modifiedRows());
    assertNotNull(response.resultSet());
    assertEquals(6, response.resultSet().size());
    // assert each row
    for (int i = 0; i < DEFAULT_EMPLOYEES.size(); i++) {
      var row = normalizeKeys(response.resultSet().get(i));
      var employee = DEFAULT_EMPLOYEES.get(i);
      employee
          .toMap()
          .forEach(
              (key, expected) -> {
                Object actual = row.get(key);
                assertThat(String.valueOf(actual))
                    .as("Key: %s", key)
                    .isEqualTo(String.valueOf(expected));
              });
    }
  }

  void selectDataAndAssertNoResult(IntegrationTestConfig config) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(false, "SELECT * FROM Employee ORDER BY Id ASC"));
    var response = jdbiJdbcClient.executeRequest(request);
    // calling QueryRunner.execute() with a SELECT works, it's just that there's no object returned
    assertEquals(-1, response.modifiedRows());
    assertNull(response.resultSet());
  }

  void selectDataWithNamedParametersAndAssertSuccess(IntegrationTestConfig config) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(
                true, "SELECT * FROM Employee WHERE name = :name", Map.of("name", "John Doe")));
    var response = jdbiJdbcClient.executeRequest(request);
    assertNull(response.modifiedRows());
    assertNotNull(response.resultSet());
    assertEquals(1, response.resultSet().size());
    Map<String, Object> expected =
        DEFAULT_EMPLOYEES.stream()
            .filter(e -> e.name().equals("John Doe"))
            .map(Employee::toMap)
            .map(IntegrationBaseTest::normalizeKeysAndValues) // normalize expected map
            .findFirst()
            .orElseThrow();
    Map<String, Object> actual =
        IntegrationBaseTest.normalizeKeysAndValues(response.resultSet().get(0));
    assertEquals(expected, actual);
  }

  void selectDataWithNamedParametersWhereInAndAssertThrows(IntegrationTestConfig config) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(
                true,
                "SELECT * FROM Employee WHERE name IN (:nameList)",
                Map.of("nameList", List.of("John Doe", "Jane Doe"))));
    assertThrows(
        UnableToCreateStatementException.class, () -> jdbiJdbcClient.executeRequest(request));
  }

  void selectDataWithUselessNamedParametersWhereInAndAssertThrows(IntegrationTestConfig config) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(
                true,
                "SELECT * FROM Employee WHERE name IN (\"John Doe\", \"Jane Doe\")",
                Map.of("uselessVar", List.of("John Doe", "Jane Doe"))));
    assertThrows(
        UnableToCreateStatementException.class, () -> jdbiJdbcClient.executeRequest(request));
  }

  void selectDataWithMissingNamedParametersWhereInAndAssertThrows(IntegrationTestConfig config) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(true, "SELECT * FROM Employee WHERE name = :name", Map.of()));
    assertThrows(
        UnableToCreateStatementException.class, () -> jdbiJdbcClient.executeRequest(request));
  }

  void selectDataWithPositionalParametersAndAssertSuccess(IntegrationTestConfig config) {
    var prefix =
        config.database() == SupportedDatabase.ORACLE
            ? ""
            : Optional.ofNullable(config.databaseName()).map(s -> s + ".").orElse("");

    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(
                true, "SELECT * FROM " + prefix + "Employee WHERE name = ?", List.of("John Doe")));
    var response = jdbiJdbcClient.executeRequest(request);
    assertNull(response.modifiedRows());
    assertNotNull(response.resultSet());
    assertEquals(1, response.resultSet().size());
    Map<String, Object> expected =
        DEFAULT_EMPLOYEES.stream()
            .filter(e -> e.name().equals("John Doe"))
            .map(Employee::toMap)
            .map(IntegrationBaseTest::normalizeKeysAndValues)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected employee not found"));
    Map<String, Object> actual = normalizeKeysAndValues(response.resultSet().get(0));
    assertEquals(expected, actual);
  }

  void selectDataWithPositionalParametersWhereInAndAssertSuccess(IntegrationTestConfig config) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(
                true,
                "SELECT * FROM Employee WHERE name IN (?, ?)",
                List.of("John Doe", "Jane Doe")));
    var response = jdbiJdbcClient.executeRequest(request);
    assertNull(response.modifiedRows());
    assertNotNull(response.resultSet());
    assertEquals(2, response.resultSet().size());
    List<Map<String, Object>> expected =
        DEFAULT_EMPLOYEES.stream()
            .filter(e -> e.name().equals("John Doe") || e.name().equals("Jane Doe"))
            .map(Employee::toMap)
            .map(IntegrationBaseTest::normalizeKeysAndValues)
            .toList();
    List<Map<String, Object>> actual =
        response.resultSet().stream().map(IntegrationBaseTest::normalizeKeysAndValues).toList();
    assertTrue(actual.containsAll(expected));
  }

  void selectDataWithBindingParametersWhereInAndAssertSuccess(IntegrationTestConfig config) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(
                true,
                "SELECT * FROM Employee WHERE name IN (<params>)",
                Map.of("params", List.of("John Doe", "Jane Doe"))));
    var response = jdbiJdbcClient.executeRequest(request);
    assertNull(response.modifiedRows());
    assertNotNull(response.resultSet());
    assertEquals(2, response.resultSet().size());
    List<Map<String, Object>> expected =
        DEFAULT_EMPLOYEES.stream()
            .filter(e -> e.name().equals("John Doe") || e.name().equals("Jane Doe"))
            .map(Employee::toMap)
            .map(IntegrationBaseTest::normalizeKeysAndValues) // replace with your test class name
            .toList();
    List<Map<String, Object>> actual =
        response.resultSet().stream().map(IntegrationBaseTest::normalizeKeysAndValues).toList();
    assertTrue(actual.containsAll(expected));
  }

  void selectJsonDataAndAssertSuccess(IntegrationTestConfig config)
      throws JsonProcessingException, JsonMappingException {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(true, "SELECT * FROM Employee ORDER BY Id ASC"));
    var response = jdbiJdbcClient.executeRequest(request);

    var row = response.resultSet().get(0);
    ObjectMapper objectMapper = new ObjectMapper();
    var expected = objectMapper.readTree(DEFAULT_ADDRESS_JSON);
    Object jsonValue =
        row.keySet().stream()
            .filter(k -> k.equalsIgnoreCase("json"))
            .findFirst()
            .map(row::get)
            .orElse(null);
    assertEquals(expected.get("street").asText(), ((ObjectNode) jsonValue).get("street").asText());
  }

  void updateDataAndAssertSuccess(IntegrationTestConfig config) {
    String name = DEFAULT_EMPLOYEES.get(0).name() + " UPDATED";
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(false, "UPDATE Employee SET name = '" + name + "' WHERE id = 1"));
    var response = jdbiJdbcClient.executeRequest(request);
    assertEquals(1, response.modifiedRows());
    assertNull(response.resultSet());
  }

  void updateDataAndAssertThrows(IntegrationTestConfig config) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(
                true, "UPDATE Employee SET name = 'John Doe UPDATED' WHERE id = 1"));
    assertThrows(NoResultsException.class, () -> jdbiJdbcClient.executeRequest(request));
  }

  void updateDataWithNamedParametersAndAssertSuccess(IntegrationTestConfig config) {
    String name = DEFAULT_EMPLOYEES.get(0).name() + " UPDATED";
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(
                false,
                "UPDATE Employee SET name = :name WHERE id = :id",
                Map.of("name", name, "id", 1)));
    var response = jdbiJdbcClient.executeRequest(request);
    assertEquals(1, response.modifiedRows());
    assertNull(response.resultSet());
  }

  void updateDataWithPositionalParametersAndAssertSuccess(IntegrationTestConfig config) {
    String name = DEFAULT_EMPLOYEES.get(0).name() + " UPDATED";
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(
                false, "UPDATE Employee SET name = ? WHERE id = ?", List.of(name, 1)));
    var response = jdbiJdbcClient.executeRequest(request);
    assertEquals(1, response.modifiedRows());
    assertNull(response.resultSet());
  }

  void updateDataWithBindingParametersAndAssertSuccess(IntegrationTestConfig config) {
    String name = DEFAULT_EMPLOYEES.get(0).name() + " UPDATED";
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(
                false,
                "UPDATE Employee SET name = :name WHERE id in (<ids>)",
                Map.of("name", name, "ids", List.of(1))));
    var response = jdbiJdbcClient.executeRequest(request);
    assertEquals(1, response.modifiedRows());
    assertNull(response.resultSet());
  }

  void assertEmployeeUpdated(IntegrationTestConfig config) throws SQLException {
    List<Map<String, Object>> result = selectAll(config, "Employee");
    assertEquals(DEFAULT_EMPLOYEES.size(), result.size());
    String nameColumn =
        result.get(0).keySet().stream()
            .filter(k -> k.equalsIgnoreCase("name"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Column 'name' not found"));
    assertEquals(DEFAULT_EMPLOYEES.get(0).name() + " UPDATED", result.get(0).get(nameColumn));
  }

  void deleteDataAndAssertSuccess(IntegrationTestConfig config) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(false, "DELETE FROM Employee WHERE id = 1"));
    var response = jdbiJdbcClient.executeRequest(request);
    assertEquals(1, response.modifiedRows());
    assertNull(response.resultSet());
  }

  void deleteDataAndAssertThrows(IntegrationTestConfig config) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(true, "DELETE FROM Employee WHERE id = 1"));
    assertThrows(NoResultsException.class, () -> jdbiJdbcClient.executeRequest(request));
  }

  void deleteDataWithNamedParametersAndAssertSuccess(IntegrationTestConfig config) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(false, "DELETE FROM Employee WHERE id = :id", Map.of("id", 1)));
    var response = jdbiJdbcClient.executeRequest(request);
    assertEquals(1, response.modifiedRows());
    assertNull(response.resultSet());
  }

  void deleteDataWithPositionalParametersAndAssertSuccess(IntegrationTestConfig config) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(false, "DELETE FROM Employee WHERE id = ?", List.of(1)));
    var response = jdbiJdbcClient.executeRequest(request);
    assertEquals(1, response.modifiedRows());
    assertNull(response.resultSet());
  }

  void deleteDataWithBindingParametersAndAssertSuccess(IntegrationTestConfig config) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(
                false, "DELETE FROM Employee WHERE id in (<ids>)", Map.of("ids", List.of(1))));
    var response = jdbiJdbcClient.executeRequest(request);
    assertEquals(1, response.modifiedRows());
    assertNull(response.resultSet());
  }

  void assertEmployeeDeleted(IntegrationTestConfig config) throws SQLException {
    List<Map<String, Object>> result = selectAll(config, "Employee");
    assertEquals(DEFAULT_EMPLOYEES.size() - 1, result.size());
    assertThat(result).doesNotContain(DEFAULT_EMPLOYEES.get(0).toMap());
  }

  void insertDataAndAssertSuccess(IntegrationTestConfig config) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(
                false, Employee.INSERT.formatted(NEW_EMPLOYEE.toInsertQueryFormat())));
    var response = jdbiJdbcClient.executeRequest(request);
    assertEquals(1, response.modifiedRows());
    assertNull(response.resultSet());
  }

  void insertDataAndAssertThrows(IntegrationTestConfig config) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(
                true, Employee.INSERT.formatted(NEW_EMPLOYEE.toInsertQueryFormat())));
    assertThrows(NoResultsException.class, () -> jdbiJdbcClient.executeRequest(request));
  }

  void insertDataWithNamedParametersAndAssertSuccess(IntegrationTestConfig config) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(
                false,
                "INSERT INTO Employee (id, name, age, department) VALUES (:id, :name, :age, :department)",
                NEW_EMPLOYEE.toUnparsedMap()));
    var response = jdbiJdbcClient.executeRequest(request);
    assertEquals(1, response.modifiedRows());
    assertNull(response.resultSet());
  }

  void insertDataWithPositionalParametersAndAssertSuccess(IntegrationTestConfig config) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(
                false,
                "INSERT INTO Employee (id, name, age, department) VALUES (?, ?, ?, ?)",
                List.of(
                    NEW_EMPLOYEE.id(),
                    NEW_EMPLOYEE.name(),
                    NEW_EMPLOYEE.age(),
                    NEW_EMPLOYEE.department())));
    var response = jdbiJdbcClient.executeRequest(request);
    assertEquals(1, response.modifiedRows());
    assertNull(response.resultSet());
  }

  void insertDataWithBindingParametersAndAssertSuccess(IntegrationTestConfig config) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(
                false,
                "INSERT INTO Employee (id, name, age, department) VALUES (<params>)",
                Map.of(
                    "params",
                    List.of(
                        NEW_EMPLOYEE.id(),
                        NEW_EMPLOYEE.name(),
                        NEW_EMPLOYEE.age(),
                        NEW_EMPLOYEE.department()))));
    var response = jdbiJdbcClient.executeRequest(request);
    assertEquals(1, response.modifiedRows());
    assertNull(response.resultSet());
  }

  void assertNewEmployeeCreated(IntegrationTestConfig config) throws SQLException {
    List<Map<String, Object>> result = selectAll(config, "Employee");
    assertEquals(DEFAULT_EMPLOYEES.size() + 1, result.size());
    Map<String, Object> expected = normalizeKeysAndValues(NEW_EMPLOYEE.toMap());
    List<Map<String, Object>> normalized =
        result.stream().map(IntegrationBaseTest::normalizeKeysAndValues).toList();
    assertThat(normalized).contains(expected);
  }

  void createTableAndAssertSuccess(IntegrationTestConfig config, String tableName, String columns) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(false, "CREATE TABLE " + tableName + " (" + columns + ")"));
    var response = jdbiJdbcClient.executeRequest(request);
    assertNull(response.resultSet());
    assertEquals(0, response.modifiedRows());
  }

  void createTableAndAssertThrows(IntegrationTestConfig config, String tableName, String columns) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(true, "CREATE TABLE " + tableName + " (" + columns + ")"));
    assertThrows(NoResultsException.class, () -> jdbiJdbcClient.executeRequest(request));
  }

  void createDatabaseAndAssertSuccess(IntegrationTestConfig config, String databaseName) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                Optional.ofNullable(config.rootUser()).orElse(config.username()),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(false, "CREATE DATABASE " + databaseName));
    var response = jdbiJdbcClient.executeRequest(request);
    assertNull(response.resultSet());
    assertEquals(
        (config.database() == SupportedDatabase.MYSQL
                || config.database() == SupportedDatabase.MARIADB)
            ? 1
            : 0,
        response.modifiedRows());
  }

  void createDatabaseAndAssertThrows(IntegrationTestConfig config, String databaseName) {
    JdbcRequest request =
        new JdbcRequest(
            config.database(),
            new DetailedConnection(
                config.host(),
                config.port(),
                Optional.ofNullable(config.rootUser()).orElse(config.username()),
                config.password(),
                config.databaseName(),
                config.properties()),
            new JdbcRequestData(true, "CREATE DATABASE " + databaseName));
    assertThrows(NoResultsException.class, () -> jdbiJdbcClient.executeRequest(request));
  }

  void cleanUpDatabase(IntegrationTestConfig config, String databaseName) throws SQLException {
    try (Connection conn =
            DriverManager.getConnection(
                config.url(),
                Optional.ofNullable(config.rootUser()).orElse(config.username()),
                config.password());
        Statement stmt = conn.createStatement()) {
      stmt.executeUpdate("DROP DATABASE IF EXISTS " + databaseName);
    }
  }

  record Employee(int id, String name, int age, String department) {

    static final String INSERT = "INSERT INTO Employee (id, name, age, department) VALUES %s";
    static final String CREATE_TABLE =
        """
                CREATE TABLE Employee (
                  id INT PRIMARY KEY,
                  name VARCHAR(100),
                  age INT,
                  department VARCHAR(100));
                """;

    static final String CREATE_TABLE_ORACLE =
        """
                CREATE TABLE Employee (
                  id NUMBER PRIMARY KEY,
                  name VARCHAR2(100),
                  age NUMBER,
                  department VARCHAR2(100)
                )
            """;

    String toInsertQueryFormat() {
      return String.format("(%d, '%s', %d, '%s')", id, name, age, department);
    }

    Map<String, Object> toMap() {
      return Map.of("id", id, "name", name, "age", age, "department", department);
    }

    Map<String, Object> toUnparsedMap() {
      return Map.of("id", id, "name", name, "age", age, "department", department);
    }
  }

  private static Map<String, Object> normalizeKeysAndValues(Map<String, Object> map) {
    return map.entrySet().stream()
        .collect(
            Collectors.toMap(e -> e.getKey().toLowerCase(), e -> normalizeValue(e.getValue())));
  }

  private static Object normalizeValue(Object value) {
    if (value instanceof Number) {
      return ((Number) value).longValue(); // or intValue() depending on expected range
    }
    if (value instanceof String) {
      return ((String) value).trim(); // avoid issues with padded CHARs
    }
    return value;
  }

  private static Map<String, Object> normalizeKeys(Map<String, Object> map) {
    return map.entrySet().stream()
        .collect(Collectors.toMap(entry -> entry.getKey().toLowerCase(), Map.Entry::getValue));
  }
}
