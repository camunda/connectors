/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.request.connection;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.jdbc.model.request.SupportedDatabase;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "authType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = UriConnection.class, name = "uri"),
  @JsonSubTypes.Type(value = DetailedConnection.class, name = "detailed")
})
@TemplateDiscriminatorProperty(
    label = "Connection type",
    group = "connection",
    name = "authType",
    defaultValue = "uri")
public sealed interface JdbcConnection permits UriConnection, DetailedConnection {
  String getConnectionString(SupportedDatabase database);
}
