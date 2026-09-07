/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.request.connection;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class JdbcConnectionConfigurationTest {

  private static final String OTHER_FIELDS =
      "\"host\":\"h\",\"port\":\"5432\",\"databaseName\":\"d\","
          + "\"username\":\"u\",\"password\":\"p\"";

  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();

  @ParameterizedTest
  @EnumSource(SupportedDatabase.class)
  void deserializesEverySupportedDatabase(SupportedDatabase database) throws Exception {
    assertThat(configurationWithDatabase(database.name()).database()).isEqualTo(database);
  }

  @Test
  void deserializesTheDatabaseCaseInsensitively() throws Exception {
    assertThat(configurationWithDatabase("postgresql").database())
        .isEqualTo(SupportedDatabase.POSTGRESQL);
  }

  @Test
  void toStringRedactsTheLogin() {
    var configuration =
        new JdbcConnectionConfiguration(
            SupportedDatabase.POSTGRESQL, "h", "5432", "d", "the-login", "the-secret");

    assertThat(configuration.toString())
        .doesNotContain("the-login")
        .doesNotContain("the-secret")
        .contains("[REDACTED]");
  }

  private JdbcConnectionConfiguration configurationWithDatabase(String database) throws Exception {
    return objectMapper.readValue(
        "{\"database\":\"" + database + "\"," + OTHER_FIELDS + "}",
        JdbcConnectionConfiguration.class);
  }
}
