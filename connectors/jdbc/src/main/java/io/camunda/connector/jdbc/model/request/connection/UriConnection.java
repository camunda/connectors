/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.request.connection;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
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
            regexp = "^(=|(jdbc:|secrets|\\{\\{).*$)",
            message = "Must start with 'jdbc:' or contain a secret reference")
        @TemplateProperty(
            group = "connection",
            label = "URI",
            description =
                "URI should contain JDBC driver, host name, and port number. For more information, see the <a href=\"https://docs.camunda.io/docs/8.6/components/connectors/out-of-the-box-connectors/sql/#uri-connection\" target=\"_blank\">documentation</a>.)")
        String uri,
    @TemplateProperty(
            group = "connection",
            label = "Properties",
            feel = FeelMode.required,
            optional = true,
            description =
                "Additional properties for the connection ('user' and 'password' for instance). For more information, see the <a href=\"https://docs.camunda.io/docs/8.6/components/connectors/out-of-the-box-connectors/sql/#connection\" target=\"_blank\">documentation</a>.")
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

  @Override
  public String toString() {
    return "UriConnection{" + "uri='" + uri + "'" + ", uriProperties=[REDACTED]" + "}";
  }
}
