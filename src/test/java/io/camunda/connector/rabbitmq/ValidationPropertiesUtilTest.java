/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ValidationPropertiesUtilTest extends BaseTest {

  @Test
  public void validateAmqpBasicPropertiesOrThrowException_shouldReturnSameObjectWithoutChanging() {
    // given json object with properties
    JsonObject expected = new JsonObject();
    JsonObject headers = new JsonObject();
    headers.addProperty("header", "value");
    expected.addProperty("userId", "1234");
    expected.addProperty("appId", "123456");
    expected.addProperty("contentEncoding", "UTF-8");
    expected.addProperty("contentType", "json");
    expected.addProperty("messageId", "00000001");
    expected.add("headers", headers);
    // when do validation
    JsonElement actual =
        ValidationPropertiesUtil.validateAmqpBasicPropertiesOrThrowException(expected);
    // then expected that method return object without changing
    assertThat(expected).isEqualTo(actual);
  }

  @ParameterizedTest
  @MethodSource("failPropertiesFieldValidationTest")
  void validateAmqpBasicPropertiesOrThrowException_shouldThrowExceptionWhenNameOfPropertyNotIsWrong(
      String input) {
    // Given request with wrong name of properties field
    JsonElement properties = gson.fromJson(input, JsonElement.class);
    // When validated properties object we check jsonElement with AMQP.BasicProperties
    // Then expect IllegalArgumentException "Unsupported field <field name> for properties"
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> ValidationPropertiesUtil.validateAmqpBasicPropertiesOrThrowException(properties),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage()).contains("Unsupported field", "for properties");
  }
}
