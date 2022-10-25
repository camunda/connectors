/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.model;

import io.camunda.connector.api.annotation.Secret;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class RabbitMqMessage {
  @Secret private Object properties;
  @NotNull @Secret private Object body;

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
