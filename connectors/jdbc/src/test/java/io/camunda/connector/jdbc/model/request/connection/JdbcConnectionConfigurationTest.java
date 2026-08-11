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

/**
 * A stored credential is deserialized into this record as part of every {@code JdbcRequest} that
 * binds one, so a value this record cannot read fails the whole job — not just validation.
 */
class JdbcConnectionConfigurationTest {

  private static final String OTHER_FIELDS =
      "\"host\":\"h\",\"port\":\"5432\",\"databaseName\":\"d\","
          + "\"username\":\"u\",\"password\":\"p\"";

  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();

  /**
   * Every supported product must be selectable. The dropdown choices in the
   * {@code @TemplateProperty} annotation are hand-written, so a choice value that no enum constant
   * matches would only fail once a modeler picked it.
   */
  @ParameterizedTest
  @EnumSource(SupportedDatabase.class)
  void deserializesEverySupportedDatabase(SupportedDatabase database) throws Exception {
    assertThat(configurationWithDatabase(database.name()).database()).isEqualTo(database);
  }

  /** The shared mapper matches enum names case-insensitively. */
  @Test
  void deserializesTheDatabaseCaseInsensitively() throws Exception {
    assertThat(configurationWithDatabase("postgresql").database())
        .isEqualTo(SupportedDatabase.POSTGRESQL);
  }

  /** The credentials this record carries must never reach a log through {@code toString}. */
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
