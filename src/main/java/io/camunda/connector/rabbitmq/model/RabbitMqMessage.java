/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.model;

import com.rabbitmq.client.AMQP;
import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.rabbitmq.ValidationPropertiesUtil;
import io.camunda.connector.rabbitmq.supplier.GsonSupplier;
import java.util.Objects;
import java.util.Optional;
import javax.validation.constraints.NotNull;

public class RabbitMqMessage {
  @Secret private Object properties;
  @NotNull @Secret private Object body;

  public AMQP.BasicProperties getPropertiesAsAmqpBasicProperties() {
    return Optional.ofNullable(properties)
        .map(GsonSupplier.gson()::toJsonTree)
        .map(ValidationPropertiesUtil::validateAmqpBasicPropertiesOrThrowException)
        .map(
            jsonProperties ->
                GsonSupplier.gson().fromJson(jsonProperties, AMQP.BasicProperties.class))
        .orElse(null);
  }

  public byte[] getBodyAsByteArray() {
    return Optional.of(body)
        .map(GsonSupplier.gson()::toJson)
        .map(String::getBytes)
        .orElseThrow(() -> new RuntimeException("Parse error to byte array"));
  }

  public Object getProperties() {
    return properties;
  }

  public void setProperties(final Object properties) {
    this.properties = properties;
  }

  public Object getBody() {
    return body;
  }

  public void setBody(final Object body) {
    this.body = body;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RabbitMqMessage message = (RabbitMqMessage) o;
    return Objects.equals(properties, message.properties) && Objects.equals(body, message.body);
  }

  @Override
  public int hashCode() {
    return Objects.hash(properties, body);
  }

  @Override
  public String toString() {
    return "RabbitMqMessage{" + "properties=" + properties + ", body=" + body + "}";
  }
}
