/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.camunda.connector.jdbc.outbound;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.jdbc.outbound.model.JdbcRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "SQL Database Connector",
    inputVariables = {"authentication", "message"}, // TODO add input variables
    type = "io.camunda:connector-jdbc:1")
@ElementTemplate(
    id = "io.camunda.connectors.Jdbc.v1",
    name = "SQL Database Connector",
    version = 1,
    description =
        "Read and write data from a Camunda process directly to a SQL database(MSSQL, MySQL, PostgreSQL)",
    icon = "icon.svg",
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/jdbc", // TODO
    // write docs
    propertyGroups = {
      @ElementTemplate.PropertyGroup(
          id = JdbcFunction.AUTHENTICATION_GROUP_ID,
          label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = JdbcFunction.OPERATION_GROUP_ID, label = "Operation"),
      @ElementTemplate.PropertyGroup(id = JdbcFunction.QUERY_GROUP_ID, label = "Query"),
    },
    inputDataClass = JdbcRequest.class)
public class JdbcFunction implements OutboundConnectorFunction {
  static final String AUTHENTICATION_GROUP_ID = "authentication";
  static final String OPERATION_GROUP_ID = "operation";
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
