/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.request;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

public record JdbcRequestData(
    @TemplateProperty(
            id = "returnResults",
            label = "Return results",
            defaultValue = "false",
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            group = "query",
            type = TemplateProperty.PropertyType.Boolean,
            description =
                "Check this box if the SQL statement return results, e.g. a SELECT or any statement with a RETURNING clause")
        boolean returnResults,
    @NotBlank
        @TemplateProperty(
            id = "query",
            label = "SQL Query to execute",
            group = "query",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            description =
                "You can use named, positional or binding <a href=\"https://docs.camunda.io/docs/8.6/components/connectors/out-of-the-box-connectors/sql/#variables\" target=\"_blank\">parameters</a>")
        String query,
    @TemplateProperty(
            id = "variables",
            label = "SQL Query variables",
            group = "query",
            optional = true,
            feel = FeelMode.required,
            description =
                "The <a href=\"https://docs.camunda.io/docs/8.6/components/connectors/out-of-the-box-connectors/sql/#variables\" target=\"_blank\">variables</a> to use in the SQL query.")
        @FEEL
        Object variables) {
  public JdbcRequestData(boolean returnResults, String query) {
    this(returnResults, query, null);
  }
}
