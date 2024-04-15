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

package io.camunda.connector.jdbc.integration;

import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import java.util.List;
import java.util.Map;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

record IntegrationTestConfig(
    SupportedDatabase database,
    String url,
    String host,
    String port,
    String username,
    String password,
    String databaseName,
    Map<String, String> properties) {

  public static List<IntegrationTestConfig> from(
      MySQLContainer mySqlServer,
      MSSQLServerContainer msSqlServer,
      PostgreSQLContainer postgreServer) {
    return List.of(
        new IntegrationTestConfig(
            SupportedDatabase.MSSQL,
            msSqlServer.getJdbcUrl(),
            msSqlServer.getHost(),
            String.valueOf(msSqlServer.getMappedPort(1433)),
            msSqlServer.getUsername(),
            msSqlServer.getPassword(),
            null,
            Map.of("encrypt", "false")),
        new IntegrationTestConfig(
            SupportedDatabase.MYSQL,
            mySqlServer.getJdbcUrl(),
            mySqlServer.getHost(),
            String.valueOf(mySqlServer.getMappedPort(3306)),
            mySqlServer.getUsername(),
            mySqlServer.getPassword(),
            mySqlServer.getDatabaseName(),
            null),
        new IntegrationTestConfig(
            SupportedDatabase.POSTGRESQL,
            postgreServer.getJdbcUrl(),
            postgreServer.getHost(),
            String.valueOf(postgreServer.getMappedPort(5432)),
            postgreServer.getUsername(),
            postgreServer.getPassword(),
            null,
            null));
  }
}
