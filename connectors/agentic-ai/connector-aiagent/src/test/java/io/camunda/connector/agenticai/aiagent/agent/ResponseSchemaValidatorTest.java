/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResponseSchemaValidatorTest {

  private ResponseSchemaValidator validator(long bytes, int depth) {
    return new ResponseSchemaValidator(ConnectorsObjectMapperSupplier.getCopy(), bytes, depth);
  }

  @Test
  void acceptsSchemaAtExactByteLimitAndRejectsOneByteOver() {
    final var format = new JsonResponseFormatConfiguration(Map.of("title", "a"), "Response");

    assertThatCode(() -> validator(13, 64).validate(format)).doesNotThrowAnyException();
    assertThatThrownBy(() -> validator(12, 64).validate(format))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("12 bytes");
  }

  @Test
  void countsUtf8BytesRatherThanCharacters() {
    final var format = new JsonResponseFormatConfiguration(Map.of("title", "\u00e9"), "Response");

    assertThatCode(() -> validator(14, 64).validate(format)).doesNotThrowAnyException();
    assertThatThrownBy(() -> validator(13, 64).validate(format))
        .isInstanceOf(ConnectorException.class);
  }

  @Test
  void countsBothObjectsAndArraysInNestingDepth() {
    final var format =
        new JsonResponseFormatConfiguration(
            Map.of("anyOf", List.of(Map.of("type", "string"))), "Response");

    assertThatCode(() -> validator(1024, 3).validate(format)).doesNotThrowAnyException();
    assertThatThrownBy(() -> validator(1024, 2).validate(format))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("nesting depth of 2");
  }

  @Test
  void rejectsLargeScalarWithoutIncludingItsContentInTheError() {
    final var format =
        new JsonResponseFormatConfiguration(
            Map.of("description", "sensitive".repeat(10000)), "Response");

    assertThatThrownBy(() -> validator(1024, 64).validate(format))
        .isInstanceOf(ConnectorException.class)
        .hasMessageNotContaining("sensitive");
  }

  @Test
  void rejectsWideSchema() {
    final var schema = new HashMap<String, Object>();
    for (int i = 0; i < 1000; i++) {
      schema.put("field" + i, Map.of("type", "string"));
    }

    assertThatThrownBy(
            () ->
                validator(1024, 64)
                    .validate(new JsonResponseFormatConfiguration(schema, "Response")))
        .isInstanceOf(ConnectorException.class);
  }

  @Test
  void rejectsCyclicMapsThroughTheDepthLimit() {
    final var schema = new HashMap<String, Object>();
    schema.put("items", schema);

    assertThatThrownBy(
            () ->
                validator(1024, 4)
                    .validate(new JsonResponseFormatConfiguration(schema, "Response")))
        .isInstanceOf(ConnectorException.class);
  }

  @Test
  void skipsAbsentAndUnconstrainedSchemas() {
    final var validator = validator(1, 1);
    assertThatCode(
            () -> {
              validator.validate(null);
              validator.validate(new TextResponseFormatConfiguration(false));
              validator.validate(new JsonResponseFormatConfiguration(null, "Response"));
              validator.validate(new JsonResponseFormatConfiguration(Map.of(), "Response"));
            })
        .doesNotThrowAnyException();
  }
}
