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

import io.camunda.connector.jdbc.model.client.JdbcClient;
import io.camunda.connector.jdbc.model.client.JdbiJdbcClient;
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
import java.util.stream.Collectors;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.result.NoResultsException;
import org.jdbi.v3.core.statement.UnableToCreateStatementException;

public abstract class IntegrationBaseTest {
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
      if (config.databaseName() != null) {
        stmt.executeUpdate("USE " + config.databaseName());
      }
      stmt.executeUpdate(Employee.CREATE_TABLE);
    }
  }

  List<Map<String, Object>> selectAll(IntegrationTestConfig config, String tableName)
      throws SQLException {
    try (Connection conn =
        DriverManager.getConnection(config.url(), config.username(), config.password())) {
      // using jdbi
      try (var handle = Jdbi.create(conn).open()) {
        if (config.databaseName() != null) {
          handle.execute("USE " + config.databaseName());
        }
        return handle
            .createQuery("SELECT * FROM " + tableName + " ORDER BY id ASC")
            .mapToMap()
            .list();
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
      if (config.databaseName() != null) {
        stmt.executeUpdate("USE " + config.databaseName());
      }
      stmt.executeUpdate(sql);
    }
  }

  void cleanUp(IntegrationTestConfig config) throws SQLException {
    try (Connection conn =
            DriverManager.getConnection(config.url(), config.username(), config.password());
        Statement stmt = conn.createStatement()) {
      stmt.executeUpdate("DELETE FROM Employee");
      stmt.executeUpdate("DROP DATABASE IF EXISTS mydb");
      if (config.databaseName() != null) {
        stmt.executeUpdate("USE " + config.databaseName());
      }
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
            new JdbcRequestData(false, "SELECT * FROM Employee ORDER BY Id ASC"));
    var response = jdbiJdbcClient.executeRequest(request);
    assertNull(response.modifiedRows());
    assertNotNull(response.resultSet());
    assertEquals(6, response.resultSet().size());
    // assert each row
    for (int i = 0; i < DEFAULT_EMPLOYEES.size(); i++) {
      var row = response.resultSet().get(i);
      var employee = DEFAULT_EMPLOYEES.get(i);
      assertEquals(employee.toMap(), row);
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
            new JdbcRequestData(true, "SELECT * FROM Employee ORDER BY Id ASC"));
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
                false, "SELECT * FROM Employee WHERE name = :name", Map.of("name", "John Doe")));
    var response = jdbiJdbcClient.executeRequest(request);
    assertNull(response.modifiedRows());
    assertNotNull(response.resultSet());
    assertEquals(1, response.resultSet().size());
    assertEquals(
        DEFAULT_EMPLOYEES.stream()
            .filter(e -> e.name().equals("John Doe"))
            .map(Employee::toMap)
            .findFirst()
            .get(),
        response.resultSet().get(0));
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
                false,
                "SELECT * FROM Employee WHERE name IN (:nameList)",
                Map.of("nameList", List.of("John Doe", "Jane Doe"))));
    assertThrows(
        UnableToCreateStatementException.class, () -> jdbiJdbcClient.executeRequest(request));
  }

  void selectDataWithPositionalParametersAndAssertSuccess(IntegrationTestConfig config) {
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
                false, "SELECT * FROM Employee WHERE name = ?", List.of("John Doe")));
    var response = jdbiJdbcClient.executeRequest(request);
    assertNull(response.modifiedRows());
    assertNotNull(response.resultSet());
    assertEquals(1, response.resultSet().size());
    assertEquals(
        DEFAULT_EMPLOYEES.stream()
            .filter(e -> e.name().equals("John Doe"))
            .map(Employee::toMap)
            .findFirst()
            .get(),
        response.resultSet().get(0));
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
                false,
                "SELECT * FROM Employee WHERE name IN (?, ?)",
                List.of("John Doe", "Jane Doe")));
    var response = jdbiJdbcClient.executeRequest(request);
    assertNull(response.modifiedRows());
    assertNotNull(response.resultSet());
    assertEquals(2, response.resultSet().size());
    assertEquals(
        DEFAULT_EMPLOYEES.stream()
            .filter(e -> e.name().equals("John Doe") || e.name().equals("Jane Doe"))
            .map(Employee::toMap)
            .collect(Collectors.toList()),
        response.resultSet());
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
                false,
                "SELECT * FROM Employee WHERE name IN (<params>)",
                Map.of("params", List.of("John Doe", "Jane Doe"))));
    var response = jdbiJdbcClient.executeRequest(request);
    assertNull(response.modifiedRows());
    assertNotNull(response.resultSet());
    assertEquals(2, response.resultSet().size());
    assertEquals(
        DEFAULT_EMPLOYEES.stream()
            .filter(e -> e.name().equals("John Doe") || e.name().equals("Jane Doe"))
            .map(Employee::toMap)
            .collect(Collectors.toList()),
        response.resultSet());
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
            new JdbcRequestData(true, "UPDATE Employee SET name = '" + name + "' WHERE id = 1"));
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
                false, "UPDATE Employee SET name = 'John Doe UPDATED' WHERE id = 1"));
    assertThrows(NoResultsException.class, () -> jdbiJdbcClient.executeRequest(request));
  }

  void assertEmployeeUpdated(IntegrationTestConfig config) throws SQLException {
    List<Map<String, Object>> result = selectAll(config, "Employee");
    assertEquals(DEFAULT_EMPLOYEES.size(), result.size());
    assertEquals(DEFAULT_EMPLOYEES.get(0).name() + " UPDATED", result.get(0).get("name"));
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
            new JdbcRequestData(true, "DELETE FROM Employee WHERE id = 1"));
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
            new JdbcRequestData(false, "DELETE FROM Employee WHERE id = 1"));
    assertThrows(NoResultsException.class, () -> jdbiJdbcClient.executeRequest(request));
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
                true, Employee.INSERT.formatted(NEW_EMPLOYEE.toInsertQueryFormat())));
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
                false, Employee.INSERT.formatted(NEW_EMPLOYEE.toInsertQueryFormat())));
    assertThrows(NoResultsException.class, () -> jdbiJdbcClient.executeRequest(request));
  }

  void assertNewEmployeeCreated(IntegrationTestConfig config) throws SQLException {
    List<Map<String, Object>> result = selectAll(config, "Employee");
    assertEquals(DEFAULT_EMPLOYEES.size() + 1, result.size());
    assertThat(result).contains(NEW_EMPLOYEE.toMap());
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
            new JdbcRequestData(true, "CREATE TABLE " + tableName + " (" + columns + ")"));
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
            new JdbcRequestData(false, "CREATE TABLE " + tableName + " (" + columns + ")"));
    assertThrows(NoResultsException.class, () -> jdbiJdbcClient.executeRequest(request));
  }

  void createDatabaseAndAssertSuccess(IntegrationTestConfig config, String databaseName) {
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
            new JdbcRequestData(true, "CREATE DATABASE " + databaseName));
    var response = jdbiJdbcClient.executeRequest(request);
    assertNull(response.resultSet());
    assertEquals(config.database() == SupportedDatabase.MYSQL ? 1 : 0, response.modifiedRows());
  }

  void createDatabaseAndAssertThrows(IntegrationTestConfig config, String databaseName) {
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
            new JdbcRequestData(false, "CREATE DATABASE " + databaseName));
    assertThrows(NoResultsException.class, () -> jdbiJdbcClient.executeRequest(request));
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

    String toInsertQueryFormat() {
      return String.format("(%d, '%s', %d, '%s')", id, name, age, department);
    }

    Map<String, Object> toMap() {
      return Map.of("id", id, "name", name, "age", age, "department", department);
    }
  }
}
