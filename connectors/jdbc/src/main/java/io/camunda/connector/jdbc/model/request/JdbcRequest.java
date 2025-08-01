/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.request;

import static io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType.Dropdown;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.jdbc.model.request.connection.JdbcConnection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record JdbcRequest(
    @NotNull
        @TemplateProperty(
            id = "database",
            label = "Select a database",
            description =
                "Select the database you want to connect to. "
                    + "If you choose Oracle, make sure the Oracle JDBC driver is included. "
                    + "<a href=\"https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/sql/#database\">Learn how to set it up.</a>",
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
    @Valid @NotNull JdbcRequestData data) {}
