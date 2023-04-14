/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import java.util.Objects;

public class KafkaInboundMessage {

  private String key;

  private String rawValue;

  private Object value;

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getRawValue() {
    return rawValue;
  }

  public void setRawValue(String stringValue) {
    this.rawValue = stringValue;
  }

  public Object getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return "KafkaInboundMessage{"
        + "key='"
        + key
        + '\''
        + ", rawValue='"
        + rawValue
        + '\''
        + ", value="
        + value
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    KafkaInboundMessage that = (KafkaInboundMessage) o;
    return Objects.equals(key, that.key)
        && Objects.equals(rawValue, that.rawValue)
        && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, rawValue, value);
  }
}
