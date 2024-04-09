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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@TemplateSubType(id = "uri", label = "URI")
public record UriConnection(
    @NotBlank
        @Pattern(
            regexp = "^(jdbc:|secrets|\\{\\{).*$",
            message = "Must start with jdbc: or contain a secret reference")
        @TemplateProperty(
            group = "connection",
            label = "URI",
            description =
                "URI should contain JDBC driver, username, password, host name, and port number")
        String uri)
    implements JdbcConnection {
  @Override
  public String getConnectionString(SupportedDatabase database) {
    return uri;
  }
}
