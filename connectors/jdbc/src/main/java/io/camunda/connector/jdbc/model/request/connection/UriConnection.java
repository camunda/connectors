/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.request.connection;

import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import java.util.Properties;

@TemplateSubType(id = "uri", label = "URI")
public record UriConnection(
    @NotBlank
        @Pattern(
            regexp = "^(=|jdbc:|secrets|\\{\\{).*$",
            message = "Must start with jdbc: or contain a secret reference")
        @TemplateProperty(
            group = "connection",
            label = "URI",
            defaultValue = "jdbc:postgresql://localhost:5432/mydb",
            description =
                "URI should contain JDBC driver, username, password, host name, and port number. See the <a href=\"https://docs.camunda.io/docs/next/components/connectors/out-of-the-box-connectors/sql/#uri-connection\" target=\"_blank\">documentation</a>.)")
        String uri,
    @TemplateProperty(
            group = "connection",
            label = "Properties",
            feel = Property.FeelMode.required,
            description =
                "Additional properties for the connection. For more information, see the <a href=\"https://docs.camunda.io/docs/next/components/connectors/out-of-the-box-connectors/sql/#connection\" target=\"_blank\">documentation</a>.")
        @FEEL
        Map<String, String> uriProperties)
    implements JdbcConnection {
  @Override
  public String getConnectionString(SupportedDatabase database) {
    return uri;
  }

  @Override
  public Properties getProperties() {
    if (uriProperties == null) {
      return new Properties();
    }
    Properties properties = new Properties();
    properties.putAll(this.uriProperties());
    return properties;
  }
}
