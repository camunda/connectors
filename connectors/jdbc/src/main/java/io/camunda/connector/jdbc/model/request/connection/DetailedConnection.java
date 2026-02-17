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
import io.camunda.connector.jdbc.utils.ConnectionStringHelper;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;

@TemplateSubType(id = "detailed", label = "Detailed")
public record DetailedConnection(
    @NotBlank @TemplateProperty(group = "connection", label = "Host") String host,
    @NotBlank @TemplateProperty(group = "connection", label = "Port") String port,
    @TemplateProperty(group = "connection", label = "Username", optional = true) String username,
    @TemplateProperty(group = "connection", label = "Password", optional = true) String password,
    @TemplateProperty(group = "connection", label = "Database name", optional = true)
        String databaseName,
    @TemplateProperty(
            group = "connection",
            label = "Properties",
            optional = true,
            feel = FeelMode.required,
            description =
                "Additional properties for the connection. For more information, see the <a href=\"https://docs.camunda.io/docs/8.6/components/connectors/out-of-the-box-connectors/sql/#connection\" target=\"_blank\">documentation</a>.")
        @FEEL
        Map<String, String> properties)
    implements JdbcConnection {
  @Override
  public String getConnectionString(SupportedDatabase database) {
    return ConnectionStringHelper.buildConnectionString(database, this);
  }

  @Override
  public Properties getProperties() {
    Properties properties = new Properties();
    if (this.properties() != null) {
      properties.putAll(this.properties());
    }
    if (StringUtils.isNotBlank(this.username())) {
      properties.put(AuthProperties.USER, this.username());
    }
    if (StringUtils.isNotBlank(this.password())) {
      properties.put(AuthProperties.PASSWORD, this.password());
    }
    return properties;
  }

  @Override
  public String toString() {
    return "DetailedConnection{"
        + "host='"
        + host
        + "'"
        + ", port='"
        + port
        + "'"
        + ", username=[REDACTED]"
        + ", password=[REDACTED]"
        + ", databaseName='"
        + databaseName
        + "'"
        + ", properties="
        + properties
        + "}";
  }
}
