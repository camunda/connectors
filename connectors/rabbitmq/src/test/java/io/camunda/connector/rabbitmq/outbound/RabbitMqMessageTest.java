/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.rabbitmq.outbound.model.RabbitMqMessage;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RabbitMqMessageTest {

  @ParameterizedTest
  @ValueSource(strings = {"{\\\"key\\\": \\\"value\\\"}", "{\n \\\"key\\\":\n \\\"value\\\"} \n "})
  public void getBodyAsByteArray_shouldRemoveBackslashesFormJson(String input) {
    // Given
    final RabbitMqMessage rabbitMqMessage = new RabbitMqMessage(null, input);
    // when
    final byte[] bodyAsByteArray = MessageUtil.getBodyAsByteArray(rabbitMqMessage.body());
    // then
    assertThat(new String(bodyAsByteArray)).isEqualTo("{\"key\":\"value\"}");
  }

  @Test
  public void getBodyAsByteArray_shouldParseJsonWithInt() {
    // Given
    final String msgWithDigital = "{\\\"key\\\": -1}";
    final RabbitMqMessage rabbitMqMessage = new RabbitMqMessage(null, msgWithDigital);
    // when
    final byte[] bodyAsByteArray = MessageUtil.getBodyAsByteArray(rabbitMqMessage.body());
    // then
    assertThat(new String(bodyAsByteArray)).isEqualTo("{\"key\":-1}");
  }

  @Test
  public void getBodyAsByteArray_shouldParseJsonWithDouble() {
    // Given
    final String msgWithDigital = "{\"key\": 0.369}";
    final RabbitMqMessage rabbitMqMessage = new RabbitMqMessage(null, msgWithDigital);
    // when
    final byte[] bodyAsByteArray = MessageUtil.getBodyAsByteArray(rabbitMqMessage.body());
    // then
    assertThat(new String(bodyAsByteArray)).isEqualTo("{\"key\":0.369}");
  }

  @Test
  public void getBodyAsByteArray_shouldParsePlainText() {
    // Given
    final String msgWithDigital = "simple text";
    final RabbitMqMessage rabbitMqMessage = new RabbitMqMessage(null, msgWithDigital);
    // when
    final byte[] bodyAsByteArray = MessageUtil.getBodyAsByteArray(rabbitMqMessage.body());
    // then
    assertThat(new String(bodyAsByteArray)).isEqualTo(msgWithDigital);
  }

  @Test
  public void getBodyAsByteArray_shouldNotEscapeCharWhenObject() {
    // Given
    final Map<String, String> msgWithDigital = Map.of("key", "\"simple\" value");
    final RabbitMqMessage rabbitMqMessage = new RabbitMqMessage(null, msgWithDigital);
    // when
    final byte[] bodyAsByteArray = MessageUtil.getBodyAsByteArray(rabbitMqMessage.body());
    // then
    assertThat(new String(bodyAsByteArray)).contains("\\\"");
  }
}
