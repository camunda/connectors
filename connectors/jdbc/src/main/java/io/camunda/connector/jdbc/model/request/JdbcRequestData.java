/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.request;

import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

public record JdbcRequestData(
    @TemplateProperty(
            id = "returnResults",
            label = "Return results",
            feel = Property.FeelMode.disabled,
            group = "query",
            type = TemplateProperty.PropertyType.Boolean,
            description =
                "Check this box if the SQL statement return results, e.g. a SELECT or any statement with a RETURNING clause")
        boolean returnResults,
    @NotBlank
        @TemplateProperty(
            id = "query",
            label = "Insert the SQL Query to execute",
            group = "query",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            description =
                "The SQL query to execute. You can use named, positional or binding <a href=\"https://docs.camunda.io/docs/next/components/connectors/out-of-the-box-connectors/sql/#variables\" target=\"_blank\">parameters</a>")
        String query,
    @TemplateProperty(
            id = "variables",
            label = "SQL Query variables",
            group = "query",
            feel = Property.FeelMode.required,
            description =
                "The variables to use in the SQL query. Could be a list of values (if you used the positional (?) syntax), or a map of names to values (if you used named (:myValue) parameters).")
        @FEEL
        Object variables) {
  public JdbcRequestData(boolean returnResults, String query) {
    this(returnResults, query, null);
  }
}
