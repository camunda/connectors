/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.request;

public enum SupportedDatabase {
  MARIADB("org.mariadb.jdbc.Driver", "jdbc:mariadb://"),
  MSSQL("com.microsoft.sqlserver.jdbc.SQLServerDriver", "jdbc:sqlserver://"),
  MYSQL("org.mariadb.jdbc.Driver", "jdbc:mysql://"),
  POSTGRESQL("org.postgresql.Driver", "jdbc:postgresql://"),
  ORACLE("oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@//");

  private final String driverClassName;

  private final String urlSchema;

  SupportedDatabase(String driverClassName, String urlSchema) {
    this.driverClassName = driverClassName;
    this.urlSchema = urlSchema;
  }

  public String getDriverClassName() {
    return driverClassName;
  }

  public String getUrlSchema() {
    return urlSchema;
  }
}
