/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.request;

import static io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType.Configuration;
import static io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType.Dropdown;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.jdbc.model.request.connection.JdbcConnection;
import io.camunda.connector.jdbc.model.request.connection.JdbcConnectionConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record JdbcRequest(
    @NotNull
        @TemplateProperty(
            id = "database",
            label = "Select a database",
            tooltip =
                "If you choose Oracle, make sure the Oracle JDBC driver is included. "
                    + "<a href=\"https://docs.camunda.io/docs/8.9/components/connectors/out-of-the-box-connectors/sql/#database\">Oracle JDBC driver setup</a>.",
            group = "database",
            type = Dropdown,
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
    @Valid @NotNull JdbcConnection connection,
    @Valid @NotNull JdbcRequestData data,
    @TemplateProperty(
            id = "connectionConfiguration",
            label = "Connection credential",
            group = "connection",
            type = Configuration,
            optional = true,
            binding = @TemplateProperty.PropertyBinding(name = "configuration"),
            description =
                "Choose a reusable JDBC connection credential. When set, it is bound as a whole to"
                    + " the connector's 'configuration' input.")
        JdbcConnectionConfiguration configuration) {

  /** Convenience constructor for the pre-configuration-chooser shape (no bound configuration). */
  public JdbcRequest(SupportedDatabase database, JdbcConnection connection, JdbcRequestData data) {
    this(database, connection, data, null);
  }
}
