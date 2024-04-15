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

import io.camunda.connector.jdbc.model.JdbcClient;
import io.camunda.connector.jdbc.model.request.JdbcRequest;
import io.camunda.connector.jdbc.model.request.JdbcRequestData;
import io.camunda.connector.jdbc.model.request.connection.DetailedConnection;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapListHandler;

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

  private final JdbcClient.ApacheJdbcClient apacheJdbcClient = new JdbcClient.ApacheJdbcClient();

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

  List<Map<String, Object>> selectAllEmployees(IntegrationTestConfig config) throws SQLException {
    try (Connection conn =
        DriverManager.getConnection(config.url(), config.username(), config.password())) {
      // using apache query runner
      QueryRunner queryRunner = new QueryRunner();
      if (config.databaseName() != null) {
        queryRunner.update(conn, "USE " + config.databaseName());
      }
      return queryRunner.query(
          conn, "SELECT * FROM Employee ORDER BY id ASC", new MapListHandler());
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

  void deleteAllEmployees(IntegrationTestConfig config) throws SQLException {
    try (Connection conn =
            DriverManager.getConnection(config.url(), config.username(), config.password());
        Statement stmt = conn.createStatement()) {
      if (config.databaseName() != null) {
        stmt.executeUpdate("USE " + config.databaseName());
      }
      stmt.executeUpdate(Employee.DELETE_ALL);
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
    var response = apacheJdbcClient.executeRequest(request);
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
    var response = apacheJdbcClient.executeRequest(request);
    assertEquals(1, response.modifiedRows());
    assertNull(response.resultSet());
  }

  void assertEmployeeUpdated(IntegrationTestConfig config) throws SQLException {
    List<Map<String, Object>> result = selectAllEmployees(config);
    assertEquals(DEFAULT_EMPLOYEES.size(), result.size());
    assertEquals(DEFAULT_EMPLOYEES.get(0).name() + " UPDATED", result.get(0).get("name"));
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
    var response = apacheJdbcClient.executeRequest(request);
    assertEquals(1, response.modifiedRows());
    assertNull(response.resultSet());
  }

  void assertNewEmployeeCreated(IntegrationTestConfig config) throws SQLException {
    List<Map<String, Object>> result = selectAllEmployees(config);
    assertEquals(DEFAULT_EMPLOYEES.size() + 1, result.size());
    assertThat(result).contains(NEW_EMPLOYEE.toMap());
  }

  record Employee(int id, String name, int age, String department) {

    static final String DELETE_ALL = "DELETE FROM Employee";
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
