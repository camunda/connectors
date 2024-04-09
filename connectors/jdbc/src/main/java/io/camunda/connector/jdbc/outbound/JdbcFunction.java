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
import io.camunda.connector.jdbc.model.request.JdbcRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "SQL Database Connector",
    inputVariables = {"database", "connection", "data"},
    type = "io.camunda:connector-jdbc:1")
@ElementTemplate(
    id = "io.camunda.connectors.Jdbc.v1",
    name = "SQL Database Connector",
    version = 1,
    description =
        "Read and write data from a Camunda process directly to a SQL database(Microsoft SQL Server, MySQL, PostgreSQL)",
    icon = "icon.svg",
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/jdbc", // TODO
    // docs
    // write docs
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = JdbcFunction.DATABASE_GROUP_ID, label = "Database"),
      @ElementTemplate.PropertyGroup(id = JdbcFunction.CONNECTION_GROUP_ID, label = "Connection"),
      @ElementTemplate.PropertyGroup(id = JdbcFunction.QUERY_GROUP_ID, label = "Query"),
    },
    inputDataClass = JdbcRequest.class)
public class JdbcFunction implements OutboundConnectorFunction {
  static final String DATABASE_GROUP_ID = "database";
  static final String CONNECTION_GROUP_ID = "connection";
  static final String QUERY_GROUP_ID = "query";

  private static final Logger LOGGER = LoggerFactory.getLogger(JdbcFunction.class);

  @Override
  public Object execute(OutboundConnectorContext context) {
    final var connectorRequest = context.bindVariables(JdbcRequest.class);
    return executeConnector(connectorRequest);
  }

  private Object executeConnector(JdbcRequest jdbcRequest) {
    LOGGER.info("Executing SQL Database Connector with request: {}", jdbcRequest);
    // TODO implement connector logic
    return null;
  }
}
