/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model;

import static io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType.Dropdown;

import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.jdbc.model.auth.JdbcAuthentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record JdbcRequest(
    @NotNull
        @TemplateProperty(
            id = "database",
            label = "Database",
            group = "database",
            type = Dropdown,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            choices = {
              @TemplateProperty.DropdownPropertyChoice(label = "MSSQL", value = "MSSQL"),
              @TemplateProperty.DropdownPropertyChoice(label = "MySQL", value = "MYSQL"),
              @TemplateProperty.DropdownPropertyChoice(label = "PostgreSQL", value = "POSTGRESQL"),
            })
        SupportedDatabase database,
    @Valid JdbcAuthentication authentication,
    @NotNull
        @TemplateProperty(
            id = "isModifyingQuery",
            label = "Modifying query",
            type = TemplateProperty.PropertyType.Dropdown,
            group = "query",
            description = "Check this box if the query is anything other than a SELECT query",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "true", label = "Yes"),
              @TemplateProperty.DropdownPropertyChoice(value = "false", label = "No")
            })
        Boolean isModifyingQuery,
    @NotBlank
        @TemplateProperty(
            id = "query",
            label = "Query",
            group = "query",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            description =
                "The SQL query to execute. You can use placeholders (?) for variables") // TODO link
        // to docs
        String query,
    @TemplateProperty(
            id = "variables",
            label = "Variables",
            group = "query",
            feel = Property.FeelMode.required,
            description =
                "The variables to use in the SQL query. Use the same order as in the statement")
        @FEEL
        List<?> variables) {}
