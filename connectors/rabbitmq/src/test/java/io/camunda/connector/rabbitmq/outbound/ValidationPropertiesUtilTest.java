/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ValidationPropertiesUtilTest extends OutboundBaseTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void validateAmqpBasicPropertiesOrThrowException_shouldReturnSameObjectWithoutChanging() {
    // given json object with properties
    ObjectNode expected = mapper.createObjectNode();
    ObjectNode headers = mapper.createObjectNode();
    headers.set("header", mapper.convertValue("value", JsonNode.class));
    expected.set("userId", mapper.convertValue("1234", JsonNode.class));
    expected.set("appId", mapper.convertValue("123456", JsonNode.class));
    expected.set("contentEncoding", mapper.convertValue("UTF-8", JsonNode.class));
    expected.set("contentType", mapper.convertValue("json", JsonNode.class));
    expected.set("messageId", mapper.convertValue("00000001", JsonNode.class));
    expected.set("headers", headers);
    // when do validation
    com.fasterxml.jackson.databind.JsonNode actual =
        ValidationPropertiesUtil.validateAmqpBasicPropertiesOrThrowException(expected);
    // then expected that method return object without changing
    assertThat(expected).isEqualTo(actual);
  }

  @ParameterizedTest
  @MethodSource("failPropertiesFieldValidationTest")
  void validateAmqpBasicPropertiesOrThrowException_shouldThrowExceptionWhenNameOfPropertyNotIsWrong(
      String input) throws JsonProcessingException {
    // Given request with wrong name of properties field
    JsonNode properties = mapper.readTree(input);
    // When validated properties object we check jsonElement with AMQP.BasicProperties
    // Then expect IllegalArgumentException "Unsupported field <field name> for properties"
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> ValidationPropertiesUtil.validateAmqpBasicPropertiesOrThrowException(properties),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage()).contains("Unsupported field [", "] for properties");
  }
}
