/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model.authorization;

import java.util.Objects;

public class ApiKeyProperties {
  public enum ApiKeyType {
    HEADER,
    QUERY_PARAMS
  }

  private ApiKeyType type;
  private String key;
  private String value;

  public ApiKeyType getType() {
    return type;
  }

  public void setType(final ApiKeyType type) {
    this.type = type;
  }

  public String getKey() {
    return key;
  }

  public void setKey(final String key) {
    this.key = key;
  }

  public String getValue() {
    return value;
  }

  public void setValue(final String value) {
    this.value = value;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final ApiKeyProperties that)) {
      return false;
    }
    return type == that.type && Objects.equals(key, that.key) && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, key, value);
  }

  @Override
  public String toString() {
    return "ApiKeyProperties{"
        + "type="
        + type
        + ", key='"
        + key
        + "'"
        + ", value='[REDACTED]'"
        + "}";
  }
}
