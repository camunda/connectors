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
import java.util.Optional;
import java.util.function.Function;

@TemplateSubType(id = "detailed", label = "Detailed")
public record DetailedConnection(
    @NotBlank @TemplateProperty(group = "connection", label = "Host") String host,
    @NotBlank @TemplateProperty(group = "connection", label = "Port") String port,
    @TemplateProperty(group = "connection", label = "Username") String username,
    @TemplateProperty(group = "connection", label = "Password") String password)
    implements JdbcConnection {
  @Override
  public String getConnectionString(SupportedDatabase database) {
    String authentication = Optional.ofNullable(username).map(buildUserPasswordString()).orElse("");
    return database.getUrlSchema() + authentication + host + ":" + port;
  }

  private Function<String, String> buildUserPasswordString() {
    return u -> u + ":" + password + "@";
  }
}
