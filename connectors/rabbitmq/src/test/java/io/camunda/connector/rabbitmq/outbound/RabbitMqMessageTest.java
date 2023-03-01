/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import io.camunda.connector.rabbitmq.common.model.RabbitMqMessage;
import io.camunda.connector.rabbitmq.supplier.GsonSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RabbitMqMessageTest {

  @ParameterizedTest
  @ValueSource(strings = {"{\\\"key\\\": \\\"value\\\"}", "{\n \\\"key\\\":\n \\\"value\\\"} \n "})
  public void getBodyAsByteArray_shouldRemoveBackslashesFormJson(String input) {
    // Given
    final RabbitMqMessage rabbitMqMessage = new RabbitMqMessage();
    rabbitMqMessage.setBody(input);
    // when
    final byte[] bodyAsByteArray = rabbitMqMessage.getBodyAsByteArray();
    // then
    assertThat(
            GsonSupplier.gson().fromJson(new String(bodyAsByteArray), JsonObject.class).toString())
        .isEqualTo("{\"key\":\"value\"}");
  }

  @Test
  public void getBodyAsByteArray_shouldParseJsonWithInt() {
    // Given
    final String msgWithDigital = "{\\\"key\\\": -1}";
    final RabbitMqMessage rabbitMqMessage = new RabbitMqMessage();
    rabbitMqMessage.setBody(msgWithDigital);
    // when
    final byte[] bodyAsByteArray = rabbitMqMessage.getBodyAsByteArray();
    // then
    assertThat(
            GsonSupplier.gson().fromJson(new String(bodyAsByteArray), JsonObject.class).toString())
        .isEqualTo("{\"key\":-1}");
  }

  @Test
  public void getBodyAsByteArray_shouldParseJsonWithDouble() {
    // Given
    final String msgWithDigital = "{\"key\": 0.369}";
    final RabbitMqMessage rabbitMqMessage = new RabbitMqMessage();
    rabbitMqMessage.setBody(msgWithDigital);
    // when
    final byte[] bodyAsByteArray = rabbitMqMessage.getBodyAsByteArray();
    // then
    assertThat(
            GsonSupplier.gson().fromJson(new String(bodyAsByteArray), JsonObject.class).toString())
        .isEqualTo("{\"key\":0.369}");
  }

  @Test
  public void getBodyAsByteArray_shouldParsePlainText() {
    // Given
    final String msgWithDigital = "simple text";
    final RabbitMqMessage rabbitMqMessage = new RabbitMqMessage();
    rabbitMqMessage.setBody(msgWithDigital);
    // when
    final byte[] bodyAsByteArray = rabbitMqMessage.getBodyAsByteArray();
    // then
    assertThat(new String(bodyAsByteArray)).isEqualTo(msgWithDigital);
  }
}
