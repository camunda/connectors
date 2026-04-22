/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.codeinterpreter.model.request;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Language {
  PYTHON("python"),
  JAVASCRIPT("javascript"),
  TYPESCRIPT("typescript");

  private final String value;

  Language(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
