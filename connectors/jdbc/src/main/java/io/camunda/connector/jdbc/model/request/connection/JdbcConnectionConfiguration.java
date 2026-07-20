/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.request.connection;

import io.camunda.connector.generator.java.annotation.ConfigurationTemplate;
import io.camunda.connector.generator.java.annotation.TemplateProperty;

/**
 * Configuration (credential) template for a reusable JDBC connection. Demonstrates the whole-object
 * binding model: an element template embeds this template under {@code configurationTemplates} and
 * a {@code Configuration} chooser lets a Camunda developer pick a stored connection instead of
 * filling in the inline connection fields.
 */
@ConfigurationTemplate(
    id = "io.camunda.connectors:jdbc-connection:1",
    version = 1,
    name = "JDBC Connection")
public record JdbcConnectionConfiguration(
    @TemplateProperty(group = "connection", label = "Host") String host,
    @TemplateProperty(group = "connection", label = "Port") String port,
    @TemplateProperty(group = "connection", label = "Database name") String databaseName,
    @TemplateProperty(group = "authentication", label = "Username", secret = true) String username,
    @TemplateProperty(group = "authentication", label = "Password", secret = true)
        String password) {

  /** Adapts this credential to the connector's existing {@link DetailedConnection} shape. */
  public DetailedConnection toDetailedConnection() {
    return new DetailedConnection(host, port, username, password, databaseName, null);
  }

  @Override
  public String toString() {
    return "JdbcConnectionConfiguration{"
        + "host="
        + host
        + ", port="
        + port
        + ", databaseName="
        + databaseName
        + ", username=[REDACTED]"
        + ", password=[REDACTED]"
        + "}";
  }
}
