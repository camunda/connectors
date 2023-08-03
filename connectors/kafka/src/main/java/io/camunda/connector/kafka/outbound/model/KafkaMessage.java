/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.outbound.model;

import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class KafkaMessage {
  @NotNull private Object key;
  @NotNull private Object value;

  public Object getKey() {
    return key;
  }

  public void setKey(Object key) {
    this.key = key;
  }

  public Object getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    KafkaMessage that = (KafkaMessage) o;
    return key.equals(that.key) && value.equals(that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, value);
  }

  @Override
  public String toString() {
    return "KafkaMessage{" + "key=" + key + ", value=" + value + '}';
  }
}
