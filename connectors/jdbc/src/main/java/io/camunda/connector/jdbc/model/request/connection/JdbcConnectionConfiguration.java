/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.request.connection;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration (credential) template for a reusable JDBC connection. Demonstrates the whole-object
 * binding model: an element template embeds this template under {@code configurationTemplates} and
 * a {@code Configuration} chooser lets a Camunda developer pick a stored connection instead of
 * filling in the inline connection fields.
 *
 * <p>{@code database} is mandatory here (unlike the connector's own inline field, which is only
 * required when no credential is bound) - the credential is a complete, standalone connection
 * description, and the driver/URL scheme it resolves to differs per database. Version bumped to 2
 * for this new required field (see {@code version()}'s floor-semantics in ADR-0004, {@code
 * docs/adr/ADR-0004-configuration-templates-in-element-templates.md}): a v1 instance predating this
 * field would otherwise be silently invalid.
 */
@Configuration(
    id = "io.camunda.connectors:jdbc-connection:1",
    version = 2,
    name = "JDBC Connection")
public record JdbcConnectionConfiguration(
    @NotNull
        @TemplateProperty(
            label = "Select a database",
            tooltip =
                "If you choose Oracle, make sure the Oracle JDBC driver is included. "
                    + "<a href=\"https://docs.camunda.io/docs/8.9/components/connectors/out-of-the-box-connectors/sql/#database\">Oracle JDBC driver setup</a>.",
            group = "connection",
            type = PropertyType.Dropdown,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            choices = {
              @TemplateProperty.DropdownPropertyChoice(label = "MariaDB", value = "MARIADB"),
              @TemplateProperty.DropdownPropertyChoice(
                  label = "Microsoft SQL Server",
                  value = "MSSQL"),
              @TemplateProperty.DropdownPropertyChoice(label = "MySQL", value = "MYSQL"),
              @TemplateProperty.DropdownPropertyChoice(label = "PostgreSQL", value = "POSTGRESQL"),
              @TemplateProperty.DropdownPropertyChoice(label = "Oracle", value = "ORACLE"),
            })
        SupportedDatabase database,
    @NotBlank @TemplateProperty(group = "connection", label = "Host") String host,
    @NotBlank @TemplateProperty(group = "connection", label = "Port") String port,
    @TemplateProperty(group = "connection", label = "Database name") String databaseName,
    @TemplateProperty(group = "authentication", label = "Username", secret = true) String username,
    @TemplateProperty(group = "authentication", label = "Password", secret = true)
        String password) {

  /**
   * Delegates to the canonical constructor for the pre-{@code database}-field shape (this
   * credential's version 1, before the database engine became part of it). {@code database} is left
   * {@code null} here, which the {@code @NotNull} constraint above then correctly rejects at
   * validation time -- see this record's class javadoc on why a v1 instance must not silently pass.
   */
  public JdbcConnectionConfiguration(
      String host, String port, String databaseName, String username, String password) {
    this(null, host, port, databaseName, username, password);
  }

  /** Adapts this credential to the connector's existing {@link DetailedConnection} shape. */
  public DetailedConnection toDetailedConnection() {
    return new DetailedConnection(host, port, username, password, databaseName, null);
  }

  @Override
  public String toString() {
    return "JdbcConnectionConfiguration{"
        + "database="
        + database
        + ", host="
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
