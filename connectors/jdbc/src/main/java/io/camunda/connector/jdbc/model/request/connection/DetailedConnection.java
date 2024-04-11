/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.request.connection;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import io.camunda.connector.jdbc.utils.ConnectionHelper;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Properties;

@TemplateSubType(id = "detailed", label = "Detailed")
public record DetailedConnection(
    @NotBlank @TemplateProperty(group = "connection", label = "Host") String host,
    @NotBlank @TemplateProperty(group = "connection", label = "Port") String port,
    @TemplateProperty(group = "connection", label = "Username") String username,
    @TemplateProperty(group = "connection", label = "Password") String password,
    @TemplateProperty(group = "connection", label = "Database name") String databaseName,
    @TemplateProperty(
            group = "connection",
            label = "Properties",
            description =
                "Provide the payload for the event as JSON. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-eventbridge/?awseventbridge=outbound\" target=\"_blank\">documentation</a>")
        Map<String, String> properties)
    implements JdbcConnection {
  @Override
  public String getConnectionString(SupportedDatabase database) {
    return ConnectionHelper.buildConnectionString(
        database, host, port, username, password, databaseName);
  }

  @Override
  public Properties getProperties() {
    if (properties == null) {
      return new Properties();
    }
    Properties properties = new Properties();
    properties.putAll(this.properties());
    return properties;
  }
}
