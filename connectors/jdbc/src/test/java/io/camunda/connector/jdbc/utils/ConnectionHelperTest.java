/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.utils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.jdbc.model.request.JdbcRequest;
import io.camunda.connector.jdbc.model.request.JdbcRequestData;
import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import io.camunda.connector.jdbc.model.request.connection.DetailedConnection;
import io.camunda.connector.jdbc.model.request.connection.UriConnection;
import org.junit.jupiter.api.Test;

class ConnectionHelperTest {

  private static final JdbcRequestData DATA = new JdbcRequestData(false, "SELECT 1");

  @Test
  void appliesTheMariaDbDriverRulesWhateverDatabaseIsSelected() {
    var uri =
        new UriConnection("jdbc:mariadb://db.example.com:3306/app?allowLocalInfile=true", null);
    var request = new JdbcRequest(SupportedDatabase.POSTGRESQL, uri, DATA);

    assertThatThrownBy(() -> ConnectionHelper.openConnection(request))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("allowLocalInfile");
  }

  @Test
  void rejectsDriverOptionsSmuggledThroughTheDatabaseName() {
    var detailed =
        new DetailedConnection(
            "db.example.com", "3306", "user", "pass", "app?allowLocalInfile=true", null);
    var request = new JdbcRequest(SupportedDatabase.MYSQL, detailed, DATA);

    assertThatThrownBy(() -> ConnectionHelper.openConnection(request))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Invalid database name");
  }
}
