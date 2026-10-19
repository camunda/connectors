/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ResponseFormatConfigurationTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void defaultsNullOrBlankSchemaName(String schemaName) {
    final var config = new JsonResponseFormatConfiguration(Map.of("type", "object"), schemaName);

    assertThat(config.schemaName()).isEqualTo("Response");
  }

  @Test
  void keepsExplicitSchemaName() {
    final var config = new JsonResponseFormatConfiguration(Map.of("type", "object"), "Answer");

    assertThat(config.schemaName()).isEqualTo("Answer");
  }

  @Test
  void emptySchemaIsTreatedAsNoSchema() {
    assertThat(new JsonResponseFormatConfiguration(Map.of(), "Answer").hasSchema()).isFalse();
    assertThat(new JsonResponseFormatConfiguration(null, "Answer").hasSchema()).isFalse();
  }
}
