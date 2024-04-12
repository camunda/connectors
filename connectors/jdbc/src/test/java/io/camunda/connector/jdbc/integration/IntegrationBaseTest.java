/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.integration;

import static io.camunda.connector.jdbc.integration.IntegrationBaseTest.Employee.INSERT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.camunda.connector.jdbc.model.JdbcClient;
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

public abstract class IntegrationBaseTest {
  static final List<Employee> DEFAULT_EMPLOYEES =
      List.of(
          new Employee(1, "John Doe", 30, "IT"),
          new Employee(2, "Jane Doe", 25, "HR"),
          new Employee(3, "Alice", 35, "Finance"),
          new Employee(4, "Bob", 40, "IT"),
          new Employee(5, "Charlie", 45, "HR"),
          new Employee(6, "David", 50, "Finance"));

  private final JdbcClient.ApacheJdbcClient apacheJdbcClient = new JdbcClient.ApacheJdbcClient();

  static void createEmployeeTable(String database, String url, String username, String password)
      throws SQLException {
    try (Connection conn = DriverManager.getConnection(url, username, password);
        Statement stmt = conn.createStatement()) {
      if (database != null) {
        stmt.executeUpdate("USE " + database);
      }
      stmt.executeUpdate(Employee.CREATE_TABLE);
    }
  }

  static void createEmployeeTable(String url, String username, String password)
      throws SQLException {
    createEmployeeTable(null, url, username, password);
  }

  void insertDefaultEmployees(String database, String url, String username, String password)
      throws SQLException {
    try (Connection conn = DriverManager.getConnection(url, username, password);
        Statement stmt = conn.createStatement()) {
      String sql =
          INSERT.formatted(
              DEFAULT_EMPLOYEES.stream()
                  .map(Employee::toInsertQueryFormat)
                  .collect(Collectors.joining(",")));
      if (database != null) {
        stmt.executeUpdate("USE " + database);
      }
      stmt.executeUpdate(sql);
    }
  }

  void insertDefaultEmployees(String url, String username, String password) throws SQLException {
    insertDefaultEmployees(null, url, username, password);
  }

  void deleteAllEmployees(String database, String url, String username, String password)
      throws SQLException {
    try (Connection conn = DriverManager.getConnection(url, username, password);
        Statement stmt = conn.createStatement()) {
      if (database != null) {
        stmt.executeUpdate("USE " + database);
      }
      stmt.executeUpdate(Employee.DELETE_ALL);
    }
  }

  void deleteAllEmployees(String url, String username, String password) throws SQLException {
    deleteAllEmployees(null, url, username, password);
  }

  protected void shouldReturnResultList_whenSelectQuery(
      SupportedDatabase database,
      String host,
      String port,
      String username,
      String password,
      String databaseName,
      Map<String, String> properties) {
    JdbcRequest request =
        new JdbcRequest(
            database,
            new DetailedConnection(host, port, username, password, databaseName, properties),
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

  record Employee(int id, String name, int age, String department) {

    static final String DELETE_ALL = "DELETE FROM Employee";
    static final String INSERT = "INSERT INTO Employee (Id, Name, Age, Department) VALUES %s";
    static final String CREATE_TABLE =
        """
                CREATE TABLE Employee (
                  Id INT PRIMARY KEY,
                  Name VARCHAR(100),
                  Age INT,
                  Department VARCHAR(100));
                """;

    String toInsertQueryFormat() {
      return String.format("(%d, '%s', %d, '%s')", id, name, age, department);
    }

    Map<String, Object> toMap() {
      return Map.of("Id", id, "Name", name, "Age", age, "Department", department);
    }
  }
}
