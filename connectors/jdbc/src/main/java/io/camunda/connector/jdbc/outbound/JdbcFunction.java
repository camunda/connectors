/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.outbound;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.jdbc.model.client.JdbcClient;
import io.camunda.connector.jdbc.model.client.JdbiJdbcClient;
import io.camunda.connector.jdbc.model.request.JdbcRequest;
import io.camunda.connector.jdbc.model.response.JdbcResponse;

@OutboundConnector(
    name = "SQL Database Connector",
    inputVariables = {"database", "connection", "data"},
    type = "io.camunda:connector-jdbc:1")
@ElementTemplate(
    engineVersion = "^8.6",
    id = "io.camunda.connectors.Jdbc.v1",
    name = "SQL Database Connector",
    version = 3,
    description =
        "Read and write data from a Camunda process directly to a SQL database (e.g., Microsoft SQL Server, MySQL, PostgreSQL)",
    metadata = @ElementTemplate.Metadata(keywords = {"relational", "database"}),
    icon = "icon.svg",
    documentationRef =
        "https://docs.camunda.io/docs/8.6/components/connectors/out-of-the-box-connectors/sql",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = JdbcFunction.DATABASE_GROUP_ID, label = "Database"),
      @ElementTemplate.PropertyGroup(id = JdbcFunction.CONNECTION_GROUP_ID, label = "Connection"),
      @ElementTemplate.PropertyGroup(id = JdbcFunction.QUERY_GROUP_ID, label = "Query"),
    },
    inputDataClass = JdbcRequest.class,
    outputDataClass = JdbcResponse.class)
public class JdbcFunction implements OutboundConnectorFunction {
  static final String DATABASE_GROUP_ID = "database";
  static final String CONNECTION_GROUP_ID = "connection";
  static final String QUERY_GROUP_ID = "query";

  private final JdbcClient jdbcClient;

  public JdbcFunction() {
    this.jdbcClient = new JdbiJdbcClient();
  }

  JdbcFunction(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    final var jdbcRequest = context.bindVariables(JdbcRequest.class);
    return jdbcClient.executeRequest(jdbcRequest);
  }
}
