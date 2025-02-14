/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.protocols.actions;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import io.camunda.connector.generator.java.annotation.EnumValue;

public enum ContentType {
  @JsonEnumDefaultValue
  @EnumValue(label = "PLAIN", order = 0)
  PLAIN("text/plain; charset=utf-8"),
  @EnumValue(label = "HTML", order = 1)
  HTML("text/html; charset=utf-8"),
  @EnumValue(label = "HTML & Plaintext", order = 2)
  MULTIPART("multipart/mixed; charset=utf-8");

  private final String value;

  ContentType(String value) {
    this.value = value;
  }

  public String type() {
    return value;
  }
}
