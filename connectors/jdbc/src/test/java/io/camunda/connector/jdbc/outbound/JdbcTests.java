/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.outbound;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MSSQLServerContainer;

public class JdbcTests {
  public static final MSSQLServerContainer mssqlserver = new MSSQLServerContainer().acceptLicense();
  static final String QUERY = "SELECT * FROM Employee";

  @BeforeAll
  public static void setUp() {
    mssqlserver.start();
    try (Connection conn =
            DriverManager.getConnection(
                mssqlserver.getJdbcUrl(), mssqlserver.getUsername(), mssqlserver.getPassword());
        Statement stmt = conn.createStatement()) {
      String sql =
          """
          CREATE TABLE Employee (
            Id INT PRIMARY KEY,
            Name VARCHAR(100),
            Age INT,
            Department VARCHAR(100));
          """;
      stmt.executeUpdate(sql);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @AfterAll
  public static void tearDown() {
    mssqlserver.stop();
  }

  @AfterEach
  public void cleanUp() {
    try (Connection conn =
            DriverManager.getConnection(
                mssqlserver.getJdbcUrl(), mssqlserver.getUsername(), mssqlserver.getPassword());
        Statement stmt = conn.createStatement()) {
      String sql = "DELETE FROM Employee";
      stmt.executeUpdate(sql);
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void test() {
    // Open a connection
    QueryRunner runner = new QueryRunner();
    try (Connection conn =
        DriverManager.getConnection(
            mssqlserver.getJdbcUrl(), mssqlserver.getUsername(), mssqlserver.getPassword())) {
      String insertSQL =
          "INSERT INTO Employee (Id,Name,Age,Department) "
              + "VALUES (?, ?, ?, ?),(?, ?, ?, ?),(?, ?, ?, ?)";
      int numRowsInserted =
          runner.update(
              conn,
              insertSQL,
              1,
              "John Doe",
              30,
              "Engineering",
              2,
              "Jane Smith",
              35,
              "Sales",
              3,
              "Mike Johnson",
              40,
              "Marketing");

      Assertions.assertEquals(numRowsInserted, 3);
    } catch (SQLException e) {
      e.printStackTrace();
    }

    MapListHandler beanListHandler = new MapListHandler();

    try (Connection conn =
        DriverManager.getConnection(
            mssqlserver.getJdbcUrl(), mssqlserver.getUsername(), mssqlserver.getPassword())) {
      List<Map<String, Object>> res = runner.query(conn, QUERY, beanListHandler);

      Assertions.assertEquals(res.size(), 3);
      res.forEach(System.out::println);
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }
}
